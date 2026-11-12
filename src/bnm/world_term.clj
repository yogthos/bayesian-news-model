(ns bnm.world-term
  "The world gauges entered once per origin (fix 3 of the step-2 rescore,
  docs/RECURSIVE.md §3). A world gauge has one value per origin, the same
  at every place. Entered as a column on every country row, as step 2 did,
  it counts as about 150 independent observations per origin, and a dozen
  such columns, several collinear, fit the handful of distinct years the
  year node had learned from.

  Here the world node learns its own term: a logistic regression of the
  share of countries with an onset in the node's window on the world
  gauges known at the origin, one row per origin (the share as two rows
  weighted share and 1 - share), from the origins
  whose outcomes are all known by t. An origin weighs the effective number
  of its places, n / (1 + (n - 1) rho), rho the share's dispersion over the
  known origins beyond the binomial (method of moments, all of it counted
  as common, so the weight is conservative): a common shock that hits every
  place at once is one observation, not n. Its departure from the world's mean,
  the gauges' part of the linear predictor, is the world's state passed
  down: one input at each node below, whatever the number of gauges.

  The gauges are standardized over the year origins up to t (all known by
  t), and a gauge whose history is a linear function of earlier ones (a
  collinear duplicate) is dropped before the fit, so it cannot change the
  term."
  (:require [augur.dynamic :as d]
            [augur.ftest :as ft]))

