(ns bnm.csv
  "A small RFC 4180 reader: one line at a time, so a multi-gigabyte file streams.
  Quoted fields may hold the separator and doubled quotes; a quoted field that
  spans lines is joined by `read-records`."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- quote-balanced? [^String line]
  (even? (count (filter #(= % \") line))))

(defn parse-line
  "The fields of one record `line`, split on the character `sep`."
  [^String line sep]
  (if-not (str/includes? line "\"")
    (str/split line (re-pattern (java.util.regex.Pattern/quote (str sep))) -1)
    (let [n (count line)]
      (loop [i 0 field (StringBuilder.) fields (transient []) quoted? false]
        (if (>= i n)
          (persistent! (conj! fields (str field)))
          (let [c (.charAt line i)]
            (cond
              quoted?
              (cond
                (and (= c \") (< (inc i) n) (= (.charAt line (inc i)) \"))
                (recur (+ i 2) (.append field \") fields true)
                (= c \") (recur (inc i) field fields false)
                :else (recur (inc i) (.append field c) fields true))
              (= c \") (recur (inc i) field fields true)
              (= c sep) (recur (inc i) (StringBuilder.) (conj! fields (str field)) false)
              :else (recur (inc i) (.append field c) fields false))))))))

(defn records
  "A lazy seq of field vectors from the lines of `rdr`, joining a quoted field
  that runs over a line break."
  [rdr sep]
  (letfn [(step [lines]
            (lazy-seq
             (when-let [[line & more] (seq lines)]
               (loop [line line more more]
                 (if (or (quote-balanced? line) (empty? more))
                   (cons (parse-line line sep) (step more))
                   (recur (str line "\n" (first more)) (rest more)))))))]
    (step (line-seq rdr))))

(defn read-maps
  "Every record of the file at `path` as a map keyed by its header row."
  [path sep]
  (with-open [r (io/reader path)]
    (let [[header & rows] (records r sep)
          ks (mapv keyword header)]
      (mapv #(zipmap ks %) (doall rows)))))
