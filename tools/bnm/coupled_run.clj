(ns bnm.coupled-run
  "The coupled engine, phase B (bead bnm-4hv.6; bnm.coupled): every
  subsystem in one causal run, and its checks.

    jolt -A:tools -M -m bnm.coupled-run [--out data/derived/coupled] [--phase-a DIR]
                                        [--assimilate true] [--members 32]

  The run: today's stock tables (bnm.modules.stocks, shared), the
  allocation channel (bnm.modules.allocation, 1950-2026 as
  bnm.allocation-run), the SFC core per country over the years of
  bnm.sfc-run's free runs (origin its last measured year less ten, ten
  years), World3-03 (1900-2100) and the sheaf consistency module, from
  1800 to 2100 at World3's half year, one member, no assimilation.
  Writes <out>/state.csv (whole years), graph.{edn,txt}, tables.edn and
  sheaf.txt (the gaps per edge), then the golden checks, each must be
  exact:
    - stocks: the coupled table's rows of the stock modules against
      phase A's table (<phase-a>/state.csv, bnm.engine-run), line for
      line, and bnm.states reading every stock table both ways;
    - allocation: its rows against data/derived/coupled/allocation.csv;
    - SFC: its rows against bnm.sfc-run's free runs written as sfc-run
      writes them (sorted: sfc-run writes run by run);
    - World3: its whole-year rows against the engine's World3 alone;
    - state inputs: bnm.state-diag's default paths and
      data/runs/sequencing/states-t45.edn's states, read by bnm.states
      from the files and from the coupled table (bnm.world-run's
      states-of, the event model's inputs), at every country and year:
      written out both ways and compared byte for byte.
  --assimilate true: then the SFC ensemble with assimilation for each
  country with measured stocks, from its first measured year, its
  parameters relearned at each analysis on what is known, through the
  year its last-but-five data year is known, then free to its last:
  <out>/assimilation.txt (innovations before and after, the identities'
  largest gap, the free run against the data). Exits 1 on any mismatch."
  (:require [bnm.allocation :as al]
            [bnm.coupled :as cp]
            [bnm.engine :as eng]
            [bnm.factors :as fac]
            [bnm.lattice-political :as lp]
            [bnm.modules.allocation :as mal]
            [bnm.modules.consistency :as mc]
            [bnm.modules.sfc :as msfc]
            [bnm.modules.stocks :as ms]
            [bnm.modules.world3 :as mw]
            [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [bnm.state-diag :as sd]
            [bnm.states :as states]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def panel-path "data/derived/sfc/panel.csv")
(def keen-path "data/derived/stocks/keen.csv")

(defn sfc-runs
  "[[iso origin p st] ...]: bnm.sfc-run's free runs (its last measured
  year less ten, at least its fifth, for each country with six)."
  [panel kn]
  (for [iso (sort (keys panel))
        :let [ys (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob))] y))]
        :when (>= (count ys) 6)
        :let [origin (max (nth ys 4) (- (last ys) 10))
              p (sfc/learn panel kn iso origin {})]]
    [iso origin p (sfc/init panel kn iso origin p)]))

