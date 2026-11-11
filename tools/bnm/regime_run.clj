(ns bnm.regime-run
  "Historical drawdowns replayed through a stock the engine holds (bead
  bnm-56c.4; bnm.engine's regime switch, docs/BUFFERS.md \"Regime
  switch\"). Descriptive only, nothing fitted: each case in
  resources/buffers/regimes.edn names a buffer record (its rigidities and
  where a cut lands) and the columns that measure it; the outflows wanted
  are those recorded, the inflow the residual that closes the recorded
  stock's change, the floor data. The run starts from the recorded stock
  at the end of the year before :from, under each sharing rule and with
  no floor. A hindsight replay of data years, current vintages: an
  illustration, not a result.

  Writes data/derived/buffers/regime-demo.txt.

    jolt -A:tools -M -m bnm.regime-run"
  (:require [bnm.buffers :as b]
            [bnm.buffers-run :as br]
            [bnm.engine :as eng]
            [bnm.modules.held :as held]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def out-path "data/derived/buffers/regime-demo.txt")

(defn- rows
  "The rows of CSV `file` (maps by header keyword) whose fields match `match`."
  [file match]
  (with-open [r (io/reader file)]
    (let [[h & ls] (line-seq r) ks (mapv keyword (br/split-csv h))]
      (vec (for [l ls :let [m (zipmap ks (br/split-csv l))]
                 :when (every? (fn [[k v]] (= v (get m k))) match)]
             m)))))

(defn- num-of [s] (some-> s str not-empty parse-double))

(defn series
  "{:stock {t v} :flows {var {t v}} :floor {t v}} of a case: the table's
  columns scaled, the residual inflow the recorded stock's change plus the
  recorded outflows, the floor from its own file at its month, lagged."
  [{:keys [table stock outflows residual floor]}]
  (let [rs (rows (:file table) (:match table))
        col (fn [c] (into (sorted-map) (for [r rs :let [v (num-of (get r c))] :when v]
                                         [(parse-long (get r (:t table))) (* v (:scale table 1.0))])))
        st (col stock)
        outs (into {} (for [[v c] outflows] [v (col c)]))
        res (into (sorted-map) (for [[t s] st :let [s0 (get st (dec t)) os (map #(get % t) (vals outs))]
                                     :when (and s0 (every? some? os))]
                                 [t (+ (- s s0) (reduce + os))]))
        fl (when floor
             (into (sorted-map) (for [r (rows (:file floor) (:match floor))
                                      :let [[y m] (str/split (:month r) #"-")]
                                      :when (= m (:month floor))
                                      :let [v (num-of (get r (:column floor)))] :when v]
                                  [(+ (parse-long y) (:lag floor 0)) (* (if (:negate floor) -1.0 1.0) v (:scale floor 1.0))])))]
    {:stock st :flows (assoc outs residual res) :floor fl}))

(defn run-case
  "The case's engine run under `sharing`, the floor as data or none."
  [c rec {:keys [stock flows floor]} sharing with-floor?]
  (let [m (held/record-module {:module (keyword (str "case-" (str/lower-case (:place c)))) :place (keyword (:place c))
                              :record rec :init (get stock (dec (:from c)))
                              :floor (if with-floor? "floor" 0.0) :sharing sharing
                              :inflows [{:var (:residual c) :counterpart "rest of the world"}]
                              :series (cond-> flows with-floor? (assoc "floor" floor))})]
    (eng/run [m] {:from (:from c) :to (:to c)})))

(defn- fmt [x] (if (number? x) (format "%.0f" (double x)) (str x)))

(defn case-lines [c rec]
  (let [ser (series c)
        outs (keys (:outflows c))]
    (concat
     [(str "# " (:id c) " (" (:record c) "): " (:place c) " " (:from c) "-" (:to c) ", " (:unit rec))
      (str "# " (:note c))
      (str "# recorded stock at year end, from " (dec (:from c)) ": "
           (str/join " " (for [t (range (dec (:from c)) (inc (:to c)))] (str t " " (fmt (get-in ser [:stock t]))))))]
     (for [[label sharing floor?] [["rigid first, floor as data" :rigid-first true]
                                   ["pro rata, floor as data" :pro-rata true]
                                   ["rigid first, no floor" :rigid-first false]]
           :let [r (run-case c rec ser sharing floor?)
                 [[_ xs]] (seq (:regimes r))]
           l (concat
              ["" (str "## " label)
               (str "year  level  floor  inflow  wanted  delivered  regime       "
                    (str/join "  " (for [o outs] (str o " cut (booked)"))) "  level at end (recorded)")]
              (for [x xs]
                (str (:t x) "  " (fmt (:level x)) "  " (fmt (:floor x)) "  " (fmt (:inflow x)) "  " (fmt (:wanted x)) "  "
                     (fmt (:delivered x)) "  " (name (:regime x)) "  "
                     (str/join "  " (for [o outs :let [f (some (fn [[v f]] (when (str/ends-with? v (str "/" o)) f)) (:flows x))]]
                                      (str (fmt (:shortfall f)) (when (:booked f) (str " (" (name (:booked f)) ")")))))
                     "  " (fmt (:next x)) " (" (fmt (get-in ser [:stock (:t x)])) ")")))]
       l))))

(defn -main [& _]
  (let [cases (:cases (edn/read-string (slurp "resources/buffers/regimes.edn")))
        recs (into {} (map (juxt :id b/record)) (:records (b/read-mappings "resources/buffers/stocks.edn")))
        ls (concat ["# The regime switch on recorded drawdowns (bnm.regime-run): descriptive, nothing fitted."
                    "# The outflows wanted are those recorded; the inflow closes the recorded stock's change; a cut is"
                    "# what the floor would not allow. Hindsight replay of data years, current vintages."]
                   (mapcat (fn [c] (concat [""] (case-lines c (recs (:record c))))) cases))]
    (io/make-parents out-path)
    (spit out-path (str (str/join "\n" ls) "\n"))
    (doseq [l ls] (println l))))
