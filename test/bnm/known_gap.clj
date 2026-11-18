(ns bnm.known-gap
  "Known gaps: assertions written first that the code does not meet yet.
  clojure.test has no expected-failure marker, so a gap is a plain function
  with `:known-gap` metadata (its reason), defined by `defgap`. run-tests
  does not run it (it has no :test). The runner runs every gap after the
  suite and prints each as OPEN (still failing, as recorded) or CLOSED (its
  assertions now pass). A closed gap fails the suite until it is turned back
  into a deftest, so a gap cannot pass silently, nor fail silently."
  (:require [clojure.test :as t]))

(defmacro defgap
  "A known gap `name` with `reason` (a string naming the bead and the
  record). The body holds the assertions as first written."
  [name reason & body]
  `(defn ~(vary-meta name assoc :known-gap reason) [] ~@body))

(defn gaps
  "The known-gap vars of namespaces `nss`, by name."
  [nss]
  (for [n nss
        v (sort-by #(str (:name (meta %))) (vals (ns-interns n)))
        :when (:known-gap (meta v))]
    v))

(defn run-gap
  "Run gap var `v` with its assertions counted, not reported to the suite."
  [v]
  (let [r (atom {:pass 0 :fail 0 :error 0 :msgs []})
        note (fn [k m] (swap! r #(-> % (update k inc)
                                     (update :msgs conj (select-keys m [:message :expected :actual])))))]
    (binding [t/report (fn [m] (case (:type m)
                                 :pass (swap! r update :pass inc)
                                 :fail (note :fail m)
                                 :error (note :error m)
                                 nil))]
      (try (v) (catch Throwable e (note :error {:message (str e)}))))
    (let [{:keys [fail error] :as res} @r]
      (assoc res :var v :reason (:known-gap (meta v))
             :status (if (pos? (+ fail error)) :open :closed)))))

(defn ok?
  "True when every gap is still open."
  [results]
  (every? #(= :open (:status %)) results))

(defn report!
  "Print each gap's status; return ok?."
  [results]
  (doseq [{:keys [var reason status pass fail error msgs]} results]
    (println (str "\nKNOWN GAP " (if (= :open status) "OPEN (expected failure)" "CLOSED (now passes: make it a deftest)")
                  " " (symbol var) ": " reason))
    (println (format "  %d passed, %d failed, %d errors" pass fail error))
    (doseq [{:keys [message actual]} msgs]
      (println "  " message (pr-str actual))))
  (println (format "\n%d known gaps: %d open, %d closed." (count results)
                   (count (filter #(= :open (:status %)) results))
                   (count (filter #(= :closed (:status %)) results))))
  (ok? results))
