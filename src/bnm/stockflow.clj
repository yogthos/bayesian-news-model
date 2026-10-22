(ns bnm.stockflow
  "Stocks and flows (system dynamics: Forrester 1961; Sterman 2000;
  docs/STOCK-FLOW.md), as data. A model is

    {:params {name value}
     :stocks {name {:init x-or-(fn [v]) :nonneg bool :info bool :unit u}}
     :flows  {name {:from stock-or-nil :to stock-or-nil :rate (fn [v]) :unit u}}
     :aux    {name (fn [v])}}

  where `v` looks up any name's value now: :t the time, a parameter, a
  stock's level, an auxiliary's or a flow's value, resolved lazily each
  step (a cycle among them an error). Names are unique across the four
  and :t is time. A stock integrates its flows, dS/dt = in - out, by Euler
  steps of dt; its :init may be a function of the model at the start (an
  equilibrium). A flow leaves one stock and enters another, or comes from
  or goes to the outside (nil); a negative rate runs it backwards. An
  :info stock (a perception, a trend) holds information, not material: no
  flow carries material between it and a material stock. A :nonneg stock
  gives at most what it holds at the start of the step, whichever flow
  drains it (an outflow, or an inflow run backwards), inflows of the same
  step not counted; what else reads a limited flow sees what moved. The
  parameters are the model's estimable set: a run may override them.

  The DYNAMO functions (table, clip, step, switch) are World3's
  (WorldDynamics.jl); Vensim's IF THEN ELSE(Time > x) is a strict
  comparison, not clip's. The delays (delay-n, smooth, smooth-n, trend)
  start in equilibrium with their input unless told otherwise, as
  Vensim's."
  (:require [clojure.string :as str]))

(defn table
  "TABHL: y at x by linear interpolation over `ys` on an even grid across
  [lo hi], flat beyond the ends."
  ^double [x ys [lo hi]]
  (when (empty? ys) (throw (ex-info "bnm.stockflow/table: no values" {})))
  (let [x (double x) lo (double lo) hi (double hi) n (dec (count ys))]
    (cond (zero? n) (double (first ys))
          (<= x lo) (double (first ys))
          (>= x hi) (double (last ys))
          :else (let [pos (* n (/ (- x lo) (- hi lo)))
                      i (min (dec n) (long (Math/floor pos)))
                      f (- pos i)]
                  (+ (* (- 1.0 f) (double (nth ys i))) (* f (double (nth ys (inc i)))))))))

(defn clip "CLIP: a when x >= threshold, else b." [a b x threshold] (if (>= (double x) (double threshold)) a b))
(defn step "STEP: height from time t0 on, 0 before." [t height t0] (if (>= (double t) (double t0)) height 0.0))
(defn switch "SWITCH: a when x is (within 1e-16 of) zero, else b." [a b x] (if (<= (Math/abs (double x)) 1e-16) a b))

