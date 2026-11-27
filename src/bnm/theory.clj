(ns bnm.theory
  "A theory forecast as a lattice node's offset (bnm-ris): known priors as
  the base, the lattice's groups learning only what departs from it. At
  each origin a pooled logit on the theory's inputs is refit on the rows
  whose outcomes are known by then and applied to the origin's inputs, so
  the offset is point in time. The political quarters already read a known
  forecast this way (bnm.onsets' named model); this builds one for a node
  that has none."
  (:require [augur.logit :as lgt]
            [augur.math :as m]))

(defn- design [{:keys [places origins inputs target base]}]
  (let [xs (into {} (for [u places t origins]
                      [[u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t))))]))
        bo (if base (fn [u t] (some-> (base u t) double)) (constantly 0.0))]
    {:xs xs :bo bo :k (alength ^doubles (val (first xs)))
     :rows (vec (for [u places t origins :let [tg (target u t) o (bo u t)] :when (and tg o)]
                  {:x (xs [u t]) :y (boolean (:y tg)) :w 1.0 :o o :known-at (:known-at tg) :t t}))}))

(defn fits
  "{origin coefficients}: `offsets`' logit at each origin (x[0] the
  intercept's), for the origins it has one. :raw true (t121): every
  slope's prior on its input's own scale (augur.logit/fit's :raw), so a
  sparse column is held near zero. :penalties (t124): one precision
  multiplier per column, x[0] the intercept's (augur.logit/fit's).
  :signs (bnm-7yg.6): one of -1, 0, +1 per column, x[0]'s ignored; a slope
  fit against its sign is held at zero: refit with that column's
  precision multiplied by 1e9 until no slope is wrong-signed (an active
  set); 0 or nil leaves a slope free. :half-life h (bnm-7yg.6): at the fit
  for origin t each row weighted 0.5^((t - its origin) / (12 h)), so
  drivers that change over the decades are tracked; nil, every row 1."
  [{:keys [origins prior raw penalties signs half-life] :as args}]
  (let [{:keys [rows k]} (design args)
        opts (fn [pens] (cond-> {:prior prior} raw (assoc :raw (set (range 1 k))) pens (assoc :penalties pens)))
        fit (fn [train]
              (loop [pinned #{}]
                (let [pens (when (or penalties (seq pinned))
                             (vec (for [j (range k)]
                                    (* (double (if penalties (nth penalties j) 1.0)) (if (pinned j) 1e9 1.0)))))
                      ^doubles b (lgt/fit train k (opts pens))
                      wrong (when signs
                              (seq (for [j (range 1 k)
                                         :let [s (double (or (get signs j) 0))]
                                         :when (and (not (pinned j)) (neg? (* s (aget b j))))]
                                     j)))]
                  (if wrong (recur (into pinned wrong)) b))))]
    (into {} (for [t origins
                   :let [train (cond->> (filter #(<= (:known-at %) t) rows)
                                 half-life (map #(assoc % :w (Math/pow 0.5 (/ (- t (:t %)) (* 12.0 half-life))))))]
                   :when (and (some :y train) (some (complement :y) train))]
               [t (fit train)]))))

(defn offsets
  "{[place origin] log odds}: at each origin, a logit fit on every earlier
  row whose outcome is known by it (`target` (fn [place origin]) ->
  {:y :known-at} or nil; `inputs` (fn [place origin]) -> vector, a nil
  entry the mean, 0), with prior precision `prior` (augur.logit/fit's),
  applied to the origin's inputs. An origin with no known outcome, or none
  of either kind, has no offset. :base (fn [place origin]) -> log odds or
  nil, optional: the fit holds it fixed as each row's offset and adds it
  back, so the theory learns the departures from it; a row with no base
  neither trains it nor takes an offset."
  [{:keys [places] :as args}]
  (let [{:keys [xs bo]} (design args)]
    (into {} (for [[t beta] (fits args) u places :let [o (bo u t)] :when o]
               [[u t] (m/logit (lgt/predict beta (xs [u t]) o))]))))

(defn any-of
  "The log odds that any of several places has an onset, from each one's
  log odds (independent), the chance kept within 1e-6 of 0 and 1 as the
  banking node's known offsets are; nil for none."
  [logits]
  (when (seq logits)
    (let [p (- 1.0 (reduce * (map #(- 1.0 (m/expit %)) logits)))]
      (m/logit (Math/max 1e-6 (Math/min (- 1.0 1e-6) p))))))

(defn place-z
  "(fn [place origin]) -> `raw`'s value standardized by the mean and sd of
  every place's values at the earlier `origins` (at least `min-n`),
  capped at 5; nil where raw is nil or the past too thin."
  [raw places origins min-n]
  (let [vals-at (memoize (fn [t] (vec (keep #(raw % t) places))))
        os (vec (sort origins))]
    (memoize
     (fn [u t]
       (when-let [x (raw u t)]
         (let [past (mapcat vals-at (take-while #(< % t) os))
               n (count past)]
           (when (>= n min-n)
             (let [m (/ (reduce + past) (double n))
                   sd (Math/sqrt (/ (reduce + (map #(Math/pow (- % m) 2) past)) (double n)))]
               (when (pos? sd) (Math/max -5.0 (Math/min 5.0 (/ (- x m) sd))))))))))))
