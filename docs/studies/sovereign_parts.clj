;; bnm-7yg.6: sovereign defaults' issued year country forecast in its parts,
;; per row at the December origins 2003-2022 (bnm.lattice's :tap): the mean
;; offset o (the theory's, with what the node takes from above), each group
;; model's log odds and weight, the history group's inputs, the outcome.
;; On the ledger command; `drop` names flags whose pair is removed (t160:
;; --theory-half-life). Output TSV on stdout, analysed by sovereign_parts.py.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/sovereign_parts.clj [flag,flag]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str])

(defn drop-flags [args flags]
  (loop [[a b & more :as xs] args out []]
    (cond (empty? xs) out
          (flags a) (recur more out)
          :else (recur (rest xs) (conj out a)))))

(let [[dropped] *command-line-args*
      kind "sovereign_default"
      args (drop-flags (vec (lg/command "diag" lg/base-end)) (set (when (seq dropped) (str/split dropped #","))))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      sp0 (get specs kind)
      names (get-in sp0 [:gauge-names :year])
      idx (fn [n] (.indexOf (vec names) n))
      hist [:since-last :own10 :own30]
      hi (mapv idx hist)
      rows (atom [])
      sp (assoc sp0 :tap (fn [{:keys [scale level place t x o f w]}]
                           (when (and (= :year scale) (= :country level) (= 11 (mod t 12)) (<= 2003 (quot t 12) 2022))
                             (swap! rows conj {:place place :t t :o o :f f :w w
                                               :h (mapv #(when (>= % 0) (nth x %)) hi)}))))
      end (:end sp)]
  (binding [*out* *err*] (println "history inputs at" (zipmap hist hi) "of" (count names) "gauges"))
  (loop [s (lt/start sp)] (when (<= (:t s) end) (recur (lt/step sp s))))
  (let [groups (vec (sort (keys (:f (first @rows)))))]
    (println (str/join "\t" (concat ["place" "year" "y" "o"] (map #(str "f_" (name %)) groups)
                                    (map #(str "w_" (name %)) groups) (map name hist))))
    (doseq [{:keys [place t o f w h]} @rows
            :let [tg ((:target sp) :year place t)]
            :when tg]
      (println (str/join "\t" (concat [place (quot t 12) (if (:y tg) 1 0) o]
                                      (map f groups) (map #(get w % 0.0) groups) (map #(or % "") h)))))))
