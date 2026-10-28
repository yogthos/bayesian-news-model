(ns bnm.modules.sfc
  "The stock-flow consistent core (bnm.sfc) as engine modules (bnm.engine),
  one country's, split as docs/COUPLED-SYSTEMS.md 6b orders the year:

    :sfc-finance  (F) the balance sheets of the five sectors, its stocks.
                  Its levels are the balance sheet at the year's start: the
                  last one plus last year's flow of funds and revaluation,
                  which :sfc-accounts booked (read at lag 1), with the
                  balance-sheet and flow-of-funds identities checked. Its
                  rates: interest and dividends from the stocks (F3), new
                  credit to households and firms (F1), the cost of credit
                  (F2).
    :sfc-firms    (R) investment: Keen's kappa of last year's profit share
                  (bnm.keen/investment), households' and financial
                  investment.
    :sfc-accounts (D) the year's books: the wage share by Keen's Phillips
                  curve, output, every flow, net lending, the flow of funds
                  and the buffer; the distribution channels (D1, D4, D5)
                  and profits (R1).

  Each step's levels-then-rates order is the engine's: finance's balance
  sheet is a level, so the accounts read it within the year; finance reads
  the accounts' flow of funds a year later, as a stock integrates its
  flows. Stepped together they give bnm.sfc/step's path exactly.

  Emits are numbers (ratios to output, rates, the output level) or, for the
  matrices passed between the three (sfc/bsm, sfc/property-income,
  sfc/credit, sfc/investment, sfc/fof), maps with unit \"matrix\", which a
  table writer skips."
  (:require [bnm.sfc :as sfc]))

(def ^:private model {:data-year 0})

(defn- e
  ([v unit] (e v unit nil nil))
  ([v unit channel] (e v unit channel nil))
  ([v unit channel phase] (cond-> {:var v :unit unit :known model} channel (assoc :channel channel) phase (assoc :phase phase))))

(def ^:private mat "matrix")
(def ^:private level-unit "national currency, millions")
(def ^:private ratio "ratio to GDP")
(def ^:private ratio-yr "ratio to GDP per year")

(def observes
  "What measures the core's stocks and flows, with publication lags."
  [{:var "sfc/bsm" :source "OECD Table 720 (T0720) financial balance sheets; Fed Z.1 (US)" :known {:data-year 2}}
   {:var "sfc/net-lending-*" :source "OECD Table 14 (T0800) B9 by sector" :known {:data-year 2}}
   {:var "sfc/wage-share" :source "OECD Table 14 D1 / S1 B1G" :known {:data-year 2}}
   {:var "sfc/employment" :source "keen-run's lambda (JST, WDI unemployment)" :known {:data-year 1}}
   {:var "sfc/bsm (rest of the world, where T720 has none)" :source "Lane & Milesi-Ferretti EWN" :known {:data-year 2}}])

(defn- ratio-of [x y] (when (and x y (pos? (double y))) (/ (double x) (double y))))

(defn- ids
  "The module ids for `iso`: :sfc-finance ... alone, :sfc-finance-<iso>
  ... with :country-ids (several countries in one run)."
  [iso {:keys [country-ids]}]
  (into {} (for [k [:sfc-finance :sfc-firms :sfc-accounts]]
             [k (if country-ids (keyword (str (name k) "-" iso)) k)])))

(defn- placed
  "The module with its id, label and span (opts :span)."
  [m iso opts]
  (cond-> (assoc m :module ((ids iso opts) (:module m)) :label (name (:module m)))
    (:span opts) (assoc :span (:span opts))))

