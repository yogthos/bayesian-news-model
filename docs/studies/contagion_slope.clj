;; bnm-ris t108: the theory's coefficients at December origins, recovered
;; from its offsets and inputs (within an origin the offset's log odds are
;; linear in the inputs, the same beta for every country).
;; jolt -A:tools -M docs/studies/contagion_slope.clj 2001 2007 2008
(require '[bnm.lattice-banking :as lb] '[augur.math :as m])
(defn solve [a b] ;; least squares via normal equations, Gauss-Jordan
  (let [k (count (first a))
        ata (vec (for [i (range k)] (vec (for [j (range k)] (reduce + (map #(* (nth % i) (nth % j)) a))))))
        atb (vec (for [i (range k)] (reduce + (map #(* (nth %1 i) %2) a b))))
        aug (mapv conj ata atb)]
    (loop [mtx aug c 0]
      (if (= c k) (mapv #(peek %) mtx)
          (let [p (nth (nth mtx c) c)
                r (mapv #(/ % p) (nth mtx c))
                mtx (vec (map-indexed (fn [i row] (if (= i c) r (mapv - row (mapv #(* (nth row c) %) r)))) mtx))]
            (recur mtx (inc c)))))))
(let [sp (lb/build 24311 "r7-at-fx" {:leak-fixes true :gauge-std :stable :real-lags true :dated-credit true
                                    :theory-offset true :theory-world :all :recession-hold :rows :theory-contagion true})
      {:keys [offsets inputs]} (:theory sp)]
  (doseq [y (map parse-long *command-line-args*) :let [t (+ 11 (* 12 y))
                                                       rows (for [[[u t'] o] offsets :when (= t t') :let [x (inputs u t)] :when x]
                                                              [(vec (cons 1.0 (map #(double (or % 0.0)) x))) o])]]
    (println y (count rows) "beta [intercept credit boom3 world-ltd world-reserves contagion]"
             (mapv #(Double/parseDouble (format "%.3f" %)) (solve (mapv first rows) (mapv second rows))))))
