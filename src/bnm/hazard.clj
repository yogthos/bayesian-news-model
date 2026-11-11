(ns bnm.hazard
  "One pooled hazard over the buffer invariants (docs/PLAN.md 1.5; the
  registration in docs/PHASE-CHANGES.md, 11 November 2026; bead
  bnm-56c.6). Pure: no I/O.

  A break of any stock is taken to share a form in the invariants of
  bnm.buffers, which are times and ratios, so they compare across types
  of stock. The hazard is a discrete-time logit of an onset in a window:

    logit p = a + a_f + sum_j (b_j + d_fj) x_j

  slopes b shared by every family of breaks, the family's deviations
  a_f, d_f partially pooled (augur.hier/grouped-slopes: d_fj ~ N(0,
  t_j^2), t_j half-normal, by Laplace EM). A family the fit never saw,
  and a stock with no family, is scored by the shared part alone. Nothing
  here names a type of stock: a family is a label of the rows.

  - `features`: the registered transforms of one stock's invariant rows
    (bnm.buffers/invariants) into a map, nil where not measured.
  - `fit`, `predict`, `pooled`: the model on rows {:f features :y :family}.
  - `label-rows`: panel place-years into labelled rows per window.
  - `auc`, `recall-at`, `cluster-interval`: the tests' measures."
  (:require [augur.hier :as h]
            [augur.math :as m]
            [augur.score :as sc]
            [clojure.string]))

;; --- features ------------------------------------------------------------------

