(ns bnm.keen-test
  (:require [augur.math :as m]
            [bnm.keen :as keen]
            [bnm.stockflow :as sf]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- close? [a b tol] (< (Math/abs (- (double a) (double b))) tol))

;; ---------------------------------------------------------------------------
;; Goodwin (1967): with all profits invested (kappa(pi) = pi), no interest
;; and a linear Phillips curve the system is Lotka-Volterra, whose orbits
;; are closed: H = b w - a ln w + rho l - c ln l is conserved, with a =
;; 1/nu - (alpha + beta + delta), b = 1/nu, c = gamma + alpha.

(def goodwin-params {:alpha 0.02 :beta 0.01 :delta 0.01 :nu 3.0 :r 0.0 :gamma 0.5 :rho 0.55
                     :omega0 0.8 :lambda0 0.9 :d0 0.0})

(defn- goodwin-h [{:keys [alpha beta delta nu gamma rho]} w l]
  (let [a (- (/ 1.0 nu) (+ alpha beta delta)) b (/ 1.0 nu) c (+ gamma alpha)]
    (+ (* b w) (- (* a (Math/log w))) (* rho l) (- (* c (Math/log l))))))

(deftest goodwin-closed-orbit
  (let [mdl (keen/model {:form :goodwin :params goodwin-params})
        r (sf/run mdl {:from 0.0 :to 40.0 :dt 0.005})
        ws (get-in r [:stocks :omega]) ls (get-in r [:stocks :lambda]) ds (get-in r [:stocks :d])
        hs (map #(goodwin-h goodwin-params %1 %2) ws ls)
        h0 (first hs)]
    (testing "the conserved quantity stays put (Euler drift only)"
      (is (every? #(close? % h0 (* 2e-3 (Math/abs h0))) hs) (str "H from " h0 " to " (last hs))))
    (testing "it cycles: the wage share and employment oscillate without decay"
      (let [half (quot (count ws) 2)
            amp (fn [xs] (- (apply max xs) (apply min xs)))]
        (is (> (amp ws) 0.05))
        (is (close? (amp (take half ws)) (amp (drop half ws)) 0.01) "the same amplitude in both halves")))
    (testing "the orbit closes: it comes back near where it started"
      (let [start [(first ws) (first ls)]
            later (drop 1000 (map vector ws ls))   ; after five years
            dist (fn [[w l]] (Math/hypot (- w (start 0)) (- l (start 1))))]
        (is (< (apply min (map dist later)) 0.005))))
    (testing "no debt arises when all profits are invested"
      (is (every? #(close? % 0.0 1e-12) ds)))
    (testing "the equilibrium is the orbit's centre: w = 1 - nu (alpha + beta + delta), l = c / rho"
      (let [mean (fn [xs] (/ (reduce + xs) (count xs)))]
        ;; time averages of a Lotka-Volterra orbit equal the equilibrium
        (is (close? (mean ws) (- 1.0 (* 3.0 0.04)) 0.02))
        (is (close? (mean ls) (/ 0.52 0.55) 0.01))))))

;; ---------------------------------------------------------------------------
;; Keen (1995) in Grasselli & Costa Lima (2012): Phi(l) = phi1/(1-l)^2 -
;; phi0, kappa(pi) = kappa0 + kappa1 exp(kappa2 pi), their baseline.

(deftest gcl-reparameterisation-round-trips
  (let [p (keen/from-raw keen/gcl)
        back (keen/to-raw p)]
    (doseq [k [:phi0 :phi1 :kappa0 :kappa1 :kappa2]]
      (is (close? (back k) (keen/gcl k) (* 1e-9 (max 1.0 (Math/abs (keen/gcl k))))) (str k)))
    (testing "the equilibrium values G&CL report"
      (let [eq (keen/good-equilibrium p)]
        (is (close? (:lambda eq) 0.9686 5e-4) (str eq))
        (is (close? (:pi eq) 0.1618 5e-4))
        (is (close? (:d eq) 0.0697 2e-3))
        (is (close? (:omega eq) 0.8361 2e-3))))
    (testing "the functions at those values"
      (is (close? (keen/phillips p (- 1.0 (:ustar p))) 0.025 1e-12) "Phi(l*) = alpha")
      (is (close? (keen/investment p (:pistar p)) (* 3.0 0.055) 1e-12) "kappa(pi*) = nu (alpha + beta + delta)")
      (let [raw keen/gcl lam 0.9 pi 0.1]
        (is (close? (keen/phillips p lam) (- (/ (raw :phi1) (Math/pow (- 1 lam) 2)) (raw :phi0)) 1e-12))
        (is (close? (keen/investment p pi) (+ (raw :kappa0) (* (raw :kappa1) (Math/exp (* (raw :kappa2) pi))))
                    1e-12))))))

(defn- gcl-path
  "G&CL's baseline is stiff near full employment, where Euler steps would
  need to be impractically short: the model's field by RK4."
  [state years]
  (vec (keen/path (keen/from-raw keen/gcl) state {:years years :dt 0.01 :method :rk4})))

(def rk4 {:years 100.0 :dt 0.01 :method :rk4})

(deftest keen-good-equilibrium
  ;; G&CL Figs 3-4: from (w, l, d) = (0.75, 0.75, 0.1) a long boom in which
  ;; debt rises to near 2, then spirals into the good equilibrium
  (let [ps (gcl-path {:omega 0.75 :lambda 0.75 :d 0.1} 300.0)
        eq (keen/good-equilibrium (keen/from-raw keen/gcl))
        [_ w l d] (peek ps)]
    (is (close? w (:omega eq) 0.005) (str w))
    (is (close? l (:lambda eq) 0.005) (str l))
    (is (close? d (:d eq) 0.01) (str d))
    (is (< 1.5 (apply max (map #(nth % 3) ps)) 2.2) "debt peaks near 2 on the way (G&CL Fig. 4)")
    (is (keen/good-stable? (keen/from-raw keen/gcl)) "locally stable by Routh-Hurwitz, G&CL (69)")
    (is (nil? (keen/blowup-year (keen/from-raw keen/gcl) {:omega 0.75 :lambda 0.75 :d 0.1} rk4)))))

(deftest keen-bad-equilibrium
  ;; G&CL Fig. 5: from (0.75, 0.7, 0.1), employment a little lower, the same
  ;; economy goes to the bad equilibrium: w -> 0, l -> 0, d -> infinity,
  ;; which is stable since kappa0/nu - delta < r, G&CL (61), (70)
  (let [p (keen/from-raw keen/gcl)
        ps (gcl-path {:omega 0.75 :lambda 0.7 :d 0.1} 300.0)
        [_ w l d] (peek ps)]
    (is (> d 1000.0) (str d))
    (is (< w 0.01) (str w))
    (is (< l 0.01) (str l))
    (is (close? (- (/ (:kappa0 p) (:nu p)) (:delta p) (:r p)) -0.0421666 1e-6))
    (is (number? (keen/blowup-year p {:omega 0.75 :lambda 0.7 :d 0.1} rk4)))
    (testing "late on, debt rises while the profit share falls: the Minsky moment"
      (let [[_ w l d] (ps 15000)                     ; year 150
            rt (keen/rates p {:omega w :lambda l :d d})]
        (is (pos? (:dd rt)))
        (is (neg? (:dpi rt)))
        (is (keen/minsky? rt)))))
  (testing "the critical debt ratio separates the basins"
    (let [p (keen/from-raw keen/gcl)
          st {:omega 0.8 :lambda 0.9}
          dc (keen/critical-debt p st rk4)]
      (is (and dc (< 0.1 dc 8.0)) (str dc))
      (is (nil? (keen/blowup-year p (assoc st :d (* 0.9 dc)) rk4)))
      (is (number? (keen/blowup-year p (assoc st :d (* 1.1 dc)) rk4)))
      (testing "and a higher interest rate brings the bad basin closer"
        (let [dc' (keen/critical-debt (assoc p :r 0.06) st rk4)]
          (is (and dc' (< dc' dc)) (str dc " -> " dc')))))))

;; The bad basin is Keen's debt crisis: debt diverging. Employment can
;; collapse without it, as in a rentier economy whose low wage share and
;; unemployment far below its window's mean send the convex Phillips curve,
;; and the wage share, past 1 (Trinidad and Tobago's 2010 calibration).
;; Each is its own probability. (The wage share passing 1 does not by itself
;; mark a path as off the model: G&CL's own path to the bad equilibrium
;; passes it within two years.)

(def rentier
  (merge (keen/from-raw keen/gcl)
         {:alpha 0.0533 :beta 0.0027 :delta 0.0415 :nu 5.857 :r 0.02
          :ustar 0.1196 :phi-slope 1.756 :pistar 0.5472 :kappa-slope 2.503}))

(deftest debt-crisis-and-employment-collapse-are-separate
  (let [st {:omega 0.268 :lambda 0.954 :d 0.267}
        opts {:years 20.0 :dt 0.1}]
    (testing "employment collapses while debt stays bounded"
      (is (number? (keen/blowup-year rentier st (assoc opts :criterion :employment))))
      (is (< (apply max (map #(nth % 3) (keen/path rentier st opts))) (:d-max keen/diverge-defaults)))
      (is (nil? (keen/blowup-year rentier st opts)) "not a debt crisis (the default criterion)")
      (is (number? (keen/blowup-year rentier st (assoc opts :criterion :either))))
      (is (< (keen/blowup-year rentier st (assoc opts :criterion :wage))
             (keen/blowup-year rentier st (assoc opts :criterion :employment)))
          "the wage share passes 1 first"))

    (testing "G&CL's bad equilibrium is a debt crisis"
      (let [p (keen/from-raw keen/gcl) s {:omega 0.75 :lambda 0.7 :d 0.1}
            t (keen/blowup-year p s rk4)
            [_ _ _ d] (last (take-while #(<= (first %) t) (keen/path p s rk4)))]
        (is (number? t))
        (is (> d (:d-max keen/diverge-defaults)) "crossed on debt")
        (is (< (keen/blowup-year p s (assoc rk4 :criterion :employment)) t) "after employment collapsed")
        (is (< (keen/blowup-year p s (assoc rk4 :criterion :wage)) 2.0) "the wage share passes 1 early")))
    (testing "critical-debt follows the criterion"
      (is (nil? (keen/critical-debt rentier {:omega 0.268 :lambda 0.954} (assoc opts :d-hi 0.5)))
          "no debt in [0, 0.5] tips the rentier into a debt crisis")
      (is (= 0.0 (keen/critical-debt rentier {:omega 0.268 :lambda 0.954} (assoc opts :criterion :employment)))))))

;; ---------------------------------------------------------------------------
;; The engine's model and the fast path the diagnostics use agree.

(def moderate
  "A gentler economy than G&CL's: equilibrium unemployment 6%, slopes nearer
  what data show; its good equilibrium (0.76, 0.94, 2.0) is a slowly
  growing spiral, so a path from near it cycles for decades."
  (merge (keen/from-raw keen/gcl)
         {:alpha 0.02 :beta 0.01 :delta 0.05 :nu 3.0 :r 0.03 :ustar 0.06 :phi-slope 1.5 :pistar 0.18 :kappa-slope 1.0}))

(deftest the-fast-path-is-the-engine-s
  (let [p (merge moderate {:omega0 0.73 :lambda0 0.92 :d0 2.3})
        r (sf/run (keen/model {:params p}) {:from 0.0 :to 20.0 :dt 0.1})
        fast (vec (keen/path p {:omega 0.73 :lambda 0.92 :d 2.3} {:years 20.0 :dt 0.1}))]
    (is (= (count (:t r)) (count fast)))
    (doseq [[i k] [[1 :omega] [2 :lambda] [3 :d]]]
      (is (every? true? (map #(close? %1 (nth %2 i) 1e-9) (get-in r [:stocks k]) fast)) (str k)))
    (testing "rates are the engine's flows"
      (let [{:keys [flows]} (sf/at (keen/model {:params p}) 0.0)
            rt (keen/rates p {:omega 0.73 :lambda 0.92 :d 2.3})]
        (is (close? (:domega rt) (- (flows :wage-bargain) (flows :productivity)) 1e-12))
        (is (close? (:dlambda rt) (- (flows :hiring) (flows :labour-supply)) 1e-12))
        (is (close? (:dd rt) (- (flows :borrowing) (flows :dilution)) 1e-12))))))

;; ---------------------------------------------------------------------------
;; Calibration recovers the parameters behind a synthetic history.

(deftest calibration-recovers-synthetic-parameters
  (let [eq (keen/good-equilibrium moderate)
        truth (merge moderate {:omega0 (- (:omega eq) 0.03) :lambda0 (- (:lambda eq) 0.02) :d0 (+ (:d eq) 0.3)})
        r (sf/run (keen/model {:params truth}) {:from 0.0 :to 30.0 :dt 0.1})
        g (m/rng 7)
        at (fn [k t] (nth (get-in r [:stocks k]) (long (Math/round (/ t 0.1)))))
        obs (into (sorted-map)
                  (for [y (range 0 31)]
                    [(+ 1970 y) {:omega (+ (at :omega y) (* 0.003 (m/normal! g)))
                                 :lambda (+ (at :lambda y) (* 0.003 (m/normal! g)))
                                 :d (+ (at :d y) (* 0.01 (m/normal! g)))}]))
        exog (select-keys truth [:alpha :beta :delta :nu :r :kappa0])
        ;; at the ensemble size and number of ES-MDA iterations the run uses,
        ;; on two seeds (4 iterations recovered the slopes on one, not both)
        {:keys [members iterations]} keen/run-defaults]
    (doseq [seed [3 4]
            :let [cal (keen/calibrate-window obs exog {:members members :iterations iterations :seed seed :dt 0.1
                                                       :calibrate [:ustar :phi-slope :pistar :kappa-slope]
                                                       :noise {:omega 0.005 :lambda 0.005 :d 0.02}
                                                       :prior-shift {:ustar 0.01 :pistar 0.03 :phi-slope -0.5 :kappa-slope 0.5}})
                  post (:params cal)
                  lr (fn [k] (Math/abs (Math/log (/ (post k) (truth k)))))]]
      (testing (str "seed " seed)
        (is (< (lr :ustar) 0.2) (str "ustar " (:ustar post) " vs " (:ustar truth)))
        (is (close? (:pistar post) (:pistar truth) 0.015) (str "pistar " (:pistar post) " vs " (:pistar truth)))
        (is (< (lr :phi-slope) 0.35) (str "phi-slope " (:phi-slope post) " vs " (:phi-slope truth)))
        (is (< (lr :kappa-slope) 0.35) (str "kappa-slope " (:kappa-slope post) " vs " (:kappa-slope truth)))
        (is (= members (count (:members cal))))
        (is (every? #(contains? % :ustar) (:members cal)) "members in natural units, as parameter maps")))))

;; ---------------------------------------------------------------------------
;; Causality and output.

(defn- synthetic-panel
  "A panel from the G&CL model, 1960-2000, each data year known the next."
  []
  (let [p (merge moderate {:omega0 0.73 :lambda0 0.92 :d0 2.3})
        r (sf/run (keen/model {:params p}) {:from 1960.0 :to 2000.0 :dt 0.1})
        at (fn [k y] (nth (get-in r [:stocks k]) (long (Math/round (/ (- y 1960) 0.1)))))
        ser (fn [f] (into (sorted-map) (for [y (range 1960 2001)] [y [(inc y) (f y)]])))]
    {:omega (ser #(at :omega %)) :lambda (ser #(at :lambda %)) :d (ser #(at :d %))
     :r (ser (constantly 0.03)) :alpha (ser (constantly 0.02)) :beta (ser (constantly 0.01))
     :nu (ser (constantly 3.0))}))

(def fast {:members 16 :iterations 2 :window 15 :min-years 8 :every 5 :dt 0.1
           :years 20.0 :from 1970 :to 2001 :seed 1})

(deftest known-uses-no-data-year-at-or-after-the-origin
  (let [panel (assoc-in (synthetic-panel) [:omega 1985] [1995 0.5])]  ; revised late
    (doseq [y [1975 1990 1994 1995 2000]
            :let [k (keen/known panel y)]]
      (doseq [[v s] k [yr _] s]
        (is (< yr y) (str v " " yr " at " y)))
      (is (= (contains? (:omega k) 1985) (>= y 1995)) (str "known only once published, at " y)))))

(deftest rows-for-a-year-are-unchanged-by-later-data
  (let [panel (synthetic-panel)
        rows (keen/country-rows "SYN" panel fast)
        cut 1990
        future (reduce (fn [pn [v yr]] (assoc-in pn [v yr] [(inc yr) (* 3.0 (second (get-in pn [v yr])))]))
                       panel (for [v [:omega :lambda :d :r :alpha :beta :nu] yr (range cut 2001)] [v yr]))
        rows' (keen/country-rows "SYN" future fast)
        upto (fn [rs] (vec (filter #(<= (:year %) cut) rs)))]
    (is (seq (upto rows)))
    (is (= (upto rows) (upto rows')) "data years >= y cannot move year y")
    (testing "and the data are live: the year after does move"
      (is (not= (filter #(= (:year %) (inc cut)) rows) (filter #(= (:year %) (inc cut)) rows'))))
    (testing "the model's diagnostics appear once calibrated"
      (let [vs (set (map :variable (filter #(= 1995 (:year %)) rows)))]
        (doseq [v ["omega" "lambda" "d" "profit-share" "bad-basin-prob" "employment-collapse-prob" "wage-explosion-prob"
                   "minsky" "gap-d" "d-bar" "calibrated"]]
          (is (vs (str "keen/" v)) v))))))

;; Debt from one source at a time, chosen at each year end from what was
;; known then: the first source with :d-min-years known data years. Whether
;; a source will one day have that many cannot choose it earlier.

(defn- two-source-panel
  "The synthetic panel with debt from two sources: :d-a (preferred) from
  1977, :d-b (10% higher) from 1960, each data year known the next."
  []
  (let [p (synthetic-panel)
        d (:d p)]
    (-> (dissoc p :d)
        (assoc :d-a (into (sorted-map) (filter #(>= (key %) 1977) d))
               :d-b (update-vals d (fn [[a x]] [a (* 1.1 x)]))))))

(def two-source (assoc fast :d-sources [:d-a :d-b] :d-min-years 20))

(deftest debt-source-chosen-from-what-was-known
  (let [panel (two-source-panel)
        rows (keen/country-rows "SYN" panel two-source)
        at (fn [rs y v] (some #(when (and (= y (:year %)) (= (str "keen/" v) (:variable %))) (:value %)) rs))]
    (testing ":d-a has 20 known data years (1977-1996) from the end of 1997"
      (is (= 2.0 (at rows 1996 "d-source")))
      (is (= 1.0 (at rows 1997 "d-source")))
      (is (= 1.0 (at rows 2001 "d-source"))))
    (testing "the debt read is the chosen source's"
      (let [t (long (at rows 1996 "state-year"))]
        (is (close? (at rows 1996 "d") (second (get-in panel [:d-b t])) 1e-12)))
      (let [t (long (at rows 1997 "state-year"))]
        (is (close? (at rows 1997 "d") (second (get-in panel [:d-a t])) 1e-12))))
    (testing "a change of source recalibrates (between the every-5 origins)"
      (is (= 1995.0 (at rows 1996 "calibrated")))
      (is (= 1997.0 (at rows 1997 "calibrated"))))
    (testing "a source's later data cannot choose it earlier"
      (let [cut 1996
            short (update panel :d-a #(into (sorted-map) (filter (fn [e] (< (key e) cut)) %)))
            rows' (keen/country-rows "SYN" short two-source)
            upto (fn [rs] (vec (filter #(<= (:year %) cut) rs)))]
        (is (= (upto rows) (upto rows')))))))

(deftest output-schema
  (let [rows (keen/country-rows "SYN" (synthetic-panel) fast)
        lines (keen/csv-lines rows)]
    (is (= "iso,year,variable,value" (first lines)))
    (is (every? #(and (= "SYN" (:iso %)) (integer? (:year %)) (contains? keen/variables (:variable %))
                      (number? (:value %)) (keen/finite? (:value %)))
                rows))
    (is (= (count rows) (count (distinct (map (juxt :iso :year :variable) rows)))) "one value per iso, year and variable")
    (is (every? #(re-matches #"[A-Z]{3},\d{4},keen/[a-z][a-z\-]*,-?[0-9.Ee\-]+" %) (rest lines)) (str (take 3 (rest lines))))
    (is (every? #(re-matches #"keen/[a-z]+(-[a-z]+)*" %) keen/variables) "prefixed, kebab-case")
    (testing "probabilities and flags in range"
      (doseq [{:keys [variable value]} rows]
        (when (#{"keen/bad-basin-prob" "keen/employment-collapse-prob" "keen/wage-explosion-prob"} variable)
          (is (<= 0.0 value 1.0)))
        (when (#{"keen/minsky" "keen/minsky-observed" "keen/good-stable"} variable) (is (#{0.0 1.0} (double value))))))))

(deftest a-failed-calibration-is-reported-and-counted
  (let [failures (atom 0)
        err (java.io.StringWriter.)
        rows (with-redefs [keen/calibrate-window (fn [& _] (throw (ex-info "boom" {})))]
               (binding [*err* err]
                 (keen/country-rows "SYN" (synthetic-panel) (assoc fast :failures failures))))]
    (is (pos? @failures))
    (is (re-find #"SYN at 1970: boom" (str err)) (str err))
    (is (not-any? #(= "keen/calibrated" (:variable %)) rows) "no model without a calibration")))
