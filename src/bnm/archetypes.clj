(ns bnm.archetypes
  "Feedback structure and leverage in a stock-flow model (Meadows 2008,
  1999; Sterman 2000).

  The stocks' Jacobian at a state, resolved by flow ({[i j flow] w}: how
  a flow moves with stock i, + into stock j, - out of it), gives the
  feedback loops among the stocks and their polarity: reinforcing when
  the product of the edges' signs is positive, balancing when negative
  (Richardson 1995's definition at the level of stocks; the product is
  Kampmann 2012's loop gain). Delay and smoothing chains (delay-n's
  name-1..n, smooth-n's name-s1..sn) collapse into one node before the
  loops are enumerated, so a loop through a third-order delay counts as
  one step. Which loops matter is Loops That Matter (Schoenberg, Eberlein
  & Hayward 2020; Eberlein & Schoenberg 2020) along a run: each link's
  score is the part of the target's change in rate its source explains,
  a loop's score the product of its links'. System archetypes are
  patterns of loops, each returned with the stocks and loops that show
  it; leverage is each parameter's elasticity on an outcome, grouped by
  Meadows' class.

  The limits, as the literature has them:
  - polarity is local: a loop's sign is that of the partials at a state,
    and changes as the state does;
  - a loop's existence or gain is not its dominance (Forrester 1982;
    Kampmann & Oliva 2006); `dominance` scores them along a run, and
    limits to growth read from a run requires the shift;
  - loops among stocks merge the auxiliaries' pathways: two routes from
    a stock into one flow, of opposite sign, net into one edge, so a loop
    at the level of the causal diagram can vanish (LTM proper works at
    the level of links);
  - flow-resolved loops are not independent (their number grows as the
    product of parallel edges), and gains of loops of different lengths
    are in different units (1/time^L), so a gain is not a strength;
  - loops are capped in length (4 nodes by default, reported when longer
    ones may exist) and loops inside a collapsed delay are left out;
  - the loops come from the functional forms the modeller wrote; fitted
    data set the parameters, which can change signs and sizes, not the
    forms."
  (:require [bnm.stockflow :as sf]))

;; ---------------------------------------------------------------------
;; partials at a state

(def ^:private tiny-dt
  "The step at which the model is read for partials: the clamp on a
  non-negative stock (what it holds over dt) never binds above zero."
  1e-12)

(def ^:private edge-tol
  "An edge counts when its share of the flow it moves, |w| |S_i| /
  (|f| + |w| |S_i|), is above this: unit-free, far above the roundoff of
  a central difference (about 1e-10)."
  1e-8)

