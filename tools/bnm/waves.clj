(ns bnm.waves
  "Global crisis waves as a self-exciting process (docs/INVARIANTS.md).

  Over the Reinhart-Rogoff chronology (70 countries, 1800-2016), each
  country-year's chance of a banking crisis starting within two years is a
  logistic function of kernels over past crisis starts: the world's (share
  of countries), at 1, 3, 10 and 30 years, so the hazard can learn a wave
  spreading, the memory that protects after it and the memory fading; the
  country's own; and the world's currency, sovereign default and inflation
  crises (Reinhart & Rogoff's twin and triple crises). No shape is imposed:
  the signs and the mix of timescales are fitted, refit each year on what
  is known by then.

  Reports how well the world's mean probability times the waves (three or
  more banking crises starting within two years), by era, and writes
  data/runs/<name>/world-intensity.tsv (year, probability) for the
  Macrohistory hazards.

    jolt -A:tools -M -m bnm.waves [--name waves-1]"
  (:require [augur.logit :as lgt]
            [augur.score :as sc]
            [bnm.invariants :as iv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def kinds [:banking :currency :default :inflation])

(defn read-onsets [path]
  (with-open [r (io/reader path)]
    (let [[header & lines] (line-seq r)
          cols (mapv keyword (str/split header #","))]
      (vec (for [line lines]
             (let [m (zipmap cols (str/split line #"," -1))]
               (into {:iso (:iso m) :year (parse-long (:year m))}
                     (for [k (concat kinds (map #(keyword (str (name %) "_known")) kinds))]
                       [k (= "1" (get m k))]))))))))

(def taus [1 3 10 30])

(defn build
  "Country-years with :x inputs (as a map) and :y (banking start within two
  years), and :wave per year."
  [onsets]
  (let [by-c (group-by :iso onsets)
        years (sort (distinct (map :year onsets)))
        n-known (frequencies (map :year (filter :banking_known onsets)))
        starts (fn [k] (into {} (for [[yr rs] (group-by :year onsets)
                                      :let [n (count (filter k rs)) d (get n-known yr 0)]
                                      :when (and (pos? n) (pos? d))]
                                  [yr (/ n (double d))])))
        world (into {} (for [k kinds] [k (starts k)]))
        own (into {} (for [[c rs] by-c] [c (into {} (for [r rs :when (:banking r)] [(:year r) 1.0]))]))
        count-in (fn [yr] (count (filter #(and (:banking %) (#{(inc yr) (+ yr 2)} (:year %))) onsets)))]
    {:wave (into {} (for [yr years] [yr (>= (count-in yr) 3)]))
     :rows (vec (for [[c rs] by-c
                      :let [m (into {} (map (juxt :year identity) rs))]
                      r rs
                      :when (:banking_known r)
                      :let [yr (:year r)
                            ongoing (some #(:banking (m %)) (range (- yr 2) (inc yr)))
                            ahead (when (and (:banking_known (m (inc yr))) (:banking_known (m (+ yr 2))))
                                    (boolean (some #(:banking (m %)) [(inc yr) (+ yr 2)])))]
                      :when (and (some? ahead) (not ongoing))]
                  {:iso c :year yr :y ahead
                   :x (merge (into {} (for [tau taus]
                                        [(keyword (str "world-banking-k" tau)) (iv/kernel (world :banking) yr (double tau) {:through yr})]))
                             (into {} (for [k [:currency :default :inflation] tau [3 10]]
                                        [(keyword (str "world-" (name k) "-k" tau)) (iv/kernel (world k) yr (double tau) {:through yr})]))
                             (into {} (for [tau [3 10 30]]
                                        [(keyword (str "own-k" tau)) (iv/kernel (own c) yr (double tau) {:through yr})])))}))}))

(defn- vectorize [r names] (double-array (cons 1.0 (map #(get-in r [:x %] 0.0) names))))

(defn prequential
  "Per year from `from`, fitted on rows whose outcome is known (year + 2 <
  the year) and from `train-from`: {:preds [[year p y]] :world {year mean p}}."
  [rows names {:keys [from train-from prior] :or {prior 10.0}}]
  (let [k (inc (count names))
        last-yr (apply max (map :year rows))
        by-year (group-by :year rows)]
    (loop [yr from beta nil preds [] world {}]
      (if (> yr last-yr)
        {:preds preds :world world}
        (let [train (for [r rows :when (and (>= (:year r) train-from) (< (+ (:year r) 2) yr))]
                      {:x (vectorize r names) :y (:y r) :w 1.0})
              beta (if (>= (count (filter :y train)) 10) (lgt/fit train k {:prior prior :start beta}) beta)
              ps (when beta (for [r (by-year yr)] [yr (lgt/predict beta (vectorize r names)) (:y r)]))]
          (recur (inc yr) beta (into preds ps)
                 (if (seq ps) (assoc world yr (/ (reduce + (map second ps)) (count ps))) world)))))))

(defn run [{:keys [name]}]
  (let [name (or name "waves")
        {:keys [rows wave]} (build (read-onsets "data/raw/slow/rr/onsets.csv"))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        fmt (fn [x] (if x (format "%.2f" (double x)) "  - "))
        all-names (vec (sort (keys (:x (first rows)))))
        world-banking (filterv #(str/starts-with? (clojure.core/name %) "world-banking") all-names)
        eras [["1860-1913" 1860 1913] ["1919-1949" 1919 1949] ["1950-1979" 1950 1979] ["1980-2014" 1980 2014] ["1950-2014" 1950 2014]]
        timing (fn [world lo hi]
                 (let [ys (filter #(and (<= lo % hi) (contains? world %) (contains? wave %)) (keys world))]
                   (when (and (some wave ys) (some (complement wave) ys))
                     (sc/auc (map world ys) (map wave ys)))))
        within (fn [preds lo hi] (sc/auc-within (filter #(<= lo (first %) hi) preds)))]
    (say "Global banking crisis waves, Reinhart-Rogoff, 70 countries. Real time: refit yearly on")
    (say (format "known outcomes from 1830. %d country-years, %d with a start within two years." (count rows) (count (filter :y rows))))
    (say "Timing: AUC of the world's mean probability against three or more starts within two years.")
    (say "Within: ranking countries in the same year.")
    (say (format "  %-34s %s" "inputs" (str/join " " (map first eras))))
    (let [results (for [[label names] [["world banking kernels" world-banking]
                                       ["world banking, slow only (10, 30)" (filterv #(re-find #"k(10|30)$" (clojure.core/name %)) world-banking)]
                                       ["world, all crisis kinds" (filterv #(str/starts-with? (clojure.core/name %) "world") all-names)]
                                       ["all, with own history" all-names]]]
                    (let [{:keys [preds world]} (prequential rows names {:from 1860 :train-from 1830})]
                      (say (format "  %-34s timing %s" label (str/join " " (map (fn [[_ lo hi]] (fmt (timing world lo hi))) eras))))
                      (say (format "  %-34s within %s" "" (str/join " " (map (fn [[_ lo hi]] (fmt (within preds lo hi))) eras))))
                      [label world]))
          best (second (last results))
          dir (str "data/runs/" name)]
      (io/make-parents (str dir "/x"))
      (spit (str dir "/world-intensity.tsv") (str/join (for [[yr p] (sort best)] (str yr "\t" p "\n"))))
      (spit (str dir "/waves.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
