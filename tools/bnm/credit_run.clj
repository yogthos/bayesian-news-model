(ns bnm.credit-run
  "The credit-to-GDP gap (Drehmann & Juselius 2014; the Basel III buffer
  guide): private non-financial credit to GDP less its one-sided
  Hodrick-Prescott trend, in percentage points of GDP, per country and year
  as known (bead bnm-3qu.2).

  The debt series are keen's (bnm.keen-run): BIS total credit to the
  private non-financial sector (finance/credit_gdp), JST bank loans over
  GDP (tloans/gdp), World Bank domestic credit to the private sector; at
  each year end one of them, chosen as keen chooses d (bnm.keen/choose-d:
  the first with 20 data years known, else the longest), and the gap is
  computed on that source's own series alone, never spliced, so a change of
  source is not a jump in the gap.

  One-sided: at year end Y the trend is fitted to the data years before Y
  published by Y (bnm.keen/known), and the gap is the last of them less
  the trend's last value; the filter is refitted each year, so no value is
  revised by later data. lambda: BIS's 400,000 is for quarterly data; on
  annual data the same cycle length is 400,000 / 4^4 = 1,562.5 (Ravn &
  Uhlig 2002). At least 10 contiguous data years, the latest no more than
  3 years before Y.

    jolt -A:tools -M -m bnm.credit-run [--out data/derived/stocks/credit.csv]

  Writes iso,year,variable,value: credit/gap (pp of GDP) and credit/source
  (1 BIS, 2 JST, 3 World Bank), the year being the year known; prints the
  correlation with BIS's own published gap (finance/credit_gap, quarterly,
  its data year's mean) where both exist."
  (:require [bnm.keen :as keen]
            [bnm.keen-run :as kr]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def annual-lambda (/ 400000.0 256.0))

(def defaults {:lambda annual-lambda :min-years 10 :max-age 3 :sources kr/d-sources :source-min-years 20})

