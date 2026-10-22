(ns bnm.energy-pilot
  "The energy pilot (docs/STOCK-FLOW.md, bead bnm-62z.6; registered in
  docs/PHASE-CHANGES.md as trial P1): does a sector's place in the supply
  chain tell how an energy price shock hits its real output, beyond its
  own trend? On the input-output spine (data/derived/io), each country's
  table three years before the year forecast; the shock, refined
  products' price change against all sectors'; the trend (A), Hulten's
  unit pass-through (H) and the estimated network pass-through (N), each
  fitted only on the years before the shock tested.

  Two analyses: the registered one, as run first, and the corrected one
  (deviations after review, PHASE-CHANGES): refining itself left out, its
  Leontief identity out of the supply-chain share and Hulten's pass-through
  scaled by L_REF,REF, sectors under 0.1% of the economy's output, years
  whose sectors share one deflator and columns whose inputs reach their
  output dropped; and the corrected one on tables not backcast.

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

(defn exposures
  "{sector {:a :ell :ell-ref :colsum}} from coefficients `a` and their
  Leontief inverse `l`: the direct energy share a_REF,s, the supply-chain
  L_REF,s, refining's own L_REF,REF and the column's input share (sum over
  r of a_r,s; 1 or more is a sector whose inputs reach its output)."
  [a l sectors]
  (let [colsum (reduce (fn [acc [[_ t] v]] (update acc t (fnil + 0.0) v)) {} a)
        lrr (get l ["REF" "REF"] 1.0)]
    (into {} (for [s sectors]
               [s {:a (get a ["REF" s] 0.0) :ell (get l ["REF" s] 0.0) :ell-ref lrr :colsum (get colsum s 0.0)}]))))

(defn flat-prices?
  "Whether a country-year's sector price changes `pc` are all one (a
  single deflator for every sector: LR-WIOD's early backcast years)."
  [pc]
  (boolean (when (seq pc)
             (let [vs (vals pc) lo (apply min vs) hi (apply max vs)]
               (<= (- hi lo) (* 1e-9 (Math/abs (double hi))))))))

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

(defn chain-share
  "The supply-chain energy share of row `r`, L_REF,s - a_REF,s; when
  `corrected?`, less refining's own output too (the identity in
  L_REF,REF), so refining's share is energy embodied in its other inputs."
  [{:keys [s a ell]} corrected?]
  (- ell a (if (and corrected? (= s "REF")) 1.0 0.0)))

(defn predictions
  "Each test row ({:c :s :t :h :g :lag :a :ell :ell-ref :e}) with the three
  models' predictions, fitted on `train`: the trend (each country-sector's
  mean growth, plus rho times last year's departure from it, rho pooled
  per horizon), Hulten's (the trend less ell e; when :corrected?, less
  ell / ell-ref e, the pass-through of the observed refined-products
  price) and the network's (the trend plus b1 a e + b2 (chain share) e, b
  per horizon)."
  ([train test] (predictions train test {}))
  ([train test {:keys [corrected?]}]
   (let [mu (update-vals (group-by (juxt :c :s) train) #(mean (map :g %)))
         gm (mean (map :g train))
         mu-of (fn [{:keys [c s]}] (get mu [c s] gm))
         rho (into {} (for [[h rs] (group-by :h train)]
                        [h (first (ols (for [r rs] {:x [(- (:lag r) (mu-of r))] :y (- (:g r) (mu-of r))})))]))
         trend (fn [r] (+ (mu-of r) (* (get rho (:h r) 0.0) (- (:lag r) (mu-of r)))))
         x (fn [{:keys [a e] :as r}] [(* a e) (* (chain-share r corrected?) e)])
         b (into {} (for [[h rs] (group-by :h train)]
                      [h (ols (for [r rs] {:x (x r) :y (- (:g r) (trend r))}))]))]
     (for [{:keys [ell ell-ref e h] :as r} test
           :let [tr (trend r) [b1 b2] (get b h [0.0 0.0]) [x1 x2] (x r)]]
       (assoc r :trend tr
              :hulten (- tr (* (if corrected? (/ ell ell-ref) ell) e))
              :network (+ tr (* b1 x1) (* b2 x2)))))))

