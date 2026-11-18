(ns bnm.cli
  "What the tools' -main functions share: options from the command line and
  a report written to the terminal and kept."
  (:require [clojure.string :as str]))

(defn opts
  "{:name value} of command-line `args` given as --name value pairs."
  [args]
  (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v])))

(defn say-fn
  "A (fn [& xs]) that prints xs joined by spaces and appends the line to the
  StringBuilder `sb`; it flushes stdout after each line unless :flush is
  false."
  ([sb] (say-fn sb {:flush true}))
  ([sb {:keys [flush] :or {flush true}}]
   (fn [& xs]
     (let [s (str/join " " xs)]
       (println s)
       (when flush (clojure.core/flush))
       (.append sb (str s "\n"))))))
