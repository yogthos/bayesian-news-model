(ns bnm.ingest
  "Turns raw downloads under data/raw/<source>/ into normalized event files
  under data/norm/<source>/, one per raw file: TSV records of
  date a1 s1 a2 s2 adm type mode n (see bnm.events), aggregated per day.

    jolt -A:tools -M -m bnm.ingest gdelt|icews|polecat|acled [jobs]

  A raw file whose normalized file exists is skipped, so a run resumes. Codes
  and names the registry does not know are tallied in data/norm/<source>/unknown.tsv."
  (:require [bnm.cells :as cells]
            [bnm.csv :as csv]
            [bnm.events :as ev]
            [bnm.geo :as geo]
            [bnm.ontology :as ont]
            [bnm.time]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def unknown (atom {}))

(defn- note-unknown! [kind s]
  (when (and s (not= s ""))
    (swap! unknown update [kind s] (fnil inc 0))))

(defn- cached
  "`f` memoized in a mutable map: the same few hundred codes recur on every row."
  [f]
  (let [m (java.util.HashMap.)]
    (fn [s]
      (if (.containsKey m s)
        (.get m s)
        (let [v (f s)] (.put m s v) v)))))

(def ^:private code
  (cached #(or (geo/resolve-code %) (do (note-unknown! :code %) nil))))

(def ^:private fips (cached geo/resolve-fips))

(def ^:private place-name
  (cached #(or (geo/resolve-name %) (do (note-unknown! :name %) nil))))

;; --- per-source row parsers: fields -> [date a1 a2 type mode] or nil --------------

(def ^:dynamic *file-day*
  "The day a daily raw file was published, when it is one."
  nil)

(def retrospective-days
  "A report of an event more than this many days before it was published is a
  look back, not news; GDELT's daily files are full of them."
  7)

(def gdelt-columns
  "The GDELT 1.0 columns read (1-based, for cut): SQLDATE; for each actor its
  CountryCode, EthnicCode, Religion1Code, Type1Code and Type2Code;
  EventRootCode; ActionGeo_Type, ActionGeo_CountryCode (FIPS) and
  ActionGeo_ADM1Code. The other forty-odd are dropped before parsing, which
  is most of the cost."
  "2,8,10,11,13,14,18,20,21,23,24,29,50,52,53")

(def ^:private adm1 (cached geo/resolve-adm1))

(defn- gdelt-sector [eth rel t1 t2]
  (ont/sector-of-codes (cond-> [t1 t2] (seq eth) (conj "ETH") (seq rel) (conj "REL"))))

(defn- gdelt-event
  "An event from GDELT fields cut to `gdelt-columns` (and more after them),
  its place within the country given by `place` of the fields."
  [f width place]
  (when (and (= (count f) width)
             (or (nil? *file-day*)
                 (let [d (f 0)]
                   (and (= 8 (count d))
                        (<= 0 (- *file-day* (bnm.time/day d)) retrospective-days)))))
    (let [root (f 11)
          type (get ont/cameo-root->type root :other)
          mode (cond (= root "03") :intend (= root "13") :threaten :else :did)
          [[a1 s1] [a2 s2]] (ev/actors (code (f 1)) (gdelt-sector (f 2) (f 3) (f 4) (f 5))
                                       (code (f 6)) (gdelt-sector (f 7) (f 8) (f 9) (f 10))
                                       (fips (f 13)))
          adm (place f)]
      (when (and a1 (not= type :other))
        [(f 0) a1 s1 a2 s2 adm type mode]))))

(defn gdelt-row
  "GDELT 1.0 event fields, cut down to `gdelt-columns`."
  [f]
  (gdelt-event f 15 #(when (not= (% 12) "1") (adm1 (% 14)))))

(def gdelt-a5-columns
  "`gdelt-columns` and ActionGeo_Lat and ActionGeo_Long."
  (str gdelt-columns ",54,55"))

(def cell-resolution
  "A5 resolution of the cell nodes: 8,300 km2 each."
  cells/resolution)

(defn- cell-node
  "The cell node of a point in the country with FIPS code `fips`: the
  country's alpha-3, \".A\" and the cell's hex id in capitals, a province id
  as far as bnm.geo can tell."
  [country lat lon]
  (when-let [iso3 (fips country)]
    (when-let [lat (parse-double lat)]
      (when-let [lon (parse-double lon)]
        (str iso3 ".A" (str/upper-case (cells/cell lon lat cell-resolution)))))))

(def ^:private cell
  (let [m (java.util.HashMap.)]
    (fn [c lat lon]
      (let [k (str c " " lat " " lon)]
        (if (.containsKey m k)
          (.get m k)
          (let [v (cell-node c lat lon)] (.put m k v) v))))))

(defn gdelt-a5-row
  "GDELT fields cut to `gdelt-a5-columns`, placed in an A5 cell when ActionGeo
  is a city (types 3 and 4, in the US and elsewhere) or a subdivision outside
  the US (5); countries and US states (1 and 2) have no cell."
  [f]
  (gdelt-event f 17 #(when (#{"3" "4" "5"} (% 12)) (cell (% 13) (% 15) (% 16)))))

(def icews-columns
  "ICEWS columns: Event Date, Source Sectors, Source Country, CAMEO Code,
  Target Sectors, Target Country, Province, Country."
  (atom nil))

(def ^:private province
  (let [m (java.util.HashMap.)]
    (fn [iso3 s]
      (let [k [iso3 s]]
        (if (.containsKey m k)
          (.get m k)
          (let [v (geo/resolve-province iso3 s)]
            (when (and (nil? v) iso3 (seq s) (not= s "None")) (note-unknown! :province (str iso3 " " s)))
            (.put m k v)
            v))))))

(def ^:private icews-sector (cached ont/icews-sector))

(defn icews-row [f]
  (let [{:keys [date ssec src code tsec tgt prov loc]} @icews-columns
        [type mode] (ont/cameo-event (f code))
        country (place-name (f loc))
        [[a1 s1] [a2 s2]] (ev/actors (place-name (f src)) (icews-sector (f ssec))
                                     (place-name (f tgt)) (icews-sector (f tsec))
                                     country)]
    (when (and a1 (not= type :other))
      [(str/replace (f date) "-" "") a1 s1 a2 s2 (province country (f prov)) type mode])))

(def polecat-columns (atom nil))

(defn- place
  "A place given as an ISO code or a name; the first of several. \"None\" is none."
  [s]
  (let [s (some-> s (str/split #"[;|]") first str/trim)]
    (when (and (seq s) (not= s "None"))
      (if (re-matches #"[A-Z]{3}" s)
        (code s)
        (place-name s)))))

(defn- polecat-sector [primary all]
  (ont/sector-of-codes (cons (some-> primary str/trim)
                             (map str/trim (str/split (or all "") #"[;,|]")))))

(defn polecat-row [f]
  (let [{:keys [date type mode src ssec ssecs tgt tsec tsecs prov loc]} @polecat-columns
        t (ont/plover-type (str/upper-case (f type)))
        m (get ont/plover-mode (str/upper-case (f mode)) :did)
        country (place (f loc))
        [[a1 s1] [a2 s2]] (ev/actors (place (f src)) (polecat-sector (f ssec) (f ssecs))
                                     (place (f tgt)) (polecat-sector (f tsec) (f tsecs))
                                     country)]
    (when (and a1 t (not= t :other))
      [(str/replace (subs (f date) 0 (min 10 (count (f date)))) "-" "") a1 s1 a2 s2
       (province country (f prov)) t m])))

(def acled-columns (atom nil))

(def ^:private months
  {"January" "01" "February" "02" "March" "03" "April" "04" "May" "05" "June" "06"
   "July" "07" "August" "08" "September" "09" "October" "10" "November" "11" "December" "12"})

(defn- acled-date [s]
  ;; "2023-10-07" from the API, "07 October 2023" from exports
  (if (re-matches #"\d{4}-\d{2}-\d{2}" s)
    (str/replace s "-" "")
    (let [[d m y] (str/split s #" ")]
      (str y (months m) (if (= 1 (count d)) (str "0" d) d)))))

(defn acled-row [f]
  (let [{:keys [date etype stype loc]} @acled-columns
        [type mode] (ont/acled-event (f etype) (f stype))
        country (place-name (f loc))]
    (when (and country (not= type :other))
      [(acled-date (f date)) country nil nil nil nil type mode])))

;; --- the sources --------------------------------------------------------------------

(defn- column-index [header wanted]
  (let [idx (zipmap header (range))]
    (into {} (for [[k names] wanted]
               [k (or (some idx names)
                      (throw (ex-info (str "no column " names) {:header header})))]))))

(def sources
  {"gdelt" {:sep \tab :header? false :row gdelt-row :cut gdelt-columns
            :raw? #(str/ends-with? % ".zip")}
   ;; GDELT again with A5 cells in place of provinces
   "gdelt-a5" {:sep \tab :header? false :row gdelt-a5-row :cut gdelt-a5-columns
               :raw-dir "gdelt" :raw? #(str/ends-with? % ".zip")}
   "icews" {:sep \tab :header? true :row icews-row
            :raw? #(re-find #"(?i)events.*\.(tab|zip|txt|csv)$" %)
            :columns icews-columns
            :wanted {:date ["Event Date"] :ssec ["Source Sectors"] :src ["Source Country"]
                     :code ["CAMEO Code"] :tsec ["Target Sectors"] :tgt ["Target Country"]
                     :prov ["Province"] :loc ["Country"]}}
   "polecat" {:sep \tab :header? true :row polecat-row
              :raw? #(re-find #"(?i)\.(txt|tsv|csv|tab)$" %)
              :columns polecat-columns
              :wanted {:date ["Event Date" "Story Date"] :type ["Event Type"] :mode ["Event Mode"]
                       :src ["Actor Country"] :ssec ["Primary Actor Sector"] :ssecs ["Actor Sectors"]
                       :tgt ["Recipient Country"] :tsec ["Primary Recipient Sector"]
                       :tsecs ["Recipient Sectors"] :prov ["Province"]
                       :loc ["Country" "Location Country" "Event Country"]}}
   "acled" {:sep \, :header? true :row acled-row :csv? true
            :raw? #(str/ends-with? % ".csv")
            :columns acled-columns
            :wanted {:date ["event_date"] :etype ["event_type"] :stype ["sub_event_type"]
                     :loc ["country"]}}})

(defn- open-lines
  "The lines of a raw file. A zip is unpacked to a temporary file first:
  reading a subprocess's output stream is several times slower than reading a
  file. Returns [reader temp-file-or-nil]."
  [^String path cut]
  (if (str/ends-with? path ".zip")
    (let [tmp (io/file "data/tmp" (str (.getName (io/file path)) ".csv"))]
      (io/make-parents tmp)
      (p/shell {:out :string} "sh" "-c"
               (if cut
                 "unzip -p \"$1\" | cut -f\"$3\" > \"$2\""
                 "unzip -p \"$1\" > \"$2\"")
               "_" path (str tmp) (or cut ""))
      [(io/reader tmp) tmp])
    [(io/reader path) nil]))

(defn normalize-file
  "Reads one raw file of `source` and writes its normalized file to `out`.
  Returns the number of raw rows read."
  [source path out]
  (let [{:keys [sep header? row columns wanted csv? cut]} (sources source)
        [rdr tmp] (open-lines path cut)
        counts (java.util.HashMap.)
        rows (volatile! 0)]
    (with-open [rdr rdr]
      (let [lines (if csv?
                    (csv/records rdr sep)
                    (let [re (re-pattern (str sep))]
                      (map #(str/split % re -1) (line-seq rdr))))
            lines (if header?
                    (let [[h & more] lines]
                      (reset! columns (column-index (mapv str/trim h) wanted))
                      more)
                    lines)]
        (doseq [f lines]
          (vswap! rows inc)
          (when-let [[date a1 s1 a2 s2 adm type mode] (try (row f) (catch Throwable _ nil))]
            (when (and date (= 8 (count date)))
              (let [k (str/join "\t" [date a1 (or s1 "") (or a2 "") (or s2 "") (or adm "")
                                      (name type) (name mode)])]
                (.put counts k (inc (or (.get counts k) 0)))))))))
    (when tmp (.delete tmp))
    (io/make-parents out)
    (with-open [w (io/writer (str out ".part"))]
      (doseq [k (sort (keys counts))]
        (.write w (str k "\t" (.get counts k) "\n"))))
    (.renameTo (io/file (str out ".part")) (io/file out))
    @rows))

(defn raw-files [source]
  (let [{:keys [raw? raw-dir]} (sources source)
        dir (io/file "data/raw" (or raw-dir source))]
    (->> (.listFiles dir)
         (map #(.getName %))
         (filter raw?)
         (remove #(str/ends-with? % ".part"))
         sort)))

(defn out-path [source raw-name]
  (str "data/norm/" source "/" (str/replace raw-name #"\.(zip|tab|txt|csv|tsv)$" "") ".tsv"))

(defn write-unknown! [source]
  (let [path (str "data/norm/" source "/unknown-" (System/currentTimeMillis) ".tsv")]
    (io/make-parents path)
    (spit path (str/join (for [[[kind s] n] (sort-by (comp - val) @unknown)]
                           (str (name kind) "\t" s "\t" n "\n"))))))

(defn -main [source & [shard shards]]
  ;; shard i of n: lets several processes split one source's files
  (let [shard (parse-long (or shard "0"))
        shards (parse-long (or shards "1"))
        files (->> (raw-files source)
                   (keep-indexed (fn [i f] (when (= shard (mod i shards)) f))))]
    (doseq [f files
            :let [out (out-path source f)]
            :when (not (.exists (io/file out)))]
      (let [t0 (System/currentTimeMillis)
            file-day (when-let [[_ d] (re-matches #"(\d{8})\..*" f)] (bnm.time/day d))
            n (binding [*file-day* file-day]
                (normalize-file source (str "data/raw/" (or (:raw-dir (sources source)) source) "/" f) out))]
        (println source f n "rows" (- (System/currentTimeMillis) t0) "ms")
        (flush)))
    (write-unknown! source)))
