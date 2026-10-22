(ns bnm.concordance
  "HS6 trade to EXIOBASE products, and the HS6 sub-nodes that stand for new
  essentials inside them (docs/ESSENTIALS.md section 8 step 4).

  The table is data/derived/supply/concordance.csv from
  scripts/concordance.py: rows of edition (HS92, HS12, HS17, HS22), the code
  in that edition, the HS92 code it chains to, the CPA 2.1 code, the
  EXIOBASE product and the weight, the share of the code's value that goes
  to the row, and for 63 HS92 codes the years the row holds in (see
  `index`). A code's weights sum to one in every year, so mapping conserves
  totals: what has no row is returned as unmapped, never dropped.

  Sub-nodes (data/derived/supply/subnodes.csv) name HS6 codes, per edition,
  with their EXIOBASE parent. A parent is split into its sub-nodes by their
  share of the parent's mapped trade value, and a rest, which add back up to
  the parent."
  (:require [bnm.csv :as csv]))

(defn- holds-in? [{:keys [from to]} year]
  (if year
    (and (or (nil? from) (<= from year)) (or (nil? to) (<= year to)))
    (nil? from)))

(defn index
  "Rows (maps with :edition :code :hs6 :cpa :product :weight, and :from and
  :to years where a code changes content) grouped by [edition code]. With
  `year`, only the rows that hold in that year; without, the rows that hold
  from the start of the edition. BACI HS92 fills some codes differently
  from 2017, when it converts HS17 reports (video consoles join 950490),
  and those codes carry two sets of rows."
  ([rows] (index rows nil))
  ([rows year]
   (group-by (juxt :edition :code) (filter #(holds-in? % year) rows))))

(defn- add [m k v] (assoc m k (+ (get m k 0.0) v)))

(defn- spread
  "Values {code v} of `edition` spread over (key-fn row) by weight, with the
  codes that have no rows kept apart."
  [idx edition values key-fn]
  (reduce-kv
   (fn [acc code v]
     (if-let [rs (get idx [edition code])]
       (update acc :mapped #(reduce (fn [m r] (add m (key-fn r) (* (double v) (:weight r)))) % rs))
       (update acc :unmapped add code (double v))))
   {:mapped {} :unmapped {}}
   values))

(defn to-products
  "Values {hs-code v} in `edition` as {:products {exiobase-product v}
  :unmapped {hs-code v}}. The two add up to the input total."
  [idx edition values]
  (let [{:keys [mapped unmapped]} (spread idx edition values :product)]
    {:products mapped :unmapped unmapped}))

(defn to-hs92
  "Values {hs-code v} in `edition` chained to HS92 codes: {:hs92 {hs6 v}
  :unmapped {hs-code v}}, conserving the total."
  [idx edition values]
  (let [{:keys [mapped unmapped]} (spread idx edition values :hs6)]
    {:hs92 mapped :unmapped unmapped}))

(defn- distinct-by [f xs] (vals (into {} (map (juxt f identity)) xs)))

(defn aggregate-subnodes
  "Values {hs-code v} in `edition` summed into {subnode v}. Codes that stand
  for no sub-node are left out; a row listed twice counts once."
  [subnodes edition values]
  (reduce (fn [m {:keys [subnode code]}]
            (if (contains? values code)
              (add m subnode (double (values code)))
              m))
          {}
          (distinct-by (juxt :subnode :code) (filter #(= edition (:edition %)) subnodes))))

(defn subnodes-of
  "The set of sub-nodes whose parent is EXIOBASE `product`."
  [subnodes product]
  (into #{} (comp (filter #(= product (:parent %))) (map :subnode)) subnodes))

(defn split-parent
  "Split `total` of EXIOBASE `product` (an output, a use, a region-year
  balance) into its sub-nodes in `edition` and a rest. Each sub-node takes
  its codes' share of the product's mapped trade value in `values`
  ({hs-code v}); only the part of a code the concordance sends to `product`
  counts. The sub-nodes and the rest add up to `total`; with no trade the
  parent stays whole."
  [idx subnodes edition values product total]
  (let [to-p (fn [code v] (* (double v) (reduce + 0.0 (for [r (get idx [edition code])
                                                            :when (= product (:product r))]
                                                        (:weight r)))))
        parent-trade (reduce-kv (fn [s code v] (+ s (to-p code v))) 0.0 values)
        subs (distinct-by (juxt :subnode :code)
                          (filter #(and (= edition (:edition %)) (= product (:parent %))) subnodes))
        trade (reduce (fn [m {:keys [subnode code]}]
                        (if-let [v (get values code)] (add m subnode (to-p code v)) m))
                      {} subs)]
    (if-not (pos? parent-trade)
      {:subnodes {} :rest (double total)}
      (let [parts (into {} (for [[k v] trade] [k (* (double total) (/ v parent-trade))]))]
        {:subnodes parts :rest (- (double total) (reduce + 0.0 (vals parts)))}))))

;; ---------------------------------------------------------------- files

(defn load-concordance
  "data/derived/supply/concordance.csv as rows for `index`."
  [path]
  (let [year (fn [s] (when (seq s) (parse-long s)))]
    (mapv (fn [r] {:edition (:hs_edition r) :code (:hs_code r) :hs6 (:hs6 r) :cpa (:cpa r)
                   :product (:exiobase_product r) :weight (parse-double (:weight r))
                   :from (year (:from_year r)) :to (year (:to_year r))})
          (csv/read-maps path \,))))

(defn load-subnodes
  "data/derived/supply/subnodes.csv as rows {:subnode :name :edition :code
  :hs6 :parent :kind :note}."
  [path]
  (mapv (fn [r] {:subnode (:subnode r) :name (:name r) :edition (:hs_edition r) :code (:hs_code r)
                 :hs6 (:hs6 r) :parent (:exiobase_parent r) :kind (:kind r) :note (:note r)})
        (csv/read-maps path \,)))

(defn load-products
  "data/derived/supply/products.csv: {:code :name :unit :group}."
  [path]
  (csv/read-maps path \,))
