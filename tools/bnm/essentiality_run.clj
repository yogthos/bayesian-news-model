(ns bnm.essentiality-run
  "Essentiality R_i(Y, H) (bead bnm-1pq.9, docs/ESSENTIALS.md section 8
  step 8; bnm.essentiality):

    python3 -I scripts/export-restrictions.py
    python3 -I scripts/essentiality-trade.py
    jolt -A:tools -M -m bnm.essentiality-run losses   # the loss tables (slow)
    jolt -A:tools -M -m bnm.essentiality-run          # stocks and report
    jolt -A:tools -M -m bnm.essentiality-run fertiliser  # the gas target

  losses writes data/derived/supply/essentiality-loss.csv: per EXIOBASE
  product p with HS92 trade, the value added lost (EUR M, 2011) in the
  world when every region's use of p is cut by f (0.1, 0.5) at the steady
  state and at months 3 and 12 (bnm.supply outage-path with the stocks
  known by 2013: the data stocks; the cited priors are known 2026), and
  the world's use of p, so lambda(f) = loss / (f x use). One cut for
  all regions at once: a cut per region and product (49 x 121 outages
  each way, about a minute apiece here) is out of reach, so every region
  shares the world's loss per unit of shortfall; the regions' R differ by
  what and from whom they import. Propagation tolerance f x 1e-3.

  The default run writes data/derived/stocks/essential.csv (iso, year
  known, variable, value) and prints the report: the ranking tables, the
  registered targets, the held-out precision and the back-test."
  (:require [bnm.cell-profiles :as cprof]
            [bnm.chokepoints :as cp]
            [bnm.concordance :as conc]
            [bnm.csv :as csv]
            [bnm.essentiality :as es]
            [bnm.essentials :as ess]
            [bnm.essentials-check :as ec]
            [bnm.supply :as sup]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "data/derived/supply")
(def loss-path (str dir "/essentiality-loss.csv"))
(def cuts [0.1 0.5])

