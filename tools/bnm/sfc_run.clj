(ns bnm.sfc-run
  "Runs and validates the stock-flow consistent core (bnm.sfc,
  bnm.modules.sfc; bead bnm-4hv.10) on scripts/sfc-data.py's panel and
  keen-run's keen.csv, and writes data/derived/sfc/:

    validation.txt   the one-year-ahead fit of sector net lending against
                     persistence (design origins to 2002, test from 2003;
                     measured and imputed stocks apart), the identities in
                     simulation, the named checks (China's household
                     surplus against its corporate deficit; the US 2009
                     swing given the crisis), Keen's quantities from the
                     frame against keen.csv's
    one-year-ahead.csv  iso, origin, year, source, sector, model,
                     persistence, data
    state.csv        the coupled table (bnm.engine's columns) of a free
                     engine run per country from its last measured year
                     less ten, ten years, numeric emits only

    jolt -A:tools -M -m bnm.sfc-run"
  (:require [bnm.engine :as eng]
            [bnm.modules.sfc :as msfc]
            [bnm.sfc :as sfc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def panel-path "data/derived/sfc/panel.csv")
(def keen-path "data/derived/stocks/keen.csv")
(def out-dir "data/derived/sfc")

(defn- f4 [x] (if (number? x) (format "%.4f" (double x)) "-"))
(defn- f3 [x] (if (number? x) (format "%.3f" (double x)) "-"))
(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))
(defn- corr [ps]
  (let [ps (filter (fn [[a b]] (and (number? a) (number? b))) ps)]
    (when (> (count ps) 2)
      (let [ma (mean (map first ps)) mb (mean (map second ps))
            c (reduce + 0.0 (map (fn [[a b]] (* (- a ma) (- b mb))) ps))
            va (reduce + 0.0 (map (fn [[a _]] (* (- a ma) (- a ma))) ps))
            vb (reduce + 0.0 (map (fn [[_ b]] (* (- b mb) (- b mb))) ps))]
        (when (and (pos? va) (pos? vb)) (/ c (Math/sqrt (* va vb))))))))

(defn- score-lines
  ([title rows] (score-lines title rows :model))
  ([title rows k]
  (let [s (sfc/score rows sfc/domestic k)]
    (concat [(str title (when (= k :model-ic) ", intercept-corrected") " (n " (get-in s [:all :n]) " forecasts, " (count (distinct (map :iso (filter k rows)))) " countries)")
             "  sector  model MAE  persistence MAE  skill   model nearer"]
            (for [k (concat sfc/sectors [:all])
                  :let [{:keys [model persistence wins]} (s k)]]
              (format "  %-6s  %9s  %15s  %6s  %s" (name k) (f4 model) (f4 persistence)
                      (if (and model persistence (pos? persistence)) (f3 (- 1.0 (/ model persistence))) "-")
                      (if wins (format "%.0f%%" (* 100 wins)) "-")))))))

;; -- identities in simulation ---------------------------------------------------------

(defn- free-run
  "The three modules for `iso` started at data year `origin`, run `n` years
  by the engine. [modules result]."
  [panel kn iso origin n]
  (let [p (sfc/learn panel kn iso origin {})
        st (sfc/init panel kn iso origin p)
        ms (msfc/modules iso st p)]
    [ms (eng/run ms {:from (inc origin) :to (+ origin n)}) st p]))

(defn- max-of [xs] (reduce max 0.0 (keep #(when (number? %) (Math/abs (double %))) xs)))

;; -- named checks ----------------------------------------------------------------------

(defn- us-2009
  "Given the crisis: learned to 2008, started from 2008, 2009's private
  investment (NFC, household, financial) set to the data's, the rest the
  model's. The direction of each domestic sector's change 2008 -> 2009,
  model and data, and the unconditional one-year-ahead. The direction is
  the model's 2009 against the data's 2008 (:hit) and against the model's
  own 2008, stepped from 2007 with the same parameters (:hit-own), which
  takes the model's level error out of the direction."
  [panel kn opts]
  (let [iso "USA" p (sfc/learn panel kn iso 2008 opts) st (sfc/init panel kn iso 2008 p)
        x9 (sfc/observed (get-in panel [iso 2009]))
        given (fn [st x] (:out (sfc/step-given st p #{:investment-nfc :investment-other} x 0.0)))
        cond-o (given st x9)
        own8 (:net-lending (given (sfc/init panel kn iso 2007 p) (sfc/observed (get-in panel [iso 2008]))))
        free-o (:out (sfc/step st p))
        d8 (fn [s] (/ (get-in panel [iso 2008 :f s :nl]) (get-in panel [iso 2008 :y])))
        d9 (fn [s] (/ (get-in panel [iso 2009 :f s :nl]) (get-in panel [iso 2009 :y])))]
    (for [s sfc/domestic]
      {:sector s :y2008 (d8 s) :data (d9 s) :given (get-in cond-o [:net-lending s]) :free (get-in free-o [:net-lending s])
       :own2008 (own8 s)
       :hit (= (pos? (- (d9 s) (d8 s))) (pos? (- (get-in cond-o [:net-lending s]) (d8 s))))
       :hit-own (= (pos? (- (d9 s) (d8 s))) (pos? (- (get-in cond-o [:net-lending s]) (own8 s))))})))

(defn- china
  "China's households against its corporations: data, one-year-ahead and a
  free run of the engine from 2009 (learned on 1992-2009) to 2019."
  [panel kn rows]
  (let [r (filter #(= "CHN" (:iso %)) rows)
        [_ res] (free-run panel kn "CHN" 2009 10)
        em (get-in res [:emits])
        yrs (:t res)]
    {:one-year (for [x r] [(:year x) (get-in x [:data :hh]) (get-in x [:model :hh]) (get-in x [:data :nfc]) (get-in x [:model :nfc])])
     :free (for [[i t] (map-indexed vector yrs)]
             [t (get-in panel ["CHN" t :f :hh :nl]) (get-in panel ["CHN" t :y])
              (get-in em [:sfc-accounts "sfc/net-lending-hh" i]) (get-in em [:sfc-accounts "sfc/net-lending-nfc" i])
              (get-in panel ["CHN" t :f :nfc :nl])])}))

;; -- Keen --------------------------------------------------------------------------------

(defn- keen-by-data-year
  "{iso {data-year {var value}}}: keen.csv's first row for each data year."
  [kn]
  (into {} (for [[iso rows] kn]
             [iso (reduce (fn [m [yk r]] (let [dy (some-> (get r "keen/state-year") long)]
                                            (if (and dy (not (contains? m dy))) (assoc m dy r) m)))
                          {} (sort-by key rows))])))

(defn- keen-lines [panel kn rows]
  (let [kd (keen-by-data-year kn)
        pairs (for [[iso obs] panel [y ob] obs :when (:flows-ok ob)
                    :let [k (get-in kd [iso y]) x (sfc/observed ob)
                          d (when (= :oecd-t720 (:stocks-source ob))
                              (/ (+ (sfc/pos (:b ob) :hh :debt :l) (sfc/pos (:b ob) :nfc :debt :l)) (:y ob)))]
                    :when k]
                {:iso iso :year y :w (/ (:W x) (:Y x)) :omega (get k "keen/omega") :d d :kd (get k "keen/d")})
        ch (fn [k1 k2] (for [[_ g] (group-by :iso pairs) :let [g (vec (sort-by :year g))]
                             [a b] (map vector g (rest g)) :when (= (:year b) (inc (:year a)))
                             :when (every? number? [(k1 a) (k1 b) (k2 a) (k2 b)])]
                         [(- (k1 b) (k1 a)) (- (k2 b) (k2 a))]))
        diff (fn [k1 k2] (mean (for [r pairs :when (and (number? (k1 r)) (number? (k2 r)))] (- (k1 r) (k2 r)))))
        lam (for [r rows :let [k (get-in kd [(:iso r) (:year r)])] :when (and k (get k "keen/lambda"))]
              [(:lambda-model r) (get k "keen/lambda") (:lambda-prev r)])
        wv (for [r rows] [(:w-model r) (:w-data r) (:w-prev r)])
        mae (fn [xs] (mean (map #(Math/abs (double %)) xs)))]
    (concat
     ["Keen's quantities: the frame's against keen.csv's (keen-run: PWT labour share, BIS/JST/WDI credit, JST/WDI unemployment)"
      (format "  wage share  frame D1/B1G vs keen/omega: n %d, mean frame - keen %s, corr of levels %s, of yearly changes %s"
              (count (filter #(number? (:omega %)) pairs)) (f3 (diff :w :omega)) (f3 (corr (map (juxt :w :omega) pairs))) (f3 (corr (ch :w :omega))))
      (format "  private debt  frame (HH + NFC F3+F4)/GDP vs keen/d: n %d, mean frame - keen %s, corr of levels %s, of yearly changes %s"
              (count (filter #(and (number? (:d %)) (number? (:kd %))) pairs)) (f3 (diff :d :kd))
              (f3 (corr (map (juxt :d :kd) pairs))) (f3 (corr (ch :d :kd))))
      (format "  one year ahead, the frame's wage share (Keen's Phillips curve) vs the data: MAE %s, persistence %s (n %d)"
              (f4 (mae (map (fn [[m d _]] (- m d)) wv))) (f4 (mae (map (fn [[_ d p]] (- p d)) wv))) (count wv))
      (format "  one year ahead, the frame's employment (Keen's dl/l from investment) vs keen/lambda: MAE %s, persistence %s (n %d)"
              (f4 (mae (map (fn [[m d _]] (- m d)) lam))) (f4 (mae (map (fn [[_ d p]] (- p d)) lam))) (count lam))]
     ["  by country (wage share frame/keen, debt frame/keen, means over common years):"]
     (for [[iso g] (sort-by key (group-by :iso pairs))
           :let [w (filter #(number? (:omega %)) g) d (filter #(and (number? (:d %)) (number? (:kd %))) g)]
           :when (seq w)]
       (format "    %s  w %s/%s  d %s/%s" iso (f3 (mean (map :w w))) (f3 (mean (map :omega w)))
               (f3 (mean (map :d d))) (f3 (mean (map :kd d))))))))

;; -- main -----------------------------------------------------------------------------

(defn -main [& _]
  (let [panel (sfc/read-panel panel-path)
        kn (if (.exists (io/file keen-path)) (sfc/read-keen keen-path) {})
        rows (sfc/one-year-ahead panel kn {})
        design (filter #(<= (:origin %) 2002) rows)
        test (filter #(> (:origin %) 2002) rows)
        measured (filter #(= :oecd-t720 (:source %)) rows)
        imputed (filter #(= :imputed (:source %)) rows)
        idents (max-of (map :identity rows))
        buffers (max-of (map :buffer rows))
        ;; free engine runs, identities per step
        runs (for [iso (sort (keys panel))
                   :let [ys (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob))] y))]
                   :when (>= (count ys) 6)
                   :let [origin (max (nth ys 4) (- (last ys) 10))]]
               (let [[ms res] (free-run panel kn iso origin 10)] [iso origin ms res]))
        run-gap (max-of (for [[_ _ _ res] runs k ["sfc/bsm-gap" "sfc/fof-gap"] v (get-in res [:emits :sfc-finance k])] v))
        run-tfm (max-of (for [[_ _ _ res] runs v (get-in res [:emits :sfc-accounts "sfc/tfm-gap"])] v))
        run-buf (max-of (for [[_ _ _ res] runs v (get-in res [:emits :sfc-accounts "sfc/buffer"])] v))
        us (us-2009 panel kn {})
        us-r (us-2009 panel kn sfc/revised)
        rows-r (sfc/one-year-ahead panel kn sfc/revised)
        design-r (filter #(<= (:origin %) 2002) rows-r)
        test-r (filter #(> (:origin %) 2002) rows-r)
        ib (sort (map :init-buffer rows))
        q (fn [f] (nth ib (min (dec (count ib)) (int (* f (count ib))))))
        big (sort (distinct (map :iso (filter #(> (:init-buffer %) 0.1) rows))))
        cn (china panel kn rows)
        lines (concat
               ["# The SFC core: validation (bnm.sfc-run)" ""
                (str "Countries: " (count (distinct (map :iso rows))) " with one-year-ahead forecasts; "
                     (count runs) " with a free engine run.") ""
                "## Identities in simulation (largest absolute value, ratio to GDP)"
                (str "  one-year-ahead steps (" (count rows) "): max of the TFM, flow-of-funds and balance-sheet gaps " (format "%.2e" idents)
                     "; buffer (redundant equation, booked to government deposits) " (format "%.2e" buffers))
                (str "  free engine runs (" (count runs) " countries x 10 years): balance sheet and flow of funds " (format "%.2e" run-gap)
                     ", TFM " (format "%.2e" run-tfm) ", buffer " (format "%.2e" run-buf))
                (str "  the measured balance sheet's own discrepancies, moved to the rest of the world at the start (sum of |instrument gaps|"
                     " over GDP): median " (format "%.2e" (q 0.5)) ", 90th pct " (format "%.3f" (q 0.9)) ", max " (format "%.3f" (last ib))
                     "; over 0.1 of GDP in " (count (filter #(> % 0.1) ib)) " of " (count ib) " origins: " (str/join " " big))
                "  (the identities above hold on the closed sheet; this closure is the data's, not the model's, and is not in the buffer)"
                ""
                "## One year ahead: sector net lending over GDP, against persistence (the data year before)"]
               (score-lines "all origins" rows) [""]
               (score-lines "design origins (to 2002)" design) [""]
               (score-lines "test origins (2003 on)" test) [""]
               ["The model's change applied to the observed level (data at y + model y+1 - model y):"]
               (score-lines "all origins" rows :model-ic) [""]
               (score-lines "design origins (to 2002)" design :model-ic) [""]
               (score-lines "test origins (2003 on)" test :model-ic) [""]
               (score-lines "measured stocks (Table 720)" measured) [""]
               (score-lines "imputed stocks" imputed) [""]
               ["## The revised rules (bnm.sfc/revised: bnm.sfc-audit's replacements, chosen on the design origins"
                "## after the first result above had been seen; the test origins were scored once, after the choice)"]
               (score-lines "all origins, revised" rows-r) [""]
               (score-lines "design origins (to 2002), revised" design-r) [""]
               (score-lines "test origins (2003 on), revised, a revision made after the first result" test-r) [""]
               (score-lines "test origins (2003 on), revised" test-r :model-ic) [""]
               ["By country, all sectors pooled: model MAE / persistence MAE"]
               (for [[iso g] (sort-by key (group-by :iso rows)) :let [s (:all (sfc/score g))]]
                 (format "  %s  n %3d  %s / %s" iso (:n s) (f4 (:model s)) (f4 (:persistence s))))
               ["" "## Named checks" ""
                "US 2009, given the crisis (2009's private investment as observed; learned to 2008):"
                "  sector  2008 data  2009 data  2009 given  2009 unconditional  right (vs 2008 data)  model's own 2008  right (vs own)"]
               (for [{:keys [sector y2008 data given free hit own2008 hit-own]} us]
                 (format "  %-6s  %9s  %9s  %10s  %18s  %-20s  %16s  %s" (name sector) (f4 y2008) (f4 data) (f4 given) (f4 free) (if hit "yes" "no")
                         (f4 own2008) (if hit-own "yes" "no")))
               [(str "  directions right: " (count (filter :hit us)) " of 4 against the 2008 data, " (count (filter :hit-own us)) " of 4 against the model's own 2008")
                (str "  revised rules: " (str/join ", " (for [{:keys [sector given own2008 hit-own]} us-r]
                                                          (str (name sector) " " (f4 given) " (own 2008 " (f4 own2008) ", " (if hit-own "right" "wrong") ")"))))
                ""
                "China, households against corporations (net lending over GDP):"
                "  one year ahead: year  hh data  hh model  nfc data  nfc model"]
               (for [[y hd hm nd nm] (:one-year cn)] (format "    %d  %s  %s  %s  %s" y (f4 hd) (f4 hm) (f4 nd) (f4 nm)))
               (let [oy (:one-year cn)]
                 [(format "  one year ahead, signs right (hh > 0, nfc < 0): hh %d/%d, nfc %d/%d"
                          (count (filter #(pos? (nth % 2)) oy)) (count oy) (count (filter #(neg? (nth % 4)) oy)) (count oy))])
               ["  free engine run from 2009 (no data after 2009): year  hh model  hh data  nfc model  nfc data"]
               (for [[t hd y hm nm nd] (:free cn)]
                 (format "    %d  %s  %s  %s  %s" t (f4 hm) (f4 (when (and hd y) (/ hd y))) (f4 nm) (f4 (when (and nd y) (/ nd y)))))
               [(format "  free run, signs right: hh %d/%d, nfc %d/%d"
                        (count (filter #(and (nth % 3) (pos? (nth % 3))) (:free cn))) (count (:free cn))
                        (count (filter #(and (nth % 4) (neg? (nth % 4))) (:free cn))) (count (:free cn)))
                ""]
               (keen-lines panel kn rows))]
    (io/make-parents (str out-dir "/x"))
    (with-open [w (io/writer (str out-dir "/one-year-ahead.csv"))]
      (.write w "iso,origin,year,source,sector,model,persistence,data\n")
      (doseq [r rows s sfc/sectors]
        (.write w (str (str/join "," [(:iso r) (:origin r) (:year r) (some-> (:source r) name) (name s)
                                      (get-in r [:model s]) (get-in r [:persistence s]) (get-in r [:data s])]) "\n"))))
    (with-open [w (io/writer (str out-dir "/state.csv"))]
      (.write w (str (str/join "," eng/table-header) "\n"))
      (doseq [[_ _ ms res] runs
              :let [by (into {} (map (juxt :module identity)) ms)]
              [id vs] (:emits res)
              [i t] (map-indexed vector (:t res))
              :let [nums (into {} (for [[v xs] vs :let [x (nth xs i nil)] :when (number? x)] [v x]))]
              r (eng/table-rows (by id) t nums)]
        (.write w (str (str/join "," (map #(if (keyword? %) (name %) (str %)) r)) "\n"))))
    (eng/write-graph (str out-dir "/graph") (let [[_ _ ms] (first runs)] ms))
    (spit (str out-dir "/validation.txt") (str (str/join "\n" lines) "\n"))
    (println (str/join "\n" lines))))
