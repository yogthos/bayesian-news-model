(ns bnm.ontology
  "One vocabulary of events for every source.

  Event types are PLOVER's (the ontology behind POLECAT), which the LLM coder
  in newsroom also uses. The model works on coarser channels: six groups that
  behave differently enough to be worth separate dynamics.

  Actors are sorted into the sectors of `bnm.geo/sectors` from the type codes
  CAMEO-coded sources use and the sector names ICEWS uses."
  (:require [clojure.string :as str]))

(def event-types
  [:agree :consult :support :concede :cooperate :aid :retreat :request
   :accuse :reject :threaten :protest :sanction :mobilize :coerce :assault
   :other])

(def modes
  "Whether the event happened, or was only spoken of."
  [:did :intend :threaten :stop :negate])

(def channels
  [:verbal-coop :material-coop :verbal-conflict :protest :coercion :violence])

(def channel-index (zipmap channels (range)))

(def n-channels (count channels))

(def type->channel
  {:agree :verbal-coop :consult :verbal-coop :support :verbal-coop
   :request :verbal-coop
   :cooperate :material-coop :aid :material-coop :concede :material-coop
   :retreat :material-coop
   :accuse :verbal-conflict :reject :verbal-conflict :threaten :verbal-conflict
   :protest :protest
   :sanction :coercion :mobilize :coercion :coerce :coercion
   :assault :violence})

(defn channel-of
  "The channel index of an event type keyword, or nil for :other."
  [t]
  (some-> (type->channel t) channel-index))

;; --- CAMEO (GDELT, ICEWS, Phoenix) ---------------------------------------------

(def cameo-root->type
  {"01" :other "02" :request "03" :cooperate "04" :consult "05" :support
   "06" :cooperate "07" :aid "08" :concede "09" :other "10" :request
   "11" :accuse "12" :reject "13" :threaten "14" :protest "15" :mobilize
   "16" :sanction "17" :coerce "18" :assault "19" :assault "20" :assault})

(defn cameo-root
  "The two-digit root of a full CAMEO event code, whether written with its
  leading zero (\"043\") or without, as ICEWS stores it (\"43\", \"841\" for
  0841). A two-digit code is always a three-digit one that lost its zero, since
  full codes have at least three digits; GDELT's EventRootCode column is
  already a root and needs no call."
  [code]
  (let [n (count code)]
    (cond
      (< n 2) nil
      (= n 2) (str "0" (subs code 0 1))
      :else (let [two (subs code 0 2)
                  v (parse-long two)]
              (cond (nil? v) nil
                    (<= 1 v 20) two
                    ;; the leading zero was dropped
                    :else (str "0" (subs code 0 1)))))))

(defn cameo-event
  "[type mode] for a CAMEO code."
  [code]
  (let [root (cameo-root code)]
    [(get cameo-root->type root :other)
     (cond (= root "03") :intend
           (= root "13") :threaten
           :else :did)]))

;; --- PLOVER (POLECAT) ------------------------------------------------------------

(def plover-type
  (into {} (for [t event-types] [(clojure.string/upper-case (name t)) t])))

(def plover-mode
  {"" :did "NONE" :did "DID" :did "INTEND" :intend "THREATEN" :threaten "STOP" :stop
   "NEGATE" :negate "NOT" :negate "DENY" :negate "ACTIVE" :did})

;; --- ACLED ------------------------------------------------------------------------

(def acled-type
  {"Battles" :assault
   "Explosions/Remote violence" :assault
   "Violence against civilians" :assault
   "Protests" :protest
   "Riots" :protest})

(def acled-strategic
  {"Agreement" :agree
   "Arrests" :coerce
   "Change to group/activity" :mobilize
   "Disrupted weapons use" :coerce
   "Headquarters or base established" :mobilize
   "Looting/property destruction" :assault
   "Non-violent transfer of territory" :concede})

(defn acled-event [event-type sub-event-type]
  [(or (acled-type event-type)
       (acled-strategic sub-event-type)
       :other)
   :did])

;; --- actor sectors (see bnm.geo/sectors) ---------------------------------------------

(def cameo-sector
  "CAMEO actor type codes (GDELT's ActorType, POLECAT's sectors) by sector."
  {"GOV" "GOV" "LEG" "GOV" "JUD" "GOV" "ELI" "GOV" "PTY" "GOV" "SPY" "POL"
   "MIL" "MIL" "COP" "POL"
   "OPP" "OPP" "ACT" "OPP" "RAD" "OPP"
   "REB" "REB" "INS" "REB" "SEP" "REB" "UAF" "REB" "CRM" "REB" "MIL-NS" "REB"
   "ETH" "IDN" "REL" "IDN"
   "CVL" "SOC" "EDU" "SOC" "MED" "SOC" "JRN" "SOC" "HLH" "SOC" "LAB" "SOC" "REF" "SOC"
   "AGR" "SOC" "ENV" "SOC" "HRI" "SOC" "NGO" "SOC" "SET" "SOC"
   "BUS" "BUS" "MNC" "BUS"})

(defn sector-of-codes
  "The sector of an actor given CAMEO-style type codes, most specific first:
  arms and security before government, government before society."
  [codes]
  (let [ss (set (keep cameo-sector codes))]
    (some ss ["MIL" "POL" "REB" "OPP" "IDN" "GOV" "BUS" "SOC"])))

(def ^:private icews-patterns
  ;; checked in order: the first that matches any of an actor's sector names wins
  [["MIL" #"(?i)^military|armed forces|army|navy|air force"]
   ["POL" #"(?i)police|intelligence|internal security"]
   ["REB" #"(?i)insurgent|rebel|separatist|militia|criminal|gang|unidentified forces|terror|paramilitary"]
   ["OPP" #"(?i)dissident|protest|opposition|activist"]
   ["IDN" #"(?i)ethnic|religio|sunni|shia|muslim|christian|catholic|orthodox|jew|hindu|buddhist|protestant"]
   ["GOV" #"(?i)government|executive|legislat|parliament|judicial|cabinet|ministry|elite|part(y|ies)|court"]
   ["BUS" #"(?i)business|corporat|industr|commerc"]
   ["SOC" #"(?i)social|civilian|population|media|education|labor|labour|medical|health|refugee|nongovernmental|ngo|agricult"]])

(defn icews-sector
  "The sector of an ICEWS actor from its comma-separated sector names; nil
  for international actors (IGOs and the like)."
  [s]
  (when (and s (not (str/blank? s)) (not (re-find #"(?i)international|IGO|global" s)))
    (let [names (str/split s #",")]
      (some (fn [[sector re]] (when (some #(re-find re %) names) sector)) icews-patterns))))
