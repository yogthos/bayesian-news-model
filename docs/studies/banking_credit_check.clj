;; Banking: target or pipeline? On the lattice's own banking year rows and
;; labels (December origins, resolved, JST countries), a plain logit on the
;; published JST row (credit stretch, three-year credit growth), refit each
;; year on the rows whose outcomes are known by then, against the lattice's
;; forecasts on the same rows; and the same logit against JST's own crisis
;; flags. If the logit does well on the lattice's labels, the lattice's
;; pipeline loses the signal; if it does badly on them but well on JST's,
;; the target differs.
;; jolt -A:tools -M docs/studies/banking_credit_check.clj <run>
(require '[augur.logit :as lgt] '[bnm.macrohistory :as mh] '[clojure.string :as str] '[clojure.java.io :as io])
(defn- ll [p y] (let [p (Math/max 1e-9 (Math/min (- 1 1e-9) p))] (Math/log (if y p (- 1.0 p)))))
(defn- auc [rs k] (let [pos (map k (filter :y rs)) neg (vec (sort (map k (remove :y rs)))) n (count neg)]
                    (when (and (seq pos) (pos? n))
                      (/ (reduce + (for [p pos] (+ (count (take-while #(< % p) neg)) (* 0.5 (count (filter #(== % p) neg)))))) (* (count pos) n)))))
(let [run (or (first *command-line-args*) "t80-base")
      jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
      built (into {} (for [r (mh/build jst-rows)] [[(:iso r) (:year r)] r]))
      jst (set (map :iso jst-rows))
      ;; JST's own flags: an onset in the two calendar years of the window
      jst-crisis (into {} (for [r jst-rows] [[(:iso r) (:year r)] (= 1.0 (:crisisJST r))]))
      rows (with-open [r (io/reader (str "data/runs/" run "/banking_crisis/forecasts.tsv"))]
             (vec (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t")]
                        :when (and (= s "year") (jst u) (#{"true" "false"} y))
                        :let [o (parse-long o) yr (quot o 12)]
                        :when (= 11 (mod o 12))
                        :let [b (built [u yr])]   ;; row yr, published by Dec yr + 1: read at Dec yr+1? use yr-1 below
                        :let [b1 (built [u (dec yr)])]
                        :when (and b1 (get-in b1 [:specific :credit]) (get-in b1 [:growth-3y :credit]))]
                    {:u u :yr yr :p (parse-double p) :y (= y "true")
                     :jst-y (boolean (or (jst-crisis [u (inc yr)]) (jst-crisis [u (+ yr 2)])))
                     :x (double-array [1.0 (get-in b1 [:specific :credit]) (get-in b1 [:growth-3y :credit])])})))
      fit-score (fn [label]
                  (vec (for [yr (range 1990 2021)
                             ;; outcomes known three years after the origin (24-month window + 12)
                             :let [train (filter #(<= (:yr %) (- yr 3)) rows)
                                   beta (when (seq (filter label train)) (lgt/fit (map #(hash-map :x (:x %) :y (boolean (label %)) :w 1.0) train) 3 {:prior 1.0}))]
                             r (filter #(= yr (:yr %)) rows) :when beta]
                         (assoc r :q (lgt/predict beta (:x r)) :lab (boolean (label r))))))
      report (fn [title scored]
               (doseq [[a b] [[1990 2002] [2003 2020]]
                       :let [rs (filter #(<= a (:yr %) b) scored) rs (map #(assoc % :y (:lab %)) rs)
                             n (count rs) rate (/ (count (filter :y rs)) (double (max 1 n)))]
                       :when (pos? n)]
                 (println (format "%-34s %d-%d rows %4d onsets %3d  logit AUC %.3f  lattice AUC %.3f  logit log-clim %+.4f  lattice log-clim %+.4f"
                                  title a b n (count (filter :y rs)) (auc rs :q) (auc rs :p)
                                  (- (/ (reduce + (map #(ll (:q %) (:y %)) rs)) n) (/ (reduce + (map #(ll rate (:y %)) rs)) n))
                                  (- (/ (reduce + (map #(ll (:p %) (:y %)) rs)) n) (/ (reduce + (map #(ll rate (:y %)) rs)) n))))))]
  (report "on the lattice's labels" (fit-score :y))
  (report "on JST's own crisis flags" (fit-score :jst-y))
  (let [both (filter #(<= 1990 (:yr %) 2020) rows)]
    (println "label agreement 1990-2020: rows" (count both) " lattice onsets" (count (filter :y both)) " JST onsets" (count (filter :jst-y both))
             " both" (count (filter #(and (:y %) (:jst-y %)) both)))))
