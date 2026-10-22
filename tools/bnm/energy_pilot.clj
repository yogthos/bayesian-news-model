(ns bnm.energy-pilot
  "The energy pilot (docs/STOCK-FLOW.md, bead bnm-62z.6; registered in
  docs/PHASE-CHANGES.md as trial P1): does a sector's place in the supply
  chain tell how an energy price shock hits its real output, beyond its
  own trend? On the input-output spine (data/derived/io), each country's
  table three years before the year forecast; the shock, refined
  products' price change against all sectors'; the trend (A), Hulten's
  unit pass-through (H) and the estimated network pass-through (N), each
  fitted only on the years before the shock tested.

    jolt -A:tools -M -m bnm.energy-pilot [--reps 400] [--states-only true]

  Prints the tests and writes data/runs/energy-pilot/score.txt, and the
  economies' energy states for the event lattice to
  data/derived/stocks/energy.csv (states)."
  (:require [augur.math :as m]
            [augur.score :as sc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn leontief
  "{[r s] L}: (I - A)^-1 of the coefficients `a` ({[r s] share of s's
  output paid to r}) over `sectors`, by the Neumann series I + A + A^2 +
  ... (a productive economy's A has spectral radius below 1), on arrays."
  [a sectors]
  (let [ss (vec sectors) n (count ss)
        am (double-array (* n n))
        _ (doseq [i (range n) j (range n)] (aset am (+ (* i n) j) (double (get a [(ss i) (ss j)] 0.0))))
        mul (fn [^doubles x]
              (let [out (double-array (* n n))]
                (dotimes [i n]
                  (dotimes [k n]
                    (let [xik (aget x (+ (* i n) k))]
                      (when-not (zero? xik)
                        (dotimes [j n]
                          (aset out (+ (* i n) j) (+ (aget out (+ (* i n) j)) (* xik (aget am (+ (* k n) j))))))))))
                out))
        total (double-array (* n n))]
    (dotimes [i n] (aset total (+ (* i n) i) 1.0))
    (loop [term (aclone total) k 0]
      (let [^doubles term' (mul term)
            size (loop [i 0 s 0.0] (if (< i (* n n)) (recur (inc i) (+ s (Math/abs (aget term' i)))) s))]
        (dotimes [i (* n n)] (aset total i (+ (aget total i) (aget term' i))))
        (cond (< size 1e-12) (into {} (for [i (range n) j (range n)] [[(ss i) (ss j)] (aget total (+ (* i n) j))]))
              (> k 2000) (throw (ex-info "bnm.energy-pilot/leontief: the series does not converge" {}))
              :else (recur term' (inc k)))))))

(defn shock
  "Refined products' log price change less the log of the output-weighted
  mean price change: `pc` {sector price change}, `go` {sector output}."
  [pc go]
  (let [ss (filter #(and (pc %) (go %)) (keys pc))
        total (reduce + 0.0 (map go ss))
        mean (/ (reduce + 0.0 (for [s ss] (* (go s) (pc s)))) total)]
    (- (Math/log (pc "REF")) (Math/log mean))))

(defn ols
  "Least-squares coefficients, no intercept, of rows {:x [..] :y}."
  [rows]
  (let [k (count (:x (first rows)))
        xtx (double-array (for [i (range k) j (range k)] (+ (reduce + 0.0 (map #(* (nth (:x %) i) (nth (:x %) j)) rows)) (if (= i j) 1e-9 0.0))))
        xty (double-array (for [i (range k)] (reduce + 0.0 (map #(* (nth (:x %) i) (:y %)) rows))))]
    (vec (m/solve-spd xtx xty k))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (max 1 (count xs))))

(defn predictions
  "Each test row ({:c :s :t :h :g :lag :a :ell :e}) with the three models'
  predictions, fitted on `train`: the trend (each country-sector's mean
  growth, plus rho times last year's departure from it, rho pooled per
  horizon), Hulten's (the trend less ell e) and the network's (the trend
  plus b1 a e + b2 (ell - a) e, b per horizon)."
  [train test]
  (let [mu (update-vals (group-by (juxt :c :s) train) #(mean (map :g %)))
        mu-of (fn [{:keys [c s]}] (get mu [c s] (mean (map :g train))))
        rho (into {} (for [[h rs] (group-by :h train)]
                       [h (first (ols (for [r rs] {:x [(- (:lag r) (mu-of r))] :y (- (:g r) (mu-of r))})))]))
        trend (fn [r] (+ (mu-of r) (* (get rho (:h r) 0.0) (- (:lag r) (mu-of r)))))
        b (into {} (for [[h rs] (group-by :h train)]
                     [h (ols (for [{:keys [a ell e] :as r} rs] {:x [(* a e) (* (- ell a) e)] :y (- (:g r) (trend r))}))]))]
    (for [{:keys [a ell e h] :as r} test
          :let [tr (trend r) [b1 b2] (get b h [0.0 0.0])]]
      (assoc r :trend tr :hulten (- tr (* ell e)) :network (+ tr (* b1 a e) (* b2 (- ell a) e))))))

(defn evaluate
  "{:rows [...] :mse {model mse}} over the shocks in `shocks`, each with
  the models fitted on the rows whose outcome year is before it."
  [rows shocks]
  (let [scored (vec (mapcat (fn [y]
                              (predictions (filter #(< (+ (:t %) (:h %)) y) rows)
                                           (filter #(= (:t %) y) rows)))
                            shocks))
        mse (fn [k] (mean (map #(let [d (- (:g %) (k %))] (* d d)) scored)))]
    {:rows scored :mse {:trend (mse :trend) :hulten (mse :hulten) :network (mse :network)}}))

(defn- read-csv [path] (with-open [r (io/reader path)] (let [[h & ls] (line-seq r) ks (str/split h #",")] (mapv #(zipmap ks (str/split % #",")) ls))))

(defn rows
  "The pilot's rows from the spine: for each country, sector, year t and
  horizon h (0, 1, 2), the real output growth in t + h, last year's, the
  sector's direct and supply-chain energy shares from the table of t - 3,
  and the shock in t."
  []
  (let [secs (read-csv "data/derived/io/spine-sectors.csv")
        go (into {} (for [d secs :when (seq (d "go"))] [[(d "country") (parse-long (d "year")) (d "sector")] (parse-double (d "go"))]))
        sectors (vec (sort (distinct (map #(% "sector") secs))))
        flows (with-open [r (io/reader "data/derived/io/spine-flows.csv")]
                (reduce (fn [acc l] (let [[c y f t v] (str/split l #",")] (assoc-in acc [[c (parse-long y)] [f t]] (parse-double v))))
                        {} (rest (line-seq r))))
        coef (memoize (fn [c y] (when-let [fl (flows [c y])]
                                  (into {} (for [[[f t] v] fl :let [g (go [c y t])] :when (and g (pos? g))] [[f t] (/ v g)])))))
        expo (memoize (fn [c y] (when-let [a (coef c y)]
                                  (let [l (leontief a sectors)]
                                    (into {} (for [s sectors] [s [(get a ["REF" s] 0.0) (get l ["REF" s] 0.0)]]))))))
        pcs (reduce (fn [acc d] (assoc-in acc [[(d "country") (parse-long (d "year"))] (d "sector")] (parse-double (d "price_change"))))
                    {} (read-csv "data/derived/io/spine-prices.csv"))
        vol (into {} (for [d (read-csv "data/derived/io/spine-volumes.csv")]
                       [[(d "country") (parse-long (d "year")) (d "sector")] (Math/log (parse-double (d "volume_change")))]))
        sh (memoize (fn [c t] (let [pc (pcs [c t]) w (into {} (for [s sectors :let [g (go [c (dec t) s])] :when g] [s g]))]
                                (when (and pc (pc "REF") (seq w)) (shock pc w)))))]
    (vec (for [[c t s] (keys vol)
               :let [e (sh c t) ex (expo c (- t 3)) lag (vol [c (dec t) s])]
               :when (and e ex lag)
               h [0 1 2]
               :let [g (vol [c (+ t h) s])]
               :when g]
           (let [[a ell] (ex s)] {:c c :s s :t t :h h :g g :lag lag :a a :ell ell :e e})))))

(defn economy-exposure
  "The output-weighted supply-chain energy share of an economy: `expo`
  {sector [a L]}, `go` {sector output}."
  [expo go]
  (let [ss (filter go (keys expo))
        total (reduce + 0.0 (map go ss))]
    (when (pos? total) (/ (reduce + 0.0 (for [s ss] (* (go s) (second (expo s))))) total))))

(defn states
  "Rows [iso year variable value] for the event lattice (bnm.state-diag),
  each as known at `year`: the energy shock of the year before
  (energy/shock), the economy's exposure from the table three years
  before (energy/exposure) and their product (energy/pass-through)."
  []
  (let [secs (read-csv "data/derived/io/spine-sectors.csv")
        go (reduce (fn [acc d] (if (seq (d "go")) (assoc-in acc [[(d "country") (parse-long (d "year"))] (d "sector")] (parse-double (d "go"))) acc)) {} secs)
        sectors (vec (sort (distinct (map #(% "sector") secs))))
        flows (with-open [r (io/reader "data/derived/io/spine-flows.csv")]
                (reduce (fn [acc l] (let [[c y f t v] (str/split l #",")] (assoc-in acc [[c (parse-long y)] [f t]] (parse-double v))))
                        {} (rest (line-seq r))))
        pcs (reduce (fn [acc d] (assoc-in acc [[(d "country") (parse-long (d "year"))] (d "sector")] (parse-double (d "price_change"))))
                    {} (read-csv "data/derived/io/spine-prices.csv"))]
    (for [[c y] (keys go)
          :let [k (+ y 3)
                g (go [c y])
                fl (flows [c y])
                a (when fl (into {} (for [[[f t] v] fl :let [gt (g t)] :when (and gt (pos? gt))] [[f t] (/ v gt)])))
                l (when a (leontief a sectors))
                ex (when l (economy-exposure (into {} (for [s sectors] [s [(get a ["REF" s] 0.0) (get l ["REF" s] 0.0)]])) g))
                pc (pcs [c (- k 1)]) w (go [c (- k 2)])
                e (when (and pc (pc "REF") w) (shock pc w))]
          row (cond-> []
                ex (conj [c k "energy/exposure" ex])
                e (conj [c k "energy/shock" e])
                (and ex e) (conj [c k "energy/pass-through" (* ex e)]))]
      row)))

(defn -main [& args]
  (let [{:keys [reps states-only]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        reps (or (some-> reps parse-long) 400)
        _ (io/make-parents "data/derived/stocks/x")
        _ (with-open [w (io/writer "data/derived/stocks/energy.csv")]
            (.write w "iso,year,variable,value\n")
            (doseq [[c y v x] (sort (states))] (.write w (str c "," y "," v "," x "\n"))))
        _ (when (= "true" states-only) (System/exit 0))
        rs (rows)
        shocks [1974 1979 1990 2008]
        {:keys [rows mse]} (evaluate rs shocks)
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))
        ;; paired squared error, rows [period model bench outcome unit]: shock
        ;; years and countries resampled
        pair (fn [rs ka kb] (sc/paired-loss-2way (for [r rs] [(:t r) (ka r) (kb r) (:g r) (:c r)])
                                                 (fn [p y] (* (- p y) (- p y)))
                                                 {:reps reps :seed 1 :alpha 0.1 :per-block 1}))
        fmt (fn [{:keys [diff lo hi]}] (format "%+.6f [%+.6f %+.6f]%s" diff lo hi (if (pos? lo) " GAIN" "")))
        intensive #{"METAL" "CHEM" "MINR" "PAPER" "TRANSP"}]
    (io/make-parents "data/runs/energy-pilot/x")
    (say (format "energy pilot: %d rows over %d training rows; shocks %s" (count rows) (count rs) shocks))
    (say (format "mse: trend %.6f, hulten %.6f, network %.6f" (:trend mse) (:hulten mse) (:network mse)))
    (say "1. network against trend, pooled:" (fmt (pair rows :network :trend)))
    (say "2. network against hulten, pooled:" (fmt (pair rows :network :hulten)))
    (doseq [y shocks :let [rs (filter #(= y (:t %)) rows)] :when (seq rs)]
      (say (format "   %d (n %d): network vs trend %s" y (count rs) (fmt (pair rs :network :trend)))))
    (let [rs (filter #(intensive (:s %)) rows)]
      (say (format "   energy-intensive sectors (n %d): network vs trend %s" (count rs) (fmt (pair rs :network :trend)))))
    (spit "data/runs/energy-pilot/score.txt" (str out))))
