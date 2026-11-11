(ns bnm.ledger
  "The forward ledger (docs/PLAN.md 0.1; the registration in
  docs/PHASE-CHANGES.md, 11 November 2026, and its amendment L1). Each
  quarter the frozen base (the world without relations; the base of record
  is t50-world-norel) issues every kind, place and window at the quarter's
  last month, from the data on disk at the issue date, to
  registered/ledger/<issue>/, with its hashes; nothing is refit between
  issue and outcome, and an issue is never edited. Later, when a window's
  outcomes are published, the issue is scored once.

    jolt -A:tools -M -m bnm.ledger issue --issue 2027-Q1 [--date 2027-02-15]
    jolt -A:tools -M -m bnm.ledger score --issue 2026-Q4-r1 --scale year [--kinds a,b]
    jolt -A:tools -M -m bnm.ledger claim [--scale year]
    jolt -A:tools -M -m bnm.ledger size-check [--run t49-world-norel]

  An issue is a quarter, YYYY-Qn, or a corrected issue of one, YYYY-Qn-rN.

  issue refuses a date outside the quarter's last two months, and unless
  the code (`code-paths`) is committed and clean. It then reruns the
  previous issue (`previous-issue`) on that issue's own archived inputs
  (data/ledger-inputs/<previous>/, each with the SHA-256 in its manifest)
  and refuses unless the run gives that issue's forecasts.tsv byte for
  byte: a code change that moves the previous issue cannot issue. Then it
  runs the base's command (`command`) on the data on disk, recording every
  file read; refuses if the run reads one not in `inputs` or one changes
  during the run; when the inputs are byte for byte the base of record's
  (`base-inputs`), refuses unless the run reproduces the base on every row
  it has (origins to `base-end`). It archives the inputs read-only to
  data/ledger-inputs/<issue>/ and writes forecasts.tsv (every row at each
  scale's latest origin, the benchmarks frozen beside it), manifest.edn
  (with the inputs that changed since the previous issue) and README.md,
  and prints the hashes to register in `issued`.

  score refuses an issue not in `issued` or whose files' SHA-256s are not
  the registered ones; refuses a kind the issue does not hold; refuses a
  kind and scale already scored (registered in `scored`, on disk, or ever
  committed); refuses a window whose outcomes are not published
  (`published?`); scores the issue's country rows that are at risk and
  covered (bnm.lattice-banking/onset-in?, as the base is scored): Brier
  and log loss and skill against the frozen benchmarks, and the watch-list
  measure (top-10 recall on hard onsets, bnm.watchlist) for the base,
  persistence, climatology and the last 12 months. Writes
  scores/<scale>/<kind>.edn with the scored rows and, when every kind of
  the scale is scored, scores/<scale>/pooled.txt.

  claim pools the registered year-node scores of the primary issues
  (`claim`, amendment L1): Brier and log skill against rate, persistence
  and last12 with a country-cluster bootstrap interval, claimed only from
  three issues on; reported issues are printed beside, with no verdict.
  size-check runs the interval's size check on development data."
  (:require [bnm.benchmarks :as bm]
            [bnm.kinds :as kinds]
            [bnm.lattice-banking :as lb]
            [bnm.sfc-rates-confirm :as sfc]
            [bnm.watchlist :as wl]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint]
            [clojure.string :as str]))

(def base "t50-world-norel")
(def base-end "The base of record's last origin, December 2025." (+ (* 12 2025) 11))
(def ledger-dir "registered/ledger/")
(def inputs-root "Where each issue's inputs are archived, read-only." "data/ledger-inputs/")

(def inputs
  "Every file the base reads (recorded by running it, 11 November 2026)."
  ["data/derived/econ-coverage.csv" "data/derived/econ-events.csv" "data/derived/fiscal.csv"
   "data/derived/jst-credit-gap.csv" "data/derived/lattice-annual.csv" "data/derived/lattice-quarterly.csv"
   "data/derived/political-annual.csv" "data/derived/political-long.csv" "data/derived/political-states.csv"
   "data/raw/slow/jst/jst.csv" "data/raw/slow/kaopen/kaopen.csv" "data/raw/slow/pwt/pwt110-full.csv"
   "data/raw/slow/rr/onsets.csv" "data/runs/r6-at-8ul/preds.tsv" "data/slow/coverage.tsv" "data/slow/events.tsv"
   "data/slow/series.tsv" "data/slow/variables.tsv"])

(def base-files
  "The base of record's forecasts, data/runs/t50-world-norel/<kind>/
  forecasts.tsv (written 11 November 2026 on the data rebuilt after the
  gazetteer fix, its quarters pooled with r6-at-8ul, bead bnm-8ul), by
  SHA-256."
  {"banking_crisis" "853869f61b302908149a37d044eebb944118625bab3c52c9ed5800846864163e"
   "conflict_onset" "a9079363ee8e794bd1fc0e6fec60979c3811d18b34b1bee0e4c853209b59df4b"
   "coup" "da950b77d2fef828795d17404087f855756d9a099eb81c386d21ea9f0b58f7c4"
   "currency_crisis" "68dea1c7e3a6bbdf77c584bcf32ca0faa9356e711f53e7a7baaec4cbf67f92e4"
   "fiscal_consolidation" "d5641ad89431f1a518671a1ebe3df3891b40f9f122014602783af399385f3b50"
   "food_price_shock" "e49b33f12552290f5e3d27a8bc35fb8d29a9ac6cc9171f63234954554b474c4d"
   "growth_collapse" "1e61875f80b6a5dbefc0ee97e2435b3113dffa39f9d270d3dd36a1f6e9ec211f"
   "inflation_crisis" "1857a999f1819f3b7edc99fa341a9f6c17d63ff5184481d0d4e169a476923b71"
   "market_crash" "890294f50bd395bf9a6e70545cb14d0bee465725253416add625155f09b2ef1b"
   "protest_surge" "377a9af2380b868bbfbd82b078e2a5d29df5060e8514c66d512197c87cc0399c"
   "sovereign_default" "9761e8ec60415a780cadbe3a9c305d864688736b46cf0e79cbde45de5271b675"
   "violence_surge" "0313995d20dba1d88dd3d0d117e607129d63fc9d928340e6fe800eb3ffa9fa4a"
   "war_onset" "0e69bd96a0d5b35b6f4f3a7ed2a0daf4f03be356e6c955c23bb96ce63fb20947"})