(defn finance-module
  [iso st p & [opts]]
  (letfn [(opening [state in]
            (cond (:open state) (:open state)
                  (get in "sfc/fof") (sfc/apply-fof (:bsm state) (get in "sfc/fof"))
                  :else (:bsm state)))]
    (placed
    {:module :sfc-finance :group :F :place (keyword iso) :dt 1
     :doc "The five sectors' balance sheets (bnm.sfc): interest, dividends and new credit from them"
     :reads [{:var "sfc/fof" :unit mat :lag 1 :phase :level :from (:sfc-accounts (ids iso opts))}
             {:var "sfc/output" :unit level-unit :lag 1 :from (:sfc-accounts (ids iso opts))}]
     :emits [(e "sfc/bsm" mat nil :level) (e "sfc/bsm-gap" ratio nil :level) (e "sfc/fof-gap" ratio nil :level)
             (e "sfc/property-income" mat) (e "sfc/credit" mat)
             (e "sfc/credit-hh" ratio-yr :F1) (e "sfc/credit-nfc" ratio-yr :F1)
             (e "sfc/rate-hh" "per year, nominal" :F2) (e "sfc/rate-nfc" "per year, nominal" :F2)
             (e "sfc/interest-hh" ratio-yr :F3) (e "sfc/interest-nfc" ratio-yr :F3) (e "sfc/interest-gov" ratio-yr :F3)]
     :stocks {:bsm {:unit level-unit :info false}}
     :params {:sfc {:value p}}
     :observes (filterv #(= "sfc/bsm" (:var %)) observes)
     :init {:bsm (:bsm st) :Y (:Y st) :bsm-prev (:bsm-prev st)}
     :observe (fn [state in _ _]
                (let [open (opening state in)
                      fof (or (:fof state) (get in "sfc/fof"))
                      y (or (:Y fof) (:Y state))]
                  {"sfc/bsm" open
                   "sfc/bsm-gap" (/ (sfc/bsm-gap open) y)
                   "sfc/fof-gap" (if fof (/ (sfc/fof-gap (:bsm state) open fof) y) 0.0)}))
     :step (fn [state in ps _ _]
             (let [p (:sfc ps)
                   open (opening state in)
                   y0 (or (get in "sfc/output") (:Y state))
                   ye (* y0 (+ 1.0 (double (:n p))))
                   ;; last year's opening balance sheet, for interest's theta
                   prev (if (get in "sfc/fof") (:bsm state) (:bsm-prev state))
                   pinc (sfc/property-income open p ye prev)
                   cred (sfc/credit open ye p)]
               {:state {:bsm open :Y y0}
                :emits {"sfc/property-income" pinc "sfc/credit" cred
                        "sfc/credit-hh" (/ (:hh cred) ye) "sfc/credit-nfc" (/ (:nfc cred) ye)
                        "sfc/rate-hh" (get-in p [:i-l :hh]) "sfc/rate-nfc" (get-in p [:i-l :nfc])
                        "sfc/interest-hh" (/ (get-in pinc [:int-paid :hh]) ye)
                        "sfc/interest-nfc" (/ (get-in pinc [:int-paid :nfc]) ye)
                        "sfc/interest-gov" (/ (get-in pinc [:int-paid :gov]) ye)}}))}
    iso opts)))

(defn firms-module
  [iso st p & [opts]]
  (placed
  {:module :sfc-firms :group :R :place (keyword iso) :dt 1
   :doc "Investment by sector: firms' by Keen's kappa(pi) (bnm.keen/investment)"
   :reads [{:var "sfc/output" :unit level-unit :lag 1 :from (:sfc-accounts (ids iso opts))}
           {:var "sfc/nfc-profit-share" :unit ratio :lag 1 :from (:sfc-accounts (ids iso opts))}
           {:var "sfc/nfc-investment-share" :unit ratio :lag 1 :from (:sfc-accounts (ids iso opts))}]
   :emits [(e "sfc/investment" mat) (e "sfc/nfc-investment" ratio)]
   :stocks {}
   :params {:sfc {:value p}}
   :init (select-keys st [:Y :pi :pi-prev :kappa])
   :step (fn [state in ps _ _]
           (let [p (:sfc ps)
                 y0 (or (get in "sfc/output") (:Y state))
                 read-pi (get in "sfc/nfc-profit-share")
                 pi (or read-pi (:pi state))
                 ;; the profit share the year before pi: last step's, or the start's
                 pi-prev (if read-pi (:pi state) (:pi-prev state))
                 kappa (or (get in "sfc/nfc-investment-share") (:kappa state))
                 ye (* y0 (+ 1.0 (double (:n p))))
                 inv (sfc/investment p ye pi {:kappa kappa :pi-prev pi-prev})]
             {:state (assoc state :pi pi :pi-prev pi-prev :kappa kappa)
              :emits {"sfc/investment" inv "sfc/nfc-investment" (/ (:nfc inv) ye)}}))}
  iso opts))

(defn accounts-module
  [iso st p & [opts]]
  (placed
  {:module :sfc-accounts :group :D :place (keyword iso) :dt 1
   :doc "The year's transaction-flow matrix and flow of funds (bnm.sfc), Keen's wage share and employment inside it"
   :reads (let [{f :sfc-finance r :sfc-firms} (ids iso opts)]
            [{:var "sfc/bsm" :unit mat :from f}
             {:var "sfc/property-income" :unit mat :from f}
             {:var "sfc/credit" :unit mat :from f}
             {:var "sfc/investment" :unit mat :from r}])
   :emits [(e "sfc/fof" mat) (e "sfc/output" level-unit)
           (e "sfc/net-lending-hh" ratio) (e "sfc/net-lending-nfc" ratio) (e "sfc/net-lending-fin" ratio)
           (e "sfc/net-lending-gov" ratio :D4) (e "sfc/net-lending-row" ratio)
           (e "sfc/tfm-gap" ratio) (e "sfc/buffer" ratio)
           (e "sfc/wage-share" "share of GDP" :D1) (e "sfc/employment" "rate" :D1)
           (e "sfc/nfc-profit-share" ratio :R1) (e "sfc/nfc-investment-share" ratio)
           (e "sfc/gov-debt" ratio :D4) (e "sfc/hh-saving-rate" "share of disposable income" :D5)
           (e "sfc/private-debt" ratio) (e "sfc/hh-debt" ratio) (e "sfc/nfc-debt" ratio)]
   :stocks {:w {:unit "share of GDP"} :lambda {:unit "rate"}}
   :params {:sfc {:value p}}
   :observes (filterv #(not= "sfc/bsm" (:var %)) observes)
   :init (dissoc st :bsm :bsm-prev :init-buffer)
   :step (fn [state in ps _ _]
           (let [p (:sfc ps)
                 open (get in "sfc/bsm")
                 {d :state fof :fof o :out} (sfc/accounts state open (get in "sfc/property-income") (get in "sfc/credit") (get in "sfc/investment") p)
                 end (sfc/apply-fof open fof)
                 y (:Y o)
                 nl (:net-lending o)]
             {:state d
              :emits {"sfc/fof" fof "sfc/output" y
                      "sfc/net-lending-hh" (:hh nl) "sfc/net-lending-nfc" (:nfc nl) "sfc/net-lending-fin" (:fin nl)
                      "sfc/net-lending-gov" (:gov nl) "sfc/net-lending-row" (:row nl)
                      "sfc/tfm-gap" (/ (:tfm-gap o) y) "sfc/buffer" (/ (:buffer o) y)
                      "sfc/wage-share" (:wage-share o) "sfc/employment" (:lambda o)
                      "sfc/nfc-profit-share" (:pi o) "sfc/nfc-investment-share" (:kappa d)
                      "sfc/gov-debt" (ratio-of (sfc/pos end :gov :debt :l) y)
                      "sfc/hh-saving-rate" (:hh-saving-rate o)
                      "sfc/private-debt" (/ (+ (sfc/pos end :hh :debt :l) (sfc/pos end :nfc :debt :l)) y)
                      "sfc/hh-debt" (/ (sfc/pos end :hh :debt :l) y)
                      "sfc/nfc-debt" (/ (sfc/pos end :nfc :debt :l) y)}}))}
  iso opts))

(defn modules
  "The core for `iso` as three engine modules, started from bnm.sfc state
  `st` with parameters `p` (bnm.sfc/learn). opts: :country-ids, ids
  :sfc-finance-<iso> ... so several countries run together (the table's
  module column keeps sfc-finance ..., their :label); :span [from to],
  the years they step."
  ([iso st p] (modules iso st p {}))
  ([iso st p opts]
   [(finance-module iso st p opts) (firms-module iso st p opts) (accounts-module iso st p opts)]))
