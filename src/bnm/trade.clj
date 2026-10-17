(ns bnm.trade
  "What a trader would ask of the hazards: will the market fall a given
  fraction below today's level within h months, and does acting on the answer
  beat holding, or the simple rules traders already use?

  Monthly returns throughout. Exposure e[t] is chosen at the end of month t
  and earns month t+1's return: e * market + (1 - e) * the bill rate, less a
  cost per unit of exposure changed.")

(defn wealth
  "Wealth from 1 through the returns: one more value than returns."
  [rets]
  (vec (reductions (fn [w r] (* w (+ 1.0 (double r)))) 1.0 rets)))

(defn drawdown-ahead?
  "Whether wealth falls `depth` or more below w[t] within the next h months;
  nil when those months are not all known."
  [w ^long t ^long h depth]
  (when (< (+ t h) (count w))
    (let [floor (* (- 1.0 (double depth)) (double (w t)))]
      (boolean (some #(<= (double (w %)) floor) (range (inc t) (+ t h 1)))))))

(defn backtest
  "Performance of exposures `ex` (ex[t] earns month t's returns, so callers
  shift decisions by one month): total and annual return, volatility, Sharpe
  over the bill rate, max drawdown, switches, and time invested, annualized
  over :periods a year (12)."
  [mkt rf ex {:keys [cost periods] :or {cost 0.001 periods 12}}]
  (let [n (count mkt)
        rs (loop [t 0 prev 1.0 acc (transient [])]
             (if (< t n)
               (let [e (double (nth ex t))
                     r (+ (* e (double (nth mkt t))) (* (- 1.0 e) (double (nth rf t))))
                     r (- (* (+ 1.0 r) (- 1.0 (* cost (Math/abs (- e prev))))) 1.0)]
                 (recur (inc t) e (conj! acc r)))
               (persistent! acc)))
        w (wealth rs)
        excess (map - rs rf)
        mean (/ (reduce + excess) n)
        sd (Math/sqrt (/ (reduce + (map #(let [d (- % mean)] (* d d)) excess)) (max 1 (dec n))))
        dd (loop [[x & more] w peak 0.0 worst 0.0]
             (if x
               (let [peak (max peak x)] (recur more peak (max worst (- 1.0 (/ x peak)))))
               worst))]
    {:total (dec (peek w))
     :annual (dec (Math/pow (peek w) (/ (double periods) n)))
     :vol (* sd (Math/sqrt periods))
     :sharpe (if (pos? sd) (* (/ mean sd) (Math/sqrt periods)) 0.0)
     :max-drawdown dd
     :switches (count (filter pos? (map #(Math/abs (- (double %1) (double %2))) (rest ex) ex)))
     :invested (/ (reduce + (map double ex)) n)}))

(defn sma-exposure
  "Faber's rule: invested at the end of month t when wealth is at or above its
  mean over the last `k` months (fewer at the start)."
  [w k]
  (vec (for [t (range (count w))
             :let [xs (subvec w (max 0 (- t (dec k))) (inc t))
                   m (/ (reduce + xs) (count xs))]]
         (if (>= (double (w t)) m) 1.0 0.0))))
