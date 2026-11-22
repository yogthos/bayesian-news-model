;; t132 diagnosis 2: which kinds' layer fits alone (no year offset), and do
;; the kernels help as departures from the forecast the run issued?
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' issued.clj bench kind...
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.aftermath :as af] '[bnm.theory :as th]
         '[augur.math :as m] '[augur.logit :as lgt] '[clojure.string :as str])

(def net-kinds #{"banking_crisis" "currency_crisis" "food_price_shock" "growth_collapse"
                 "market_crash" "protest_surge" "violence_surge"})

(def opts (dissoc (wr/options (lg/command "diag" lg/base-end)) :aftermath :aftermath-net))
(def cats (wr/run-catalogues opts))
(def ons (wr/catalogue-onsets cats))
(def kinds (sort (keys cats)))
(def bench (first *command-line-args*))

(defn issued [k]
  (into {} (for [l (rest (str/split-lines (slurp (str "data/runs/" bench "/" k "/forecasts.tsv"))))
                 :let [[s u t p] (str/split l #"\t")]
                 :when (= "year" s)]
             [[u (parse-long t)] (parse-double p)])))

(defn f4 [x] (format "%.5f" (double x)))
(defn mean [f xs] (/ (reduce + 0.0 (map f xs)) (max 1.0 (double (count xs)))))
(defn brier [kp xs] (mean #(let [d (- (kp %) (:y %))] (* d d)) xs))

(doseq [k (rest *command-line-args*)
        row-kind [:kernel :net]]
  (let [ks [k]
        sp (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k)
        {:keys [scales start end target offset]} sp
        countries (second (first (:levels sp)))
        per (:per (first (filter #(= :year (:name %)) scales)))
        os (filter #(zero? (mod (inc (- % start)) per)) (range start (inc end)))
        with-offset (count (for [u countries t os :when (offset :year u t)] 1))
        iss (issued k)
        clip #(Math/max 1e-6 (Math/min (- 1.0 1e-6) %))
        base (fn [u t] (some-> (iss [u t]) clip m/logit))
        row (if (= :net row-kind) (af/net-row ons k kinds countries) (af/kernel-row ons k kinds))
        tgt (fn [u t] (target :year u t))
        fits (th/fits {:places countries :origins os :prior 1.0 :raw true :inputs row :target tgt :base base})
        rs (for [[t ^doubles b] fits u countries
                 :let [tg (tgt u t) o (base u t)]
                 :when (and tg o (>= t (+ (* 12 2003) 11)) (<= t (+ (* 12 2022) 11)))]
             (let [x (row u t)
                   sl (reduce + 0.0 (map-indexed (fn [i v] (* (aget b (inc i)) (double v))) x))]
               {:y (if (:y tg) 1.0 0.0) :p0 (m/expit o) :p1 (m/expit (+ o sl)) :p2 (m/expit (+ o sl (aget b 0)))}))
        lb (vec (val (last (sort fits))))]
    (println "==" k (name row-kind) "| year-offset rows" with-offset "| scored 2003-22" (count rs))
    (println "   last coefs" (mapv #(format "%.3f" %) lb))
    (println "   rate" (f4 (mean :y rs)) "mean p issued" (f4 (mean :p0 rs)) "+slopes" (f4 (mean :p1 rs)) "+slopes+b0" (f4 (mean :p2 rs)))
    (println "   brier gain vs issued: +slopes" (f4 (- (brier :p0 rs) (brier :p1 rs))) " +slopes+b0" (f4 (- (brier :p0 rs) (brier :p2 rs))))
    (flush)))
