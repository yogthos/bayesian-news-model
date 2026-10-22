(ns bnm.stockflow
  "Stocks and flows (system dynamics: Forrester 1961; Sterman 2000;
  docs/STOCK-FLOW.md), as data. A model is

    {:stocks {name {:init x :nonneg bool :info bool :unit u}}
     :flows  {name {:from stock-or-nil :to stock-or-nil :rate (fn [v]) :unit u}}
     :aux    {name (fn [v])}}

  where `v` looks up any name's value now (:t the time, a stock's level,
  an auxiliary's or a flow's value, resolved lazily each step, a cycle
  among them an error). A stock integrates its flows, dS/dt = in - out,
  by Euler steps of dt. A flow leaves one stock and enters another, or
  comes from or goes to the outside (nil); the accounts close (imbalance).
  An :info stock (a perception, a trend) holds information, not material,
  and is left out of the accounts. A :nonneg stock gives at most what it
  holds. The DYNAMO functions (table, clip, step, switch) and the delays
  (delay-n, smooth, trend) are as World3's (WorldDynamics.jl).")

(defn table
  "TABHL: y at x by linear interpolation over `ys` on an even grid across
  [lo hi], flat beyond the ends."
  ^double [x ys [lo hi]]
  (let [x (double x) lo (double lo) hi (double hi) n (dec (count ys))]
    (cond (<= x lo) (double (first ys))
          (>= x hi) (double (last ys))
          :else (let [pos (* n (/ (- x lo) (- hi lo)))
                      i (min (dec n) (long (Math/floor pos)))
                      f (- pos i)]
                  (+ (* (- 1.0 f) (double (nth ys i))) (* f (double (nth ys (inc i)))))))))

(defn clip "CLIP: a when x >= threshold, else b." [a b x threshold] (if (>= (double x) (double threshold)) a b))
(defn step "STEP: height from time t0 on, 0 before." [t height t0] (if (>= (double t) (double t0)) height 0.0))
(defn switch "SWITCH: a when x is (within 1e-16 of) zero, else b." [a b x] (if (< (Math/abs (double x)) 1e-16) a b))

(defn model
  "A model from its parts (see the namespace), each part optional."
  [{:keys [stocks flows aux]}]
  {:stocks (or stocks {}) :flows (or flows {}) :aux (or aux {})})

(defn combine
  "Models side by side, as one (names must not clash)."
  [& ms]
  (reduce (fn [a b]
            (doseq [k [:stocks :flows :aux] n (keys (k b)) :when (contains? (k a) n)]
              (throw (ex-info (str "bnm.stockflow/combine: " n " defined twice") {:name n})))
            (merge-with merge a b))
          (model {}) ms))

(defn- stage [name i] (keyword (str (clojure.core/name name) "-" i)))

