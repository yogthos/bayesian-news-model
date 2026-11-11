(ns bnm.ledger
  "The forward ledger (docs/PLAN.md 0.1; the registration in
  docs/PHASE-CHANGES.md, 11 November 2026). Each quarter the frozen base
  (t48-world-norel: the world without relations) issues every kind, place
  and window at the quarter's last month, from the data on disk at the
  issue date, to registered/ledger/<quarter>/, with its hashes; nothing is
  refit between issue and outcome, and an issue is never edited. Later,
  when a window's outcomes are published, the issue is scored once.

    jolt -A:tools -M -m bnm.ledger issue --quarter 2026-Q4 [--date 2026-11-11]
    jolt -A:tools -M -m bnm.ledger score --quarter 2026-Q4 --scale year [--kinds a,b]

  issue refuses a date outside the quarter's last two months, and unless
  the code (`code-paths`: src, resources, tools, test, deps.edn) is
  committed and clean; runs the base's command (`command`) in this
  process and reads the catalogues, recording every file read, and
  refuses if it reads one not in `inputs` or one changes during the run;
  refuses unless the base of record's files are the pinned ones
  (`base-files`) and the run reproduces them on every row they have
  (origins to `base-end`); then writes
  forecasts.tsv (every row at each scale's latest origin, with the
  benchmarks frozen beside it), manifest.edn and README.md, and prints the
  hashes to register in `issued`.

  score refuses an issue not in `issued` or whose files' SHA-256s are not
  the registered ones; refuses a window whose outcomes are not published
  (the kind's catalogue covers the window's end, and the window has
  closed by today) or already scored; scores the issue's country rows that
  are at risk and covered (bnm.lattice-banking/onset-in?, as the base is
  scored): Brier and log loss and skill against the frozen benchmarks
  (bnm.benchmarks), and at the year node the watch-list measure (top-10
  recall on hard onsets, bnm.watchlist) for the base, persistence,
  climatology and the last 12 months. Writes scores/<scale>/<kind>.edn
  and, when every kind of the scale is scored, scores/<scale>/pooled.txt."
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

(def base "t48-world-norel")
(def base-end "The base of record's last origin, December 2025." (+ (* 12 2025) 11))
(def ledger-dir "registered/ledger/")

(def inputs
  "Every file the base reads (recorded by running it, 11 November 2026)."
  ["data/derived/econ-coverage.csv" "data/derived/econ-events.csv" "data/derived/fiscal.csv"
   "data/derived/jst-credit-gap.csv" "data/derived/lattice-annual.csv" "data/derived/lattice-quarterly.csv"
   "data/derived/political-annual.csv" "data/derived/political-long.csv" "data/derived/political-states.csv"
   "data/raw/slow/jst/jst.csv" "data/raw/slow/kaopen/kaopen.csv" "data/raw/slow/pwt/pwt110-full.csv"
   "data/raw/slow/rr/onsets.csv" "data/runs/r6-at/preds.tsv" "data/slow/coverage.tsv" "data/slow/events.tsv"
   "data/slow/series.tsv" "data/slow/variables.tsv"])

(def base-files
  "The base of record's forecasts, data/runs/t48-world-norel/<kind>/
  forecasts.tsv (written 28 October 2026), by SHA-256: the reproduction
  check compares against these files and refuses others."
  {"banking_crisis" "1b1cf77e2b849d3ce2edce7fad416532bd3b83f0ef681d27d8af4be7aa771d62"
   "conflict_onset" "3ffe7bb8fb1773f6134b10612440084b2f8b3da7e87a43c1f2970653027b844d"
   "coup" "dce6322793f0566e211426647d8aa2a99ed46d156d152c51dcaa92bcee895af4"
   "currency_crisis" "e4f685eb2108449a8742b7d59bd506f2b58cf04f8d68b6dac629b8e8d546d99c"
   "fiscal_consolidation" "d2f9e81b90ab9305a2bcff2311d96b291014e0a0d94b8322a81b94b333fad664"
   "food_price_shock" "655695430c0311b39b98da5d157c0f39e8d5f93ef8eed4702c53cbcea3ce98bc"
   "growth_collapse" "5082166dfa74cee558ec3481c6bd9f2573698b55bcc815b3b92a8aaa49dc3fd3"
   "inflation_crisis" "ec8e9f8213ee6729b8be7bca3d77daafb9e8c97adc62f11647f532e94908e3d5"
   "market_crash" "37c23cb3f6e1b8d3d8bc8b9f23ec86c3181747d7bc79c68d29f878e0bb309305"
   "protest_surge" "3e31c22eb51b0942f943b541c34caa7bc26c80f18ae2755d12b423f033e054b1"
   "sovereign_default" "a99f51ba0eee65a63a2669434209fb1dcf0191f80c1769824131b0e8d23555ff"
   "violence_surge" "ba4208623e7ee55f34cc4b2162954236fffa78511ecdf58a3fa2b84999b1a6bf"
   "war_onset" "58a13eb387ff741f3296bc87a18ad77c4f920e6fb9c2c5c2a2bed7727364811f"})

