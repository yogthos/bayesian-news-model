(ns bnm.metrics-gate
  "The code-quality ratchet over a jolt-metrics EDN report (docs/METRICS.md).
  The repository commits metrics-baseline.edn, the findings it already has;
  CI fails when a change adds one: a near-duplicate pair, a critical
  function, an unused require or private function, or a recurring pattern
  that would save at least `min-saving` syntax nodes as a function. A
  finding's identity is its file and definition (or the pattern's text),
  never its line, so moving code is not new. Removing findings passes and
  lists them, and --update writes the smaller baseline.

    jolt-metrics . report.edn --format edn --no-git
    jolt -A:tools -M -m bnm.metrics-gate report.edn [--baseline metrics-baseline.edn] [--update]

  Exits 1 when the gate fails, 2 on bad usage."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def min-saving
  "A new recurring pattern fails the gate once extracting it would save at
  least this many syntax nodes (jolt-metrics' Saves); smaller ones are
  reported by the tool and left to review."
  150)

(defn- site [{:keys [path name]}] [(str path) (str name)])

(defn identities
  "The gated findings of a jolt-metrics report, as sets of identities."
  [{:keys [queue duplicates abstractions cleanup]}]
  {:critical (set (for [f queue :when (= :critical (keyword (:severity f)))] (site f)))
   :duplicates (set (for [{:keys [a b]} duplicates] #{(site a) (site b)}))
   :unused-requires (set (for [{:keys [path lib]} (:unused-requires cleanup)] [(str path) (str lib)]))
   :unused-private (set (for [f (:unused-private cleanup)] (site f)))
   :abstractions (set (for [{:keys [pattern saving]} abstractions :when (>= (long saving) min-saving)] (pr-str pattern)))})

(defn compare-ids
  "{:new {kind #{...}} :gone {kind #{...}}} of report identities `cur`
  against the baseline's `base`."
  [base cur]
  (let [ks (into (set (keys base)) (keys cur))]
    {:new (into {} (for [k ks :let [d (remove (get base k #{}) (get cur k #{}))] :when (seq d)] [k (set d)]))
     :gone (into {} (for [k ks :let [d (remove (get cur k #{}) (get base k #{}))] :when (seq d)] [k (set d)]))}))

(defn pass?
  "Whether a comparison adds nothing."
  [{:keys [new]}]
  (empty? new))

(defn- show [ident]
  (cond (set? ident) (str/join "  ~  " (map #(str/join " " %) (sort ident)))
        (vector? ident) (str/join " " ident)
        :else (str ident)))

(defn- print-section [title m]
  (doseq [[k ids] (sort-by key m)]
    (println (str title " " (name k) " (" (count ids) "):"))
    (doseq [i (sort-by show ids)] (println "  " (show i)))))

(defn- sorted-baseline [ids]
  (into (sorted-map) (for [[k v] ids] [k (vec (sort-by show v))])))

(defn -main [& args]
  (let [[report-path & opts] args
        opts (into {} (for [[k v] (partition 2 2 [nil] opts)] [k v]))
        baseline-path (or (get opts "--baseline") "metrics-baseline.edn")]
    (when-not (and report-path (.exists (io/file report-path)))
      (binding [*out* *err*] (println "usage: bnm.metrics-gate report.edn [--baseline path] [--update]"))
      (System/exit 2))
    (let [cur (identities (edn/read-string (slurp report-path)))]
      (if (contains? opts "--update")
        (do (spit baseline-path (with-out-str (prn (sorted-baseline cur))))
            (println "wrote" baseline-path (into (sorted-map) (update-vals cur count))))
        (let [base (update-vals (edn/read-string (slurp baseline-path)) set)
              c (compare-ids base cur)]
          (print-section "NEW" (:new c))
          (when (seq (:gone c))
            (print-section "gone (lower the baseline with --update)" (:gone c)))
          (if (pass? c)
            (println "metrics gate: pass" (into (sorted-map) (update-vals cur count)))
            (do (println "metrics gate: FAIL, new findings above. Fix them, or, if one is intended, run with --update and commit the baseline with the reason.")
                (System/exit 1))))))))
