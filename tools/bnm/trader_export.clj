(ns bnm.trader-export
  "Writes the trader's monthly market returns and inputs as a table, for
  models run outside Jolt (scripts/regime-hmm.py):
  data/runs/<name>/inputs.tsv with month, market return, bill return and
  every input in bnm.trader/all-input-names (blank when missing).

    jolt -A:tools -M -m bnm.trader-export [--name trader-inputs]"
  (:require [bnm.paths]
            [bnm.trader :as td]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn -main [& args]
  (let [{:keys [name]} (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        name (or name "trader-inputs")
        {:keys [fr months raw-inputs]} (td/setup)
        {:keys [start mkt rf]} fr
        f (if (= name "trader-inputs") bnm.paths/trader-inputs (str "data/runs/" name "/inputs.tsv"))]
    (io/make-parents f)
    (with-open [w (io/writer f)]
      (.write w (str (str/join "\t" (concat ["month" "mkt" "rf"] (map clojure.core/name td/all-input-names))) "\n"))
      (doseq [t months]
        (.write w (str (str/join "\t" (concat [t (mkt (- t start)) (rf (- t start))]
                                              (map #(if % % "") (raw-inputs t))))
                       "\n"))))
    (println "wrote" f)))
