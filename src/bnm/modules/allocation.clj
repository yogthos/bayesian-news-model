(ns bnm.modules.allocation
  "The allocation channel as engine modules (bnm.engine; docs/COUPLED-
  SYSTEMS.md 5.1, 5.2; bead bnm-4hv.3), over bnm.allocation's panel:

    :allocation  (F) credit by sector. Its levels are the credit stocks'
                 shares, as known: allocation/<source>/share-<group>. Its
                 rates (channel F1) are the year's flows from two
                 consecutive data years of one source:
                 allocation/<source>/share-change-<group> (pp) and, where
                 the source is in currency, allocation/<source>/new-share-
                 <group> (the share of new credit).
    :investment  (R) investment and capital by industry. Its levels are
                 the capital stock's shares (investment/klems/capital-
                 share-<industry>, investment/wiod/...). Its rates: the
                 shares of gross fixed capital formation (klems), real
                 capital growth (klems) and capital growth against the
                 economy's (wiod), investment demand by product (lrwiod);
                 the link to the product graph and the stages,
                 capacity/growth/<product> and capacity/growth/stage/
                 <commodity>-<stage> (klems), capacity/relative-growth/...
                 (wiod), for every product and stage whose industry the
                 concordance gives; and the credit shares of the ISIC
                 sections that are also its industries, read from
                 :allocation a year late (investment/credit-share-<section>),
                 the financing side of 5.2.

  A source's rows enter at their known year (data year + `bnm.allocation/
  lags`), each source under its own variables so each is checked against
  its own lag. A step emits the rows that became known since the last
  (all those known by the first step at the first). Emit values are rows
  [iso value data-year source]; iso is ISO3 (EMU for the euro area's NACE
  loans), so a reader of the product graph maps it to EXIOBASE's region.

  The control layer's choice of the mix (the Dirichlet DLM of 5.1) is not
  here: it is the registered test's model (COUPLED-SYSTEMS.md 9d)."
  (:require [bnm.allocation :as al]))

;; -- rows by the year they become known ------------------------------------------------------

(defn- by-known
  "A sorted map {known [[key iso year] ...]} over every country-year of
  the sources and measures `keys` ([source measure]) in `idx`."
  [idx keys]
  (reduce (fn [m [[source measure :as k] iso year]]
            (update m (+ year (al/lags source)) (fnil conj []) [k iso year]))
          (sorted-map)
          (sort (for [[source measure :as k] keys
                      [iso ys] (get-in idx [source measure]) year (clojure.core/keys ys)]
                  [k iso year]))))

(defn- newly
  "The entries of `bk` known in (through, t]."
  [bk through t]
  (mapcat val (if through (subseq bk > through <= t) (subseq bk <= t))))

(defn- push [m v r] (update m v (fnil conj []) r))

;; -- credit ---------------------------------------------------------------------------------

(defn- credit-var [spec kind g] (str "allocation/" (:label spec) "/" kind "-" g))

(defn- credit-emits []
  (vec (for [k al/credit-keys
             :let [spec (al/credit-spec k) known {:data-year (al/lags (first k))}]
             g (sort (keys (:groups spec)))
             e (cond-> [{:var (credit-var spec "share" g) :unit (:unit spec) :known known :phase :level :channel :F1}
                        {:var (credit-var spec "share-change" g) :unit "pp per year" :known known :channel :F1}]
                 (:flows spec) (conj {:var (credit-var spec "new-share" g) :unit "share of new credit" :known known :channel :F1}))]
         e)))

(def ^:private sources
  {"mv" "data/raw/slow/mullerverner (Build/MuellerVerner.dta)"
   "bis" "data/raw/slow/bis/WS_TC.zip"
   "pboc" "data/raw/econ/pbc-tsf-credit"
   "ecb" "data/raw/econ/ecb-mfi-loans-by-sector"
   "klems" "data/raw/econ/euklems-intanprod-2023/capital_accounts.csv"
   "wiod" "data/raw/io/wiod2016/Socio_Economic_Accounts.xlsx"
   "lrwiod" "data/raw/io/lrwiod/lr_wiod_niot_final.csv"})

(defn- src [v unit s lag] {:var v :unit unit :source (str (sources s) " via data/derived/allocation/panel.csv") :known {:data-year lag}})

(defn allocation-module
  [idx]
  (let [bk (by-known idx al/credit-keys)]
    {:module :allocation :group :F :place :country :dt 1
     :doc "Credit by sector as known: shares of the stock, share changes and shares of new credit (bnm.allocation)"
     :reads [(src "mv/credit" "LCU" "mv" 2) (src "bis/credit" "% GDP" "bis" 1) (src "pboc/credit" "CNY 100m" "pboc" 1)
             (src "pboc/tsf" "CNY tn" "pboc" 1) (src "ecb/credit" "national currency, millions" "ecb" 1)]
     :emits (credit-emits)
     :stocks {:credit {:unit "credit stocks by sector, as known" :info true}}
     :params {}
     :init {:through nil}
     :observe (fn [state _ _ t]
                (reduce (fn [acc [[source measure :as k] iso year]]
                          (let [spec (al/credit-spec k)]
                            (reduce (fn [acc [g s]] (push acc (credit-var spec "share" g) [iso (al/fmt s) year source]))
                                    acc (sort (al/shares spec (get-in idx [source measure iso year]))))))
                        {} (newly bk (:through state) t)))
     :step (fn [state _ _ t _]
             {:state (assoc state :through t)
              :emits (reduce (fn [acc [[source measure :as k] iso year]]
                               (let [spec (al/credit-spec k)
                                     a (get-in idx [source measure iso (dec year)])
                                     b (get-in idx [source measure iso year])]
                                 (if-not a
                                   acc
                                   (let [{:keys [share-change new-share]} (al/flows spec a b)
                                         acc (reduce (fn [acc [g x]] (push acc (credit-var spec "share-change" g) [iso (al/fmt x) year source]))
                                                     acc (sort share-change))]
                                     (reduce (fn [acc [g x]] (push acc (credit-var spec "new-share" g) [iso (al/fmt x) year source]))
                                             acc (sort new-share))))))
                             {} (newly bk (:through state) t))})}))

;; -- investment -------------------------------------------------------------------------------

(def credit-sections
  "Müller-Verner's ISIC sections that are also investment industries."
  ["A" "B" "C" "F" "G" "I" "K" "L"])

(defn- iv [s kind x] (str "investment/" s "/" kind "-" x))

(defn- investment-emits []
  (vec (concat
        (for [s ["klems" "wiod"] ind al/industries]
          {:var (iv s "capital-share" ind) :unit "share of the capital stock" :known {:data-year (al/lags s)} :phase :level})
        (for [ind al/industries]
          {:var (iv "klems" "gfcf-share" ind) :unit "share of gross fixed capital formation" :known {:data-year 2} :channel :R3})
        (for [ind al/industries]
          {:var (iv "klems" "capital-growth" ind) :unit "log change per year" :known {:data-year 2} :channel :R3})
        (for [ind al/industries]
          {:var (iv "wiod" "relative-capital-growth" ind) :unit "log change against the economy's capital" :known {:data-year 3} :channel :R3})
        (for [p al/lrwiod-products]
          {:var (iv "lrwiod" "gfcf-product-share" p) :unit "share of gross fixed capital formation" :known {:data-year 3}})
        (for [g credit-sections]
          {:var (str "investment/credit-share-" g) :unit "share of private credit" :known {:data-year 2} :channel :F1})
        [{:var "capacity/growth/*" :unit "log change per year" :known {:data-year 2} :channel :R6}
         {:var "capacity/relative-growth/*" :unit "log change against the economy's capital" :known {:data-year 3} :channel :R6}])))

(defn- link-targets
  "{industry [node ...]}: the product graph's products and the stages
  (\"stage/<commodity>-<stage>\") each industry's growth is carried to."
  [{:keys [products stages]}]
  (reduce (fn [m [node ind]] (if ind (update m ind (fnil conj []) node) m))
          {}
          (concat (sort products)
                  (for [[c s] stages] [(str "stage/" c "-" s) (al/stage-industry c s)]))))

(defn investment-module
  [idx links]
  (let [bk (by-known idx [["klems" "capital"] ["klems" "gfcf"] ["klems" "capital-volume"] ["wiod" "capital"] ["lrwiod" "gfcf-product"]])
        targets (link-targets links)
        at (fn [s m iso y] (get-in idx [s m iso y]))
        link (fn [acc prefix ind row]
               (reduce (fn [acc node] (push acc (str prefix node) row)) acc (get targets ind)))]
    {:module :investment :group :R :place :country :dt 1
     :doc "Investment and capital by industry, and the link to capacity in the product graph and stages (bnm.allocation)"
     :reads (into [(src "klems/capital-accounts" "LCU m" "klems" 2) (src "wiod/capital" "LCU m" "wiod" 3)
                   (src "lrwiod/gfcf" "USD m" "lrwiod" 3)
                   {:var "supply/concordance" :unit "weight" :source "data/derived/supply/concordance.csv" :known {:data-year 0}}]
                  (for [g credit-sections]
                    {:var (str "allocation/mv/share-" g) :unit "share of private credit" :lag 1 :from :allocation :known {:data-year 2}}))
     :emits (investment-emits)
     :stocks {:capital {:unit "capital stock by industry, as known" :info true}}
     :params {}
     :init {:through nil}
     :observe (fn [state _ _ t]
                (reduce (fn [acc [[s m] iso y]]
                          (if (= m "capital")
                            (reduce (fn [acc [ind x]] (if (some #{ind} al/industries) (push acc (iv s "capital-share" ind) [iso (al/fmt x) y s]) acc))
                                    acc (sort (al/industry-shares (at s m iso y))))
                            acc))
                        {} (newly bk (:through state) t)))
     :step (fn [state in _ t _]
             (let [emits (reduce
                          (fn [acc [[s m] iso y]]
                            (case [s m]
                              ["klems" "gfcf"]
                              (reduce (fn [acc [ind x]] (if (some #{ind} al/industries) (push acc (iv s "gfcf-share" ind) [iso (al/fmt x) y s]) acc))
                                      acc (sort (al/industry-shares (at s m iso y))))
                              ["klems" "capital-volume"]
                              (let [a (at s m iso (dec y)) b (at s m iso y)]
                                (reduce (fn [acc ind]
                                          (if-let [g (al/log-growth (get a ind) (get b ind))]
                                            (let [row [iso (al/fmt g) y s]]
                                              (-> acc (push (iv s "capital-growth" ind) row) (link "capacity/growth/" ind row)))
                                            acc))
                                        acc al/industries))
                              ["wiod" "capital"]
                              (let [a (at s m iso (dec y)) b (at s m iso y)]
                                (reduce (fn [acc ind]
                                          (if-let [g (al/relative-growth (get a ind) (get a "TOT") (get b ind) (get b "TOT"))]
                                            (let [row [iso (al/fmt g) y s]]
                                              (-> acc (push (iv s "relative-capital-growth" ind) row) (link "capacity/relative-growth/" ind row)))
                                            acc))
                                        acc al/industries))
                              ["lrwiod" "gfcf-product"]
                              (reduce (fn [acc [p x]] (push acc (iv s "gfcf-product-share" p) [iso (al/fmt x) y s]))
                                      acc (sort (al/product-shares (select-keys (at s m iso y) al/lrwiod-products))))
                              acc))
                          {} (newly bk (:through state) t))
                   emits (reduce (fn [acc g]
                                   (if-let [rows (seq (get in (str "allocation/mv/share-" g)))]
                                     (assoc acc (str "investment/credit-share-" g) (vec rows))
                                     acc))
                                 emits credit-sections)]
               {:state (assoc state :through t) :emits emits}))}))

(defn modules
  "The allocation channel's two modules over bnm.allocation/index `idx`,
  with `links` {:products {product industry} :stages [[commodity stage]]}
  (bnm.allocation/load-links)."
  [idx links]
  [(allocation-module idx) (investment-module idx links)])
