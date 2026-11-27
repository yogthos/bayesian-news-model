;; bnm-7yg.6: a kind's theory-offset coefficients by December origin, refit
;; as bnm.kind-theory/with-theory fits them (its :kind-theory inputs and
;; penalties, prior 1, the year target, trained on every December
;; origin's rows known by the origin), on the ledger command.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/sovereign_theory_fits.clj <kind> <y1,y2,...>
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.kind-theory :as kt] '[augur.logit :as lgt] '[clojure.string :as str])
(let [[kind years] *command-line-args*
      opts (wr/options (vec (lg/command "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      {:keys [levels target start end] :as sp} (get specs kind)
      {:keys [inputs penalties raw]} (:kind-theory sp)
      countries (second (first levels))
      os (filter #(zero? (mod (inc (- % start)) 12)) (range start (inc end)))
      rows (vec (for [u countries t os :let [tg (target :year u t)] :when tg]
                  {:x (double-array (cons 1.0 (map #(double (or % 0.0)) (inputs u t)))) :y (boolean (:y tg)) :w 1.0 :o 0.0 :known-at (:known-at tg)}))
      k (alength ^doubles (:x (first rows)))
      want (map #(+ 11 (* 12 (parse-long %))) (str/split years #","))
      names (concat ["intercept"] (map (comp name :name) (kt/decls kind)) ["missing-share"])]
  (println kind "theory coefficients (inputs' own scale, place-z), columns:" (str/join " " names))
  (doseq [t want :let [train (filter #(<= (:known-at %) t) rows)]]
    (println (quot t 12) (count train) "rows" (count (filter :y train)) "onsets"
             (str/join " " (map #(format "%+.3f" %)
                                (seq (lgt/fit train k (cond-> {:prior 1.0} raw (assoc :raw (set (range 1 k))) penalties (assoc :penalties penalties)))))))))
