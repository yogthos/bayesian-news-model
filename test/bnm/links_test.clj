(ns bnm.links-test
  "bnm-56c.2: the weighting candidates are opt-in. The default run still
  reproduces t48-world-norel byte for byte (market crashes and food price
  shocks) on t48's own inputs, read from the
  first ledger issue's archive (data/ledger-inputs/2026-Q4/): the data on
  disk were rebuilt after it (bnm-8ul)."
  (:require [bnm.fast-run :as fr]
            [bnm.kinds :as kinds]
            [bnm.lattice :as lt]
            [bnm.ledger :as lg]
            [bnm.links :as links]
            [bnm.world-run :as wr]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def bench "data/runs/t48-world-norel")
(def archive "t48's inputs, as archived with the first ledger issue." "data/ledger-inputs/2026-Q4")

(defn- written
  "{file text} as bnm.world-run writes a kind's result."
  [kind r]
  (let [dir (str (System/getProperty "java.io.tmpdir") "/bnm-links-test-" kind)]
    (#'wr/write-kind dir kind r)
    (into {} (for [f ["forecasts.tsv" "preds.tsv" "weights.tsv"]] [f (slurp (str dir "/" f))]))))

(deftest the-default-run-is-the-base-of-record
  (if-not (and (.exists (io/file bench "market_crash" "forecasts.tsv")) (.exists (io/file archive)))
    (println "skipped: no" bench "or" archive)
    ;; two kinds: market crashes, which a group with nothing in it cost
    ;; most, and food price shocks, the 2020-2022 fold the candidates lose
    (doseq [kind ["market_crash" "food_price_shock"]]
      (let [end (+ (* 12 2025) 11)
            ;; no :flow-std-from: bnm-56c.33's fix is an option the base of
            ;; record sets, and the default is the code these issues ran on
            [r] (lg/recording #(lt/run (get (wr/build-specs [kind] end (kinds/catalogues)) kind))
                              (lg/redirect-to archive lg/inputs))
            got (written kind r)]
        (doseq [[f text] got]
          (testing (str kind "/" f)
            (is (= (slurp (io/file bench kind f)) text) (str kind "/" f " differs from " bench))))))))

(deftest set-merges-and-removes
  (is (= {:a 1 :c 3} (fr/with-set {:a 1 :b 2} {:b nil :c 3}))))

(deftest the-acceptance-rule
  ;; bnm-56c.2: the year nodes' Brier cost of a noise group, each draw less
  ;; its weighting's base; accepted when the mean is within 0.0003 of zero;
  ;; the interval is the v2 rule's (A3) for a fresh draw
  (let [{:keys [mean sd lo hi accept]} (links/noise-acceptance [0.0001 0.0002 0.0003 0.0002])]
    (is (< (Math/abs (- mean 0.0002)) 1e-12))
    (is (< (Math/abs (- sd (Math/sqrt (/ 0.00000002 3)))) 1e-12))
    ;; t(0.95, 3) = 2.3534
    (is (< (Math/abs (- hi (+ 0.0002 (* 2.3534 sd (Math/sqrt 1.25))))) 1e-7))
    (is (< lo mean hi))
    (is accept))
  (is (not (:accept (links/noise-acceptance [-0.0020 -0.0017 -0.0016 -0.0021]))))
  (is (not (:accept (links/noise-acceptance [0.0004 0.0005 0.0003 0.0006]))) "within of zero, either way"))

(deftest weight-dynamics
  ;; bnm-56c.2: how a node's weights move through time
  (is (< (Math/abs (- 1.0 (links/entropy {:a 0.5 :b 0.5}))) 1e-12) "even: one")
  (is (< (links/entropy {:a 0.999999 :b 0.000001}) 0.01) "selection: near zero")
  (is (= 0.0 (links/entropy {:a 1.0})))
  (is (< (Math/abs (- 1.0 (links/rank-agreement {:a 0.6 :b 0.3 :c 0.1} {:a 0.5 :b 0.4 :c 0.1}))) 1e-12))
  (is (< (Math/abs (- -1.0 (links/rank-agreement {:a 0.6 :b 0.3 :c 0.1} {:a 0.1 :b 0.3 :c 0.6}))) 1e-12))
  (testing "a summary over years"
    (let [{:keys [entropy top-changes agreement]} (links/dynamics [[2003 {:a 0.6 :b 0.4}] [2004 {:a 0.4 :b 0.6}] [2005 {:a 0.3 :b 0.7}]])]
      (is (= 1 top-changes))
      (is (< (Math/abs (- agreement 0.0)) 1e-12) "one swap, one agreement")
      (is (< 0.85 entropy 1.0))))
  (testing "shrinkage scales are read as the weights of a single model"
    (is (= {:scale/x 2.0 :scale/y 1.0} (links/node-weights {:all 1.0 :scale/x 2.0 :scale/y 1.0})))
    (is (= {:a 0.5 :b 0.5} (links/node-weights {:a 0.5 :b 0.5})))))

;; --- the fold rule, amended 18 November 2026 (docs/PHASE-CHANGES.md):
;; a fold with fewer than `min-fold-onsets` onsets cannot fail a candidate
;; on its own; the pooled score must still gain

(deftest a-thin-fold-cannot-veto
  (let [f (fn [lo hi pos] {:brier {:diff (/ (+ lo hi) 2) :lo lo :hi hi} :pos pos})
        ok {:pooled (f 0.0001 0.0010 82)}]
    (testing "a loss in a fold with enough onsets fails"
      (is (= :fail (:verdict (links/fold-verdict (assoc ok :folds [(f -0.002 -0.001 25) (f 0.0 0.001 29)]))))))
    (testing "the same loss in a fold of 0 onsets does not, and is named"
      (let [v (links/fold-verdict (assoc ok :folds [(f -0.002 -0.001 0) (f 0.0 0.001 29)]))]
        (is (= :pass (:verdict v)))
        (is (= 1 (count (:thin-losses v))))))
    (testing "4 onsets is thin, 5 is not"
      (is (= :pass (:verdict (links/fold-verdict (assoc ok :folds [(f -0.002 -0.001 4)])))))
      (is (= :fail (:verdict (links/fold-verdict (assoc ok :folds [(f -0.002 -0.001 5)]))))))
    (testing "the pooled score must still gain"
      (is (= :fail (:verdict (links/fold-verdict {:pooled (f -0.0003 0.0001 82) :folds [(f 0.0 0.001 29)]})))))))

;; --- amendment of 20 November 2026 (docs/PHASE-CHANGES.md): a fold loss
;; does not veto a candidate that gains on pooled Brier, the alert cost at
;; 4:1 and pooled AUC together (each point estimate above zero)

(deftest dominance-overrides-a-fold-loss
  (let [f (fn [lo hi pos] {:brier {:diff (/ (+ lo hi) 2) :lo lo :hi hi} :pos pos})
        lost (links/fold-verdict {:pooled (f -0.0076 0.0140 142) :folds [(f -0.0274 -0.0087 9) (f -0.0156 -0.0011 9)]})]
    (is (= :fail (:verdict lost)) "t124's fold verdict")
    (testing "pooled Brier, alert cost and AUC all gain: pass, the losses named"
      (let [v (links/dominance-verdict lost {:pooled 0.00223 :dcost 0.0777 :auc-gain 0.028})]
        (is (= :pass (:verdict v)))
        (is (= :dominance (:by v)))
        (is (= 2 (count (:losses v))))))
    (testing "any one of the three not gaining: the fold rule stands"
      (is (= :fail (:verdict (links/dominance-verdict lost {:pooled 0.00223 :dcost 0.0777 :auc-gain -0.001}))))
      (is (= :fail (:verdict (links/dominance-verdict lost {:pooled 0.00223 :dcost 0.0 :auc-gain 0.028}))))
      (is (= :fail (:verdict (links/dominance-verdict lost {:pooled -0.0001 :dcost 0.0777 :auc-gain 0.028})))))
    (testing "a fold-rule pass stays a pass"
      (let [ok (links/fold-verdict {:pooled (f 0.0001 0.0010 82) :folds [(f 0.0 0.001 29)]})]
        (is (= :pass (:verdict (links/dominance-verdict ok {:pooled 0.0005 :dcost -0.01 :auc-gain -0.01}))))
        (is (= :folds (:by (links/dominance-verdict ok {:pooled 0.0005 :dcost -0.01 :auc-gain -0.01}))))))))
