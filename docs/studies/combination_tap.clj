;; bnm-7yg.8.6 (R1): every kind's year country node, row by row, on the
;; base's command, for docs/studies/combination.py. As reference_tap.clj,
;; plus each group model's predictive log odds and the averaging's weights.
;; Writes <out>/<kind>.tsv, a header line then one line per country row at a
;; December origin with a resolved outcome:
;;   place origin y known-at o p osz f:<group>... w:<group>... x0 x1 ...
;; osz the offsets after the frailty, w:o;w:o (bnm-7yg.8.6's amendment).
;; First checks the issued year forecasts against <check-run>'s, 2003-2022.
;; Usage: jolt -Sdeps '{:paths ["src" "resources" "test" "tools"]}' docs/studies/combination_tap.clj <out-dir> <check-run> [k1,k2]
(require '[bnm.world-run :as wr] '[bnm.ledger :as lg] '[bnm.lattice :as lt] '[clojure.string :as str] '[clojure.java.io :as io])

(defn- num [v] (if (some? v) (str (double v)) ""))

(let [[out check only] *command-line-args*
      args (vec (lg/command "diag" lg/base-end))
      opts (wr/options args)
      cats (wr/run-catalogues opts)
      ks (vec (sort (keys cats)))
      specs (wr/pipeline-specs opts ks lg/base-end cats {:fresh (wr/fresh-kinds (:fresh-frailty opts) ks cats) :nq #{}})
      run-ks (if (seq only) (str/split only #",") ks)]
  (.mkdirs (io/file out))
  (doseq [k run-ks
          :let [sp0 (get specs k)
                rows (atom [])
                sp (assoc sp0 :tap (fn [{:keys [scale level t] :as r}]
                                     (when (and (= :year scale) (= :country level) (= 11 (mod t 12)))
                                       (swap! rows conj (select-keys r [:place :t :x :o :osz :f :w])))))
                end (:end sp)
                res (loop [s (lt/start sp)] (if (> (:t s) end) (lt/finish s) (recur (lt/step sp s))))
                issued (into {} (for [{:keys [scale place origin p]} (:forecasts res)
                                      :when (and (= :year scale) (= 3 (count place)) (= 11 (mod origin 12)))]
                                  [[place origin] p]))
                world (into {} (for [l (str/split-lines (slurp (str "data/runs/" check "/" k "/forecasts.tsv")))
                                     :let [[s u o p] (str/split l #"\t")]
                                     :when (and (= "year" s) (= 3 (count u)))]
                                 [[u (parse-long o)] (parse-double p)]))
                diffs (keep (fn [[[u o :as key] p]] (when (<= 2003 (quot o 12) 2022) (when-let [q (world key)] (Math/abs (- p q))))) issued)
                groups (vec (sort-by str (distinct (mapcat #(keys (:f %)) @rows))))]]
    (println k "rows" (count @rows) "groups" (count groups) "checked 2003-2022" (count diffs)
             "max |p - run p|" (if (seq diffs) (apply max diffs) "none"))
    (with-open [w (io/writer (str out "/" k ".tsv"))]
      (.write w (str (str/join "\t" (concat ["place" "origin" "y" "known-at" "o" "p" "osz"]
                                            (map #(str "f:" (name %)) groups)
                                            (map #(str "w:" (name %)) groups)))
                     "\tx...\n"))
      (doseq [{:keys [place t x o osz f] wt :w} @rows
              :let [{:keys [y known-at]} ((:target sp) :year place t)]
              :when (some? y)]
        (.write w (str/join "\t" (concat [place t y known-at (num o) (num (issued [place t]))
                                                 (str/join ";" (for [[a b] osz] (str (double a) ":" (double b))))]
                                         (map #(num (get f %)) groups)
                                         (map #(num (get wt % 0.0)) groups)
                                         (map num x))))
        (.write w "\n")))))