(def code-paths
  "What must be committed and clean before an issue: everything on the
  run's classpath (deps.edn :paths and the tools alias) and deps.edn."
  ["src" "resources" "tools" "test" "deps.edn"])

(def issued
  "Registered issues: {quarter {:files {name SHA-256}}}. An issue enters
  here in the commit that adds it, and is never edited."
  {"2026-Q4" {:files {"forecasts.tsv" "70e31c27c8bf7afe9d20fe4c6befcb3def80e32c19c99fd3b08c0ab0f5d4968d"
                      "manifest.edn" "2eb4b6a5fe1e1fe4227983e8307e028243d24831f2abd9b0e439dc9a3e676d8a"}}})

(def scanners
  "The scanner's forward rankings written beside an issue (bnm.hazard-run;
  docs/PHASE-CHANGES.md, 11 November 2026, bead bnm-56c.6): {quarter
  {name SHA-256}}. New files in the issue's directory, never edited; the
  issue's own files and hashes (`issued`) are untouched by them."
  {"2026-Q4" {"scanner.tsv" "e7f3bed96c4dd269568d23a1b6f5c5b3dd8eb8f64cf30a1c26d1c416138874cb"
              "scanner.edn" "dd6a4073587fd9d502e7242eb6b1bd8f79411ba7b6c3bf15dcc7f2390314d74b"}})

;; --- months -------------------------------------------------------------------