(defn evaluate
  "{:rows [...] :mse {model mse} :train {shock rows}} over the shocks in
  `shocks`, each with the models fitted on the rows whose outcome year is
  before it; `opts` as `predictions`."
  ([rows shocks] (evaluate rows shocks {}))
  ([rows shocks opts]
   (let [fits (for [y shocks :let [train (filterv #(< (+ (:t %) (:h %)) y) rows)]]
                [y (count train) (predictions train (filter #(= (:t %) y) rows) opts)])
         scored (vec (mapcat #(nth % 2) fits))
         mse (fn [k] (mean (map #(let [d (- (:g %) (k %))] (* d d)) scored)))]
     {:rows scored
      :mse {:trend (mse :trend) :hulten (mse :hulten) :network (mse :network)}
      :train (into {} (map (fn [[y n]] [y n]) fits))})))

(defn keep-row?
  "Whether a row stays in the corrected analysis: not refining itself, at
  least :min-share (0.1%) of the economy's output at t - 1, no year of it
  (t - 1, t, t + h) with one deflator for every sector, its table column's
  inputs under its output, and, given :backcast-max, its table's backcast
  share at most that."
  [r {:keys [min-share backcast-max] :or {min-share 0.001}}]
  (and (not= "REF" (:s r))
       (>= (:share r) min-share)
       (not (:flat r))
       (< (:colsum r) 1.0)
       (or (nil? backcast-max) (<= (:backcast r) backcast-max))))

(defn pair-rows
  "Rows [period model bench outcome unit] for augur.score/paired-loss-2way:
  `two-way?` the shock years as periods (resampled with the countries),
  else one period (countries resampled only, as registered)."
  [rs ka kb two-way?]
  (mapv (fn [r] [(if two-way? (:t r) 0) (ka r) (kb r) (:g r) (:c r)]) rs))

(defn build-rows
  "The pilot's rows: for each country, sector, year t and horizon h (0, 1,
  2), the real output growth in t + h (:g), last year's (:lag), the
  sector's direct and supply-chain energy shares from the table of t - 3
  (:a :ell, with :ell-ref L_REF,REF and the column's :colsum), the shock
  in t (:e), its share of the economy's output in t - 1 (:share), the
  table's backcast share and whether a year of it has flat prices.
  `data`: :sectors, :go {[c y s] output}, :flows {[c y] {[from to] v}},
  :prices {[c y] {s price change}}, :vol {[c y s] log volume change},
  :backcast {[c y] share}."
  [{:keys [sectors go flows prices vol backcast]}]
  (let [total (reduce (fn [acc [[c y _] v]] (update acc [c y] (fnil + 0.0) v)) {} go)
        coef (memoize (fn [c y] (when-let [fl (flows [c y])]
                                  (into {} (for [[[f t] v] fl :let [g (go [c y t])] :when (and g (pos? g))] [[f t] (/ v g)])))))
        expo (memoize (fn [c y] (when-let [a (coef c y)] (exposures a (leontief a sectors) sectors))))
        flat? (memoize (fn [c y] (flat-prices? (prices [c y]))))
        sh (memoize (fn [c t] (let [pc (prices [c t]) w (into {} (for [s sectors :let [g (go [c (dec t) s])] :when g] [s g]))]
                                (when (and pc (pc "REF") (seq w)) (shock pc w)))))]
    (vec (for [[c t s] (keys vol)
               :let [e (sh c t) ex (expo c (- t 3)) lag (vol [c (dec t) s])]
               :when (and e ex lag)
               h [0 1 2]
               :let [g (vol [c (+ t h) s])]
               :when g]
           (let [{:keys [a ell ell-ref colsum]} (ex s)
                 tot (total [c (dec t)])]
             {:c c :s s :t t :h h :g g :lag lag :a a :ell ell :ell-ref ell-ref :e e
              :colsum colsum
              :share (if (and tot (pos? tot)) (/ (get go [c (dec t) s] 0.0) tot) 0.0)
              :backcast (get backcast [c (- t 3)] 0.0)
              :flat (boolean (some #(flat? c %) [(dec t) t (+ t h)]))})))))

(defn- read-csv [path] (with-open [r (io/reader path)] (let [[h & ls] (line-seq r) ks (str/split h #",")] (mapv #(zipmap ks (str/split % #",")) ls))))

(defn- read-flows []
  (with-open [r (io/reader "data/derived/io/spine-flows.csv")]
    (reduce (fn [acc l] (let [[c y f t v] (str/split l #",")] (assoc-in acc [[c (parse-long y)] [f t]] (parse-double v))))
            {} (rest (line-seq r)))))

(defn- read-prices []
  (reduce (fn [acc d] (assoc-in acc [[(d "country") (parse-long (d "year"))] (d "sector")] (parse-double (d "price_change"))))
          {} (read-csv "data/derived/io/spine-prices.csv")))

(defn rows
  "`build-rows` on the spine (data/derived/io)."
  []
  (let [secs (read-csv "data/derived/io/spine-sectors.csv")]
    (build-rows
     {:sectors (vec (sort (distinct (map #(% "sector") secs))))
      :go (into {} (for [d secs :when (seq (d "go"))] [[(d "country") (parse-long (d "year")) (d "sector")] (parse-double (d "go"))]))
      :backcast (into {} (for [d secs :when (seq (d "backcast"))] [[(d "country") (parse-long (d "year"))] (parse-double (d "backcast"))]))
      :flows (read-flows)
      :prices (read-prices)
      :vol (into {} (for [d (read-csv "data/derived/io/spine-volumes.csv")]
                      [[(d "country") (parse-long (d "year")) (d "sector")] (Math/log (parse-double (d "volume_change")))]))})))

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
  before (energy/exposure: the output-weighted L_REF,s, refining's own
  output, the identity in L_REF,REF, left out) and their product
  (energy/pass-through)."
  []
  (let [secs (read-csv "data/derived/io/spine-sectors.csv")
        go (reduce (fn [acc d] (if (seq (d "go")) (assoc-in acc [[(d "country") (parse-long (d "year"))] (d "sector")] (parse-double (d "go"))) acc)) {} secs)
        sectors (vec (sort (distinct (map #(% "sector") secs))))
        flows (read-flows)
        pcs (read-prices)]
    (for [[c y] (keys go)
          :let [k (+ y 3)
                g (go [c y])
                fl (flows [c y])
                a (when fl (into {} (for [[[f t] v] fl :let [gt (g t)] :when (and gt (pos? gt))] [[f t] (/ v gt)])))
                ex (when a (economy-exposure (into {} (for [[s {:keys [ell]}] (exposures a (leontief a sectors) sectors)]
                                                        [s [(get a ["REF" s] 0.0) (- ell (if (= s "REF") 1.0 0.0))]]))
                                             g))
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
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (.append out (str s "\n"))))
        ;; paired squared error, rows [period model bench outcome unit]
        pair (fn [rs ka kb two-way?] (sc/paired-loss-2way (pair-rows rs ka kb two-way?)
                                                          (fn [p y] (* (- p y) (- p y)))
                                                          {:reps reps :seed 1 :alpha 0.1 :per-block 1}))
        fmt (fn [{:keys [diff lo hi]}] (format "%+.6f [%+.6f %+.6f]%s" diff lo hi (if (pos? lo) " GAIN" (if (neg? hi) " LOSS" ""))))
        intensive #{"METAL" "CHEM" "MINR" "PAPER" "TRANSP"}
        report (fn [title rows opts]
                 (let [{:keys [rows mse train]} (evaluate rows shocks opts)]
                   (say title)
                   (say (format "%d test rows; training rows per shock %s" (count rows) (pr-str (into (sorted-map) train))))
                   (say (format "mse: trend %.6f, hulten %.6f, network %.6f" (:trend mse) (:hulten mse) (:network mse)))
                   (say "1. network against trend, pooled (countries resampled):" (fmt (pair rows :network :trend false)))
                   (say "   (extra, countries and shock years resampled):" (fmt (pair rows :network :trend true)))
                   (say "2. network against hulten, pooled (countries resampled):" (fmt (pair rows :network :hulten false)))
                   (say "   (extra, countries and shock years resampled):" (fmt (pair rows :network :hulten true)))
                   (say "   hulten against trend, pooled (countries resampled):" (fmt (pair rows :hulten :trend false)))
                   (doseq [y shocks :let [rs (filter #(= y (:t %)) rows)] :when (seq rs)]
                     (say (format "   %d (n %d): network vs trend %s; hulten vs trend %s" y (count rs)
                                  (fmt (pair rs :network :trend false)) (fmt (pair rs :hulten :trend false)))))
                   (let [rs (filter #(intensive (:s %)) rows)]
                     (say (format "   energy-intensive sectors (n %d): network vs trend %s" (count rs) (fmt (pair rs :network :trend false)))))))]
    (io/make-parents "data/runs/energy-pilot/x")
    (say (format "energy pilot: %d rows; shocks %s" (count rs) shocks))
    (say (format "flagged rows: refining %d, under 0.1%% of output %d, a flat-price year %d, column inputs >= output %d"
                 (count (filter #(= "REF" (:s %)) rs)) (count (filter #(< (:share %) 0.001) rs))
                 (count (filter :flat rs)) (count (filter #(>= (:colsum %) 1.0) rs))))
    (report "\nREGISTERED (as run first; intervals as registered, countries resampled, and the original two-way)" rs {})
    (report "\nCORRECTED (deviations after review: refining out, its identity out of the chain share, Hulten / L_REF,REF, share >= 0.1%, no flat-price years, column inputs < output)"
            (filterv #(keep-row? % {}) rs) {:corrected? true})
    (report "\nCORRECTED, tables not backcast (backcast share <= 0.5)"
            (filterv #(keep-row? % {:backcast-max 0.5}) rs) {:corrected? true})
    (spit "data/runs/energy-pilot/score.txt" (str out))))
