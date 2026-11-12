(ns bnm.world-term-test
  (:require [augur.math :as m]
            [bnm.lattice :as lt]
            [bnm.world-term :as wt]
            [clojure.test :refer [deftest is testing]]))

(deftest collinear-columns-are-dropped
  (is (= [0 2] (wt/independent-columns [[1.0 2.0 3.0 4.0] [3.0 5.0 7.0 9.0] [1.0 0.0 1.0 5.0]])) "2x+1 is the first again")
  (is (= [0 1] (wt/independent-columns [[1.0 2.0 3.0 4.0] [1.0 0.0 1.0 5.0] [3.0 2.0 5.0 14.0]])) "a sum of the two before")
  (is (= [1] (wt/independent-columns [[2.0 2.0 2.0] [1.0 0.0 3.0]])) "a constant carries nothing"))

;; A world momentum, the same at every place, raises every country's chance
;; of an onset in the year after the origin, and a common shock no gauge
;; sees moves every country together in each year (as food 2020-22 did):
;; the origins, not the rows, are the observations of a world effect. Rows
;; are learned two years late
(def months 480)
(def countries (mapv #(str (if (< % 6) "a" "b") %) (range 12)))
(def parent (merge (into {} (for [c countries] [c (str (first c))])) {"a" "W" "b" "W"}))
(def origins (vec (range 11 months 12)))

(defn- world [seed]
  (let [g (m/rng seed)
        blocks (vec (repeatedly (inc (quot months 24)) #(- (* 3.0 (m/next-double! g)) 1.5)))
        mom (mapv (fn [t] (+ (blocks (quot t 24)) (* 0.2 (m/normal! g)))) (range months))
        noise (mapv (fn [_] (m/normal! g)) (range months))
        common (mapv (fn [_] (* 0.8 (m/normal! g))) (range months))
        crisis (into {} (for [c countries]
                          [c (into {} (for [t origins]
                                        [t (< (m/next-double! g) (m/expit (+ -2.5 (* 1.2 (mom t)) (common t) (* 0.2 (m/normal! g)))))]))]))]
    {:mom mom :noise noise :crisis crisis :g g}))

(defn- target [w]
  (fn [_ place t]
    (let [cs (cond (= place "W") countries (> (count place) 1) [place] :else (filter #(= place (parent %)) countries))
          ys (keep #(get-in w [:crisis % t]) cs)]
      (when (and (seq ys) (< (+ t 12) months)) {:y (boolean (some true? ys)) :known-at (+ t 36)}))))

(defn- outcome [w]
  (fn [t] (when (< (+ t 12) months)
            {:share (/ (count (filter #(get-in w [:crisis % t]) countries)) (double (count countries)))
             :n (count countries) :known-at (+ t 36)})))

(defn- base-spec [w]
  {:scales [{:name :year :per 12 :horizon 12 :discount 0.998}]
   :levels [[:country countries] [:region ["a" "b"]] [:world ["W"]]]
   :parent parent :start 0 :end (dec months) :forget 0.995 :shrink 0.1
   :target (target w)
   :gauges (fn [_ _ t] [(nth (:noise w) (mod (* 7 t) months))])
   :gauge-width {:year 1}
   :state-from :reference :evidence-at :issue})

(defn- once
  "The world gauges `cols` (fn [t]) -> [values] entered once per origin:
  the world term, added to every node's offset."
  [sp w cols]
  (let [term (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins})]
    (assoc sp :world-offset (fn [_ _ t] {:x (:x (term t)) :reads [{:dir :world :origin t :known-at t}]}))))

(defn- replicated
  "The same gauges as columns on every row (step 2's entry)."
  [sp cols n]
  (assoc sp :world-gauges (fn [_ _ t] {:x (cols t) :reads []}) :world-width {:year n}))

(defn- brier [fs] (let [fs (filter #(some? (:y %)) fs)] (/ (reduce + (map (fn [{:keys [p y]}] (let [d (- (if y 1.0 0.0) p)] (* d d))) fs)) (count fs))))
(defn- late [r] (filter #(and (= 2 (count (:place %))) (> (:origin %) 240)) (:forecasts r)))

(deftest test-fix-3-a-world-signal-entered-once-per-origin
  (let [runs (vec (for [seed [31 32 33 34 35 36 37 38]
                        :let [w (world seed)
                              mom (fn [t] [((:mom w) t)])
                              mom+noise (fn [t] [((:mom w) t) ((:noise w) t)])
                              sp (base-spec w)]]
                    {:without (brier (late (lt/run sp)))
                     :once (brier (late (lt/run (once sp w mom))))
                     :once+noise (brier (late (lt/run (once sp w mom+noise))))
                     :rep (brier (late (lt/run (replicated sp mom 1))))
                     :term (wt/term-fn {:gauges mom :outcome (outcome w) :origins origins})
                     :w w}))
        avg (fn [f] (/ (reduce + (map f runs)) (count runs)))]
    ;; measured over these eight worlds: without 0.1310, once 0.1189, as a
    ;; column on every row 0.1247, the oracle about 0.112
    (testing "the country year node tracks the planted world signal through the term"
      (is (< (+ (avg :once) 0.008) (avg :without)) [(avg :once) (avg :without)]))
    (testing "better than the same gauge as a column on every row, under a common shock"
      (is (< (+ (avg :once) 0.003) (avg :rep)) [(avg :once) (avg :rep)]))
    (testing "the term follows the signal once some origins are known"
      (doseq [{:keys [term w]} runs]
        (let [ts (filter #(> % 240) origins)
              xs (map #(:x (term %)) ts) ys (map #((:mom w) %) ts)
              n (count ts) mx (/ (reduce + xs) n) my (/ (reduce + ys) n)
              c (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))]
          (is (> (/ c (Math/sqrt (* (reduce + (map #(let [d (- % mx)] (* d d)) xs))
                                    (reduce + (map #(let [d (- % my)] (* d d)) ys)))))
                 0.75)))))
    (testing "a world gauge that carries nothing costs little"
      (is (< (Math/abs (- (avg :once+noise) (avg :once))) 0.002) [(avg :once+noise) (avg :once)]))
    (testing "each origin's row is learned once: the term's rows are the origins known by t"
      (let [{:keys [term]} (first runs)]
        (is (= (count (filter #(<= (+ % 36) 300) (filter #(< (+ % 12) months) origins))) (:n (term 300))))))))

(deftest test-fix-3-a-collinear-duplicate-changes-nothing
  (let [w (world 41)
        sp (base-spec w)
        a (lt/run (once sp w (fn [t] [((:mom w) t)])))
        b (lt/run (once sp w (fn [t] [((:mom w) t) (+ 1.0 (* 2.0 ((:mom w) t)))])))]
    (is (= (map :p (:forecasts a)) (map :p (:forecasts b))))
    (testing "as columns on every row it does"
      (let [c (lt/run (replicated sp (fn [t] [((:mom w) t)]) 1))
            d (lt/run (replicated sp (fn [t] [((:mom w) t) (+ 1.0 (* 2.0 ((:mom w) t)))]) 2))]
        (is (not= (map :p (:forecasts c)) (map :p (:forecasts d))))))))

;; The lead test as the term's gate (bnm-56c.28): a world gauge enters a
;; kind's term only at origins where it has passed bnm.lead-test on the
;; origins whose outcomes are known by then, so the gate never looks ahead
;; and a gauge that carries nothing (the harness's noise) is dropped by the
;; same evidence that admits food momentum. Since bnm-56c.29 the test is
;; statistical: on these twelve countries' 25 online errors the registered
;; family (0.10 over 26 cells) admits neither gauge; one cell's test at 0.10
;; (family 1) admits momentum at about half the late origins, and is used
;; here for the term's mechanics
(deftest test-gate-a-gauge-enters-the-term-only-once-it-leads
  (let [w (world 31)
        gate (assoc wt/default-gate :h 1 :lags 1 :family 1)
        cols (fn [t] [((:mom w) t) ((:noise w) t)])
        both (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins :gate gate})
        registered (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins :gate (assoc wt/default-gate :h 1 :lags 1)})
        alone (wt/term-fn {:gauges (fn [t] [((:mom w) t)]) :outcome (outcome w) :origins origins :gate gate})
        ungated (wt/term-fn {:gauges cols :outcome (outcome w) :origins origins})
        late (filter #(> % 240) origins)
        share (fn [f j] (/ (count (filter #(some #{j} (:kept (f %))) late)) (double (count late))))]
    (testing "under the registered family nothing is admitted on 25 errors"
      (is (= 0.0 (share registered 0) (share registered 1))))
    (testing "one cell's test admits the planted momentum at the late origins"
      (is (> (share both 0) 0.5) (share both 0)))
    (testing "where the noise fails it changes nothing: it is dropped, not shrunk"
      (doseq [t late :when (= [0] (:kept (both t)))]
        (is (= (:x (alone t)) (:x (both t))))))
    (testing "on three online errors nothing passes and the term is 0"
      ;; at 107 six origins are known (11..71), so three online errors
      (is (= [] (:kept (both 107))))
      (is (= 0.0 (:x (both 107))))
      (is (= [0 1] (:kept (ungated 107))) "ungated, both enter"))
    (testing "the gate reads only the outcomes known at t"
      (let [t 300
            o (outcome w)
            flipped (fn [s] (let [r (o s)] (if (and r (> (:known-at r) t)) (assoc r :share (- 1.0 (:share r))) r)))]
        (is (= (both t) (wt/term-at {:gauges cols :outcome flipped :origins origins :gate gate} t)))))))
;; bnm-56c.29: the gate as a statistical test. The paired loss difference
;; (climatology's squared error less the fit's, each origin's fit on the rows
;; known by it) over the online errors known at t: one-sided Diebold-Mariano
;; with Newey-West variance and HLN's small-sample correction, t(n - 1), at
;; familywise 0.10 per origin, alpha/26 per kind and scale, Holm over a
;; cell's gauges. The old rule (online error at most 0.9 of climatology's on
;; five) is `old-pass` below, for contrast.

(defn- ar1 [g n phi sd]
  (vec (reductions (fn [x _] (+ (* phi x) (* sd (Math/sqrt (- 1.0 (* phi phi))) (m/normal! g))))
                   (* sd (m/normal! g)) (range (dec n)))))

(def ^:private g-origins (vec (range 11 (* 12 60) 12)))

(defn- gate-world
  "Sixty yearly origins; the year share over (t, t + 24] known 42 months
  later, persistent (AR 0.6) apart from what the gauges lead. :noise, two
  persistent gauges (AR 0.8) that carry nothing; :planted, a gauge leading
  the share; :burst, a gauge known from origin 30 that leads the share for
  `burst` origins and nothing after."
  [seed kind {:keys [burst] :or {burst 12}}]
  (let [g (m/rng seed)
        n (count g-origins)
        e (ar1 g n 0.6 0.5)
        a (ar1 g n 0.8 1.0) b (ar1 g n 0.8 1.0)
        lead (ar1 g n 0.5 1.0)
        gauge (mapv #(+ %1 (* 0.3 (m/normal! g))) lead)
        idx (fn [t] (quot t 12))
        latent (fn [i] (+ -1.5 (nth e i)
                          (case kind
                            :planted (* 0.8 (nth lead i))
                            :burst (if (<= 30 i (+ 29 burst)) (* 0.8 (nth lead i)) 0.0)
                            0.0)))]
    {:origins g-origins
     :outcome (fn [t] (let [i (idx t)] (when (< i (- n 2)) {:share (m/expit (latent i)) :n 150 :known-at (+ t 42)})))
     :gauges (fn [t] (let [i (idx t)]
                       (case kind
                         :noise [(nth a i) (nth b i)]
                         :planted [(nth gauge i)]
                         :burst [(when (>= i 30) (nth gauge i))])))}))

(def ^:private year-gate (assoc wt/default-gate :h 2 :lags 2))

(defn- old-pass
  "bnm-56c.28's gate: the lead test on what is known at t, passed at 0.9 of
  climatology's online error on at least five online errors."
  [{:keys [gauges outcome origins]} t j]
  (:pass (wt/lead-test {:gauge (fn [s] (nth (gauges s) j))
                        :rate (fn [s] (when-let [o (outcome s)] (when (<= (:known-at o) t) {:rate (:share o) :known-at (:known-at o)})))
                        :origins (filter #(<= % t) origins) :ratio 0.9 :min-online 5})))

(deftest test-gate-noise-is-admitted-at-most-at-the-level
  ;; 26 cells (13 kinds x 2 scales), each with two noise gauges on a
  ;; persistent share, over 40 draws: any admission in an origin's family
  ;; at most 10% of the time, at origins late enough for 40-odd errors
  (let [ts (map #(nth g-origins %) [40 48 56])
        fam (for [seed (range 40) t ts]
              (some #(seq (wt/passing (gate-world (+ 1000 (* 26 seed) %) :noise {}) year-gate t)) (range 26)))
        rate (/ (count (filter identity fam)) (double (count fam)))]
    (is (<= rate 0.10) rate)))

(deftest test-gate-a-planted-lead-is-admitted
  ;; the lead explains about 70% of the share's log-odds spread, as the
  ;; harness's planted column does. Alone in a cell at 0.10 it is admitted
  ;; almost always; under the family's 0.10/26 about half the time on 40-odd
  ;; errors, more as errors accrue (measured: 0.33, 0.40, 0.48 at origins
  ;; 44, 50, 56)
  (let [rate (fn [gate i] (/ (count (filter true? (for [seed (range 2000 2040)]
                                                     (= [0] (wt/passing (gate-world seed :planted {}) gate (nth g-origins i))))))
                             40.0))]
    (is (>= (rate (assoc year-gate :family 1) 50) 0.8) (rate (assoc year-gate :family 1) 50))
    (is (>= (rate year-gate 56) 0.35) (rate year-gate 56))
    (is (< (rate year-gate 44) (rate year-gate 56)) "power grows with the errors known")))

(deftest test-gate-a-short-burst-of-lead-is-not-admitted
  ;; food12 at market crashes: a gauge known from the 1990s whose first
  ;; rows lead, admitted in 2005-08 by five online errors at 0.9 of
  ;; climatology's. Just after the burst is known, and later
  (let [seeds (range 40)
        at (fn [i f] (/ (count (filter true? (for [s seeds] (boolean (f (gate-world (+ 3000 s) :burst {}) (nth g-origins i))))))
                        (double (count seeds))))
        new (fn [w t] (seq (wt/passing w year-gate t)))
        old (fn [w t] (old-pass w t 0))]
    (testing "the old rule lets it through on the burst's few errors"
      (is (>= (at 46 old) 0.3) (at 46 old)))
    (doseq [i [42 44 46 50 56]]
      (is (<= (at i new) 0.05) [i (at i new) (at i old)]))))

(deftest test-gate-reads-nothing-not-visible-at-t
  (let [w (gate-world 4001 :planted {})
        t (nth g-origins 50)
        o (:outcome w) gs (:gauges w)
        hidden (assoc w
                      :outcome (fn [s] (let [r (o s)] (if (and r (> (:known-at r) t)) (assoc r :share 0.99) r)))
                      :gauges (fn [s] (if (> s t) [1e6] (gs s))))]
    (is (= (wt/gate w year-gate t 0) (wt/gate hidden year-gate t 0)))
    (is (= (wt/passing w year-gate t) (wt/passing hidden year-gate t)))
    (testing "a row known exactly at t is read; one known a month later is not"
      (let [s (nth g-origins 45)
            at-t (fn [ka] (assoc w :outcome (fn [u] (let [r (o u)] (if (= u s) (assoc r :known-at ka :share 0.99) r)))))]
        (is (not= (:n-online (wt/gate w year-gate t 0)) nil))
        (is (= (:n-online (wt/gate (at-t t) year-gate t 0)) (inc (:n-online (wt/gate (at-t (inc t)) year-gate t 0)))))))))

(deftest test-gate-with-nothing-to-test-admits-nothing
  ;; a kind with no resolved outcome, a gauge never known, a share that
  ;; never moves, or gate options short of keys (bnm-56c.28's map): no error
  (let [w {:origins (vec (range 11 720 12))
           :outcome (fn [t] (when (< t 600) {:share 0.3 :n 10 :known-at (+ t 42)}))
           :gauges (fn [t] [(Math/sin t) nil])}]
    (is (= [] (wt/passing (assoc w :outcome (constantly nil)) year-gate 500)))
    (is (= [] (wt/passing (assoc w :gauges (fn [_] [nil nil])) year-gate 500)))
    (is (= [] (wt/passing w year-gate 500)))
    (is (= 1.0 (:p (wt/gate w year-gate 500 1))))
    (is (= 0 (:n-online (wt/gate w year-gate 11 0))))
    (doseq [g [{} {:ratio 0.9 :min-online 5} (dissoc year-gate :h :lags)]]
      (is (= [] (wt/passing w g 500)) g))
    (is (= [] (:kept (wt/term-at (assoc w :gate {:ratio 0.9 :min-online 5}) 500))))))
