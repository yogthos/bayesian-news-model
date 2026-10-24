(ns bnm.sfc
  "The stock-flow consistent core (bead bnm-4hv.10; docs/COUPLED-SYSTEMS.md
  2a, 4a, 6d): a five-sector Godley-Lavoie frame per country-year,
  households (:hh), non-financial corporations (:nfc), financial
  corporations (:fin), general government (:gov) and the rest of the world
  (:row), with the transaction-flow and balance-sheet identities as hard
  constraints and bnm.keen's Phillips curve and investment function as the
  behaviour of wages and firms inside them.

  The balance sheet (bsm) is {sector {instrument {:a assets :l
  liabilities}}} in national currency, the instruments :money (F2), :debt
  (F3 + F4), :equity (F5) and :other (F1, F6-F8). The transaction-flow
  matrix (book) has a row per flow and a column per sector plus :prod, the
  production account that sells final demand and pays value added; every
  row sums to zero, :prod's column sums to zero, and each sector's column
  sums to its net lending, so the net lending sums to zero.

  A year (step), from the balance sheet at its start:
    finance (F)   interest and dividends from the stocks at sector implicit
                  rates, the financial sector receiving what the others
                  pay less what they receive (its margin); new credit to
                  households and firms by partial adjustment to a learned
                  debt-to-output path (F1);
    firms (R)     investment, Keen's kappa(pi) of last year's profit share
                  (bnm.keen/investment, fitted to the frame's own NFC
                  investment), households' and financial investment as
                  shares of expected output;
    accounts (D)  the wage share by Keen's Phillips curve (bnm.keen/
                  phillips, keen-run's calibration), government spending
                  and benefits growing at trend nominal growth (the fiscal
                  rule: taxes move with income, spending does not),
                  consumption out of disposable income and wealth (Godley-
                  Lavoie's alpha1, alpha2), imports a share of absorption;
                  output solves the multiplier; every flow is booked; then
                  the flow of funds: households and firms hold their assets
                  in learned shares with deposits as the buffer, government
                  issues debt for its deficit, the rest of the world's debt
                  claims take its balance, and the financial sector holds
                  what closes each instrument's row. Its own net worth then
                  follows twice, from its income and from its positions:
                  the redundant equation, whose residual (rounding) is
                  booked to government deposits and reported (:buffer).
  Employment follows Keen's dl/l = g - alpha - beta, real growth from
  investment, g = kappa/nu - delta: in the frame, the firms' investment
  share against its own trend (:kappa-bar, at which employment holds),
  over nu. Output in the frame is nominal and demand-led, so it does not
  set employment: a higher wage share raises demand and prices, and
  employment through profits and investment, as in Keen. Equity is repriced at :rho a year,
  both sides, so revaluation sums to zero.

  Learning (learn) is per country from the data years up to the origin,
  on the balance sheets closed as init closes them: shares as
  exponentially weighted means (half-life 1 year, the best on the design
  origins to 2002 of 1, 2, 3 and 5), rates as ratios of weighted means,
  debt paths as weighted trends, alpha1 and alpha2 and kappa's curve by
  weighted ridge regressions (half-life 10 years, :fit-half-life);
  keen-run's employment and calibration as known with the origin's data
  (origin + 2, keen-of).

  The assumption audit (bnm.sfc-audit, step-given) found that none of the
  behavioural rules beats holding its ratio one year ahead; `revised`
  holds them (learn's :consumption, :wages, :investment, :theta), the
  frame and its identities unchanged. Keen's rules stay the default, as
  the theory the coupled model is built on.

  The data are scripts/sfc-data.py's panel (data/derived/sfc/panel.csv)."
  (:require [bnm.csv :as csv]
            [bnm.keen :as keen]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def sectors [:hh :nfc :fin :gov :row])
(def domestic [:hh :nfc :fin :gov])
(def instruments [:money :debt :equity :other])

(defn- num ^double [x] (if (number? x) (double x) 0.0))
(defn- sum ^double [xs] (reduce + 0.0 (map num xs)))
(defn- finite? [x] (and (number? x) (< (Math/abs (double x)) 1e300) (== x x)))

;; -- the balance sheet ------------------------------------------------------------

(defn pos ^double [bsm s k side] (num (get-in bsm [s k side])))
(defn assets ^double [bsm s] (sum (for [k instruments] (pos bsm s k :a))))
(defn liabilities ^double [bsm s] (sum (for [k instruments] (pos bsm s k :l))))
(defn nfw "Net financial worth." ^double [bsm s] (- (assets bsm s) (liabilities bsm s)))
(defn- ib-l ^double [bsm s] (+ (pos bsm s :money :l) (pos bsm s :debt :l)))
(defn- ib-a ^double [bsm s] (+ (pos bsm s :money :a) (pos bsm s :debt :a)))

(defn- row-gap ^double [bsm k]
  (- (sum (for [s sectors] (pos bsm s k :a))) (sum (for [s sectors] (pos bsm s k :l)))))

(defn bsm-gap
  "The largest |assets - liabilities| of an instrument across the sectors."
  [bsm]
  (reduce max 0.0 (for [k instruments] (Math/abs (row-gap bsm k)))))

(defn close-init
  "{:bsm :buffer}: a measured balance sheet closed by moving each
  instrument's discrepancy (assets less liabilities across the sectors,
  :buffer {instrument gap}) to the rest of the world: a liability where
  assets exceed, an asset where they fall short. Missing positions are 0."
  [bsm]
  (let [full (into {} (for [s sectors] [s (into {} (for [k instruments] [k {:a (pos bsm s k :a) :l (pos bsm s k :l)}]))]))
        gaps (into {} (for [k instruments] [k (row-gap full k)]))]
    {:bsm (reduce (fn [b [k g]] (if (pos? g) (update-in b [:row k :l] + g) (update-in b [:row k :a] + (- g)))) full gaps)
     :buffer gaps}))

;; -- the transaction-flow matrix --------------------------------------------------------

(defn- neg [m] (into {} (for [[k v] m] [k (- (num v))])))

(defn book
  "The transaction-flow matrix of year flows `x`: {:rows {row {column
  amount}} :nl {sector net lending}}. x holds :Y, :W, :va {producer} (gov's
  is Y less the others': product taxes and anything unallocated), :comp,
  :ptax {producer}, :int-paid and :div-paid {sector}, :int-recv and
  :div-recv {sector but :fin}, :itax {:hh :nfc :fin}, :socc, :ben, :cons
  {:hh :gov}, :inv {producer}, :nx and :other {:hh :nfc :fin :row}.
  Households receive all wages, government all taxes and contributions,
  the financial sector the interest and dividends the others pay less what
  they receive, government the other flows' counterpart."
  [{:keys [Y W va comp ptax int-paid int-recv div-paid div-recv itax socc ben cons inv nx other]}]
  (let [resid (fn [paid recv] (- (sum (vals paid)) (sum (vals (dissoc recv :fin)))))
        oth (select-keys other [:hh :nfc :fin :row])
        rows {:consumption {:hh (- (num (:hh cons))) :gov (- (num (:gov cons))) :prod (sum (vals cons))}
              :investment (assoc (neg inv) :prod (sum (vals inv)))
              :net-exports {:row (- (num nx)) :prod (num nx)}
              :value-added (assoc (into {} (for [[k v] va] [k (num v)])) :prod (- (num Y)))
              :wages (merge-with + (neg comp) {:hh (num W)})
              :production-taxes (merge-with + (neg ptax) {:gov (sum (vals ptax))})
              :interest (merge-with + (neg int-paid) (dissoc int-recv :fin) {:fin (resid int-paid int-recv)})
              :dividends (merge-with + (neg div-paid) (dissoc div-recv :fin) {:fin (resid div-paid div-recv)})
              :income-taxes (merge-with + (neg itax) {:gov (sum (vals itax))})
              :social-contributions {:hh (- (num socc)) :gov (num socc)}
              :social-benefits {:gov (- (num ben)) :hh (num ben)}
              :other (assoc (into {} (for [[k v] oth] [k (num v)])) :gov (- (sum (vals oth))))}]
    {:rows rows
     :nl (into {} (for [s sectors] [s (sum (for [[_ m] rows] (get m s 0.0)))]))}))

