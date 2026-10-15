(ns bnm.calibrate
  "Turning the model's raw probabilities into calibrated ones, learned from how
  its past forecasts turned out.

  A simulation's escalation probability reflects only the uncertainty the
  model knows about; it is typically overconfident. Raw probabilities are
  binned per key (a channel, or a channel and stratum: whatever the caller
  keys them by), and each bin keeps forgetting-weighted counts of how often
  the outcome followed. The calibrated probability is a Beta
  posterior mean: the bin's observed rate, shrunk toward the raw probability
  when the bin has seen little. It is plain data in the model, and learns
  whenever forecasts resolve.")

(def edges
  "Upper edges of the probability bins."
  [0.02 0.05 0.1 0.15 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 0.95 1.0])

(def shrink
  "Pseudo-observations at the raw probability each bin starts with."
  10.0)

(def forget
  "Discount applied to old counts each time a batch of outcomes is learned
  (one batch per forecast round: per year in a backtest)."
  0.9)

(defn- bin-of [p]
  (or (first (keep-indexed (fn [i e] (when (<= p e) i)) edges))
      (dec (count edges))))

(defn calibrate
  "The calibrated probability for raw `p` in `channel` under `cal`."
  [cal channel p]
  (if-let [{:keys [pos n]} (get cal channel)]
    (let [i (bin-of p)]
      (/ (+ (nth pos i) (* shrink p)) (+ (nth n i) shrink)))
    p))

(defn learn
  "`cal` updated with resolved forecasts: a seq of {:channel :p :outcome}."
  [cal outcomes]
  (let [nb (count edges)
        empty-bins {:pos (vec (repeat nb 0.0)) :n (vec (repeat nb 0.0))}
        decayed (into {} (for [[ch b] cal]
                           [ch {:pos (mapv #(* forget %) (:pos b))
                                :n (mapv #(* forget %) (:n b))}]))]
    (reduce (fn [cal {:keys [channel p outcome]}]
              (let [i (bin-of p)]
                (-> cal
                    (update channel #(or % empty-bins))
                    (update-in [channel :n i] + 1.0)
                    (update-in [channel :pos i] + (if outcome 1.0 0.0)))))
            decayed outcomes)))
