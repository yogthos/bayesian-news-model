;; bnm-7yg.6: a kind's theory-offset coefficients by December origin, refit
;; as bnm.kind-theory/with-theory fits them (its :kind-theory inputs and
;; penalties, prior 1, the year target), on the ledger command.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/sovereign_theory_fits.clj <kind> <y1,y2,...>
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.theory :as th] '[bnm.kind-theory :as kt] '[clojure.string :as str])
(let [[kind years] *command-line-args*
      opts (wr/options (vec (lg/command "diag" lg/base-end)))
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      {:keys [levels target] :as sp} (get specs kind)
      {:keys [inputs penalties raw]} (:kind-theory sp)
      countries (second (first levels))
      want (map #(+ 11 (* 12 (parse-long %))) (str/split years #","))
      fits (th/fits {:places countries :origins want :prior 1.0 :inputs inputs :raw (boolean raw) :penalties penalties
                     :target (fn [u t] (target :year u t))})
      names (concat ["intercept"] (map (comp name :name) (kt/decls kind)) ["missing-share"])]
  (println kind "theory coefficients (standardized inputs), columns:" (str/join " " names))
  (doseq [t want :let [^doubles b (get fits t)]]
    (println (quot t 12) (if b (str/join " " (map #(format "%+.3f" %) (seq b))) "no fit"))))