(defn- full-levels
  "Every stock's level: those given, the rest at the unperturbed state
  (the model's inits at t), held there."
  [m t levels params]
  (let [missing (remove (set (keys levels)) (keys (:stocks m)))]
    (into {} (for [[k x] (merge (when (seq missing) (select-keys (:stocks (sf/at m t (or params {}))) missing)) levels)]
               [k (double x)]))))

(defn- flows-at
  "{flow value} of `m` at t with every stock at `levels`."
  [m t params levels]
  (:flows (sf/at (assoc m :stocks (into {} (for [[k s] (:stocks m)] [k (assoc s :init (levels k))])))
                 t (or params {}) tiny-dt)))

(defn- partials
  "{:flows {k f} :d {[i k] df_k/dS_i} :levels}: each flow's sensitivity to
  each stock by central differences (one-sided at a non-negative stock's
  bound), at the full levels."
  [m t levels params]
  (let [lv (full-levels m t levels params)
        base (flows-at m t params lv)]
    {:flows base
     :levels lv
     :d (into {} (for [i (keys lv)
                       :let [x (lv i)
                             h (* 1e-6 (max 1.0 (Math/abs x)))
                             fwd? (and (get-in m [:stocks i :nonneg]) (< (- x h) 0.0))
                             up (flows-at m t params (assoc lv i (+ x h)))
                             down (if fwd? base (flows-at m t params (assoc lv i (- x h))))
                             span (if fwd? h (* 2.0 h))]
                       k (keys (:flows m))
                       :let [d (/ (- (get up k 0.0) (get down k 0.0)) span)]
                       :when (not (zero? d))]
                   [[i k] d]))}))

(defn- scale [x] (if (zero? x) 1.0 (Math/abs (double x))))

(defn- significant? [w s f] (> (/ (* (Math/abs w) s) (+ (Math/abs f) (* (Math/abs w) s))) edge-tol))

(defn- flow-edges
  "{[i j k] w} from partials: the edges above the relative threshold."
  [m {:keys [flows d levels]}]
  (into {} (for [[[i k] w] d
                 :when (significant? w (scale (levels i)) (get flows k 0.0))
                 :let [{:keys [from to]} (get-in m [:flows k])]
                 [j sign] [[to 1.0] [from -1.0]] :when j]
             [[i j k] (* sign w)])))

(defn flow-jacobian
  "{[i j flow] w}: each flow's sensitivity to stock i, signed by its effect
  on stock j (+ into j, - out of it): the Jacobian resolved by flow, so a
  stock fed by growth and drained by crowding shows both loops. Read at
  `levels` (stocks not given held at the unperturbed state, the model's
  inits at t), with opts :params overriding the model's; limits on
  non-negative stocks do not bind (the continuous system's partials);
  edges whose share of their flow is below 1e-8 left out."
  ([m t levels] (flow-jacobian m t levels {}))
  ([m t levels {:keys [params]}]
   (flow-edges m (partials m t levels params))))

(defn jacobian
  "{[i j] d(dS_j/dt)/dS_i}: how stock j's rate of change moves with stock
  i at `levels`, an edge from i to j (see flow-jacobian for the reading);
  an entry kept when its share of j's gross throughput is above 1e-8."
  ([m t levels] (jacobian m t levels {}))
  ([m t levels {:keys [params]}]
   (let [{:keys [flows d levels]} (partials m t levels params)
         gross (reduce (fn [g [k {:keys [from to]}]]
                         (let [x (Math/abs (double (get flows k 0.0)))]
                           (cond-> g from (update from (fnil + 0.0) x) to (update to (fnil + 0.0) x))))
                       {} (:flows m))
         sums (reduce (fn [acc [[i k] w]]
                        (let [{:keys [from to]} (get-in m [:flows k])]
                          (cond-> acc to (update [i to] (fnil + 0.0) w) from (update [i from] (fnil - 0.0) w))))
                      {} d)]
     (into {} (for [[[i j] w] sums :when (significant? w (scale (levels i)) (get gross j 0.0))] [[i j] w])))))

;; ---------------------------------------------------------------------
;; loops

(defn- stage-of
  "[base index] of a delay or smoothing stage's name (name-3, name-s3)."
  [k]
  (let [n (name k)]
    (if-let [[_ b i] (re-matches #"(.+)-s(\d+)" n)]
      [b (parse-long i)]
      (when-let [[_ b i] (re-matches #"(.+)-(\d+)" n)] [b (parse-long i)]))))

(defn- chains
  "{node {:stages [s1..sn] :chain [edge..] :weight w}}: the stage stocks
  that form a pure chain (one edge from each stage to the next, entered
  only at the first stage, left only from the last, nothing else inside
  but self-edges), each to collapse into one node named for its base."
  [edges]
  (let [stocks (set (mapcat (fn [[[i j] _]] [i j]) edges))
        cands (group-by first (keep (fn [s] (when-let [[b i] (stage-of s)] [b i s])) stocks))]
    (into {} (for [[b xs] cands
                   :let [by-i (into {} (map (fn [[_ i s]] [i s]) xs))
                         n (count by-i)
                         ordered (mapv by-i (range 1 (inc n)))
                         node (keyword b)]
                   :when (and (>= n 2) (every? some? ordered) (not (stocks node)))
                   :let [members (set ordered)
                         pos (zipmap ordered (range))
                         links (vec (for [[a c] (partition 2 1 ordered)]
                                      (filterv (fn [[[i j] _]] (and (= i a) (= j c))) edges)))
                         pure? (every? (fn [[[i j] _]]
                                         (cond (and (members i) (members j)) (or (= i j) (= (inc (pos i)) (pos j)))
                                               (members j) (= j (first ordered))
                                               (members i) (= i (peek ordered))
                                               :else true))
                                       edges)]
                   :when (and pure? (every? #(= 1 (count %)) links))]
               [node {:stages ordered
                      :chain (mapv (comp first first) links)
                      :weight (reduce * 1.0 (map (comp second first) links))}]))))

(defn- graph
  "The edges between nodes once chains are collapsed: {:from :to :label
  :w :path}, :path the model's edges it stands for."
  [edges groups]
  (let [node-of (into {} (for [[g {:keys [stages]}] groups s stages] [s g]))]
    (vec (for [[[i j k :as e] w] edges
               :let [gi (node-of i) gj (node-of j)]
               :when (not (and gi (= gi gj)))]
           (let [{:keys [chain weight]} (groups gj)]
             {:from (or gi i) :to (or gj j) :label k
              :path (into [e] (when gj chain))
              :w (* (double w) (if gj weight 1.0))})))))

(defn- sccs
  "The strongly connected components of a graph (Tarjan)."
  [nodes succ]
  (let [counter (atom 0) idx (atom {}) low (atom {}) stack (atom []) on (atom #{}) out (atom [])]
    (letfn [(visit [v]
              (swap! idx assoc v @counter) (swap! low assoc v @counter) (swap! counter inc)
              (swap! stack conj v) (swap! on conj v)
              (doseq [w (succ v)]
                (cond (not (contains? @idx w)) (do (visit w) (swap! low update v min (@low w)))
                      (@on w) (swap! low update v min (@idx w))))
              (when (= (@low v) (@idx v))
                (loop [c []]
                  (let [w (peek @stack)]
                    (swap! stack pop) (swap! on disj w)
                    (if (= w v) (swap! out conj (conj c w)) (recur (conj c w)))))))]
      (doseq [v nodes :when (not (contains? @idx v))] (visit v))
      @out)))

(defn loops
  "{:loops [{:stocks [..] :flows [..] :edges [..] :gain g :polarity}]
  :truncated bool :groups {node [stages]}}: the cycles among the stocks
  of a Jacobian, `jac` resolved by flow {[i j flow] w} or not {[i j] w}
  (then no :flows), within each strongly connected component, each once
  (from its smallest node; a cycle through other flows another loop).
  Delay and smoothing chains collapse into one node (opts :collapse,
  true), their stages' own loops left out; :edges are the model's edges a
  loop runs through, :gain their product. Loops of up to opts
  :max-length nodes (4); :truncated when a longer path stays inside a
  component, so longer loops may exist."
  ([jac] (loops jac {}))
  ([jac {:keys [max-length collapse] :or {max-length 4 collapse true}}]
   (let [edges (filterv (fn [[_ w]] (not (zero? (double w)))) jac)
         groups (if collapse (chains edges) {})
         g (graph edges groups)
         out (group-by :from g)
         nodes (distinct (mapcat (juxt :from :to) g))
         comp-of (into {} (for [c (sccs nodes (fn [v] (distinct (map :to (out v))))) v c] [v (set c)]))
         truncated (atom false)
         paths (fn [s]
                 (let [c (comp-of s)]
                   (loop [frontier [[[s] []]] found []]
                     (if (empty? frontier)
                       found
                       (let [ext (for [[ns es] frontier e (out (peek ns))
                                       :let [j (:to e)]
                                       :when (and (c j) (not (neg? (compare j s))))]
                                   [(conj ns j) (conj es e)])
                             closed (filter #(= s (peek (first %))) ext)
                             open (filter (fn [[ns _]] (and (not= s (peek ns)) (apply distinct? ns))) ext)
                             within (filterv (fn [[ns _]] (<= (count ns) max-length)) open)]
                         (when (< (count within) (count open)) (reset! truncated true))
                         (recur within (into found closed)))))))
         aggregate? (every? (fn [[k _]] (= 2 (count k))) edges)]
     {:loops (vec (for [s (sort nodes) [ns es] (paths s)
                        :let [gain (reduce * 1.0 (map :w es))]]
                    (cond-> {:stocks (pop ns)
                             :edges (vec (mapcat :path es))
                             :gain gain
                             :polarity (if (pos? gain) :reinforcing :balancing)}
                      (not aggregate?) (assoc :flows (mapv :label es)))))
      :truncated @truncated
      :groups (update-vals groups :stages)})))

(defn structure
  "The feedback structure of `m` at time t and `levels` (opts :params,
  :max-length, :collapse): `loops` of its flow-resolved Jacobian, with
  :jacobian, :flows (their values), :levels, :t, :params and :model, as
  `archetypes` reads them."
  ([m t levels] (structure m t levels {}))
  ([m t levels {:keys [params] :as opts}]
   (let [p (partials m t levels params)
         jac (flow-edges m p)]
     (assoc (loops jac opts)
            :jacobian jac :flows (:flows p) :levels (:levels p) :t t :params params :model m))))

;; ---------------------------------------------------------------------
;; dominance: loops that matter

(defn- net-rates [m flows]
  (reduce (fn [d [k {:keys [from to]}]]
            (let [x (get flows k 0.0)]
              (cond-> d from (update from (fnil - 0.0) x) to (update to (fnil + 0.0) x))))
          {} (:flows m)))

(defn dominance
  "Loops That Matter (Schoenberg, Eberlein & Hayward 2020) along a run of
  `m` (opts :from :to :dt :params :levels as sf/run; :every, sample each
  that many steps, 1; :max-length, :collapse as `loops`):

    {:t [..] :loops [..] :truncated :groups :scores [[..]] :relative [[..]] :dominant [..]}

  The loops are those of every edge seen at any sample (an edge flat at
  one state may matter at another); each link i -> j through flow k
  scores sign(w) |w dS_i / d(dS_j/dt)|, w its flow-resolved partial, dS_i
  the stock's change over the step and d(dS_j/dt) the change in j's net
  rate (0 when that does not change): the part of j's change in rate
  that i's change explains. A loop's score is the product of its links'
  (its sign the loop's polarity at that time), :relative each score over
  the sum of their sizes at that time, :dominant the index of the largest
  loop. The loops carry no :gain or :polarity (both change along the
  run)."
  [m {:keys [every params] :or {every 1} :as opts}]
  (let [r (sf/run m (select-keys opts [:from :to :dt :params :levels]))
        ts (:t r)
        stocks (keys (:stocks m))
        level (fn [i] (into {} (for [s stocks] [s (nth (get-in r [:stocks s]) i)])))
        rates (fn [i] (net-rates m (into {} (for [k (keys (:flows m))] [k (nth (get-in r [:flows k]) i)]))))
        idx (vec (range 0 (dec (count ts)) every))
        jacs (mapv #(flow-jacobian m (nth ts %) (level %) {:params params}) idx)
        union (apply merge jacs)
        {:keys [loops truncated groups]} (loops union opts)
        edges (distinct (mapcat :edges loops))
        scores (mapv (fn [i jac]
                       (let [l0 (level i) l1 (level (inc i)) r0 (rates i) r1 (rates (inc i))
                             link (into {} (for [[a b :as e] edges
                                                 :let [w (get jac e 0.0)
                                                       ds (- (l1 a) (l0 a))
                                                       dr (- (get r1 b 0.0) (get r0 b 0.0))]]
                                             [e (if (or (zero? w) (zero? dr)) 0.0 (* (Math/signum w) (Math/abs (/ (* w ds) dr))))]))]
                         (mapv (fn [l] (reduce * 1.0 (map link (:edges l)))) loops)))
                     idx jacs)
        relative (mapv (fn [ss] (let [tot (reduce + 0.0 (map #(Math/abs %) ss))]
                                  (mapv #(if (zero? tot) 0.0 (/ % tot)) ss)))
                       scores)]
    {:t (mapv ts idx)
     :loops (mapv #(dissoc % :gain :polarity) loops)
     :truncated truncated
     :groups groups
     :scores scores
     :relative relative
     :dominant (mapv (fn [ss] (when (seq ss) (apply max-key #(Math/abs (nth ss %)) (range (count ss))))) scores)}))

;; ---------------------------------------------------------------------
;; archetypes

(defn- info-link?
  "Edge i -> j through flow k is a reaction, not material: k flows into j
  and does not drain i."
  [m [i j k]]
  (let [{:keys [from to]} (get-in m [:flows k])]
    (and k (= to j) (not= from i))))

(defn- material? [m s] (not (get-in m [:stocks s :info])))

(defn- pairs
  "The two-stock loops (two edges, no collapsed chain) of polarity `pol`
  whose edges are reactions of sign `cross` into material stocks, each
  through a flow whose dependence on its own stock has sign `own`:
  [{:pair #{a b} :loop l :own [[a a k] [b b k']]}]; `source?`, the flows
  come from outside (no :from), not converted from another stock."
  [{:keys [loops jacobian model]} pol cross own source?]
  (let [sgn #(Math/signum (double (get jacobian % 0.0)))]
    (for [{:keys [stocks edges polarity] :as l} loops
          :when (and (= 2 (count stocks)) (= 2 (count edges)) (= pol polarity)
                     (every? #(material? model %) stocks)
                     (every? #(info-link? model %) edges)
                     (or (not source?) (every? (fn [[_ _ k]] (nil? (get-in model [:flows k :from]))) edges))
                     (every? #(= cross (sgn %)) edges))
          :let [selfs (mapv (fn [[_ j k]] [j j k]) edges)]
          :when (every? #(= own (sgn %)) selfs)]
      {:pair (set stocks) :loop l :own selfs})))

(defn- own-loops [loops selfs]
  (let [want (set (map (fn [[s _ k]] [[s] [k]]) selfs))]
    (filter #(want [(:stocks %) (:flows %)]) loops)))

(defn- escalation
  "Two material stocks, each adjusted toward a level set by the other: the
  flow into each, from outside (each actor's own build-up, not a
  conversion from another stock), rises with the other stock and falls with its own (a balancing loop through the same
  flow), so the pair is a reinforcing loop of two goal-seeking ones;
  :runaway when the cross gain exceeds the product of the own ones (k > 1)."
  [{:keys [loops jacobian] :as s}]
  (for [[pair ps] (group-by :pair (pairs s :reinforcing 1.0 -1.0 true))]
    (let [selfs (distinct (mapcat :own ps))
          runaway (some (fn [{:keys [loop own]}]
                          (> (:gain loop) (reduce * 1.0 (map #(get jacobian % 0.0) own))))
                        ps)]
      {:archetype :escalation :stocks (vec (sort pair)) :runaway (boolean runaway)
       :loops (vec (concat (map :loop ps) (own-loops loops selfs)))})))

(defn- success-to-the-successful
  "Two material stocks each gaining at the other's expense: the flow into
  each falls with the other stock (mutual inhibition, a reinforcing loop
  of two negative links) and rises with its own (a reinforcing loop
  through the same flow): an allocation by relative success."
  [{:keys [loops] :as s}]
  (for [[pair ps] (group-by :pair (pairs s :reinforcing -1.0 1.0 false))]
    {:archetype :success-to-the-successful :stocks (vec (sort pair))
     :loops (vec (concat (map :loop ps) (own-loops loops (distinct (mapcat :own ps)))))}))

(defn- policy-resistance
  "A stock two flows pull opposite ways, each closing its own gap on it (a
  balancing self-loop through the flow, the flow's elasticity to the
  stock at least 0.1), toward different goals, each the level at which
  that flow alone would stop (linearised). A goal of zero is decay, not
  an actor's goal: a proportional drain does not count."
  [{:keys [loops jacobian flows levels model]}]
  (for [[n ls] (group-by (comp first :stocks) (filter #(and (= 1 (count (:stocks %))) (= :balancing (:polarity %))) loops))
        :let [x (levels n)
              push (for [l ls :let [[_ _ k :as e] (first (:edges l))
                                    c (* (if (= n (get-in model [:flows k :to])) 1.0 -1.0) (get flows k 0.0))
                                    slope (get jacobian e 0.0)]
                         :when (and k (not (zero? c)) (neg? slope)
                                    (>= (/ (* (Math/abs slope) (Math/abs x)) (Math/abs c)) 0.1))
                         :let [goal (+ x (/ c (- slope)))]
                         :when (> (Math/abs goal) (* 1e-6 (scale x)))]
                     {:loop l :flow k :push c :goal goal})
              ups (filter #(pos? (:push %)) push)
              downs (filter #(neg? (:push %)) push)]
        :when (and (seq ups) (seq downs))]
    {:archetype :policy-resistance :stocks [n]
     :loops (mapv :loop (concat ups downs))
     :goals (into {} (map (juxt :flow :goal) (concat ups downs)))}))

(defn- self-feedback [jac n] (reduce + 0.0 (for [[[i j] w] jac :when (= i j n)] w)))

(defn- limits-to-growth-at
  "A stock with a reinforcing loop and a balancing one whose net
  self-feedback (the Jacobian's diagonal) falls as it rises 1%: the
  balance moving from growth toward the limit; :loops the shortest five
  reinforcing ones and five balancing ones whose gain grows."
  [{:keys [loops groups jacobian model t levels params]}]
  (for [n (distinct (mapcat :stocks loops))
        :when (not (contains? groups n))
        :let [on (filter #(some #{n} (:stocks %)) loops)
              rs (filter #(= :reinforcing (:polarity %)) on)
              bs (filter #(= :balancing (:polarity %)) on)]
        :when (and (seq rs) (seq bs))
        :let [jac' (flow-jacobian model t (update levels n #(if (zero? %) 1e-3 (* 1.01 %))) {:params params})
              f0 (self-feedback jacobian n) f1 (self-feedback jac' n)]
        :when (< (- f1 f0) (- (* 1e-6 (Math/abs f0))))
        :let [gain (fn [l] (reduce * 1.0 (map #(get jac' % 0.0) (:edges l))))
              strengthening (filter #(> (Math/abs (gain %)) (* (Math/abs (:gain %)) (+ 1.0 1e-6))) bs)]
        :when (seq strengthening)]
    {:archetype :limits-to-growth :stocks [n]
     :loops (vec (concat (take 5 (sort-by (comp count :stocks) rs)) (take 5 (sort-by (comp count :stocks) strengthening))))}))

(defn- limits-to-growth-run
  "A node whose loops' scores (the loops through it, as enumerated: up to
  the length cap) shift once from
  reinforcing to balancing and stay: some sample splits the run so that
  at least 80% of the samples before it (2 or more) have the reinforcing
  loops' scores above the balancing ones' and at least 80% from it on (2
  or more) the reverse; :shift the first such split's time."
  [{:keys [loops scores t]}]
  (let [members (reduce (fn [acc [i l]] (reduce #(update %1 %2 (fnil conj []) i) acc (distinct (:stocks l))))
                        {} (map-indexed vector loops))
        peak (fn [i sel] (reduce max 0.0 (map #(sel (nth % i)) scores)))]
    (for [[n ix] members
          :let [r-dom (mapv (fn [ss] (let [xs (map #(nth ss %) ix)]
                                       (> (reduce + 0.0 (filter pos? xs)) (- (reduce + 0.0 (filter neg? xs))))))
                            scores)
                cnt (count r-dom)
                split (first (for [k (range 2 (- cnt 1))
                                   :let [before (subvec r-dom 0 k) after (subvec r-dom k)]
                                   :when (and (>= (count (filter true? before)) (* 0.8 (count before)))
                                              (>= (count (filter false? after)) (* 0.8 (count after))))]
                               k))]
          :when split
          :let [top (fn [sel] (->> ix (map (fn [i] [i (peak i sel)])) (sort-by (comp - second)) (take 3) (map (comp loops first))))]]
      {:archetype :limits-to-growth :stocks [n]
       :shift (nth t split)
       :loops (vec (concat (top #(max 0.0 %)) (top #(max 0.0 (- %)))))})))

(defn archetypes
  "[{:archetype :stocks [..] :loops [..]}]: the system archetypes in a
  `structure` (flow-resolved), each with the stocks and loops that show
  it:
  - :escalation, two material stocks each adjusted toward a level set by
    the other (the flow into each, from outside, rises with the other and
    falls with its own: dA/dt = (k B - A)/tau), :runaway when k > 1;
  - :success-to-the-successful, two material stocks whose inflows each
    rise with their own stock and fall with the other's (mutual
    inhibition: an allocation by relative success);
  - :policy-resistance, a stock two flows pull opposite ways, each
    closing its own gap (a balancing loop through the flow, elasticity
    to the stock 0.1 or more), :goals the nonzero level at which each
    would stop;
  - :limits-to-growth, a stock with a reinforcing loop and a balancing
    one: with `dominance` (a run's loop scores), the reinforcing loops'
    scores giving way to the balancing ones' and staying so (:shift);
    without, its net self-feedback falling as it rises, a balancing
    loop's gain growing.
  Information stocks (perceptions, smoothings) are not actors: they take
  no part in escalation or success to the successful. Matching motifs on
  a large model is suggestive, not proof: read the evidence."
  ([s] (archetypes s nil))
  ([s dominance]
   (vec (concat (escalation s)
                (if dominance (limits-to-growth-run dominance) (limits-to-growth-at s))
                (policy-resistance s)
                (success-to-the-successful s)))))

;; ---------------------------------------------------------------------
;; leverage

(defn leverage
  "Each parameter's elasticity on `outcome` (a fn of a run), d ln y / d ln
  p by the central log-difference over p (1 +- 1%), the run's :params
  overrides kept, and the mean size of the elasticities in each class
  (`classes` {param class}, each a parameter of the model):
  {:by-param {p e} :by-class {class mean |e|} :reasons {p why}}. A zero
  parameter (:zero-param), an outcome that is zero or changes sign
  (:zero-outcome) or is not finite (:non-finite) gives nil, left out of
  its class's mean (nil when none is left).

  Parameters reach only the low end of Meadows' (1999) list (constants,
  buffers, delays and loop strengths as numbers); rules, information
  flows, structure, goals as structure and paradigms are not parameters,
  so :by-class is a sensitivity by label, not her ordering, and it
  depends on the outcome and horizon chosen."
  [m classes run-opts outcome]
  (when-let [bad (seq (remove (set (keys (:params m))) (keys classes)))]
    (throw (ex-info (str "bnm.archetypes/leverage: no parameters " (vec bad)) {:names (vec bad)})))
  (let [ps (merge (:params m) (:params run-opts))
        y (fn [overrides] (double (outcome (sf/run m (assoc run-opts :params (merge (:params run-opts) overrides))))))
        base (y {})
        finite? #(not (or (Double/isNaN %) (Double/isInfinite %)))
        res (into {} (for [[p _] classes
                           :let [v (double (ps p))]]
                       [p (cond (zero? v) {:reason :zero-param}
                                (not (finite? base)) {:reason :non-finite}
                                (zero? base) {:reason :zero-outcome}
                                :else (let [up (y {p (* 1.01 v)}) down (y {p (* 0.99 v)})]
                                        (cond (not (and (finite? up) (finite? down))) {:reason :non-finite}
                                              (not (pos? (* up down base base))) {:reason :zero-outcome}
                                              (not (pos? (* up base))) {:reason :zero-outcome}
                                              :else {:e (/ (Math/log (/ up down)) (Math/log (/ 1.01 0.99)))})))]))
        el (update-vals res :e)]
    {:by-param el
     :reasons (into {} (for [[p {:keys [reason]}] res :when reason] [p reason]))
     :by-class (into {} (for [[c ps] (group-by val classes)
                              :let [es (keep #(el (key %)) ps)]]
                          [c (when (seq es) (/ (reduce + (map #(Math/abs (double %)) es)) (count es)))]))}))