(def base-inputs
  "The SHA-256 of every input t50-world-norel was built on: a run on these
  must reproduce it."
  {"data/derived/econ-coverage.csv" "863b42e001b45e7eb43569b3f48eb92035d62821f3a3ea9cd543d38c1c2a4c7f"
   "data/derived/econ-events.csv" "db737e6ef92fafe1c37ae3d4ac1bd801f7f5acd8ddada2f5e4c9e37bcb572267"
   "data/derived/fiscal.csv" "cf8a1f4e5769901d3f09e85f4925f1b0e89dddf4f2f1b7aae1aebab8e67d98bc"
   "data/derived/jst-credit-gap.csv" "334bac25217589ad7d1f1d9e03e41e4770655bed0bea24e4d58b54da2906e9c4"
   "data/derived/lattice-annual.csv" "6bf028adc82c45e33d7d7d4d7bcd4581092ef8853b1dbbb3ebfd2dfebab6cf03"
   "data/derived/lattice-quarterly.csv" "6d90af49651238dd955526ea825a65796bcc30cf8f4aaaa8ec1d8630173a4923"
   "data/derived/political-annual.csv" "8bc657fcc49bd69d52382a8237ad1f98f2ccb7237aa992a2551b243fd4108591"
   "data/derived/political-long.csv" "06a537c9f442aea6e1010a9ec7614ab44483b4917c5b283474704cad4f877563"
   "data/derived/political-states.csv" "2685bc4ff0ab40c2f88d38b9cd15f8acf809e589521896f972485fd74d13885f"
   "data/raw/slow/jst/jst.csv" "c4e930b520a9b4488620c766292f1e2b55f3bbd67742097a634e5aa54a9c2020"
   "data/raw/slow/kaopen/kaopen.csv" "5b2d58021c0f3874f51730f0fbadba9eeb62d21beff6985abf809f1aeb5dd4dd"
   "data/raw/slow/pwt/pwt110-full.csv" "310920974fb86512c2176110a8b790712d108584ed1ac7035aa94fe2b3040302"
   "data/raw/slow/rr/onsets.csv" "5ed893bdfe01e294aa9d8834b6906ced3b603cb7db9daa51888eb03df10a0a0d"
   "data/runs/r6-at-8ul/preds.tsv" "eb79d8bc568e5081add82c4c27754eefe2ccf5f069695249ffc52717bfcdb9d5"
   "data/slow/coverage.tsv" "c1937d50f6a3ff2d2eb34342e1540ebae2c19cbe6742cce7d4883b861f9e54b3"
   "data/slow/events.tsv" "4405a6d552a2fe549ae67c0b0ead5a88cbef98c0ecd8b139e437617c4ed493c0"
   "data/slow/series.tsv" "2900140fe79f6934f8736ac799df0eec29dd2d5f62b1a59605c90fed00ee7673"
   "data/slow/variables.tsv" "1e2f1de00b9d9ab0855c79210480e69b7617b1c98ed3934d9aace7dbd3ccce99"})

(def code-paths
  "What must be committed and clean before an issue: everything on the
  run's classpath (deps.edn :paths and the tools alias) and deps.edn."
  ["src" "resources" "tools" "test" "deps.edn"])

(def issued
  "Registered issues: {issue {:files {name SHA-256} :role}}. An issue
  enters here in the commit that adds it, and is never edited. :role
  :primary carries the verdicts; :reported is scored by the same rule and
  reported beside, with no verdict (amendment L1). :supplement names a
  file, under registered/ledger/, of baselines computed later from the
  issue's own archived inputs."
  {"2026-Q4" {:files {"forecasts.tsv" "70e31c27c8bf7afe9d20fe4c6befcb3def80e32c19c99fd3b08c0ab0f5d4968d"
                      "manifest.edn" "2eb4b6a5fe1e1fe4227983e8307e028243d24831f2abd9b0e439dc9a3e676d8a"}
              :role :reported}})

(def scanners
  "The scanner's forward rankings written beside an issue (bnm.hazard-run;
  docs/PHASE-CHANGES.md, 11 November 2026, bead bnm-56c.6): {quarter
  {name SHA-256}}. New files in the issue's directory, never edited; the
  issue's own files and hashes (`issued`) are untouched by them."
  {"2026-Q4" {"scanner.tsv" "e7f3bed96c4dd269568d23a1b6f5c5b3dd8eb8f64cf30a1c26d1c416138874cb"
              "scanner.edn" "dd6a4073587fd9d502e7242eb6b1bd8f79411ba7b6c3bf15dcc7f2390314d74b"}})

(def scored
  "Registered scores: {[issue scale kind] SHA-256 of registered/ledger/
  <issue>/scores/<scale>/<kind>.edn}, entered in the commit that adds the
  file. A kind and scale here is never scored again."
  {})

;; --- months and issues ------------------------------------------------------------

