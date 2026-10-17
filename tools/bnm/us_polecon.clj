(ns bnm.us-polecon
  "US political economy inputs for the drawdown hazard (docs/REGIMES.md),
  from BEA annual accounts on FRED (1929-) and monthly labour series:

    wage share     compensation of employees / national income
    profit rate    corporate profits (IVA, CCAdj) / last year's current-cost
                   net stock of private nonresidential fixed assets
    investment     private nonresidential investment / GDP

  each as its deviation from the previous 20 years' mean and its change over
  three years. Annual accounts for year Y are taken as known from April of
  Y+1, fixed assets (needed for Y's profit rate, which uses Y-1's stock) by
  then too.

  Crisis theory says why single macro predictors flip between eras:
  - in a profit squeeze (Weisskopf 1979; Goodwin 1967), a tight labour
    market and rising wages cut profits, so falling unemployment, inflation
    and tightening come before crashes, but only when labour's share is high;
  - in a realization or overaccumulation crisis (Kotz 2009; Moseley), the
    wage share is low, investment has outrun profitability, and demand rests
    on credit, so the same signals mean little.
  So the macro inputs enter multiplied by the regime: the wage share's and
  the profit rate's deviations."
  (:require [bnm.polecon :as pe]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/raw/slow/market/")

(defn read-annual
  "{year value} from a FRED csv."
  [file]
  (with-open [r (io/reader (str dir file))]
    (into {} (for [line (rest (line-seq r))
                   :let [[d v] (str/split line #",")
                         x (some-> v str/trim not-empty parse-double)]
                   :when x]
               [(parse-long (subs d 0 4)) x]))))

(defn read-monthly [file]
  (with-open [r (io/reader (str dir file))]
    (into {} (for [line (rest (line-seq r))
                   :let [[d v] (str/split line #",")
                         x (some-> v str/trim not-empty parse-double)]
                   :when x]
               [(+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 5 7)))) x]))))

(def names
  [:wage-share-dev :wage-share3 :profit-rate-dev :profit-rate3 :investment-dev :overaccumulation
   :unemployment12 :payrolls12
   ;; the macro predictors that flip, through the regime
   :tightening-x-wage :inflation-x-wage :term-x-wage :unemployment-x-wage :payrolls-x-wage
   :tightening-x-profit :inflation-x-profit :term-x-profit :unemployment-x-profit
   ;; realization on credit: household debt growing while labour's share is low
   :household-debt3 :household-debt-x-low-wage
   :polecon-present])

(defn- x* [a b] (when (and a b) (* (double a) (double b))))

(defn inputs
  "f of month t and the base macro values {:term :inflation :tightening},
  giving the values for `names`; db is bnm.named's series."
  [db]
  (let [ws (pe/annual-series (pe/ratio (read-annual "A033RC1A027NBEA.csv") (read-annual "A032RC1A027NBEA.csv")) 3)
        pr (pe/annual-series (pe/profit-rate (read-annual "A051RC1A027NBEA.csv") (read-annual "K1NTOTL1ES000.csv")) 3)
        inv (pe/annual-series (pe/ratio (read-annual "PNFIA.csv") (read-annual "GDPA.csv")) 3)
        unrate (read-monthly "UNRATE.csv")
        payems (read-monthly "PAYEMS.csv")
        hh (db ["USA" "finance/household_credit_gdp"])]
    (fn [t {:keys [term inflation tightening]}]
      (let [wd (pe/deviation ws t 20 5) pd (pe/deviation pr t 20 5)
            idv (pe/deviation inv t 20 5) p3 (pe/change pr t 3)
            u12 (when (and (unrate (dec t)) (unrate (- t 13))) (- (unrate (dec t)) (unrate (- t 13))))
            h3 (some-> hh (sk/change t 36 {:stale 12}))
            e12 (when (and (payems (dec t)) (payems (- t 13))) (Math/log (/ (payems (dec t)) (payems (- t 13)))))]
        [wd (pe/change ws t 3) pd p3 idv
         ;; investment above its norm while profitability falls
         (when (and idv p3) (* idv (- p3)))
         u12 e12
         (x* tightening wd) (x* inflation wd) (x* term wd) (x* u12 wd) (x* e12 wd)
         (x* tightening pd) (x* inflation pd) (x* term pd) (x* u12 pd)
         h3 (x* h3 (some-> wd -))
         (when wd 1.0)]))))