(defn- clip [lo hi x] (when (some? x) (max lo (min hi (double x)))))
(defn- logf [floor x] (when (some? x) (Math/log (max floor (double x)))))
(defn- bins [cuts x] (when (some? x) (mapv #(if (< (double x) %) 1.0 0.0) cuts)))

(def groups
  "The registered feature groups: each a name, its columns, and how they
  come from the invariant rows ({[invariant window] row}). A group is
  missing when its first column is nil; one missing indicator a group."
  [{:group :cover1 :cols [:cover1<0.25 :cover1<0.5 :cover1<1 :cover1<2]
    :from (fn [g] (let [r (g [:cover 1])]
                    (when (some? (:value r))
                      (bins [0.25 0.5 1.0 2.0] (:value r)))))}
   {:group :cover3 :cols [:log-cover3] :from (fn [g] [(logf 0.01 (some-> (:value (g [:cover 3])) (min 100.0)))])}
   {:group :drain :cols [:drain1] :from (fn [g] [(clip -2.0 2.0 (:value (g [:drain-rate 1])))])}
   {:group :gross :cols [:log-gross :gross<0.125 :gross<0.25 :gross<0.5]
    :from (fn [g] (let [v (:value (g [:gross-cover 1]))]
                    (when (some? v) (into [(logf 0.001 v)] (bins [0.125 0.25 0.5] v)))))}
   {:group :claim :cols [:log-claim] :from (fn [g] [(logf 0.001 (:value (g [:claim-gap nil])))])}
   {:group :rigidity :cols [:rigidity] :from (fn [g] [(clip 0.0 1.0 (:value (g [:rigidity nil])))])}
   {:group :restoration :cols [:restoration] :from (fn [g] [(clip -5.0 5.0 (:value (g [:restoration 1])))])}
   {:group :carry :cols [:carry] :from (fn [g] [(clip -1.0 1.0 (:value (g [:carry nil])))])}
   {:group :idle :cols [:idle-share] :from (fn [g] [(clip 0.0 1.0 (:value (g [:idle-share nil])))])}
   {:group :hhi :cols [:holder-hhi] :from (fn [g] [(clip 0.0 1.0 (:value (g [:holder-hhi nil])))])}
   {:group :leverage :cols [:log-leverage] :from (fn [g] [(logf 0.01 (:value (g [:holder-leverage nil])))])}
   {:group :market :cols [:log-market] :from (fn [g] [(logf 0.01 (:value (g [:market-reading nil])))])}
   {:group :disp5 :cols [:disp5] :from (fn [g] [(clip -5.0 5.0 (:value (g [:displacement 5])))])}
   {:group :disp10 :cols [:disp10] :from (fn [g] [(clip -5.0 5.0 (:value (g [:displacement 10])))])}
   {:group :disp20 :cols [:disp20] :from (fn [g] [(clip -5.0 5.0 (:value (g [:displacement 20])))])}
   {:group :resonance :cols [:resonance] :from (fn [g] [(clip 0.0 5.0 (:value (g [:resonance nil])))])}
   {:group :sxf :cols [:log1p-sxf] :from (fn [g] [(some-> (:value (g [:stock-x-flow 10])) double Math/log1p)])}])

(defn- row-key [r]
  [(:invariant r) (when (#{:cover :drain-rate :gross-cover :restoration :displacement :stock-x-flow} (:invariant r)) (:window r))])

(defn features
  "The registered features of one stock's invariant rows: {column value},
  nil where the invariant is not measured. A cover at the cap (not
  draining) is measured: it falls in no bin."
  [rows]
  (let [g (into {} (map (juxt row-key identity)) rows)]
    (into {} (for [{:keys [cols from]} groups
                   :let [vs (from g)
                         vs (if (and vs (some? (first vs))) vs (repeat (count cols) nil))]
                   [c v] (map vector cols vs)]
               [c (some-> v double)]))))

(defn measured?
  "Whether any invariant of the features is measured."
  [f]
  (boolean (some some? (vals f))))

(defn group-present? [f {:keys [cols]}] (some? (get f (first cols))))

;; --- the design --------------------------------------------------------------------

(defn design
  "The columns a fit uses: the groups measured on at least `min-share`
  of the training features `fs`, with each group's columns and a missing
  indicator when it is ever missing; and the mean of each column where
  measured (a missing value is set to it). {:groups :cols :means}."
  [fs min-share]
  (let [n (count fs)
        used (filterv (fn [gr] (>= (count (filter #(group-present? % gr) fs)) (* min-share n))) groups)
        means (into {} (for [{:keys [cols] :as gr} used
                             :let [have (filter #(group-present? % gr) fs)]
                             c cols]
                         [c (/ (reduce + 0.0 (map #(double (get % c)) have)) (max 1 (count have)))]))
        missing? (into {} (for [gr used] [(:group gr) (boolean (some #(not (group-present? % gr)) fs))]))]
    {:groups (mapv :group used)
     :cols (vec (concat (mapcat :cols used) (for [gr used :when (missing? (:group gr))] [:missing (:group gr)])))
     :means means}))

(defn x-of
  "The input vector [1 x...] of features `f` under design `d`."
  [{:keys [cols means]} f]
  (let [gm (into {} (map (juxt :group identity)) groups)]
    (into [1.0] (for [c cols]
                  (if (vector? c)
                    (if (group-present? f (gm (second c))) 0.0 1.0)
                    (double (or (get f c) (means c))))))))

;; --- the model ------------------------------------------------------------------------

(def registered
  "The registered priors: b_j ~ N(0, 1) on standardized inputs; family
  deviations with half-normal scales 1.0 (intercepts) and 0.5 (slopes)."
  {:prior 1.0 :sd-intercept 1.0 :sd-slope 0.5 :tau2 0.1 :sweeps 15 :min-share 0.01})

(defn fit
  "The pooled hazard on rows {:f features :y bool :family f-or-nil}."
  ([rows] (fit rows registered))
  ([rows {:keys [prior sd-intercept sd-slope tau2 sweeps min-share]}]
   (let [rows (vec rows)
         d (design (map :f rows) min-share)
         k (inc (count (:cols d)))
         xs (mapv #(hash-map :x (x-of d (:f %)) :y (boolean (:y %)) :w 1.0 :g (:family %)) rows)
         g (h/grouped-slopes xs k {:prior prior :tau2 (vec (repeat k tau2))
                                   :sd-prior (vec (cons sd-intercept (repeat (dec k) sd-slope)))
                                   :sweeps sweeps})]
     {:design d :fit g :n (count rows) :events (count (filter :y rows))})))

(defn predict
  "The hazard of features `f` for `family` (its deviations when the fit
  has them; the shared part for nil or a family never seen)."
  ^double [model f family]
  (h/grouped-predict (:fit model) (x-of (:design model) f) family))

(defn pooled "The shared part's hazard: the scanner's score." ^double [model f] (predict model f nil))

(defn coefficients
  "{column [shared {family deviation}]} for a report."
  [{:keys [design fit]}]
  (into (sorted-map-by #(compare (str %1) (str %2)))
        (map-indexed (fn [i c] [c [(nth (:beta fit) i) (into {} (for [[g d] (:dev fit)] [g (nth d i)]))]])
                     (cons :intercept (:cols design)))))

;; --- labels --------------------------------------------------------------------------------

(defn month-of "\"YYYY-MM\" as year x 12 + month - 1." [s]
  (when (and s (re-matches #"\d{4}-\d{2}.*" s))
    (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7))))))

(defn reserve-class
  "An episode's class for the reserve-crisis labels: :gra when its only
  catalogue is the IMF GRA drawing rule, :rr when, from 1970, its only
  catalogue besides that rule is Reinhart-Rogoff's currency column, else
  :main."
  [{:keys [catalogues start-year]}]
  (let [cs (set catalogues)]
    (cond (every? #{"rule:imf-gra"} cs) :gra
          (and (>= start-year 1970) (every? #{"rule:imf-gra" "rr"} cs)) :rr
          :else :main)))

(defn label-rows
  "Labelled rows from panel rows ({:place :year :family :break :episodes
  :ongoing :known :covered_by}, strings as read): one per place, family
  and year with :y12 and :y24 (onset in the year, or in it or the next),
  :known12 and :known24 (months), and :class (:main, or for a reserve
  break whose episodes are all :gra or :rr, that class). Rows outside a
  catalogue's coverage or inside an earlier episode are left out.
  `episode-class` maps an episode id to its class."
  [panel episode-class]
  (let [cls (fn [r] (if (= "1" (:break r))
                      (let [cs (map episode-class (filter seq (clojure.string/split (:episodes r) #";")))]
                        (cond (some #{:main} cs) :main (some #{:rr} cs) :rr (seq cs) :gra :else :main))
                      :main))
        by (into {} (map (fn [r] [[(:place r) (:family r) (parse-long (:year r))] r])) panel)]
    (vec (for [r panel
               :let [y (parse-long (:year r))]
               :when (and (seq (:covered_by r)) (empty? (:ongoing r)))
               :let [nx (by [(:place r) (:family r) (inc y)])
                     b (= "1" (:break r)) k (month-of (:known r))
                     nb (when nx (= "1" (:break nx)))]]
           {:place (:place r) :family (:family r) :year y
            :y12 b :known12 k :class (cls r)
            :y24 (cond b true nx nb :else nil)
            :class24 (if (and (not b) nb) (cls nx) (cls r))
            :known24 (cond b k nx (max k (month-of (:known nx))) :else nil)}))))

;; --- measures -------------------------------------------------------------------------------

(defn auc "AUC of scores against labels, nil without both." [ps ys] (sc/auc (map double ps) (map boolean ys)))

(defn recall-at
  "Share of the positives in the top `q` of the rows by score (ties at
  the cut share the slots left)."
  [ps ys q]
  (let [n (count ps) pos (count (filter identity ys))]
    (when (pos? pos)
      (let [k (* q n)
            sorted (sort-by - (map double ps))
            cut (nth sorted (max 0 (dec (long (Math/ceil k)))))
            above (filter (fn [[p y]] (> p cut)) (map vector ps ys))
            at (filter (fn [[p y]] (= (double p) cut)) (map vector ps ys))
            slots (- k (count above))
            hits (+ (count (filter second above))
                    (if (seq at) (* (/ (double slots) (count at)) (count (filter second at))) 0.0))]
        (/ hits pos)))))

(defn precision-at
  "Share of the top `q` of rows by score that are positive (ties shared)."
  [ps ys q]
  (let [n (count ps) pos (count (filter identity ys))]
    (when (pos? n)
      (if (zero? pos) 0.0 (/ (* (recall-at ps ys q) pos) (* q n))))))

(defn cluster-draws
  "`reps` statistics of `stat` on rows resampled by cluster: the clusters
  (`cluster-of` each row) drawn with replacement, each draw's rows all of
  its clusters' rows (augur.math/rng-mixed `seed`). nil statistics are
  dropped."
  [rows cluster-of stat reps seed]
  (let [blocks (vec (vals (group-by cluster-of rows)))
        k (count blocks)
        g (m/rng-mixed seed k)]
    (vec (keep (fn [_] (stat (vec (mapcat (fn [_] (blocks (min (dec k) (long (* k (m/next-double! g)))))) (range k)))))
               (range reps)))))

(defn interval
  "The 5% and 95% points of draws."
  [xs]
  (when (seq xs)
    (let [v (vec (sort xs)) n (count v)]
      [(v (min (dec n) (long (* 0.05 n)))) (v (min (dec n) (long (* 0.95 n))))])))

;; --- what a fit at an origin may read ---------------------------------------------------

(def windows
  "The registered windows: months, the label and known keys, and the gap
  (the window plus the catalogues' known-lag of up to 11 months, rounded
  to whole years)."
  {12 {:y :y12 :known :known12 :class :class12 :gap 24}
   24 {:y :y24 :known :known24 :class :class24 :gap 36}})

(defn label
  "A row's label under window `w` and the label set (:main drops reserve
  breaks of class :gra and :rr; :all keeps them; :no-gra drops only
  :gra): true, false, or nil when it has none."
  [row w labels]
  (let [{:keys [y class]} (windows w)
        yv (get row y)
        c (get row (if (= 12 w) :class :class24))]
    (cond (nil? yv) nil
          (and yv (= :main labels) (not= :main c)) nil
          (and yv (= :no-gra labels) (= :gra c)) nil
          :else (boolean yv))))

(defn trainable
  "The rows a fit used from origin month `T` may train on under window
  `w`: a label known by T, the row's origin (January of its year) at
  least the window's gap before T."
  [rows T w labels]
  (let [{:keys [known gap]} (windows w)]
    (filterv #(and (some? (label % w labels))
                   (some? (get % known)) (<= (long (get % known)) (long T))
                   (<= (+ (* 12 (long (:year %))) gap) (long T)))
             rows)))
