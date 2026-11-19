;; The level bug: is there fresher outcome information at the origin than
;; the year node uses? The year node learns its level from rows resolved
;; by the origin (windows ending 2-3 years back). Each catalogue publishes
;; onsets after its known-lag, so at Dec of year Y the onsets of
;; (t - lag - 12, t - lag] are known. Per kind, over Dec origins 1990-2022,
;; the correlation with the year's row rate (the outcome) of:
;;   fresh: the share of the catalogue's countries with an onset in the
;;          last 12 published months;
;;   old:   the row rate of origin Y-3, the newest fully resolved;
;;   p:     the run's yearly mean p.
;; jolt -A:tools -M docs/studies/level_fresh.clj t111-base
(require '[bnm.kinds :as kinds] '[clojure.java.io :as io] '[clojure.string :as str])
(defn corr [a b]
  (let [n (count a) ma (/ (reduce + a) n) mb (/ (reduce + b) n)
        sa (Math/sqrt (reduce + (map #(Math/pow (- % ma) 2) a))) sb (Math/sqrt (reduce + (map #(Math/pow (- % mb) 2) b)))]
    (if (and (pos? sa) (pos? sb)) (/ (reduce + (map #(* (- %1 ma) (- %2 mb)) a b)) (* sa sb)) Double/NaN)))
(let [run (first *command-line-args*)
      cats (kinds/catalogues)
      yrs (range 1990 2023)]
  (println (format "%-21s %4s %6s %6s %6s   (r with the year's row rate, %d origins)" "kind" "lag" "fresh" "old" "p" (count yrs)))
  (doseq [k (sort (keys cats))
          :let [f (io/file "data/runs" run k "forecasts.tsv")]
          :when (.exists f)]
    (let [{:keys [onsets known-lag]} (cats k)
          lag (or known-lag 0)
          by (with-open [r (io/reader f)]
               (reduce (fn [acc l] (let [[s u o p y] (str/split l #"\t" -1) o (parse-long o)]
                                     (if (and (= s "year") (= 3 (count u)) (#{"true" "false"} y) (= 11 (mod o 12)))
                                       (update acc (quot o 12) (fnil conj []) [(parse-double p) (= y "true")]) acc)))
                       {} (line-seq r)))
          rate (fn [y] (when-let [v (seq (by y))] (/ (count (filter second v)) (double (count v)))))
          mp (fn [y] (let [v (by y)] (/ (reduce + (map first v)) (count v))))
          n-c (count onsets)
          fresh (fn [y] (let [t (+ 11 (* 12 y)) hi (- t lag) lo (- hi 12)]
                          (/ (count (filter (fn [[_ ms]] (some #(and (> % lo) (<= % hi)) ms)) onsets)) (double n-c))))
          ys (filter #(and (rate %) (rate (- % 3))) yrs)]
      (when (> (count ys) 10)
        (println (format "%-21s %4d %+.2f  %+.2f  %+.2f" k lag
                         (corr (map fresh ys) (map rate ys)) (corr (map #(rate (- % 3)) ys) (map rate ys)) (corr (map mp ys) (map rate ys))))))))