(defn delay-n
  "A material delay of order n: `input` (a name) through n stocks in
  series, each draining at L/(tau/n), out as `name` (an auxiliary, the
  last stage's outflow). What goes in comes out tau later on average,
  spread as an Erlang-n."
  ([name input tau n] (delay-n name input tau n 0.0))
  ([name input tau n init]
   (let [stocks (for [i (range 1 (inc n))] (stage name i))
         rate (fn [s] (fn [v] (/ (v s) (/ (double tau) n))))]
     (model {:stocks (into {} (for [s stocks] [s {:init (double init)}]))
             :flows (into {(stage name "in") {:to (first stocks) :rate (fn [v] (v input))}}
                          (for [[a b] (partition 2 1 (concat stocks [nil]))]
                            [(keyword (str (clojure.core/name a) "-out")) {:from a :to b :rate (rate a)}]))
             :aux {name (fn [v] (v (keyword (str (clojure.core/name (last stocks)) "-out"))))}}))))

(defn smooth
  "SMOOTH: a perception `name` of `input`, dP/dt = (input - P)/tau, from
  `init`; information, not material."
  [name input tau init]
  (model {:stocks {name {:init (double init) :info true}}
          :flows {(stage name "adjust") {:to name :rate (fn [v] (/ (- (v input) (v name)) (double tau)))}}}))

(defn trend
  "TREND: `name`, the growth rate of `input` as perceived: its value
  smoothed over tau-p against that smoothed over tau-h, (P - R)/(R tau-h)
  (Sterman 2000, 16.1), starting at `init` (a growth rate)."
  [name input tau-p tau-h init]
  (let [p (stage name "perceived") r (stage name "reference")]
    (combine (model {:stocks {p {:init 1.0 :info true} r {:init (/ 1.0 (+ 1.0 (* init tau-h))) :info true}}
                     :flows {(stage name "p-adjust") {:to p :rate (fn [v] (/ (- (v input) (v p)) (double tau-p)))}
                             (stage name "r-adjust") {:to r :rate (fn [v] (/ (- (v p) (v r)) (double tau-h)))}}
                     :aux {name (fn [v] (/ (- (v p) (v r)) (* (v r) (double tau-h))))}}))))

(defn- values
  "Every auxiliary's and flow's value at time t with stocks `levels`,
  resolved lazily; flows out of a :nonneg stock scaled to what it holds."
  [{:keys [stocks flows aux]} t levels dt]
  (let [memo (atom {}) busy (atom #{})
        v (fn v [k]
            (cond (= k :t) t
                  (contains? levels k) (levels k)
                  (contains? @memo k) (@memo k)
                  :else (do (when (@busy k) (throw (ex-info (str "bnm.stockflow: a cycle through " k) {:name k})))
                            (swap! busy conj k)
                            (let [f (or (aux k) (:rate (flows k))
                                        (throw (ex-info (str "bnm.stockflow: no " k) {:name k})))
                                  x (double (f v))]
                              (swap! busy disj k)
                              (swap! memo assoc k x)
                              x))))
        raw (into {} (for [k (keys flows)] [k (v k)]))
        ;; a stock that cannot go negative gives at most what it holds this step
        scale (into {} (for [[s {:keys [nonneg]}] stocks :when nonneg
                             :let [out (reduce + 0.0 (for [[k {:keys [from]}] flows :when (= from s)] (max 0.0 (raw k))))
                                   room (/ (max 0.0 (levels s)) dt)]
                             :when (> out room)]
                         [s (/ room out)]))
        fl (into {} (for [[k x] raw :let [from (:from (flows k))]] [k (* x (get scale from 1.0))]))]
    {:flows fl :aux (into {} (for [k (keys aux)] [k (v k)]))}))

(defn run
  "{:t [...] :stocks {name [...]} :aux {name [...]} :flows {name [...]}
  :outside [in out]} of `m` from :from to :to by Euler steps of :dt: each
  series at every time, flows and auxiliaries from the stocks then;
  :outside the material that came in from and went out to the outside
  over the run (for imbalance)."
  [{:keys [stocks flows aux] :as m} {:keys [from to dt]}]
  (let [n (long (Math/round (/ (- (double to) (double from)) (double dt))))
        conserved (set (keep (fn [[k {:keys [info]}]] (when-not info k)) stocks))]
    (loop [i 0 levels (into {} (for [[k {:keys [init]}] stocks] [k (double init)]))
           acc {:t [] :stocks {} :aux {} :flows {}} in 0.0 out 0.0]
      (let [t (+ (double from) (* i (double dt)))
            {fl :flows ax :aux} (values m t levels dt)
            acc (-> acc (update :t conj t)
                    (update :stocks #(merge-with conj (or (not-empty %) (update-vals levels (constantly []))) levels))
                    (update :aux #(merge-with conj (or (not-empty %) (update-vals ax (constantly []))) ax))
                    (update :flows #(merge-with conj (or (not-empty %) (update-vals fl (constantly []))) fl)))]
        (if (>= i n)
          (assoc acc :outside [in out] :conserved conserved)
          (let [d (reduce (fn [d [k x]]
                            (let [{:keys [from to]} (flows k)]
                              (cond-> d from (update from (fnil - 0.0) x) to (update to (fnil + 0.0) x))))
                          {} fl)
                ext (fn [pick] (reduce + 0.0 (for [[k x] fl :let [f (flows k)] :when (pick f)] x)))
                in' (+ in (* dt (ext #(and (nil? (:from %)) (conserved (:to %))))))
                out' (+ out (* dt (ext #(and (nil? (:to %)) (conserved (:from %))))))]
            (recur (inc i)
                   (into {} (for [[k l] levels] [k (+ l (* (double dt) (get d k 0.0)))]))
                   acc in' out')))))))

(defn imbalance
  "How far a run's accounts fail to close: the conserved stocks' change
  less what came in from outside plus what went out, in size."
  [{:keys [stocks outside conserved]}]
  (let [[in out] outside
        total (fn [f] (reduce + 0.0 (for [s conserved] (f (stocks s)))))]
    (Math/abs (- (- (total last) (total first)) (- in out)))))

(defn unit-errors
  "The flows whose unit is not their stocks' per `time-unit`."
  [{:keys [stocks flows]} time-unit]
  (vec (sort (for [[k {:keys [from to unit]}] flows
                   :let [su (some #(get-in stocks [% :unit]) [from to])]
                   :when (and unit su (not= unit (str su "/" time-unit)))]
               k))))
