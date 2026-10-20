(ns bnm.trader
  "The trader's test (docs/TRADING.md): at the end of each month, the
  probability that the US market falls 20% (or 10%) below today's level
  within the next 6 or 12 months, and what acting on it would have earned.

  Inputs are what the bear market literature finds, all as known at the
  month's end: the term spread and inflation (Chen 2009; Nyberg 2013;
  Resnick & Shoesmith 2002), the default spread and the excess bond premium
  (Gilchrist & Zakrajšek 2012), valuation (CAPE, lagged), monetary
  tightening, momentum and the distance from the 10-month average (Faber),
  realized volatility (Moreira & Muir), the current drawdown, and from the
  slow layer the VIX, financial conditions, oil, US credit and house prices
  over three years and credit growth abroad.

  A pooled logistic hazard is refit every January on the outcomes known by
  then. Its prior, discount and trading threshold are chosen on 1950-1989;
  1990 on is held out. Strategies are scored with a 10bp cost per switch
  against buy and hold, the 10-month average and volatility management.

    jolt -A:tools -M -m bnm.trader [--name trader]

  Reads data/raw/slow/market/ (scripts/fetch-slow.sh market); writes
  data/runs/<name>/trader.txt."
  (:require [augur.calibrate :as pcal]
            [augur.logit :as lgt]
            [augur.score :as sc]
            [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [bnm.trade :as tr]
            [bnm.us-polecon :as upc]
            [bnm.us-shocks :as ush]
            [bnm.paths :as paths]
            [bnm.us-loops :as ul]
            [bnm.us-invariants :as uinv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/raw/slow/market/")

(defn- month-of-date [s] (+ (* 12 (parse-long (subs s 0 4))) (dec (parse-long (subs s 5 7)))))

(defn read-csv-col
  "{month value} from a csv with a date in the first column and the value in
  column `col`, skipping blanks, dots and zeros when `zero-missing`."
  [file col & {:keys [zero-missing]}]
  (with-open [r (io/reader (str dir file))]
    (into {} (for [line (rest (line-seq r))
                   :let [cells (str/split line #",")
                         v (some-> (get cells col) str/trim not-empty parse-double)]
                   :when (and v (not (and zero-missing (zero? v))))]
               [(month-of-date (first cells)) v]))))

(defn read-cape
  "Shiller's CAPE by month: his data (shiller.csv, to mid 2023) under
  multpl.com's monthly table of it (cape.html), which carries it to now and
  matches it where both have a month."
  []
  (let [months {"Jan" 0 "Feb" 1 "Mar" 2 "Apr" 3 "May" 4 "Jun" 5 "Jul" 6 "Aug" 7 "Sep" 8 "Oct" 9 "Nov" 10 "Dec" 11}
        f (io/file (str dir "cape.html"))
        recent (when (.exists f)
                 (into {} (for [[_ mon d y v] (re-seq #"<td>(\w+) (\d+), (\d+)</td>\s*<td[^>]*>\s*(?:&#x2002;)?\s*([0-9.]+)" (slurp f))
                                :when (= d "1")]
                            [(+ (* 12 (parse-long y)) (months mon)) (parse-double v)])))]
    (merge (read-csv-col "shiller.csv" 9 :zero-missing true) recent)))

(defn read-french
  "Monthly US market and bill returns: {:start month :mkt [..] :rf [..]}."
  []
  (with-open [r (io/reader (str dir "F-F_Research_Data_Factors.csv"))]
    (let [rows (->> (line-seq r)
                    (drop-while #(not (str/starts-with? % ",Mkt-RF")))
                    rest
                    (take-while #(re-matches #"\s*\d{6},.*" %))
                    (map #(map str/trim (str/split % #","))))
          months (map #(let [d (first %)] (+ (* 12 (parse-long (subs d 0 4))) (dec (parse-long (subs d 4 6))))) rows)]
      {:start (first months)
       :mkt (mapv #(/ (+ (parse-double (nth % 1)) (parse-double (nth % 4))) 100.0) rows)
       :rf (mapv #(/ (parse-double (nth % 4)) 100.0) rows)})))

(defn lg [x] (when (and x (pos? x)) (Math/log x)))

(def input-names
  [:term :default :inflation :cape :tightening :mom12 :mom1 :sma10 :vol6 :vol-ratio :drawdown12
   :ebp :gz :vix :nfci :oil12 :us-credit3 :us-house3 :world-credit3
   ;; a rich market rolling over: valuation times the turn of the trend
   :cape-x-drawdown :cape-x-decel :cape-x-below-sma
   :cape-present :ebp-present :vix-present :nfci-present :bis-present])

(defn sd [xs]
  (let [n (count xs) m (/ (reduce + xs) n)]
    (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) n))))

(defn build-inputs
  "Per month t (end of month), the inputs as a vector of nils and numbers."
  [{:keys [start mkt]} w data db wc]
  (let [{:keys [gs10 tb3 baa aaa cpi pe10 longrate ebp gz]} data
        ws (fn [t] (w (inc (- t start))))
        nfci (db ["USA" "finance/conditions"])
        vix (db ["world" "finance/vix"])
        oil (db ["world" "energy/oil_brent"])
        cr (db ["USA" "finance/credit_gdp"])
        hs (db ["USA" "finance/real_house_prices"])]
    (fn [t]
      (let [r (fn [k] (mkt (- t start)))
            long (or (gs10 t) (longrate t))
            term (when (and long (tb3 t)) (- long (tb3 t)))
            default (when (and (baa t) (aaa t)) (- (baa t) (aaa t)))
            infl (when (and (cpi (- t 1)) (cpi (- t 13))) (- (lg (cpi (- t 1))) (lg (cpi (- t 13)))))
            cape (lg (pe10 (- t 3)))
            tight (when (and (tb3 t) (tb3 (- t 12))) (- (tb3 t) (tb3 (- t 12))))
            i (inc (- t start))
            hist? (>= i 37)
            mom12 (when hist? (Math/log (/ (ws t) (ws (- t 12)))))
            mom1 (when hist? (Math/log (/ (ws t) (ws (- t 1)))))
            sma (when hist? (Math/log (/ (ws t) (/ (reduce + (map ws (range (- t 9) (inc t)))) 10.0))))
            rets (fn [k] (map #(mkt (- % start)) (range (- t (dec k)) (inc t))))
            vol6 (when hist? (sd (rets 6)))
            vr (when hist? (/ vol6 (max 1e-6 (sd (rets 36)))))
            dd (when hist? (- 1.0 (/ (ws t) (apply max (map ws (range (- t 11) (inc t)))))))
            decel (when (and hist? (>= i 25)) (- (Math/log (/ (ws (- t 12)) (ws (- t 24)))) mom12))
            e (ebp (dec t)) g (gz (dec t))
            v (some-> vix (sk/value t {:stale 3}))
            nf (some-> nfci (sk/value t {:stale 3}))
            o (some-> oil (sk/change t 12 {:stale 3}))
            c3 (some-> cr (sk/change t 36 {:stale 12}))
            h3 (some-> hs (sk/change t 36 {:stale 12}))]
        [term default infl cape tight mom12 mom1 sma vol6 vr dd e g v nf o c3 h3 (wc t)
         (when (and cape dd) (* cape dd)) (when (and cape decel) (* cape decel))
         (when (and cape sma) (if (neg? sma) cape 0.0))
         (when cape 1.0) (when e 1.0) (when v 1.0) (when nf 1.0) (when c3 1.0)]))))

(def regime-names
  "The bear regime's filtered probability from scripts/regime-hmm.py:
  Markov switching with fixed transitions, and with transitions driven by
  displacement; and the risk of a large fall as learned from small ones
  (bnm.scales, data/runs/scales-1/transfer.tsv)."
  [:hmm-plain :hmm-tvtp :small-scale :lppls-bubble :lppls-antibubble])

(def all-input-names (-> input-names (into upc/names) (into regime-names) (into ush/names) (into ul/names) (into uinv/names)))

(defn read-regimes
  "{month [plain tvtp]} from data/runs/trader-inputs/regimes.tsv, or {}."
  []
  (let [f (io/file paths/regimes)]
    (if (.exists f)
      (into {} (for [line (rest (str/split-lines (slurp f)))
                     :let [[m a b] (str/split line #"\t" -1)]]
                 [(parse-long m) [(some-> a not-empty parse-double) (some-> b not-empty parse-double)]]))
      {})))

(defn read-lppls
  "{month [positive negative]} LPPLS bubble confidence from
  scripts/lppls-confidence.py (data/runs/trader-inputs/lppls.tsv), or {}."
  []
  (let [f (io/file paths/lppls)]
    (if (.exists f)
      (into {} (for [line (rest (str/split-lines (slurp f))) :let [[m a b] (str/split line #"\t")]]
                 [(parse-long m) [(parse-double a) (parse-double b)]]))
      {})))

(defn read-transfer
  "{month logit} from bnm.scales' transfer.tsv, or {}."
  []
  (let [f (io/file paths/small-fall-transfer)]
    (if (.exists f)
      (into {} (for [line (str/split-lines (slurp f)) :let [[m v] (str/split line #"\t")]]
                 [(parse-long m) (parse-double v)]))
      {})))

(defn all-inputs
  "The base inputs followed by the political economy ones (bnm.us-polecon)
  and the discovered regimes."
  [fr w data db wc]
  (let [base (build-inputs fr w data db wc)
        pe (upc/inputs db)
        regimes (read-regimes)
        transfer (read-transfer)
        lppls (read-lppls)
        shocks (ush/inputs)
        {:keys [start mkt]} fr
        loops (ul/inputs (into {} (for [i (range 1 (count w))] [(+ start (dec i)) (Math/log (w i))])))
        invariants (uinv/inputs db (:pe10 data))]
    (fn [t] (let [b (base t)]
              (-> b (into (pe t {:term (b 0) :inflation (b 2) :tightening (b 4)}))
                  (into (get regimes t [nil nil]))
                  ;; the month's last week is known at its end
                  (conj (transfer t))
                  (into (get lppls t [nil nil]))
                  (into (shocks t))
                  (into (loops t))
                  (into (invariants t)))))))

(defn- vectorize ^doubles [xs] (double-array (cons 1.0 (map #(if % (double %) 0.0) xs))))

(def stable-inputs
  "Inputs whose relation to drawdowns ahead has the same sign in 1930-1949
  and 1950-1989, univariately, and that do not lower the hazard's AUC on
  1950-1989 when added (docs/TRADING.md): chosen before looking at 1990 on.
  The term spread, inflation and tightening flip sign between those eras;
  the default spread, the GZ spread and house prices lowered it. The
  interactions of valuation with the turn of the trend agree in sign in
  1930-1949, 1950-1969 and 1970-1989."
  [:cape :mom12 :sma10 :vol6 :vol-ratio :drawdown12 :cape-x-drawdown :cape-x-decel :cape-x-below-sma
   :cape-present])

(def record-inputs
  "The model of record (docs/INVARIANTS.md): the stable base, the bear regime
  discovered by Markov switching, the 12-month oil change, the risk learned
  on small falls, and the dimension-free displacement of fifteen US
  dimensions. No input names a regime; each was stable before 1990 or was
  learned from the data's own dynamics."
  (into stable-inputs [:hmm-plain :oil12 :shocks-present :small-scale
                       :us-norm :us-breadth :us-resonance :us-flow-norm :us-stock-x-flow :us-invariants-present]))

(defn select-inputs [names]
  (let [idx (into {} (map-indexed (fn [i n] [n i]) all-input-names))
        is (mapv idx names)]
    (fn [xs] (mapv #(nth xs %) is))))

(def theory-signs
  "The sign each input's effect on crash risk should have: rich valuations,
  drawdowns and volatility raise it; momentum and a price above its average
  lower it."
  {:cape 1 :drawdown12 1 :vol6 1 :vol-ratio 1 :mom12 -1 :sma10 -1 :mom1 -1 :default 1 :gz 1
   :us-house3 1 :term -1 :inflation 1 :tightening 1 :ebp 1 :vix 1 :nfci 1 :oil12 1 :us-credit3 1})

(defn combination
  "Out-of-sample probabilities from the mean of one-input hazards, each refit
  every January; an input whose fitted effect has the wrong sign gives the
  base rate instead (Rapach, Strauss & Zhou 2010; Campbell & Thompson 2008).
  `rows` carry :xs, the raw inputs in the order of `names`."
  [rows names h prior discount from]
  (let [last-t (apply max (map :t rows))
        years (range from (inc (quot last-t 12)))
        signs (mapv #(theory-signs % 0) names)]
    (loop [[yr & more] years betas (vec (repeat (count names) nil)) out []]
      (if-not yr
        out
        (let [t0 (* 12 yr)
              known (filter #(and (some? (:y %)) (< (+ (:t %) h) t0)) rows)
              wt #(Math/pow discount (/ (- t0 (:t %)) 12.0))
              rate (let [ws (map wt known)] (/ (reduce + (map #(if (:y %1) %2 0.0) known ws)) (max 1e-9 (reduce + ws))))
              betas (vec (for [i (range (count names))]
                           (let [tr (for [r known :let [v (nth (:xs r) i)] :when v]
                                      {:x (double-array [1.0 v]) :y (:y r) :w (wt r)})]
                             (when (>= (count (filter :y tr)) 5)
                               (let [b (lgt/fit tr 2 {:prior prior})]
                                 (when (pos? (* (aget b 1) (signs i))) b))))))
              scored (for [{:keys [t xs y unit]} rows :when (= yr (quot t 12))]
                       (let [ps (for [i (range (count names))
                                      :let [b (betas i) v (nth xs i)]]
                                  (if (and b v) (lgt/predict b (double-array [1.0 v])) rate))]
                         [t (/ (reduce + ps) (count ps)) y unit]))]
          (recur more betas (into out scored)))))))

(defn predictions
  "Out-of-sample probabilities per month (and unit, for a panel) from year
  `from`: [[t p y unit]]."
  [rows h prior discount from]
  (let [k (alength ^doubles (:x (first rows)))
        last-t (apply max (map :t rows))
        years (range from (inc (quot last-t 12)))]
    (loop [[yr & more] years beta nil out []]
      (if-not yr
        out
        (let [t0 (* 12 yr)
              train (for [{:keys [t x y]} rows :when (and (some? y) (< (+ t h) t0))]
                      {:x x :y y :w (Math/pow discount (/ (- t0 t) 12.0))})
              beta (if (>= (count (filter :y train)) 5) (lgt/fit train k {:prior prior :start beta}) beta)
              scored (for [{:keys [t x y unit]} rows :when (and beta (= yr (quot t 12)))]
                       [t (lgt/predict beta x) y unit])]
          (recur more beta (into out scored)))))))

(defn log-score [preds]
  (let [ps (filter #(some? (nth % 2)) preds)]
    (/ (reduce + (for [[_ p y] ps :let [p (min (max p 1e-6) (- 1 1e-6))]] (Math/log (if y p (- 1 p)))))
       (max 1 (count ps)))))

(defn- in-years [preds lo hi] (filter #(<= lo (quot (first %) 12) hi) preds))

(defn- fmt [x] (if x (format "%.3f" (double x)) "  -  "))

(defn setup
  "The US market data and the inputs by month: {:fr :w :months :raw-inputs}."
  []
  (let [fr (read-french)
        {:keys [start mkt]} fr
        w (tr/wealth mkt)
        data {:gs10 (read-csv-col "GS10.csv" 1) :tb3 (read-csv-col "TB3MS.csv" 1)
              :baa (read-csv-col "BAA.csv" 1) :aaa (read-csv-col "AAA.csv" 1)
              :cpi (read-csv-col "CPIAUCNS.csv" 1)
              :pe10 (read-cape)
              :longrate (read-csv-col "shiller.csv" 5 :zero-missing true)
              :ebp (read-csv-col "ebp.csv" 2) :gz (read-csv-col "ebp.csv" 1)}
        variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        units (distinct (map first (keys db)))
        months (range (+ start 36) (+ start (count mkt)))
        wc (nm/world-credit db units months)]
    {:fr fr :w w :months months :raw-inputs (all-inputs fr w data db wc)}))

(defn run [{:keys [name inputs method]}]
  (let [run-name (or name "trader")
        out-dir (str "data/runs/" run-name)
        _ (io/make-parents (str out-dir "/x"))
        {:keys [fr w months raw-inputs]} (setup)
        {:keys [start mkt rf]} fr
        chosen (if inputs (mapv keyword (str/split inputs #",")) record-inputs)
        pick (select-inputs chosen)
        combo? (= method "combo")
        combo-names (vec (remove #(str/ends-with? (clojure.core/name %) "-present") chosen))
        pick-combo (select-inputs combo-names)
        wt (fn [t] (inc (- t start)))
        targets {"20% within 12m" [12 0.2] "20% within 6m" [6 0.2] "10% within 6m" [6 0.1]}
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        ;; months whose exposure is decided at their end: t earns t+1
        trade-months (fn [lo hi] (filter #(<= lo (quot % 12) hi) (butlast months)))
        returns (fn [ts] [(mapv #(mkt (- (inc %) start)) ts) (mapv #(rf (- (inc %) start)) ts)])
        strat (fn [ts ex-fn] (let [[m r] (returns ts)] (tr/backtest m r (mapv ex-fn ts) {:cost 0.001})))
        sma (let [e (tr/sma-exposure w 10)] (fn [t] (e (wt t))))
        vol-target (let [v6 (fn [t] (sd (map #(mkt (- % start)) (range (- t 5) (inc t)))))
                         target (sd mkt)]
                     (fn [t] (min 1.0 (/ target (max 1e-6 (v6 t))))))
        line (fn [label r]
               (say (format "    %-24s annual %6.2f%%  vol %5.2f%%  sharpe %5.2f  maxdd %5.1f%%  invested %3.0f%%  switches %d"
                            label (* 100 (:annual r)) (* 100 (:vol r)) (:sharpe r) (* 100 (:max-drawdown r))
                            (* 100 (:invested r)) (:switches r))))]
    (say "The trader's test: P(US market falls X below today's level within h months), out of sample.")
    (say "Prior, discount and threshold chosen on 1950-1989; 1990 on is held out. Strategies switch to")
    (say "bills when the probability is above the threshold, 10bp per switch. Inputs:" (str/join " " (map clojure.core/name chosen)))
    (doseq [[label [h depth]] (sort targets)]
      (let [rows (vec (for [t months
                            :let [raw (raw-inputs t)]]
                        {:t t :x (vectorize (pick raw)) :xs (pick-combo raw) :y (tr/drawdown-ahead? w (wt t) h depth)}))
            ;; long memory only: with a dozen bear markets, a short one makes the
            ;; intercept chase the last crash wave and inverts the ranking
            grid (for [prior [10.0 100.0] discount [1.0 0.99 0.98]]
                   (let [ps (if combo?
                              (combination rows combo-names h prior discount 1940)
                              (predictions rows h prior discount 1940))]
                     {:prior prior :discount discount :preds ps :ls (log-score (in-years ps 1950 1989))}))
            best (apply max-key :ls grid)
            preds (:preds best)
            p-at (into {} (map (juxt first second) preds))
            base (fn [lo hi] (let [ys (keep #(nth % 2) (in-years preds lo hi))] (/ (count (filter true? ys)) (double (max 1 (count ys))))))
            score (fn [lo hi]
                    (let [ps (filter #(some? (nth % 2)) (in-years preds lo hi))
                          ys (map #(nth % 2) ps)
                          ;; base rate known before the period
                          b (base 1940 (dec lo))]
                      {:n (count ps) :pos (count (filter true? ys))
                       :auc (sc/auc (map second ps) ys) :ap (sc/avg-precision (map second ps) ys)
                       :bss (- 1.0 (/ (sc/brier (map second ps) ys) (sc/brier (repeat (count ps) b) ys)))}))
            ;; threshold by Sharpe on the tuning years, as multiples of the base rate
            taus (map #(* % (base 1940 1949)) [1.0 1.25 1.5 2.0 2.5 3.0 4.0])
            ex-model (fn [tau] (fn [t] (if (< (p-at t 0.0) tau) 1.0 0.0)))
            tune-ts (trade-months 1950 1989)
            tau (apply max-key #(:sharpe (strat tune-ts (ex-model %))) taus)
            tau-both (apply max-key #(:sharpe (strat tune-ts (fn [t] (min ((ex-model %) t) (sma t))))) taus)
            ;; the 10-month rule, but staying in when the model says risk is low
            veto (fn [tau] (fn [t] (max (sma t) ((ex-model tau) t))))
            low-taus (map #(* % (base 1940 1949)) [0.25 0.5 0.75 1.0 1.25])
            tau-veto (apply max-key #(:sharpe (strat tune-ts (veto %))) low-taus)
            ;; a hedge growing with the excess probability: fully out at k times the base rate
            scaled (fn [k] (let [b (base 1940 1949)]
                             (fn [t] (- 1.0 (min 1.0 (max 0.0 (/ (- (p-at t 0.0) b) (* (- k 1.0) b))))))))
            ks [1.5 2.0 3.0 4.0 6.0]
            k-scaled (apply max-key #(:sharpe (strat tune-ts (scaled %))) ks)]
        ;; the out-of-sample probabilities by month, for scripts outside Jolt (protection-value.py)
        (spit (str out-dir "/preds-" (str/replace label #"[^0-9a-z]+" "-") ".tsv")
              (str/join (for [[t p y] preds] (str t "\t" p "\t" (if (nil? y) "" (if y 1 0)) "\n"))))
        (say)
        (say (format "%s  (prior %.0f, discount %.2f, threshold %.2f, with the 10-month rule %.2f)"
                     label (:prior best) (:discount best) tau tau-both))
        (doseq [[era lo hi] [["tune 1950-89" 1950 1989] ["held out 1990-" 1990 2100] ["2000-" 2000 2100]]]
          (let [s (score lo hi)]
            (say (format "  %-15s n=%4d pos=%4d  auc %s  ap %s  bss %s" era (:n s) (:pos s) (fmt (:auc s)) (fmt (:ap s)) (fmt (:bss s))))))
        (doseq [[era lo hi] [["tune 1950-89" 1950 1989] ["held out 1990-" 1990 2100]]]
          (let [ts (trade-months lo hi)]
            (say (str "  strategies, " era))
            (line "buy and hold" (strat ts (constantly 1.0)))
            (line "10-month average" (strat ts sma))
            (line "volatility managed" (strat ts vol-target))
            (line "model" (strat ts (ex-model tau)))
            (line "model and 10-month" (strat ts (fn [t] (min ((ex-model tau-both) t) (sma t)))))
            (line "10-month unless low risk" (strat ts (veto tau-veto)))
            (line "hedge by probability" (strat ts (scaled k-scaled)))
            (say (str "      Sharpe, 10-month unless low risk (x base 0.25 0.5 0.75 1 1.25): "
                      (str/join " " (for [tau low-taus] (format "%.2f" (:sharpe (strat ts (veto tau))))))
                      "; hedge by probability (k 1.5 2 3 4 6): "
                      (str/join " " (for [k ks] (format "%.2f" (:sharpe (strat ts (scaled k))))))))
            (say (str "      Sharpe by threshold (x base 1 1.25 1.5 2 2.5 3 4): model "
                      (str/join " " (for [tau taus] (format "%.2f" (:sharpe (strat ts (ex-model tau))))))
                      "; with 10-month "
                      (str/join " " (for [tau taus] (format "%.2f" (:sharpe (strat ts (fn [t] (min ((ex-model tau) t) (sma t))))))))))))
        (let [[t-now p-now] (last preds)
              held (map second (in-years preds 1990 2100))
              pct (/ (count (filter #(< % p-now) held)) (double (count held)))]
          (say (format "  now (%s): P = %.3f, above %.0f%% of held-out months; base rate %.3f; calibrated bucket observed %s"
                       (st/month-str t-now) p-now (* 100 pct) (base 1940 2100)
                       (let [ps (sort-by second (filter #(some? (nth % 2)) (in-years preds 1990 2100)))
                             near (filter #(< (Math/abs (- (second %) p-now)) 0.05) ps)]
                         (if (seq near) (format "%.2f (n=%d)" (/ (count (filter #(true? (nth % 2)) near)) (double (count near))) (count near)) "-")))))
        (say "  calibration, held out: mean predicted vs observed by fifth")
        (let [ps (sort-by second (filter #(some? (nth % 2)) (in-years preds 1990 2100)))
              n (count ps)]
          (doseq [qi (range 5)
                  :let [chunk (subvec (vec ps) (quot (* qi n) 5) (quot (* (inc qi) n) 5))]]
            (say (format "    %.3f  %.3f  (n=%d)" (/ (reduce + (map second chunk)) (count chunk))
                         (/ (count (filter #(true? (nth % 2)) chunk)) (double (count chunk))) (count chunk)))))
        ;; recalibrated in real time: each January, a logistic fit of outcome on the forecast's logit
        ;; over the forecasts already resolved (bnm.calibrate/logistic-map), applied to the year's;
        ;; the isotonic fit improved the levels but its flat steps cost the ranking (AUC 0.81 to 0.67)
        (let [years (range 1950 (inc (quot (first (last preds)) 12)))
              recal (vec (mapcat (fn [yr]
                                   (let [known (filter #(and (some? (nth % 2)) (< (+ (first %) h) (* 12 yr))) preds)
                                         f (pcal/logistic-map (map second known) (map #(true? (nth % 2)) known))]
                                     (for [[t p y u] preds :when (= yr (quot t 12))] [t (f p) y u])))
                                 years))
              held (filter #(some? (nth % 2)) (in-years recal 1990 2100))
              ys (map #(nth % 2) held)
              b (base 1940 1989)
              ps (sort-by second held)
              n (count ps)]
          (say (format "  recalibrated (logistic on resolved forecasts, refit yearly), held out: bss %.3f (raw %.3f), auc %.3f"
                       (- 1.0 (/ (sc/brier (map second held) ys) (sc/brier (repeat (count held) b) ys)))
                       (- 1.0 (/ (sc/brier (map #(p-at (first %) 0.0) held) ys) (sc/brier (repeat (count held) b) ys)))
                       (sc/auc (map second held) ys)))
          (say "    by fifth, mean predicted vs observed:"
               (str/join "  " (for [qi (range 5)
                                    :let [chunk (subvec (vec ps) (quot (* qi n) 5) (quot (* (inc qi) n) 5))]]
                                (format "%.3f/%.3f" (/ (reduce + (map second chunk)) (count chunk))
                                        (/ (count (filter #(true? (nth % 2)) chunk)) (double (count chunk)))))))
          (say (format "    now: %.3f" (double (second (last recal)))))
          ;; the current reading for the readings pipeline, with the calibration monitor:
          ;; CUSUM charts over the last ten years of resolved recalibrated forecasts
          (let [[t-now p-now] (last preds)
                recent (filter #(and (some? (nth % 2)) (>= (first %) (- t-now 120))) recal)
                ps (map second recent) ys (map #(true? (nth % 2)) recent)
                up (sc/cusum ps ys 2.0 :overlap h :h 4.0) down (sc/cusum ps ys 0.5 :overlap h :h 4.0)
                held (map second (filter #(>= (quot (first %) 12) 1990) preds))]
            (io/make-parents (str paths/derived "x"))
            (spit (str paths/derived "trader-now-" (str/replace label #"[^0-9a-z]+" "-") ".edn")
                  (pr-str {:target label :month (st/month-str t-now) :p p-now
                           :p-recal (second (last recal)) :base (base 1940 2100)
                           :percentile (/ (count (filter #(< % p-now) held)) (double (max 1 (count held))))
                           :cusum {:up (:max up) :up-now (:last up) :down (:max down) :down-now (:last down)
                                   :h 4.0 :months (count recent)}}))))
        (say "  lead: probability over the 12 months before each held-out onset (first month the fall begins within h)")
        (let [onsets (for [[[t0 _ y0] [t1 _ y1]] (partition 2 1 (in-years preds 1989 2100))
                           :when (and (true? y1) (not (true? y0)))]
                       t1)]
          (doseq [t onsets]
            (say (format "    %s  %s" (st/month-str t)
                         (str/join " " (for [s (range (- t 12) (inc t) 2)] (format "%.2f" (double (p-at s 0.0)))))))))))
    (spit (str out-dir "/trader.txt") (str out))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
