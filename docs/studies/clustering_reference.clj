;; Does a run beat a reference that knows only that crises cluster? (reports/
;; Cross domain detection transfer.md: earthquake forecasting's lesson, Luen
;; & Stark.) For each kind's year country rows (December origins, resolved),
;; the reference's p at origin t is a logit, refit each year on the rows
;; before, on two inputs known by then: the share of the country's region
;; and of the world with an onset in the two years whose outcomes are known
;; (origins t-24 and t-36). Scored 2003-2022: log score per row of the run
;; and of the reference, and AUC.
;; jolt -A:tools -M docs/studies/clustering_reference.clj <run>
(require '[augur.logit :as lgt] '[bnm.geo :as geo] '[clojure.string :as str] '[clojure.java.io :as io])
(defn- ll [p y] (let [p (Math/max 1e-9 (Math/min (- 1 1e-9) p))] (Math/log (if y p (- 1.0 p)))))
(defn- auc [rs] (let [pos (map :p (filter :y rs)) neg (vec (sort (map :p (remove :y rs)))) n (count neg)]
                  (when (and (seq pos) (pos? n))
                    (/ (reduce + (for [p pos] (+ (count (take-while #(< % p) neg)) (* 0.5 (count (filter #(== % p) neg)))))) (* (count pos) n)))))
(let [run (or (first *command-line-args*) "t80-base")
      kinds (sort (map #(.getName %) (filter #(.isDirectory %) (.listFiles (io/file (str "data/runs/" run))))))]
  (println "kind                  rows   run log   ref log   run-ref    run AUC  ref AUC")
  (doseq [k kinds]
    (let [rows (with-open [r (io/reader (str "data/runs/" run "/" k "/forecasts.tsv"))]
                 (vec (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t")] :when (and (= s "year") (= 3 (count u)) (#{"true" "false"} y))
                            :let [o (parse-long o)] :when (= 11 (mod o 12))]
                        {:u u :t o :yr (quot o 12) :p (parse-double p) :y (= y "true")})))
          region (memoize (fn [u] (first (geo/parents u))))
          by-t (group-by :t rows)
          share (fn [rs] (when (seq rs) (/ (count (filter :y rs)) (double (count rs)))))
          recent (fn [t pred] (share (filter pred (concat (by-t (- t 24)) (by-t (- t 36))))))
          feat (fn [{:keys [u t]}] (double-array [1.0 (or (recent t #(= (region (:u %)) (region u))) 0.0) (or (recent t (constantly true)) 0.0)]))
          scored (for [yr (range 2003 2023)
                       :let [train (filter #(< (:yr %) (- yr 1)) rows)
                             beta (when (seq (filter :y train)) (lgt/fit (map #(hash-map :x (feat %) :y (:y %) :w 1.0) train) 3 {:prior 1.0}))]
                       r (filter #(= yr (:yr %)) rows) :when beta]
                   (assoc r :ref (lgt/predict beta (feat r))))
          n (count scored)
          m (fn [f] (/ (reduce + (map f scored)) n))]
      (when (pos? n)
        (let [a (m #(ll (:p %) (:y %))) b (m #(ll (:ref %) (:y %)))]
          (println (format "%-21s %5d  %+.4f   %+.4f   %+.4f     %.3f    %.3f" k n a b (- a b) (auc scored) (auc (map #(assoc % :p (:ref %)) scored)))))))))
