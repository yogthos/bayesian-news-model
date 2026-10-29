(ns bnm.sfc-audit
  "The SFC core's assumption audit (bead bnm-4hv.10): which behavioural
  rule costs the one-year-ahead net lending most. On the design origins
  (to 2002) only, each block of bnm.sfc/block-docs is replaced in turn by

    oracle       the next year's observed value (its ratio to output):
                 how much of the error the rule causes;
    persistence  the origin year's own ratio: whether the rule beats
                 holding its ratio,

  the rest of the model left as it is, and the blocks are ranked by the
  share of the model's excess error over persistence each removes. Then
  the worst rules are set against the data directly (the Phillips curve at
  an annual step, employment from investment, the import share,
  government as a rule or a path). Writes data/derived/sfc/audit.txt.

    jolt -A:tools -M -m bnm.sfc-audit [design|test|all] [revised|estimated]"
  (:require [bnm.keen :as keen]
            [bnm.sfc :as sfc]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def panel-path "data/derived/sfc/panel.csv")
(def keen-path "data/derived/stocks/keen.csv")
(def out-path "data/derived/sfc/audit.txt")

(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))
(defn- mae [xs] (mean (map #(Math/abs (double %)) xs)))
(defn- f4 [x] (if (number? x) (format "%.4f" (double x)) "-"))
(defn- f3 [x] (if (number? x) (format "%.3f" (double x)) "-"))

(defn cases
  "[iso origin] with flows and stocks at the origin, flows the year after,
  five years of flows to the origin (one-year-ahead's sample)."
  [panel pick]
  (for [iso (sort (keys panel)) :let [obs (panel iso)] y (sort (keys obs))
        :let [ob (obs y) nx (obs (inc y))]
        :when (and ob (:flows-ok ob) (:y ob) (:b ob) nx (:flows-ok nx) (:y nx) (pick y)
                   (>= (count (filter #(and (<= (key %) y) (:flows-ok (val %))) obs)) 5))]
    [iso y]))

(defn- nl-of [ob] (into {} (for [s sfc/sectors] [s (/ (double (get-in ob [:f s :nl] 0.0)) (:y ob))])))

(defn prepared
  "Per case: parameters, start, the origin's and next year's flows, net
  lending (data and persistence), growth (actual and last year's)."
  [panel kn cs opts]
  (vec (for [[iso y] cs
             :let [p (sfc/learn panel kn iso y opts)
                   st (sfc/init panel kn iso y p)
                   ob (get-in panel [iso y]) nx (get-in panel [iso (inc y)]) pv (get-in panel [iso (dec y)])]]
         {:iso iso :y y :p p :st st :x0 (sfc/observed ob) :x1 (sfc/observed nx)
          :data (nl-of nx) :persist (nl-of ob)
          :g1 (- (/ (:y nx) (:y ob)) 1.0)
          :g0 (if (and pv (:y pv)) (- (/ (:y ob) (:y pv)) 1.0) (:n p))})))

(defn- err [rows k]
  (mae (for [r rows s sfc/domestic] (- (get-in r [k s]) (get-in r [:data s])))))

(defn run-given
  "The cases' net lending with blocks `given` set from :x1 (oracle) or :x0
  (persistence)."
  [prep given which]
  (for [c prep
        :let [[x g] (if (= which :oracle) [(:x1 c) (:g1 c)] [(:x0 c) (:g0 c)])
              o (:out (sfc/step-given (:st c) (:p c) given x g))]]
    (assoc c :model (:net-lending o))))

(defn ablation
  "{:base model MAE :persist persistence MAE :rows [{:block :oracle
  :persistence :removed-oracle :removed-persistence}]}: MAE of the four
  domestic sectors' net lending over output; removed is the share of the
  model's excess over persistence that giving the block takes away."
  [prep]
  (let [base (err (run-given prep #{} :oracle) :model)
        pers (err prep :persist)
        excess (- base pers)
        one (fn [g w] (err (run-given prep g w) :model))]
    {:base base :persist pers
     :all-oracle (one (set sfc/blocks) :oracle)
     :rows (sort-by :removed-oracle >
                    (for [b sfc/blocks
                          :let [o (one #{b} :oracle) q (one #{b} :persistence)]]
                      {:block b :oracle o :persistence q
                       :removed-oracle (/ (- base o) excess) :removed-persistence (/ (- base q) excess)}))}))

;; -- the rules against the data -------------------------------------------------

(defn- ols
  "[slope intercept r2 n] of y on x."
  [pairs]
  (let [ps (filter (fn [[a b]] (and (number? a) (number? b) (keen/finite? a) (keen/finite? b))) pairs)
        n (count ps)]
    (when (> n 3)
      (let [mx (mean (map first ps)) my (mean (map second ps))
            sxx (reduce + (map (fn [[a _]] (* (- a mx) (- a mx))) ps))
            sxy (reduce + (map (fn [[a b]] (* (- a mx) (- b my))) ps))
            syy (reduce + (map (fn [[_ b]] (* (- b my) (- b my))) ps))
            b (if (pos? sxx) (/ sxy sxx) 0.0)]
        [b (- my (* b mx)) (if (and (pos? sxx) (pos? syy)) (/ (* sxy sxy) (* sxx syy)) 0.0) n]))))

(defn data-checks
  "The worst rules against the data on the cases: lines of text."
  [panel kn prep]
  (let [w (fn [x] (/ (:W x) (:Y x)))
        ph (for [c prep :let [p (:p c) phl (:phillips p)] :when phl]
             {:dlw (Math/log (/ (w (:x1 c)) (w (:x0 c))))
              :phi (- (keen/phillips phl (get-in c [:st :lambda])) (:alpha phl))
              :lam (get-in c [:st :lambda])})
        kd (fn [iso y] (some (fn [[_ r]] (when (= (double y) (get r "keen/state-year")) r)) (sort-by key (get kn iso))))
        emp (for [c prep :let [k0 (kd (:iso c) (:y c)) k1 (kd (:iso c) (inc (:y c))) p (:p c)]
                  :when (and k0 k1 (get k0 "keen/lambda") (get k1 "keen/lambda"))]
              [(/ (- (/ (get-in c [:x1 :inv :nfc]) (:Y (:x1 c))) (:kappa-bar p)) (double (get-in p [:invest :nu] 3.0)))
               (- (/ (get k1 "keen/lambda") (get k0 "keen/lambda")) 1.0)])
        emp-g (for [c prep :let [k0 (kd (:iso c) (:y c)) k1 (kd (:iso c) (inc (:y c)))]
                    :when (and k0 k1 (get k0 "keen/lambda") (get k1 "keen/lambda"))]
                [(:g1 c) (- (/ (get k1 "keen/lambda") (get k0 "keen/lambda")) 1.0)])
        trade (for [[iso obs] panel [_ ob] obs :when (:flows-ok ob)
                    :let [im (get-in ob [:f :row :imports]) x (sfc/observed ob)
                          a (+ (reduce + (vals (:cons x))) (reduce + (vals (:inv x))))]]
                {:iso iso :mu (when im (/ im a))})
        gov (for [c prep :let [ye (* (get-in c [:st :Y]) (+ 1.0 (get-in c [:p :n])))
                               g1 (+ 1.0 (get-in c [:p :n]))
                               sh (fn [x k] (/ (k x) (:Y x)))
                               ks {:cg #(get-in % [:cons :gov]) :ig #(get-in % [:inv :gov]) :ben :ben}]
                  k (keys ks)]
              {:k k :rule (- (/ (* g1 ((ks k) (:x0 c))) ye) (sh (:x1 c) (ks k)))
               :share (- (sh (:x0 c) (ks k)) (sh (:x1 c) (ks k)))})
        cons (for [c prep :let [prop (fn [x] (/ (get-in x [:cons :hh]) (+ (get-in x [:cons :hh]) (get-in x [:inv :hh]) (get-in (sfc/book x) [:nl :hh]))))
                                o (:out (sfc/step-given (:st c) (:p c) #{} (:x1 c) (:g1 c)))]]
               {:model (- (prop (:x o)) (prop (:x1 c))) :persist (- (prop (:x0 c)) (prop (:x1 c)))})
        [b0 a0 r0 n0] (ols (map (juxt :phi :dlw) ph))
        [b1 _ r1 _] (ols (map (juxt :lam :dlw) ph))
        [be ae re ne] (ols emp)
        [bg _ rg _] (ols emp-g)
        dk (for [c prep :let [pp (get-in c [:st :pi-prev])] :when pp]
             [(- (get-in c [:st :pi]) pp)
              (- (/ (get-in c [:x1 :inv :nfc]) (:Y (:x1 c))) (get-in c [:st :kappa]))
              (#'sfc/kappa-slope (get-in c [:p :invest]) (get-in c [:st :pi]))])
        [bk ak rk nk] (ols (map (fn [[d k _]] [d k]) dk))
        lvl (for [c prep] [(bnm.keen/investment (get-in c [:p :invest]) (get-in c [:st :pi])) (/ (get-in c [:x1 :inv :nfc]) (:Y (:x1 c))) (get-in c [:st :kappa])])]
    (concat
     ["## The rules against the data (the same origins)" ""
      "Phillips curve at an annual step: the frame's dlog w (D1/B1G, y to y+1) on keen-run's Phi(lambda_y) - alpha"
      (format "  slope %s (Keen: 1), intercept %s, R2 %s, n %d; on lambda_y alone R2 %s (slope %s)"
              (f3 b0) (f3 a0) (f3 r0) n0 (f3 r1) (f3 b1))
      (format "  mean dlog w: data %s, the curve %s; mean |.|: data %s, the curve %s"
              (f4 (mean (map :dlw ph))) (f4 (mean (map :phi ph))) (f4 (mae (map :dlw ph))) (f4 (mae (map :phi ph))))
      ""
      "Employment from investment: keen-run's dlambda/lambda (y to y+1) on (I_nfc/Y - kappa-bar)/nu at y+1"
      (format "  slope %s (the rule: 1), intercept %s, R2 %s, n %d; on nominal output growth R2 %s (slope %s)"
              (f3 be) (f3 ae) (f3 re) ne (f3 rg) (f3 bg))
      ""
      "Firms' investment: the change in I_nfc/Y (y to y+1) on the change in the profit share (y-1 to y)"
      (format "  slope %s (Keen's kappa'(pi) at the origin: median %s), R2 %s, n %d"
              (f3 bk) (f3 (nth (sort (map #(nth % 2) dk)) (quot (count dk) 2) nil)) (f3 rk) nk)
      (format "  next year's share, MAE: Keen's kappa(pi) fitted %s, last year's share %s"
              (f4 (mae (map (fn [[m d _]] (- m d)) lvl))) (f4 (mae (map (fn [[_ d q]] (- q d)) lvl))))
      ""
      "The import share (imports over absorption, Table 14 S2 P7):"
      (format "  country-years with imports %d of %d (the 0.3 default applies to none with trade data)"
              (count (filter :mu trade)) (count trade))
      (str "  by country, mean: "
           (str/join ", " (for [[iso g] (sort-by key (group-by :iso trade)) :let [m (keep :mu g)] :when (seq m)]
                            (str iso " " (format "%.2f" (mean m))))))
      ""
      "Government: the rule (last year's level grown at n, over expected output) against holding the share,"
      "MAE against the next year's share of output:"]
     (for [[k g] (sort-by key (group-by :k gov))]
       (format "  %-4s rule %s  share held %s" (name k) (f4 (mae (map :rule g))) (f4 (mae (map :share g)))))
     [""
      "Households' propensity to consume out of disposable income (C over C + I_h + NL_h), MAE against the next year's:"
      (format "  the fitted C = a1 YD + a2 V %s, last year's propensity %s"
              (f4 (mae (map :model cons))) (f4 (mae (map :persist cons))))])))

(defn report
  [panel kn pick label opts]
  (let [prep (prepared panel kn (cases panel pick) opts)
        {:keys [base persist all-oracle rows]} (ablation prep)]
    (concat
     [(str "# The SFC core: assumption audit (bnm.sfc-audit), " label " origins") ""
      (format "Cases %d (%d countries). Four domestic sectors' net lending over GDP, one year ahead, MAE:"
              (count prep) (count (distinct (map :iso prep))))
      (format "  model %s, persistence %s, excess %s; every block given (oracle) %s" (f4 base) (f4 persist) (f4 (- base persist)) (f4 all-oracle))
      ""
      "Each block replaced alone; removed = share of the model's excess over persistence it takes away"
      "(oracle: the next year's observed ratio; persistence: the origin's own ratio):"
      "(when the model beats persistence the excess is negative and removed changes sign; held - model is the"
      " change in MAE from holding that block's ratio instead of the rule: positive, the rule helps)"
      "  block              oracle MAE  removed   persistence MAE  removed  held - model   the rule"]
     (for [{:keys [block oracle persistence removed-oracle removed-persistence]} rows]
       (format "  %-17s  %10s  %7s   %15s  %7s  %+.5f   %s" (name block) (f4 oracle) (f3 removed-oracle)
               (f4 persistence) (f3 removed-persistence) (- persistence base) (sfc/block-docs block)))
     [""]
     (data-checks panel kn prep))))

(defn -main [& [which rules]]
  (let [panel (sfc/read-panel panel-path)
        kn (if (.exists (io/file keen-path)) (sfc/read-keen keen-path) {})
        [pick label] (case which
                       "test" [#(> % 2002) "test (2003 on)"]
                       "all" [(constantly true) "all"]
                       [#(<= % 2002) "design (to 2002)"])
        [opts tag] (case rules
                     "revised" [sfc/revised ", the revised rules (bnm.sfc/revised)"]
                     "estimated" [sfc/estimated ", the rules estimated from data (bnm.sfc/estimated)"]
                     [{} nil])
        lines (report panel kn pick (str label tag) opts)]
    (when (and (or (nil? which) (= which "design")) (nil? rules)) (spit out-path (str (str/join "\n" lines) "\n")))
    (when (and (= which "design") rules) (spit (str/replace out-path ".txt" (str "-" rules ".txt")) (str (str/join "\n" lines) "\n")))
    (println (str/join "\n" lines))))