(defn modules
  "Every subsystem's modules for the phase B run (see the namespace)."
  [{:keys [panel kn idx links]}]
  (let [stocks (filterv #(.exists (io/file (:table %))) (ms/modules))
        runs (sfc-runs panel kn)
        sfc-ms (vec (mapcat (fn [[iso origin p st]] (msfc/modules iso st p {:country-ids true :span [(inc origin) (+ origin 10)]})) runs))
        alloc (mapv #(assoc % :span [1950 2026]) (mal/modules idx links))
        w3 (mapv #(assoc % :span [1900.0 2100.0]) (mw/modules))
        cons (mc/module {:sfc-ids (into {} (for [[iso] runs] [iso (:sfc-accounts (sa/ids-of iso))]))
                         :keen? (some #(= :keen (:module %)) stocks) :idx idx})]
    {:modules (vec (concat stocks alloc sfc-ms w3 [cons]))
     :stocks stocks :alloc alloc :sfc sfc-ms :world3 w3 :runs runs}))

(defn- sfc-phase-a!
  "bnm.sfc-run's state.csv rows of `runs` at `path` (as sfc-run writes
  them: each run by the engine alone, numbers only)."
  [path runs]
  (with-open [w (io/writer path)]
    (.write w (str (str/join "," eng/table-header) "\n"))
    (doseq [[iso origin p st] runs
            :let [ms (msfc/modules iso st p) res (eng/run ms {:from (inc origin) :to (+ origin 10)})
                  by (into {} (map (juxt :module identity)) ms)]
            [id vs] (:emits res)
            [i t] (map-indexed vector (:t res))
            :let [nums (into {} (for [[v xs] vs :let [x (nth xs i nil)] :when (number? x)] [v x]))]
            r (eng/table-rows (by id) t nums)]
      (.write w (str (str/join "," (map #(if (keyword? %) (name %) (str %)) r)) "\n")))))

(defn- world3-alone!
  "The whole-year rows of World3's modules run alone, at `path`."
  [path]
  (let [ms (mw/modules) {:keys [write close]} (eng/table-writer path ms)]
    (eng/run ms {:from 1900.0 :to 2100.0 :keep false
                 :on-step (fn [id t e] (when (== t (Math/floor t)) (write id t e)))})
    (close)))

(defn- dump-inputs!
  "Every chosen state's value as the event model reads it (bnm.world-run/
  states-of) at every country and year end 1950-2025, to `path`."
  [path spec countries]
  (let [chosen (wr/states-of spec countries)]
    (with-open [w (io/writer path)]
      (doseq [[k ss] (sort-by key chosen) {:keys [name at]} ss c (sort countries) y (range 1950 2026)
              :let [t (+ (* 12 y) 11) v (at c t)] :when (some? v)]
        (.write w (str k "\t" name "\t" c "\t" y "\t" v "\n"))))))

(defn- same-bytes? [a b]
  (let [fa (io/file a) fb (io/file b)]
    (and (= (.length fa) (.length fb))
         (with-open [x (io/input-stream fa) y (io/input-stream fb)]
           (let [ba (byte-array 65536) bb (byte-array 65536)]
             (loop []
               (let [n (.read x ba) n2 (.read y bb)]
                 (cond (not= n n2) false
                       (neg? n) true
                       (not (java.util.Arrays/equals ba bb)) false
                       :else (recur)))))))))

(defn- sheaf-report
  "Per edge: rows, countries, median and largest |gap|, and the five
  countries with the largest median |gap|, from the coupled table's
  consistency rows."
  [path]
  (let [rows (with-open [r (io/reader path)]
               (let [[h & ls] (line-seq r) ix (zipmap (str/split h #",") (range))]
                 (vec (for [l ls :let [f (str/split l #"," -1)] :when (= "consistency" (nth f (ix "module")))]
                        {:iso (nth f (ix "iso")) :var (nth f (ix "variable")) :gap (parse-double (nth f (ix "value")))
                         :dy (nth f (ix "data_year"))}))))
        med (fn [xs] (let [v (vec (sort xs))] (when (seq v) (nth v (quot (count v) 2)))))]
    (concat
     ["# Sheaf gaps (bnm.modules.consistency): first named less second, per country and data year"
      "# edge  rows  countries  median|gap|  max|gap|  mean gap  (largest median |gap| by country)"]
     (for [[v rs] (sort-by key (group-by :var rows))
           :let [gs (map :gap rs) by (group-by :iso rs)
                 worst (take 5 (sort-by (comp - second) (for [[iso xs] by] [iso (med (map #(Math/abs (:gap %)) xs))])))]]
       (format "%-28s %6d %4d  %.4f  %.4f  %+.4f  %s" v (count rs) (count by) (med (map #(Math/abs %) gs))
               (reduce max (map #(Math/abs %) gs)) (/ (reduce + gs) (count gs))
               (str/join " " (for [[iso g] worst] (format "%s %.3f" iso g))))))))

(defn- assimilation-lines
  "The SFC ensemble with assimilation, per country with measured stocks."
  [panel kn members]
  (concat
   ["# SFC assimilation (bnm.sfc-analysis): members, each country from its first measured year (state at that data year)"
    "# iso  from  through  analyses  mean |innovation| before -> after (sd units)  max identity gap / Y   free 5y from origin: |nl err| model vs persistence"]
   (for [iso (sort (keys panel))
         :let [ys (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob) (= :oecd-t720 (:stocks-source ob)))] y))]
         :when (>= (count ys) 8)
         :let [from (first ys) last-y (last ys) origin (- last-y 5)
               p (sfc/learn panel kn iso from {}) st (sfc/init panel kn iso from p)
               ids (sa/ids-of iso)
               an (sa/analysis {:ids ids :obs (sa/observations panel kn iso {})
                                :relearn (sa/relearn-at panel kn iso)})
               mof (sa/members-of iso st p {:members members :seed 1})
               r (cp/free-run mof {:members members :from (inc from) :origin (+ origin sfc/vintage) :years 3 :keep true :analyses [an] :seed 2})
               log (mapcat #(:log (first %)) (take 1 (:memories r)))
               gap (reduce max 0.0 (for [mem (:memories r) [_ rec] (:years (first mem))] (sa/identity-gaps rec)))
               idx-of (fn [y] (.indexOf ^java.util.List (:t r) y))
               errs (for [y (range (inc origin) (inc last-y))
                          :let [i (idx-of y) ob (get-in panel [iso y]) o0 (get-in panel [iso origin])]
                          :when (and (>= i 0) ob o0 (:flows-ok ob) (:flows-ok o0))
                          s [:hh :nfc :gov]
                          :let [mv (/ (reduce + (map #(or (get-in % [:emits (:sfc-accounts ids) (str "sfc/net-lending-" (name s)) i]) 0.0) (:members r))) members)
                                d (/ (get-in ob [:f s :nl] 0.0) (:y ob)) pd (/ (get-in o0 [:f s :nl] 0.0) (:y o0))]]
                      [(Math/abs (- mv d)) (Math/abs (- pd d))])
               mean (fn [xs] (if (seq xs) (/ (reduce + xs) (count xs)) Double/NaN))]]
     (format "%s  %d  %d  %3d  %.2f -> %.2f  %.1e   %.4f vs %.4f" iso from (+ origin sfc/vintage) (count log)
             (mean (map :innovation log)) (mean (map :after log)) gap (mean (map first errs)) (mean (map second errs))))))

(defn -main [& args]
  (let [opts (into {} (for [[k v] (partition-all 2 args)] [k v]))
        out (get opts "--out" "data/derived/coupled")
        phase-a (get opts "--phase-a")
        members (parse-long (get opts "--members" "32"))
        t0 (System/currentTimeMillis)
        secs #(quot (- (System/currentTimeMillis) t0) 1000)
        panel (sfc/read-panel panel-path)
        kn (if (.exists (io/file keen-path)) (sfc/read-keen keen-path) {})
        idx (al/index (al/read-panel "data/derived/allocation/panel.csv"))
        links (al/load-links "data/derived/supply/concordance.csv" "data/derived/supply/stages.csv")
        {:keys [modules stocks alloc runs]} (modules {:panel panel :kn kn :idx idx :links links})
        errs (eng/validate modules)
        _ (println "modules:" (count modules) "; validate:" (if (empty? errs) "empty" (str/join "; " (map :msg errs))))
        _ (println "causal step order:" (:text (eng/causal-order modules)))
        shared (set (map :module (concat stocks alloc)))
        res (cp/run! (fn [_] modules) out {:from 1800 :to 2100 :dt 0.5 :shared shared})
        _ (println "wrote" (str out "/state.csv") "in" (secs) "s;" (reduce + (vals (:rows res))) "rows")
        _ (spit (str out "/sheaf.txt") (str (str/join "\n" (sheaf-report (str out "/state.csv"))) "\n"))
        _ (println (slurp (str out "/sheaf.txt")))
        tmp (str out "/.golden")
        _ (io/make-parents (str tmp "/x"))
        checks (atom [])
        check! (fn [label ok detail] (swap! checks conj ok) (println (format "  %-44s %s %s" label (if ok "same" "DIFFERENT") (or detail ""))))]
    (println "golden checks")
    (doseq [[id n] (:leftover res) :when (pos? n)] (check! (str "rows left in " (name id)) false n))
    (when phase-a
      (let [g (cp/golden-lines (str out "/state.csv") (str phase-a "/state.csv") (map (comp name :module) stocks) {})]
        (check! "stock modules vs phase A's table" (:same g) (pr-str (dissoc g :same)))))
    (let [gs (ms/golden stocks out)]
      (check! "bnm.states: every stock table, file vs coupled" (every? (comp zero? :mismatches) gs)
              (str (reduce + (map :vars gs)) " variables")))
    (let [g (cp/golden-lines (str out "/state.csv") "data/derived/coupled/allocation.csv" ["allocation" "investment"] {})]
      (check! "allocation vs bnm.allocation-run's table" (:same g) (pr-str (dissoc g :same))))
    (sfc-phase-a! (str tmp "/sfc.csv") runs)
    (let [g (cp/golden-lines (str out "/state.csv") (str tmp "/sfc.csv") ["sfc-finance" "sfc-firms" "sfc-accounts"] {:sorted? true})]
      (check! "SFC vs bnm.sfc-run's free runs" (:same g) (pr-str (dissoc g :same))))
    (let [g (cp/golden-lines (str out "/state.csv") "data/derived/sfc/state.csv" ["sfc-finance" "sfc-firms" "sfc-accounts"] {:sorted? true})]
      (println (format "  %-44s %s %s" "(SFC vs data/derived/sfc/state.csv on disk)" (if (:same g) "same" "different") (pr-str (dissoc g :same)))))
    (world3-alone! (str tmp "/world3.csv"))
    (let [g (cp/golden-lines (str out "/state.csv") (str tmp "/world3.csv") (map (comp name :module) (mw/modules)) {})]
      (check! "World3 vs the engine's World3 alone" (:same g) (pr-str (dissoc g :same))))
    (let [paths (sd/default-paths)
          a (states/read-states paths) b (states/read-states paths {:coupled out})]
      (check! "default state paths: states" (= a b) (str (count a) " variables"))
      (check! "default state paths: readers" (ms/same-readers? a b {:sample 1}) nil))
    (let [spec (read-string (slurp "data/runs/sequencing/states-t45.edn"))
          countries (set (filter #(= 3 (count %)) (mapcat keys (vals (states/read-states (:paths spec))))))]
      (dump-inputs! (str tmp "/inputs-files.tsv") spec countries)
      (dump-inputs! (str tmp "/inputs-coupled.tsv") (assoc spec :coupled out) countries)
      (check! "states-t45 inputs, byte for byte" (same-bytes? (str tmp "/inputs-files.tsv") (str tmp "/inputs-coupled.tsv"))
              (str (.length (io/file (str tmp "/inputs-files.tsv"))) " bytes")))
    (when (= "true" (get opts "--assimilate"))
      (let [ls (assimilation-lines panel kn members)]
        (spit (str out "/assimilation.txt") (str (str/join "\n" ls) "\n"))
        (doseq [l ls] (println l))))
    (println "checked in" (secs) "s")
    (shutdown-agents)
    (System/exit (if (every? true? @checks) 0 1))))
