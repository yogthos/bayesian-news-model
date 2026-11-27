;; bnm-7yg.6: a kind's theory-offset coefficients by December origin, refit
;; as bnm.kind-theory/with-theory fits them (its :kind-theory inputs and
;; penalties, prior 1, the year target, trained on every December
;; origin's rows known by the origin), on the ledger command, without and
;; with a half-life on the training rows (bnm.theory/fits :half-life); then
;; the point-in-time offset's AUC over the December rows 2003-2022 for each.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/sovereign_theory_fits.clj <kind> <y1,y2,...> [half-life years] [rows.tsv]
;; rows.tsv: every design row (place, origin, known-at, y, x...) and the penalties, for sovereign_theory_rows.py.
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.kind-theory :as kt] '[augur.logit :as lgt] '[clojure.string :as str])

(defn auc [pairs]
  (let [pos (vec (keep (fn [[s y]] (when y s)) pairs))
        neg (vec (keep (fn [[s y]] (when-not y s)) pairs))]
    (when (and (seq pos) (seq neg))
      (/ (reduce + (for [p pos n neg] (cond (> p n) 1.0 (= p n) 0.5 :else 0.0)))
         (* (count pos) (double (count neg)))))))

(let [[kind years hl dump] *command-line-args*
      hl (some-> hl parse-double)
      opts (wr/options (vec (lg/command "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      {:keys [levels target start end] :as sp} (get specs kind)
      {:keys [inputs penalties raw]} (:kind-theory sp)
      countries (second (first levels))
      os (filter #(zero? (mod (inc (- % start)) 12)) (range start (inc end)))
      xs (into {} (for [u countries t os] [[u t] (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t))))]))
      rows (vec (for [u countries t os :let [tg (target :year u t)] :when tg]
                  {:x (xs [u t]) :y (boolean (:y tg)) :w 1.0 :o 0.0 :known-at (:known-at tg) :t t :u u}))
      k (alength ^doubles (:x (first rows)))
      want (set (map #(+ 11 (* 12 (parse-long %))) (str/split years #",")))
      names (concat ["intercept"] (map (comp name :name) (kt/decls kind)) ["missing-share"])
      fit-at (fn [t h]
               (let [train (cond->> (filter #(<= (:known-at %) t) rows)
                             h (map #(assoc % :w (Math/pow 0.5 (/ (- t (:t %)) (* 12.0 h))))))]
                 (lgt/fit train k (cond-> {:prior 1.0} raw (assoc :raw (set (range 1 k))) penalties (assoc :penalties penalties)))))
      tests (filter #(<= 2003 (quot % 12) 2022) os)]
  (when dump
    (spit dump (with-out-str
                 (println (str "#penalties\t" (str/join "\t" (or penalties [])) "\traw\t" (boolean raw)))
                 (println (str/join "\t" (concat ["place" "t" "known_at" "y"] names)))
                 (doseq [{:keys [u t known-at y x]} rows]
                   (println (str/join "\t" (concat [u t known-at (if y 1 0)] (seq x)))))))))
  (println kind "theory coefficients (inputs' own scale, place-z), columns:" (str/join " " names))
  (doseq [[label h] [["no half-life" nil] [(str "half-life " hl) hl]] :when (or (nil? h) hl)]
    (let [fits (into {} (for [t tests] [t (fit-at t h)]))]
      (println "==" label)
      (doseq [t (sort want) :let [b (fits t)] :when b]
        (println (quot t 12) (str/join " " (map #(format "%+.3f" %) (seq b)))))
      (let [scored (for [{:keys [t x y]} rows :when (fits t)
                         :let [b ^doubles (fits t)]]
                     [(reduce + (map * (seq b) (seq x))) y])]
        (println "offset AUC, December rows 2003-2022:" (format "%.3f" (auc scored)) (count scored) "rows")))))
