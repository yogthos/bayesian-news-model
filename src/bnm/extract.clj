(ns bnm.extract
  "Coding news into events with an LLM: the prompt, and the reading of its
  answer. Pure: the host makes the call and parses the JSON (newsroom has
  `analysis/json-object`), and hands the parsed map to `events`.

  The codebook is PLOVER's, with a fixed label order and explicit boundary
  rules: LLM coders are sensitive to label order and wording, so both are
  pinned, and `prompt-version` changes whenever either does. The coder's
  output is noisy; the model treats it as one more source with its own
  exposure, never as ground truth."
  (:require [bnm.events]
            [bnm.geo :as geo]
            [clojure.string :as str]))

(def prompt-version "plover-1")

(def codebook
  "Event types in the order the prompt lists them, with their definitions."
  [[:agree "agreed to, signed or settled something (a deal, ceasefire, treaty)"]
   [:consult "met, called, visited or negotiated"]
   [:support "praised, endorsed or defended another"]
   [:request "asked, appealed or demanded something of another"]
   [:cooperate "cooperated in substance: joint operations, trade, intelligence sharing"]
   [:aid "gave economic, military, humanitarian or other aid"]
   [:concede "yielded: released prisoners, eased restrictions, accepted demands"]
   [:retreat "withdrew forces, demobilized, de-escalated militarily"]
   [:accuse "criticized, blamed, denounced or accused"]
   [:reject "refused, rejected or vetoed"]
   [:threaten "threatened force, sanctions or other harm"]
   [:protest "demonstrated, rallied, rioted or went on strike"]
   [:sanction "imposed sanctions, embargoes, expulsions, or cut ties"]
   [:mobilize "mobilized, deployed or exercised forces; put them on alert"]
   [:coerce "arrested, detained, seized, censored, repressed or blockaded"]
   [:assault "attacked, bombed, shelled, fought or killed"]])

(def modes
  [[:did "it happened"]
   [:intend "it was promised or planned"]
   [:threaten "it was threatened"]
   [:stop "it was halted or ended"]
   [:negate "it was denied, or the report says it did not happen"]])

(defn prompt
  "The prompt asking for every political event in `articles` (maps of
  :n :title :text :published) as JSON."
  [articles]
  (str
   "You code news into political events: who did what to whom, where, and when.\n\n"
   "Event types (use exactly these names):\n"
   (str/join "\n" (for [[t d] codebook] (str "- " (name t) ": " d)))
   "\n\nModes:\n"
   (str/join "\n" (for [[m d] modes] (str "- " (name m) ": " d)))
   "\n\nRules:\n"
   "- One event per distinct real-world action. Several reports of the same action are one event.\n"
   "- A threat to attack is `threaten` with mode `did`, not `assault`.\n"
   "- Code only events in the article's present, within about a week of publication. "
   "Set \"retrospective\": true for anything older (anniversaries, background, history).\n"
   "- Actors and target are places: ISO 3166 alpha-3 country codes of the actor's country "
   "(the state, its forces, its citizens or groups based there). Use null when unknown.\n"
   "- \"where\" is the country where it happened, as an alpha-3 code.\n"
   "- Skip statements of opinion with no action, sports, and business news without a political actor.\n\n"
   "Articles:\n\n"
   (str/join "\n\n" (for [{:keys [n title text published]} articles]
                      (str "[" n "] " title " (" published ")\n" text)))
   "\n\nAnswer with JSON only, in this shape:\n"
   "{\"events\": [{\"article\": 3, \"date\": \"2026-10-14\", \"actor\": \"ISR\", \"target\": \"LBN\", "
   "\"where\": \"LBN\", \"type\": \"assault\", \"mode\": \"did\", \"retrospective\": false, "
   "\"quote\": \"the words that report it\"}]}"))

(def ^:private type-names (into {} (for [[t _] codebook] [(name t) t])))
(def ^:private mode-names (into {} (for [[m _] modes] [(name m) m])))

(defn- place [s]
  (when (string? s)
    (let [s (str/trim s)]
      (or (geo/resolve-code (str/upper-case s)) (geo/resolve-name s)))))

(defn- date-of [s fallback]
  (let [d (some->> s str (re-find #"\d{4}-\d{2}-\d{2}"))]
    (if d (str/replace d "-" "") fallback)))

(defn events
  "Normalized events (see `bnm.events`) from the coder's parsed answer
  (a map with \"events\"). Unknown types, places and retrospectives are
  dropped; `default-date` (YYYYMMDD) stands in for a missing date."
  [answer default-date]
  (vec
   (for [e (get answer "events")
         :when (map? e)
         :let [t (type-names (some-> (get e "type") str str/lower-case))
               m (get mode-names (some-> (get e "mode") str str/lower-case) :did)
               [a1 a2] (bnm.events/actors (place (get e "actor")) (place (get e "target"))
                                          (place (get e "where")))]
         :when (and t a1 (not (true? (get e "retrospective"))))]
     {:date (date-of (get e "date") default-date)
      :a1 a1 :a2 a2 :type t :mode m :n 1
      :article (get e "article")
      :quote (get e "quote")})))