(defn- read-plain
  "Rows of a plain CSV (no quoted fields) as maps."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r) ks (mapv keyword (str/split h #","))]
      (mapv #(zipmap ks (str/split % #"," -1)) (doall ls)))))

(defn hs92-parents
  "The EXIOBASE products HS92 codes map to."
  []
  (->> (conc/load-concordance (str dir "/concordance.csv"))
       (filter #(= "HS92" (:edition %)))
       (map :product)
       distinct sort vec))

(defn losses [& [only]]
  (let [t0 (System/currentTimeMillis)
        g (sup/load-graph dir)
        h (sup/hypergraph g)
        production (read-plain (str dir "/production.csv"))
        trade (mapv #(update % :quantity parse-double) (read-plain (str dir "/trade.csv")))
        va (es/value-added g production)
        uses (es/use-values trade production)
        stocks (sup/stocks (filter #(<= (:known %) 2013) (sup/stock-rows (str dir "/stocks.csv"))))
        regions (sort (distinct (map first (keys (:recipes g)))))
        parents (if only (str/split only #",") (hs92-parents))
        done (if (.exists (io/file loss-path)) (set (map :product (read-plain loss-path))) #{})
        todo (remove done parents)]
    (println "graph" (- (System/currentTimeMillis) t0) "ms;" (count todo) "of" (count parents) "products to do")
    (when-not (.exists (io/file loss-path))
      (spit loss-path "product,cut,horizon,world_loss,use\n"))
    (let [lock (Object.)
          one (fn [p]
                (let [t (System/currentTimeMillis)
                      use (reduce + 0.0 (for [r regions] (get uses [r p] 0.0)))
                      rows (vec (for [f cuts
                                      :let [cut (into {} (for [r regions :when (pos? (get uses [r p] 0.0))] [[r p] f]))
                                            opts {:hypergraph h :tol (* f 1e-3)}
                                            o (sup/outage g cut (assoc opts :at :use))
                                            path (sup/outage-path g cut stocks (assoc opts :at :use :months 12))
                                            l (fn [ps] (:world (es/outage-loss va ps)))]
                                      [hz ps] [[:steady (:products o)] [:m3 (:products (nth path 2))]
                                               [:m12 (:products (nth path 11))]]]
                                  (format "%s,%s,%s,%.6g,%.6g" p f (name hz) (l ps) use)))]
                  (locking lock
                    (spit loss-path (str (str/join "\n" rows) "\n") :append true)
                    (println p (- (System/currentTimeMillis) t) "ms" (subs (rows 0) 0 (min 60 (count (rows 0))))))))]
      (doall (pmap (fn [ps] (doseq [p ps] (one p)))
                   (let [n 3] (for [i (range n)] (take-nth n (drop i todo)))))))
    (println "losses done" (- (System/currentTimeMillis) t0) "ms")))

;; ---------------------------------------------------------------- fertiliser check

(def fertiliser-regions ["DE" "FR" "IT" "NL" "PL" "GB"])

(defn fertiliser
  "The registered gas target: per region, the loss of N fertiliser when
  half the region's use of each of its inputs (critical, or a value
  share of at least 1%) is cut."
  []
  (let [g (sup/load-graph dir)
        h (sup/hypergraph g)]
    (doseq [r fertiliser-regions]
      (let [ins (for [[i row] (get-in g [:recipes [r "C_NFER"]])
                      :when (or (= :critical (:kind row)) (>= (or (:share row) 0.0) 0.01))]
                  [i row])
            res (vec (pmap (fn [[i row]]
                             (let [o (sup/outage g {[r i] 0.5} {:hypergraph h :at :use :tol 5e-4})]
                               {:input i :kind (:kind row) :share (:share row)
                                :loss (- 1.0 (get-in o [:products [r "C_NFER"]] 1.0))}))
                           ins))
            ranked (sort-by (comp - :loss) res)
            gas (first (filter #(= "C_GASE" (:input %)) ranked))
            gl (double (or (:loss gas) 0.0))
            above (filter #(> (:loss %) (+ gl 1e-6)) ranked)
            tied (filter #(and (not= "C_GASE" (:input %)) (<= (Math/abs (- (:loss %) gl)) 1e-6)) ranked)]
        (println (format "%s C_NFER under a cut of half of each input (%d inputs): %s" r (count res)
                         (str/join " " (for [{:keys [input kind loss]} (take 6 ranked)]
                                         (format "%s%s %.3f" input (if (= :critical kind) "*" "") loss)))))
        (println (format "   gas %s: %.2f of the cut; %s; target (more than any other, >= 0.3): %s"
                         (if gas (str "rank " (inc (count above))) "not an input") (/ gl 0.5)
                         (if (seq tied) (str "tied with " (str/join " " (map :input tied))) "no tie")
                         (if (and gas (empty? above) (empty? tied) (>= (/ gl 0.5) 0.3)) "met" "NOT met")))))))

;; ---------------------------------------------------------------- inputs of the report

(def years (range 2014 2027))
(def restriction-weight
  "A restriction counts as this share of a disruption (a tightening cuts
  exports by about a tenth, a disruption by half)."
  0.2)
(def cut 0.5)
(def strait-loss 0.5)

(defn- num [s] (when (and s (not (str/blank? s))) (parse-double s)))

(defn read-world
  "HS92 rows of hs6-world.csv: {:hs :hs6 :year :known :value :tonnes
  :tonnes-cover :share :hhi}."
  [path]
  (with-open [r (io/reader path)]
    (let [[h & ls] (line-seq r) ks (mapv keyword (str/split h #","))]
      (->> ls
           (filter #(str/starts-with? % "HS92,"))
           (mapv (fn [l] (let [m (zipmap ks (str/split l #"," -1))]
                           {:hs (:hs m) :hs6 (:hs6 m) :year (parse-long (:year m)) :known (parse-long (:known m))
                            :value (num (:value m)) :tonnes (num (:tonnes m)) :tonnes-cover (num (:tonnes_cover m))
                            :share (num (:share m)) :hhi (num (:hhi m))})))))))

(defn load-loss
  "{product {horizon {:cuts [..] :loss [..] :use U}}} from essentiality-loss.csv."
  [path]
  (->> (read-plain path)
       (group-by (juxt :product (comp keyword :horizon)))
       (reduce (fn [m [[p hz] rs]]
                 (let [rs (sort-by (comp parse-double :cut) rs)
                       use (parse-double (:use (first rs)))]
                   (if (and use (pos? use))
                     (assoc-in m [p hz] {:cuts (mapv (comp parse-double :cut) rs)
                                         :loss (mapv (comp parse-double :world_loss) rs) :use use})
                     m)))
               {})))

(defn main-parents
  "{hs6 EXIOBASE product}: each HS92 code's largest-weight product (rows
  that hold from the start of the edition)."
  [conc]
  (->> conc
       (filter #(and (= "HS92" (:edition %)) (nil? (:from %))))
       (group-by :code)
       (into {} (map (fn [[c rs]] [c (:product (apply max-key :weight rs))])))))

(defn import-shares
  "{[region product] share of the region's use imported}, from trade.csv."
  [trade]
  (into {} (for [[[d p] rs] (group-by (juxt :dest :product) trade)
                 :let [tot (reduce + 0.0 (map #(or (:quantity %) 0.0) rs))
                       dom (reduce + 0.0 (for [r rs :when (= d (:origin r))] (or (:quantity r) 0.0)))]
                 :when (pos? tot)]
             [[d p] (- 1.0 (/ dom tot))])))

(defn- kv-list [s f]
  (if (str/blank? s) {}
      (into {} (for [kv (str/split s #";") :let [i (.lastIndexOf ^String kv ":")]]
                 [(subs kv 0 i) (f (subs kv (inc i)))]))))

(defn year-trade
  "{hs6 {:world :exporters {iso3 kUSD} :importers {region kUSD}}} of BACI
  data year `y` (essentiality-trade.csv.gz)."
  [y]
  (let [prefix (str y ",")]
    (with-open [r (io/reader (java.util.zip.GZIPInputStream. (io/input-stream (str dir "/essentiality-trade.csv.gz"))))]
      (into {} (for [l (rest (line-seq r)) :when (str/starts-with? l prefix)
                     :let [[_ _ hs6 w xs ms] (str/split l #"," -1)]]
                 [hs6 {:world (parse-double w) :exporters (kv-list xs parse-double)
                       :importers (kv-list ms parse-double)}])))))

(def region-iso
  "EXIOBASE region -> the iso the stocks use (iso3 for a country region)."
  (into {} (concat (for [r ["WA" "WE" "WF" "WL" "WM"]] [r r]) cprof/exio-iso)))

(defn strait-exposure
  "{K {product-key {region {strait share}}}} from chokepoints.csv for the
  known years `ks`: a country region's own row; a rest-of-world region the
  mean over its members with a seaborne row for the product; WLD the
  world's trade."
  [path ks]
  (let [ks (set ks)
        names (sort-by (comp - count) (keys es/straits))
        acc (java.util.HashMap.)
        cnt (java.util.HashMap.)]
    (with-open [r (io/reader path)]
      (doseq [l (rest (line-seq r))]
        (let [[iso y v x] (str/split l #",")
              k (parse-long y)]
          (when (contains? ks k)
            (let [reg (if (= iso "WLD") "WLD" (cprof/exio-region iso))
                  own? (or (= iso "WLD") (= iso (region-iso reg)))]
              (when (or own? (contains? #{"WA" "WE" "WF" "WL" "WM"} reg))
                (let [body (subs v 11)]
                  (if (str/starts-with? body "seaborne-")
                    (let [key [k (subs body 9) reg]] (.put cnt key (inc (or (.get cnt key) 0))))
                    (when-let [s (some #(when (str/starts-with? body (str % "-")) %) names)]
                      (let [key [k (subs body (inc (count s))) reg s]]
                        (.put acc key (+ (or (.get acc key) 0.0) (parse-double x)))))))))))))
    (reduce (fn [m [[k p reg s] total]]
              (let [n (or (get cnt [k p reg]) 1)]
                (assoc-in m [k p reg s] (/ (double total) n))))
            {} (into {} acc))))

(def stage-key
  "HS92 code -> the bnm.stages stage whose world HHI is its concentration
  (for the materials stages.csv has)."
  {"282520" "lithium-chemical" "283691" "lithium-chemical" "253090" "lithium-mine"
   "260500" "cobalt-mine" "282200" "cobalt-refinery" "810510" "cobalt-refinery"
   "280530" "rare-earths-separation" "284610" "rare-earths-separation" "284690" "rare-earths-separation"
   "250410" "graphite-mine" "250490" "graphite-mine" "811291" "gallium-refinery" "811230" "germanium-refinery"
   "261100" "tungsten-mine" "810110" "tungsten-mine" "260200" "manganese-mine" "261000" "chromium-mine"
   "260400" "nickel-mine" "750110" "nickel-refinery" "750210" "nickel-refinery" "283324" "nickel-refinery"
   "260300" "copper-mine" "740311" "copper-refinery" "260600" "aluminium-mine" "760110" "aluminium-smelter"
   "261710" "antimony-mine" "811000" "antimony-mine" "260900" "tin-mine" "800110" "tin-refinery"
   "261390" "molybdenum-mine" "810411" "magnesium-smelter" "711011" "platinum-mine"})

(defn stage-hhi
  "{[stage K] hhi}: the latest WLD stages/<stage>-hhi known by K."
  [path ks]
  (let [rows (for [r (csv/read-maps path \,)
                   :when (and (= "WLD" (:iso r)) (str/ends-with? (:variable r) "-hhi")
                              (not (str/includes? (:variable r) "plant")) (not (str/includes? (:variable r) "quota")))]
               [(subs (:variable r) 7 (- (count (:variable r)) 4)) (parse-long (:year r)) (parse-double (:value r))])
        by (group-by first rows)]
    (into {} (for [[st rs] by k ks
                   :let [v (last (sort-by second (filter #(<= (second %) k) rs)))]
                   :when v]
               [[st k] (nth v 2)]))))

(defn users-of
  "{product #{outputs}}: outputs whose recipes take the product at a
  median value share of at least `floor` over the regions."
  [floor]
  (with-open [r (io/reader (str dir "/recipes.csv"))]
    (let [[h & ls] (line-seq r) ks (str/split h #",")
          io (.indexOf ^java.util.List ks "output") ii (.indexOf ^java.util.List ks "input")
          is (.indexOf ^java.util.List ks "share_2011")
          shares (reduce (fn [m l] (let [f (str/split l #"," -1) s (num (nth f is))]
                                     (if (and s (pos? s)) (update m [(nth f ii) (nth f io)] (fnil conj []) s) m)))
                         {} ls)]
      (reduce-kv (fn [m [i o] ss]
                   (let [v (vec (sort ss)) med (v (quot (count v) 2))]
                     ;; regions without the row count as zero
                     (if (and (not= i o) (>= (if (>= (count v) 25) med 0.0) floor)) (update m i (fnil conj #{}) o) m)))
                 {} shares))))

;; ---------------------------------------------------------------- risk per year

(defn- spearman [xs ys]
  (let [rk (fn [v] (let [ix (sort-by #(nth v %) (range (count v)))]
                     (reduce (fn [a [r i]] (assoc a i (double r))) (vec (repeat (count v) 0.0)) (map-indexed vector ix))))
        a (rk (vec xs)) b (rk (vec ys)) n (count a)
        ma (/ (reduce + a) n) mb (/ (reduce + b) n)
        cov (reduce + (map #(* (- %1 ma) (- %2 mb)) a b))
        va (reduce + (map #(let [d (- % ma)] (* d d)) a))
        vb (reduce + (map #(let [d (- % mb)] (* d d)) b))]
    (/ cov (Math/sqrt (* va vb)))))

(defn risk-year
  "Every ranked product's risk at known year K: [{:hs6 :parent :risk
  :risk-3m :risk-12m :regions :p :top-supplier :strait-share ...}]."
  [{:keys [k ranks trade parents losses shares exposure rates restr events region-of keyfn]} & [variant]]
  (let [ev (events k)
        rr (restr k)
        q (into {} (for [[s r] (rates k)] [s (:mean r)]))
        w (if (= variant :no-restriction) 0.0 restriction-weight)
        x (if (= variant :cut-0.1) 0.1 cut)
        imports-by-parent (reduce-kv (fn [m hs6 {:keys [importers]}]
                                       (if-let [p (parents hs6)]
                                         (reduce-kv (fn [m r v] (update-in m [p r] (fnil + 0.0) v)) m importers)
                                         m))
                                     {} trade)
        horizons (if variant [:steady] [:steady :m3 :m12])]
    (vec
     (for [{:keys [hs6] :as e} ranks
           :let [p (parents hs6) tr (trade hs6) tbls (get losses p)]
           :when (and p tr tbls (pos? (:world tr)))]
       (let [use (into {} (for [[r v] (imports-by-parent p)]
                            [r (/ v (max 0.01 (get shares [r p] 1.0)))]))
             pc (into {} (for [c (keys (:exporters tr))]
                           [c (es/union [(get ev c (:default ev)) (* w (es/restriction-p rr c hs6))])]))
             ex (get-in exposure [k (keyfn hs6)])
             st (when-not (= variant :no-strait) (dissoc ex "WLD"))
             res (into {} (for [hz horizons
                                :let [tbl (tbls hz)
                                      lam (fn [_ f] (/ (es/interp-loss tbl f) (* (max f 1e-12) (:use tbl))))]]
                            [hz (es/product-risk {:world (:world tr) :exporters (:exporters tr) :importers (:importers tr)
                                                  :region-of region-of :p pc :use use :lambda lam :x x
                                                  :e (es/spare hz) :straits st :q q :d strait-loss
                                                  :strait-world (get ex "WLD")})]))
             s (res :steady)
             top (when (seq (:suppliers s)) (apply max-key (comp :risk val) (:suppliers s)))
             wx (reduce + 0.0 (vals (:exporters tr)))]
         (assoc e :parent p :risk (:world s) :risk-3m (:world (res :m3)) :risk-12m (:world (res :m12))
                :regions (:regions s) :regions-3m (:regions (res :m3)) :regions-12m (:regions (res :m12))
                :value (:world tr)
                :p (if (pos? wx) (/ (reduce + 0.0 (for [[c v] (:exporters tr)] (* v (pc c)))) wx) 0.0)
                :top-supplier (key top) :top-share (if (pos? (:world s)) (/ (:risk (val top)) (:world s)) 0.0)
                :strait-share (if (pos? (:world s)) (/ (reduce + 0.0 (vals (:straits s))) (:world s)) 0.0)))))))

;; ---------------------------------------------------------------- report

(defn- f [x d] (if (some? x) (format (str "%." d "f") (double x)) "-"))
(defn- bn [kusd] (/ (double kusd) 1e6))

(defn- print-top [k rows n names]
  (println (format "K = %d (data %d): %d products ranked; top %d by world R (steady, USD bn of value added a year)" k (- k 2) (count rows) n))
  (println "   rank hs6     R      R-3m   R-12m  trade  P     top supplier (its share of R) strait  rise-pct parent  name")
  (doseq [r (take n rows)]
    (println (format "   %4d %s %6.2f %6.2f %6.2f %6.1f %.3f %s (%.2f)          %.2f   %s %-7s %s"
                     (:rank r) (:hs6 r) (bn (:risk r)) (bn (:risk-3m r)) (bn (:risk-12m r)) (bn (:value r)) (:p r)
                     (:top-supplier r) (:top-share r) (:strait-share r) (f (:pct-rise r) 3) (:parent r) (get names (:hs6 r) "")))))

(def targets
  [["lithium" ["282520" "283691"]] ["cobalt" ["260500" "282200" "810510"]]
   ["rare earths" ["280530" "284610" "284690"]]])

(defn- precision-report [ranked-by-k k labels label]
  (let [rows (ranked-by-k k)
        n (count rows)
        lab? #(contains? labels (:hs6 %))
        m (count (filter lab? rows))
        base (/ (double m) n)
        rising (filter #(<= (:pct-rise %) 0.10) rows)
        top-r (take 20 (sort-by (comp - :risk) rising))
        top-rise (take 20 (sort-by :rank-rise rows))
        top-all (take 20 (sort-by (comp - :risk) rows))
        hits (fn [xs] (count (filter lab? xs)))
        line (fn [what xs]
               (let [h (hits xs)]
                 (println (format "     %-26s %2d/20 precision %.2f (random %.3f, lift %.1f, p %.3g)%s" what h (/ h 20.0) base
                                  (/ (/ h 20.0) base) (ess/hypergeometric-tail n m 20 h)
                                  (str "  " (str/join " " (map :hs6 (filter lab? xs))))))))]
    (println (format "   %s, K %d: %d ranked, %d listed among them, %d rising" label k n m (count rising)))
    (line "R top 20 of the rising" top-r)
    (line "rise top 20" top-rise)
    (line "R top 20 of all" top-all)
    (let [hr (hits top-r) hz (hits top-rise)]
      {:hits hr :rise hz :p (ess/hypergeometric-tail n m 20 hr)})))

(defn- uv-spikes
  "{[hs6 data-year] largest log unit-value rise over the next three data
  years}, from world rows with tonnes cover >= 0.5."
  [world]
  (let [uv (into {} (for [r world :let [{:keys [value tonnes tonnes-cover]} r]
                          :when (and value tonnes (pos? tonnes) (pos? value) (>= (or tonnes-cover 1.0) 0.5))]
                      [[(:hs6 r) (:year r)] (Math/log (/ (* value (or tonnes-cover 1.0)) tonnes))]))]
    (into {} (for [[[c y] u] uv
                   :let [later (keep #(uv [c %]) (range (inc y) (+ y 4)))]
                   :when (= 3 (count later))]
               [[c y] (- (apply max later) u)]))))

(defn- backtest [rows-by-k restr-rows spikes]
  (let [covered (set (map :hs6 restr-rows))
        onset? (let [s (set (for [r restr-rows :when (pos? (:onsets r))] [(:hs6 r) (:year r)]))]
                 (fn [c k] (boolean (some #(s [c %]) (range (inc k) (+ k 4))))))
        pool (vec (for [k (range 2014 2022) r (rows-by-k k)] (assoc r :k k)))
        groups (fn [rows]
                 (let [by (group-by :k rows)]
                   (reduce (fn [acc [_ rs]]
                             (let [v (vec (sort-by (comp - :risk) rs)) n (count v)]
                               (-> acc (update :top into (subvec v 0 (quot n 10)))
                                   (update :bottom into (subvec v (quot n 2))))))
                           {:top [] :bottom []} by)))
        stat (fn [rows outcome]
               (let [{:keys [top bottom]} (groups rows)
                     mean (fn [xs] (let [v (keep outcome xs)] (when (seq v) (/ (reduce + 0.0 v) (count v)))))
                     a (mean top) b (mean bottom)]
                 [a b (when (and a b) (- a b))]))
        restr-out (fn [r] (when (covered (:hs6 r)) (if (onset? (:hs6 r) (:k r)) 1.0 0.0)))
        spike-out (fn [r] (spikes [(:hs6 r) (- (:k r) 2)]))
        rng (java.util.Random. 20261023)
        placebo (fn [outcome obs]
                  (let [ds (vec (for [_ (range 999)]
                                  (let [sh (vec (mapcat (fn [[_ rs]] (es/shuffle-within rs rng)) (group-by :k pool)))]
                                    (nth (stat sh outcome) 2))))
                        ge (count (filter #(and % (>= % obs)) ds))
                        sd (let [v (filter some? ds) mu (/ (reduce + v) (count v))]
                             (Math/sqrt (/ (reduce + (map #(let [d (- % mu)] (* d d)) v)) (count v))))]
                    [(/ (inc ge) 1000.0) (/ (reduce + (filter some? ds)) (count (filter some? ds))) sd]))]
    (doseq [[label outcome what] [["OECD onsets K+1..K+3 (codes covered)" restr-out "share with an onset"]
                                  ["unit-value spike Y+1..Y+3" spike-out "mean max log rise"]]]
      (let [[a b d] (stat pool outcome)
            [p mu sd] (placebo outcome d)]
        (println (format "   %s: %s top decile %.3f, bottom half %.3f, difference %.3f; placebo mean %.3f sd %.3f, p %.3f"
                         label what a b d mu sd p))))
    (let [rs (filter #(covered (:hs6 %)) pool)]
      (println (format "   (product-years 2014-2021: %d, of which %d on OECD-covered codes)" (count pool) (count rs))))))

(defn report []
  (let [t0 (System/currentTimeMillis)
        tick (fn [what] (println (format "  [%s %.0fs]" what (/ (- (System/currentTimeMillis) t0) 1000.0))))
        world (read-world (str dir "/hs6-world.csv"))
        _ (tick "world rows")
        ranks (into {} (for [k years] [k (vec (filter :rank (ess/rise world (- k 2))))]))
        _ (tick "rise ranks")
        conc (conc/load-concordance (str dir "/concordance.csv"))
        parents (main-parents conc)
        losses (load-loss loss-path)
        trade-rows (mapv #(update % :quantity parse-double) (read-plain (str dir "/trade.csv")))
        shares (import-shares trade-rows)
        subnodes (cp/read-subnodes (str dir "/subnodes.csv"))
        keyfn (fn [hs6] (or (subnodes hs6) (cp/energy-products hs6) (str "hs" (subs hs6 0 2))))
        exposure (strait-exposure "data/derived/stocks/chokepoints.csv" years)
        _ (tick "strait exposure")
        closures (es/portwatch-closures "data/raw/material/imf-portwatch/portwatch_disruptions_database.csv")
        restr-rows (es/load-restrictions (str dir "/export-restrictions.csv"))
        n-codes (count (distinct (map :hs6 restr-rows)))
        fc (es/load-forecasts "data/runs/t44-world-norel" es/event-kinds)
        _ (tick "forecasts")
        ctx {:parents parents :losses losses :shares shares :exposure exposure :keyfn keyfn
             :rates (memoize (fn [k] (es/strait-rates closures k {})))
             :restr (memoize (fn [k] (es/restriction-rates restr-rows k {:codes n-codes})))
             :events (memoize (fn [k] (es/event-hazard fc k)))
             :region-of (memoize cprof/exio-region)}
        names (merge subnodes cp/energy-products)
        rows-by-k (into {} (for [k years]
                             (let [trade (year-trade (- k 2))
                                   rs (risk-year (assoc ctx :k k :ranks ranks :trade trade
                                                        :ranks (map #(assoc % :pct-rise (:pct %) :rank-rise (:rank %)) (ranks k))))]
                               (tick (str "risk " k " (" (count rs) " products)"))
                               [k (es/rank-risk rs)])))]
    (println)
    (println "closures counted (PortWatch):" (str/join ", " (for [c closures] (str (:strait c) " " (:year c) " " (:name c)))))
    (println "strait rates at K 2026:" (str/join " " (for [[s r] (sort-by key ((:rates ctx) 2026)) :when (> (:mean r) 0.0151)] (format "%s %.3f±%.3f" s (:mean r) (:sd r)))))
    (println "restriction rates at K 2022: tighten" (f (:tighten ((:restr ctx) 2022)) 3) "pooled entry" (f (:pooled ((:restr ctx) 2022)) 4))
    (println "event hazard at K 2022:" (str/join " " (for [c ["CHN" "COD" "CHL" "AUS" "RUS" "ZAF" "IDN" "USA" :default]] (str (name c) " " (f (get ((:events ctx) 2022) c) 3)))))
    (println "missing loss tables for parents:" (str/join " " (sort (remove losses (distinct (vals parents))))))
    (println)
    (doseq [k [2016 2020 2023 2026]] (print-top k (rows-by-k k) 20 names) (println))
    {:rows-by-k rows-by-k :ctx ctx :world world :restr-rows restr-rows :names names :ranks ranks :subnodes subnodes
     :tick tick}))


;; ---------------------------------------------------------------- flags, targets, stocks

(def tech-material
  "HS92 code -> its bnm.tech-demand implied-demand growth (5-year log
  change, WLD): the technology link of signal 2."
  (into {} (for [[v codes] {"lithium-li-ion-growth5" ["282520" "283691" "253090"]
                            "cobalt-li-ion-growth5" ["260500" "282200" "810510"]
                            "nickel-li-ion-growth5" ["260400" "283324" "750110" "750120" "750210"]
                            "graphite-li-ion-growth5" ["250410" "250490" "380110"]
                            "copper-growth5" ["260300" "740200" "740311" "740312" "740319"]
                            "rare-earths-growth5" ["280530" "284610" "284690" "850511"]
                            "silicon-growth5" ["280461" "280469"]
                            "silver-growth5" ["710691" "710692"]}
                 c codes]
             [c (str "tech-demand/" v)])))

(defn tech-growth
  "{[variable K] growth}: the latest WLD value known by K."
  [path ks]
  (let [rows (for [r (csv/read-maps path \,)
                   :when (and (= "WLD" (:iso r)) (str/ends-with? (:variable r) "growth5"))]
               [(:variable r) (parse-long (:year r)) (parse-double (:value r))])]
    (into {} (for [[v rs] (group-by first rows) k ks
                   :let [x (last (sort-by second (filter #(<= (second %) k) rs)))]
                   :when x]
               [[v k] (nth x 2)]))))

(defn with-flags
  "rows-by-k with the four signals and :flag on each row. Linked: a
  recipe user of the product's parent (median value share >= 0.005) whose
  world trade grew faster than the world's over five years or is in the
  top quarter by value, or a technology the product feeds whose implied
  demand grew (`tech-material`)."
  [rows-by-k world parents users st-hhi tg]
  (let [pv (reduce (fn [m {:keys [hs6 year value]}]
                     (if-let [p (and value (parents hs6))] (update m [p year] (fnil + 0.0) value) m))
                   {} world)
        tot (reduce (fn [m {:keys [year value]}] (if value (update m year (fnil + 0.0) value) m)) {} world)
        q75 (memoize (fn [y] (let [v (vec (sort (for [[[_ yy] x] pv :when (= yy y)] x)))]
                               (if (seq v) (v (int (* 0.75 (dec (count v))))) Double/MAX_VALUE))))
        g5 (fn [a b] (when (and a b (pos? a) (pos? b)) (Math/log (/ a b))))
        linked? (fn [p y] (boolean (some (fn [o] (let [v (pv [o y]) gw (g5 (tot y) (tot (- y 5))) go (g5 v (pv [o (- y 5)]))]
                                                   (or (and v (>= v (q75 y))) (and go gw (> go gw)))))
                                         (users p))))
        hhi (into {} (for [r world :when (:hhi r)] [[(:hs6 r) (:year r)] (:hhi r)]))
        conc? (fn [c y k] (let [h (hhi [c y]) h5 (hhi [c (- y 5)]) sh (some-> (stage-key c) (vector k) st-hhi)]
                            (boolean (or (and h (>= h 0.25)) (and h h5 (>= (- h h5) 0.05)) (and sh (>= sh 0.25))))))
        idx (into {} (for [[k rs] rows-by-k] [k (into {} (map (juxt :hs6 :risk)) rs)]))
        past (fn [c k] (keep #(get-in idx [% c]) (range (- k 3) k)))]
    (into {} (for [[k rs] rows-by-k]
               [k (mapv (fn [r] (let [y (- k 2)
                                      tech (some-> (tech-material (:hs6 r)) (vector k) tg)
                                      m (assoc r :rising (<= (:pct-rise r) 0.10)
                                               :linked (or (linked? (:parent r) y) (boolean (and tech (pos? tech))))
                                               :concentrated (conc? (:hs6 r) y k)
                                               :risk-up (boolean (es/risk-up? (:risk r) (past (:hs6 r) k))))]
                                  (assoc m :flag (es/flag? m))))
                        rs)]))))

(defn write-stocks
  "data/derived/stocks/essential.csv: WLD rows for every ranked product;
  region rows for the sub-nodes, the energy codes and each year's top 50."
  [rows-by-k named path]
  (with-open [w (io/writer path)]
    (.write w "iso,year,variable,value\n")
    (doseq [[k rs] (sort-by key rows-by-k)
            :let [top (set (map :hs6 (take 50 rs)))]
            r rs
            :let [v (fn [n] (str "essential/" (:hs6 r) "-" n))
                  line (fn [iso n x] (.write w (str iso "," k "," (v n) "," (format "%.6g" (double x)) "\n")))]]
      (line "WLD" "risk" (/ (:risk r) 1e3))
      (line "WLD" "risk-3m" (/ (:risk-3m r) 1e3))
      (line "WLD" "risk-12m" (/ (:risk-12m r) 1e3))
      (line "WLD" "risk-rank" (:rank r))
      (line "WLD" "risk-pct" (:pct r))
      (line "WLD" "p" (:p r))
      (doseq [s (conj es/signals :flag)] (line "WLD" (name s) (if (get r s) 1 0)))
      (when (or (top (:hs6 r)) (named (:hs6 r)))
        (doseq [[n regs] [["risk" (:regions r)] ["risk-3m" (:regions-3m r)] ["risk-12m" (:regions-12m r)]]
                [reg x] regs :when (pos? x)]
          (line (region-iso reg) n (/ x 1e3)))))))

(defn finish [{:keys [rows-by-k ctx world restr-rows names ranks tick]}]
  (let [users (users-of 0.005)
        st-hhi (stage-hhi "data/derived/stocks/stages.csv" (range 1995 2027))
        tg (tech-growth "data/derived/stocks/tech-demand.csv" (range 2014 2027))
        rows-by-k (with-flags rows-by-k world (:parents ctx) users st-hhi tg)
        _ (tick "flags")
        sets (ec/label-sets (conc/load-subnodes (str dir "/subnodes.csv")))]
    (write-stocks rows-by-k (set (keys names)) "data/derived/stocks/essential.csv")
    (tick "stocks written")
    (println)
    (println "Rising essentials flagged (all four signals), by K:")
    (doseq [[k rs] (sort-by key rows-by-k) :when (>= k 2017)]
      (let [fl (filter :flag rs)]
        (println (format "   %d: %3d flagged of %d (rising %d, linked %d, concentrated %d, risk up %d): %s" k (count fl) (count rs)
                         (count (filter :rising rs)) (count (filter :linked rs)) (count (filter :concentrated rs)) (count (filter :risk-up rs))
                         (str/join " " (for [r (take 15 fl)] (str (:hs6 r) (when-let [n (names (:hs6 r))] (str "(" n ")")) "@" (:rank r))))))))
    (println)
    (println "Registered: lithium, cobalt and rare earths in the top 10% of R in some K of 2020-2022")
    (doseq [[mat codes] targets]
      (let [best (for [k [2020 2021 2022]
                       :let [rs (rows-by-k k) n (count rs)
                             hit (keep (fn [c] (first (filter #(= c (:hs6 %)) rs))) codes)
                             b (when (seq hit) (apply min-key :rank hit))]]
                   [k b n])
            ok (some (fn [[_ b _]] (and b (<= (:pct b) 0.10))) best)]
        (println (format "   %-12s %s: %s" mat (if ok "met" "NOT met")
                         (str/join "; " (for [[k b n] best] (if b (format "%d %s rank %d/%d (%.3f)" k (:hs6 b) (:rank b) n (:pct b)) (str k " none"))))))
        (println (format "                all codes: %s"
                         (str/join " " (for [c codes k [2020 2022] :let [r (first (filter #(= c (:hs6 %)) (rows-by-k k)))]]
                                         (str c "@" k ":" (if r (:rank r) "-"))))))))
    (println)
    (println "Registered: precision of the 20 highest-R rising products (broad mapping primary)")
    (let [res (into {} (for [[lst k] [[:eu 2023] [:usgs 2022]] mapping [:broad :strict]]
                         [[lst mapping] (precision-report rows-by-k k (sets [lst mapping]) (str (name lst) " " (name mapping)))]))]
      (doseq [lst [:eu :usgs] :let [{:keys [hits rise p]} (res [lst :broad])]]
        (println (format "   %s broad: R %d vs rise %d hits, p %.3g: beats random %s, beats rise %s" (name lst) hits rise p
                         (if (<= p 0.05) "met" "NOT met") (if (> hits rise) "met" "NOT met")))))
    (println)
    (println "Descriptive: the same precision at other K (broad, R top 20 of the rising / rise top 20)")
    (doseq [lst [:eu :usgs]]
      (println (format "   %-4s %s" (name lst)
                       (str/join " " (for [k (range 2016 2027)
                                           :let [rs (rows-by-k k) lab? #(contains? (sets [lst :broad]) (:hs6 %))
                                                 a (count (filter lab? (take 20 (sort-by (comp - :risk) (filter :rising rs)))))
                                                 b (count (filter lab? (take 20 (sort-by :rank-rise rs))))]]
                                       (str k ":" a "/" b))))))
    (println)
    (println "Sensitivity at K 2022 and 2023 (Spearman with the registered R; precision as above, EU at 2023)")
    (doseq [k [2022 2023] variant [:cut-0.1 :no-restriction :no-strait]]
      (let [trade (year-trade (- k 2))
            alt (es/rank-risk (risk-year (assoc ctx :k k :trade trade
                                                :ranks (map #(assoc % :pct-rise (:pct %) :rank-rise (:rank %)) (ranks k)))
                                         variant))
            base (into {} (map (juxt :hs6 :risk)) (rows-by-k k))
            common (filter #(base (:hs6 %)) alt)
            lab? #(contains? (sets [:eu :broad]) (:hs6 %))
            hits (count (filter lab? (take 20 (sort-by (comp - :risk) (filter #(<= (:pct-rise %) 0.10) alt)))))]
        (println (format "   %d %-15s Spearman %.4f; EU broad hits in the rising top 20: %d" k (name variant)
                         (spearman (map :risk common) (map (comp base :hs6) common)) hits))))
    (println)
    (println "Back-test (descriptive): top decile of R against the bottom half, K 2014-2021")
    (backtest rows-by-k restr-rows (uv-spikes world))
    (println)
    (println "Concentration (signal 3) for rare earths and gallium, from stages.csv (no R before 2014)")
    (println (format "   rare-earth mine HHI by K: %s"
                     (str/join " " (for [k (range 1998 2014)] (str k ":" (f (st-hhi ["rare-earths-mine" k]) 2))))))
    (println (format "   gallium refinery HHI by K: %s; 811291 rise pct by K: %s"
                     (str/join " " (for [k (range 2008 2027) :let [h (st-hhi ["gallium-refinery" k])] :when h] (str k ":" (f h 2))))
                     (str/join " " (for [k (range 2010 2027) :let [e (first (filter #(= "811291" (:hs6 %)) (ranks k)))] :when e]
                                     (str k ":" (f (:pct e) 2))))))
    (tick "done")))

(defn -main [& [cmd & args]]
  (case cmd
    "losses" (apply losses args)
    "fertiliser" (fertiliser)
    nil (finish (report)))
  (shutdown-agents))