(defn independent-columns
  "The indices of `cols` (each a vector over the same rows, no nils) that
  are not linear functions of the earlier ones kept (with a constant):
  Gram-Schmidt on the centered columns, a column dropped when what is left
  of it has less than `tol` of its centered sum of squares."
  ([cols] (independent-columns cols 1e-9))
  ([cols tol]
   (let [center (fn [c] (let [n (count c) mu (/ (reduce + c) (double n))] (mapv #(- % mu) c)))
         dot (fn [a b] (reduce + (map * a b)))]
     (loop [i 0 basis [] kept []]
       (if (= i (count cols))
         kept
         (let [c (center (nth cols i))
               ss (dot c c)
               r (reduce (fn [r q] (let [k (dot r q)] (mapv - r (map #(* k %) q)))) c basis)
               rr (dot r r)]
           (if (and (> ss 0.0) (> rr (* tol ss)))
             (recur (inc i) (conj basis (mapv #(/ % (Math/sqrt rr)) r)) (conj kept i))
             (recur (inc i) basis kept))))))))

(defn effective-n
  "Each origin's weight: n / (1 + (n - 1) rho), rho the intra-origin
  correlation that makes the binomial variance of the shares `rows`
  [{:share :n}] equal their observed variance (0 to 1)."
  [rows]
  (let [k (count rows)]
    (if (< k 2)
      (constantly 1.0)
      (let [ps (map :share rows)
            pbar (/ (reduce + ps) k)
            nbar (/ (reduce + (map :n rows)) (double k))
            v (/ (reduce + (map #(let [d (- % pbar)] (* d d)) ps)) (dec k))
            pq (* pbar (- 1.0 pbar))
            rho (if (or (<= pq 1e-12) (<= nbar 1.0))
                  1.0
                  (max 0.0 (min 1.0 (/ (- (/ v pq) (/ 1.0 nbar)) (- 1.0 (/ 1.0 nbar))))))]
        (fn [n] (/ (double n) (+ 1.0 (* (dec (double n)) rho))))))))

(defn fit
  "The world term's model from `rows` [{:z [standardized gauges] :share s
  :n places}] (one per origin): a logistic on an intercept and the gauges,
  prior variance 4 on the intercept and `var` on each gauge, no discount,
  each origin weighted by its effective number of places."
  [rows k {:keys [var] :or {var 1.0}}]
  (let [weight (effective-n rows)]
    (d/step (d/logistic {:mean (repeat (inc k) 0.0) :var (cons 4.0 (repeat k (double var))) :discount 1.0})
            (mapcat (fn [{:keys [z share n]}]
                      (let [x (vec (cons 1.0 z)) w (weight (or n 1))]
                        [{:x x :y true :w (* w share)} {:x x :y false :w (* w (- 1.0 share))}]))
                    rows))))

(defn- corr [xs ys]
  (let [n (count xs)]
    (when (> n 2)
      (let [mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
            c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
            vx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
            vy (reduce + (map #(let [d (- % my)] (* d d)) ys))]
        (when (and (pos? vx) (pos? vy)) (/ c (Math/sqrt (* vx vy))))))))

(defn- ols
  "[a b] of y ~ a + b x."
  [xs ys]
  (let [n (count xs) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
        vx (reduce + (map #(let [d (- % mx)] (* d d)) xs))
        b (if (pos? vx) (/ (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys)) vx) 0.0)]
    [(- my (* b mx)) b]))

(defn lead-test
  "The lead test a world gauge must pass before it is wired (bnm.lead-test
  has the doc and the table): {:lead :concurrent :online :clim :n
  :n-online :pass}. `gauge` (fn [t]) -> the gauge known at t or nil,
  `window-mean` (fn [t]) -> its mean over the window (or nil), `rate` (fn
  [t]) -> {:rate :known-at} or nil, over `origins`. Each origin's online
  error is the fit on the rows known by it, :errors the pairs [fit's
  climatology's] in origin order; passes when the online error is at most
  `ratio` (0.9) of climatology's over at least `min-online` (1) online
  errors (a hindsight diagnostic; the gate is `gate`)."
  [{:keys [gauge window-mean rate origins ratio min-online] :or {ratio 0.9 min-online 1}}]
  (let [rows (vec (for [t origins :let [g (gauge t) r (rate t)] :when (and g r)]
                    {:t t :g (double g) :r (double (:rate r)) :known-at (:known-at r)
                     :w (some-> (when window-mean (window-mean t)) double)}))
        online (vec (for [{:keys [t g r]} rows
                          :let [known (filter #(<= (:known-at %) t) rows)]
                          :when (>= (count known) 3)
                          :let [[a b] (ols (map :g known) (map :r known))
                                clim (/ (reduce + (map :r known)) (count known))]]
                      [(let [e (- r (+ a (* b g)))] (* e e)) (let [e (- r clim)] (* e e))]))
        mse (fn [k] (when (seq online) (/ (reduce + (map k online)) (count online))))
        cw (filter :w rows)]
    {:n (count rows)
     :errors online
     :n-online (count online)
     :lead (corr (map :g rows) (map :r rows))
     :concurrent (corr (map :w cw) (map :r cw))
     :online (mse first) :clim (mse second)
     :pass (boolean (and (>= (count online) (max 1 min-online)) (<= (mse first) (* ratio (mse second)))))}))

(def default-gate
  "The gate's test (bnm-56c.29, registered before any score): familywise
  `alpha` 0.10 per origin over the `family` of 26 cells (13 kinds x the year
  and the half-decade), Bonferroni across cells and Holm over a cell's
  gauges; `h` and `lags`, the horizon in origins and the Newey-West lags,
  are the scale's (bnm.world-gauges sets them: year 2, half-decade 1)."
  {:alpha 0.10 :family 26 :h 1 :lags 1})

(defn gate
  "The lead test as of origin t, with nothing that was not known by then
  (bnm-56c.29): gauge j over `origins` up to t, each origin's outcome only
  if known by t. Each origin s with a known outcome and three rows known by
  s gives an online error pair, the fit's and climatology's (`lead-test`);
  their difference is tested one-sided, Diebold-Mariano with Newey-West
  variance (`lags`) and Harvey-Leybourne-Newbold's correction (`h`), t(n -
  1) (augur.ftest/diebold-mariano-hln). {:p :stat :n-online :online :clim
  :pass}, :pass as a cell with this gauge alone (p <= alpha / family).
  Keys missing from the gate's options are `default-gate`'s."
  [{:keys [gauges outcome origins]} gate-opts t j]
  (let [{:keys [alpha family h lags]} (merge default-gate gate-opts)
        {:keys [errors online clim]}
        (lead-test {:gauge (fn [s] (nth (gauges s) j))
                    :rate (fn [s] (when-let [o (outcome s)]
                                    (when (<= (:known-at o) t) {:rate (:share o) :known-at (:known-at o)})))
                    :origins (filter #(<= % t) origins)})
        {:keys [p stat]} (ft/diebold-mariano-hln (map first errors) (map second errors) {:h h :lags lags})]
    {:p p :stat stat :n-online (count errors) :online online :clim clim
     :pass (<= p (/ alpha family))}))

(defn passing
  "The indices of the gauges offered at t that the gate admits: each
  gauge's `gate` p-value, Holm over the cell's gauges at alpha / family
  (a fixed family, whichever kinds a run holds)."
  [{:keys [gauges] :as opts} gate-opts t]
  (let [{:keys [alpha family]} (merge default-gate gate-opts)
        k (count (gauges t))
        ps (mapv #(:p (gate opts gate-opts t %)) (range k))]
    (vec (keep-indexed (fn [j ok] (when ok j)) (ft/holm ps (/ alpha family))))))

(defn term-at
  "The world term at origin t: {:x the gauges' part of the world's log
  odds, :kept the gauges kept, :n the origins learned from}. `gauges` (fn
  [s]) -> [values] known at origin s (nil unknown), `outcome` (fn [s]) ->
  {:share :n :known-at} or nil (n the places scored), `origins` the node's
  origins (ascending). With `gate` (`default-gate`'s keys, bnm-56c.29) a
  gauge is kept only if `passing` admits it at t: the lead test on what is
  known at t, re-decided at each origin."
  [{:keys [gauges outcome origins var] gate-opts :gate :as opts} t]
  (let [past (vec (filter #(<= % t) origins))
        hist (mapv gauges past)
        k (count (gauges t))
        ;; each gauge's mean and spread over the origins up to t
        col (fn [j] (keep #(nth % j) hist))
        stats (vec (for [j (range k)]
                     (let [vs (col j) n (count vs)]
                       (when (>= n 3)
                         (let [mu (/ (reduce + vs) (double n))
                               sd (Math/sqrt (/ (reduce + (map #(let [e (- % mu)] (* e e)) vs)) n))]
                           (when (> sd 1e-12) [mu sd]))))))
        ;; the gauges kept: with a spread, and not a linear function of
        ;; earlier ones over the origins where all are known
        admitted (if gate-opts (set (passing opts gate-opts t)) (constantly true))
        usable (filterv #(and (nth stats %) (admitted %)) (range k))
        full (filterv #(every? some? (map (fn [j] (nth % j)) usable)) hist)
        kept (if (seq full)
               (mapv usable (independent-columns (mapv (fn [j] (mapv #(nth % j) full)) usable)))
               [])
        z (fn [g] (mapv (fn [j] (let [v (nth g j) [mu sd] (nth stats j)] (if (nil? v) 0.0 (/ (- v mu) sd)))) kept))
        rows (vec (for [s past
                        :let [o (outcome s)]
                        :when (and o (<= (:known-at o) t))]
                    {:z (z (gauges s)) :share (double (:share o)) :n (or (:n o) 1)}))
        mdl (fit rows (count kept) {:var (or var 1.0)})
        b (rest (d/means mdl))]
    {:x (reduce + 0.0 (map * b (z (gauges t)))) :kept kept :n (count rows)}))

(defn term-fn
  "(fn [t]) -> `term-at`, memoized: the world term over `origins`."
  [opts]
  (memoize (fn [t] (term-at opts t))))
