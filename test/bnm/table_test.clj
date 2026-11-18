(ns bnm.table-test
  "Pins bnm.table, bnm.cli and bnm.time's month indices to what the copies
  they replaced wrote and read."
  (:require [bnm.cli :as cli]
            [bnm.table :as tbl]
            [bnm.time :as t]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- tmp [ext] (.getPath (java.io.File/createTempFile "bnm-table" ext)))

(defn- written [content] (let [p (tmp ".csv")] (spit p content) p))

(deftest writes-a-stock
  (let [p (tmp ".csv")]
    (tbl/write-stock [{:iso "FRA" :year 2001 :variable "econ/x" :value 1 :known 2001 :data-year 2000}
                      {:iso "DEU" :year 2002 :variable "econ/y" :value -0.1234567 :known 2002 :data-year 2001}]
                     p)
    (is (= (str "iso,year,variable,value,known,data_year\n"
                "FRA,2001,econ/x,1.000000,2001,2000\n"
                "DEU,2002,econ/y,-0.123457,2002,2001\n")
           (slurp p))))
  (testing "parents made"
    (let [d (str (tmp ".d") "-dir/sub/stock.csv")]
      (tbl/write-stock [] d)
      (is (= "iso,year,variable,value,known,data_year\n" (slurp d))))))

(deftest writes-values
  (let [p (tmp ".csv")]
    (tbl/write-values p [{:iso "FRA" :year 2001 :variable "v" :value 0.5} {:iso "USA" :year 2002 :variable "w" :value nil}])
    (is (= "iso,year,variable,value\nFRA,2001,v,0.5\nUSA,2002,w,\n" (slurp p))))
  (let [p (tmp ".csv")]
    (tbl/write-csv! p "a,b" (fn [[a b]] (str a "," (format "%.6g" (double b)))) [["x" 1234567.0] ["y" 0.5]])
    (is (= "a,b\nx,1.23457e+06\ny,0.500000\n" (slurp p)))))

(deftest reads-maps
  (is (= [{:a "1" :b "x"} {:a "" :b ""} {:a "3" :b ""}]
         (tbl/read-csv-maps (written "a,b\n1,x\n,\n3,\n"))))
  (is (= [] (tbl/read-csv-maps (written "a,b\n")))))

(deftest reads-monthly
  (let [p (written "date,v,w\n2001-01-01,1.5,7\n2001-02-01,.,8\n2001-03-01, 2 ,\nbad,3,3\n2001-04\n")]
    (is (= {(+ (* 12 2001) 0) 1.5 (+ (* 12 2001) 2) 2.0} (tbl/read-monthly p)))
    (is (= {(+ (* 12 2001) 0) 7.0 (+ (* 12 2001) 1) 8.0} (tbl/read-monthly p 2)))))

(deftest reads-a-known-panel
  (let [p (written "iso,indicator,year,known,value\nFRA,x,2001,2002,1.5\nFRA,x,1999,2000,2\nDEU,y,2001,2003,-1\n")
        m (tbl/read-known-panel p "indicator" "year")]
    (is (= {"x" {"FRA" {1999 [2000 2.0] 2001 [2002 1.5]}} "y" {"DEU" {2001 [2003 -1.0]}}} m))
    (is (sorted? (get-in m ["x" "FRA"])))))

(deftest month-indices
  (is (= (+ (* 2008 12) 8) (t/month-index "2008-09-30")))
  (is (= (+ (* 2008 12) 8) (t/month-index "2008-09")))
  (is (= (+ (* 2008 12) 8) (t/month-index-compact "20080930")))
  (is (= (+ (* 2008 12) 8) (t/month-index-compact "200809"))))

(deftest command-line-options
  (is (= {:run "r1" :from "2003"} (cli/opts ["--run" "r1" "--from" "2003"])))
  (is (= {:run "r1"} (cli/opts ["--run" "r1" "--dangling"])) "an option without a value is dropped")
  (is (= {} (cli/opts []))))

(deftest say-prints-and-keeps
  (let [sb (StringBuilder.)
        say (cli/say-fn sb)
        out (with-out-str (say "a" 1 nil) (say))]
    (is (= "a 1 \n\n" out))
    (is (= "a 1 \n\n" (str sb))))
  (let [sb (StringBuilder.)
        out (with-out-str ((cli/say-fn sb {:flush false}) "b" 2.5))]
    (is (= "b 2.5\n" out (str sb)))))
