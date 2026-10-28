(ns bnm.modules.consistency
  "Sheaf consistency across the coupled modules (docs/COUPLED-SYSTEMS.md
  6d, item 2; bead bnm-4hv.6 phase B): every quantity two subsystems both
  see is an edge, and its coboundary (bnm.sheaf/coboundary) is the gap
  between what one says and what the other says, written per country and
  data year as a diagnostic. Nothing is reconciled: a gap is reported,
  not fixed.

  The edges (each gap is the first named less the second):
    sheaf/wage-share          the SFC core's wage share (D1 over GDP)
                              against Keen's omega (keen.csv, PWT's labour
                              share of value added), at Keen's state year
    sheaf/employment          the SFC core's employment rate against
                              Keen's lambda
    sheaf/private-debt        the SFC core's household and NFC debt over
                              GDP against Keen's d (BIS, JST)
    sheaf/nfc-investment      the SFC core's NFC investment against EU
                              KLEMS gross fixed capital formation of the
                              NFC industries (bnm.capacity/nfc-industries),
                              both over the SFC core's output
    sheaf/nfc-investment-data the same edge on the data alone: OECD Table
                              14's S11 P5 (the SFC panel) against KLEMS,
                              over the SFC core's output where it runs and
                              else not written; what of the model's gap is
                              the sources' coverage

  The module steps last in the year (group P, as the diagnostics read
  everything), keeps the SFC core's values by data year, and writes a gap
  when the other side's value for that data year becomes known: Keen's at
  its state year (known a year later), KLEMS two years later
  (bnm.allocation/lags). The SFC core's values are the model's, known in
  their year."
  (:require [bnm.allocation :as al]
            [bnm.capacity :as cap]
            [bnm.sheaf :as sheaf]))

(def sfc-vars
  "The SFC accounts' emits the edges read, with their units."
  {"sfc/output" "national currency, millions" "sfc/wage-share" "share of GDP" "sfc/employment" "rate"
   "sfc/private-debt" "ratio to GDP" "sfc/nfc-investment-share" "ratio to GDP"})

(def keen-edges
  "[edge sfc-variable keen-variable keen-unit]."
  [["sheaf/wage-share" "sfc/wage-share" "keen/omega" "share of value added"]
   ["sheaf/employment" "sfc/employment" "keen/lambda" "rate"]
   ["sheaf/private-debt" "sfc/private-debt" "keen/d" "ratio to GDP"]])

(def emits
  [{:var "sheaf/wage-share" :unit "share of GDP" :known {:data-year 1}}
   {:var "sheaf/employment" :unit "rate" :known {:data-year 1}}
   {:var "sheaf/private-debt" :unit "ratio to GDP" :known {:data-year 1}}
   {:var "sheaf/nfc-investment" :unit "ratio to GDP" :known {:data-year (al/lags "klems")}}
   {:var "sheaf/nfc-investment-data" :unit "ratio to GDP" :known {:data-year (al/lags "klems")}}])

(defn klems-nfc
  "EU KLEMS GFCF of the NFC industries, `iso`'s data year `y` (LCU m), or
  nil where KLEMS has none of them."
  [idx iso y]
  (let [m (get-in idx ["klems" "gfcf" iso y])
        xs (keep #(get m %) cap/nfc-industries)]
    (when (seq xs) (reduce + 0.0 (map double xs)))))

(defn- fmt [x] (al/fmt x))

(defn- row-of
  "{iso [value data-year ...]} of an emit's rows, the last for an iso."
  [rows]
  (into {} (for [[iso v dy] rows :when (and iso (seq (str v)))] [iso [(parse-double (str v)) dy]])))

(defn module
  "The consistency module over the SFC countries `sfc-ids` ({iso
  accounts-module-id}), reading Keen when `keen?` and KLEMS and the SFC
  panel's NFC investment from bnm.allocation/index `idx` when given."
  [{:keys [sfc-ids keen? idx]}]
  (let [lag (al/lags "klems")]
    {:module :consistency :group :P :place :country :dt 1
     :doc "Sheaf gaps between subsystems that see one quantity (bnm.sheaf/coboundary), as diagnostics"
     :reads (vec (concat
                  (for [[iso id] (sort sfc-ids) [v u] (sort sfc-vars)] {:var v :unit u :from id :as [iso v]})
                  (when keen?
                    (concat (for [[_ _ kv u] keen-edges] {:var kv :unit u :from :keen :known {:data-year 1}})
                            [{:var "keen/state-year" :unit "mixed" :from :keen :known {:data-year 1}}]))
                  (when idx
                    [{:var "klems/gfcf" :unit "LCU m" :source "data/raw/econ/euklems-intanprod-2023/capital_accounts.csv via data/derived/allocation/panel.csv" :known {:data-year lag}}
                     {:var "sfc/nfc-gcf" :unit "LCU m" :source "OECD Table 14 S11 P5 via data/derived/allocation/panel.csv" :known {:data-year lag}}])))
     :emits emits
     :stocks {:sfc {:unit "the SFC core's values by data year" :info true}}
     :params {}
     :init {:sfc {}}
     :step (fn [state in _ t _]
             (let [rec (reduce (fn [m [iso _]]
                                 (let [vs (into {} (for [v (keys sfc-vars) :let [x (get in [iso v])] :when (number? x)] [v x]))]
                                   (if (seq vs) (assoc m [iso t] vs) m)))
                               (into {} (filter (fn [[[_ y] _]] (> y (- t 8)))) (:sfc state))
                               sfc-ids)
                   at (fn [iso y v] (get-in rec [[iso y] v]))
                   keen (when keen?
                          (let [sy (row-of (get in "keen/state-year"))]
                            (into {} (for [[edge sv kv _] keen-edges]
                                       [edge (vec (for [[iso [k _]] (sort (row-of (get in kv)))
                                                        :let [y (some-> (get sy iso) first long)
                                                              own (when y (at iso y sv))]
                                                        :when (and own k (number? k))]
                                                    [iso (fmt (get (sheaf/coboundary own {:keen k}) :keen)) (str y) "keen"]))]))))
                   y (- t lag)
                   inv (when idx
                         (let [isos (sort (distinct (concat (keys (get-in idx ["klems" "gfcf"])) (map first (keys rec)))))]
                           {"sheaf/nfc-investment"
                            (vec (for [iso isos
                                       :let [k (klems-nfc idx iso y) out (at iso y "sfc/output") sh (at iso y "sfc/nfc-investment-share")]
                                       :when (and k out sh (pos? out))]
                                   [iso (fmt (get (sheaf/coboundary sh {:klems k} {:klems #(/ % out)}) :klems)) (str y) "klems"]))
                            "sheaf/nfc-investment-data"
                            (vec (for [iso isos
                                       :let [k (klems-nfc idx iso y) out (at iso y "sfc/output")
                                             d (get-in idx ["sfc" "nfc" iso y "gcf"])]
                                       :when (and k d out (pos? out))]
                                   [iso (fmt (get (sheaf/coboundary (/ d out) {:klems k} {:klems #(/ % out)}) :klems)) (str y) "klems"]))}))]
               {:state {:sfc rec}
                :emits (into {} (filter (comp seq val)) (merge keen inv))}))}))
