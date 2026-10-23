(ns bnm.econ-coupling
  "Coupling: do a deficit country's liabilities to a surplus partner rise
  with the bilateral deficit? (bead bnm-3ka.8; docs/ECONOMIC-CHARACTER.md
  section 4, H5). Descriptive, with pair and year fixed effects.

  Input: scripts/econ-coupling.py's dyads (holder h, issuer i, data year,
  known, h's goods surplus with i, h's portfolio (PIP), direct (DIP) and
  nationality-restated portfolio (GCAP) positions in i, i's GDP; USD mn).

  An observation is an ordered pair in a data year whose position is held
  that year and the one before: y = the change in h's claims on i (i's new
  liabilities to h) over i's GDP, x = h's surplus with i over i's GDP. A
  slope of 1 would mean the bilateral deficit is matched one for one by the
  surplus partner's acquisition of the deficit country's assets. Valuation
  changes are in the positions, so this is gross holdings, not flows. The
  fit is OLS on x with a fixed effect per ordered pair and per year (two-way
  demeaning by alternating projections), standard errors clustered by
  ordered pair. x and y are clipped at their 1st and 99th percentiles
  (set before the fit, against offshore centres' positions far above their
  GDP).

  Stock (data/derived/stocks/econ-coupling.csv, year = known, with known
  and data_year columns), per issuer and data year:
    econ/coupling-liability-share  the share of i's PIP plus DIP liabilities
                                   to reporting holders held by partners
                                   with a goods surplus with i that year (at
                                   least three holders)
    econ/coupling-slope            the fixed-effects slope on i's pairs over
                                   the five data years to y (PIP plus DIP;
                                   at least 20 observations and 5 partners)
  each known when the latest dyad row used is."
  (:require [augur.math :as m]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- the fit --------------------------------------------------------------------------

(defn- demean-by
  "Each value less the mean of its key's values."
  [^doubles v keys]
  (let [n (alength v)
        sums (reduce (fn [acc i] (update acc (nth keys i) (fn [[s c]] [(+ (or s 0.0) (aget v i)) (inc (or c 0))]))) {} (range n))
        out (double-array n)]
    (dotimes [i n] (let [[s c] (sums (nth keys i))] (aset out i (- (aget v i) (/ s c)))))
    out))

(defn two-way-demean
  "`v` with both fixed effects (keys `ga` and `gb`) projected out."
  [^doubles v ga gb]
  (loop [v v it 0]
    (let [w (demean-by (demean-by v ga) gb)
          d (reduce max 0.0 (map #(Math/abs (- (double %1) (double %2))) w v))]
      (if (or (< d 1e-10) (>= it 500)) w (recur w (inc it))))))

(defn fe-ols
  "OLS of :y on :x (a vector of k regressors) with a fixed effect per :g
  and per :t, clustered by :g (or :cluster). {:b :se :t :n :groups}."
  [rows]
  (let [rows (vec rows) n (count rows) k (count (:x (first rows)))
        ga (mapv :g rows) gb (mapv :t rows) cl (mapv #(or (:cluster %) (:g %)) rows)
        y (two-way-demean (double-array (map :y rows)) ga gb)
        xs (vec (for [j (range k)] (two-way-demean (double-array (map #(nth (:x %) j) rows)) ga gb)))
        xx (double-array (* k k)) xy (double-array k)]
    (dotimes [a k]
      (aset xy a (reduce + (map * (xs a) y)))
      (dotimes [b k] (aset xx (+ (* a k) b) (reduce + (map * (xs a) (xs b))))))
    (let [b (m/solve-spd xx xy k)
          e (double-array n)
          _ (dotimes [i n] (aset e i (- (aget y i) (reduce + (for [j (range k)] (* (aget b j) (aget ^doubles (xs j) i)))))))
          ;; the meat: per cluster, the score's outer product
          scores (vals (reduce (fn [acc i] (update acc (cl i) (fn [s] (let [s (or s (double-array k))]
                                                                         (dotimes [j k] (aset ^doubles s j (+ (aget ^doubles s j) (* (aget e i) (aget ^doubles (xs j) i)))))
                                                                         s))))
                               {} (range n)))
          G (count scores)
          meat (double-array (* k k))
          _ (doseq [^doubles s scores] (dotimes [a k] (dotimes [c k] (aset meat (+ (* a k) c) (+ (aget meat (+ (* a k) c)) (* (aget s a) (aget s c)))))))
          inv (let [cols (for [j (range k)] (m/solve-spd xx (double-array (for [i (range k)] (if (= i j) 1.0 0.0))) k))]
                (double-array (for [i (range k) j (range k)] (aget ^doubles (nth cols j) i))))
          mul (fn [^doubles p ^doubles q] (double-array (for [i (range k) j (range k)] (reduce + (for [l (range k)] (* (aget p (+ (* i k) l)) (aget q (+ (* l k) j))))))))
          v (mul (mul inv meat) inv)
          adj (if (> G 1) (/ G (- G 1.0)) 1.0)
          se (vec (for [j (range k)] (Math/sqrt (* adj (aget v (+ (* j k) j))))))]
      {:b (vec b) :se se :t (mapv #(if (pos? %2) (/ %1 %2) Double/NaN) (vec b) se) :n n :groups G})))

(defn winsorizer
  "(fn [v]) clipping at the q and 1-q quantiles of `xs`."
  [xs q]
  (let [a (double-array (sort xs))
        lo (m/quantile a q) hi (m/quantile a (- 1.0 q))]
    (fn [v] (min hi (max lo (double v))))))

;; --- observations -----------------------------------------------------------------------

(defn position
  "A row's position for measure :pip, :dip, :gcap or :pip+dip (both held)."
  [row measure]
  (if (= measure :pip+dip)
    (when (and (:pip row) (:dip row)) (+ (:pip row) (:dip row)))
    (get row measure)))

(defn observations
  "[{:g [h i] :t year :known :x [surplus/gdp] :y change/gdp :h :i}] from
  consecutive data years of each pair."
  [rows measure]
  (vec (for [[[h i] rs] (group-by (juxt :h :i) rows)
             :let [by (into {} (map (juxt :year identity) rs))]
             [y r] (sort-by key by)
             :let [p (get by (dec y))
                   c1 (position r measure) c0 (some-> p (position measure))
                   gdp (:gdp-i r)]
             :when (and c1 c0 (:surplus r) gdp (pos? gdp))]
         {:g [h i] :t y :h h :i i :known (max (:known r) (:known p))
          :x [(/ (:surplus r) gdp)] :y (/ (- c1 c0) gdp)})))

(defn clip
  "Observations with x and y clipped at their 1st and 99th percentiles."
  [obs]
  (if (< (count obs) 10)
    obs
    (let [fy (winsorizer (map :y obs) 0.01)
          fx (winsorizer (map (comp first :x) obs) 0.01)]
      (mapv #(assoc % :y (fy (:y %)) :x (into [(fx (first (:x %)))] (rest (:x %)))) obs))))

;; --- the stock ---------------------------------------------------------------------------

(def slope-window 5)

(defn stock-rows
  "The per-issuer stock rows (see the ns doc)."
  [rows]
  (let [by-issuer (group-by :i rows)
        share (for [[i rs] by-issuer [y yr] (group-by :year rs)
                    :let [held (filter #(and (position % :pip+dip) (:surplus %)) yr)]
                    :when (>= (count held) 3)
                    :let [tot (reduce + (map #(position % :pip+dip) held))]
                    :when (pos? tot)]
                {:iso i :data-year y :known (reduce max (map :known held)) :variable "econ/coupling-liability-share"
                 :value (/ (reduce + (map #(position % :pip+dip) (filter #(pos? (:surplus %)) held))) tot)})
        slope (for [[i rs] by-issuer
                    :let [obs (observations rs :pip+dip)]
                    y (sort (distinct (map :t obs)))
                    :let [w (filter #(<= (- y slope-window -1) (:t %) y) obs)]
                    :when (and (>= (count w) 20) (>= (count (distinct (map :g w))) 5)
                               (> (count (distinct (map (comp first :x) w))) 2))
                    :let [{:keys [b]} (fe-ols w)]
                    :when (< (Math/abs (double (first b))) Double/POSITIVE_INFINITY)]
                {:iso i :data-year y :known (reduce max (map :known w)) :variable "econ/coupling-slope" :value (first b)})]
    (->> (concat share slope)
         (map #(assoc % :year (:known %)))
         (sort-by (juxt :variable :iso :data-year))
         vec)))

;; --- io ----------------------------------------------------------------------------------

(defn- num [s] (when (seq s) (parse-double s)))

(defn read-pairs [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ix (zipmap (str/split h #",") (range))]
      (mapv (fn [l]
              (let [f (str/split l #"," -1) g #(nth f (ix %))]
                {:h (g "holder") :i (g "issuer") :year (parse-long (g "year")) :known (parse-long (g "known"))
                 :surplus (num (g "surplus")) :pip (num (g "pip")) :dip (num (g "dip")) :gcap (num (g "gcap"))
                 :gdp-i (num (g "gdp_i")) :gdp-h (num (g "gdp_h"))}))
            ls))))

(defn write-stock [rows path]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value,known,data_year\n")
    (doseq [{:keys [iso year variable value known data-year]} rows]
      (.write w (str iso "," year "," variable "," (format "%.6f" (double value)) "," known "," data-year "\n")))))