(defn tfm-gap
  "The largest of: any row's sum, production's column, the net lending's
  sum."
  [{:keys [rows nl]}]
  (reduce max (Math/abs (sum (vals nl)))
          (cons (Math/abs (sum (for [[_ m] rows] (get m :prod 0.0))))
                (for [[_ m] rows] (Math/abs (sum (vals m)))))))

(defn profit-share
  "The NFCs' operating surplus plus net interest, over output (Keen's pi
  for the investment function)."
  [{:keys [Y va comp ptax int-paid int-recv]}]
  (/ (+ (- (num (:nfc va)) (num (:nfc comp)) (num (:nfc ptax))) (num (:nfc int-recv)) (- (num (:nfc int-paid)))) (double Y)))

(defn- os-of [x s] (- (num (get-in x [:va s])) (num (get-in x [:comp s])) (num (get-in x [:ptax s]))))

(defn tax-bases
  "Income-tax bases: households' wages, mixed income and property income
  received; firms' operating surplus plus net interest and dividends
  received; financial corporations' operating surplus."
  [x]
  {:hh (+ (num (:W x)) (os-of x :hh) (num (get-in x [:int-recv :hh])) (num (get-in x [:div-recv :hh])))
   :nfc (max 0.0 (+ (os-of x :nfc) (num (get-in x [:int-recv :nfc])) (- (num (get-in x [:int-paid :nfc]))) (num (get-in x [:div-recv :nfc]))))
   :fin (max 0.0 (os-of x :fin))})

;; -- data ---------------------------------------------------------------------------

(def flow-items
  [:va :comp_paid :comp_recv :os :os_b2g :mixed :int_recv :int_paid :div_recv :div_paid :tax_paid
   :socc_paid :socb_recv :cons :gcf :nl :exports :imports])

(defn- pd [s] (when (and s (not= s "")) (let [x (parse-double s)] (when (and x (finite? x)) x))))

(defn read-panel
  "scripts/sfc-data.py's panel: {iso {year obs}}, obs {:iso :year :y (GDP)
  :flows-ok :stocks-source (:oecd-t720, :imputed or nil) :known-flows
  :known-stocks :f {sector {item value}} :b balance sheet (when stocks)}."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & rows] (csv/records r \,)
          ix (zipmap h (range))
          col (fn [row c] (when-let [i (ix c)] (nth row i nil)))]
      (reduce
       (fn [acc row]
         (let [iso (col row "iso") yr (parse-long (col row "year"))
               src (col row "stocks_source")
               src (when (seq src) (keyword src))
               f (into {} (for [s sectors]
                            [s (into {} (for [it flow-items :let [v (pd (col row (str (name s) "_" (name it))))] :when v] [it v]))]))
               b (when src
                   (into {} (for [s sectors]
                              [s (into {} (for [k instruments]
                                            [k {:a (num (pd (col row (str (name s) "_" (name k) "_a"))))
                                                :l (num (pd (col row (str (name s) "_" (name k) "_l"))))}]))])))]
           (assoc-in acc [iso yr]
                     (cond-> {:iso iso :year yr :y (pd (col row "y"))
                              :flows-ok (= "True" (col row "flows_ok"))
                              :stocks-source src
                              :known-flows (some-> (pd (col row "known_flows")) long)
                              :known-stocks (some-> (pd (col row "known_stocks")) long)
                              :f f}
                       b (assoc :b b)))))
       {} rows))))

(def keen-vars
  #{"keen/lambda" "keen/state-year" "keen/phi-slope" "keen/l-star" "keen/alpha" "keen/beta" "keen/delta"
    "keen/nu" "keen/kappa-slope" "keen/pi-star" "keen/omega" "keen/d" "keen/r"})

