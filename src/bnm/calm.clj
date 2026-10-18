(ns bnm.calm
  "Long calm and sudden breaks in a place's event stream, as onset inputs.
  Prolonged calm, not rising variance, is what predicted banking crises out
  of sample (Danielsson, Valenzuela & Zer, RFS 2018); jumps in the mean and
  the variance of an attention series forecast protest (Timoneda & Wibbels,
  Political Analysis 2022). Per channel, from its share of the place's
  events (free of GDELT's growing volume):
  - since-spike: exp(-quarters since the share last doubled its trailing
    mean / 8), 0 if it never has;
  - cv: the share's coefficient of variation over the last 8 quarters (low
    is calm);
  - jump: the log share against its trailing mean;
  - var-jump: the log spread of the last 4 quarters against the 8 before.
  A quarter's share is shrunk toward the trailing mean by `pseudo` events,
  so a handful says little.")

(def channels [["protest" "protest-did"] ["violence" "assault-did"] ["coercion" "coerce-did"]])

(def labels
  (vec (for [[c _] channels f ["since-spike" "cv" "jump" "var-jump"]] (keyword (str c "-" f)))))

(def window "Quarters of history before the first output." 12)

(def pseudo 20.0)

(def ^:private eps 0.005)

(defn- mean [xs] (/ (reduce + xs) (double (count xs))))

(defn- sd [xs]
  (let [m (mean xs)] (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (count xs)))))

(defn- channel-step
  "One channel's state after quarter q: {:shares [...] :spike q-or-nil} and
  its outputs."
  [{:keys [shares spike]} q c n]
  (let [prior (take-last 8 shares)
        raw (/ c n)
        m (if (seq prior) (mean prior) raw)
        s (/ (+ c (* pseudo m)) (+ n pseudo))
        spike (if (and (seq prior) (>= c 5.0) (>= s (* 2.0 m))) q spike)
        shares (conj shares s)
        last8 (take-last 8 shares)
        recent (take-last 4 shares)
        older (take-last 8 (drop-last 4 shares))
        mu (mean last8)]
    [{:shares (vec (take-last (inc window) shares)) :spike spike}
     [(if spike (Math/exp (- (/ (- q spike) 8.0))) 0.0)
      (if (pos? mu) (/ (sd last8) mu) 0.0)
      (Math/log (/ (+ s eps) (+ m eps)))
      (if (seq older) (Math/log (/ (+ (sd recent) eps) (+ (sd older) eps))) 0.0)]]))

(defn features
  "{quarter double[]} from {quarter {type count}} (sorted by quarter), from
  `window` quarters after the first on. A quarter without events keeps the
  last values."
  [counts]
  (when (seq counts)
    (let [q0 (key (first counts)) q1 (key (last counts))]
      (loop [q q0 states (vec (repeat (count channels) {:shares [] :spike nil})) out nil acc (transient {})]
        (if (> q q1)
          (persistent! acc)
          (let [cs (get counts q)
                n (reduce + (vals cs))]
            (if (pos? n)
              (let [steps (mapv (fn [st [_ t]] (channel-step st q (get cs t 0.0) n)) states channels)
                    out (double-array (mapcat second steps))]
                (recur (inc q) (mapv first steps) out (if (>= (- q q0) window) (assoc! acc q out) acc)))
              (recur (inc q) states out (if (and out (>= (- q q0) window)) (assoc! acc q out) acc)))))))))