(defn quarter-end
  "The last month of quarter \"YYYY-Qn\"."
  [q]
  (let [[_ y n] (re-matches #"(\d{4})-Q([1-4])" (str q))]
    (when-not y (throw (ex-info (str "bnm.ledger: a quarter is YYYY-Qn, not " q) {:quarter q})))
    (+ (* 12 (parse-long y)) (* 3 (parse-long n)) -1)))

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

(def columns ["kind" "scale" "place" "origin" "lo" "hi" "opened" "p" "clim" "last12" "share" "pers" "wclim"])

(defn- cell [v] (cond (nil? v) "" (keyword? v) (name v) :else (str v)))

(defn rows->tsv [rows]
  (str (str/join "\t" columns) "\n"
       (apply str (for [r rows]
                    (str (str/join "\t" (map #(cell (get r (keyword %))) columns)) "\n")))))

(defn tsv->rows [text]
  (let [[head & ls] (str/split-lines text)
        ks (map keyword (str/split head #"\t"))
        num (fn [s] (when (seq s) (parse-double s)))]
    (vec (for [l ls :let [m (zipmap ks (concat (str/split l #"\t" -1) (repeat "")))]]
           (-> m
               (update :scale keyword)
               (update :origin parse-long) (update :lo parse-long) (update :hi parse-long)
               (update :opened #(= "true" %))
               (update :p parse-double)
               (update :clim num) (update :last12 num) (update :share num) (update :pers num) (update :wclim num))))))

(defn reproduction
  "{:rows :differing}: the base's lines with origins to `through`, and
  how many the run's lines do not hold byte for byte."
  [base-lines run-lines through]
  (let [run (set run-lines)
        bs (filter #(<= (:origin (parse-line %)) through) base-lines)]
    {:rows (count bs) :differing (count (remove run bs))}))

;; --- hashes ----------------------------------------------------------------------

(defn check-file
  "`path`'s SHA-256 when it is `expected`; refuses otherwise."
  [path expected]
  (when-not (.exists (io/file path))
    (throw (ex-info (str path " is missing; refusing") {:path path})))
  (let [got (sfc/sha256-file path)]
    (when-not (= got expected)
      (throw (ex-info (str path "'s SHA-256 is " got ", not the registered " expected "; refusing") {:path path :got got})))
    got))

(defn sha256-big
  "SHA-256 of a large file, by the system's shasum (or sha256sum)."
  [path]
  (let [r (sh/sh "shasum" "-a" "256" path)
        r (if (zero? (:exit r)) r (sh/sh "sha256sum" path))]
    (when-not (zero? (:exit r)) (throw (ex-info (str "bnm.ledger: cannot hash " path) r)))
    (first (str/split (:out r) #"\s+"))))

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

;; --- issuing ---------------------------------------------------------------------

(defn command [out end]
  ["--out" out "--without-relations" "true" "--end" (str end)])

(defn- git [& args] (str/trim (:out (apply sh/sh "git" args))))

(defn recording
  "[(f) files-read]: calls `f`, recording every file opened by name
  through io/reader, io/input-stream or slurp (resources, read by URL,
  are code)."
  [f]
  (let [opened (atom #{})
        rec (fn [g] (fn [x & more] (when (or (string? x) (instance? java.io.File x)) (swap! opened conj (str x))) (apply g x more)))
        r0 io/reader i0 io/input-stream s0 slurp]
    (alter-var-root #'io/reader rec)
    (alter-var-root #'io/input-stream rec)
    (alter-var-root #'clojure.core/slurp rec)
    (let [v (try (f)
                 (finally (alter-var-root #'io/reader (constantly r0))
                          (alter-var-root #'io/input-stream (constantly i0))
                          (alter-var-root #'clojure.core/slurp (constantly s0))))]
      [v @opened])))

(defn- benchmarks-at
  "{[kind scale place origin] {:clim :last12 :share :pers :wclim}} for
  the issue's country rows, from what was known at the origin."
  [dir kind cat at]
  (let [lag (:known-lag cat 0)]
    (into {} (for [[scale rs] (bm/kind-benchmarks dir cat)
                   r rs :when (= (:t r) (at scale))
                   :let [on (get-in cat [:onsets (:place r)] (sorted-set))
                         fr (get-in cat [:from (:place r)])]]
               [[kind scale (:place r) (:t r)]
                {:clim (:clim r) :last12 (:last12 r) :share (:share r)
                 :pers (wl/persistence on lag (:t r))
                 :wclim (when fr (wl/climatology on fr lag (:t r)))}]))))

(def readme
  "# Forward ledger issue %s

Forecasts issued on %s by the frozen base (%s, the world without
relations) for every kind, place and window at each scale's latest origin
at or before %s, from the data on disk at the issue date. The registration
is in docs/PHASE-CHANGES.md (\"Registered, 11 November 2026: the forward
ledger\"). manifest.edn pins the code commit, the command, every input's
SHA-256 and the check that the run reproduces the base of record.

**These files must never be edited, reformatted or regenerated.** Nothing
is refit between issue and outcome. The scorer (tools/bnm/ledger.clj,
`score`) reads them from here and refuses any whose SHA-256 is not the
one registered in bnm.ledger/issued; the test suite hashes them too
(test/bnm/ledger_test.clj). Scores are written once, under scores/.

| file | bytes | SHA-256 |
|---|---|---|
| forecasts.tsv | %d | %s |
| manifest.edn | %d | %s |

forecasts.tsv: one row per kind, scale and place. origin is a month
(year x 12 + month - 1), the window is (lo, hi] in months, opened is true
when the window opened before the issue date (those rows are for the
record and are not counted). p is the base's probability of an onset in
the window; clim, last12 and share are the benchmarks' probabilities and
pers and wclim the watch list's persistence and climatology scores, all
from what was known at the origin (country rows only).

To check by hand:

    shasum -a 256 registered/ledger/%s/*
")

(defn issue!
  [{:keys [quarter date run]}]
  (let [dir (str ledger-dir quarter)
        end (quarter-end quarter)
        issue-month (check-issue-date quarter date)
        run (or run (str "ledger-" quarter))
        run-dir (str "data/runs/" run)]
    (when (.exists (io/file dir)) (throw (ex-info (str dir " exists; an issue is never redone") {:dir dir})))
    (let [dirty (apply git "status" "--porcelain" "--" code-paths)]
      (when (seq dirty) (throw (ex-info (str "bnm.ledger: commit the code first:\n" dirty) {}))))
    (let [base-shas (check-base (str "data/runs/" base) base-files)
          commit (git "rev-parse" "HEAD")
          before (into (sorted-map) (for [f inputs] [f (sha256-big f)]))
          args (command run end)
          _ (println "running the base:" (str/join " " (concat ["jolt -A:tools -M -m bnm.world-run"] args)))
          ;; the run and the catalogues the benchmarks read, both recorded
          [cats opened] (recording (fn [] (apply wr/-main args) (kinds/catalogues)))
          stray (remove (fn [f] (or (some #{f} inputs) (str/starts-with? f run-dir)
                                    (not (.exists (io/file f)))))
                        opened)
          after (into (sorted-map) (for [f inputs] [f (sha256-big f)]))]
      (when (seq stray) (throw (ex-info (str "bnm.ledger: the run read files not in bnm.ledger/inputs: " (vec stray)) {:files stray})))
      (when (not= before after) (throw (ex-info "bnm.ledger: an input changed during the run" {:before before :after after})))
      (let [ks (sort (filter #(.exists (io/file (str run-dir "/" % "/forecasts.tsv"))) (keys kinds/tiers)))
            repro (into (sorted-map)
                        (for [k ks]
                          [k (reproduction (str/split-lines (slurp (str "data/runs/" base "/" k "/forecasts.tsv")))
                                           (str/split-lines (slurp (str run-dir "/" k "/forecasts.tsv"))) base-end)]))
            _ (when (not= (set ks) (set (keys base-files)))
                (throw (ex-info (str "bnm.ledger: the run's kinds " (vec ks) " are not the base's") {:kinds ks})))
            _ (when (some (comp pos? :differing) (vals repro))
                (throw (ex-info (str "bnm.ledger: the run does not reproduce " base ": " repro) {:reproduction repro})))
            rows (vec (mapcat (fn [k]
                                (let [parsed (map parse-line (str/split-lines (slurp (str run-dir "/" k "/forecasts.tsv"))))
                                      rs (issue-rows k parsed {:end end :issue-month issue-month})
                                      at (into {} (for [r rs] [(:scale r) (:origin r)]))
                                      bms (benchmarks-at (str run-dir "/" k) k (cats k) at)]
                                  (sort-by (juxt #(.indexOf [:quarter-lead :quarter-w2 :year :half-decade] (:scale %)) :place)
                                           (map #(merge % (bms [k (:scale %) (:place %) (:origin %)])) rs))))
                              ks))
            text (rows->tsv rows)
            _ (io/make-parents (str dir "/x"))
            _ (spit (str dir "/forecasts.tsv") text)
            fsha (sfc/sha256-file (str dir "/forecasts.tsv"))
            manifest {:issue quarter :issued date :base base :model "the world without relations"
                      :code-commit commit
                      :command (str "jolt -A:tools -M -m bnm.world-run " (str/join " " args))
                      :jolt (str/trim (:out (sh/sh "jolt" "--version")))
                      :deps (select-keys (:deps (read-string (slurp "deps.edn"))) ['io.github.jlt-commons/augur 'io.github.jlt-commons/dodeca])
                      :end end :issue-month issue-month
                      :origins (into (sorted-map) (for [s [:quarter-lead :quarter-w2 :year :half-decade]] [s (latest-origin s end)]))
                      :inputs before
                      :reproduces {:base base :through base-end :kinds repro :base-files base-shas}
                      :rows (count rows) :kinds (vec ks)
                      :files {"forecasts.tsv" fsha}
                      :rule "never edited; nothing refit between issue and outcome; scored once"}
            _ (spit (str dir "/manifest.edn") (with-out-str (clojure.pprint/pprint manifest)))
            msha (sfc/sha256-file (str dir "/manifest.edn"))
            size (fn [f] (.length (io/file (str dir "/" f))))]
        (spit (str dir "/README.md") (format readme quarter date base quarter (size "forecasts.tsv") fsha (size "manifest.edn") msha quarter))
        (println "wrote" dir (count rows) "rows;" (size "forecasts.tsv") "bytes")
        (println "register in bnm.ledger/issued:")
        (prn {quarter {:files {"forecasts.tsv" fsha "manifest.edn" msha}}})
        {:dir dir :rows (count rows) :forecasts fsha :manifest msha}))))

;; --- scoring ---------------------------------------------------------------------

(defn published?
  "Whether a window ending at `hi` is published by month `now`: it has
  closed, and the catalogue covers it at some place."
  [cat hi now]
  (boolean (and (< hi now) (some #(>= % hi) (vals (:through cat))))))

(def sources [:p :pers :wclim :last12])
(def benches [:clim :last12 :share])

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

(defn check-issue
  "Refuses an issue not registered, or with a file not as registered."
  [quarter]
  (let [{:keys [files]} (or (issued quarter)
                            (throw (ex-info (str "bnm.ledger: " quarter " is not a registered issue; refusing") {:quarter quarter})))]
    (doseq [[n sha] files] (check-file (str ledger-dir quarter "/" n) sha))
    (let [m (read-string (slurp (str ledger-dir quarter "/manifest.edn")))]
      (doseq [[n sha] (:files m)]
        (when-not (= sha (get files n))
          (throw (ex-info (str "bnm.ledger: the manifest's hash of " n " is not the registered one; refusing") {:file n})))))
    true))

(defn- today-month []
  (date-month (str/trim (:out (sh/sh "date" "+%Y-%m-%d")))))

(defn score!
  [{:keys [quarter scale kinds k]}]
  (check-issue quarter)
  (let [scale (keyword scale)
        k (or k 10)
        dir (str ledger-dir quarter)
        rows (filterv #(and (= scale (:scale %)) (= 3 (count (:place %))) (not (:opened %)))
                      (tsv->rows (slurp (str dir "/forecasts.tsv"))))
        all-ks (sort (distinct (map :kind rows)))
        ks (if kinds (str/split kinds #",") all-ks)
        cats (kinds/catalogues)
        now (today-month)
        out-of (fn [kd] (str dir "/scores/" (name scale) "/" kd ".edn"))
        by-kind (group-by :kind rows)
        unpublished (remove #(let [rs (by-kind %)] (and (seq rs) (published? (cats %) (:hi (first rs)) now))) ks)
        done (filter #(.exists (io/file (out-of %))) ks)]
    (when (empty? rows) (throw (ex-info (str "bnm.ledger: " quarter " has no counted " (name scale) " rows") {})))
    (when (seq unpublished)
      (throw (ex-info (str "bnm.ledger: outcomes not yet published for " (vec unpublished) "; refusing") {:kinds unpublished})))
    (when (seq done)
      (throw (ex-info (str "bnm.ledger: already scored " (vec done) "; an issue is scored once") {:kinds done})))
    (doseq [kd ks
            :let [cat (cats kd)
                  scored (vec (keep (fn [{:keys [place origin lo hi] :as r}]
                                      (let [y (#'lb/onset-in? cat place origin lo hi)]
                                        (when (some? y)
                                          (assoc r :y y :hard (wl/hard? (get-in cat [:onsets place] (sorted-set)) origin)))))
                                    (by-kind kd)))
                  res (assoc (score-cell scored {:k k}) :kind kd :scale scale :issue quarter :scored-month now :k k
                             :unscored (- (count (by-kind kd)) (count scored)))]]
      (io/make-parents (out-of kd))
      (spit (out-of kd) (with-out-str (clojure.pprint/pprint res)))
      (println kd (select-keys res [:n :onsets :skill :recall])))
    (when (every? #(.exists (io/file (out-of %))) all-ks)
      (let [rs (map #(read-string (slurp (out-of %))) all-ks)
            mean (fn [xs] (let [xs (remove nil? xs)] (when (seq xs) (/ (reduce + xs) (double (count xs))))))
            f (fn [x] (if x (format "%+.4f" (double x)) "  -  "))
            lines (concat [(format "Issue %s, %s nodes, %d kinds weighed equally, scored at month %d." quarter (name scale) (count rs) now)
                           (format "Brier skill against clim / last12 / share: %s / %s / %s"
                                   (f (mean (map #(get-in % [:skill :brier :clim]) rs))) (f (mean (map #(get-in % [:skill :brier :last12]) rs)))
                                   (f (mean (map #(get-in % [:skill :brier :share]) rs))))
                           (format "Log skill against clim / last12 / share: %s / %s / %s"
                                   (f (mean (map #(get-in % [:skill :log :clim]) rs))) (f (mean (map #(get-in % [:skill :log :last12]) rs)))
                                   (f (mean (map #(get-in % [:skill :log :share]) rs))))]
                          (when (= :year scale)
                            (for [sample [:hard :all]]
                              (format "Top-%d recall, %s rows: base %s  pers %s  clim %s  last12 %s" k (name sample)
                                      (f (mean (map #(get-in % [:recall sample :p]) rs))) (f (mean (map #(get-in % [:recall sample :pers]) rs)))
                                      (f (mean (map #(get-in % [:recall sample :wclim]) rs))) (f (mean (map #(get-in % [:recall sample :last12]) rs)))))))]
        (spit (str dir "/scores/" (name scale) "/pooled.txt") (str (str/join "\n" lines) "\n"))
        (doseq [l lines] (println l))))))

(defn -main [& args]
  (let [[cmd & more] args
        opts (into {} (for [[k v] (partition 2 more)] [(keyword (subs k 2)) v]))]
    (case cmd
      "issue" (issue! (update opts :date #(or % (str/trim (:out (sh/sh "date" "+%Y-%m-%d"))))))
      "score" (score! (update opts :k #(some-> % parse-long)))
      (throw (ex-info "bnm.ledger: issue or score" {:args args})))
    (shutdown-agents)))
