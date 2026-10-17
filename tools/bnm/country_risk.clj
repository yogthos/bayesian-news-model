(ns bnm.country-risk
  "Does the model's political risk move country stock markets before they
  price it? (docs/TRADING.md). Changes in country risk ratings have predicted
  country returns (Erb, Harvey & Viskanta 1996; Bekaert, Harvey, Lundblad &
  Siegel 2014). The model's version: at each quarter's end, each country's
  out-of-sample probability of a political onset within four quarters
  (conflict or war onset, protest or violence surge: the chance of any, from
  the onset hazards' predictions), its level and its change over the year.

  Scored against the next quarter's change in the country's share price index
  (OECD, local currency, monthly averages, so the quarter after the signal's
  is used): the rank correlation each quarter, a portfolio long the least
  risky third and short the riskiest, and how often each third falls 20%
  within four quarters. Against two references: the same from the history
  model alone, and twelve-month momentum (Asness, Moskowitz & Pedersen 2013).

    jolt -A:tools -M -m bnm.country-risk --preds events-1 [--model pool]"
  (:require [bnm.named :as nm]
            [bnm.slow-train :as st]
            [bnm.stocks :as sk]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def political ["conflict_onset" "war_onset" "protest_surge" "violence_surge"])

(defn read-preds
  "{[model unit q] {kind p}} for window w1."
  [run]
  (with-open [r (io/reader (str "data/runs/" run "/preds.tsv"))]
    (reduce (fn [acc line]
              (let [[kind win model q unit p] (str/split line #"\t")]
                (if (= win "w1")
                  (assoc-in acc [[model unit (parse-long q)] kind] (parse-double p))
                  acc)))
            {} (line-seq r))))

(defn- any-onset
  "P(any of the political kinds), treating them as independent; nil unless all are there."
  [m]
  (let [ps (map #(get m %) political)]
    (when (every? some? ps) (- 1.0 (reduce * (map #(- 1.0 %) ps))))))

(defn- ranks [xs]
  (let [order (sort-by second (map-indexed vector xs))]
    (reduce (fn [acc [r [i _]]] (assoc acc i (double r))) (vec (repeat (count xs) 0.0)) (map-indexed vector order))))

(defn- spearman [xs ys]
  (let [rx (ranks xs) ry (ranks ys) n (count xs)
        mx (/ (reduce + rx) n) my (/ (reduce + ry) n)
        c (reduce + (map #(* (- %1 mx) (- %2 my)) rx ry))
        vx (reduce + (map #(let [d (- % mx)] (* d d)) rx))
        vy (reduce + (map #(let [d (- % my)] (* d d)) ry))]
    (if (and (pos? vx) (pos? vy)) (/ c (Math/sqrt (* vx vy))) 0.0)))

(defn- mean [xs] (/ (reduce + xs) (count xs)))
(defn- sd* [xs] (let [m (mean xs)] (Math/sqrt (/ (reduce + (map #(let [d (- % m)] (* d d)) xs)) (max 1 (dec (count xs)))))))

(defn run [{:keys [preds model]}]
  (let [model (or model "pool")
        pr (read-preds preds)
        variables (st/read-variables "data/slow/variables.tsv")
        db (nm/load-series "data/slow/series.tsv" variables)
        units (sort (distinct (for [[u v] (keys db) :when (= v "markets/share_prices")] u)))
        price (fn [u m] (some-> (db [u "markets/share_prices"]) (sk/value (inc m) {:stale 2})))
        qs (sort (distinct (map #(nth % 2) (keys pr))))
        signal (fn [mdl u q] (any-onset (pr [mdl u q])))
        rows (vec (for [q qs u units
                        :let [m (+ 2 (* 3 q))
                              p0 (price u (+ m 1)) p1 (price u (+ m 4))
                              s (signal model u q) s-prev (signal model u (- q 4))
                              h (signal "history" u q)
                              mom (when-let [a (price u m)] (when-let [b (price u (- m 12))] (Math/log (/ a b))))
                              fall (when p0 (let [ps (keep #(price u (+ m 1 %)) (range 1 13))]
                                              (when (= 12 (count ps)) (boolean (some #(<= % (* 0.8 p0)) ps)))))]
                        :when (and p0 p1 s)]
                    {:q q :unit u :ret (Math/log (/ p1 p0)) :level s :change (when s-prev (- s s-prev))
                     :history h :momentum mom :fall fall}))
        out (StringBuilder.)
        say (fn [& xs] (let [s (str/join " " xs)] (println s) (flush) (.append out s) (.append out "\n")))
        evaluate
        (fn [label k sign lo hi]
          ;; sign: +1 when a higher value should mean higher returns
          (let [per-q (for [[q rs] (group-by :q (filter #(and (some? (k %)) (<= lo (quot (:q %) 4) hi)) rows))
                            :when (>= (count rs) 9)]
                        (let [rs (vec (sort-by #(* sign (k %)) rs))
                              n (count rs) third (quot n 3)
                              low (subvec rs 0 third) high (subvec rs (- n third))]
                          {:ic (spearman (map #(* sign (k %)) rs) (map :ret rs))
                           :ls (- (mean (map :ret high)) (mean (map :ret low)))
                           :fall-low (keep :fall low) :fall-high (keep :fall high)}))
                ics (map :ic per-q) lss (map :ls per-q)
                frac (fn [xs] (if (seq xs) (/ (count (filter true? xs)) (double (count xs))) 0.0))]
            (when (seq per-q)
              (say (format "  %-26s quarters %3d  IC %+.3f (t %+.2f)  long-short %+5.2f%%/yr (Sharpe %+.2f)  falls 20%%: worst third %.2f, best third %.2f"
                           label (count per-q) (mean ics) (/ (mean ics) (/ (sd* ics) (Math/sqrt (count ics))))
                           (* 400 (mean lss)) (/ (* 2.0 (mean lss)) (max 1e-9 (sd* lss)))
                           (frac (mapcat :fall-low per-q)) (frac (mapcat :fall-high per-q)))))))]
    (say "Political risk and country stock markets, next quarter, out of sample. IC is the mean rank")
    (say "correlation between the signal (signed so that higher should mean higher returns) and returns;")
    (say "long-short is the best third minus the worst third. Signals: the model's P(any political onset")
    (say "within a year), its level (" model ") and change over the year, the history model's, and momentum.")
    (say (count units) "markets," (count rows) "country-quarters")
    (doseq [[era lo hi] [["2000-2007" 2000 2007] ["2008-" 2008 2100] ["all" 2000 2100]]]
      (say)
      (say era)
      (evaluate "risk level (low = good)" :level -1 lo hi)
      (evaluate "risk change (falling = good)" :change -1 lo hi)
      (evaluate "history model level" :history -1 lo hi)
      (evaluate "12-month momentum" :momentum 1 lo hi))
    (let [dir (str "data/runs/" preds)]
      (spit (str dir "/country-risk.txt") (str out)))))

(defn -main [& args]
  (run (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))))
