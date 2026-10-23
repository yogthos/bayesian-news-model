(ns bnm.essentiality-run
  "Essentiality R_i(Y, H) (bead bnm-1pq.9, docs/ESSENTIALS.md section 8
  step 8; bnm.essentiality):

    python3 -I scripts/export-restrictions.py
    python3 -I scripts/essentiality-trade.py
    jolt -A:tools -M -m bnm.essentiality-run losses   # the loss tables (slow)
    jolt -A:tools -M -m bnm.essentiality-run          # stocks and report

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
  (:require [bnm.concordance :as conc]
            [bnm.csv :as csv]
            [bnm.essentiality :as es]
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

(defn -main [& [cmd & args]]
  (case cmd
    "losses" (apply losses args)
    nil (println "report: not yet"))
  (shutdown-agents))
