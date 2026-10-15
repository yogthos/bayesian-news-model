(ns bnm.store
  "The model as data, for a database.

  `snapshot` gives an EDN-safe map (arrays become vectors) and `restore`
  rebuilds a working model from one. `->rows` splits a snapshot into one meta
  record and one record per node, so a host can keep it in two tables and
  update nodes in place:

    meta:  {:format :config :structure :bin :exposure :stats :volumes}
    nodes: [{:node \"RUS|UKR\" :level 0 :data \"{...edn...}\"} ...]

  Derived values (indices, priors, scratch space) are not stored."
  (:require [bnm.model :as model]
            [clojure.edn :as edn]))

(def format-version 1)

(def ^:private saved-arrays
  [:h :last :ybar :mu :smu :tmu :alpha :sa :ta :kappa :sk :tk :loss-own :loss-ch :disp])

(defn- node->data [node]
  (into {:id (:id node) :level (:level node) :sources (:sources node) :kinds (:kinds node)}
        (for [k saved-arrays] [k (vec (get node k))])))

(defn snapshot
  "An EDN-safe map of everything the model has learned."
  [mdl]
  {:format format-version
   :config (:config mdl)
   :structure (:structure mdl)
   :bin (:bin mdl)
   :exposure (:exposure mdl)
   :stats (:stats mdl)
   :calibration (:calibration mdl)
   :volumes (when-let [v (:volumes mdl)]
              {:epoch (:epoch v)
               :dyads (into {} (:dyads v))
               :places (into {} (:places v))})
   :nodes (mapv node->data (:nodes mdl))})

(defn- fill! [^doubles arr xs]
  (when (= (alength arr) (count xs))
    (dotimes [i (alength arr)] (aset arr i (double (nth xs i)))))
  arr)

(defn restore
  "A working model from a `snapshot`."
  [snap]
  (when-not (= format-version (:format snap))
    (throw (ex-info "unknown model format" {:format (:format snap)})))
  (let [mdl (model/create (:structure snap) (:config snap))
        saved (into {} (map (juxt :id identity)) (:nodes snap))]
    (doseq [node (:nodes mdl)
            :let [data (saved (:id node))]
            :when (and data (= (:sources data) (:sources node)))]
      (doseq [k saved-arrays] (fill! (get node k) (get data k))))
    (merge mdl
           (select-keys snap [:bin :exposure :stats :calibration])
           (when-let [v (:volumes snap)]
             {:volumes {:epoch (:epoch v)
                        :dyads (java.util.HashMap. ^java.util.Map (:dyads v))
                        :places (java.util.HashMap. ^java.util.Map (:places v))}}))))

(defn ->rows [mdl]
  (let [snap (snapshot mdl)]
    {:meta (dissoc snap :nodes)
     :nodes (mapv (fn [n] {:node (:id n) :level (:level n) :data (pr-str n)})
                  (:nodes snap))}))

(defn rows->model [meta node-rows]
  (restore (assoc meta :nodes (mapv #(edn/read-string (:data %)) node-rows))))

(defn write-file
  "Saves the model to `path` as EDN."
  [mdl path]
  (spit path (pr-str (snapshot mdl))))

(defn read-file [path]
  (restore (edn/read-string (slurp path))))
