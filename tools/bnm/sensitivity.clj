(ns bnm.sensitivity
  "Compares training runs that differ only in their priors or forgetting:
  how their forecasts scored, and how stable the learned graph of what
  drives what is across them.

    jolt -A:tools -M -m bnm.sensitivity base run-a run-b ...

  For each run: escalation scores of country violence and protest, overall
  and for onsets (average precision against the node's own history, Brier
  skill against climatology), and CRPS and 80% coverage of country totals,
  from its scored.edn and periods.edn. For the graph: per country, its five
  strongest drivers of violence and of protest in the run's final model,
  against the base run's: the mean overlap (Jaccard) of the two sets, and how
  often the strongest driver is the same."
  (:require [bnm.eval :as ev]
            [bnm.geo :as geo]
            [bnm.model :as model]
            [bnm.ontology :as ont]
            [bnm.store :as store]
            [clojure.edn :as edn]
            [clojure.set :as set]))

(defn- read-edn [path] (edn/read-string (slurp path)))

(defn- scores [run]
  (let [dir (str "data/runs/" run)
        scored (read-edn (str dir "/scored.edn"))
        periods (read-edn (str dir "/periods.edn"))
        esc (fn [ch st]
              (ev/escalation-scores (filter #(and (= :country (:level %)) (= ch (:channel %))
                                                  (or (nil? st) (= st (:stratum %))))
                                            scored)))
        totals (keep #(get-in % [:totals [:country :violence]]) periods)
        mean (fn [k] (/ (reduce + (keep k totals)) (max 1 (count (keep k totals)))))]
    {:violence (esc :violence nil) :onset (esc :violence :quiet) :protest (esc :protest nil)
     :crps (mean :crps-model) :crps-hist (mean :crps-hist) :cover80 (mean :coverage80)}))

(defn- drivers
  "{[country channel] [source-and-channel ...]}: each country's five
  strongest drivers of violence and protest."
  [mdl]
  (into {} (for [node (:nodes mdl)
                 :when (geo/country? (:id node))
                 ch [:violence :protest]]
             [[(:id node) ch]
              (mapv (juxt :source :channel)
                    (take 5 (model/connections mdl (:id node) (ont/channel-index ch))))])))

(defn- stability [base other]
  (let [ks (filter (set (keys other)) (keys base))
        jac (fn [a b] (let [a (set a) b (set b)]
                        (/ (count (set/intersection a b)) (double (max 1 (count (set/union a b)))))))]
    {:n (count ks)
     :jaccard (/ (reduce + (map #(jac (base %) (other %)) ks)) (max 1 (count ks)))
     :top1 (/ (count (filter #(= (first (base %)) (first (other %))) ks)) (double (max 1 (count ks))))}))

(defn- f3 [x] (if x (format "%.3f" (double x)) "  -  "))

(defn -main [base & runs]
  (let [model-of #(store/read-file (str "data/runs/" % "/model.edn"))
        base-drivers (drivers (model-of base))]
    (println "Prior sensitivity: country escalation (AP model/history, Brier skill vs climatology),")
    (println "country violence totals (CRPS model/history, 80% coverage), and driver graph stability")
    (println "against" base "(top-5 Jaccard, top-1 agreement), per run")
    (doseq [run (cons base runs)]
      (let [{:keys [violence onset protest crps crps-hist cover80]} (scores run)
            bss (fn [s] (when (and (:brier-model s) (pos? (:brier-clim s)))
                          (* 100 (- 1 (/ (:brier-model s) (:brier-clim s))))))
            g (if (= run base) {:jaccard 1.0 :top1 1.0} (stability base-drivers (drivers (model-of run))))]
        (println (format "%-22s violence ap %s/%s bss %6.1f%% | onset ap %s/%s | protest ap %s/%s | crps %s/%s cover %s | graph %s %s"
                         run (f3 (:ap-model violence)) (f3 (:ap-hist violence)) (double (or (bss violence) 0.0))
                         (f3 (:ap-model onset)) (f3 (:ap-hist onset))
                         (f3 (:ap-model protest)) (f3 (:ap-hist protest))
                         (f3 crps) (f3 crps-hist) (f3 cover80)
                         (f3 (:jaccard g)) (f3 (:top1 g))))))))