(defn read-keen
  "data/derived/stocks/keen.csv: {iso {year-known {variable value}}}."
  [path]
  (with-open [r (io/reader path)]
    (reduce (fn [acc line]
              (let [[iso yr v x] (str/split line #",")]
                (if (keen-vars v) (assoc-in acc [iso (parse-long yr) v] (parse-double x)) acc)))
            {} (rest (line-seq r)))))

(defn keen-at
  "keen.csv's row for `iso` known at year `t` (the latest at or before)."
  [kn iso t]
  (let [rows (get kn iso)]
    (when (seq rows)
      (some->> (keys rows) (filter #(<= % t)) seq (apply max) (get rows)))))

(def vintage
  "Years after a data year that its flows and stocks are known (OECD
  Tables 14 and 720): a forecast from origin y is made at y + 2."
  2)

(defn keen-of
  "keen.csv's row for `iso` as known when data year `origin` is (origin +
  vintage), describing a data year at or before origin: its state-year is
  origin where keen-run has it by then, an older one where not, never a
  later one."
  [kn iso origin]
  (let [rows (get kn iso)]
    (some->> (keys rows)
             (filter #(and (<= % (+ origin vintage))
                           (<= (or (get-in rows [% "keen/state-year"]) %) origin)))
             seq (apply max) (get rows))))

(defn observed
  "Year flows in book's form from a data observation, :other being what
  the data's net lending holds beyond the modelled rows (transfers,
  capital transfers, cross-border wages, other property income, and the
  data's own discrepancies), so book gives the observed net lending of
  every sector but government, whose other flows close the matrix."
  [{:keys [y f]}]
  (let [g (fn [s k] (get-in f [s k]))
        Y (double y)
        va3 (into {} (for [s [:hh :nfc :fin]] [s (num (g s :va))]))
        va (assoc va3 :gov (- Y (sum (vals va3))))
        comp (into {} (for [s domestic] [s (num (g s :comp_paid))]))
        os (fn [s] (or (g s :os) (+ (num (g s :os_b2g)) (num (g s :mixed)))))
        ptax (into {} (for [s domestic] [s (- (va s) (comp s) (num (os s)))]))
        m (fn [ss k] (into {} (for [s ss] [s (num (g s k))])))
        cons {:hh (num (g :hh :cons)) :gov (num (g :gov :cons))}
        inv (m domestic :gcf)
        x {:Y Y :W (sum (vals comp)) :va va :comp comp :ptax ptax
           :int-paid (m sectors :int_paid) :int-recv (m [:hh :nfc :gov :row] :int_recv)
           :div-paid (m sectors :div_paid) :div-recv (m [:hh :nfc :gov :row] :div_recv)
           :itax (m [:hh :nfc :fin] :tax_paid)
           :socc (num (g :hh :socc_paid)) :ben (num (g :hh :socb_recv))
           :cons cons :inv inv :nx (- Y (sum (vals cons)) (sum (vals inv)))
           :other {:hh 0.0 :nfc 0.0 :fin 0.0 :row 0.0}}
        nl0 (:nl (book x))]
    (assoc x :other (into {} (for [s [:hh :nfc :fin :row]] [s (- (num (g s :nl)) (nl0 s))])))))

(defn obs-of
  "A model state (and its year's output) as the data layer writes an
  observation: the end-of-year balance sheet and the year's flows."
  [st o]
  (let [base {:iso "SIM" :year (:year st) :y (:Y st) :flows-ok (some? o) :stocks-source :model :b (:bsm st)}]
    (if-not o
      base
      (let [x (:x o) nl (get-in o [:booked :nl])
            fin-recv (fn [k] (get-in o [:booked :rows k :fin]))]
        (assoc base :f
               (into {} (for [s sectors]
                          [s (cond-> {:nl (nl s)
                                      :int_paid (num (get-in x [:int-paid s])) :div_paid (num (get-in x [:div-paid s]))
                                      :int_recv (if (= s :fin) (+ (fin-recv :interest) (num (get-in x [:int-paid :fin])))
                                                    (num (get-in x [:int-recv s])))
                                      :div_recv (if (= s :fin) (+ (fin-recv :dividends) (num (get-in x [:div-paid :fin])))
                                                    (num (get-in x [:div-recv s])))}
                               (#{:hh :nfc :fin :gov} s) (assoc :va (get-in x [:va s]) :comp_paid (get-in x [:comp s])
                                                                :os (os-of x s) :gcf (get-in x [:inv s]))
                               (#{:hh :nfc :fin} s) (assoc :tax_paid (get-in x [:itax s]))
                               (= s :hh) (assoc :cons (get-in x [:cons :hh]) :socc_paid (:socc x) :socb_recv (:ben x)
                                                :comp_recv (:W x))
                               (= s :gov) (assoc :cons (get-in x [:cons :gov])))])))))))

;; -- behaviour ------------------------------------------------------------------------

(defn property-income
  "Interest and dividends, from the balance sheet at the year's start.
  Interest is paid on each sector's money and debt liabilities and
  received on its money and debt assets at the sectors' implicit rates,
  the financial sector receiving the rest (its margin). Dividends, when
  :div-share is learned, are paid as shares of expected output `ye` (they
  follow profits, not the market value of equity) and received in
  proportion to each sector's equity holdings weighted by its yield
  (:q-a), the financial sector's share closing the row; otherwise at the
  implicit yields on equity (:q-l).

  With :theta below 1 and last year's opening balance sheet `prev`,
  interest is on theta of this year's opening stock and 1 - theta of
  last year's grown at :n (theta 0 holds interest's share of output, 1
  is the opening stock alone): a revaluation or reclassification of the
  stocks passes into interest a part at a time."
  [bsm p ye & [prev]]
  (let [th (if (and prev (:theta p)) (double (:theta p)) 1.0)
        g1 (+ 1.0 (num (:n p)))
        mix (fn [f s] (if (< th 1.0) (+ (* th (f bsm s)) (* (- 1.0 th) g1 (f prev s))) (f bsm s)))
        eq-l (fn [s] (pos bsm s :equity :l)) eq-a (fn [s] (pos bsm s :equity :a))
        r (fn [k s] (num (get-in p [k s])))
        nf [:hh :nfc :gov :row]
        ds (:div-share p)
        paid (if ds
               (into {} (for [s sectors] [s (* (num (get ds s)) (double ye))]))
               (into {} (for [s sectors] [s (* (r :q-l s) (eq-l s))])))
        wts (into {} (for [s sectors] [s (* (r :q-a s) (eq-a s))]))
        tw (sum (vals wts))
        recv (if ds
               (into {} (for [s nf] [s (if (pos? tw) (* (sum (vals paid)) (/ (wts s) tw)) 0.0)]))
               (into {} (for [s nf] [s (wts s)])))]
    {:int-paid (into {} (for [s sectors] [s (* (r :i-l s) (mix ib-l s))]))
     :int-recv (into {} (for [s nf] [s (* (r :i-a s) (mix ib-a s))]))
     :div-paid paid
     :div-recv recv}))

(defn credit
  "New lending to households and firms (F1): to their learned
  debt-to-output path at expected output `ye`."
  [bsm ye p]
  (into {} (for [s [:hh :nfc]]
             [s (if-let [d (get-in p [:debt-target s])] (- (* d ye) (pos bsm s :debt :l)) 0.0)])))

(defn- kappa-slope ^double [ip pi]
  (/ (- (keen/investment ip (+ pi 1e-6)) (keen/investment ip (- pi 1e-6))) 2e-6))

(defn investment
  "Gross capital formation by firms (Keen's kappa of last year's profit
  share `pi`, bnm.keen/investment), households and financial corporations
  (shares of expected output), at expected output `ye`.

  With :investment-rule {:phi :norm} and the state's last share :kappa
  and :pi-prev (`st`), firms' share moves in differences instead: last
  year's share, plus Keen's slope kappa'(pi) (times :slope, 1) times the
  change in the profit share, plus phi of the way to the norm (an error-correction form
  of kappa(pi): Keen's response, its level anchored on the data)."
  [p ye pi & [st]]
  {:nfc (* ye (let [ir (:investment-rule p) k (:kappa st) pp (:pi-prev st)]
                (if (and ir (number? k) (number? pp))
                  (+ k (* (double (:slope ir 1.0)) (kappa-slope (:invest p) (double pi)) (- (double pi) (double pp)))
                     (* (num (:phi ir)) (- (num (:norm ir)) k)))
                  (keen/investment (:invest p) pi))))
   :hh (* ye (num (get-in p [:inv-share :hh])))
   :fin (* ye (num (get-in p [:inv-share :fin])))})

(defn- revalue [bsm rho]
  (into {} (for [[s m] bsm] [s (-> m (update-in [:equity :a] #(* (num %) (+ 1.0 rho))) (update-in [:equity :l] #(* (num %) (+ 1.0 rho))))])))

(defn allocate
  "The flow of funds: each sector's transactions by instrument {:tx
  {sector {instrument {:a :l}}}} given the start balance sheet, the year's
  net lending `nl`, output `y` and new credit; :reval {sector} (equity
  repriced at :rho, both sides), :nl as booked, :buffer the redundant
  equation's residual (booked to government deposits), :Y."
  [open nl y cred p]
  (let [rho (num (:rho p))
        rev (revalue open rho)
        reval (into {} (for [s sectors] [s (* rho (- (pos open s :equity :a) (pos open s :equity :l)))]))
        inject (num (:inject p))
        nl (update nl :fin - inject)
        target (into {} (for [s sectors] [s (+ (nfw open s) (num (nl s)) (reval s))]))
        tgt (fn [s k side] (if-let [r (get-in p [:target s k side])] (* r y) (pos rev s k side)))
        kp (fn [m s k side v] (assoc-in m [s k side] v))
        ;; households and firms: liabilities, then assets in shares, deposits the buffer
        private (fn [b s]
                  (let [l {:money (tgt s :money :l) :debt (+ (pos rev s :debt :l) (num (get cred s)))
                           :equity (tgt s :equity :l) :other (tgt s :other :l)}
                        atot (+ (target s) (sum (vals l)))
                        sh (fn [k] (if-let [x (get-in p [:share s k])] x (let [t (assets rev s)] (if (pos? t) (/ (pos rev s k :a) t) 0.0))))
                        a (into {} (for [k [:debt :equity :other]] [k (* (sh k) atot)]))
                        a (assoc a :money (- atot (sum (vals a))))]
                    (reduce (fn [b k] (-> b (kp s k :a (a k)) (kp s k :l (l k)))) b instruments)))
        gov (fn [b]
              (let [a (into {} (for [k instruments] [k (tgt :gov k :a)]))
                    l (into {} (for [k [:money :equity :other]] [k (tgt :gov k :l)]))
                    l (assoc l :debt (- (sum (vals a)) (target :gov) (sum (vals l))))]
                (reduce (fn [b k] (-> b (kp :gov k :a (a k)) (kp :gov k :l (l k)))) b instruments)))
        row (fn [b]
              (let [l (into {} (for [k instruments] [k (tgt :row k :l)]))
                    a (into {} (for [k [:money :equity :other]] [k (tgt :row k :a)]))
                    a (assoc a :debt (- (+ (target :row) (sum (vals l))) (sum (vals a))))]
                (reduce (fn [b k] (-> b (kp :row k :a (a k)) (kp :row k :l (l k)))) b instruments)))
        b (-> {} (private :hh) (private :nfc) gov row)
        ;; the financial sector: its chosen positions, then what closes each row
        b (-> b (kp :fin :money :a (tgt :fin :money :a))
              (kp :fin :debt :l (tgt :fin :debt :l)) (kp :fin :equity :l (tgt :fin :equity :l))
              (kp :fin :other :l (tgt :fin :other :l)))
        others (remove #{:fin} sectors)
        tot (fn [b k side ss] (sum (for [s ss] (pos b s k side))))
        b (-> b
              (kp :fin :money :l (- (tot b :money :a sectors) (tot b :money :l others)))
              (kp :fin :debt :a (- (tot b :debt :l sectors) (tot b :debt :a others)))
              (kp :fin :equity :a (- (tot b :equity :l sectors) (tot b :equity :a others)))
              (kp :fin :other :a (- (tot b :other :l sectors) (tot b :other :a others))))
        eps (- (nfw b :fin) (target :fin))
        end (-> b (update-in [:fin :money :l] + eps) (update-in [:gov :money :a] + eps))]
    {:tx (into {} (for [s sectors] [s (into {} (for [k instruments] [k {:a (- (pos end s k :a) (pos rev s k :a))
                                                                        :l (- (pos end s k :l) (pos rev s k :l))}]))]))
     :rho rho :reval reval :nl nl :buffer eps :Y y}))

(defn apply-fof
  "The balance sheet after a year's flow of funds: `open` revalued, plus the
  transactions."
  [open {:keys [tx rho]}]
  (let [rev (revalue open (num rho))]
    (into {} (for [s sectors] [s (into {} (for [k instruments] [k {:a (+ (pos rev s k :a) (num (get-in tx [s k :a])))
                                                                   :l (+ (pos rev s k :l) (num (get-in tx [s k :l])))}]))]))))

(defn fof-gap
  "The largest |change in net financial worth - net lending - revaluation|
  over the sectors, government's stated buffer taken out."
  [open end {:keys [nl reval buffer]}]
  (reduce max 0.0 (for [s sectors]
                    (Math/abs (- (nfw end s) (nfw open s) (num (nl s)) (num (reval s)) (if (= s :gov) (num buffer) 0.0))))))

(def w-bounds
  "The wage share's guard, a share of output: Keen's Phillips curve
  stepped a year at a time near full employment would multiply it many
  times over (bnm.keen guards Phi at phi-max for the same reason)."
  [0.05 0.95])

(def lambda-bounds "Employment's guard: the labour force bounds it." [0.3 0.995])

(defn- clamp ^double [x [lo hi]] (max (double lo) (min (double hi) (double x))))

(defn accounts
  "The year's distribution and closure (the D module): from the D state
  `st` (last year's output, wage share, employment, profit share,
  government spending and benefits), the start balance sheet, property
  income, credit and investment. {:state D state for next year :fof
  flow of funds :out {...}}."
  [st open pinc cred inv p]
  (let [n (num (:n p)) g1 (+ 1.0 n)
        t (inc (long (:year st)))
        ye (* (double (:Y st)) g1)
        ph (:phillips p)
        wr (:wage-rule p)
        w (cond wr (clamp (+ (double (:w st)) (* (num (:phi wr)) (- (num (:norm wr)) (double (:w st))))) w-bounds)
                ph (clamp (* (double (:w st)) (Math/exp (- (keen/phillips ph (:lambda st)) (:alpha ph)))) w-bounds)
                :else (double (:w st)))
        cg (* (num (:cg st)) g1) ig (* (num (:ig st)) g1) ben (* (num (:ben st)) g1)
        inv (assoc inv :gov ig)
        itot (sum (vals inv))
        mu (num (:mu p)) X (* (num (:x p)) ye)
        v (:v p) om (:omega p) tp (:tp p) tax (:tax p) oth (:other p)
        ss (fn [m s] (num (get m s)))
        rh (+ (ss (:int-recv pinc) :hh) (ss (:div-recv pinc) :hh))
        ph-h (+ (ss (:int-paid pinc) :hh) (ss (:div-paid pinc) :hh))
        th (ss tax :hh) socc-r (num (:socc p))
        k1 (+ (* (- 1.0 th) (+ (* w (- 1.0 (ss om :hh))) (* (ss v :hh) (- 1.0 (ss tp :hh))))) (- (* socc-r w)) (ss oth :hh))
        k0 (+ (* (- 1.0 th) rh) (- ph-h) ben)
        a1 (num (:a1 p)) a2 (num (:a2 p))
        V (nfw open :hh)
        Y (/ (+ (* (- 1.0 mu) (+ (* a1 k0) (* a2 V) cg itot)) X) (- 1.0 (* (- 1.0 mu) a1 k1)))
        W (* w Y)
        comp (into {} (for [s domestic] [s (* (ss om s) W)]))
        va3 (into {} (for [s [:hh :nfc :fin]] [s (* (ss v s) Y)]))
        va (assoc va3 :gov (- Y (sum (vals va3))))
        ptax (into {} (for [s domestic] [s (* (ss tp s) (va s))]))
        x0 (merge pinc {:Y Y :W W :va va :comp comp :ptax ptax})
        bases (tax-bases x0)
        itax (into {} (for [s [:hh :nfc :fin]] [s (* (ss tax s) (bases s))]))
        socc (* socc-r W)
        yd (+ W (os-of x0 :hh) rh (- ph-h) (- (itax :hh)) (- socc) ben (* (ss oth :hh) Y))
        ch (+ (* a1 yd) (* a2 V))
        A (+ ch cg itot)
        nx (- X (* mu A))
        x (assoc x0 :itax itax :socc socc :ben ben :cons {:hh ch :gov cg} :inv inv :nx nx
                 :other (into {} (for [s [:hh :nfc :fin :row]] [s (* (ss oth s) Y)])))
        booked (book x)
        nl (:nl booked)
        fof (allocate open nl Y cred p)
        pi (profit-share x)
        lam (clamp (* (double (:lambda st)) (+ 1.0 (/ (- (/ (num (:nfc inv)) ye) (num (:kappa-bar p))) (double (get-in p [:invest :nu] 3.0)))))
                   lambda-bounds)]
    {:state {:year t :Y Y :w w :lambda lam :pi pi :cg cg :ig ig :ben ben
             :pi-prev (:pi st) :kappa (/ (num (:nfc inv)) Y)}
     :fof fof
     :out {:year t :Y Y :ye ye :x x :booked booked :inv inv :yd yd
           :wage-share (/ W Y) :lambda lam :pi pi
           :net-lending (into {} (for [s sectors] [s (/ (nl s) Y)]))
           :hh-saving-rate (if (pos? yd) (/ (- yd ch) yd) 0.0)
           :tfm-gap (tfm-gap booked) :buffer (:buffer fof)}}))

(defn step
  "One year of the core from state `st` (the year it describes, :Y, :w,
  :lambda, :pi, :cg, :ig, :ben and the end-of-year balance sheet :bsm):
  {:state next :out the year's flows, identities and Keen quantities}."
  [st p]
  (let [open (:bsm st)
        ye (* (double (:Y st)) (+ 1.0 (num (:n p))))
        pinc (property-income open p ye (:bsm-prev st))
        cred (credit open ye p)
        inv (investment p ye (:pi st) st)
        {d :state fof :fof o :out} (accounts (dissoc st :bsm :bsm-prev) open pinc cred inv p)
        end (apply-fof open fof)
        y (:Y o)]
    {:state (assoc d :bsm end :bsm-prev open)
     :out (assoc o :end end :fof fof :credit cred
                 :fof-gap (fof-gap open end fof) :bsm-gap (bsm-gap end)
                 :private-debt (/ (+ (pos end :hh :debt :l) (pos end :nfc :debt :l)) y)
                 :gov-debt (/ (pos end :gov :debt :l) y))}))

;; -- the assumption audit -------------------------------------------------------------

(def revised
  "learn's options for the rules the assumption audit selected on the
  design origins (to 2002; bnm.sfc-audit, docs/COUPLED-SYSTEMS.md): the
  propensity to consume, the wage share and firms' investment share held
  at their last observed values (no reversion; Keen's investment slope
  off), interest holding its share of output (theta 0), and the shares
  and rates weighted at a half-life of half a year (0.25 did no better).
  Chosen after the first result had been seen."
  {:consumption {:phi 0.0} :wages {:phi 0.0} :investment {:phi 0.0 :slope 0.0} :theta 0.0
   :half-life 0.5 :rate-half-life 0.5})

(def block-docs
  "The behavioural blocks of a year, each a rule the audit can replace by
  observed values (step-given)."
  {:wages "the wage share: Keen's Phillips curve (w' = w exp(Phi(lambda) - alpha))"
   :consumption "households' consumption: C = alpha1 YD + alpha2 V(-1)"
   :investment-nfc "firms' investment: Keen's kappa(pi) of last year's profit share"
   :investment-other "households' and financial investment: shares of expected output"
   :government "government consumption, investment and benefits: grow at trend nominal growth"
   :exports "exports: a share of expected output (imports a share mu of absorption)"
   :growth "expected nominal growth n, which scales investment, government and exports"
   :interest "interest: sector implicit rates on the opening stocks"
   :dividends "dividends: shares of expected output, received by yield-weighted equity"
   :taxes "income taxes and social contributions: rates on their bases"
   :other "other flows (transfers, capital transfers, discrepancies): shares of output"
   :structure "the production structure: value-added shares, compensation shares, production taxes"})

(def blocks (vec (sort (keys block-docs))))

(defn step-given
  "step from `st` with parameters `p`, the behavioural blocks in `given`
  (a set of block-docs' keys) replaced by the observed values of year flows
  `x` (bnm.sfc/observed), as ratios to its output scaled to the model's
  expected output, and expected growth by `g` when :growth is given. With
  x the next year's data each block's error is measured (an oracle); with
  x the origin's, each rule is set against holding its ratio (persistence)."
  [st p given x g]
  (let [gv (fn [k] (contains? given k))
        Yx (double (:Y x))
        r (fn [v] (/ (num v) Yx))
        shares (fn [m] (into {} (for [[k v] m] [k (r v)])))
        a (+ (sum (vals (:cons x))) (sum (vals (:inv x))))
        yd (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (book x) [:nl :hh]))
        bases (tax-bases x)
        p (cond-> p
            (gv :growth) (assoc :n g)
            (gv :wages) (assoc :phillips nil)
            (gv :consumption) (assoc :a1 (/ (get-in x [:cons :hh]) yd) :a2 0.0)
            (gv :investment-other) (assoc :inv-share (shares (select-keys (:inv x) [:hh :fin])))
            (gv :exports) (assoc :x (/ (+ (:nx x) (* (num (:mu p)) a)) Yx))
            (gv :taxes) (assoc :tax (into {} (for [s [:hh :nfc :fin]] [s (if (pos? (bases s)) (/ (num (get-in x [:itax s])) (bases s)) 0.0)]))
                               :socc (/ (num (:socc x)) (num (:W x))))
            (gv :other) (assoc :other (shares (:other x)))
            (gv :structure) (assoc :v (shares (select-keys (:va x) [:hh :nfc :fin]))
                                   :omega (into {} (for [s domestic] [s (/ (num (get-in x [:comp s])) (num (:W x)))]))
                                   :tp (into {} (for [s domestic] [s (let [v (num (get-in x [:va s]))] (if (pos? (Math/abs v)) (/ (num (get-in x [:ptax s])) v) 0.0))]))))
        open (:bsm st)
        g1 (+ 1.0 (num (:n p)))
        ye (* (double (:Y st)) g1)
        st (cond-> st
             (gv :wages) (assoc :w (/ (num (:W x)) Yx))
             (gv :government) (assoc :cg (/ (* (r (get-in x [:cons :gov])) ye) g1) :ig (/ (* (r (get-in x [:inv :gov])) ye) g1)
                                     :ben (/ (* (r (:ben x)) ye) g1)))
        model-pinc (property-income open p ye (:bsm-prev st))
        scaled (fn [k] (into {} (for [[s v] (get x k)] [s (* (r v) ye)])))
        pinc (cond-> model-pinc
               (gv :interest) (assoc :int-paid (scaled :int-paid) :int-recv (scaled :int-recv))
               (gv :dividends) (assoc :div-paid (scaled :div-paid) :div-recv (scaled :div-recv)))
        inv (cond-> (investment p ye (:pi st) st)
              (gv :investment-nfc) (assoc :nfc (* (r (get-in x [:inv :nfc])) ye)))
        cred (credit open ye p)
        {d :state fof :fof o :out} (accounts (dissoc st :bsm :bsm-prev) open pinc cred inv p)]
    {:state (assoc d :bsm (apply-fof open fof) :bsm-prev open) :out o}))

;; -- learning -------------------------------------------------------------------------

(defn- weight ^double [hl origin y] (Math/pow 0.5 (/ (- (double origin) (double y)) (double hl))))

(defn- ew
  "Exponentially weighted mean of [[year value]] (finite values only), or
  `default`."
  [pairs hl origin default]
  (let [ps (filter (comp finite? second) pairs)]
    (if (empty? ps)
      default
      (let [ws (map #(weight hl origin (first %)) ps)]
        (/ (sum (map * ws (map second ps))) (sum ws))))))

(defn- ew-trend
  "The weighted least-squares line through [[year value]], at year `at`;
  the weighted mean when fewer than three points."
  [pairs hl origin at]
  (let [ps (filter (comp finite? second) pairs)]
    (when (seq ps)
      (let [ws (mapv #(weight hl origin (first %)) ps)
            sw (sum ws)
            mx (/ (sum (map * ws (map first ps))) sw)
            my (/ (sum (map * ws (map second ps))) sw)
            sxx (sum (map (fn [w [x _]] (* w (- x mx) (- x mx))) ws ps))
            sxy (sum (map (fn [w [x y]] (* w (- x mx) (- y my))) ws ps))]
        (if (or (< (count ps) 3) (< sxx 1e-12)) my (+ my (* (/ sxy sxx) (- at mx))))))))

(defn- ridge2
  "Weighted ridge regression of y on two regressors through the origin,
  shrunk toward `prior` by `frac` of each regressor's weighted sum of
  squares: [b1 b2]."
  [rows prior frac]
  (let [s (fn [f] (sum (map (fn [[w x1 x2 y]] (* w (f x1 x2 y))) rows)))
        a11 (s (fn [x1 _ _] (* x1 x1))) a22 (s (fn [_ x2 _] (* x2 x2))) a12 (s (fn [x1 x2 _] (* x1 x2)))
        b1 (s (fn [x1 _ y] (* x1 y))) b2 (s (fn [_ x2 y] (* x2 y)))
        l1 (* frac (max a11 1e-12)) l2 (* frac (max a22 1e-12))
        m11 (+ a11 l1) m22 (+ a22 l2)
        r1 (+ b1 (* l1 (first prior))) r2 (+ b2 (* l2 (second prior)))
        det (- (* m11 m22) (* a12 a12))]
    (if (< (Math/abs det) 1e-18)
      prior
      [(/ (- (* r1 m22) (* a12 r2)) det) (/ (- (* m11 r2) (* a12 r1)) det)])))

(defn fit-consumption
  "Godley-Lavoie's C = alpha1 YD + alpha2 V(-1), in ratios to output, from
  rows [weight yd v c]; ridge toward (0.9, 0.02), alpha1 within [0.3, 1.1]
  and alpha2 within [-0.05, 0.15]."
  [rows]
  (let [[a1 a2] (if (< (count rows) 3) [0.9 0.02] (ridge2 rows [0.9 0.02] 0.002))]
    {:a1 (max 0.3 (min 1.1 a1)) :a2 (max -0.05 (min 0.15 a2))}))

(defn fit-investment
  "Keen's kappa(pi) = kappa0 + S exp(c1 (pi - pistar)) on rows [weight pi
  share], as log(share - kappa0) = c0 + c1 pi (weighted, slope shrunk
  toward `prior` and kept positive), turned into bnm.keen's parameters on
  `base` (its kappa0, nu, alpha, beta, delta give the span S)."
  [rows base prior]
  (let [k0 (double (:kappa0 base))
        z (fn [s] (Math/log (max 1e-4 (- s k0))))
        rs (map (fn [[w p s]] [w p (z s)]) rows)
        sw (sum (map first rs))
        [c0 c1] (if (< (count rs) 3)
                  (let [m (if (pos? sw) (/ (sum (map #(* (first %) (nth % 2)) rs)) sw) (Math/log 0.1))
                        mp (if (pos? sw) (/ (sum (map #(* (first %) (second %)) rs)) sw) 0.2)]
                    [(- m (* prior mp)) prior])
                  (let [mp (/ (sum (map #(* (first %) (second %)) rs)) sw)
                        mz (/ (sum (map #(* (first %) (nth % 2)) rs)) sw)
                        sxx (sum (map (fn [[w p _]] (* w (- p mp) (- p mp))) rs))
                        sxy (sum (map (fn [[w p zz]] (* w (- p mp) (- zz mz))) rs))
                        lam (* 0.05 (max sxx 1e-12))
                        c1 (max 1e-6 (/ (+ sxy (* lam prior)) (+ sxx lam)))]
                    [(- mz (* c1 mp)) c1]))
        S (max 1e-6 (- (* (double (:nu base)) (+ (:alpha base) (:beta base) (:delta base))) k0))]
    (assoc base :kappa-slope (* c1 S) :pistar (/ (- (Math/log S) c0) c1))))

(defn- keen-params
  "keen-run's calibration as known with data year `origin` (keen-of): the
  Phillips curve's parameters and the structure (alpha beta delta nu) for
  kappa's span."
  [kn iso origin]
  (when-let [r (keen-of kn iso origin)]
    (let [g (fn [k] (get r (str "keen/" k)))]
      (when (and (g "phi-slope") (g "l-star") (g "alpha"))
        (merge (keen/from-raw keen/gcl)
               {:alpha (g "alpha") :ustar (- 1.0 (g "l-star")) :phi-slope (g "phi-slope")}
               (into {} (for [k ["beta" "delta" "nu"] :when (g k)] [(keyword k) (g k)])))))))

(defn learn
  "The core's parameters for `iso` from the data years up to `origin`
  (panel from read-panel, `kn` keen.csv from read-keen as known with the
  origin's data, keen-of). opts: :half-life (1, best on the design origins
  to 2002 of 1, 2, 3 and 5), :rate-half-life (1: interest and dividend
  rates are persistent, and fell for most of the sample), :phillips (true;
  false holds the wage share, a diagnostic), :mu (0.3, imports over
  absorption where the data have no imports: no country-year with flows
  lacks them), :rho (equity repricing; trend nominal growth).

  The audit's replacements (bnm.sfc-audit), each off when absent:
    :consumption {:phi :norm-half-life}  the propensity to consume out of
        disposable income as last observed (weighted at :half-life),
        moving phi of the way to its norm (weighted at :norm-half-life,
        10): Godley-Lavoie's alpha1 anchored on the data, its wealth
        effect a reversion of saving to a norm (alpha2 0);
    :wages {:phi :norm-half-life}  the wage share moving phi of the way to
        its norm instead of Keen's Phillips curve (:wage-rule);
    :investment {:phi :norm-half-life :slope}  firms' share in differences
        with Keen's slope (times :slope, 1) and phi of reversion to its
        norm (:investment-rule);
    :theta  the share of a change in the stocks that passes into interest
        in the year (property-income)."
  [panel kn iso origin {:keys [half-life rate-half-life fit-half-life mu rho phillips consumption wages investment theta]
                            :or {half-life 1.0 rate-half-life 1.0 fit-half-life 10.0 mu 0.3 phillips true}}]
  (let [hl half-life
        ;; the balance sheets closed as init closes the start (close-init), so
        ;; rates and positions are learned on the stocks the model runs on
        obs (->> (get panel iso) vals (filter #(and (:y %) (<= (:year %) origin))) (sort-by :year)
                 (mapv #(cond-> % (:b %) (update :b (comp :bsm close-init)))))
        by (into {} (map (juxt :year identity)) obs)
        fl (filter :flows-ok obs)
        xs (into {} (for [o fl] [(:year o) (observed o)]))
        e (fn [f default] (ew (for [[y x] xs] [y (f x (by y))]) hl origin default))
        e' (fn [f hl' default] (ew (for [[y x] xs] [y (f x (by y))]) hl' origin default))
        ratio (fn [a b] (when (and (finite? a) (finite? b) (> (Math/abs (double b)) 1e-9)) (/ (double a) (double b))))
        n (ew (for [o obs :let [q (by (dec (:year o)))] :when q] [(:year o) (ratio (- (:y o) (:y q)) (:y q))]) hl origin 0.04)
        prev (fn [y] (:b (by (dec y))))
        ;; a rate is the ratio of weighted means (flow and opening stock over
        ;; the year's output), not the weighted mean of ratios: a year whose
        ;; opening stock is near zero carries no weight, not a huge ratio
        rate-of (fn [pairs] ; [[year flow stock]] in output ratios
                  (let [ps (filter (fn [[_ f d]] (and (finite? f) (finite? d))) pairs)
                        w (map #(weight rate-half-life origin (first %)) ps)
                        sd (sum (map * w (map #(nth % 2) ps)))]
                    (if (and (seq ps) (> (Math/abs sd) 1e-9)) (/ (sum (map * w (map second ps))) sd) 0.0)))
        rate (fn [k s den] (rate-of (for [[y x] xs :let [b (prev y)] :when b] [y (/ (num (get-in x [k s])) (:Y x)) (/ (den b s) (:Y x))])))
        eq-l (fn [b s] (pos b s :equity :l)) eq-a (fn [b s] (pos b s :equity :a))
        stocked (filter :b obs)
        pr (fn [s k side] (ew (for [o stocked] [(:year o) (ratio (pos (:b o) s k side) (:y o))]) hl origin nil))
        nx-share (e (fn [x _] (/ (:nx x) (:Y x))) 0.0)
        trade (for [[y x] xs :let [f (:f (by y)) ex (get-in f [:row :exports]) im (get-in f [:row :imports])] :when (and ex im)]
                [y (/ ex (:Y x)) (/ im (+ (sum (vals (:cons x))) (sum (vals (:inv x)))))])
        mu* (if (seq trade) (ew (map (fn [[y _ m]] [y m]) trade) hl origin mu) mu)
        ;; exports as the frame's net exports plus imports, so X - mu A gives
        ;; Y - C - I at the origin: the trade data's own balance differs by
        ;; the statistical discrepancy (and, where B1G is at basic prices,
        ;; product taxes), which would otherwise jump the first step
        x* (e (fn [x _] (let [a (+ (sum (vals (:cons x))) (sum (vals (:inv x))))] (/ (+ (:nx x) (* mu* a)) (:Y x)))) (+ nx-share (* mu* (- 1.0 nx-share))))
        cons-rows (for [[y x] xs :let [b (prev y) Y (:Y x)] :when b]
                    [(weight fit-half-life origin y) (/ (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (book x) [:nl :hh])) Y)
                     (/ (nfw b :hh) Y) (/ (get-in x [:cons :hh]) Y)])
        kp (or (keen-params kn iso origin) (merge (keen/from-raw keen/gcl) {:phi-slope nil}))
        base (select-keys kp [:kappa0 :nu :alpha :beta :delta :kappa-max])
        prior (if-let [ks (get-in (keen-of kn iso origin) ["keen/kappa-slope"])]
                (/ ks (max 1e-6 (- (* (:nu base) (+ (:alpha base) (:beta base) (:delta base))) (:kappa0 base))))
                20.0)
        inv-rows (for [[y x] xs :let [q (xs (dec y))] :when q]
                   [(weight fit-half-life origin y) (profit-share q) (/ (get-in x [:inv :nfc]) (:Y x))])]
    (merge
     {:n n :half-life hl
      :v (into {} (for [s [:hh :nfc :fin]] [s (e (fn [x _] (/ (get-in x [:va s]) (:Y x))) 0.0)]))
      :omega (into {} (for [s domestic] [s (e (fn [x _] (ratio (get-in x [:comp s]) (:W x))) 0.0)]))
      :tp (into {} (for [s domestic] [s (e (fn [x _] (ratio (get-in x [:ptax s]) (get-in x [:va s]))) 0.0)]))
      :tax (into {} (for [s [:hh :nfc :fin]] [s (e (fn [x _] (let [r (ratio (get-in x [:itax s]) ((tax-bases x) s))] (when (and r (< -1 r 1)) r))) 0.0)]))
      :socc (e (fn [x _] (ratio (:socc x) (:W x))) 0.0)
      :other (into {} (for [s [:hh :nfc :fin :row]] [s (e (fn [x _] (/ (get-in x [:other s]) (:Y x))) 0.0)]))
      :inv-share (into {} (for [s [:hh :fin]] [s (e (fn [x _] (/ (get-in x [:inv s]) (:Y x))) 0.0)]))
      :mu mu* :x x*
      :i-l (into {} (for [s sectors] [s (rate :int-paid s ib-l)]))
      :i-a (into {} (for [s [:hh :nfc :gov :row]] [s (rate :int-recv s ib-a)]))
      :q-l (into {} (for [s sectors] [s (rate :div-paid s eq-l)]))
      :q-a (assoc (into {} (for [s [:hh :nfc :gov :row]] [s (rate :div-recv s eq-a)]))
                  :fin (rate-of (for [o fl :let [b (prev (:year o)) d (get-in o [:f :fin :div_recv])] :when (and b d)]
                                  [(:year o) (/ d (:y o)) (/ (eq-a b :fin) (:y o))])))
      :div-share (into {} (for [s sectors] [s (e (fn [x _] (/ (get-in x [:div-paid s]) (:Y x))) 0.0)]))
      :target (into {} (for [s sectors]
                         [s (into {} (for [k instruments]
                                       [k (into {} (for [side [:a :l] :let [r (pr s k side)] :when r] [side r]))]))]))
      :share (into {} (for [s [:hh :nfc]]
                        [s (into {} (for [k [:debt :equity :other]]
                                      [k (ew (for [o stocked] [(:year o) (ratio (pos (:b o) s k :a) (assets (:b o) s))]) hl origin 0.0)]))]))
      :debt-target (into {} (for [s [:hh :nfc] :let [d (ew-trend (for [o stocked] [(:year o) (ratio (pos (:b o) s :debt :l) (:y o))]) hl origin (inc origin))] :when d]
                              [s d]))
      :rho (or rho n)
      :phillips (when (and phillips (:phi-slope kp)) (select-keys kp [:alpha :ustar :phi-slope :phi-max]))
      :invest (fit-investment inv-rows base prior)
      :kappa-bar (e (fn [x _] (/ (get-in x [:inv :nfc]) (:Y x))) 0.1)}
     (if consumption
       (let [prop (fn [hl'] (ew (for [[y x] xs :let [yd (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (book x) [:nl :hh]))]]
                                  [y (ratio (get-in x [:cons :hh]) yd)]) hl' origin 0.9))
             c (prop hl)]
         {:a1 (+ c (* (num (:phi consumption)) (- (prop (double (:norm-half-life consumption 10.0))) c))) :a2 0.0})
       (fit-consumption cons-rows))
     (when wages
       {:wage-rule {:phi (num (:phi wages))
                    :norm (e' (fn [x _] (ratio (:W x) (:Y x))) (double (:norm-half-life wages 10.0)) 0.6)}})
     (when investment
       {:investment-rule {:phi (num (:phi investment)) :slope (double (:slope investment 1.0))
                          :norm (e' (fn [x _] (/ (get-in x [:inv :nfc]) (:Y x))) (double (:norm-half-life investment 10.0)) 0.1)}})
     (when theta {:theta (double theta)}))))

(defn init
  "The state at the end of data year `origin`: its balance sheet closed on
  the rest of the world (:init-buffer the discrepancy moved), output, the
  wage share, keen-run's employment rate for the origin year (keen-of: as
  known at origin + 2, when the origin's flows are; 0.95 when none), the
  profit share, government spending and benefits; firms' investment share
  (:kappa), and where the year before has them its profit share
  (:pi-prev) and closed balance sheet (:bsm-prev)."
  [panel kn iso origin p]
  (let [ob (get-in panel [iso origin])
        x (observed ob)
        {:keys [bsm buffer]} (close-init (:b ob))
        lam (get (keen-of kn iso origin) "keen/lambda")
        pv (get-in panel [iso (dec origin)])]
    (cond-> {:year origin :Y (:Y x) :w (/ (:W x) (:Y x)) :lambda (or lam 0.95) :pi (profit-share x)
             :cg (get-in x [:cons :gov]) :ig (get-in x [:inv :gov]) :ben (:ben x)
             :kappa (/ (num (get-in x [:inv :nfc])) (:Y x))
             :bsm bsm :init-buffer buffer}
      (and pv (:b pv)) (assoc :bsm-prev (:bsm (close-init (:b pv))))
      (and pv (:flows-ok pv) (:y pv)) (assoc :pi-prev (profit-share (observed pv))))))

;; -- validation ---------------------------------------------------------------------------

(defn- ok-year? [ob] (and ob (:flows-ok ob) (:y ob)))

(defn one-year-ahead
  "Per country and origin y (data year; flows and stocks at y, flows at y +
  1, at least :min-years (5) years of flows up to y): learn on the data up
  to y, start from y, step one year. Rows {:iso :origin :year :source
  :model :persistence :data (net lending over output by sector) :w-model
  :w-data :lambda-model :d-model :d-data :buffer :identity :init-buffer
  (the measured balance sheet's discrepancies that init moved to the rest
  of the world, summed in absolute value, over output)}. :model-ic is
  the model's change applied to the observed level (an intercept
  correction): the data at y plus the model's y + 1 less its own y (the
  same parameters stepped from y - 1), where y - 1 has stocks. opts:
  :origin? (a predicate on y: the design origins alone, say) and learn's."
  [panel kn {:keys [min-years isos origin?] :or {min-years 5 origin? (constantly true)} :as opts}]
  (vec
   (for [iso (sort (or isos (keys panel)))
         :let [obs (get panel iso)]
         y (sort (keys obs))
         :let [ob (obs y) nx (obs (inc y))]
         :when (and (origin? y) (ok-year? ob) (:b ob) (ok-year? nx)
                    (>= (count (filter #(and (<= (key %) y) (:flows-ok (val %))) obs)) min-years))
         :let [p (learn panel kn iso y opts)
               st (init panel kn iso y p)
               {s :state o :out} (step st p)
               xd (observed nx) xp (observed ob)
               nl-d (:nl (book xd)) nl-p (:nl (book xp))
               nld (into {} (for [sct sectors] [sct (/ (num (get-in nx [:f sct :nl])) (:y nx))]))
               nlp (into {} (for [sct sectors] [sct (/ (num (get-in ob [:f sct :nl])) (:y ob))]))]
         :when (every? finite? (vals (:net-lending o)))
         :let [ob0 (obs (dec y))
               m0 (when (and (ok-year? ob0) (:b ob0))
                    (:net-lending (:out (step (init panel kn iso (dec y) p) p))))
               ic (when (and m0 (every? finite? (vals m0)))
                    (into {} (for [sct sectors] [sct (+ (nlp sct) (- ((:net-lending o) sct) (m0 sct)))])))]]
     {:iso iso :origin y :year (inc y) :source (:stocks-source ob)
      :model (:net-lending o) :persistence nlp :data nld :model-ic ic
      :y-growth-model (- (/ (:Y o) (:y ob)) 1.0) :y-growth-data (- (/ (:y nx) (:y ob)) 1.0)
      :w-model (:w s) :w-data (/ (:W xd) (:Y xd)) :w-prev (/ (:W xp) (:Y xp))
      :lambda-model (:lambda s) :lambda-prev (:lambda st)
      :d-model (:private-debt o)
      :d-data (when (:b nx) (/ (+ (pos (:b nx) :hh :debt :l) (pos (:b nx) :nfc :debt :l)) (:y nx)))
      :buffer (/ (:buffer o) (:Y o))
      :init-buffer (/ (sum (map #(Math/abs (double %)) (vals (:init-buffer st)))) (:Y st))
      :identity (/ (max (:tfm-gap o) (:fof-gap o) (:bsm-gap o)) (:Y o))
      :nl-gov-closure (- (/ (nl-d :gov) (:Y xd)) (nld :gov))
      :nl-p-check (- (/ (nl-p :hh) (:Y xp)) (nlp :hh))})))

(defn- mae [xs] (when (seq xs) (/ (sum (map #(Math/abs (double %)) xs)) (count xs))))

(defn score
  "MAE of the model and of persistence against the data, by sector and over
  the four domestic sectors (:all), with the share of forecasts where the
  model is nearer (:wins)."
  ([rows] (score rows domestic :model))
  ([rows ss k]
   (let [rows (filter k rows)
         one (fn [rs ss]
               (let [pairs (for [r rs s ss] [(- (get-in r [k s]) (get-in r [:data s])) (- (get-in r [:persistence s]) (get-in r [:data s]))])]
                 {:n (count rs) :model (mae (map first pairs)) :persistence (mae (map second pairs))
                  :wins (when (seq pairs) (/ (count (filter (fn [[m p]] (< (Math/abs (double m)) (Math/abs (double p)))) pairs)) (double (count pairs))))}))]
     (assoc (into {} (for [s sectors] [s (one rows [s])])) :all (one rows ss)))))
