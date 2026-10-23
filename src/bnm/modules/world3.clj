(ns bnm.modules.world3
  "World3-03 (bnm.world3) split into engine modules (bnm.engine), one per
  sector: population (with its perceptions), capital, agriculture,
  nonrenewable resources, persistent pollution, and the indices (shares of
  output, human welfare, footprint). Each runs its own stocks and flows by
  Euler steps of dt 0.5 and reads the others' variables as declared, so
  the engine, stepping them in causal order, must reproduce bnm.world3's
  one-model run (docs/COUPLED-SYSTEMS.md section 7: the engine's first
  test, on a known answer).

  The sectors are coupled within the instant: capital's output feeds
  agriculture's investment, whose allocation feeds capital's. The engine
  orders them as system dynamics does: levels, then rates. A sector's
  :level emits are functions of stocks only (population, the resource
  fraction, pollution's index, land and its inputs, capital's output),
  observed first; then each sector's rates, reading levels and the rates
  of the sectors before it."
  (:require [bnm.stockflow :as sf]
            [bnm.world3 :as w3]))

(def ^:private cross-units
  "Units of the variables one sector reads from another (World3-03's)."
  {:pop "people" :p2 "people" :p3 "people" :le "years"
   :io "$/year" :iopc "$/person-year" :so "$/year" :sopc "$/person-year"
   :al "hectares" :uil "hectares" :aiph "$/hectare-year" :fioaa "fraction"
   :f "veg-equivalent kg/year" :fpc "veg-equivalent kg/person-year"
   :nrfr "fraction" :fcaor "fraction" :pcrum "resource units/person-year"
   :ppolx "dimensionless" :ppgr "pollution units/year"})

(def sector-specs
  "Per sector: its group of World3's parameters, its level emits, and its
  reads of the others ([variable phase], :level for what its levels need)."
  {:population {:params [:len :sfpc :hsid :iphst :rlt :pet :mtfn :lpd :zpgt :dcfsn :sad :ieat :fcest]
                :level [:pop :p2 :p3]
                :reads [[:fpc] [:sopc] [:iopc] [:ppolx]]}
   :capital {:params [:pyear :icor1 :icor2 :alic1 :alic2 :iet :iopcd :fioac1 :fioac2 :alsc1 :alsc2 :scor1 :scor2 :lfpf :lufdt]
             :level [:io :iopc :so :sopc]
             :reads [[:pop :level] [:fcaor :level] [:fioaa] [:aiph] [:al] [:p2] [:p3]]}
   :agriculture {:params [:pyear :sfpc :palt :lfh :pl :alai1 :alai2 :lyf1 :lyf2 :io70 :sd :alln :uildt :ilf :fspd]
                 :level [:al :aiph]
                 :reads [[:io] [:iopc] [:pop] [:ppolx]]}
   :resources {:params [:pyear :nri :nruf1 :nruf2]
               :level [:nrfr :fcaor]
               :reads [[:pop] [:iopc]]}
   :pollution {:params [:pyear :ppgf1 :ppgf21 :ppgf22 :swat :frpm :imef :imti :fipm :amti :pptd1 :pptd2 :ppol70 :ahl70]
               :level [:ppolx]
               :reads [[:pcrum] [:pop] [:aiph] [:al]]}
   :indices {:params [:oy :gdpu :rlgdp :rhgdp :tl :hup :hgha]
             :level []
             :reads [[:f] [:so] [:io] [:iopc] [:le] [:ppgr] [:al] [:uil]]}})

(defn- resolver
  "A lookup at time t of the sector's names: `params`, its stocks' `levels`
  (or their :init where no level is known yet: the start), its auxiliaries
  and flows computed once, the other sectors' through `foreign` (a name it
  did not declare an error)."
  [t params levels {:keys [stocks flows aux]} foreign]
  (let [fns (merge aux (update-vals flows :rate)
                   (into {} (for [[k {:keys [init]}] stocks :when (not (contains? levels k))]
                              [k (if (fn? init) init (constantly init))])))
        memo (atom {}) busy (atom #{})]
    (fn v [k]
      (cond (= k :t) t
            (contains? params k) (double (params k))
            (contains? levels k) (levels k)
            (contains? @memo k) (@memo k)
            (contains? fns k) (do (when (@busy k) (throw (ex-info (str "bnm.modules.world3: a cycle through " k) {:name k})))
                                  (swap! busy conj k)
                                  (let [x (try (double ((fns k) v)) (finally (swap! busy disj k)))]
                                    (swap! memo assoc k x)
                                    x))
            (contains? foreign k) (double (foreign k))
            :else (throw (ex-info (str "bnm.modules.world3: " k " is neither the sector's nor a declared read") {:name k}))))))

(defn- sector-module [id model {:keys [params level reads]} p units]
  (when (some :nonneg (vals (:stocks model)))
    (throw (ex-info "bnm.modules.world3: non-negative stocks are not supported" {:sector id})))
  (let [level (set level)
        names (concat (keys (:stocks model)) (keys (:flows model)) (keys (:aux model)))]
    {:module id :group :W :place :world :dt 0.5
     :doc (str "World3-03's " (name id) " sector (bnm.world3/sectors)")
     :reads (vec (for [[v ph] reads] (cond-> {:var v :unit (cross-units v)} ph (assoc :phase ph))))
     :emits (vec (for [k names] (cond-> {:var k :unit (or (cross-units k) (units k))} (level k) (assoc :phase :level))))
     :stocks (into {} (for [[k s] (:stocks model)] [k (select-keys s [:unit :info])]))
     :params (select-keys p params)
     :observe (fn [state in ps t]
                (let [v (resolver t ps (or (:levels state) {}) model in)]
                  (into {} (for [k level] [k (v k)]))))
     :step (fn [state in ps t dt]
             (let [v (resolver t ps (or (:levels state) {}) model in)
                   lv (into {} (for [k (keys (:stocks model))] [k (v k)]))
                   fl (into {} (for [k (keys (:flows model))] [k (v k)]))
                   ax (into {} (for [k (keys (:aux model))] [k (v k)]))
                   d (reduce (fn [d [k x]]
                               (let [{:keys [from to]} ((:flows model) k)]
                                 (cond-> d from (update from (fnil - 0.0) x) to (update to (fnil + 0.0) x))))
                             {} fl)]
               {:state {:levels (into {} (for [[k l] lv] [k (+ l (* dt (get d k 0.0)))]))}
                :emits (merge lv fl ax)}))}))

(defn modules
  "World3-03's sectors as engine modules (opts as bnm.world3/sectors';
  :params over World3-03's parameters)."
  ([] (modules {}))
  ([opts]
   (let [whole (w3/model (select-keys opts [:params :tables :inits :start]))
         p (:params whole)
         units (merge (update-vals (:stocks whole) :unit) (update-vals (:flows whole) :unit))]
     (vec (for [[id model] (w3/sectors opts)]
            (sector-module id model (sector-specs id) p units))))))