(defn hp-trend
  "The Hodrick-Prescott trend of `ys` with smoothing `lambda`: the solution
  of (I + lambda D'D) tau = y, D the second-difference operator, by a
  banded Cholesky factorization (the system is pentadiagonal)."
  [ys lambda]
  (let [ys (mapv double ys)
        n (count ys)
        lam (double lambda)]
    (if (or (< n 3) (zero? lam))
      ys
      (let [;; D'D's diagonals: band 0, 1 and 2 of the symmetric matrix
            c [1.0 -2.0 1.0]
            rows-at (fn [k] (range (max 0 (- k 2)) (inc (min k (- n 3)))))
            d0 (fn [k] (reduce + (for [i (rows-at k) :let [a (c (- k i))]] (* a a))))
            d1 (fn [k] (reduce + (for [i (rows-at k) :when (<= (- (inc k) i) 2)] (* (c (- k i)) (c (- (inc k) i)))))) ; (k, k+1)
            a0 (double-array (for [k (range n)] (+ 1.0 (* lam (d0 k)))))
            a1 (double-array (for [k (range (dec n))] (* lam (d1 k))))
            a2 (double-array (for [_ (range (- n 2))] lam))
            ;; LDL': L unit lower with bands l1, l2; D diag
            dd (double-array n) l1 (double-array n) l2 (double-array n)]
        (doseq [k (range n)]
          (let [l2k (when (>= k 2) (/ (aget a2 (- k 2)) (aget dd (- k 2))))
                _ (when l2k (aset l2 k l2k))
                l1k (when (>= k 1)
                      (/ (- (aget a1 (dec k))
                            (if (>= k 2) (* (aget l2 k) (aget dd (- k 2)) (aget l1 (dec k))) 0.0))
                         (aget dd (dec k))))
                _ (when l1k (aset l1 k l1k))]
            (aset dd k (- (aget a0 k)
                          (if (>= k 1) (* (aget l1 k) (aget l1 k) (aget dd (dec k))) 0.0)
                          (if (>= k 2) (* (aget l2 k) (aget l2 k) (aget dd (- k 2))) 0.0)))))
        ;; forward: L z = y; scale: D w = z; back: L' tau = w
        (let [z (double-array n) tau (double-array n)]
          (doseq [k (range n)]
            (aset z k (- (ys k)
                         (if (>= k 1) (* (aget l1 k) (aget z (dec k))) 0.0)
                         (if (>= k 2) (* (aget l2 k) (aget z (- k 2))) 0.0))))
          (doseq [k (range n)] (aset z k (/ (aget z k) (aget dd k))))
          (doseq [k (reverse (range n))]
            (aset tau k (- (aget z k)
                           (if (< k (dec n)) (* (aget l1 (inc k)) (aget tau (inc k))) 0.0)
                           (if (< k (- n 2)) (* (aget l2 (+ k 2)) (aget tau (+ k 2))) 0.0))))
          (vec tau))))))

(defn- tail-run
  "The trailing run of consecutive data years of `s` (sorted-map yr x)."
  [s]
  (let [ys (reverse (seq s))]
    (when (seq ys)
      (reverse (cons (first ys)
                     (map second (take-while (fn [[[a _] [b _]]] (= b (dec a))) (partition 2 1 ys))))))))

(defn gap-at
  "{:gap :source :data-year :years} at year end `y` from a country's debt
  `panel` ({source {data-year [year-known ratio]}}, ratio a fraction of
  GDP), nil when no source has enough recent years: the one-sided gap of
  the source chosen (see the namespace)."
  [panel y opts]
  (let [{:keys [lambda min-years max-age sources source-min-years]} (merge defaults opts)
        k (keen/known (select-keys panel sources) y)
        [i s] (keen/choose-d k sources source-min-years)
        run (some-> s tail-run)]
    (when (and run (>= (count run) min-years) (>= (first (last run)) (- y max-age)))
      (let [xs (mapv second run)
            tau (hp-trend xs lambda)]
        {:gap (* 100.0 (- (peek xs) (peek tau)))
         :source (inc i)
         :data-year (first (last run))
         :years (count run)}))))

(defn country-rows
  "[{:iso :year :variable :value}] for each year end in `years`."
  [iso panel years opts]
  (vec (for [y years :let [g (gap-at panel y opts)] :when g
             [v x] [["credit/gap" (:gap g)] ["credit/source" (double (:source g))]]]
         {:iso iso :year y :variable v :value x})))

(defn- bis-published
  "{[iso data-year] mean} of BIS's own published gap, quarterly data years
  complete."
  [path]
  (let [acc (java.util.HashMap.)]
    (with-open [r (io/reader path)]
      (doseq [line (line-seq r)
              :let [[unit v f pend _ value] (str/split line #"\t" -1)]
              :when (and (= v "finance/credit_gap") (= f "q"))
              :let [x (parse-double value)]
              :when x]
        (let [k [unit (parse-long (subs pend 0 4))]]
          (.put acc k (conj (or (.get acc k) []) x)))))
    (into {} (for [[k xs] acc :when (= 4 (count xs))] [k (/ (reduce + xs) 4.0)]))))

(defn- corr [ps]
  (let [n (count ps) mx (/ (reduce + (map first ps)) n) my (/ (reduce + (map second ps)) n)
        sxy (reduce + (map (fn [[x y]] (* (- x mx) (- y my))) ps))
        sxx (reduce + (map (fn [[x _]] (* (- x mx) (- x mx))) ps))
        syy (reduce + (map (fn [[_ y]] (* (- y my) (- y my))) ps))]
    (/ sxy (Math/sqrt (* sxx syy)))))

(defn -main [& args]
  (let [opts (apply hash-map args)
        out (get opts "--out" "data/derived/stocks/credit.csv")
        slow (kr/read-slow "data/slow/series.tsv" "data/slow/variables.tsv")
        jst (kr/jst-panels "data/raw/slow/jst/jst.csv")
        isos (sort (set (concat (keys jst) (for [[[iso v] s] slow :when (and (#{"finance/credit_gdp" "finance/private_credit_gdp"} v) (seq s))] iso))))
        years (range 1880 2027)
        rows (vec (mapcat (fn [iso] (country-rows iso (select-keys (:panel (kr/panel iso slow jst)) kr/d-sources) years {})) isos))
        gaps (filter #(= "credit/gap" (:variable %)) rows)]
    (io/make-parents out)
    (with-open [w (io/writer out)]
      (.write w "iso,year,variable,value\n")
      (doseq [{:keys [iso year variable value]} rows] (.write w (str iso "," year "," variable "," value "\n"))))
    (println "wrote" (count rows) "rows to" out ";" (count (distinct (map :iso gaps))) "countries;"
             "1960-2002:" (count (filter #(<= 1960 (:year %) 2002) gaps)) "country-years,"
             (count (distinct (map :iso (filter #(<= 1960 (:year %) 2002) gaps)))) "countries")
    ;; against BIS's own (two-sided revisions aside, the same construction
    ;; on quarterly data): the gap at Y against BIS's for data year Y - 1,
    ;; where the source is BIS
    (let [bis (bis-published "data/slow/series.tsv")
          src (into {} (for [{:keys [iso year variable value]} rows :when (= variable "credit/source")] [[iso year] value]))
          ps (for [{:keys [iso year value]} gaps
                   :let [b (get bis [iso (dec year)])]
                   :when (and b (= 1.0 (src [iso year])))]
               [value b])]
      (println "against BIS's published gap (source BIS, data year Y - 1):" (count ps) "country-years, correlation"
               (when (> (count ps) 2) (format "%.4f" (corr ps)))))))
