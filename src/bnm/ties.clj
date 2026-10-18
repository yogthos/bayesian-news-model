(ns bnm.ties
  "Ties between countries learned from the events between them, never from a
  list of blocs (docs/STRUCTURE.md, plan step 1). Each country's partners
  are weighted by their share of its events with other countries over the
  trailing year, its top `k` kept; the ties move as the events do.

  From them, per country and quarter: its partners' protest and violence
  surges, near (by a predicate, e.g. the same subregion) and far; cross-
  pressure, the conflict between its own partners, weighted by both ties,
  which is how tension between patrons reaches a client without naming any
  patron; how concentrated its ties are; and its own conflict share with its
  partners. A placebo shuffles any such inputs across countries within each
  quarter, keeping their distribution and breaking who has them."
  (:require [bnm.math :as m]))

(def top-k 10)

(def labels
  [:far-protest :far-violence :near-protest :near-violence
   :cross-pressure :cross-pressure-change :concentration
   :own-conflict :own-conflict-change])

(defn- trailing
  "{[a b] {:coop :conflict}} summed over quarters q-3..q."
  [dyads q]
  (apply merge-with #(merge-with + %1 %2) {} (keep dyads (range (- q 3) (inc q)))))

(defn partner-weights
  "{country {partner weight}} from the year to quarter `q`: each country's
  top `k` partners by events with it, weights summing to one."
  [dyads q k]
  (let [vol (reduce-kv (fn [acc [a b] {:keys [coop conflict] :or {coop 0.0 conflict 0.0}}]
                         (let [v (+ coop conflict)]
                           (if (pos? v)
                             (-> acc (assoc-in [a b] v) (assoc-in [b a] v))
                             acc)))
                       {} (trailing dyads q))]
    (into {} (for [[c ps] vol
                   :let [top (take k (sort-by (comp - val) ps))
                         total (reduce + (map val top))]]
               [c (into {} (for [[p v] top] [p (/ v total)]))]))))

(defn conflict-shares
  "{[a b] share} sorted pairs: conflict over all events between them in the
  year to quarter `q`, shrunk by one event toward none."
  [dyads q]
  (into {} (for [[pair {:keys [coop conflict] :or {coop 0.0 conflict 0.0}}] (trailing dyads q)]
             [pair (/ conflict (+ coop conflict 1.0))])))

(defn- pair [a b] (if (neg? (compare a b)) [a b] [b a]))

(defn cross-pressure
  "The conflict between the partners in `w` ({partner weight}), each pair
  weighted by both ties."
  [w share]
  (let [ps (vec w)]
    (reduce + 0.0 (for [i (range (count ps)) j (range (inc i) (count ps))
                        :let [[a wa] (ps i) [b wb] (ps j)]]
                    (* wa wb (get share (pair a b) 0.0))))))

(defn- surge
  "Log change of `type`'s count for unit `u` in quarter `q` against its mean
  over the four quarters before."
  [counts u q type]
  (let [c #(get-in counts [[u %] type] 0.0)
        before (/ (reduce + (map c (range (- q 4) q))) 4.0)]
    (- (Math/log1p (c q)) (Math/log1p before))))

(defn features
  "{[country quarter] double[]} in the order of `labels`, from `dyads`
  ({quarter {[a b] {:coop :conflict}}}, pairs sorted), `counts` ({[unit
  quarter] {type count}}, the event-code mix's counts) and `near?`, a
  predicate on two countries. A country without partners that year has
  none. :weights-of, a function of the quarter giving {country {partner
  weight}}, weights partners another way (UN voting agreement); the conflict
  between them still comes from the events."
  ([dyads counts near?] (features dyads counts near? {}))
  ([dyads counts near? {:keys [weights-of]}]
   (let [qs (sort (keys dyads))
        state (memoize (fn [q] {:w (if weights-of (weights-of q) (partner-weights dyads q top-k))
                                :share (conflict-shares dyads q)}))]
    (into {}
          (for [q qs
                :let [{:keys [w share]} (state q)
                      prev (state (- q 4))]
                [u wu] w]
            (let [weighted (fn [near type]
                             (reduce + 0.0 (for [[p wp] wu :when (= near (boolean (near? u p)))]
                                             (* wp (surge counts p q type)))))
                  own (fn [{:keys [w share]}]
                        (reduce + 0.0 (for [[p wp] (get w u)] (* wp (get share (pair u p) 0.0)))))
                  cp (cross-pressure wu share)
                  cp0 (if-let [w0 (get (:w prev) u)] (cross-pressure w0 (:share prev)) cp)
                  oc (own (state q))
                  oc0 (if (get (:w prev) u) (own prev) oc)]
              [[u q] (double-array [(weighted false "protest-did") (weighted false "assault-did")
                                    (weighted true "protest-did") (weighted true "assault-did")
                                    cp (- cp cp0)
                                    (reduce + (map #(* % %) (vals wu)))
                                    oc (- oc oc0)])]))))))

(defn placebo
  "`xs` ({[unit quarter] x}) with each quarter's values shuffled among its
  units, seeded."
  [xs seed]
  (into {} (mapcat (fn [[q entries]]
                     (let [g (m/rng (+ (* 7919 (long seed)) (long q)))
                           vs (object-array (map val entries))]
                       (loop [i (dec (alength vs))]
                         (when (pos? i)
                           (let [j (long (* (inc i) (m/next-double! g)))
                                 t (aget vs i)]
                             (aset vs i (aget vs j)) (aset vs j t)
                             (recur (dec i)))))
                       (map (fn [[k _] v] [k v]) entries vs)))
                   (group-by (comp second key) xs))))
