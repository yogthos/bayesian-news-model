(ns bnm.us-invariants
  "The dimension-free invariants of docs/INVARIANTS.md for the US monthly
  hazard: each crash stretches a different stock (credit and housing in
  2007, inflation in 2022, valuation in 2000), so instead of naming the
  channel, these measure how far the whole state is from its own history.

  Dimensions, each read a month late (quarterly and annual ones as last
  published): credit and household credit to GDP, real house prices (BIS),
  CAPE, the default spread, the long and bill rates, inflation over 12
  months, unemployment, industrial production, payrolls, real oil, and the
  wage share, profit rate and investment share (BEA, bnm.us-polecon).

    us-norm, us-breadth   root mean square stretch against the previous ten
                          years, and the share of dimensions past 1.5
    us-norm-short/long    the same against five and twenty years
    us-resonance          the smallest of the three: displaced at every scale
    us-flow-norm          each dimension's 12-month change against its own
                          past changes: the flows turning
    us-stock-x-flow       a stretched stock with a turning flow
    crisis-transfer       the log odds of a banking crisis within two years,
                          from the hazard fitted on 18 countries since 1870
                          (credit, mortgages and house prices stretched over twenty years, and
                          the displacement of the whole state), applied to the
                          same US stretches: what US history alone cannot teach,
                          since no US equity crash before 1990 came from credit"
  (:require [bnm.invariants :as iv]
            [bnm.polecon :as pe]
            [bnm.stocks :as sk]
            [bnm.us-polecon :as upc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def names [:us-norm :us-breadth :us-norm-short :us-norm-long :us-resonance :us-flow-norm
            :us-stock-x-flow :us-invariants-present :crisis-transfer :credit-gap])

(defn read-betas
  "{year [b0 b-credit b-mortgages b-house b-norm]}: the banking-crisis hazard
  fitted on the Macrohistory panel by year (bnm.macrohistory, crisis-betas.tsv)."
  [run]
  (let [f (io/file (str "data/runs/" run "/crisis-betas.tsv"))]
    (if (.exists f)
      (into (sorted-map) (for [line (str/split-lines (slurp f)) :let [[y & bs] (str/split line #"\t")]]
                           [(parse-long y) (mapv parse-double bs)]))
      (sorted-map))))

(defn- read-monthly [path]
  (with-open [r (io/reader path)]
    (into {} (for [line (rest (line-seq r))
                   :let [[d v] (str/split line #",")
                         x (some-> v str/trim not-empty parse-double)]
                   :when (and x (re-matches #"\d{4}-\d{2}.*" d))]
               [(+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7)))) x]))))

(defn inputs
  "f of month t giving the values for `names`; db is bnm.named's series,
  pe10 the CAPE by month."
  [db pe10]
  (let [dir "data/raw/slow/market/"
        rd #(read-monthly (str dir % ".csv"))
        cpi (rd "CPIAUCNS") baa (rd "BAA") aaa (rd "AAA") oil (rd "WTISPLC")
        lgm (fn [m] (into {} (for [[k v] m :when (pos? v)] [k (Math/log v)])))
        lcpi (lgm cpi)
        slow (fn [v] (let [s (db ["USA" v])] (fn [m] (some-> s (sk/value m {:stale 12})))))
        annual (fn [series] (fn [m] (second (pe/known series m))))
        bea {:wage (pe/annual-series (pe/ratio (upc/read-annual "A033RC1A027NBEA.csv") (upc/read-annual "A032RC1A027NBEA.csv")) 3)
             :profit (pe/annual-series (pe/profit-rate (upc/read-annual "A051RC1A027NBEA.csv") (upc/read-annual "K1NTOTL1ES000.csv")) 3)
             :invest (pe/annual-series (pe/ratio (upc/read-annual "PNFIA.csv") (upc/read-annual "GDPA.csv")) 3)}
        ;; each dimension as a function of the month at which it is read
        dims [(slow "finance/credit_gdp") (slow "finance/household_credit_gdp") (slow "finance/real_house_prices")
              (fn [m] (some-> (pe10 (- m 2)) (as-> x (when (pos? x) (Math/log x)))))
              (fn [m] (when-let [a (baa m)] (some->> (aaa m) (- a))))
              (rd "GS10") (rd "TB3MS")
              (fn [m] (when-let [a (lcpi m)] (some->> (lcpi (- m 12)) (- a))))
              (rd "UNRATE") (lgm (rd "INDPRO")) (lgm (rd "PAYEMS"))
              (fn [m] (when-let [o (oil m)] (when-let [c (cpi m)] (Math/log (/ o c)))))
              (annual (:wage bea)) (annual (:profit bea)) (annual (:invest bea))]
        months (range (* 12 1900) (* 12 2027))
        ;; each dimension sampled by month once, as a vector from 1900
        m0 (* 12 1900)
        vecs (mapv (fn [f] (mapv f months)) dims)
        flows (mapv (fn [xs] (mapv (fn [i] (let [a (get xs i) b (get xs (- i 12))] (when (and a b) (- a b))))
                                   (range (count xs))))
                    vecs)
        at (fn [xs i w n] (when (>= i w) (iv/stretch (subvec xs (- i w) (inc i)) w w n)))
        agg (fn [zs] (iv/aggregate zs {:threshold 1.5 :min-n 5}))
        betas (read-betas "macro-15")
        credit-gap (slow "finance/credit_gap")]
    (fn [t]
      (let [i (- (dec t) m0)
            mid (agg (map #(at % i 120 60) vecs))
            short (agg (map #(at % i 60 36) vecs))
            long (agg (map #(at % i 240 120) vecs))
            flow (agg (map #(at % i 120 60) flows))
            vals [(:norm mid) (:breadth mid) (:norm short) (:norm long)
                  (when (and short mid long) (min (:norm short) (:norm mid) (:norm long)))
                  (:norm flow)
                  (when (and mid flow) (* (:norm mid) (:norm flow)))]]
        (-> vals
            (conj (when (some some? vals) 1.0))
            ;; credit (0), household credit for mortgages (1), house prices (2), with the
            ;; panel's coefficients of the latest year known
            (conj (when-let [[_ b] (first (rsubseq betas <= (quot t 12)))]
                    (let [zs [(at (vecs 0) i 240 144) (at (vecs 1) i 240 144) (at (vecs 2) i 240 144) (:norm mid)]]
                      (when (every? some? zs)
                        (reduce + (first b) (map * (rest b) zs))))))
            ;; the BIS credit-to-GDP gap: credit's stretch against a one-sided trend
            ;; of 20-30 years (Drehmann & Tsatsaronis 2014), the long financial cycle
            (conj (credit-gap (dec t))))))))
