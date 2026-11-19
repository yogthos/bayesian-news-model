;; bnm-5lr: banking's theory offset and its inputs per country and December
;; origin (the spec's :theory), to see what moves a forecast.
;; jolt -A:tools -M docs/studies/theory_diag.clj FRA,JPN,DEU 2019 2023 [recession]
(require '[bnm.lattice-banking :as lb] '[augur.math :as m] '[clojure.string :as str])
(let [[us a b rec] *command-line-args*
      us (str/split us #",")
      sp (lb/build 24311 "r7-at-fx" {:catalogue-through nil :leak-fixes true :gauge-std :stable :real-lags true :dated-credit true
                                     :theory-offset true :theory-world :all :recession-hold (= rec "recession")})
      {:keys [offsets inputs]} (:theory sp)]
  (println "unit origin  p-theory  inputs [credit-stretch boom-credit3 world-ltd world-reserves]")
  (doseq [u us y (range (parse-long a) (inc (parse-long b))) :let [t (+ 11 (* 12 y))]]
    (println u y (some-> (get offsets [u t]) m/expit (#(format "%.3f" %)))
             (some->> (inputs u t) (mapv #(when % (Double/parseDouble (format "%.2f" (double %)))))))))