(defn quarter-end
  "The last month of quarter \"YYYY-Qn\"."
  [q]
  (let [[_ y n] (re-matches #"(\d{4})-Q([1-4])" (str q))]
    (when-not y (throw (ex-info (str "bnm.ledger: a quarter is YYYY-Qn, not " q) {:quarter q})))
    (+ (* 12 (parse-long y)) (* 3 (parse-long n)) -1)))

(defn issue-quarter
  "The quarter of issue `id`: YYYY-Qn, or YYYY-Qn-rN for a corrected issue."
  [id]
  (let [[_ q] (re-matches #"(\d{4}-Q[1-4])(?:-r[1-9]\d*)?" (str id))]
    (or q (throw (ex-info (str "bnm.ledger: an issue is YYYY-Qn or YYYY-Qn-rN, not " id) {:issue id})))))

(defn- revision [id] (or (some-> (re-find #"-r(\d+)$" (str id)) second parse-long) 0))

(defn previous-issue
  "The issue `id` reproduces: among the registered issues before it (an
  earlier quarter, or an earlier revision of its own), the latest
  quarter's primary issue, else its latest revision; nil for the first."
  [issued id]
  (let [end (quarter-end (issue-quarter id))
        cands (for [[i m] issued :when (not= i id)
                    :let [e (quarter-end (issue-quarter i))]
                    :when (or (< e end) (and (= e end) (< (revision i) (revision id))))]
                [e (if (= :primary (:role m)) 1 0) (revision i) i])]
    (when (seq cands) (peek (last (sort cands))))))

(defn date-month
  "The month of date \"YYYY-MM-DD\"."
  [d]
  (let [[_ y mo dd] (re-matches #"(\d{4})-(\d{2})-(\d{2})" (str d))
        mo (some-> mo parse-long)]
    (when-not (and y (<= 1 mo 12) (<= 1 (parse-long dd) 31))
      (throw (ex-info (str "bnm.ledger: a date is YYYY-MM-DD, not " d) {:date d})))
    (+ (* 12 (parse-long y)) (dec mo))))

(defn check-issue-date
  "The month of `date` when it is in the last two months of `quarter`
  (the registration); refuses otherwise."
  [quarter date]
  (let [end (quarter-end quarter) m (date-month date)]
    (when-not (<= (dec end) m end)
      (throw (ex-info (str "bnm.ledger: " date " is not in the last two months of " quarter "; refusing")
                      {:date date :quarter quarter})))
    m))

(def ^:private scale-of (into {} (map (juxt :name identity) lb/scales)))

(defn latest-origin
  "The scale's last origin at or before month `end` (origins as the
  lattice steps them, from January 1870)."
  [scale end]
  (let [per (:per (scale-of scale)) start wr/start]
    (- end (mod (inc (- end start)) per))))

;; --- the issue's rows ---------------------------------------------------------------

(defn parse-line [line]
  (let [[s place o p y] (str/split line #"\t")]
    {:scale (keyword s) :place place :origin (parse-long o) :p (parse-double p)}))

(defn issue-rows
  "One kind's rows at each scale's latest origin at or before `end`:
  {:kind :scale :place :origin :lo :hi :opened :p}, the window (lo, hi],
  :opened when it opened before the issue month."
  [kind parsed {:keys [end issue-month]}]
  (let [last-at (into {} (for [[s rs] (group-by :scale parsed)]
                           [s (reduce max (keep #(when (<= (:origin %) end) (:origin %)) rs))]))]
    (vec (for [{:keys [scale origin] :as r} parsed
               :when (= origin (last-at scale))
               :let [{:keys [offset horizon]} (scale-of scale)
                     lo (+ origin offset)]]
           (assoc r :kind kind :lo lo :hi (+ lo horizon) :opened (< lo issue-month))))))

(def columns
  "forecasts.tsv's columns. rate and persist from the corrected 2026-Q4
  issue on (amendment L1)."
  ["kind" "scale" "place" "origin" "lo" "hi" "opened" "p" "clim" "last12" "share" "pers" "wclim" "rate" "persist"])

(def ^:private numeric #{:clim :last12 :share :pers :wclim :rate :persist})

(defn- cell [v] (cond (nil? v) "" (keyword? v) (name v) :else (str v)))

(defn rows->tsv
  ([rows] (rows->tsv rows columns))
  ([rows cols]
   (str (str/join "\t" cols) "\n"
        (apply str (for [r rows]
                     (str (str/join "\t" (map #(cell (get r (keyword %))) cols)) "\n"))))))

(defn tsv->rows [text]
  (let [[head & ls] (str/split-lines text)
        ks (map keyword (str/split head #"\t"))
        num (fn [s] (when (seq s) (parse-double s)))]
    (vec (for [l ls :let [m (zipmap ks (concat (str/split l #"\t" -1) (repeat "")))]]
           (reduce (fn [m k] (if (contains? m k) (update m k num) m))
                   (cond-> m
                     (:scale m) (update :scale keyword)
                     (:origin m) (update :origin parse-long)
                     (:lo m) (update :lo parse-long)
                     (:hi m) (update :hi parse-long)
                     (:opened m) (update :opened #(= "true" %))
                     (:p m) (update :p parse-double))
                   numeric)))))

(defn reproduction
  "{:rows :differing}: the base's lines with origins to `through`, and
  how many the run's lines do not hold byte for byte."
  [base-lines run-lines through]
  (let [run (set run-lines)
        bs (filter #(<= (:origin (parse-line %)) through) base-lines)]
    {:rows (count bs) :differing (count (remove run bs))}))

;; --- hashes ----------------------------------------------------------------------

(defn sha256-big
  "SHA-256 of a large file, by the system's shasum (or sha256sum)."
  [path]
  (let [r (sh/sh "shasum" "-a" "256" (str path))
        r (if (zero? (:exit r)) r (sh/sh "sha256sum" (str path)))]
    (when-not (zero? (:exit r)) (throw (ex-info (str "bnm.ledger: cannot hash " path) r)))
    (first (str/split (:out r) #"\s+"))))

(defn check-file
  "`path`'s SHA-256 when it is `expected`; refuses otherwise."
  ([path expected] (check-file path expected sfc/sha256-file))
  ([path expected hash]
   (when-not (.exists (io/file path))
     (throw (ex-info (str path " is missing; refusing") {:path path})))
   (let [got (hash path)]
     (when-not (= got expected)
       (throw (ex-info (str path "'s SHA-256 is " got ", not the registered " expected "; refusing") {:path path :got got})))
     got)))

(defn check-base
  "{kind SHA-256} of the base's forecasts under `dir` when each is the
  pinned one in `pinned`; refuses otherwise."
  [dir pinned]
  (into (sorted-map)
        (for [[k sha] pinned :let [f (str dir "/" k "/forecasts.tsv")]]
          (do (when-not (.exists (io/file f))
                (throw (ex-info (str f " is missing; refusing") {:path f})))
              (let [got (sha256-big f)]
                (when-not (= got sha)
                  (throw (ex-info (str f "'s SHA-256 is " got ", not the base of record's " sha "; refusing") {:path f :got got})))
                [k got])))))

(defn hashes
  "{path SHA-256} of `paths`."
  [paths]
  (into (sorted-map) (for [f paths] [f (sha256-big f)])))

;; --- the archive of an issue's inputs ---------------------------------------------------

(defn check-archive
  "`shas` ({path SHA-256}) when each archived copy, <root>/<path>, has its
  hash; refuses a missing or changed copy."
  [root shas]
  (doseq [[p sha] shas] (check-file (str root "/" p) sha sha256-big))
  shas)

(defn archive!
  "Copies each of `paths` to <root>/<path>, read-only, and returns their
  {path SHA-256}. A copy already there must be the same file: an archive
  is never overwritten."
  [root paths]
  (let [shas (hashes paths)]
    (doseq [[p sha] shas :let [dest (io/file (str root "/" p))]]
      (if (.exists dest)
        (check-file (str dest) sha sha256-big)
        (do (io/make-parents dest)
            (let [r (sh/sh "cp" (str p) (str dest))]
              (when-not (zero? (:exit r)) (throw (ex-info (str "bnm.ledger: cannot archive " p) r))))
            (check-file (str dest) sha sha256-big)
            (.setWritable dest false false))))
    (doseq [f (file-seq (io/file root)) :when (.isDirectory f)]
      (.setWritable f false false))
    shas))

(defn data-changes
  "{path {:old :new}} for each input whose SHA-256 differs between `old`
  and `new` ({path SHA-256}), nil where one lacks it."
  [old new]
  (into (sorted-map)
        (for [p (sort (distinct (concat (keys old) (keys new))))
              :when (not= (get old p) (get new p))]
          [p {:old (get old p) :new (get new p)}])))

;; --- running, recorded --------------------------------------------------------------

(defn- git [& args] (str/trim (:out (apply sh/sh "git" args))))

(defn redirect-to
  "Reads of `paths` go to <root>/<path> instead."
  [root paths]
  (let [ps (set paths)] (fn [p] (when (ps p) (str root "/" p)))))

(defn recording
  "[(f) files-read]: calls `f`, recording every file opened by name
  through io/reader, io/input-stream or slurp (resources, read by URL,
  are code). With `redirect` ((fn [path]) -> another path or nil), a read
  of a path it maps is served from the other path, and recorded under its
  own name."
  ([f] (recording f (constantly nil)))
  ([f redirect]
   (let [opened (atom #{})
         rec (fn [g] (fn [x & more]
                       (let [p (cond (string? x) x (instance? java.io.File x) (str x))
                             to (when p (redirect p))]
                         (when p (swap! opened conj p))
                         (apply g (cond (nil? to) x (string? x) to :else (io/file to)) more))))
         r0 io/reader i0 io/input-stream s0 slurp]
     (alter-var-root #'io/reader rec)
     (alter-var-root #'io/input-stream rec)
     (alter-var-root #'clojure.core/slurp rec)
     (let [v (try (f)
                  (finally (alter-var-root #'io/reader (constantly r0))
                           (alter-var-root #'io/input-stream (constantly i0))
                           (alter-var-root #'clojure.core/slurp (constantly s0))))]
       [v @opened]))))

(defn- line-differences [a b]
  (let [as (str/split-lines a) bs (str/split-lines b)]
    (+ (count (remove (set bs) as)) (Math/abs (- (count as) (count bs))))))

(defn reproduce
  "Reruns an issue on its own archived inputs: checks the archive at
  `root` against `inputs` ({path SHA-256}), calls `run!` with every read
  of an input served from the archive, and refuses unless the text it
  returns is `expected` byte for byte, it read nothing outside the inputs
  (and `allowed`, the run's own output), and the archive is unchanged
  after."
  [{:keys [issue root inputs expected run! allowed]}]
  (check-archive root inputs)
  (let [[text opened] (recording run! (redirect-to root (keys inputs)))
        stray (remove (fn [f] (or (contains? inputs f) (str/starts-with? f root) (and allowed (allowed f))
                                  (not (.exists (io/file f)))))
                      opened)]
    (when (seq stray)
      (throw (ex-info (str "bnm.ledger: reproducing " issue " read files not in its inputs: " (vec stray) "; refusing") {:files stray})))
    (check-archive root inputs)
    (when-not (= text expected)
      (throw (ex-info (str "bnm.ledger: the code does not reproduce " issue " on its archived inputs ("
                           (line-differences expected (str text)) " lines differ); refusing")
                      {:issue issue})))
    {:issue issue :reproduced true :inputs-dir root :lines (count (str/split-lines expected))}))

(def known
  "The bnm.onsets run the base's quarters pool with (bnm.world-run --known):
  r6-at-8ul, refit on the catalogues rebuilt after the gazetteer fix with
  lapsed sources kept (bead bnm-8ul). The first issue, and t48 and t49,
  read r6-at."
  "r6-at-8ul")

(defn command [out end]
  ["--out" out "--without-relations" "true" "--end" (str end) "--known" known])

(defn manifest-args
  "The bnm.world-run arguments of a manifest's :command, its --out
  replaced by `out`: an issue is rerun with its own command, whatever the
  base's command is now."
  [command out]
  (let [[_ rest] (re-matches #"jolt -A:tools -M -m bnm\.world-run (.*)" (str command))
        args (some-> rest (str/split #" "))
        i (.indexOf (vec args) "--out")]
    (when (or (nil? args) (neg? i) (odd? i) (>= (inc i) (count args)))
      (throw (ex-info (str "bnm.ledger: cannot rerun the command " (pr-str command) "; refusing") {:command command})))
    (assoc (vec args) (inc i) out)))

(defn- benchmarks-at
  "{[kind scale place origin] {:clim :last12 :share :rate :persist :pers
  :wclim}} for the issue's country rows, from what was known at the
  origin."
  [dir kind cat at]
  (let [lag (:known-lag cat 0)]
    (into {} (for [[scale rs] (bm/kind-benchmarks dir cat)
                   r rs :when (= (:t r) (at scale))
                   :let [on (get-in cat [:onsets (:place r)] (sorted-set))
                         fr (get-in cat [:from (:place r)])]]
               [[kind scale (:place r) (:t r)]
                {:clim (:clim r) :last12 (:last12 r) :share (:share r) :rate (:rate r) :persist (:persist r)
                 :pers (wl/persistence on lag (:t r))
                 :wclim (when fr (wl/climatology on fr lag (:t r)))}]))))

(defn run-kinds [run-dir]
  (sort (filter #(.exists (io/file (str run-dir "/" % "/forecasts.tsv"))) (keys kinds/tiers))))

(defn build-rows
  "Every kind's issue rows from the run in `run-dir`, with the benchmarks."
  [run-dir cats ks {:keys [end issue-month]}]
  (vec (mapcat (fn [k]
                 (let [parsed (map parse-line (str/split-lines (slurp (str run-dir "/" k "/forecasts.tsv"))))
                       rs (issue-rows k parsed {:end end :issue-month issue-month})
                       at (into {} (for [r rs] [(:scale r) (:origin r)]))
                       bms (benchmarks-at (str run-dir "/" k) k (cats k) at)]
                   (sort-by (juxt #(.indexOf [:quarter-lead :quarter-w2 :year :half-decade] (:scale %)) :place)
                            (map #(merge % (bms [k (:scale %) (:place %) (:origin %)])) rs))))
               ks)))

(defn read-manifest [id] (read-string (slurp (str ledger-dir id "/manifest.edn"))))

(defn check-issue
  "Refuses an issue not registered, or with a file not as registered."
  [id]
  (let [{:keys [files]} (or (issued id)
                            (throw (ex-info (str "bnm.ledger: " id " is not a registered issue; refusing") {:issue id})))]
    (doseq [[n sha] files] (check-file (str ledger-dir id "/" n) sha))
    (let [m (read-manifest id)]
      (doseq [[n sha] (:files m)]
        (when-not (= sha (get files n))
          (throw (ex-info (str "bnm.ledger: the manifest's hash of " n " is not the registered one; refusing") {:file n})))))
    true))

(defn reproduce-issue!
  "Reruns registered issue `prev` on its archived inputs with this code;
  refuses unless its forecasts.tsv comes back byte for byte. Returns the
  summary and the rows, with every benchmark this code computes."
  [prev]
  (check-issue prev)
  (let [m (read-manifest prev)
        f (str ledger-dir prev "/forecasts.tsv")
        expected (slurp f)
        cols (str/split (first (str/split-lines expected)) #"\t")
        run (str "ledger-" prev "-repro")
        run-dir (str "data/runs/" run)
        rows (atom nil)
        _ (println "reproducing" prev "on" (str inputs-root prev))
        r (reproduce {:issue prev :root (str inputs-root prev) :inputs (:inputs m) :expected expected
                      :allowed #(str/starts-with? % run-dir)
                      :run! (fn []
                              (apply wr/-main (manifest-args (:command m) run))
                              (let [rs (build-rows run-dir (kinds/catalogues) (run-kinds run-dir)
                                                   {:end (:end m) :issue-month (:issue-month m)})]
                                (reset! rows rs)
                                (rows->tsv rs cols)))})]
    (assoc r :forecasts (sfc/sha256-file f) :columns cols :rows @rows)))

;; --- issuing ---------------------------------------------------------------------

(def readme
  "# Forward ledger issue %s

Forecasts issued on %s by the frozen base (the world without relations;
base of record %s) for every kind, place and window at each scale's
latest origin at or before %s, from the data on disk at the issue date.
The registration is in docs/PHASE-CHANGES.md (\"Registered, 11 November
2026: the forward ledger\", and its amendment L1). manifest.edn pins the
code commit, the command, every input's SHA-256 (the inputs are archived
read-only in data/ledger-inputs/%s/), the inputs that changed since the
previous issue, and the check that this code reproduces the previous
issue on its own archived inputs.%s

**These files must never be edited, reformatted or regenerated.** Nothing
is refit between issue and outcome. The scorer (tools/bnm/ledger.clj,
`score`) reads them from here and refuses any whose SHA-256 is not the
one registered in bnm.ledger/issued; the test suite hashes them too
(test/bnm/ledger_test.clj). Scores are written once, under scores/.

| file | bytes | SHA-256 |
|---|---|---|
%s
forecasts.tsv: one row per kind, scale and place. origin is a month
(year x 12 + month - 1), the window is (lo, hi] in months, opened is true
when the window opened before the issue date (those rows are for the
record and are not counted). p is the base's probability of an onset in
the window; clim, last12, share, rate and persist are the benchmarks'
probabilities and pers and wclim the watch list's persistence and
climatology scores, all from what was known at the origin (country rows
only).

To check by hand:

    shasum -a 256 registered/ledger/%s/*
")

(defn issue!
  [{:keys [issue quarter date run]}]
  (let [id (or issue quarter)
        quarter (issue-quarter id)
        dir (str ledger-dir id)
        end (quarter-end quarter)
        issue-month (check-issue-date quarter date)
        run (or run (str "ledger-" id))
        run-dir (str "data/runs/" run)
        archive (str inputs-root id)]
    (when (.exists (io/file dir)) (throw (ex-info (str dir " exists; an issue is never redone") {:dir dir})))
    (let [dirty (apply git "status" "--porcelain" "--" code-paths)]
      (when (seq dirty) (throw (ex-info (str "bnm.ledger: commit the code first:\n" dirty) {}))))
    (let [commit (git "rev-parse" "HEAD")
          prev (previous-issue issued id)
          repro (when prev (reproduce-issue! prev))
          _ (when repro (println "reproduced" prev "byte for byte:" (:lines repro) "lines"))
          before (hashes inputs)
          args (command run end)
          _ (println "running the base:" (str/join " " (concat ["jolt -A:tools -M -m bnm.world-run"] args)))
          ;; the run and the catalogues the benchmarks read, both recorded
          [cats opened] (recording (fn [] (apply wr/-main args) (kinds/catalogues)))
          stray (remove (fn [f] (or (some #{f} inputs) (str/starts-with? f run-dir)
                                    (not (.exists (io/file f)))))
                        opened)
          after (hashes inputs)]
      (when (seq stray) (throw (ex-info (str "bnm.ledger: the run read files not in bnm.ledger/inputs: " (vec stray)) {:files stray})))
      (when (not= before after) (throw (ex-info "bnm.ledger: an input changed during the run" {:before before :after after})))
      (let [ks (run-kinds run-dir)
            _ (when (not= (set ks) (set (keys kinds/tiers)))
                (throw (ex-info (str "bnm.ledger: the run's kinds " (vec ks) " are not the base's") {:kinds ks})))
            base-check (if (= before base-inputs)
                         (let [shas (check-base (str "data/runs/" base) base-files)
                               rp (into (sorted-map)
                                        (for [k ks]
                                          [k (reproduction (str/split-lines (slurp (str "data/runs/" base "/" k "/forecasts.tsv")))
                                                           (str/split-lines (slurp (str run-dir "/" k "/forecasts.tsv"))) base-end)]))]
                           (when (some (comp pos? :differing) (vals rp))
                             (throw (ex-info (str "bnm.ledger: the run does not reproduce " base " on its own inputs: " rp) {:reproduction rp})))
                           {:base base :through base-end :kinds rp :base-files shas})
                         {:base base :not-checked "the inputs are not the ones the base was built on"})
            rows (build-rows run-dir cats ks {:end end :issue-month issue-month})
            archived (archive! archive inputs)
            _ (when (not= archived before) (throw (ex-info "bnm.ledger: an input changed while it was archived" {})))
            _ (io/make-parents (str dir "/x"))
            _ (spit (str dir "/forecasts.tsv") (rows->tsv rows))
            ;; baselines the previous issue did not freeze, from its own inputs
            missing (when repro (remove (set (:columns repro)) columns))
            supplement (when (seq missing) (str prev "-baselines.tsv"))
            _ (when supplement
                (spit (str dir "/" supplement)
                      (rows->tsv (:rows repro) (concat ["kind" "scale" "place" "origin"] missing))))
            fsha (sfc/sha256-file (str dir "/forecasts.tsv"))
            files (cond-> {"forecasts.tsv" fsha}
                    supplement (assoc supplement (sfc/sha256-file (str dir "/" supplement))))
            prev-inputs (when prev (:inputs (read-manifest prev)))
            manifest {:issue id :quarter quarter :issued date :base base :model "the world without relations"
                      :code-commit commit
                      :command (str "jolt -A:tools -M -m bnm.world-run " (str/join " " args))
                      :jolt (str/trim (:out (sh/sh "jolt" "--version")))
                      :deps (select-keys (:deps (read-string (slurp "deps.edn"))) ['io.github.jlt-commons/augur 'io.github.jlt-commons/dodeca])
                      :end end :issue-month issue-month
                      :origins (into (sorted-map) (for [s [:quarter-lead :quarter-w2 :year :half-decade]] [s (latest-origin s end)]))
                      :inputs before
                      :inputs-dir archive
                      :previous (when repro (select-keys repro [:issue :reproduced :inputs-dir :lines :forecasts]))
                      :data-changes (when prev (data-changes prev-inputs before))
                      :reproduces base-check
                      :rows (count rows) :kinds (vec ks)
                      :files files
                      :rule "never edited; nothing refit between issue and outcome; scored once"}
            _ (spit (str dir "/manifest.edn") (with-out-str (clojure.pprint/pprint manifest)))
            msha (sfc/sha256-file (str dir "/manifest.edn"))
            size (fn [f] (.length (io/file (str dir "/" f))))
            table (apply str (for [[f sha] (sort (assoc files "manifest.edn" msha))]
                               (format "| %s | %d | %s |\n" f (size f) sha)))
            note (if supplement
                   (format "\n\n%s holds the rate and persistence baselines for issue %s's rows,\ncomputed from %s's archived inputs by the run that reproduced it." supplement prev prev)
                   "")]
        (spit (str dir "/README.md") (format readme id date base quarter id note table id))
        (println "wrote" dir (count rows) "rows;" (size "forecasts.tsv") "bytes")
        (when prev (println "data changed since" prev ":" (count (:data-changes manifest)) "inputs"))
        (println "register in bnm.ledger/issued:")
        (prn {id {:files (assoc files "manifest.edn" msha) :role :primary}})
        {:dir dir :rows (count rows) :forecasts fsha :manifest msha :files files}))))

;; --- scoring ---------------------------------------------------------------------

(defn published?
  "Whether the windows of `rows` ({:place :origin :hi}, one kind and
  scale) are published by month `now`: they have closed, and the
  catalogue covers the window's end at every place it follows past the
  origin (covered through a month at or after it). Places it stopped
  following before the origin are left out (amendment L1)."
  [cat rows now]
  (let [through (:through cat)
        followed (filter #(when-let [t (get through (:place %))] (>= t (:origin %))) rows)]
    (boolean (and (seq rows) (every? #(< (:hi %) now) rows) (seq followed)
                  (every? #(>= (get through (:place %)) (:hi %)) followed)))))

(defn check-kinds
  "The kinds to score: `requested`, or `all` the issue holds; refuses a
  kind the issue does not hold."
  [requested all]
  (let [ks (or (seq requested) all)
        missing (remove (set all) ks)]
    (when (seq missing)
      (throw (ex-info (str "bnm.ledger: " (vec missing) " not in the issue, which holds " (vec all) "; refusing") {:kinds missing})))
    (vec ks)))

(defn ever-committed?
  "Whether `path` was ever committed in the git repository at `repo`."
  [repo path]
  (boolean (seq (str/trim (:out (sh/sh "git" "-C" (str repo) "log" "--all" "--format=%H" "--" path))))))

(defn check-unscored
  "`kinds` when none is scored yet at `scale` of `issue`; refuses one
  registered in `scored`, with its score file on disk, or with the file
  ever committed (so deleting it does not allow a rescore)."
  [{:keys [scored issue scale kinds path-of repo]}]
  (let [done (filter (fn [k] (or (contains? scored [issue scale k])
                                 (.exists (io/file (str repo "/" (path-of k))))
                                 (ever-committed? repo (path-of k))))
                     kinds)]
    (when (seq done)
      (throw (ex-info (str "bnm.ledger: already scored " (vec done) " (registered, on disk or committed once); an issue is scored once")
                      {:kinds done})))
    (vec kinds)))

(def sources [:p :pers :wclim :last12])
(def benches [:clim :last12 :share :rate :persist])

(defn score-cell
  "One kind and scale's scored rows ({:place :y :hard :p ...}): n,
  onsets, mean losses, skill against each benchmark on the rows it has,
  and top-`k` recall for each ranking source, hard and all rows."
  [rows {:keys [k]}]
  (let [mean (fn [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))
        loss-of (fn [loss key] (mean (for [r rows :when (some? (key r))] (loss (double (key r)) (:y r)))))
        recall (fn [rs s] (let [{:keys [hits pos]} (wl/cell-hits (map s rs) (map :y rs) k)]
                            (when (pos? pos) (/ hits pos))))]
    {:n (count rows)
     :onsets (count (filter :y rows))
     :n-of (into {} (for [b benches] [b (count (filter #(some? (b %)) rows))]))
     :brier (into {} (for [s (cons :p benches)] [s (loss-of bm/brier s)]))
     :log (into {} (for [s (cons :p benches)] [s (loss-of bm/log-loss s)]))
     :skill (into {} (for [[nm loss] [[:brier bm/brier] [:log bm/log-loss]]]
                       [nm (into {} (for [b benches] [b (bm/skill rows :p b loss)]))]))
     :recall (let [hard (filterv :hard rows)]
               {:hard (into {} (for [s sources] [s (recall hard s)]))
                :all (into {} (for [s sources] [s (recall rows s)]))})}))

(defn- today-month []
  (date-month (str/trim (:out (sh/sh "date" "+%Y-%m-%d")))))

(defn issue-forecasts
  "Issue `id`'s rows, with the baselines of its :supplement merged in."
  [id]
  (let [rows (tsv->rows (slurp (str ledger-dir id "/forecasts.tsv")))
        sup (some-> (:supplement (issued id)) (#(tsv->rows (slurp (str ledger-dir %)))))
        by (into {} (for [r sup] [[(:kind r) (:scale r) (:place r) (:origin r)] (dissoc r :kind :scale :place :origin)]))]
    (mapv #(merge % (by [(:kind %) (:scale %) (:place %) (:origin %)])) rows)))

(def row-keys [:place :origin :lo :hi :p :y :hard :clim :last12 :share :rate :persist :pers :wclim])

(defn score!
  [{:keys [issue quarter scale kinds k]}]
  (let [id (or issue quarter)
        _ (check-issue id)
        scale (keyword scale)
        k (or k 10)
        dir (str ledger-dir id)
        rows (filterv #(and (= scale (:scale %)) (= 3 (count (:place %))) (not (:opened %))) (issue-forecasts id))
        _ (when (empty? rows) (throw (ex-info (str "bnm.ledger: " id " has no counted " (name scale) " rows") {})))
        all-ks (sort (distinct (map :kind rows)))
        ks (check-kinds (some-> kinds (str/split #",")) all-ks)
        rel (fn [kd] (str dir "/scores/" (name scale) "/" kd ".edn"))
        _ (check-unscored {:scored scored :issue id :scale scale :kinds ks :path-of rel :repo "."})
        cats (kinds/catalogues)
        now (today-month)
        by-kind (group-by :kind rows)
        unpublished (remove #(published? (cats %) (by-kind %) now) ks)]
    (when (seq unpublished)
      (throw (ex-info (str "bnm.ledger: outcomes not yet published for " (vec unpublished) "; refusing") {:kinds unpublished})))
    (doseq [kd ks
            :let [cat (cats kd)
                  scored-rows (vec (keep (fn [{:keys [place origin lo hi] :as r}]
                                           (let [y (#'lb/onset-in? cat place origin lo hi)]
                                             (when (some? y)
                                               (assoc r :y y :hard (wl/hard? (get-in cat [:onsets place] (sorted-set)) origin)))))
                                         (by-kind kd)))
                  res (assoc (score-cell scored-rows {:k k}) :kind kd :scale scale :issue id :scored-month now :k k
                             :unscored (- (count (by-kind kd)) (count scored-rows))
                             :rows (mapv #(select-keys % row-keys) scored-rows))]]
      (io/make-parents (rel kd))
      (spit (rel kd) (with-out-str (clojure.pprint/pprint res)))
      (println kd (select-keys res [:n :onsets :unscored :skill :recall]))
      (println "register in bnm.ledger/scored:" (pr-str {[id scale kd] (sfc/sha256-file (rel kd))})))
    (when (every? #(.exists (io/file (rel %))) all-ks)
      (let [rs (map #(read-string (slurp (rel %))) all-ks)
            mean (fn [xs] (let [xs (remove nil? xs)] (when (seq xs) (/ (reduce + xs) (double (count xs))))))
            f (fn [x] (if x (format "%+.4f" (double x)) "  -  "))
            sk (fn [loss] (str/join " / " (for [b benches] (f (mean (map #(get-in % [:skill loss b]) rs))))))
            lines (concat [(format "Issue %s, %s nodes, %d kinds weighed equally, scored at month %d." id (name scale) (count rs) now)
                           (str "Brier skill against " (str/join " / " (map name benches)) ": " (sk :brier))
                           (str "Log skill against " (str/join " / " (map name benches)) ": " (sk :log))]
                          (when (= :year scale)
                            (for [sample [:hard :all]]
                              (format "Top-%d recall, %s rows: base %s  pers %s  clim %s  last12 %s" k (name sample)
                                      (f (mean (map #(get-in % [:recall sample :p]) rs))) (f (mean (map #(get-in % [:recall sample :pers]) rs)))
                                      (f (mean (map #(get-in % [:recall sample :wclim]) rs))) (f (mean (map #(get-in % [:recall sample :last12]) rs)))))))]
        (spit (str dir "/scores/" (name scale) "/pooled.txt") (str (str/join "\n" lines) "\n"))
        (doseq [l lines] (println l))))))

;; --- the claim (amendment L1) ---------------------------------------------------------

(def baselines "The claim's baselines." [:rate :persist :last12])
(def minimum-issues "Year-node issues scored before any claim." 3)

(defn pooled-skill
  "1 - loss(:p) / loss(`bench`) per kind of `cells` ({kind [rows]}), on
  the rows with both, the kinds weighed equally."
  [cells bench loss]
  (let [ss (keep (fn [[_ rows]] (bm/skill rows :p bench loss)) cells)]
    (when (seq ss) (/ (reduce + ss) (double (count ss))))))

(defn- loss-sums
  "[countries {kind [base bench]}]: per kind, each country's summed losses
  over every issue in `issues` ([{kind [rows]}])."
  [issues bench loss]
  (let [by-kind (apply merge-with into issues)
        cs (vec (sort (distinct (for [[_ rs] by-kind r rs] (:place r)))))
        idx (zipmap cs (range))
        n (count cs)]
    [cs (into {} (for [[kd rs] by-kind]
                   (let [a (double-array n) b (double-array n)]
                     (doseq [r rs :when (and (some? (:p r)) (some? (bench r)) (boolean? (:y r)))
                             :let [i (idx (:place r))]]
                       (aset a i (+ (aget a i) (double (loss (double (:p r)) (:y r)))))
                       (aset b i (+ (aget b i) (double (loss (double (bench r)) (:y r))))))
                     [kd [a b]])))]))

(defn- skill-of
  "The kinds' mean skill with country weights `w`; nil with no kind."
  [sums w]
  (let [n (alength w)
        ss (keep (fn [[_ [a b]]]
                   (loop [i 0 la 0.0 lb 0.0]
                     (if (< i n)
                       (let [wi (aget w i)] (recur (inc i) (+ la (* wi (aget a i))) (+ lb (* wi (aget b i)))))
                       (when (pos? lb) (- 1.0 (/ la lb))))))
                 sums)]
    (when (seq ss) (/ (reduce + ss) (double (count ss))))))

(defn interval
  "{:skill :lo :hi}: the pooled skill of :p against `bench` over `issues`
  ([{kind [rows]}]), and its 90% country-cluster bootstrap interval: each
  draw resamples countries with replacement and weighs every row of a
  drawn country, in every issue and kind, by how often it was drawn."
  [issues bench loss {:keys [draws seed]}]
  (let [[cs sums] (loss-sums issues bench loss)
        n (count cs)
        point (skill-of sums (double-array n 1.0))
        rng (java.util.Random. (long seed))
        ds (vec (sort (keep (fn [_]
                              (let [w (double-array n)]
                                (dotimes [_ n] (let [j (.nextInt rng n)] (aset w j (+ 1.0 (aget w j)))))
                                (skill-of sums w)))
                            (range draws))))
        m (count ds)
        q (fn [p] (when (pos? m) (nth ds (min (dec m) (max 0 (long (Math/floor (* p m))))))))]
    {:skill point :lo (q 0.05) :hi (q 0.95)}))

(defn verdict [{:keys [lo hi]}]
  (cond (and lo (pos? lo)) :better (and hi (neg? hi)) :worse :else :undecided))

(defn claim
  "The claim over `issues` ([{kind [scored rows]}], year nodes): Brier and
  log skill against each baseline with its interval and verdict;
  :claimed when there are at least `minimum-issues` and the Brier lower
  end is above 0 against every baseline."
  [issues {:keys [draws seed] :or {draws 2000 seed 1}}]
  (let [res (into {} (for [[nm loss] [[:brier bm/brier] [:log bm/log-loss]]]
                       [nm (into {} (for [b baselines]
                                      (let [iv (interval issues b loss {:draws draws :seed seed})]
                                        [b (assoc iv :verdict (verdict iv))])))]))]
    (assoc res
           :issues (count issues)
           :claimed (boolean (and (>= (count issues) minimum-issues)
                                  (every? #(= :better (get-in res [:brier % :verdict])) baselines))))))

(defn null-claim-rate
  "The interval's size: the share of `sims` outcome sets, each row's y
  drawn from the mean of :p and `bench` (equal expected Brier), on which
  it claims a gain against `bench` (lower end above 0)."
  [issues bench {:keys [sims draws seed]}]
  (let [rng (java.util.Random. (long seed))
        draw (fn [r] (assoc r :y (when (and (some? (:p r)) (some? (bench r)))
                                   (< (.nextDouble rng) (/ (+ (double (:p r)) (double (bench r))) 2.0)))))
        hits (count (filter true?
                            (for [s (range sims)]
                              (let [drawn (mapv (fn [cells] (update-vals cells #(mapv draw %))) issues)
                                    {:keys [lo]} (interval drawn bench bm/brier {:draws draws :seed (+ seed s 1)})]
                                (boolean (and lo (pos? lo)))))))]
    (/ hits (double sims))))

(defn scored-issues
  "{issue [{kind rows}]} of the registered scores at `scale`."
  [scale]
  (let [by (group-by first (for [[[id s kd] _] scored :when (= s scale)] [id kd]))]
    (into (sorted-map)
          (for [[id pairs] by]
            [id (into {} (for [[_ kd] pairs]
                           [kd (:rows (read-string (slurp (str ledger-dir id "/scores/" (name scale) "/" kd ".edn"))))]))]))))

(defn claim!
  [{:keys [scale draws]}]
  (let [scale (keyword (or scale "year"))
        draws (or (some-> draws parse-long) 2000)
        by (scored-issues scale)
        role #(:role (issued %))
        f (fn [x] (if x (format "%+.4f" (double x)) "  -  "))
        show (fn [title ids]
               (let [c (claim (mapv by ids) {:draws draws :seed 1})]
                 (println title (vec ids))
                 (doseq [nm [:brier :log] b baselines :let [{:keys [skill lo hi verdict]} (get-in c [nm b])]]
                   (println (format "  %-5s skill against %-7s %s [%s %s] %s" (name nm) (name b) (f skill) (f lo) (f hi) (name verdict))))
                 c))]
    (let [primary (filter #(= :primary (role %)) (keys by))
          c (show (str "Primary issues scored at the " (name scale) " node (" (count primary) "; a claim needs " minimum-issues "):") primary)]
      (println (if (:claimed c) "Claimed: the base beats rate, persistence and last12 on Brier."
                   "No claim: reported as it falls."))
      (doseq [id (filter #(= :reported (role %)) (keys by))]
        (show (str "Reported, no verdict:") [id])))))

(defn size-check!
  "The interval's size on development data (amendment L1): the base's
  year nodes at year-end origins 2016, 2018 and 2020 as three issues."
  [{:keys [run sims draws]}]
  (let [run (or run base)
        sims (or (some-> sims parse-long) 1000)
        draws (or (some-> draws parse-long) 500)
        cats (kinds/catalogues)
        origins [(+ (* 12 2016) 11) (+ (* 12 2018) 11) (+ (* 12 2020) 11)]
        ks (run-kinds (str "data/runs/" run))
        rows (into {} (pmap (fn [kd] [kd (filterv #(boolean? (:y %))
                                                  (get (bm/kind-benchmarks (str "data/runs/" run "/" kd) (cats kd)) :year))])
                            ks))
        issues (mapv (fn [o] (into {} (for [kd ks] [kd (filterv #(= o (:t %)) (rows kd))]))) origins)
        out (StringBuilder.)
        say (fn [s] (println s) (.append out (str s "\n")))]
    (say (format "Size check of the ledger's claim interval (amendment L1) on %s, year nodes, origins 2016, 2018, 2020 as three issues;" run))
    (say (format "%d kinds, %d country rows; y drawn from the mean of the base's and the baseline's p; %d sets, %d draws each, 90%% interval."
                 (count ks) (reduce + (for [i issues [_ rs] i] (count rs))) sims draws))
    (doseq [b baselines]
      (let [r (null-claim-rate issues b {:sims sims :draws draws :seed 1})]
        (say (format "  against %-7s claims a gain in %.3f of sets (nominal 0.05, limit 0.075): %s" (name b) r
                     (if (<= r 0.075) "passes" "FAILS")))))
    (spit (str "data/runs/" run "/ledger-size-check.txt") (str out))))

(defn -main [& args]
  (let [[cmd & more] args
        opts (into {} (for [[k v] (partition 2 more)] [(keyword (subs k 2)) v]))]
    (case cmd
      "issue" (issue! (update opts :date #(or % (str/trim (:out (sh/sh "date" "+%Y-%m-%d"))))))
      "score" (score! (update opts :k #(some-> % parse-long)))
      "claim" (claim! opts)
      "size-check" (size-check! opts)
      (throw (ex-info "bnm.ledger: issue, score, claim or size-check" {:args args})))
    (shutdown-agents)))
