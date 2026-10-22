(ns bnm.supply
  "The product graph of the material economy (docs/STOCK-FLOW.md, \"Product
  graph\"; epic bnm-1pq): what each product is made from, who uses it, and
  where, per region, from the EXIOBASE hybrid tables 2011 (physical units)
  and the monetary product-by-product tables (value shares), as written by
  scripts/product-graph.py to data/derived/supply/.

  A product made in a region is a node [region product]; its recipe's
  inputs are the products used in that region, each drawn from its
  origins in the shares the multi-regional tables give (trade, domestic
  included). Each recipe row is classified (`classify`): critical
  (Leontief, no substitute) or substitutable (CES).

    downstream  what a product reaches: products made from it, through
                recipes and trade, and the industries making them, with
                the depth (recipe steps) and the dependence share (the
                share of the node's value that traces back to the
                product, summed over every path: the cost-push Leontief
                inverse);
    upstream    what a product ultimately needs: the Leontief inverse in
                the hybrid units (tonnes, TJ, M EUR per unit), with the
                value dependence alongside;
    outage      a cut of a product in a region propagated through the
                recipes as a bnm.hypergraph (critical inputs cap their
                output, the substitutable ones pass on a CES loss, imports
                are not reallocated), solved by hypergraph/propagate.

  One year (2011) of structure: recipes do not change in it, and the
  value shares of 1995 and 2022 are kept to see how they moved."
  (:require [bnm.csv :as csv]
            [bnm.hypergraph :as hg]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ------------------------------------------------------------ the rule

(def critical-share
  "A good (not a service) whose share of the output's value is at least
  this is critical."
  0.05)

(def feedstock
  "A good in tonnes going into a product in tonnes at this many tonnes per
  tonne or more is a feedstock, critical whatever its value share (the
  monetary tables route some feedstocks through other products)."
  0.1)

(def default-sigma
  "The elasticity of substitution of a recipe's substitutable inputs among
  themselves and with value added: near Leontief in the short run
  (Atalay 2017; Boehm, Flaaen & Pandalai-Nayar 2019)."
  0.2)

(defn- kind-of [products {:keys [input output phys share]}]
  (let [{g :group u :unit} (products input)
        ou (:unit (products output))]
    (if (and g (not= g "service")
             (or (>= (or share 0.0) critical-share)
                 (and (= u "tonnes") (= ou "tonnes") (>= (or phys 0.0) feedstock))))
      :critical
      :substitutable)))

;; ------------------------------------------------------------ the graph

(declare graph* critical-hypergraph)

(defn graph
  "The product graph from rows (as the CSVs give them, keys keywordised):
  :products {:code :name :unit :group}, :recipes {:region :output :input
  :phys :share ...}, :trade {:product :origin :dest :share}, :supply
  {:region :industry :product :industry-share}, :use {:region :product
  :user :kind :quantity :share :value-share}. Recipes are classified on
  the way in."
  [{:keys [products recipes trade supply use]}]
  (let [ps (into {} (for [p products] [(:code p) (dissoc p :code)]))
        rs (mapv #(assoc % :kind (kind-of ps %)) recipes)
        g (graph* ps rs trade supply use)]
    (assoc g :critical-h (delay (critical-hypergraph g)))))

(defn- graph* [ps rs trade supply use]
  {:products ps
   :recipes (reduce (fn [m {:keys [region output input] :as r}]
                      (assoc-in m [[region output] input] (dissoc r :region :output :input)))
                    {} rs)
   :inputs-of (reduce (fn [m {:keys [region output input] :as r}]
                        (assoc-in m [[region input] output] r))
                      {} rs)
   :imports (reduce (fn [m {:keys [product origin dest share]}] (assoc-in m [[dest product] origin] share)) {} trade)
   :exports (reduce (fn [m {:keys [product origin dest share]}] (assoc-in m [[origin product] dest] share)) {} trade)
   :makes (reduce (fn [m {:keys [region industry product industry-share]}]
                    (if (and industry-share (pos? industry-share))
                      (assoc-in m [[region industry] product] industry-share)
                      m))
                  {} supply)
   :use (group-by (juxt :region :product) use)})

(defn classify
  "The kind of `input` in `output`'s recipe in `region`: :critical or
  :substitutable; nil where the recipe has no such input."
  [g region output input]
  (get-in g [:recipes [region output] input :kind]))

(defn recipe-rows
  "Every recipe row, with :region :output :input and its :kind."
  [g]
  (for [[[r o] ins] (:recipes g) [i row] ins] (assoc row :region r :output o :input i)))

(defn users
  "The direct uses of `product` in `region`: industries and final demand,
  largest first."
  [g region product]
  (->> (get-in g [:use [region product]])
       (map #(assoc (select-keys % [:region :user :kind :quantity :share]) :value-share (:value-share %)))
       (sort-by (comp - :share))
       vec))

(defn- origins
  "{origin share} of `product` as used in `region`: the trade shares, or
  all domestic where the tables give none."
  [g region product]
  (or (get-in g [:imports [region product]]) {region 1.0}))

(defn- destinations
  "{dest share}: where `origin`'s `product` goes, as a share of each
  destination's use of it; home at 1 where the destination has no trade
  record of it."
  [g origin product]
  (let [ex (get-in g [:exports [origin product]] {})]
    (if (get-in g [:imports [origin product]]) ex (assoc ex origin 1.0))))

;; ------------------------------------------------------- walking the graph

(def ^:private tiny 1e-12)

(defn- series
  "Σ_k≥1 of x_k, x_{k+1} = (step x_k), from x_0: until a term's total is
  below tol of the sum. Throws past `cap` steps."
  [step x0 what]
  (loop [x (step x0) total {} k 0]
    (let [total (merge-with + total x)
          size (reduce + 0.0 (map #(Math/abs (double %)) (vals x)))]
      (cond (< size (* 1e-10 (max 1.0 (reduce + 0.0 (vals total))))) total
            (> k 5000) (throw (ex-info (str "bnm.supply: " what " did not converge") {:steps k :last size}))
            :else (recur (step x) total (inc k))))))

(defn- add [m k v] (if (< (Math/abs (double v)) tiny) m (assoc! m k (+ (get m k 0.0) v))))

(defn- push-down
  "One recipe step downstream of the value vector x ({[region product]
  value}): to the uses in every destination, then to the outputs made
  from them."
  [g x]
  (let [uses (persistent! (reduce-kv (fn [acc [o i] v]
                                       (reduce-kv (fn [acc s t] (add acc [s i] (* v t))) acc (destinations g o i)))
                                     (transient {}) x))]
    (persistent! (reduce-kv (fn [acc [s i] v]
                              (reduce-kv (fn [acc p {:keys [share]}] (add acc [s p] (* v (or share 0.0))))
                                         acc (get-in g [:inputs-of [s i]])))
                            (transient {}) uses))))

(defn- hops
  "{node depth}: breadth-first from `src` over (succ node) -> nodes."
  [succ src]
  (loop [frontier [src] seen {src 0} d 0]
    (if (empty? frontier)
      seen
      (let [nxt (distinct (for [n frontier m (succ n) :when (not (contains? seen m))] m))]
        (recur nxt (into seen (map #(vector % (inc d)) nxt)) (inc d))))))

(defn- down-succ
  "The nodes one recipe step down of [o i]: outputs of i in every
  destination, along edges that pass `keep?` [trade-share row]."
  [g keep?]
  (fn [[o i]]
    (for [[s t] (destinations g o i)
          [p row] (get-in g [:inputs-of [s i]])
          :when (keep? t row)]
      [s p])))

(defn- critical-hypergraph
  "The recipes' critical rows alone as a bnm.hypergraph: [:make region
  product] the minimum of its critical inputs' [:use ...], each the
  trade-share mean of its origins' [:make ...]. Products with no critical
  input are primary (untouched)."
  [g]
  (let [h (reduce-kv (fn [h [s p] ins]
                       (let [crit (into {} (for [[i r] ins :when (= :critical (:kind r))] [[:use s i] 1.0]))]
                         (if (seq crit) (hg/recipe h [:make s p] crit :critical) h)))
                     (hg/hypergraph) (:recipes g))
        used (distinct (for [[[s _] ins] (:recipes g) [i r] ins :when (= :critical (:kind r))] [s i]))]
    (reduce (fn [h [s i]] (hg/recipe h [:use s i] (into {} (for [[o t] (origins g s i) :when (pos? t)] [[:make o i] t]))
                                     :substitutable))
            h used)))

(defn exposure
  "{[region product] share}: what each product would lose were `product`
  made in `region` cut entirely and its critical recipes Leontief (the
  substitutable inputs aside, imports not reallocated): the hard
  dependence however small the value share (plastics on crude). Only
  products that lose anything; to 1e-6."
  [g region product]
  (let [h (force (:critical-h g))
        a (hg/propagate h {[:make region product] 0.0} {:tol 1e-6})]
    (into {} (for [[[t r p] v] a :when (and (= t :make) (< v 1.0))] [[r p] (- 1.0 v)]))))

(defn downstream
  "What `product` made in `region` reaches: {:products [{:region :product
  :depth :dependence :exposure}] :industries [{:region :industry :depth
  :dependence :exposure}]}, largest dependence first. dependence: the
  share of the node's value that traces back to the product over every
  path (value shares times trade shares, summed: the cost-push Leontief
  inverse less the product itself). exposure: see `exposure`. depth:
  recipe steps on the shortest path along edges of weight at least
  :min-edge (default 0.01) or critical, else along any. Kept: dependence
  at least :min-share (default 0.01), or exposure at least :min-exposure
  (default 0.05). An industry is the mix of the products it makes (their
  dependence and exposure weighted by its output shares; its least
  depth)."
  ([g region product] (downstream g region product {}))
  ([g region product {:keys [min-edge min-share min-exposure] :or {min-edge 0.01 min-share 0.01 min-exposure 0.05}}]
   (let [src [region product]
         dep (series #(push-down g %) {src 1.0} "downstream dependence")
         ex (exposure g region product)
         d-strong (hops (down-succ g (fn [t row] (or (= :critical (:kind row)) (>= (* t (or (:share row) 0.0)) min-edge)))) src)
         d-any (delay (hops (down-succ g (fn [t row] (pos? (* t (or (:share row) 0.0))))) src))
         nodes (->> (concat (keys dep) (keys ex))
                    distinct
                    (remove #{src}))
         ps (for [[r p :as n] nodes]
              {:region r :product p :depth (or (d-strong n) (@d-any n)) :dependence (get dep n 0.0)
               :exposure (get ex n 0.0)})
         keep? #(or (>= (:dependence %) min-share) (>= (:exposure %) min-exposure))
         by-node (into {} (map (juxt (juxt :region :product) identity) ps))
         inds (for [[[r j] mix] (:makes g)
                    :let [hit (keep (fn [[p w]] (when-let [x (by-node [r p])] [x w])) mix)
                          tot (reduce + (vals mix))]
                    :when (seq hit)]
                {:region r :industry j
                 :depth (reduce min (map (comp #(or % Long/MAX_VALUE) :depth first) hit))
                 :dependence (/ (reduce + (for [[x w] hit] (* w (:dependence x)))) tot)
                 :exposure (/ (reduce + (for [[x w] hit] (* w (:exposure x)))) tot)})]
     {:products (vec (sort-by (comp - :dependence) (filter keep? ps)))
      :industries (vec (sort-by (comp - :dependence) (filter keep? inds)))})))

(defn- push-up
  "One recipe step upstream of x ({[region product] [quantity value]}):
  each output's inputs as used in its region, then to their origins."
  [g x]
  (let [uses (persistent! (reduce-kv (fn [acc [s p] [q v]]
                                       (reduce-kv (fn [acc i {:keys [phys share]}]
                                                    (let [k [s i] [q0 v0] (get acc k [0.0 0.0])]
                                                      (assoc! acc k [(+ q0 (* q (or phys 0.0))) (+ v0 (* v (or share 0.0)))])))
                                                  acc (get-in g [:recipes [s p]])))
                                     (transient {}) x))]
    (persistent! (reduce-kv (fn [acc [s i] [q v]]
                              (reduce-kv (fn [acc o t]
                                           (let [k [o i] [q0 v0] (get acc k [0.0 0.0])
                                                 q' (* q t) v' (* v t)]
                                             (if (and (< (Math/abs q') tiny) (< (Math/abs v') tiny))
                                               acc
                                               (assoc! acc k [(+ q0 q') (+ v0 v')]))))
                                         acc (origins g s i)))
                            (transient {}) uses))))

(defn upstream
  "What one unit of `product` made in `region` ultimately needs: [{:region
  :product :quantity :unit :dependence :depth}], largest dependence first.
  quantity: the Leontief inverse in the hybrid units (tonnes, TJ or M EUR
  of the input per unit of the product, every round of the supply chain
  summed, through the trade shares); dependence: the same in value shares;
  depth: the fewest recipe steps. The product itself appears where a cycle
  brings it back (beyond the unit). Kept: dependence at least :min-share
  (default 0.001), or a physical quantity of at least :min-quantity
  (default 0.01) where input and product are both in tonnes or TJ."
  ([g region product] (upstream g region product {}))
  ([g region product {:keys [min-share min-quantity] :or {min-share 0.001 min-quantity 0.01}}]
   (let [src [region product]
         step (fn [x] (push-up g x))
         total (loop [x (step {src [1.0 1.0]}) total {} k 0]
                 (let [total (merge-with (fn [[a b] [c d]] [(+ a c) (+ b d)]) total x)
                       size (reduce + 0.0 (map (fn [[q v]] (+ (Math/abs q) (Math/abs v))) (vals x)))
                       scale (reduce + 0.0 (map (fn [[q v]] (+ (Math/abs q) (Math/abs v))) (vals total)))]
                   (cond (< size (* 1e-10 (max 1.0 scale))) total
                         (> k 5000) (throw (ex-info "bnm.supply: upstream did not converge" {:steps k :last size}))
                         :else (recur (step x) total (inc k)))))
         depth (hops (fn [[s p]] (for [[i row] (get-in g [:recipes [s p]])
                                       :when (or (pos? (or (:phys row) 0.0)) (pos? (or (:share row) 0.0)))
                                       o (keys (origins g s i))]
                                   [o i]))
                     src)
         unit #(get-in g [:products % :unit])
         phys? #(#{"tonnes" "TJ"} (unit %))]
     (->> (for [[[r p :as n] [q v]] total
                :when (or (>= v min-share) (and (phys? p) (phys? product) (>= q min-quantity)))]
            {:region r :product p :quantity q :unit (unit p) :dependence v :depth (depth n)})
          (sort-by (comp - :dependence))
          vec))))

;; ---------------------------------------------------------------- outage

(defn hypergraph
  "The product graph as a bnm.hypergraph: [:make region product] is
  critical over its critical inputs and a bundle, the bundle [:bundle
  region product] a CES (elasticity `sigma`) of its substitutable inputs
  and [:factors region] (labour and capital, weighted by one less the
  input shares); a recipe with no critical input is the bundle itself.
  [:use region product] is the share-weighted mean of its origins' [:make
  ...] (imports not reallocated); [:industry region code] the mean of what
  it makes, by output share."
  ([g] (hypergraph g default-sigma))
  ([g sigma]
   (let [w #(max 1e-6 (double (or % 0.0)))
         h (reduce-kv
            (fn [h [s p] ins]
              (let [crit (into {} (for [[i r] ins :when (= :critical (:kind r))] [[:use s i] (w (:share r))]))
                    subs (into {} (for [[i r] ins :when (= :substitutable (:kind r))] [[:use s i] (w (:share r))]))
                    va (max 0.01 (- 1.0 (reduce + 0.0 (map #(or (:share %) 0.0) (vals ins)))))
                    bundle (assoc subs [:factors s] va)]
                (if (seq crit)
                  (-> h
                      (hg/recipe [:bundle s p] bundle :ces sigma)
                      (hg/recipe [:make s p] (assoc crit [:bundle s p] 1.0) :critical))
                  (hg/recipe h [:make s p] bundle :ces sigma))))
            (hg/hypergraph) (:recipes g))
         used (distinct (for [[[s _] ins] (:recipes g) i (keys ins)] [s i]))
         h (reduce (fn [h [s i]] (hg/recipe h [:use s i] (into {} (for [[o t] (origins g s i) :when (pos? t)] [[:make o i] t]))
                                            :substitutable))
                   h used)]
     (reduce-kv (fn [h [r j] mix] (hg/recipe h [:industry r j] (into {} (for [[p x] mix] [[:make r p] x])) :substitutable))
                h (:makes g)))))

(defn outage
  "Cut `cuts` ({[region product] fraction}, 0.5 a cut of half) and
  propagate: {:products {[region product] availability} :industries
  {[region code] availability}}, the nodes below 1 (by more than 1e-9).
  Options: :at :make (default; the product's output in the region) or
  :use (what the region can use of it, all origins); :sigma the
  substitutable inputs' elasticity (default-sigma); :hypergraph one built
  already by `hypergraph` (with that sigma); :tol (default 1e-6) for
  hypergraph/propagate."
  ([g cuts] (outage g cuts {}))
  ([g cuts {:keys [at sigma tol] :or {at :make sigma default-sigma tol 1e-6} :as opts}]
   (let [h (or (:hypergraph opts) (hypergraph g sigma))
         tag (case at :make :make :use :use)
         h (reduce (fn [h [r p]] (if (and (= tag :use) (not (get-in h [:recipes [:use r p]])))
                                   (hg/recipe h [:use r p] {[:make r p] 1.0} :substitutable)
                                   h))
                   h (keys cuts))
         given (into {} (for [[[r p] x] cuts] [[tag r p] (- 1.0 (double x))]))
         a (hg/propagate h given {:tol tol})
         pick (fn [k] (into {} (for [[[t & ks] v] a :when (and (= t k) (< v (- 1.0 1e-9)))] [(vec ks) v])))]
     {:products (pick :make) :industries (pick :industry)})))

;; ---------------------------------------------------------------- loading

(defn- num [s] (when (and s (not (str/blank? s))) (parse-double s)))

(defn- read-rows
  "The rows of a plain CSV (no quoted fields) as maps through `f`."
  [path f]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r)
          ks (mapv keyword (str/split h #","))]
      (mapv #(f (zipmap ks (str/split % #"," -1))) (doall ls)))))

(defn load-graph
  "The product graph from the CSVs in `dir` (data/derived/supply)."
  [dir]
  (let [f #(str (io/file dir %))]
    (graph
     {:products (mapv #(select-keys % [:code :name :unit :group]) (csv/read-maps (f "products.csv") \,))
      :recipes (read-rows (f "recipes.csv")
                          (fn [m] {:region (:region m) :output (:output m) :input (:input m)
                                   :phys (num (:phys m)) :share (num (:share_2011 m))
                                   :share-1995 (num (:share_1995 m)) :share-2022 (num (:share_2022 m))
                                   :physical? (= "1" (:physical m))}))
      :trade (read-rows (f "trade.csv") (fn [m] (-> m (update :quantity num) (update :share num))))
      :supply (read-rows (f "supply.csv")
                         (fn [m] {:region (:region m) :industry (:industry m) :product (:product m)
                                  :industry-share (num (:industry_share m))}))
      :use (read-rows (f "use.csv")
                      (fn [m] {:region (:region m) :product (:product m) :user (:user m) :kind (:kind m)
                               :quantity (num (:quantity m)) :share (num (:share m))
                               :value-share (num (:value_share m))}))})))
