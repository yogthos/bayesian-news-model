;; bnm-7ny: does t137's decay rank currency crises in long-quiet countries
;; lower? Refits the currency theory with --theory-decay (bnm.theory/fits),
;; then over the December origins 2003-2022 and the rows 9+ years past the
;; country's last known crisis, the mean log-odds contribution of the decay
;; columns (d and each stock x d) for onset rows against quiet rows.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/currency/decay_contrib.clj
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.kind-theory :as kt] '[bnm.theory :as th])
(def k "currency_crisis")
(def opts (wr/options (into (vec (lg/command "diag" lg/base-end)) ["--theory-decay" k])))
(def cats (wr/run-catalogues opts))
(def ks ["growth_collapse" "market_crash" k])
(def sp (get (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}}) k))
(let [{:keys [levels start end target kind-theory]} sp
      countries (second (first levels))
      os (filter #(zero? (mod (inc (- % start)) 12)) (range start (inc end)))
      inputs (:inputs kind-theory)
      fits (th/fits {:places countries :origins os :prior 1.0 :inputs inputs :raw (:raw kind-theory)
                     :penalties (:penalties kind-theory) :target (fn [u t] (target :year u t))})
      ons (:onsets (cats k))
      n-in (* 5 (count (kt/decls k)))
      labels ["d" "reserves x d" "external-debt x d" "inflation x d"]
      acc (atom {})]
  (doseq [[t ^doubles b] fits :when (<= 2003 (quot t 12) 2022)
          u countries
          :let [tg (target :year u t)
                last-on (some->> (get ons u) (filter #(<= % t)) seq (apply max))]
          :when (and tg (some? (:y tg)) last-on (>= (- t last-on) 108))]
    (let [x (inputs u t)
          cs (mapv (fn [i] (* (aget b (+ 1 n-in i)) (double (nth x (+ n-in i))))) (range 4))
          z (fn [i] (double (nth x (+ n-in i))))]
      (swap! acc update (if (:y tg) :onset :quiet)
             (fn [[n s zs]] [(inc (or n 0)) (mapv + (or s [0 0 0 0]) cs) (mapv + (or zs [0 0 0 0]) (map z (range 4)))]))))
  (doseq [[g [n s zs]] @acc]
    (println (name g) "rows" n)
    (doseq [i (range 4)]
      (println (format "   %-18s mean column %.4f  mean log-odds contribution %+.4f" (labels i) (/ (nth zs i) n) (/ (nth s i) n))))))
