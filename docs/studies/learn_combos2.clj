;; Out of sample on JST: does learning combinations of stocks and turning
;; flows across dimensions beat theory's credit alone and the fixed
;; invariants? Target: a systemic crisis starting in y+1..y+2, not while one
;; is under way (bnm.macrohistory :crisis). Each test era is predicted from
;; models fit on the years before it only.
(require '[bnm.macrohistory :as mh] '[augur.logit :as lgt] '[augur.hier :as hier] '[augur.math :as m])

(def dims (mapv first mh/dimensions))
(def rows (let [built (mh/build (mh/read-jst "data/raw/slow/jst/jst.csv"))]
            (into {} (for [r built] [[(:iso r) (:year r)] r]))))

(defn z [r k] (or (get-in r [:specific k]) 0.0))
(defn turn
  "A dimension stretched last year (|z| >= 1) moving back toward its mean:
  the size of the move, 0 otherwise."
  [r0 r k]
  (let [a (get-in r0 [:specific k]) b (get-in r [:specific k])]
    (if (and a b (>= (Math/abs (double a)) 1.0) (neg? (* (Math/signum (double a)) (- b a))))
      (Math/abs (- (double b) (double a)))
      0.0)))

(def years (sort (distinct (map second (keys rows)))))
(def isos (sort (distinct (map first (keys rows)))))

;; world aggregates by year: mean stretch and mean turn per dimension
(def world
  (into {} (for [y years]
             (let [cs (keep (fn [u] (when-let [r (rows [u y])] (when-let [r0 (rows [u (dec y)])] [r0 r]))) isos)]
               [y (when (>= (count cs) 8)
                    (vec (concat (for [k dims] (/ (reduce + (map (fn [[_ r]] (z r k)) cs)) (count cs)))
                                 (for [k dims] (/ (reduce + (map (fn [[r0 r]] (turn r0 r k)) cs)) (count cs))))))]))))

(defn feats [set-name u y]
  (let [r (rows [u y]) r0 (rows [u (dec y)]) w (world y)]
    (when (and r r0 w (some? (:crisis r)))
      (case set-name
        :credit [(z r :credit) (or (get-in r [:growth-3y :credit]) 0.0)]
        :fixed [(or (get-in r [:invariant :norm]) 0.0) (or (get-in r [:invariant :breadth]) 0.0)
                (or (get-in r [:invariant :resonance]) 0.0) (or (get-in r [:invariant :stock-x-flow]) 0.0)
                (or (get-in r [:invariant :world-norm]) 0.0)]
        :learned (vec (concat (map #(z r %) dims) (map #(turn r0 r %) dims) w))
        :country (vec (concat (map #(z r %) dims) (map #(turn r0 r %) dims)))
        :credit+country (vec (concat [(z r :credit) (or (get-in r [:growth-3y :credit]) 0.0)] (map #(z r %) (remove #{:credit} dims)) (map #(turn r0 r %) dims)))))))

(defn data-for [set-name pred]
  (vec (for [u isos y years :when (pred y) :let [x (feats set-name u y)] :when x]
         {:x (double-array (cons 1.0 x)) :y (boolean (:crisis (rows [u y]))) :w 1.0 :yr y :u u
          :g (cond (< y 1914) "1870-1913" (< y 1939) "1919-38" (< y 1974) "1946-73" (< y 1990) "1974-89" (< y 2000) "1990-99" :else "2000-")})))

(defn auc [ps]
  (let [pos (keep (fn [[p y]] (when y p)) ps) neg (vec (sort (keep (fn [[p y]] (when-not y p)) ps))) n (count neg)
        below (fn [p] (count (take-while #(< % p) neg))) ties (fn [p] (count (filter #(== % p) neg)))]
    (if (and (seq pos) (pos? n)) (/ (reduce + (map #(+ (below %) (* 0.5 (ties %))) pos)) (* (count pos) n)) Double/NaN)))

(defn within [scored]
  (let [by (group-by :yr scored)
        parts (for [[_ rs] by :let [a (count (filter :y rs)) b (- (count rs) a)] :when (and (pos? a) (pos? b))]
                [(auc (map (juxt :p :y) rs)) (* a b)])]
    (/ (reduce + (map #(* (first %) (second %)) parts)) (reduce + (map second parts)))))

(defn level-r [scored]
  (let [by (sort-by key (group-by :yr scored))
        mp (map (fn [[_ rs]] (/ (reduce + (map :p rs)) (count rs))) by)
        rt (map (fn [[_ rs]] (/ (count (filter :y rs)) (double (count rs)))) by)
        n (count mp) a (/ (reduce + mp) n) b (/ (reduce + rt) n)
        sa (Math/sqrt (reduce + (map #(* (- % a) (- % a)) mp))) sb (Math/sqrt (reduce + (map #(* (- % b) (- % b)) rt)))]
    (/ (reduce + (map #(* (- %1 a) (- %2 b)) mp rt)) (* sa sb))))

(defn logloss-skill [scored base]
  (let [ll (fn [p y] (Math/log (if y p (- 1.0 p))))]
    (- (/ (reduce + (map #(ll (:p %) (:y %)) scored)) (count scored))
       (/ (reduce + (map #(ll base (:y %)) scored)) (count scored)))))

(def tests [[1919 1938] [1946 1989] [1990 2016] [2000 2016]])

(doseq [[set-name how] [[:country :ridge] [:country :horseshoe] [:country :era-pooled] [:credit+country :horseshoe] [:credit+country :era-pooled]]]
  (println (format "%-8s %-10s" (name set-name) (name how))
           (apply str
                  (for [[a b] tests]
                    (let [train (data-for set-name #(< % a))
                          test (data-for set-name #(<= a % b))
                          k (alength ^doubles (:x (first train)))
                          base (/ (+ 0.5 (count (filter :y train))) (+ 1.0 (count train)))
                          predict (case how
                                    :ridge (let [beta (lgt/fit train k {:prior 1.0})] #(lgt/predict beta (:x %)))
                                    :horseshoe (let [beta (lgt/fit-shrunk train k {:prior 1.0})] #(lgt/predict beta (:x %)))
                                    :era-pooled (let [{:keys [beta]} (hier/grouped-slopes train k {:prior 1.0 :tau2 (vec (repeat k 0.1)) :sd-prior (vec (repeat k 0.5)) :sweeps 5})]
                                                  #(lgt/predict (double-array beta) (:x %))))
                          scored (mapv #(assoc % :p (predict %)) test)]
                      (format " | %d-%d auc %.3f within %.3f level-r %+.2f dlog %+.4f" a b
                              (auc (map (juxt :p :y) scored)) (within scored) (level-r scored) (logloss-skill scored base)))))))
