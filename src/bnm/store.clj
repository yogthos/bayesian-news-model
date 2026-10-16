(ns bnm.store
  "Keeping a model somewhere: a database, a file, memory.

  A model is stored as a few named parts of plain data and one row per node.
  The parts are :header (config, tracked units, exposures, calibration:
  small), :volumes (event volumes per unit, which only restructuring reads),
  :pending (the events buffered for the open bin, which change with every
  batch) and :track (the history and the record its live scoring keeps, see
  `bnm.track`); a host keeps each as text (`->text`, `<-text`). A row is

    {:id \"UKR:GOV|UKR:OPP\" :level 0 :links \"{:parents [...] ...}\" :data <bytes>}

  where :links is EDN (the node's place in the structure, and its sources)
  and :data the node's learned arrays, packed by `encode-node`. A host maps
  this onto two tables (parts by name, nodes by id), and implements
  `ModelStore` over them; `bnm.core` does the rest:

    (bnm/load store)           ; the whole model, to run
    (bnm/save! store m)        ; after learning; writes only what changed
    (bnm/drivers store id ch)  ; one node's connections, without loading the model

  Live, the model is kept in memory and saved after each batch of events:
  every node learns from every bin (an empty bin is evidence too), so a save
  writes every row once a bin has closed, and only :pending in between. Derived values
  (indices, priors, mu and alpha, scratch space) are not stored: mu and alpha
  are recomputed from their sufficient statistics on load (see
  `bnm.model/refresh!`), which is also what keeps rows small.

  `memory-store` and `file-store` are the stores this library provides: the
  first for tests, the second a single binary file for shipping a trained
  model."
  (:require [bnm.model :as model]
            [bnm.track :as track]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(set! *unchecked-math* true)

(def format-version 3)

(defprotocol ModelStore
  (read-part [store part] "The stored value of `part` (:header, :volumes, :pending, :track), or nil.")
  (read-nodes [store ids] "The rows of the nodes with ids in `ids`, or of every node when nil.")
  (node-ids [store] "The ids of every stored node.")
  (write! [store parts rows deleted]
    "In one transaction: replaces each part in the map `parts`, inserts or
    replaces `rows` by id, and deletes the rows with ids in `deleted`."))

(defn ->text "A part as text, for a host to store." [v] (pr-str v))
(defn <-text "A part from its text." [s] (when s (edn/read-string s)))

;; --- node rows ---------------------------------------------------------------------------

(def ^:private saved-arrays
  "The node arrays stored: its state and sufficient statistics."
  [:h :last :ybar :smu :tmu :sa :ta :kappa :sk :tk :loss-own :loss-ch :disp])

(def ^:private magic 0x424E4D33) ; "BNM3"

(defn- put-doubles! [^java.nio.ByteBuffer bb ^doubles a ^long from ^long n]
  (dotimes [i n] (.putDouble bb (aget a (+ from i)))))

(defn- get-doubles! [^java.nio.ByteBuffer bb ^doubles a ^long from ^long n]
  (dotimes [i n] (aset a (+ from i) (.getDouble bb))))

(defn encode-node
  "A node's learned arrays as bytes: doubles, little-endian, each array
  prefixed by its length (an int). Doubles, not floats, so a model restored
  from a store is the model that was saved, bit for bit."
  ^bytes [node]
  (let [n (+ (* 4 (+ 2 (count saved-arrays)))
             (* 8 (reduce + (map #(alength ^doubles (get node %)) saved-arrays))))
        bb (java.nio.ByteBuffer/allocate n)]
    (.order bb java.nio.ByteOrder/LITTLE_ENDIAN)
    (.putInt bb (unchecked-int magic))
    (.putInt bb (count saved-arrays))
    (doseq [k saved-arrays]
      (let [^doubles a (get node k)]
        (.putInt bb (alength a))
        (put-doubles! bb a 0 (alength a))))
    (.array bb)))

(defn- decode-into!
  "Fills `node`'s arrays from `data`, when the shapes agree. Returns whether
  they did."
  [node ^bytes data]
  (let [bb (java.nio.ByteBuffer/wrap data)]
    (.order bb java.nio.ByteOrder/LITTLE_ENDIAN)
    (and (= (.getInt bb) (unchecked-int magic))
         (= (.getInt bb) (count saved-arrays))
         (every? (fn [k]
                   (let [^doubles a (get node k)
                         n (.getInt bb)]
                     (when (= n (alength a))
                       (get-doubles! bb a 0 n)
                       true)))
                 saved-arrays))))

(defn node->row [node structure-node order]
  {:id (:id node)
   :level (:level node)
   :links (pr-str (assoc (select-keys structure-node [:parents :neighbors :children])
                         :sources (:sources node) :order order))
   :data (encode-node node)})

(defn row-links [row] (edn/read-string (:links row)))

;; --- the parts ------------------------------------------------------------------------------

(defn header
  "Everything learned that isn't in a node, the volumes or the buffer."
  [mdl]
  {:format format-version
   :config (:config mdl)
   :tracked (get-in mdl [:structure :tracked])
   :bin (:bin mdl)
   :exposure (:exposure mdl)
   :stats (:stats mdl)
   :calibration (:calibration mdl)})

(defn- volumes-part [mdl]
  (when-let [v (:volumes mdl)]
    {:epoch (:epoch v) :units (into {} (:units v))}))

;; --- whole models ------------------------------------------------------------------------------

(defn rows
  "Every node of `mdl` as a row."
  [mdl]
  (let [snodes (get-in mdl [:structure :nodes])]
    (vec (map-indexed (fn [i node] (node->row node (snodes i) i)) (:nodes mdl)))))

(defn assemble
  "A working model from its parts and node rows."
  [{:keys [header volumes pending track]} node-rows]
  (when-not (= format-version (:format header))
    (throw (ex-info "unknown model format" {:format (:format header)})))
  (let [linked (sort-by (comp :order second) (map (juxt identity row-links) node-rows))
        structure {:tracked (:tracked header)
                   :nodes (vec (for [[row links] linked]
                                 (merge {:id (:id row) :level (:level row)}
                                        (select-keys links [:parents :neighbors :children]))))}
        mdl (model/create structure (:config header))]
    (doseq [[[row links] node] (map vector linked (:nodes mdl))]
      (when-not (and (= (:sources links) (:sources node)) (decode-into! node (:data row)))
        (throw (ex-info "stored node does not fit the structure" {:id (:id row)}))))
    (model/refresh!
     (merge mdl
            (select-keys header [:bin :exposure :stats :calibration])
            {:pending (or pending {})}
            (when track {:track (track/<-part track)})
            (when volumes
              {:volumes {:epoch (:epoch volumes)
                         :units (java.util.HashMap. ^java.util.Map (:units volumes))}})))))

(defn load-model
  "The model in `store`, or nil when it is empty."
  [store]
  (when-let [h (read-part store :header)]
    (let [mdl (assemble {:header h
                         :volumes (read-part store :volumes)
                         :pending (read-part store :pending)
                         :track (read-part store :track)}
                        (read-nodes store nil))]
      (assoc mdl :saved {:bin (:bin mdl) :ids (mapv :id (:nodes mdl)) :track (:track mdl)}))))

(defn save-model!
  "Writes `mdl` to `store`. When it has learned since it was last saved to
  or loaded from the store (the model's :saved marks when), that is every
  part and every node, deleting nodes the structure has dropped; otherwise
  only the buffered events, and its track (see `bnm.track`) when a forecast
  was registered since. Returns the model, marked saved."
  [store mdl]
  (let [ids (mapv :id (:nodes mdl))
        saved (:saved mdl)
        fresh? (and saved (= (:bin saved) (:bin mdl)) (= (:ids saved) ids))]
    (if fresh?
      (write! store (cond-> {:pending (:pending mdl {})}
                      (not (identical? (:track saved) (:track mdl))) (assoc :track (track/->part (:track mdl))))
              [] [])
      (write! store {:header (header mdl) :volumes (volumes-part mdl) :pending (:pending mdl {})
                     :track (track/->part (:track mdl))}
              (rows mdl)
              (vec (remove (set ids) (node-ids store)))))
    (assoc mdl :saved {:bin (:bin mdl) :ids ids :track (:track mdl)})))

;; --- one node at a time ---------------------------------------------------------------------

(defn- standalone [cfg row]
  (let [links (row-links row)
        node (model/standalone-node cfg (merge {:id (:id row) :level (:level row)} links)
                                    (:sources links))]
    (when-not (decode-into! node (:data row))
      (throw (ex-info "stored node does not fit its links" {:id (:id row)})))
    node))

(defn read-node
  "Node `id` read from `store` on its own, with its mu and alpha recomputed:
  for looking at one node without loading the model. nil when not stored."
  ([store id] (read-node store (:config (read-part store :header)) id))
  ([store cfg id]
   (when-let [row (first (read-nodes store [id]))]
     (let [sources (:sources (row-links row))
           ybars (into {} (for [r (read-nodes store sources)] [(:id r) (:ybar (standalone cfg r))]))
           node (standalone cfg row)]
       (model/refresh-node! cfg node (mapv ybars sources))
       (assoc node :source-ybars ybars)))))

(defn node-drivers
  "`bnm.model/node-connections` of node `id`, channel index `c`, read from
  `store`: the node's row and its sources' rows, not the whole model."
  [store id c]
  (let [cfg (:config (read-part store :header))]
    (when-let [node (read-node store cfg id)]
      (model/node-connections cfg node (:source-ybars node) c))))

;; --- stores -----------------------------------------------------------------------------------

(defn- apply-write [state parts rows deleted]
  (-> state
      (update :parts merge parts)
      (update :rows #(apply dissoc % deleted))
      (update :rows into (map (juxt :id identity) rows))))

(defn memory-store
  "A store in an atom."
  []
  (let [state (atom {:parts {} :rows {}})]
    (reify ModelStore
      (read-part [_ part] (get-in @state [:parts part]))
      (read-nodes [_ ids]
        (let [rs (:rows @state)]
          (if ids (vec (keep rs ids)) (vec (vals rs)))))
      (node-ids [_] (keys (:rows @state)))
      (write! [_ parts rows deleted]
        (swap! state apply-write parts rows deleted)
        nil))))

;; A file is: magic, then length-prefixed blocks: the parts as EDN, then per
;; node its id, level, links and data.

(defn- put-block! [^java.io.OutputStream o ^bytes bs]
  (let [bb (java.nio.ByteBuffer/allocate 4)]
    (.order bb java.nio.ByteOrder/LITTLE_ENDIAN)
    (.putInt bb (alength bs))
    (.write o (.array bb))
    (.write o bs)))

(defn- utf8 ^bytes [^String s] (.getBytes s "UTF-8"))

(defn- read-file-store [path]
  (let [f (io/file path)]
    (if-not (.exists f)
      {:parts {} :rows {}}
      (let [bs (java.nio.file.Files/readAllBytes (.toPath f))
            bb (java.nio.ByteBuffer/wrap bs)
            _ (.order bb java.nio.ByteOrder/LITTLE_ENDIAN)
            block (fn [] (let [b (byte-array (.getInt bb))] (.get bb b) b))
            text (fn [] (String. ^bytes (block) "UTF-8"))]
        (when-not (= (.getInt bb) (unchecked-int magic))
          (throw (ex-info "not a model file" {:path path})))
        (let [parts (edn/read-string (text))]
          (loop [rows {}]
            (if (pos? (.remaining bb))
              (let [id (text)
                    level (parse-long (text))
                    links (text)
                    data (block)]
                (recur (assoc rows id {:id id :level level :links links :data data})))
              {:parts parts :rows rows})))))))

(defn- write-file-store! [path {:keys [parts rows]}]
  (io/make-parents path)
  (let [tmp (str path ".part")]
    (with-open [o (io/output-stream tmp)]
      (let [bb (java.nio.ByteBuffer/allocate 4)]
        (.order bb java.nio.ByteOrder/LITTLE_ENDIAN)
        (.putInt bb (unchecked-int magic))
        (.write o (.array bb)))
      (put-block! o (utf8 (pr-str parts)))
      (doseq [{:keys [id level links data]} (vals rows)]
        (put-block! o (utf8 id))
        (put-block! o (utf8 (str level)))
        (put-block! o (utf8 links))
        (put-block! o data)))
    (.renameTo (io/file tmp) (io/file path))))

(defn file-store
  "A store in one binary file at `path`, read whole and written whole: for
  shipping and backing up a model, not for a live one."
  [path]
  (reify ModelStore
    (read-part [_ part] (get-in (read-file-store path) [:parts part]))
    (read-nodes [_ ids]
      (let [rs (:rows (read-file-store path))]
        (if ids (vec (keep rs ids)) (vec (vals rs)))))
    (node-ids [_] (keys (:rows (read-file-store path))))
    (write! [_ parts rows deleted]
      (write-file-store! path (apply-write (read-file-store path) parts rows deleted))
      nil)))

(defn write-file
  "Saves `mdl` to a new file at `path`."
  [mdl path]
  (io/delete-file path true)
  (save-model! (file-store path) (dissoc mdl :saved))
  nil)

(defn read-file
  "The model saved at `path`."
  [path]
  (load-model (file-store path)))
