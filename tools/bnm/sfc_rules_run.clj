(ns bnm.sfc-rules-run
  "The SFC core's behavioural rules estimated from data (bead bnm-4hv.14;
  docs/COUPLED-SYSTEMS.md 2a, 9g).

    jolt -A:tools -M -m bnm.sfc-rules-run design [--rules estimated,hold,...] [--vs estimated] [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run estimates [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run mask|power [--split holdout]
    jolt -A:tools -M -m bnm.sfc-rules-run test --out FILE [--all true]
    jolt -A:tools -M -m bnm.sfc-rules-run score --in FILE --bounds EDN [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run held --in FILE [--edn FILE] [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run interest-drift [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run scenario [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rules-run rates-shape|rates-test|rates-freeze --out FILE
    jolt -A:tools -M -m bnm.sfc-rules-run rates-score --in FILE --null FILE [--out FILE]

  design takes --target-to YEAR to add the skills on targets to that
  data year only.
  held and interest-drift are reported-only diagnostics from the review
  (held-report, interest-drift).

  design: the restart (one member, learned at the origin on the panel as
  known at origin + 2, started from the origin's data, stepped by
  bnm.sfc/step: bnm.sfc-analysis/restart's path without the engine) on
  the design origins (data years to 2002, measured or imputed stocks,
  five years of flows) under each candidate rule set, at horizons 1, 3
  and 5, against persistence and damped reversion: skill 1 - SSE_m /
  SSE_x pooled over the rows, by outcome and horizon, with the 90%
  country-bootstrap interval. The rule sets were chosen here.

  estimates: the pooled error-correction fits on data years to 2002 and
  their leave-one-country-out skill against holding the ratio.

  mask, power, test, score: the registered test (9g). Its rows (design,
  test and holdout splits; the holdout is panel-holdout.csv's countries,
  scripts/sfc-data.py holdout), the power simulation behind its bounds,
  the forecasts of every model at every row, and the verdicts."
  (:require [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [bnm.sfc-horizon-run :as h]
            [augur.math]
            [clojure.pprint]
            [clojure.string :as str]))

(def panel-path "data/derived/sfc/panel.csv")
(def keen-path "data/derived/stocks/keen.csv")
(def policy-path "data/derived/sfc/policy.csv")

(def horizons [1 3 5])
(def design-to 2002)

(defn stocked-years
  "`iso`'s data years with flows and a balance sheet (measured or imputed)."
  [panel iso]
  (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:y ob) (:b ob))] y)))

(defn design-origins
  "Origins o <= `to` with flows and stocks at o and five or more years of
  flows to o; countries the hyperinflation rule excludes left out."
  [panel to]
  (for [iso (sort (keys panel))
        :when (not (h/hyperinflation? panel iso))
        o (stocked-years panel iso)
        :when (and (<= o to)
                   (>= (count (filter (fn [[y ob]] (and (<= y o) (:flows-ok ob))) (get panel iso))) 5))]
    [iso o]))

(defn restart-path
  "{data-year {:nl {sector ratio} :debt ratio}} of the restart from data
  year `o`, `years` on, under learn options `opts`."
  [panel kn iso o years opts]
  (let [kp (sa/known-panel panel (+ o sfc/vintage))
        p (sfc/learn kp kn iso o opts)
        st (sfc/init kp kn iso o p)]
    (loop [st st i 0 acc {}]
      (if (= i years)
        acc
        (let [{s :state out :out} (sfc/step st p)]
          (recur s (inc i) (assoc acc (:year out) {:nl (:net-lending out) :debt (:private-debt out)})))))))

(defn rows-of
  "Scored rows for the origins `os`: {:iso :o :h :outcome :data :f {model
  value}}, `models` {name learn-opts}; persistence and damped added."
  [panel kn os models]
  (vec (for [[iso o] os
             :let [paths (into {} (for [[k opts] models] [k (try (restart-path panel kn iso o (apply max horizons) opts)
                                                                  (catch Exception _ nil))]))]
             hz horizons
             out (concat (for [s h/nl-sectors] [:nl s]) [[:debt]])
             :let [y (+ o hz)
                   obs (fn [yy] (if (= :nl (first out)) (h/nl-data panel iso yy (second out)) (h/debt-data panel iso yy)))
                   d (obs y) d0 (obs o)
                   fm (into {} (for [[k p] paths :let [v (if (= :nl (first out)) (get-in p [y :nl (second out)]) (get-in p [y :debt]))]
                                     :when (and (number? v) (== v v) (< (Math/abs (double v)) 1e300))]
                                 [k v]))]
             :when (and d d0 (= (count fm) (count models)))]
         {:iso iso :o o :h hz :outcome out :data d :split :design
          :f (assoc fm :persistence d0 :damped (h/damped panel iso out o hz))})))

(defn loco
  "One-year skill of the pooled error-correction fit against holding the
  ratio (a zero change), each country's rows predicted from the others'
  fit: 1 - SSE_fit / SSE_hold."
  [by-iso]
  (let [k (dec (count (first (first (vals by-iso)))))
        es (for [[iso rs] by-iso
                 :let [f (sfc/ols (apply concat (vals (dissoc by-iso iso))))]
                 :when f
                 r rs]
             (let [y (double (nth r k)) yh (reduce + 0.0 (map #(* (double (nth r %)) (double ((:b f) %))) (range k)))]
               [(- y yh) y]))]
    (- 1.0 (/ (reduce + (map #(* (first %) (first %)) es)) (reduce + (map #(* (second %) (second %)) es))))))

(defn skills
  "Lines: per model, its skill against persistence and against damped
  reversion with the 90% country-bootstrap interval, by outcome group
  and horizon (rows where every model and damped reversion have a value)."
  [rows models & [vs]]
  (let [rows (filter #(number? (get-in % [:f :damped])) rows)
        groups (concat [["net lending" #(= :nl (first %))]]
                       (for [s h/nl-sectors] [(str "nl " (name s)) #(= [:nl s] %)])
                       [["private debt" #(= [:debt] %)]])
        f (fn [[p lo hi]] (if p (format "%+.3f [%+.3f,%+.3f]" p lo hi) "-"))]
    (concat
     (for [[label sel] groups]
       (let [rs (filter #(sel (:outcome %)) rows)]
         (format "%s: rows h1 %d, h3 %d, h5 %d; countries %d" label (count (filter #(= 1 (:h %)) rs)) (count (filter #(= 3 (:h %)) rs))
                 (count (filter #(= 5 (:h %)) rs)) (count (distinct (map :iso rs))))))
     (for [[label sel] groups x (concat [:persistence :damped] vs)
           :let [hdr (str "## " label " against " (name x))]
           line (cons hdr
                      (for [m models :when (not= m x)]
                        (str (format "  %-20s" (name m))
                             (str/join "  " (for [hz horizons :let [rs (filter #(and (= hz (:h %)) (sel (:outcome %))) rows)]]
                                              (str "h" hz " " (f (h/bootstrap rs m x 500 20261029))))))))]
       line))))

;; -- the registered test (bnm-4hv.14; docs/COUPLED-SYSTEMS.md 9g) -----------------------------

(def holdout-path "data/derived/sfc/panel-holdout.csv")
(def test-from 2003)

(defn mask
  "The registered test's rows [{:iso :o :h :outcome :split :data}]: every
  country with eight or more measured years (bnm.sfc-horizon-run/countries)
  in `panel`, origins its measured years at least five after its first,
  horizons 1, 3 and 5, outcomes the four domestic sectors' net lending
  and private debt (measured), where the data have the target and the
  origin's value. :split is :holdout for a country in `holdout`, else
  :design (origin to 2002) or :test (2003 on). With `all` false the
  hyperinflation rule (bnm.sfc-horizon-run/hyperinflation?) applies."
  [panel holdout all]
  (vec (for [iso (h/countries panel) :when (or all (not (h/hyperinflation? panel iso)))
             :let [ys (h/measured-years panel iso) start (first ys)]
             o ys :when (>= o (+ start 5))
             hz horizons
             out (concat (for [s h/nl-sectors] [:nl s]) [[:debt]])
             :let [obs (fn [y] (if (= :nl (first out)) (h/nl-data panel iso y (second out)) (h/debt-data panel iso y)))
                   d (obs (+ o hz)) d0 (obs o)]
             :when (and d d0)]
         {:iso iso :o o :h hz :outcome out :data d
          :split (cond (contains? holdout iso) :holdout (< o test-from) :design :else :test)})))

(defn- emit-of [out] (if (= :nl (first out)) (str "sfc/net-lending-" (name (second out))) "sfc/private-debt"))
(defn- fin [v] (when (and (number? v) (== v v) (< (Math/abs (double v)) 1e300)) (double v)))

(defn forecasts
  "{[o h outcome] {model value}} for `iso`'s rows: :estimated (the restart
  under bnm.sfc/estimated, bnm.sfc-analysis/restart through the engine),
  :restart (the current restart, learn's defaults: 9f's), :persistence,
  :damped."
  [panel kn iso rows]
  (let [os (sort (distinct (map :o rows)))
        hmax (apply max horizons)
        run (fn [o opts] (try (sa/restart panel kn iso o hmax nil opts) (catch Exception _ nil)))
        rs (into {} (for [o os] [o {:estimated (run o sfc/estimated) :restart (run o {})}]))
        emit emit-of]
    (into {} (for [{:keys [o h outcome]} rows :let [y (+ o h)]]
               [[o h outcome]
                {:estimated (fin (some-> (get-in rs [o :estimated]) (h/path-at (emit outcome) y)))
                 :restart (fin (some-> (get-in rs [o :restart]) (h/path-at (emit outcome) y)))
                 :persistence (if (= :nl (first outcome)) (h/nl-data panel iso o (second outcome)) (h/debt-data panel iso o))
                 :damped (h/damped panel iso outcome o h)}]))))

(defn block-bootstrap
  "[point lo hi]: skill of m against x over `rows` and its 90% interval,
  resampling each country's origins in moving blocks of `block`
  consecutive origins (every row of a drawn origin, all horizons and
  outcomes, comes with it), the countries kept; `draws` draws from
  augur.math/rng-mixed `seed`. For a holdout of few countries, where a
  country bootstrap has no spread to draw on."
  [rows m x draws seed block]
  (let [by (group-by :iso rows)
        sse (fn [rs k] (reduce + 0.0 (map #(let [e (- (double (get-in % [:f k])) (double (:data %)))] (* e e)) rs)))
        skill (fn [rs] (let [sx (sse rs x)] (when (pos? sx) (- 1.0 (/ (sse rs m) sx)))))
        g (augur.math/rng-mixed seed)
        draw (fn []
               (vec (mapcat (fn [[_ rs]]
                              (let [byo (group-by :o rs) os (vec (sort (keys byo))) n (count os) L (min block n)]
                                (->> (repeatedly #(let [s (long (* (inc (- n L)) (augur.math/next-double! g)))] (subvec os s (+ s L))))
                                     (apply concat) (take n) (mapcat byo))))
                            (sort-by key by))))
        qs (vec (sort (keep identity (repeatedly draws #(skill (draw))))))
        q (fn [p] (nth qs (min (dec (count qs)) (long (* p (count qs))))))]
    [(skill rows) (q 0.05) (q 0.95)]))

(defn overall
  "The registered verdict from cells {[baseline horizon-group] {outcome
  [point lo hi]}} and bounds {outcome b} (or {horizon-group {outcome b}}):
  each cell's bnm.sfc-horizon-run/verdict; :pass when every cell passes,
  :worse when any is worse, :equivalent when every cell is, else
  :inconclusive."
  [cells bounds]
  (let [vs (for [[[_ hg] cis] cells] (h/verdict cis (get bounds hg bounds)))]
    (cond (every? #{:pass} vs) :pass
          (some #{:worse} vs) :worse
          (every? #{:equivalent} vs) :equivalent
          :else :inconclusive)))

(defn simulate
  "The rows with simulated errors (9f's model, power's): per country,
  horizon and outcome series an AR(1) over origins (`rho`), a country scale
  (lognormal, sd 0.5), the model's errors correlated `kappa` with the
  comparator's (:x) and scaled to true skill `s` (:m); generator `g`."
  [rows s g {:keys [rho kappa] :or {rho 0.8 kappa 0.7}}]
  (let [n! #(augur.math/normal! g)
        scale (into {} (for [iso (sort (distinct (map :iso rows)))] [iso (Math/exp (* 0.5 (n!)))]))]
    (vec (mapcat (fn [[[iso _ _] rs]]
                   (loop [rs (sort-by :o rs) ex nil em nil acc []]
                     (if-let [r (first rs)]
                       (let [zx (n!) zm (+ (* kappa zx) (* (Math/sqrt (- 1 (* kappa kappa))) (n!)))
                             ex (if ex (+ (* rho ex) (* (Math/sqrt (- 1 (* rho rho))) zx)) zx)
                             em (if em (+ (* rho em) (* (Math/sqrt (- 1 (* rho rho))) zm)) zm)
                             sc (scale iso)]
                         (recur (rest rs) ex em (conj acc (assoc r :data 0.0 :f {:x (* sc ex) :m (* sc (Math/sqrt (- 1 s)) em)}))))
                       acc)))
                 (sort-by key (group-by (juxt :iso :h :outcome) rows))))))

(defn- skill-rows [rows m x]
  (let [sse (fn [k] (reduce + 0.0 (map #(let [e (- (double (get-in % [:f k])) (double (:data %)))] (* e e)) rows)))
        sx (sse x)]
    (when (pos? sx) (- 1.0 (/ (sse m) sx)))))

(defn null-spread
  "[q05 q95] of the skill over rows shaped like `rows` when the true skill
  is 0, by simulation (simulate), `sims` draws from augur.math/rng-mixed
  `seed`."
  [rows sims seed]
  (let [ps (vec (sort (keep (fn [k] (skill-rows (simulate rows 0.0 (augur.math/rng-mixed seed k) {}) :m :x)) (range sims))))
        q (fn [p] (nth ps (min (dec (count ps)) (long (* p (count ps))))))]
    [(q 0.05) (q 0.95)]))

(defn interval
  "The 90% interval of a split. For the test and design origins, countries
  resampled (bnm.sfc-horizon-run/bootstrap, 9f's). For the holdout (two
  countries, whose origins share most of their paths) no resampling of
  the data is calibrated: its block bootstrap passed a true skill of 0 a
  quarter of the time. Its interval is [p - q95, p - q05], q the null
  spread of the skill on its rows' shape under 9f's error model
  (null-spread, 1,000 simulations): an assumption, registered as such."
  [split rows m x draws seed]
  (if (= :holdout split)
    (let [p (skill-rows rows m x) [q05 q95] (null-spread rows 1000 seed)]
      [p (when p (- p q95)) (when p (- p q05))])
    (h/bootstrap rows m x draws seed)))

(defn power
  "{true-skill pass-rate}: how often the lower end of the split's 90%
  interval is above 0 when the model's true skill against the comparator
  is s, by simulation on the rows' mask: comparator errors per country,
  horizon and outcome series with a country scale (lognormal, sd 0.5) and
  an AR(1) over origins (rho 0.8), the model's correlated 0.7 with them
  and scaled to skill s (9f's assumptions, not fitted to any outcome);
  generators from augur.math/rng-mixed."
  [split rows {:keys [rho kappa sims draws seed] :or {rho 0.8 kappa 0.7 sims 300 draws 500 seed 20261029}}]
  (if (= :holdout split)
    (let [[_ q95] (null-spread rows 1000 seed)]
      (into (sorted-map)
            (for [[si s] (map-indexed vector [0.0 0.05 0.10 0.15 0.20 0.25 0.30 0.40 0.50])]
              [s (/ (count (filter (fn [k] (when-let [p (skill-rows (simulate rows s (augur.math/rng-mixed seed 7 si k) {}) :m :x)] (> p q95)))
                                   (range 1000)))
                    1000.0)])))
  (let [keyed (group-by (juxt :iso :h :outcome) rows)]
    (into (sorted-map)
          (for [[si s] (map-indexed vector [0.0 0.05 0.10 0.15 0.20 0.25 0.30 0.40 0.50])]
            [s (/ (count (filter true?
                                 (for [k (range sims)]
                                   (let [g (augur.math/rng-mixed seed si k)
                                         n! #(augur.math/normal! g)
                                         scale (into {} (for [iso (sort (distinct (map :iso rows)))] [iso (Math/exp (* 0.5 (n!)))]))
                                         sim (vec (mapcat (fn [[[iso _ _] rs]]
                                                            (loop [rs (sort-by :o rs) ex nil em nil acc []]
                                                              (if-let [r (first rs)]
                                                                (let [zx (n!) zm (+ (* kappa zx) (* (Math/sqrt (- 1 (* kappa kappa))) (n!)))
                                                                      ex (if ex (+ (* rho ex) (* (Math/sqrt (- 1 (* rho rho))) zx)) zx)
                                                                      em (if em (+ (* rho em) (* (Math/sqrt (- 1 (* rho rho))) zm)) zm)
                                                                      sc (scale iso)]
                                                                  (recur (rest rs) ex em (conj acc (assoc r :data 0.0 :f {:x (* sc ex) :m (* sc (Math/sqrt (- 1 s)) em)}))))
                                                                acc)))
                                                          (sort-by key keyed)))
                                         [_ lo _] (interval split sim :m :x draws (augur.math/seed-of seed si k 1))]
                                     (> lo 0.0)))))
                  (double sims))])))))

(def held
  "9g's fallback on a worse: every behavioural ratio held, interest and
  dividends received as shares of output (the design harness's hold)."
  (assoc (dissoc sfc/estimated :fiscal) :investment {:phi 0.0 :slope 0.0}))

(defn add-model
  "`scored` rows with model `k`'s forecast added to :f: the restart under
  learn options `opts`, through the engine as forecasts runs it."
  [panel kn scored k opts]
  (let [hmax (apply max horizons)]
    (vec (mapcat (fn [[iso rs]]
                   (let [runs (into {} (for [o (distinct (map :o rs))]
                                         [o (try (sa/restart panel kn iso o hmax nil opts) (catch Exception _ nil))]))]
                     (println iso (count rs) "rows") (flush)
                     (for [r rs]
                       (assoc-in r [:f k] (fin (some-> (runs (:o r)) (h/path-at (emit-of (:outcome r)) (+ (:o r) (:h r)))))))))
                 (sort-by key (group-by :iso scored))))))

(defn held-report
  "Reported-only lines (no verdict; the review of bnm-4hv.14): the held
  core's skill against each baseline and the estimated core's against
  the held, per split, outcome and horizon group, with the registered
  intervals (interval), on rows where every model has a value."
  [scored]
  (let [ms [:estimated :hold :persistence :damped :restart]
        rows (filter (fn [r] (every? #(number? (get-in r [:f %])) ms)) scored)
        f (fn [[p lo hi]] (if p (format "%+.3f [%+.3f, %+.3f]" p lo hi) "-"))
        outs [[:nl #(= :nl (first %))] [:debt #(= [:debt] %)]]]
    (cons (format "rows with every model: %d of %d" (count rows) (count scored))
          (for [split [:holdout :test :design] [o pred] outs [m x] [[:hold :persistence] [:hold :damped] [:hold :restart] [:estimated :hold]]
                :let [sel (fn [hs] (filter #(and (= split (:split %)) (pred (:outcome %)) (hs (:h %))) rows))
                      ci (fn [rs] (when (seq rs) (interval split rs m x 2000 20261029)))]]
            (format "%-7s %-4s %-9s vs %-11s h1 %s   h3-5 %s" (name split) (name o) (name m) (name x)
                    (f (ci (sel #{1}))) (f (ci (sel #{3 5}))))))))

(defn- ib-liab [b s] (+ (sfc/pos b s :money :l) (sfc/pos b s :debt :l)))

(defn interest-drift
  "Reported-only (review of bnm-4hv.14): with interest held as a share of
  output, the rate it implies on the model's own stocks drifts. For each
  origin [iso o] the restart under `opts` (bnm.sfc/step) to o + 5: per
  sector paying interest on money and debt (hh, nfc, gov), the implied
  rate at year o + 5 (interest paid over the opening liabilities) over
  the rate learn's revised rule would charge (:i-l), and the same ratio in
  the data (the implicit rate at o + 5 over o's), and the change in the
  liabilities over output. {sector {:model [r ...] :data [r ...] :stock
  [[model data] ...]}}."
  [panel kn os opts]
  (let [rate-data (fn [iso y s] (let [ob (get-in panel [iso y]) pv (get-in panel [iso (dec y)])]
                                  (when (and ob pv (:flows-ok ob) (:b pv))
                                    (let [st (ib-liab (:bsm (sfc/close-init (:b pv))) s)]
                                      (when (> st 1e-9) (/ (double (get-in ob [:f s :int_paid] 0.0)) st))))))]
    (reduce (fn [acc [iso o]]
              (let [kp (sa/known-panel panel (+ o sfc/vintage))
                    p (try (sfc/learn kp kn iso o opts) (catch Exception _ nil))
                    st0 (when p (try (sfc/init kp kn iso o p) (catch Exception _ nil)))
                    run (when st0 (try (loop [st st0 i 0] (if (= i 4) st (recur (:state (sfc/step st p)) (inc i))))
                                       (catch Exception _ nil)))
                    end5 (when run (try (sfc/step run p) (catch Exception _ nil)))
                    rates (when end5 (:i-l (sfc/learn kp kn iso o (dissoc opts :interest-hold :dividend-hold))))]
                (if-not end5
                  acc
                  (reduce (fn [acc s]
                            (let [open (:bsm run) stock (ib-liab open s)
                                  r5 (when (> stock 1e-9) (/ (double (get-in end5 [:out :x :int-paid s])) stock))
                                  r0 (get rates s)
                                  d0 (rate-data iso o s) d5 (rate-data iso (+ o 5) s)
                                  y0 (double (:Y st0)) y5 (double (get-in end5 [:out :Y]))
                                  s0 (/ (ib-liab (:bsm st0) s) y0) s5 (/ (ib-liab (get-in end5 [:out :end]) s) y5)
                                  ob5 (get-in panel [iso (+ o 5)])
                                  sd5 (when (and ob5 (:b ob5) (:y ob5)) (/ (ib-liab (:bsm (sfc/close-init (:b ob5))) s) (double (:y ob5))))]
                              (cond-> acc
                                (and r5 r0 (pos? r0)) (update-in [s :model] (fnil conj []) (/ r5 r0))
                                (and d0 d5 (pos? d0)) (update-in [s :data] (fnil conj []) (/ d5 d0))
                                sd5 (update-in [s :stock] (fnil conj []) [(- s5 s0) (- sd5 s0)]))))
                          acc [:hh :nfc :gov]))))
            {} os)))

(def baselines [:persistence :damped :restart])
(def hgroups {:h1 #{1} :h35 #{3 5}})

;; -- the rate channel's test (bnm-4hv.15; docs/COUPLED-SYSTEMS.md 9h) ---------------------------

(def holdout2-path "data/derived/sfc/panel-holdout2.csv")

(defn forecasts-of
  "{[o h outcome] {model value}} for `iso`'s rows: each of `models` {name
  learn-opts} as the restart through the engine (bnm.sfc-analysis/restart),
  with :persistence and :damped."
  [panel kn iso rows models]
  (let [os (sort (distinct (map :o rows)))
        hmax (apply max horizons)
        rs (into {} (for [o os] [o (into {} (for [[k opts] models]
                                              [k (try (sa/restart panel kn iso o hmax nil opts) (catch Exception _ nil))]))]))]
    (into {} (for [{:keys [o h outcome]} rows :let [y (+ o h)]]
               [[o h outcome]
                (into {:persistence (if (= :nl (first outcome)) (h/nl-data panel iso o (second outcome)) (h/debt-data panel iso o))
                       :damped (h/damped panel iso outcome o h)}
                      (for [k (keys models)] [k (fin (some-> (get-in rs [o k]) (h/path-at (emit-of outcome) y)))]))]))))

(defn non-inferior
  "9h's verdict from cells {[horizon-group outcome] [point lo hi]} (h1 and
  h35, nl and debt; the rates core's skill against estimated) and margin
  `delta`: :non-inferior when all four lower ends are above -delta,
  :inferior when any upper end is below -delta, else :inconclusive."
  [cells delta]
  (let [cs (for [hg [:h1 :h35] o [:nl :debt]] (get cells [hg o]))]
    (cond (some #(and % (< (nth % 2) (- delta))) cs) :inferior
          (every? #(and % (> (second %) (- delta))) cs) :non-inferior
          :else :inconclusive)))

(defn freeze-rows
  "The rows of the later confirmation (9h): for each country, its latest
  measured origin (bnm.sfc-horizon-run/measured-years) and horizons 1 to
  5 whose target year is after the country's last data year with flows,
  the four sectors' net lending and private debt; :data nil (not yet
  published)."
  [panel]
  (vec (for [iso (sort (keys panel))
             :let [ys (h/measured-years panel iso) o (last ys)
                   last-flows (reduce max 0 (for [[y ob] (get panel iso) :when (:flows-ok ob)] y))]
             :when o
             hz (range 1 6) :when (> (+ o hz) last-flows)
             out (concat (for [s h/nl-sectors] [:nl s]) [[:debt]])]
         {:iso iso :o o :h hz :outcome out :data nil :split :frozen})))

(def rate-models [:rates :estimated :persistence :damped :restart])

(defn rates-report
  "9h's lines and verdicts from scored rows (:f with every rate-models
  value): the rates core's skill against estimated per split, outcome and
  horizon group, its interval (the test and design origins: countries
  resampled, 2,000 draws, seed 20261029; the holdout: [p - q95, p - q05]
  from `null` {[hg outcome] [q05 q95]}, the error model fitted to the test
  split, scripts/sfc-rates-null.py), and the verdicts (non-inferior,
  margin `delta`) for the holdout (primary) and the test origins
  (secondary); against persistence, damped reversion and Keen's restart,
  reported. {:lines [...] :verdicts {split verdict}}."
  [scored null delta]
  (let [rows (filter (fn [r] (every? #(number? (get-in r [:f %])) rate-models)) scored)
        sel (fn [split o hs] (filter #(and (= split (:split %)) (if (= o :nl) (= :nl (first (:outcome %))) (= [:debt] (:outcome %))) (hs (:h %))) rows))
        ci (fn [split rs x hg o]
             (when (seq rs)
               (if (= split :holdout)
                 (let [p (skill-rows rs :rates x) [q05 q95] (get null [hg o])]
                   (when (and p q05) [p (- p q95) (- p q05)]))
                 (h/bootstrap rs :rates x 2000 20261029))))
        f (fn [[p lo hi]] (if p (format "%+.3f [%+.3f, %+.3f]" p lo hi) "-"))
        splits [:holdout :test :design]
        cells (fn [split] (into {} (for [[hg hs] hgroups o [:nl :debt]] [[hg o] (ci split (sel split o hs) :estimated hg o)])))
        verdicts (into {} (for [split [:holdout :test]] [split (non-inferior (cells split) delta)]))
        lines (concat
               [(format "rows scored (every model present): %d of %d" (count rows) (count scored))]
               (for [split splits o [:nl :debt]]
                 (format "%s %s: rows h1 %d, h3 %d, h5 %d; countries %d" (name split) (name o)
                         (count (sel split o #{1})) (count (sel split o #{3})) (count (sel split o #{5}))
                         (count (distinct (map :iso (sel split o #{1 3 5}))))))
               (for [split splits o [:nl :debt] x [:estimated :persistence :damped :restart]]
                 (format "%-7s %-4s rates vs %-11s h1 %s   h3-5 %s" (name split) (name o) (name x)
                         (f (if (= x :estimated) (ci split (sel split o #{1}) x :h1 o) (when (not= split :holdout) (ci split (sel split o #{1}) x :h1 o))))
                         (f (if (= x :estimated) (ci split (sel split o #{3 5}) x :h35 o) (when (not= split :holdout) (ci split (sel split o #{3 5}) x :h35 o))))))
               (for [split [:holdout] o [:nl :debt] x [:persistence :damped :restart]
                     :let [pt (fn [hs] (some-> (seq (sel split o hs)) (skill-rows :rates x)))]]
                 (format "holdout %-4s rates vs %-11s points only: h1 %s   h3-5 %s" (name o) (name x)
                         (some->> (pt #{1}) (format "%+.3f")) (some->> (pt #{3 5}) (format "%+.3f"))))
               (for [[split v] verdicts] (format "verdict, %s (%s): %s" (name split)
                                                 (if (= split :holdout) "primary" "secondary, origins seen three times before") (name v))))]
    {:lines (vec lines) :verdicts verdicts}))

;; -- the rate channel's scenarios (bnm-4hv.15; reported only) --------------------------------

(defn scenario-paths
  "{:base :rate :fiscal} -> {data-year out}: the restart from data year `o`
  (learned as restart-path learns it, under learn options `opts`), `years`
  on, as is (:base), with the policy rate 100bp higher from o + 1 on
  (:rate, bnm.sfc/policy-shock), and with government consumption raised
  by 1% of expected output in o + 1 to o + 5, financed by debt (:fiscal,
  :spend-shock). Each out: :Y, :int-paid, :int-recv {sector} and :margin
  over output, :gov-debt, :private-debt, :identity (the largest of the
  matrix, flow-of-funds and balance-sheet gaps over output)."
  [panel kn iso o years opts]
  (let [kp (sa/known-panel panel (+ o sfc/vintage))
        p (sfc/learn kp kn iso o opts)
        st (sfc/init kp kn iso o p)
        run (fn [p] (loop [st st i 0 acc (sorted-map)]
                      (if (= i years)
                        acc
                        (let [{s :state out :out} (sfc/step st p)
                              y (:Y out) x (:x out)
                              rel (fn [m] (into {} (for [[k v] m] [k (/ (double v) y)])))]
                          (recur s (inc i)
                                 (assoc acc (:year out)
                                        {:Y y :int-paid (rel (:int-paid x)) :int-recv (rel (:int-recv x))
                                         :margin (/ (sfc/margin-of x) y)
                                         :gov-debt (:gov-debt out) :private-debt (:private-debt out)
                                         :identity (/ (max (:tfm-gap out) (:fof-gap out) (:bsm-gap out)) y)}))))))]
    {:base (run p)
     :rate (run (sfc/policy-shock p (inc o) 0.01))
     :fiscal (run (assoc p :spend-shock (into {} (for [y (range (inc o) (+ o 6))] [y 0.01]))))}))

(defn scenario-report
  "Lines: for each rule set in `models` {name opts}, over countries' runs
  `runs` {iso {name scenario-paths}}, the median across countries of each
  scenario's difference from the base (over output) in interest paid by
  households, NFCs and government, the margin, the domestic receipts and
  government debt, at years 1, 3, 5 and 10, and the largest identity gap."
  [runs models]
  (let [med (fn [xs] (let [v (vec (sort xs))] (when (seq v) (nth v (quot (count v) 2)))))
        f (fn [x] (if x (format "%+.5f" x) "-"))
        q {"gov interest" #(get-in % [:int-paid :gov]) "hh interest" #(get-in % [:int-paid :hh])
           "nfc interest" #(get-in % [:int-paid :nfc]) "margin" :margin
           "domestic receipts" #(+ (get-in % [:int-recv :hh]) (get-in % [:int-recv :nfc]) (get-in % [:int-recv :gov]))
           "gov debt" :gov-debt}]
    (concat
     [(format "countries %d" (count runs))]
     (for [k (keys models) sc [:rate :fiscal] [label g] q]
       (let [at (fn [i] (med (for [[_ r] runs
                                   :let [sp (get r k) ys (vec (keys (:base sp)))]
                                   :when (>= (count ys) i)
                                   :let [y (ys (dec i))]]
                               (- (g (get-in sp [sc y])) (g (get-in sp [:base y]))))))]
         (str (format "%-10s %-6s %-18s" (name k) (name sc) label)
              (str/join "  " (for [i [1 3 5 10]] (str "y" i " " (f (at i))))))))
     [(format "largest identity gap over output, every run, year and scenario: %.2e"
              (double (reduce max 0.0 (for [[_ r] runs [_ sp] r [_ path] sp [_ o] path] (:identity o)))))])))


(defn report
  "The registered test's lines and verdicts from scored rows (each with :f
  {model value}): per split, outcome and horizon group, the estimated
  core's skill against each baseline with its interval (interval); the
  verdicts (overall) for the holdout (primary) and the test origins
  (secondary) with their bounds {split {hg {outcome b}}}; per sector,
  without verdicts."
  [scored bounds]
  (let [rows (filter (fn [r] (every? #(number? (get-in r [:f %])) (cons :estimated baselines))) scored)
        sel (fn [split pred hs] (filter #(and (= split (:split %)) (pred (:outcome %)) (hs (:h %))) rows))
        outs {:nl #(= :nl (first %)) :debt #(= [:debt] %)}
        ci (fn [split rs x] (when (seq rs) (interval split rs :estimated x 2000 20261029)))
        f (fn [[p lo hi]] (if p (format "%+.3f [%+.3f, %+.3f]" p lo hi) "-"))
        lines (atom [])
        say (fn [& xs] (let [l (apply str xs)] (println l) (flush) (swap! lines conj l)))
        cells (fn [split] (into {} (for [x baselines [hg hs] hgroups]
                                     [[x hg] (into {} (for [[o pred] outs] [o (ci split (sel split pred hs) x)]))])))]
    (say (format "rows scored (every model present): %d of %d" (count rows) (count scored)))
    (doseq [split [:holdout :test :design] [o pred] outs]
      (say (format "%s %s: rows h1 %d, h3 %d, h5 %d; countries %d; origins %d" (name split) (name o)
                   (count (sel split pred #{1})) (count (sel split pred #{3})) (count (sel split pred #{5}))
                   (count (distinct (map :iso (sel split pred #{1 3 5})))) (count (distinct (map (juxt :iso :o) (sel split pred #{1 3 5})))))))
    (doseq [split [:holdout :test :design] [o pred] outs]
      (say (str "## " (name split) ", " (if (= o :nl) "net lending (four sectors)" "private debt")))
      (doseq [x baselines]
        (say (format "  estimated vs %-12s h1 %s   h3-5 %s   h3 %s   h5 %s" (name x)
                     (f (ci split (sel split pred #{1}) x)) (f (ci split (sel split pred #{3 5}) x))
                     (f (ci split (sel split pred #{3}) x)) (f (ci split (sel split pred #{5}) x))))))
    (doseq [split [:holdout :test] s h/nl-sectors]
      (say (format "  by sector (no verdict), %s nl %-4s vs persistence h1 %s  h3-5 %s" (name split) (name s)
                   (f (ci split (sel split #(= [:nl s] %) #{1}) :persistence)) (f (ci split (sel split #(= [:nl s] %) #{3 5}) :persistence)))))
    (doseq [split [:holdout :test] :let [c (cells split)]]
      (doseq [[[x hg] cis] (sort-by str c)]
        (say (format "  cell %s %s vs %s: %s" (name split) (name hg) (name x) (name (h/verdict cis (get-in bounds [split hg]))))))
      (say (format "verdict, %s (%s): %s" (name split) (if (= split :holdout) "primary" "secondary, origins seen twice before")
                   (name (overall c (bounds split))))))
    @lines))

(defn -main [mode & args]
  (let [opts (into {} (for [[k v] (partition-all 2 args)] [k v]))
        panel (sfc/with-policy (sfc/read-panel panel-path) (sfc/read-policy policy-path))
        kn (sfc/read-keen keen-path)
        hold held
        named {"keen" {} "revised" sfc/revised
               "revised+interest" (assoc sfc/revised :interest-hold true :dividend-hold true)
               "hold" hold
               "hold+fiscal" (assoc hold :fiscal (:fiscal sfc/estimated))
               "hold+investment" (assoc hold :investment (:investment sfc/estimated))
               "estimated" sfc/estimated
               "slopes-by-country" (assoc sfc/estimated :fiscal {:estimate true} :investment {:estimate true})
               "+consumption-ec" (assoc sfc/estimated :consumption-ec {:estimate true})
               "+other-ec" (assoc sfc/estimated :other-ec {:phi 0.3})
               "+interest-adjust" (assoc sfc/estimated :interest-adjust {})
               ;; bnm-4hv.15's search on the design origins (2a), each a change from the first form
               "rates" sfc/rates
               "rates-first" (assoc sfc/rates :rates {:estimate true})
               "rates-margin-output" (assoc sfc/rates :rates {:estimate true :margin-base :output})
               "rates-mix" (assoc sfc/rates :rates {:estimate true :margin-base :output :stock :mix})
               "rates-mix-held" (assoc sfc/rates :rates {:estimate true :margin-base :output :stock :mix :pass-through false})
               "rates-open-held" (assoc sfc/rates :rates {:estimate true :margin-base :output :pass-through false})
               "rates-anchor" (assoc sfc/rates :rates {:estimate true :margin-base :output :anchor true})
               "rates-anchor-mix" (assoc sfc/rates :rates {:estimate true :margin-base :output :anchor true :stock :mix})
               "rates-anchor-held" (assoc sfc/rates :rates {:estimate true :margin-base :output :anchor true :pass-through false})
               "rates-anchor-recv" (assoc sfc/rates :rates {:estimate true :margin-base :output :anchor true :recv :share})
               "rates-held" (update sfc/rates :rates assoc :pass-through false)}
        ks (str/split (get opts "--rules" "keen,revised") #",")
        models (vec (for [k ks] [(keyword k) (or (named k) (throw (ex-info "unknown rules" {:rules k})))]))]
    (case mode
      "mask"
      (let [hp (merge panel (sfc/with-policy (sfc/read-panel holdout-path) (sfc/read-policy policy-path)))
            rows (mask hp (set (keys (sfc/read-panel holdout-path))) (= "true" (get opts "--all")))]
        (doseq [[k v] (sort-by str (group-by (juxt :split :h) rows))]
          (println k (count v) "rows," (count (distinct (map :iso v))) "countries," (count (distinct (map (juxt :iso :o) v))) "origins"))
        (println "holdout countries:" (sort (distinct (map :iso (filter #(= :holdout (:split %)) rows))))))
      "power"
      (let [hp (merge panel (sfc/read-panel holdout-path))
            rows (mask hp (set (keys (sfc/read-panel holdout-path))) false)]
        (doseq [split (if (= "holdout" (get opts "--split")) [:holdout] [:holdout :test]) [hg hs] hgroups [o pred] {:nl #(= :nl (first %)) :debt #(= [:debt] %)}
                :let [rs (filter #(and (= split (:split %)) (hs (:h %)) (pred (:outcome %))) rows)]]
          (println (name split) (name hg) (name o) (count rs) "rows; pass rate by true skill:" (power split rs {})) (flush)))
      "test"
      (let [hold (sfc/read-panel holdout-path)
            hp (merge panel (sfc/with-policy hold (sfc/read-policy policy-path)))
            rows (mask hp (set (keys hold)) (= "true" (get opts "--all")))
            scored (vec (for [iso (sort (distinct (map :iso rows)))
                              :let [rs (filter #(= iso (:iso %)) rows)
                                    fs (forecasts hp kn iso rs)
                                    _ (println iso (count rs) "rows") _ (flush)]
                              r rs]
                          (assoc r :f (fs [(:o r) (:h r) (:outcome r)]))))]
        (spit (get opts "--out") (with-out-str (clojure.pprint/pprint scored))))
      "score"
      (let [scored (read-string (slurp (get opts "--in")))
            bounds (read-string (get opts "--bounds"))
            ls (report scored bounds)]
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" ls) "\n"))))
      "rates-shape"
      (let [hp (merge panel (sfc/with-policy (sfc/read-panel holdout2-path) (sfc/read-policy policy-path)))
            rows (mask hp (set (keys (sfc/read-panel holdout2-path))) false)
            shape (mapv #(select-keys % [:iso :o :h :outcome]) (filter #(= :holdout (:split %)) rows))]
        (doseq [[k v] (sort-by str (group-by (juxt :split :h) rows))]
          (println k (count v) "rows," (count (distinct (map :iso v))) "countries," (count (distinct (map (juxt :iso :o) v))) "origins"))
        (spit (get opts "--out") (with-out-str (clojure.pprint/pprint shape))))
      "rates-test"
      (let [h2 (sfc/read-panel holdout2-path)
            hp (merge panel (sfc/with-policy h2 (sfc/read-policy policy-path)))
            rows (mask hp (set (keys h2)) false)
            ms {:rates sfc/rates :estimated sfc/estimated :restart {}}
            scored (vec (for [iso (sort (distinct (map :iso rows)))
                              :let [rs (filter #(= iso (:iso %)) rows)
                                    fs (forecasts-of hp kn iso rs ms)
                                    _ (println iso (count rs) "rows") _ (flush)]
                              r rs]
                          (assoc r :f (fs [(:o r) (:h r) (:outcome r)]))))]
        (spit (get opts "--out") (with-out-str (clojure.pprint/pprint scored))))
      "rates-score"
      (let [scored (read-string (slurp (get opts "--in")))
            null (read-string (slurp (get opts "--null")))
            {ls :lines} (rates-report scored null 0.05)]
        (doseq [l ls] (println l))
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" ls) "\n"))))
      "rates-freeze"
      (let [h1 (sfc/read-panel holdout-path) h2 (sfc/read-panel holdout2-path)
            pol (sfc/read-policy policy-path)
            hp (merge panel (sfc/with-policy h1 pol) (sfc/with-policy h2 pol))
            rows (freeze-rows hp)
            ms {:rates sfc/rates :estimated sfc/estimated :restart {}}
            frozen (vec (for [iso (sort (distinct (map :iso rows)))
                              :let [rs (filter #(= iso (:iso %)) rows) fs (forecasts-of hp kn iso rs ms)]
                              r rs]
                          (assoc r :f (fs [(:o r) (:h r) (:outcome r)]))))]
        (println (count frozen) "rows," (count (distinct (map :iso frozen))) "countries; targets" (sort (distinct (map #(+ (:o %) (:h %)) frozen))))
        (spit (get opts "--out") (with-out-str (clojure.pprint/pprint frozen))))
      "scenario"
      (let [ms (into {} (for [k ["rates" "estimated" "keen"]] [(keyword k) (named k)]))
            runs (into (sorted-map)
                       (for [iso (h/countries panel) :when (not (h/hyperinflation? panel iso))
                             :let [o (last (h/measured-years panel iso))]
                             :when (and o (number? (get-in panel [iso o :policy])))]
                         [iso (into {} (for [[k opts] ms] [k (scenario-paths panel kn iso o 10 opts)]))]))
            ls (scenario-report runs ms)]
        (doseq [l ls] (println l))
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" ls) "\n"))))
      "estimates"
      (let [ls (concat
                ["# Pooled error-correction fits, data years to 2002 (bnm.sfc/rule-specs; regressors: own gap to norm, other gaps)"
                 "# loco: one-year skill against holding the ratio (dx = 0), each country left out of the pooled fit"]
                (for [k [:fiscal :investment :consumption :wages :fin-va :other-fin :hh-debt :nfc-debt]
                      :let [rows (sfc/ec-rows panel design-to (sfc/rule-specs k)) e (sfc/partial-pool rows)]]
                  (format "%-12s pooled %s  se %s  tau2 %s  rows %d  countries %d  loco %+.3f" (name k)
                          (mapv #(format "%.3f" (double %)) (:pooled e))
                          (mapv #(format "%.3f" (Math/sqrt (double %))) (:v (sfc/ols (apply concat (vals rows)))))
                          (mapv #(format "%.4f" (double %)) (:tau2 e)) (:n e) (:countries e) (loco rows)))
                [(let [e (sfc/ols (apply concat (vals (sfc/interest-rows panel design-to 10.0)))) ]
                   (format "interest     lambda %.3f  se %.3f  rows %d (implicit rate less policy rate, to its norm)" (first (:b e)) (Math/sqrt (first (:v e))) (:n e)))])]
        (doseq [l ls] (println l))
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" ls) "\n"))))
      "lambda"
      (doseq [o [1995 2002] :let [e (sfc/ols (apply concat (vals (sfc/interest-rows panel o 10.0))))]]
        (println o "interest partial adjustment" e))
      "held"
      (let [scored (read-string (slurp (get opts "--in")))
            hp (merge panel (sfc/with-policy (sfc/read-panel holdout-path) (sfc/read-policy policy-path)))
            out (add-model hp kn scored :hold held)
            ls (held-report out)]
        (when-let [o (get opts "--edn")] (spit o (with-out-str (clojure.pprint/pprint out))))
        (doseq [l ls] (println l))
        (when-let [o (get opts "--out")] (spit o (str (str/join "\n" ls) "\n"))))
      "held-report"
      (let [ls (held-report (read-string (slurp (get opts "--in"))))]
        (doseq [l ls] (println l))
        (when-let [o (get opts "--out")] (spit o (str (str/join "\n" ls) "\n"))))
      "interest-drift"
      (let [os (distinct (map (juxt :iso :o) (filter #(= :test (:split %)) (mask panel #{} false))))
            q (fn [xs p] (let [v (vec (sort xs))] (when (seq v) (nth v (min (dec (count v)) (long (* p (count v))))))))
            ls (cons (format "test origins %d; ratio at o + 5: [10%%, 50%%, 90%%]" (count os))
                     (for [[s m] (sort-by key (interest-drift panel kn os sfc/estimated))]
                       (format "%-4s implied rate / revised rate, model %s (n %d); implicit rate o+5 / o, data %s (n %d); liabilities/Y change, model %s data %s"
                               (name s) (mapv #(some->> (q (:model m) %) (format "%.2f")) [0.1 0.5 0.9]) (count (:model m))
                               (mapv #(some->> (q (:data m) %) (format "%.2f")) [0.1 0.5 0.9]) (count (:data m))
                               (mapv #(some->> (q (map first (:stock m)) %) (format "%+.3f")) [0.1 0.5 0.9])
                               (mapv #(some->> (q (map second (:stock m)) %) (format "%+.3f")) [0.1 0.5 0.9]))))]
        (doseq [l ls] (println l))
        (when-let [o (get opts "--out")] (spit o (str (str/join "\n" ls) "\n"))))
      "design"
      (let [os (design-origins panel design-to)
            rows (rows-of panel kn os models)
            tt (some-> (get opts "--target-to") parse-long)
            early (when tt (filterv #(<= (+ (:o %) (:h %)) tt) rows))
            vs (some->> (get opts "--vs") (#(str/split % #",")) (mapv keyword))
            ls (concat (cons (format "design origins %d (%d countries), rows %d" (count os) (count (distinct (map first os))) (count rows))
                             (skills rows (map first models) vs))
                       (when tt
                         (cons (format "# targets to data year %d only: rows %d (h1 %d, h3 %d, h5 %d)" tt (count early)
                                       (count (filter #(= 1 (:h %)) early)) (count (filter #(= 3 (:h %)) early)) (count (filter #(= 5 (:h %)) early)))
                               (skills early (map first models) vs))))]
        (when-let [e (get opts "--edn")] (spit e (with-out-str (clojure.pprint/pprint (mapv #(assoc % :split :design) rows)))))
        (doseq [l ls] (println l))
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" ls) "\n")))))
    (shutdown-agents)))
