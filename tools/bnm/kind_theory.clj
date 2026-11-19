(ns bnm.kind-theory
  "A theory forecast as a kind's year-node offset (bnm-1dw), banking's
  design (bnm.lattice-banking --theory-offset) for the kinds built by
  bnm.lattice-political: a pooled logit on stocks and flows read at the
  origin, refit at each December origin on the rows known by then
  (bnm.theory), its inputs standardized against every country's values at
  earlier origins. The level then comes from what is read at the origin,
  not from lagged outcomes (docs/INVARIANTS.md point 4)."
  (:require [bnm.signals :as sg]
            [bnm.theory :as th]))

(def ^:private sovereign
  "Sovereign defaults, fixed before any run (docs/CALIBRATION.md t109):
  the early-warning literature's country inputs (Manasse and Roubini 2009,
  rules of thumb: external debt, reserves, inflation, growth; Reinhart and
  Rogoff 2009) and the global push, US monetary tightening (the Volcker
  defaults of 1982-83, 2022's)."
  [{:name :external-debt :var "debt/external_gni" :read [:value] :stale 30}
   {:name :reserves :var "debt/reserves_months" :read [:value] :stale 30}
   {:name :inflation :var "economy/inflation" :read [:value] :stale 30}
   {:name :growth :var "economy/gdp_growth" :read [:value] :stale 30}
   {:name :us-rate-change24 :var "finance/policy_rate" :unit "USA" :read [:change 24] :stale 3}])

(def ^:private by-kind {"sovereign_default" sovereign})

(defn decls "A kind's theory signals, or nil." [kind] (get by-kind kind))

(defn- members-fn [{:keys [levels parent]}]
  (let [countries (second (first levels))
        ancestors (fn [c] (set (take-while some? (iterate parent c))))
        anc (into {} (for [c countries] [c (ancestors c)]))]
    (memoize (fn [place] (if (= "world" place) countries (filter #(contains? (anc %) place) countries))))))

(defn with-theory
  "`sp` with its year node's offset the theory's: `row` (fn [unit origin])
  -> `width` raw inputs (nil for missing), each standardized by
  bnm.theory/place-z (at least 20 earlier values), the logit refit by
  bnm.theory/offsets on the spec's year target; an aggregate takes the
  chance any member has one. Other scales keep the spec's offset."
  [{:keys [scales levels start end target offset] :as sp} row width]
  (let [countries (second (first levels))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        zs (vec (for [i (range width)] (th/place-z (fn [u t] (nth (row u t) i)) countries os 20)))
        off (th/offsets {:places countries :origins os :prior 1.0
                         :inputs (fn [u t] (mapv #(% u t) zs))
                         :target (fn [u t] (target :year u t))})
        members (members-fn sp)]
    (assoc sp
           :offset (fn [scale place t]
                     (if (= :year scale)
                       (th/any-of (keep #(get off [% t]) (members place)))
                       (when offset (offset scale place t))))
           :kind-theory {:offsets off :inputs (fn [u t] (mapv #(% u t) zs))})))

(defn with-kind-theory
  "The named kinds' specs with their declared theory (`decls`) read from
  the slow table `db`."
  [specs kinds db]
  (reduce (fn [acc k]
            (let [ds (or (decls k) (throw (ex-info (str "bnm.kind-theory: no theory declared for " k) {:kind k})))]
              (if (contains? acc k)
                (update acc k with-theory (:row (sg/assemble db ds)) (count ds))
                acc)))
          specs kinds))
