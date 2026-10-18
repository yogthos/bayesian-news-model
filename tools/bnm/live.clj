(ns bnm.live
  "The event model brought up to date and read: the latest saved model (the
  derived one if the pipeline has run before, else a training run's final
  model, which carries the live scoring state in :track) learns every
  complete weekly bin after its last one from data/bins/<source>.tsv
  (forecasting each quarter on its eve as it passes it, and scoring that
  forecast when the quarter ends, which recalibrates its probabilities and
  ranges), is saved back to data/derived/event-model.edn, and its outlook for the next
  quarter gives the likeliest escalations per channel, by calibrated
  probability, with the climatology and own history beside them.

    jolt -A:tools -M -m bnm.live [--from-run v6-q] [--source gdelt] [--n 15]"
  (:require [bnm.core :as core]
            [bnm.paths :as paths]
            [bnm.series :as series]
            [bnm.store :as store]
            [bnm.time :as t]
            [bnm.train :as train]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def model-path (str paths/derived "event-model.edn"))
(def outlook-path (str paths/derived "event-outlook.txt"))

(defn- load-latest [from-run]
  (let [derived (io/file model-path)
        src (if (.exists derived) model-path (str "data/runs/" from-run "/model.edn"))]
    (println "model from" src)
    [src (-> (store/read-file src) (update :pending #(or % {})) series/with-routes)]))

(defn catch-up
  "`m` with every complete bin after its last learned from `source`'s bins
  file: the newest bin in the file may still be filling, so it waits."
  [m source]
  (let [last-bin (long (:bin m))]
    (with-open [r (io/reader (str "data/bins/" source ".tsv"))]
      (let [fresh (->> (#'train/bin-records r)
                       (drop-while #(<= (first %) last-bin))
                       vec)]
        (if (empty? fresh)
          m
          (let [m (reduce (fn [m [b recs]] (assoc-in m [:pending b source] recs)) m fresh)
                newest (first (peek fresh))]
            (#'core/advance m newest)))))))

(defn -main [& args]
  (let [{:keys [from-run source n]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        source (or source "gdelt")
        n (parse-long (or n "15"))
        [src m] (load-latest (or from-run "v6-q"))
        before (:bin m)
        m (catch-up m source)
        bin-days (get-in m [:config :bin-days])
        as-of (t/date-str (* (inc (long (:bin m))) bin-days))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out s) (.append out "\n")))]
    (io/make-parents model-path)
    (store/write-file m model-path)
    (say (str "Event model: "
              (if (= before (:bin m))
                (format "no new complete bins since %d" (:bin m))
                (format "learned bins %d to %d" (inc (long before)) (:bin m)))
              (format " (data through %s); outlook for the next 13 weeks." as-of)))
    (let [ol (core/outlook m {:horizon 13 :sims 100 :levels #{:country}})]
      (doseq [ch [:violence :protest :coercion :verbal-conflict]]
        (say)
        (say (str "Likeliest escalations, " (name ch) " (country; calibrated / raw / climatology / own history):"))
        (doseq [r (core/risks ol :channel ch :n n)]
          (say (format "  %-28s %.2f / %.2f / %.2f / %.2f   expected %.1f [%.1f, %.1f], last quarter %.1f"
                       (:name r) (double (or (:p-cal r) 0.0)) (double (:p-escalate r))
                       (double (or (:p-clim r) 0.0)) (double (or (:p-hist r) 0.0))
                       (double (or (:expected r) 0.0)) (double (or (:low r) 0.0)) (double (or (:high r) 0.0))
                       (double (or (:last r) 0.0)))))))
    (let [rec (core/record m)]
      (say)
      (if (empty? rec)
        (say "Live record: no period forecast and scored since training yet (each quarter's is made on its eve).")
        (do (say "Live record (escalation Brier scores, calibrated / climatology / own history; lower is better):")
            (doseq [[[kind ch] r] rec :when (= kind :country)]
              (say (format "  %-16s %d forecasts over %d periods, %d escalations: %.4f / %.4f / %.4f"
                           (name ch) (:n r) (:periods r) (:positives r)
                           (double (or (:brier-cal r) 0.0)) (double (or (:brier-clim r) 0.0))
                           (double (or (:brier-hist r) 0.0))))))))
    (spit outlook-path (str out))))
