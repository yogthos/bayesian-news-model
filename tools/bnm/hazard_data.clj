(ns bnm.hazard-data
  "Reads the stocks of resources/buffers/hazard-stocks.edn into bnm.buffers
  records and their series (bead bnm-56c.6), and the episode panel into
  bnm.hazard's labelled rows. I/O only: what is read is data."
  (:require [bnm.buffers :as b]
            [bnm.episodes :as ep]
            [bnm.hazard :as hz]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def mapping-path "resources/buffers/hazard-stocks.edn")

(defn period-end
  "A period (YYYY, YYYY-MM, YYYY-MM-DD, YYYYQn, YYYY-Qn) as the decimal
  year of its end."
  [s]
  (let [s (str/trim (str s))]
    (cond (re-matches #"\d{4}" s) (+ 1.0 (parse-long s))
          (re-matches #"\d{4}-?Q[1-4]" s) (+ (parse-long (subs s 0 4)) (/ (parse-long (subs s (dec (count s)))) 4.0))
          (re-matches #"\d{4}-\d{2}(-\d{2})?" s) (+ (parse-long (subs s 0 4)) (/ (parse-long (subs s 5 7)) 12.0))
          :else nil)))

(defn- lines [path] (with-open [r (io/reader path)] (vec (line-seq r))))

(defn- csv-rows
  "Rows of a CSV as maps (quoted fields allowed)."
  [path]
  (let [[h & ls] (lines path)
        ks (mapv keyword (ep/parse-line h))]
    (map #(zipmap ks (ep/parse-line %)) ls)))

(defn read-source
  "{key [obs]} of one source spec (see the mapping file's header)."
  [{:keys [file format var key place time value sum scale lag] filt :filter}]
  (let [scale (double (or scale 1.0))
        lagy (/ (double (or lag 0)) 12.0)
        num (fn [s] (when (and s (not (str/blank? s))) (some-> (parse-double s) (* scale))))]
    (if (= format :series-tsv)
      (let [pre (str "\t" var "\t")]
        (with-open [r (io/reader file)]
          (->> (line-seq r)
               (filter #(str/includes? % pre))
               (keep (fn [l] (let [[p _ _ d k v] (str/split l #"\t")
                                   t (period-end d) kn (period-end k) x (num v)]
                               (when (and t kn x) [p {:t t :v x :known kn}]))))
               (reduce (fn [m [p o]] (update m p (fnil conj []) o)) {})
               (into {} (map (fn [[p os]] [p (vec (sort-by :t os))]))))))
      (let [ok? (fn [r] (every? (fn [[c vs]] (some #{(get r c)} vs)) filt))
            pts (for [r (csv-rows file) :when (ok? r)
                      :let [t (period-end (get r time)) x (num (get r value))]
                      :when (and t x)]
                  [[(or place (get r key)) t] x])
            agg (if sum
                  (reduce (fn [m [k x]] (update m k (fnil + 0.0) x)) {} pts)
                  (into {} pts))]
        (->> agg
             (reduce (fn [m [[p t] x]] (update m p (fnil conj []) {:t t :v x :known (+ t lagy)})) {})
             (into {} (map (fn [[p os]] [p (vec (sort-by :t os))]))))))))

(defn- aggregate
  "The sum over every key's observations at each t (the world from its
  countries), known when the last of them is."
  [by-key]
  (->> (vals by-key) (apply concat)
       (group-by :t)
       (map (fn [[t os]] {:t t :v (reduce + 0.0 (map :v os)) :known (reduce max (map :known os))}))
       (sort-by :t) vec))

(def ^:private place-re #"[A-Z]{2,3}")

(defn load-stocks
  "{:records [...] :series {id {role [obs]}}} for every record of the
  mapping. A record carries :families, :sector and :revised-roles (the
  roles whose source is a revised vintage)."
  ([] (load-stocks mapping-path))
  ([path]
   (let [{:keys [sources records]} (edn/read-string (slurp path))
         cache (atom {})
         src (fn [k] (or (@cache k)
                         (let [spec (sources k)
                               v (if (:const spec) {:const (:const spec)} (read-source spec))]
                           (swap! cache assoc k v) v)))]
     (reduce
      (fn [acc tmpl]
        (let [places (let [ps (:places tmpl)]
                       (if (map? ps)
                         (sort (filter #(or (:any-key (sources (:from ps))) (re-matches place-re %)) (keys (src (:from ps)))))
                         ps))
              places (remove (set (:except tmpl)) places)]
          (reduce
           (fn [acc p]
             (let [key-of (fn [k] (if (:aggregate tmpl) nil (or (:source-place tmpl) p)))
                   series (into {} (for [[role k] (:series tmpl)
                                         :let [s (src k)]
                                         :when (not (:const s))
                                         :let [os (if (:aggregate tmpl) (aggregate s) (get s (key-of k)))]
                                         :when (seq os)]
                                     [role os]))
                   ;; a constant fills the t of the record's other claim or flow series
                   series (reduce (fn [ser [role k]]
                                    (if-let [c (:const (src k))]
                                      (let [base (or (:claims ser) (:stock ser) (first (vals ser)))]
                                        (assoc ser role (mapv #(assoc % :v c) base)))
                                      ser))
                                  series (:series tmpl))
                   id (str/replace (:id tmpl) "{place}" p)
                   revised (set (for [[role k] (:series tmpl) :when (and (:revised (sources k)) (contains? series role))] role))]
               (if (empty? series)
                 acc
                 (-> acc
                     (update :records conj
                             (b/record (cond-> {:id id :name (:name tmpl) :unit (:unit tmpl) :time-unit "year"
                                                :holder (assoc (:holder tmpl) :place (or (:place-is tmpl) p))
                                                :families (vec (:families tmpl)) :sector (:sector tmpl)
                                                :measure {:frequency 1 :lag 0}
                                                :revised-roles revised
                                                :flags (if (seq revised) #{:revised-vintage} #{})}
                                         (:outflows tmpl) (assoc :outflows (:outflows tmpl))
                                         (:displace tmpl) (assoc :displace (:displace tmpl)))))
                     (assoc-in [:series id] series)))))
           acc places)))
      {:records [] :series {}}
      records))))

;; --- the panel ---------------------------------------------------------------------------

(defn episode-classes
  "{episode id :main | :gra | :rr} for the reserve-crisis labels
  (bnm.hazard/reserve-class); every other family's episodes are :main."
  []
  (into {} (for [r (ep/episodes)]
             [(:episode r) (if (= "reserve_crisis" (:family r))
                             (hz/reserve-class {:catalogues (ep/split-list (:episode_catalogues r))
                                                :start-year (parse-long (subs (:episode_start r) 0 4))})
                             :main)])))

(defn label-rows [] (hz/label-rows (ep/panel) (episode-classes)))
