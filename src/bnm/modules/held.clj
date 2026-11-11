(ns bnm.modules.held
  "A stock the engine holds (bnm.engine, the regime switch; docs/PLAN.md
  1.3, docs/BUFFERS.md \"Regime switch\") built from data: its flows'
  wanted values from series, its terms from a buffer record
  (bnm.buffers/record). Nothing here names a kind of stock; reserves,
  grain or fuel are records and series.

  series-module's step at t emits each flow's value at t from its series
  {t value}, and the floor's when the floor is a series; nothing after t.
  A flow with no value at t is not emitted (the engine flags it)."
  (:require [clojure.string :as str]))

(defn- slug [s] (-> (str s) str/trim str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))

(defn series-module
  "An engine module holding one stock. spec: :module (id), :place, :group
  (:W), :stock (key), :unit, :init (a number or a parameter), :floor (a
  number, or a variable of :series: a floor that moves), :sharing, :inflows
  and :outflows [{:var v ...the buffer schema's flow keys, :shortfall}],
  :series {var {t value}}, :params, :doc. Variables are emitted as
  <module>/<var>; the level as <module>/<stock>."
  [{:keys [module place group stock unit init floor sharing inflows outflows series params doc]}]
  (let [q (fn [v] (str (name module) "/" v))
        lv (q (name stock))
        floor-var (when (string? floor) floor)
        flow-vars (distinct (concat (map :var inflows) (map :var outflows) (when floor-var [floor-var])))
        flow-unit (str unit " per year")]
    {:module module :group (or group :W) :place (or place :world) :dt 1
     :doc (or doc (str "a stock held by the engine, its flows from series: " lv))
     :reads []
     :emits (into [{:var lv :unit unit :phase :level}]
                  (for [v flow-vars] {:var (q v) :unit (if (= v floor-var) unit flow-unit)}))
     :params (or params {})
     :stocks {stock (cond-> {:unit unit :level lv :init init
                             :inflows (mapv #(update % :var q) inflows)
                             :outflows (mapv #(update % :var q) outflows)}
                      floor (assoc :floor (if floor-var (q floor-var) floor))
                      sharing (assoc :sharing sharing))}
     :step (fn [s _ _ t _]
             {:state s
              :emits (into {} (for [v flow-vars :let [x (get-in series [v t])] :when (number? x)] [(q v) (double x)]))})}))

(defn record-module
  "series-module from a buffer record (bnm.buffers/record): its stock
  named by the record's :name, its unit and its outflows (with :rigid,
  :counterpart and :shortfall as the record gives them); :inflows replace
  the record's when given (a residual where the record's are not
  measured). spec as series-module's, with :record."
  [{:keys [record inflows] :as spec}]
  (let [keep-keys [:var :counterpart :rigid :rigid-sd :shortfall]]
    (series-module (merge {:stock (keyword (slug (:name record))) :unit (:unit record)}
                          (dissoc spec :record)
                          {:inflows (mapv #(select-keys % keep-keys) (or inflows (:inflows record)))
                           :outflows (mapv #(select-keys % keep-keys) (:outflows record))}))))
