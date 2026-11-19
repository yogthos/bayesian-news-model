;; bnm-5lr: what read at the origin tracks banking's world level? Per
;; December origin year: the world onset rate on the lattice's banking year
;; rows; the rate known by then (origins three or more years back, the
;; frailty's information); and the breadth of credit booms in the JST
;; countries from the row published by then (row yr-1): the share with a
;; credit stretch above 1, and the mean stretch.
;; jolt -A:tools -M docs/studies/calm_level.clj <run>
(require '[bnm.macrohistory :as mh] '[clojure.string :as str] '[clojure.java.io :as io])
(defn- mean [xs] (when (seq xs) (/ (reduce + xs) (double (count xs)))))
(defn- corr [ps] (let [xs (map first ps) ys (map second ps) mx (mean xs) my (mean ys)
                       c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))]
                   (/ c (Math/sqrt (* (reduce + (map #(Math/pow (- % mx) 2) xs)) (reduce + (map #(Math/pow (- % my) 2) ys)))))))
(let [run (or (first *command-line-args*) "t80-base")
      jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
      built (into {} (for [r (mh/build jst-rows)] [[(:iso r) (:year r)] r]))
      jst (sort (set (map :iso jst-rows)))
      rows (with-open [r (io/reader (str "data/runs/" run "/banking_crisis/forecasts.tsv"))]
             (vec (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t")]
                        :when (and (= s "year") (= 3 (count u)) (#{"true" "false"} y))
                        :let [o (parse-long o)] :when (= 11 (mod o 12))]
                    {:yr (quot o 12) :p (parse-double p) :y (= y "true")})))
      by-yr (group-by :yr rows)
      rate (fn [yr] (mean (map #(if (:y %) 1.0 0.0) (by-yr yr))))
      table (vec (for [yr (range 1975 2023) :when (by-yr yr)
                       :let [st (keep #(get-in (built [% (dec yr)]) [:specific :credit]) jst)
                             known (mean (keep rate (range (- yr 5) (- yr 2))))]
                       :when (and (seq st) known)]
                   {:yr yr :rate (rate yr) :known known :breadth (mean (map #(if (> % 1.0) 1.0 0.0) st))
                    :stretch (mean st) :growth (mean (keep #(get-in (built [% (dec yr)]) [:growth-3y :credit]) jst)) :p (mean (map :p (by-yr yr)))}))]
  (println "year  rate   known  breadth stretch growth3  lattice-p")
  (doseq [{:keys [yr rate known breadth stretch growth p]} table]
    (println (format "%d  %.3f  %.3f  %.2f   %+.2f   %+.3f   %.3f" yr rate known breadth stretch (or growth 0.0) p)))
  (doseq [[a b] [[1975 2022] [1990 2022] [2003 2022]]
          :let [t (filter #(<= a (:yr %) b) table)]]
    (println (format "%d-%d corr with the rate: known %+.2f  breadth %+.2f  stretch %+.2f  growth3 %+.2f  lattice-p %+.2f" a b
                     (corr (map (juxt :known :rate) t)) (corr (map (juxt :breadth :rate) t))
                     (corr (map (juxt :stretch :rate) t)) (corr (map (juxt :growth :rate) t)) (corr (map (juxt :p :rate) t))))))

;; A world level from the stocks, refit as known: each origin's world rate
;; forecast by a logit on the world mean stretch (and three-year growth),
;; fit on origins whose outcomes are known by then (three years back),
;; against the known rate carried (the frailty's information) and a
;; constant; Brier on the country rows by fold.
(require '[augur.logit :as lgt])
(let [run (or (first *command-line-args*) "t80-base")
      jst-rows (mh/read-jst "data/raw/slow/jst/jst.csv")
      built (into {} (for [r (mh/build jst-rows)] [[(:iso r) (:year r)] r]))
      jst (sort (set (map :iso jst-rows)))
      rows (with-open [r (io/reader (str "data/runs/" run "/banking_crisis/forecasts.tsv"))]
             (vec (for [l (line-seq r) :let [[s u o p y] (str/split l #"\t")]
                        :when (and (= s "year") (= 3 (count u)) (#{"true" "false"} y))
                        :let [o (parse-long o)] :when (= 11 (mod o 12))]
                    {:yr (quot o 12) :p (parse-double p) :y (= y "true")})))
      world (fn [yr k] (mean (keep #(get-in (built [% (dec yr)]) [k :credit]) jst)))
      by-yr (group-by :yr rows)
      known (fn [yr] (mean (for [r rows :when (<= (- yr 5) (:yr r) (- yr 3))] (if (:y r) 1.0 0.0))))
      lk (fn [yr] (let [k (or (known yr) 0.05)] (Math/log (/ (+ k 0.005) (- 1.005 k)))))
      xs (fn [yr] (double-array [1.0 (or (world yr :specific) 0.0) (* 10 (or (world yr :growth-3y) 0.0)) (lk yr)
                                ;; the calm carried only while credit is not building (Minsky): known x max(0, 1 - stretch)
                                (* (lk yr) (Math/max 0.0 (- 1.0 (or (world yr :specific) 0.0))))]))
      pred (fn [yr cols]
             (let [train (for [r rows :when (<= (:yr r) (- yr 3))] {:x (double-array (map (vec (xs (:yr r))) cols)) :y (:y r) :w 1.0})]
               (lgt/predict (lgt/fit train (count cols) {:prior 1.0}) (double-array (map (vec (xs yr)) cols)))))
      brier (fn [f a b] (mean (for [yr (range a (inc b)) r (by-yr yr)] (Math/pow (- (f yr) (if (:y r) 1.0 0.0)) 2))))
      cache (memoize (fn [yr cols] (pred yr cols)))]
  (println "\nfold        constant   known-rate  world-stretch  stretch+growth  logit-known  stretch+known  gated-calm  lattice")
  (doseq [[a b] [[2003 2006] [2007 2009] [2010 2012] [2013 2019] [2020 2022] [2003 2022]]]
    (println (format "%d-%d   %.5f    %.5f     %.5f        %.5f        %.5f      %.5f       %.5f     %.5f" a b
                     (brier #(cache % [0]) a b) (brier known a b) (brier #(cache % [0 1]) a b) (brier #(cache % [0 1 2]) a b)
                     (brier #(cache % [0 3]) a b) (brier #(cache % [0 1 3]) a b) (brier #(cache % [0 1 4]) a b)
                     (mean (for [yr (range a (inc b)) r (by-yr yr)] (Math/pow (- (:p r) (if (:y r) 1.0 0.0)) 2)))))))
