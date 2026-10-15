(ns bnm.bins
  "Compacts a source's normalized files into one weekly file the trainer
  streams through: data/bins/<source>.tsv, records of
  bin a1 a2 channel n, sorted by bin.

    jolt -A:tools -M -m bnm.bins gdelt [bin-days]"
  (:require [bnm.events :as ev]
            [bnm.time :as t]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- flush-bins! [^java.io.Writer w ^java.util.TreeMap bins below]
  ;; write and drop every bin below `below`
  (loop []
    (when-let [e (.firstEntry bins)]
      (when (< (.getKey e) below)
        (let [^java.util.HashMap m (.getValue e)]
          (doseq [k (sort (keys m))]
            (.write w (str (.getKey e) "\t" k "\t" (.get m k) "\n"))))
        (.remove bins (.getKey e))
        (recur)))))

(defn -main [source & [bin-days]]
  (let [bin-days (parse-long (or bin-days "7"))
        files (->> (.listFiles (io/file "data/norm" source))
                   (map #(.getPath %))
                   (filter #(re-find #"/\d[^/]*\.tsv$" %))
                   sort)
        out (str "data/bins/" source ".tsv")
        bins (java.util.TreeMap.)]
    (io/make-parents out)
    (with-open [w (io/writer (str out ".unsorted"))]
      (doseq [f files]
        (with-open [r (io/reader f)]
          (doseq [line (line-seq r)]
            (let [{:keys [date a1 a2 n] :as e} (ev/parse-line line)
                  c (ev/channel e)]
              (when c
                (let [b (t/bin-of (t/day date) bin-days)
                      k (str a1 "\t" (or a2 "") "\t" c)
                      ^java.util.HashMap m (or (.get bins b)
                                               (let [m (java.util.HashMap.)] (.put bins b m) m))]
                  (.put m k (+ n (or (.get m k) 0.0))))))))
        ;; files are in date order; keep a margin for stragglers
        (when-let [first-key (some-> (.lastEntry bins) .getKey)]
          (flush-bins! w bins (- first-key 8)))
        (println "bins" f) (flush))
      (flush-bins! w bins Long/MAX_VALUE))
    ;; merge any bin written twice and sort
    (p/shell "sh" "-c" (str "sort -t'\t' -k1,1n -k2,2 -k3,3 -k4,4n -S 1G '" out ".unsorted' | "
                            "awk -F'\\t' 'BEGIN{OFS=\"\\t\"} {k=$1 FS $2 FS $3 FS $4; if (k==p) s+=$5; else {if (NR>1) print p, s; p=k; s=$5}} END{if (NR) print p, s}' > '" out "'"))
    (io/delete-file (str out ".unsorted"))
    (println "wrote" out)))
