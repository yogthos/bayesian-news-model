(ns bnm.ews
  "Early warnings of a critical transition (Scheffer et al. 2009; Dakos et
  al. 2012): as a system loses resilience near a tipping point it
  recovers more slowly from small shocks, so over a rolling window its
  fluctuations grow more autocorrelated and more variable, and the rate
  at which a departure decays falls (critical slowing down). Computed on a
  series' own part (bnm.factors), each window ending at the index it
  stands for, its linear trend removed first: a trend is not slowing
  down."
  )

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- detrend
  "xs less their least-squares line."
  [xs]
  (let [n (count xs) ts (range n) mt (mean ts) mx (mean xs)
        stt (reduce + (map #(let [d (- % mt)] (* d d)) ts))
        b (if (pos? stt) (/ (reduce + (map #(* (- %1 mt) (- %2 mx)) ts xs)) stt) 0.0)]
    (mapv (fn [t x] (- x mx (* b (- t mt)))) ts xs)))

(defn- ac1 [rs]
  (let [v (reduce + (map #(* % %) rs))]
    (if (pos? v) (/ (reduce + (map * rs (rest rs))) v) 0.0)))

(defn kendall-tau
  "Kendall's rank correlation of xs with time: the trend of an indicator."
  [xs]
  (let [xs (vec xs) n (count xs)
        pairs (for [i (range n) j (range (inc i) n)] (compare (xs j) (xs i)))
        c (count (filter pos? pairs)) d (count (filter neg? pairs))]
    (if (pos? (+ c d)) (/ (double (- c d)) (+ c d)) 0.0)))

(defn indicators
  "One map per index of `xs`: {:ac1 :variance :return-rate :ac1-trend
  :variance-trend} over the `:window` values ending there (nil before a
  full window): the lag-1 autocorrelation and variance of the detrended
  window, the return rate -log(ac1) (how fast a departure decays; nil
  where ac1 is not positive), and the indicators' trends (Kendall's tau)
  over the last `:window` of them."
  [xs {:keys [window]}]
  (let [xs (vec xs) n (count xs)
        base (vec (for [i (range n)]
                    (when (>= i (dec window))
                      (let [rs (detrend (subvec xs (- (inc i) window) (inc i)))
                            a (ac1 rs)]
                        {:ac1 a
                         :variance (/ (reduce + (map #(* % %) rs)) (dec window))
                         :return-rate (when (pos? a) (- (Math/log a)))}))))]
    (vec (for [i (range n)]
           (when-let [b (base i)]
             (let [past (keep base (range (max 0 (- (inc i) window)) (inc i)))]
               (assoc b
                      :ac1-trend (kendall-tau (map :ac1 past))
                      :variance-trend (kendall-tau (map :variance past)))))))))
