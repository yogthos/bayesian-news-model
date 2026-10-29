(ns bnm.sfc-rates-confirm-test
  (:require [bnm.sfc-rates-confirm :as c]
            [augur.math]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

;; Synthetic vintages only: nothing here reads data/.

(defn- tmp-dir []
  (doto (io/file (System/getProperty "java.io.tmpdir") (str "bnm-confirm-" (System/nanoTime))) .mkdirs))

(def ^:private sectors [:hh :nfc :fin :gov])

(defn- frozen-rows
  "Frozen rows like rates-freeze's: each country's origin o, horizons 1 to
  5, the four sectors' net lending and private debt; :f from truth `d`
  {[iso h outcome] value} and errors `e`, rates' error `k` times
  estimated's."
  [isos o d e k]
  (vec (for [iso isos hz (range 1 6) out (concat (for [s sectors] [:nl s]) [[:debt]])
             :let [t (d [iso hz out]) x (e [iso hz out])]]
         {:iso iso :o o :h hz :outcome out :data nil :split :frozen
          :f {:persistence (+ t (* 2.0 x)) :damped (+ t (* 1.5 x)) :rates (+ t (* k x))
              :estimated (+ t x) :restart (+ t (* 3.0 x))}})))

(defn- world
  "Truth and estimated's errors for `isos` from generator seed `seed`."
  [isos seed]
  (let [g (augur.math/rng seed)
        ks (for [iso isos hz (range 1 6) out (concat (for [s sectors] [:nl s]) [[:debt]])] [iso hz out])]
    [(into {} (for [k ks] [k (* 0.02 (augur.math/normal! g))]))
     (into {} (for [k ks] [k (* 0.01 (augur.math/normal! g))]))]))

(defn- write-vintage!
  "panel.csv, panel-holdout.csv and panel-holdout2.csv in `dir` with
  targets `d` for the countries `isos` (origin o), output 100: net
  lending in every year from o + 1 to `last`, measured stocks too."
  [dir isos o d last]
  (let [hdr "iso,year,y,flows_ok,stocks_source,hh_nl,nfc_nl,fin_nl,gov_nl,hh_debt_l,nfc_debt_l"
        line (fn [iso y]
               (let [hz (- y o) v (fn [out] (* 100.0 (d [iso hz out])))]
                 (str/join "," [iso y 100.0 "True" "oecd-t720" (v [:nl :hh]) (v [:nl :nfc]) (v [:nl :fin]) (v [:nl :gov])
                                (v [:debt]) 0.0])))
        [a b] (split-at (max 0 (- (count isos) 1)) isos)]
    (spit (io/file dir "panel.csv") (str/join "\n" (cons hdr (for [iso a y (range (inc o) (inc last))] (line iso y)))))
    (spit (io/file dir "panel-holdout.csv") (str hdr "\n"))
    (spit (io/file dir "panel-holdout2.csv") (str/join "\n" (cons hdr (for [iso b y (range (inc o) (inc last))] (line iso y)))))
    dir))

(defn- write-frozen! [dir rows]
  (let [f (io/file dir "rates-frozen.edn")]
    (spit f (with-out-str (clojure.pprint/pprint rows)))
    (str f)))

(def ^:private null {[:h1 :nl] [-0.02 0.02] [:h1 :debt] [-0.02 0.02] [:h35 :nl] [-0.03 0.03] [:h35 :debt] [-0.03 0.03]})

(defn- run
  "The scorer end to end on a synthetic vintage: n countries, targets to
  `last`, rates' error k times estimated's. Returns score's result."
  [{:keys [n k last seed] :or {last 2025 seed 1}}]
  (let [dir (tmp-dir)
        isos (vec (for [i (range n)] (format "C%02d" i)))
        [d e] (world isos seed)
        fp (write-frozen! dir (frozen-rows isos 2024 d e k))
        sha (c/sha256-file fp)]
    (write-vintage! dir isos 2024 d last)
    (let [sh (io/file dir "shape.edn")]
      (c/write-shape! fp sha (str dir) "2027-11-01" (str sh))
      (c/score fp sha (str dir) "2027-11-01" (str sh) null))))

(deftest sha-256-matches-the-standard-vectors
  (is (= "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" (c/sha256-hex (.getBytes "" "UTF-8"))))
  (is (= "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" (c/sha256-hex (.getBytes "abc" "UTF-8"))))
  (is (= "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
         (c/sha256-hex (.getBytes "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" "UTF-8"))))
  (is (= "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
         (c/sha256-hex (.getBytes (apply str (repeat 1000000 "a")) "UTF-8")))
      "a million bytes, many blocks"))

(deftest the-registered-constants-are-9h-and-the-amendment
  (is (= "0339880a359941334046d651966facd9d2473811fd205c77143111997eb7ccaf" c/frozen-sha256))
  (is (= #{["KOR" 2024 1 [:debt]] ["NZL" 2023 1 [:debt]]} c/excluded-rows))
  (is (= #{"BGR" "ROU"} c/excluded-countries))
  (is (= 5 c/min-countries))
  (is (= 0.05 c/delta))
  (is (= "2027-11-01" c/vintage-from)))

(deftest a-tampered-frozen-file-is-refused
  (let [dir (tmp-dir)
        isos ["C00" "C01"]
        [d e] (world isos 3)
        fp (write-frozen! dir (frozen-rows isos 2024 d e 1.0))
        sha (c/sha256-file fp)]
    (is (= 50 (count (c/read-frozen fp sha))) "the untouched file reads")
    (spit fp (str/replace-first (slurp fp) ":o 2024" ":o 2023"))
    (is (thrown-with-msg? Exception #"SHA-256" (c/read-frozen fp sha)) "a changed byte is refused")
    (is (thrown-with-msg? Exception #"SHA-256" (c/score fp sha (str dir) "2027-11-01" "nope" null))
        "score refuses before reading anything else")
    (is (thrown-with-msg? Exception #"SHA-256" (c/read-frozen fp c/frozen-sha256)))))

(deftest a-vintage-before-the-registered-date-is-refused
  (is (thrown-with-msg? Exception #"2027-11-01" (c/check-date "2027-10-31")))
  (is (thrown-with-msg? Exception #"date" (c/check-date "1 Nov 2027")))
  (is (= "2027-11-01" (c/check-date "2027-11-01")))
  (is (= "2028-01-15" (c/check-date "2028-01-15"))))

(deftest published-targets-are-dated-by-flows-for-net-lending-and-stocks-for-debt
  ;; flows end 2023, stocks run to 2024 (as Korea's and New Zealand's)
  (let [ob (fn [y flows] (cond-> {:y 100.0 :flows-ok flows :stocks-source :oecd-t720
                                  :b {:hh {:debt {:l 50.0}} :nfc {:debt {:l 10.0}}}}
                           flows (assoc :f {:hh {:nl 1.0} :nfc {:nl 1.0} :fin {:nl 1.0} :gov {:nl 1.0}})))
        panel {"NZX" {2022 (ob 2022 true) 2023 (ob 2023 true) 2024 (ob 2024 false)}}
        rows (for [hz (range 1 6) out [[:nl :hh] [:debt]]] {:iso "NZX" :o 2023 :h hz :outcome out})]
    (is (= [["NZX" 2023 1 [:debt]]] (mapv c/row-key (c/published-at-freeze rows panel))))))

(deftest data-is-rebuilt-from-the-vintage
  (let [dir (tmp-dir)
        isos ["C00" "C01" "C02"]
        [d e] (world isos 5)
        rows (frozen-rows isos 2024 d e 1.0)]
    (write-vintage! dir (take 2 isos) 2024 d 2025)
    (let [vp (c/vintage-panel (str dir))
          with (c/attach-data rows vp)
          at (fn [iso hz out] (:data (first (filter #(and (= iso (:iso %)) (= hz (:h %)) (= out (:outcome %))) with))))]
      (is (< (Math/abs (- (at "C00" 1 [:nl :gov]) (d ["C00" 1 [:nl :gov]]))) 1e-12))
      (is (< (Math/abs (- (at "C01" 1 [:debt]) (d ["C01" 1 [:debt]]))) 1e-12) "from panel-holdout2.csv")
      (is (nil? (at "C00" 2 [:nl :gov])) "a target year the vintage lacks")
      (is (nil? (at "C02" 1 [:nl :gov])) "a country the vintage lacks")
      (is (= ["C02"] (c/absent with))))))

(deftest the-cells-drop-h2-h4-and-the-excluded-rows
  (let [mk (fn [iso o hz out] {:iso iso :o o :h hz :outcome out :data 0.1
                               :f {:rates 0.1 :estimated 0.1 :persistence 0.1 :damped 0.1 :restart 0.1}})
        rows [(mk "AAA" 2024 1 [:nl :hh]) (mk "AAA" 2024 2 [:nl :hh]) (mk "AAA" 2024 4 [:debt])
              (mk "AAA" 2024 3 [:debt]) (mk "AAA" 2024 5 [:nl :gov])
              (mk "BGR" 2022 1 [:nl :hh]) (mk "ROU" 2025 1 [:debt])
              (mk "KOR" 2024 1 [:debt]) (mk "KOR" 2024 1 [:nl :hh]) (mk "NZL" 2023 1 [:debt])
              (assoc (mk "CCC" 2024 1 [:nl :hh]) :data nil)
              (update (mk "DDD" 2024 1 [:nl :hh]) :f dissoc :restart)]
        kept (c/scorable rows)]
    (is (= #{["AAA" 1 [:nl :hh]] ["AAA" 3 [:debt]] ["AAA" 5 [:nl :gov]] ["KOR" 1 [:nl :hh]]}
           (set (map (juxt :iso :h :outcome) kept))))
    (is (= {[:h1 :nl] 2 [:h35 :debt] 1 [:h35 :nl] 1} (frequencies (map c/cell-of kept))))))

(deftest cell-and-overall-verdicts-follow-the-amendment
  (is (= :too-few (c/cell-verdict [0.5 0.4 0.6] 4)))
  (is (= :non-inferior (c/cell-verdict [0.0 -0.04 0.04] 5)))
  (is (= :inferior (c/cell-verdict [-0.2 -0.3 -0.06] 5)))
  (is (= :inconclusive (c/cell-verdict [-0.05 -0.07 -0.03] 5)))
  (let [ni :non-inferior]
    (is (= :non-inferior (c/overall {[:h1 :nl] ni [:h1 :debt] ni [:h35 :nl] :too-few [:h35 :debt] :too-few})))
    (is (= :non-inferior (c/overall {[:h1 :nl] ni [:h1 :debt] ni [:h35 :nl] ni [:h35 :debt] ni})))
    (is (= :inconclusive (c/overall {[:h1 :nl] ni [:h1 :debt] ni [:h35 :nl] :inconclusive [:h35 :debt] :too-few})))
    (is (= :inconclusive (c/overall {[:h1 :nl] ni [:h1 :debt] :too-few [:h35 :nl] ni [:h35 :debt] ni}))
        "an h1 cell too few: never non-inferior")
    (is (= :inferior (c/overall {[:h1 :nl] :too-few [:h1 :debt] :too-few [:h35 :nl] :inferior [:h35 :debt] :too-few})))
    (is (= :inconclusive (c/overall {[:h1 :nl] :too-few [:h1 :debt] :too-few [:h35 :nl] :too-few [:h35 :debt] :too-few}))
        "always defined")))

(deftest a-planted-gain-passes
  (let [{:keys [overall cells lines]} (run {:n 12 :k 0.7})]
    (is (= :non-inferior overall))
    (is (= :non-inferior (get-in cells [[:h1 :nl] :verdict])))
    (is (= :non-inferior (get-in cells [[:h1 :debt] :verdict])))
    (is (= 12 (get-in cells [[:h1 :nl] :countries])) "targets to 2025: h1 only")
    (is (= :too-few (get-in cells [[:h35 :nl] :verdict])) "no h3-5 target published")
    (is (some #(re-find #"verdict: non-inferior" %) lines))
    (is (some #(re-find #"rests on: h1 nl, h1 debt" %) lines))))

(deftest a-null-at-the-margin-does-not-pass
  ;; rates' errors sqrt(1.05) times estimated's: a skill of exactly -0.05
  (let [{:keys [overall cells]} (run {:n 12 :k (Math/sqrt 1.05)})]
    (is (< (Math/abs (+ 0.05 (first (get-in cells [[:h1 :nl] :ci])))) 1e-9))
    (is (not= :non-inferior overall))
    (is (= :inconclusive overall)))
  (is (= :inferior (:overall (run {:n 12 :k (Math/sqrt 1.3)}))) "a planted loss is inferior"))

(deftest too-few-countries-is-inconclusive
  (let [{:keys [overall cells lines]} (run {:n 4 :k 0.7})]
    (is (= :too-few (get-in cells [[:h1 :nl] :verdict])))
    (is (= :inconclusive overall))
    (is (some #(re-find #"too few" %) lines))))

(deftest score-refuses-a-shape-the-null-was-not-run-on
  (let [dir (tmp-dir)
        isos (vec (for [i (range 6)] (format "C%02d" i)))
        [d e] (world isos 9)
        fp (write-frozen! dir (frozen-rows isos 2024 d e 0.7))
        sha (c/sha256-file fp)
        sh (io/file dir "shape.edn")]
    (write-vintage! dir isos 2024 d 2025)
    (c/write-shape! fp sha (str dir) "2027-11-01" (str sh))
    (is (= :non-inferior (:overall (c/score fp sha (str dir) "2027-11-01" (str sh) null))))
    (write-vintage! dir isos 2024 d 2027)
    (is (thrown-with-msg? Exception #"shape" (c/score fp sha (str dir) "2027-11-01" (str sh) null))
        "the vintage changed after the null was simulated")))
