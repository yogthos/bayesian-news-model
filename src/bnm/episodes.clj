(ns bnm.episodes
  "The episode library (docs/PLAN.md 1.4): known breaks of a buffer, built
  by scripts/episodes.py into resources/bnm/episodes/. This namespace only
  reads it.

  - rules.csv: (catalogue, kind) -> family, candidate buffers, restoring
    flows, loss holder. The only place a family is assigned.
  - episodes.csv: one row per catalogue entry that is a break, with its
    episode id (family:place[:group]:yyyy-mm).
  - families.csv, hindcasts.csv, manifest.csv: counts, the plan's named
    hindcasts, and the inputs' hashes (the version).
  - mechanisms.csv: free text from the literature, never a model input.

  The panel of place-years with and without a break is data/derived/
  episodes/panel.csv (rebuilt, not versioned).

  Known dates: every row has known_date (when its rule's condition could
  first be seen; an investment bust only when the fall reaches 20%) and
  episode_known. The panel's known is when a place-year's label is final;
  a forecast at month t trains only on rows with known <= t. A 0 with
  ongoing set lies inside an earlier episode and is not a clean non-event.
  outcome-columns look past the break and are never model inputs."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def dir "resources/bnm/episodes")

(def ^:private field-re #"(?:^|,)(\"(?:[^\"]|\"\")*\"|[^,]*)")

(defn parse-line
  "The fields of one CSV line: quoted fields may hold commas and doubled
  quotes (not newlines: the library's files have none)."
  [line]
  (if (str/includes? line "\"")
    (mapv (fn [[_ f]]
            (if (str/starts-with? f "\"")
              (str/replace (subs f 1 (dec (count f))) "\"\"" "\"")
              f))
          (re-seq field-re line))
    (str/split line #"," -1)))

(defn parse-csv
  "Rows (vectors of strings) of CSV text, one row per line."
  [s]
  (mapv parse-line (remove str/blank? (str/split-lines s))))

(defn read-table
  "Maps keyed by header keyword of a CSV file."
  [path]
  (let [[h & rs] (parse-csv (slurp path))
        ks (mapv keyword h)]
    (mapv #(zipmap ks %) rs)))

(def outcome-columns
  "Episode columns that describe what happened after the break: never inputs."
  #{:end_date :followed_by :next_buffers :mechanism_note :mechanism_source})

(defn rules [] (read-table (str dir "/rules.csv")))
(defn episodes [] (read-table (str dir "/episodes.csv")))
(defn families [] (read-table (str dir "/families.csv")))

(defn rule-index
  "{[catalogue kind] rule}."
  [rs]
  (into {} (map (juxt (juxt :catalogue :kind) identity)) rs))

(defn by-family
  "{family #{episode id}}."
  [eps]
  (reduce (fn [m r] (update m (:family r) (fnil conj #{}) (:episode r))) {} eps))

(defn panel
  "Rows of data/derived/episodes/panel.csv, or nil when it has not been
  built. Its fields hold no commas or quotes, so lines are split plainly."
  []
  (let [p "data/derived/episodes/panel.csv"]
    (when (.exists (io/file p))
      (with-open [r (io/reader p)]
        (let [[h & ls] (line-seq r)
              ks (mapv keyword (str/split h #","))]
          (mapv #(zipmap ks (str/split % #"," -1)) ls))))))

(defn families-of-rules
  "The break families the rule table defines (not the '-' of outcomes)."
  [rs]
  (into (sorted-set) (remove #{"-"}) (map :family rs)))

(defn date? [s] (boolean (re-matches #"\d{4}-\d{2}-01" (or s ""))))

(defn split-list [s] (if (str/blank? s) [] (str/split s #";")))