(defn- check
  "The model, or an error naming what is wrong with it."
  [{:keys [params stocks flows aux] :as m}]
  (let [names (concat (keys params) (keys stocks) (keys flows) (keys aux))
        twice (for [[k c] (frequencies names) :when (> c 1)] k)]
    (when (seq twice) (throw (ex-info (str "bnm.stockflow: names defined twice " (vec twice)) {:names (vec twice)})))
    (when (some #{:t} names) (throw (ex-info "bnm.stockflow: :t is time" {})))
    (doseq [[k {:keys [init]}] stocks :when (nil? init)]
      (throw (ex-info (str "bnm.stockflow: stock " k " has no :init") {:stock k})))
    (doseq [[k {:keys [from to]}] flows
            s [from to] :when (and s (not (contains? stocks s)))]
      (throw (ex-info (str "bnm.stockflow: flow " k " names no stock " s) {:flow k :stock s})))
    (doseq [[k {:keys [from to]}] flows
            :let [info? #(boolean (get-in stocks [% :info]))]
            :when (and from to (not= (info? from) (info? to)))]
      (throw (ex-info (str "bnm.stockflow: flow " k " carries material between information and a stock") {:flow k})))
    m))

(defn model
  "A model from its parts (see the namespace), each optional; checked."
  [{:keys [params stocks flows aux]}]
  (check {:params (or params {}) :stocks (or stocks {}) :flows (or flows {}) :aux (or aux {})}))

(defn combine
  "Models side by side, as one (names must not clash)."
  [& ms]
  (let [parts [:params :stocks :flows :aux]
        twice (for [[k c] (frequencies (for [m ms p parts n (keys (p m))] n)) :when (> c 1)] k)]
    (when (seq twice) (throw (ex-info (str "bnm.stockflow/combine: names defined twice " (vec twice)) {:names (vec twice)})))
    (check (into {} (for [p parts] [p (apply merge (map p ms))])))))

(defn- stage [name i] (keyword (str (clojure.core/name name) "-" i)))

(defn- value-of
  "A number, or a name looked up through v."
  [v x]
  (if (keyword? x) (double (v x)) (double x)))

(defn delay-n
  "A material delay of order n (DELAYN): `input` (a name) through n stocks
  in series, each draining at L/(tau/n), out as `name`, the last stage's
  outflow: a flow into stock :to (in the model it joins) when given, else
  to the outside with `name` an auxiliary reading it. What goes in comes
  out tau later on average, spread as an Erlang-n. `tau` a number or a
  name (a delay that changes); :init the initial outflow (each stage
  starting at init tau/n), by default the input at the start: in
  equilibrium."
  ([name input tau n] (delay-n name input tau n {}))
  ([name input tau n {:keys [init to]}]
   (when-not (and (integer? n) (pos? n)) (throw (ex-info "bnm.stockflow/delay-n: an order of 1 or more" {:n n})))
   (let [stocks (vec (for [i (range 1 (inc n))] (stage name i)))
         start (fn [v] (* (if (some? init) (double init) (double (v input))) (/ (value-of v tau) n)))
         rate (fn [s] (fn [v] (/ (v s) (/ (value-of v tau) n))))
         last-out (if to name (stage (peek stocks) "out"))]
     ;; checked when combined: its :to stock lives in the model it joins
     {:params {}
      :stocks (into {} (for [s stocks] [s {:init start}]))
      :flows (into {(stage name "in") {:to (first stocks) :rate (fn [v] (v input))}
                    last-out {:from (peek stocks) :to to :rate (rate (peek stocks))}}
                   (for [[a b] (partition 2 1 stocks)]
                     [(stage a "out") {:from a :to b :rate (rate a)}]))
      :aux (if to {} {name (fn [v] (v last-out))})})))

(defn smooth-n
  "An information delay of order n (SMOOTHN, DLINF3 for n = 3): `input`
  smoothed through n first-order smoothings over tau/n each, out as
  `name`; information, not material; from `init`, by default the input
  at the start."
  ([name input tau n] (smooth-n name input tau n nil))
  ([name input tau n init]
   (when-not (and (integer? n) (pos? n)) (throw (ex-info "bnm.stockflow/smooth-n: an order of 1 or more" {:n n})))
   (let [stocks (vec (for [i (range 1 (inc n))] (stage name (str "s" i))))
         start (fn [v] (if (some? init) (double init) (double (v input))))]
     (model {:stocks (into {} (for [s stocks] [s {:init start :info true}]))
             :flows (into {} (for [[i s] (map-indexed vector stocks)
                                   :let [src (if (zero? i) input (stocks (dec i)))]]
                               [(stage s "adjust") {:to s :rate (fn [v] (/ (- (v src) (v s)) (/ (value-of v tau) n)))}]))
             :aux {name (fn [v] (v (peek stocks)))}}))))

(defn smooth
  "SMOOTH: a perception `name` (a stock) of `input`, dP/dt = (input -
  P)/tau, `tau` a number or a name; from `init`, by default the input at
  the start; information, not material."
  ([name input tau] (smooth name input tau nil))
  ([name input tau init]
   (model {:stocks {name {:init (fn [v] (if (some? init) (double init) (double (v input)))) :info true}}
           :flows {(stage name "adjust") {:to name :rate (fn [v] (/ (- (v input) (v name)) (value-of v tau)))}}})))

(defn trend
  "TREND: `name`, the growth rate of `input` as perceived: its value
  smoothed over tau-p (P, from the input) against that smoothed over tau-h
  (R, from P/(1 + init tau-h)), (P - R)/(R tau-h) (Sterman 2000, 16.1),
  starting at `init` (a growth rate)."
  [name input tau-p tau-h init]
  (let [p (stage name "perceived") r (stage name "reference")]
    (model {:stocks {p {:init (fn [v] (double (v input))) :info true}
                     r {:init (fn [v] (/ (double (v input)) (+ 1.0 (* init (value-of v tau-h))))) :info true}}
            :flows {(stage name "p-adjust") {:to p :rate (fn [v] (/ (- (v input) (v p)) (value-of v tau-p)))}
                    (stage name "r-adjust") {:to r :rate (fn [v] (/ (- (v p) (v r)) (value-of v tau-h)))}}
            :aux {name (fn [v] (/ (- (v p) (v r)) (* (v r) (value-of v tau-h))))}})))

(defn- resolver
  "A lookup of every name at time t: params, the `levels` given, and
  otherwise each of `fns` ({name (fn [v])}: auxiliaries, flows, stocks'
  inits at the start) computed once, `pinned` values taking precedence; a
  cycle is an error."
  [t params levels fns pinned]
  (let [memo (atom pinned) busy (atom #{})]
    (fn v [k]
      (cond (= k :t) t
            (contains? params k) (double (params k))
            (contains? levels k) (levels k)
            (contains? @memo k) (@memo k)
            :else (let [f (or (fns k) (throw (ex-info (str "bnm.stockflow: no " k) {:name k})))]
                    (when (@busy k) (throw (ex-info (str "bnm.stockflow: a cycle through " k) {:name k})))
                    (swap! busy conj k)
                    (let [x (try (double (f v)) (finally (swap! busy disj k)))]
                      (swap! memo assoc k x)
                      x))))))

(defn- initial-levels [{:keys [stocks flows aux]} params t]
  (let [fns (merge aux (update-vals flows :rate)
                   (into {} (for [[k {:keys [init]}] stocks] [k (if (fn? init) init (constantly init))])))
        v (resolver t params {} fns {})]
    (into {} (for [k (keys stocks)] [k (v k)]))))

(defn- values
  "Every flow's and auxiliary's value at t: flows draining a :nonneg stock
  (an outflow, or an inflow run backwards) scaled to what it holds, then
  everything resolved again with those flows pinned, so what reads them
  sees what moved; again until no stock is overdrawn (a flow reading a
  limited one may itself need limiting)."
  [{:keys [stocks flows aux]} params t levels dt]
  (let [fns (merge aux (update-vals flows :rate))
        nonneg (keep (fn [[s {:keys [nonneg]}]] (when nonneg s)) stocks)
        drains (fn [k x] (let [{:keys [from to]} (flows k)] (if (neg? x) to from)))]
    (loop [limited {} pass 0]
      (let [v (resolver t params levels fns limited)
            fl (into {} (for [k (keys flows)] [k (v k)]))
            scale (into {} (for [s nonneg
                                 :let [demand (reduce + 0.0 (for [[k x] fl :when (= s (drains k x))] (Math/abs (double x))))
                                       room (/ (max 0.0 (levels s)) dt)]
                                 :when (> demand (* room (+ 1.0 1e-12)))]
                             [s (/ room demand)]))]
        (cond (empty? scale) {:flows fl :aux (into {} (for [k (keys aux)] [k (v k)]))}
              (> pass (inc (count nonneg))) (throw (ex-info "bnm.stockflow: limits on non-negative stocks do not settle" {:t t}))
              :else (recur (into limited (for [[k x] fl :let [s (scale (drains k x))] :when s] [k (* x s)])) (inc pass)))))))

(defn- params-of
  "The model's parameters with a run's overrides, each naming one."
  [m overrides]
  (when-let [bad (seq (remove (set (keys (:params m))) (keys overrides)))]
    (throw (ex-info (str "bnm.stockflow: no parameters " (vec bad)) {:names (vec bad)})))
  (merge (:params m) overrides))

(defn at
  "{:stocks :flows :aux} of `m` at its start, time `t`: the initial
  levels and what follows from them, :params overriding the model's, the
  limits on non-negative stocks those of a step of `dt` (1 by default)."
  ([m t] (at m t {}))
  ([m t params] (at m t params 1.0))
  ([m t params dt]
   (let [params (params-of m params)
         levels (initial-levels m params (double t))]
     (assoc (values m params (double t) levels (double dt)) :stocks levels))))

(defn run
  "{:t [...] :stocks {name [...]} :aux {name [...]} :flows {name [...]}
  :outside [in out] :conserved #{stocks}} of `m` from :from to :to by
  Euler steps of :dt (the span a whole number of steps), :params
  overriding the model's, :levels the stocks' starting levels where given
  (to continue a run, or start from an estimate): each series at every time, flows and
  auxiliaries from the stocks then; :outside the material that came in
  from and went out to the outside over the run, into or out of the
  conserved (material) stocks (for imbalance)."
  [{:keys [stocks flows aux] :as m} {:keys [from to dt] :as opts}]
  (let [from (double from) to (double to) dt (double dt)
        span (/ (- to from) dt)
        n (long (Math/round span))]
    (when-not (and (pos? dt) (>= to from) (< (Math/abs (- span n)) 1e-9))
      (throw (ex-info "bnm.stockflow/run: dt > 0 and the span a whole number of steps" (select-keys opts [:from :to :dt]))))
    (let [params (params-of m (:params opts))
          conserved (set (keep (fn [[k {:keys [info]}]] (when-not info k)) stocks))
          outside (fn [x f] (cond (and (nil? (:from f)) (conserved (:to f))) [x 0.0]
                                  (and (nil? (:to f)) (conserved (:from f))) [0.0 x]
                                  :else [0.0 0.0]))
          snap (fn [acc k m] (reduce (fn [acc [name x]] (update-in acc [k name] (fnil conj! (transient [])) x)) acc m))]
      (loop [i 0 levels (merge (initial-levels m params from) (:levels opts)) acc {} in 0.0 out 0.0]
        (let [t (+ from (* i dt))
              {fl :flows ax :aux} (values m params t levels dt)
              acc (-> (update acc :t (fnil conj! (transient [])) t)
                      (snap :stocks levels) (snap :aux ax) (snap :flows fl))]
          (if (>= i n)
            (let [done (fn [m] (update-vals (or m {}) persistent!))]
              {:t (persistent! (:t acc)) :stocks (done (:stocks acc)) :aux (done (:aux acc))
               :flows (done (:flows acc)) :outside [in out] :conserved conserved})
            (let [d (reduce (fn [d [k x]]
                              (let [{:keys [from to]} (flows k)]
                                (cond-> d from (update from (fnil - 0.0) x) to (update to (fnil + 0.0) x))))
                            {} fl)
                  [din dout] (reduce (fn [[a b] [k x]] (let [[p q] (outside x (flows k))] [(+ a p) (+ b q)])) [0.0 0.0] fl)]
              (recur (inc i)
                     (into {} (for [[k l] levels] [k (+ l (* dt (get d k 0.0)))]))
                     acc (+ in (* dt din)) (+ out (* dt dout))))))))))

(defn imbalance
  "How far a run's accounts fail to close: the conserved stocks' change
  less what came in from outside plus what went out, in size. With a
  checked model it closes by construction: a guard on the engine."
  [{:keys [stocks outside conserved]}]
  (let [[in out] outside
        total (fn [f] (reduce + 0.0 (for [s conserved] (f (stocks s)))))]
    (Math/abs (- (- (total last) (total first)) (- in out)))))

(defn- norm [u] (when u (str/replace u #"\s+" "")))

(defn unit-errors
  "The flows whose unit is not each of their stocks' per `time-unit`, or
  that have none where a stock has one."
  [{:keys [stocks flows]} time-unit]
  (vec (sort (for [[k {:keys [from to unit]}] flows
                   :let [want (keep #(some-> (get-in stocks [% :unit]) (str "/" time-unit) norm) [from to])]
                   :when (and (seq want) (not-every? #(= (norm unit) %) want))]
               k))))
