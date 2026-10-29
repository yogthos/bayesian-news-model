(ns bnm.sfc-horizon-run
  "SFC free runs scored against the data (beads bnm-2zk, bnm-h0i, bnm-cvs;
  docs/COUPLED-SYSTEMS.md 6e).

    jolt -A:tools -M -m bnm.sfc-horizon-run review --seed 1 [--members 32] [--out FILE]
    jolt -A:tools -M -m bnm.sfc-horizon-run mask|power [--all true]
    jolt -A:tools -M -m bnm.sfc-horizon-run test --seeds 1,2,3 [--isos A,B] [--all true] --out FILE
    jolt -A:tools -M -m bnm.sfc-horizon-run score --in F1,F2 --seeds 1,2,3 --bound-nl B --bound-debt B [--out FILE]

  review: phase B's check (bnm.coupled-run's assimilation lines), per
  country with eight or more measured years: the last data year less five
  is the origin o, the first measured year the start. The assimilated ensemble (bnm.sfc-analysis/free-runs)
  is analysed through engine year o + 2, when o's data are known, and run
  free to o + 5; the restart (bnm.sfc-analysis/restart) is learned at o
  on what is known at o + 2 and started from o's data; persistence holds
  o's data. Each is scored on sector net lending over output (households,
  NFCs, government), mean |error| over data years o + 1 to o + 5. Also the
  analyses' mean |innovation| before and after (sd units) and inflation.
  Writes EDN rows to --out.

  mask, power, test, score: the registered multi-year test (bnm-cvs,
  docs/COUPLED-SYSTEMS.md 9f): its rows, the power simulation that sets
  the equivalence bounds, the forecasts of every model at every row, and
  the skills, intervals and verdicts. --all true keeps the countries the
  registration excludes (a sensitivity)."
  (:require [bnm.sfc :as sfc]
            [bnm.sfc-analysis :as sa]
            [augur.math]
            [clojure.pprint :as pp]
            [clojure.string]))

(def panel-path "data/derived/sfc/panel.csv")
(def keen-path "data/derived/stocks/keen.csv")

(defn measured-years [panel iso]
  (sort (for [[y ob] (get panel iso) :when (and (:flows-ok ob) (:b ob) (= :oecd-t720 (:stocks-source ob)))] y)))

(defn countries
  "Countries with eight or more measured years (bnm.coupled-run's)."
  [panel]
  (filter #(>= (count (measured-years panel %)) 8) (sort (keys panel))))

(defn nl-data
  "Sector `s`'s net lending over output in data year y, or nil."
  [panel iso y s]
  (let [ob (get-in panel [iso y])]
    (when (and ob (:flows-ok ob) (:y ob)) (/ (double (get-in ob [:f s :nl] 0.0)) (double (:y ob))))))

(defn path-at
  "The run's median of `v` at data year y, or nil; nil for a run whose
  assimilated pass diverged (bnm.sfc-analysis/divergence), so it is never
  scored."
  [{:keys [t vars diverged]} v y]
  (let [i (.indexOf ^java.util.List (vec t) y)]
    (when (and (>= i 0) (not diverged)) (get-in vars [v :median i]))))

(defn- mean [xs] (when (seq xs) (/ (reduce + 0.0 xs) (count xs))))

(defn review-row [panel kn iso {:keys [members seed]}]
  (let [ys (measured-years panel iso)
        o (- (last ys) 5)
        fr (sa/free-runs panel kn iso {:start (first ys) :origins [(+ o sfc/vintage)] :years 3 :members members :seed seed})
        run (get fr (+ o sfc/vintage))
        rs (sa/restart panel kn iso o 5)
        errs (fn [f] (keep (fn [[y s]] (let [d (nl-data panel iso y s) x (f y s)] (when (and d x) (Math/abs (- x d)))))
                           (for [y (range (inc o) (+ o 6)) s [:hh :nfc :gov]] [y s])))
        nl (fn [r] (fn [y s] (path-at r (str "sfc/net-lending-" (name s)) y)))
        log (:log fr)]
    {:iso iso :origin o :seed seed :members members :diverged (:diverged run)
     :model (mean (errs (nl run))) :restart (mean (errs (nl rs)))
     :persistence (mean (errs (fn [_ s] (nl-data panel iso o s))))
     :innovation (mean (map :innovation log)) :after (mean (map :after log))
     :inflation (mean (map :inflation log)) :analyses (count log)}))


;; -- the registered multi-year test (bnm-cvs; docs/COUPLED-SYSTEMS.md 9f) -------------------

(def horizons [3 5])
(def nl-sectors [:hh :nfc :fin :gov])
(def test-from 2003)

(defn debt-data
  "Households' and NFCs' debt (F3 + F4 liabilities) over output in data
  year y, measured (Table 720) only, or nil."
  [panel iso y]
  (let [ob (get-in panel [iso y])]
    (when (and ob (:b ob) (:y ob) (= :oecd-t720 (:stocks-source ob)))
      (/ (+ (double (get-in ob [:b :hh :debt :l] 0.0)) (double (get-in ob [:b :nfc :debt :l] 0.0))) (double (:y ob))))))

(defn origins-of
  "`iso`'s origins: measured years o at least five after its first measured
  year, with flows at o and some target year o + h in the data (latest
  vintage) with flows or measured stocks."
  [panel iso]
  (let [ys (measured-years panel iso) start (first ys)]
    (vec (for [o ys :when (and (>= o (+ start 5))
                               (some (fn [h] (or (nl-data panel iso (+ o h) :hh) (debt-data panel iso (+ o h)))) horizons))]
           o))))

(defn hyperinflation?
  "Whether `iso` has a measured year whose nominal output is over twice the
  year before's (BGR 1997): the core's trend growth extrapolates it and its
  ensemble's output runs away (bead bnm-rr7), so the registered test leaves
  such a country out."
  [panel iso]
  (boolean (some (fn [y] (let [a (get-in panel [iso y :y]) b (get-in panel [iso (dec y) :y])]
                           (and a b (pos? (double b)) (> (/ (double a) (double b)) 2.0))))
                 (measured-years panel iso))))

(defn mask
  "The rows to score, [{:iso :o :h :outcome [:nl s] or [:debt] :split}]:
  only where the data have the target and the origin's own value; with
  `all` false the countries hyperinflation? excludes are left out."
  ([panel] (mask panel false))
  ([panel all]
  (vec (for [iso (countries panel) :when (or all (not (hyperinflation? panel iso))) o (origins-of panel iso) h horizons
             out (concat (for [s nl-sectors] [:nl s]) [[:debt]])
             :let [d (if (= :nl (first out)) (nl-data panel iso (+ o h) (second out)) (debt-data panel iso (+ o h)))
                   d0 (if (= :nl (first out)) (nl-data panel iso o (second out)) (debt-data panel iso o))]
             :when (and d d0)]
         {:iso iso :o o :h h :outcome out :split (if (< o test-from) :design :test) :data d}))))

(defn- emit-of [out] (if (= :nl (first out)) (str "sfc/net-lending-" (name (second out))) "sfc/private-debt"))

(defn- obs-of [panel iso out y] (if (= :nl (first out)) (nl-data panel iso y (second out)) (debt-data panel iso y)))

(defn damped
  "Damped reversion: the origin's value moving 0.2 a year to its mean over
  the origin and the four years before (three or more of them)."
  [panel iso out o h]
  (let [xs (keep #(obs-of panel iso out %) (range (- o 4) (inc o))) x0 (obs-of panel iso out o)]
    (when (and x0 (>= (count xs) 3))
      (let [m (/ (reduce + xs) (count xs))] (+ m (* (Math/pow 0.8 h) (- x0 m)))))))

(defn forecasts
  "The forecasts of `iso`'s rows: {[o h outcome] {model value}}, models
  :persistence :damped :restart :restart-cond and per seed [:model s]
  [:model-cond s] (the assimilated ensemble's median, free and given the
  observed government path and NFC investment share). A seed whose
  assimilated pass diverged by the forecast's engine year (bnm.sfc-
  analysis/divergence) gives no value, and is listed under :diverged."
  [panel kn iso rows {:keys [seeds members]}]
  (let [os (sort (distinct (map :o rows)))
        start (first (measured-years panel iso))
        hmax (apply max horizons)
        path (fn [o] (sa/given-path panel iso (range (inc o) (+ o hmax 1))))
        rs (into {} (for [o os] [o {:free (sa/restart panel kn iso o hmax) :cond (sa/restart panel kn iso o hmax (path o))}]))
        fr (into {} (for [s seeds]
                      [s (sa/free-runs panel kn iso {:start start :origins (map #(+ % sfc/vintage) os) :years (- hmax sfc/vintage)
                                                     :members members :seed s :given (fn [t] (path (- t sfc/vintage)))})]))]
    (into {} (for [{:keys [o h outcome]} rows :let [y (+ o h) v (emit-of outcome)]]
               [[o h outcome]
                (merge {:persistence (obs-of panel iso outcome o)
                        :damped (damped panel iso outcome o h)
                        :restart (path-at (get-in rs [o :free]) v y)
                        :restart-cond (path-at (get-in rs [o :cond]) v y)}
                       (when-let [ds (seq (for [s seeds :when (get-in fr [s (+ o sfc/vintage) :diverged])] s))]
                         {:diverged (vec ds)})
                       (into {} (for [s seeds :let [f (get-in fr [s (+ o sfc/vintage)])]]
                                  [[:model s] (path-at f v y)]))
                       (into {} (for [s seeds :let [f (get-in fr [s (+ o sfc/vintage) :conditional])]]
                                  [[:model-cond s] (path-at f v y)])))]))))

(defn- keys-of [m] (if (set? m) (sort-by str m) [m]))

(defn country-sse
  "{iso {model sse}} over `rows` for the models `ks`."
  [rows ks]
  (into {} (for [[iso rs] (group-by :iso rows)]
             [iso (into {} (for [k ks] [k (reduce + 0.0 (map #(let [e (- (double (get-in % [:f k])) (:data %))] (* e e)) rs))]))])))

(defn skill-of
  "1 - SSE_m / SSE_x from per-country sums `cs` over countries `isos`
  (repeats allowed); m a model key or a set of per-seed keys, whose
  skills are averaged."
  [cs isos m x]
  (let [tot (fn [k] (reduce + 0.0 (map #(get-in cs [% k]) isos))) sx (tot x) ms (keys-of m)]
    (when (pos? sx) (/ (reduce + (map #(- 1.0 (/ (tot %) sx)) ms)) (count ms)))))

(defn bootstrap
  "[point lo hi]: skill of m against x over `rows` and its 90% interval
  from resampling countries with replacement (`draws`, seed `seed`)."
  [rows m x draws seed]
  (let [cs (country-sse rows (cons x (keys-of m))) isos (vec (sort (keys cs))) n (count isos)
        g (augur.math/rng seed)
        qs (vec (sort (keep identity (for [_ (range draws)]
                                       (skill-of cs (repeatedly n #(isos (long (* n (augur.math/next-double! g))))) m x)))))
        q (fn [p] (nth qs (min (dec (count qs)) (long (* p (count qs))))))]
    [(skill-of cs isos m x) (q 0.05) (q 0.95)]))

(defn power
  "The skill against a comparator at which the test (lower end of the 90%
  country-bootstrap interval above 0) passes 80% of the time, on the
  rows' mask (countries, origins, horizons, sectors), by simulation:
  comparator errors with a country scale (lognormal, sd 0.5), an AR(1)
  over a country's origins (rho), independent across horizons' and
  sectors' series; the model's errors correlated kappa with the
  comparator's, scaled so the true skill is s. {s pass-rate}."
  [rows {:keys [rho kappa sims draws] :or {rho 0.8 kappa 0.7 sims 300 draws 500}}]
  (let [g (augur.math/rng 20261028)
        n! #(augur.math/normal! g)
        keyed (group-by (juxt :iso :h :outcome) rows)]
    (into (sorted-map)
          (for [s [0.0 0.05 0.10 0.15 0.20 0.25 0.30]]
            [s (/ (count (filter true?
                                 (for [_ (range sims)]
                                   (let [scale (into {} (for [iso (distinct (map :iso rows))] [iso (Math/exp (* 0.5 (n!)))]))
                                         sim (vec (mapcat (fn [[[iso _ _] rs]]
                                                            (let [rs (sort-by :o rs)]
                                                              (loop [rs rs ex nil em nil acc []]
                                                                (if-let [r (first rs)]
                                                                  (let [zx (n!) zm (+ (* kappa zx) (* (Math/sqrt (- 1 (* kappa kappa))) (n!)))
                                                                        ex (if ex (+ (* rho ex) (* (Math/sqrt (- 1 (* rho rho))) zx)) zx)
                                                                        em (if em (+ (* rho em) (* (Math/sqrt (- 1 (* rho rho))) zm)) zm)
                                                                        sc (scale iso)]
                                                                    (recur (rest rs) ex em (conj acc {:iso iso :data 0.0 :f {:x (* sc ex) :m (* sc (Math/sqrt (- 1 s)) em)}})))
                                                                  acc))))
                                                          keyed))
                                         [_ lo _] (bootstrap sim :m :x draws (long (* 1e6 (augur.math/next-double! g))))]
                                     (> lo 0.0)))))
                  (double sims))]))))

(defn verdict
  "The registered verdict of a comparison over outcomes, from each
  outcome's [point lo hi] and its equivalence bound: :pass when every
  lower end is above 0; :worse when some upper end is below 0; :equivalent
  (to nothing) when every upper end is below its bound; else :inconclusive."
  [cis bounds]
  (cond (every? (fn [[k [_ lo _]]] (> lo 0.0)) cis) :pass
        (some (fn [[k [_ _ hi]]] (< hi 0.0)) cis) :worse
        (every? (fn [[k [_ _ hi]]] (< hi (bounds k))) cis) :equivalent
        :else :inconclusive))

(defn diverged-line
  "The report's line on the rows left out because an ensemble seed's
  assimilated pass diverged (the bnm-tvk guard): every model's value at
  such a row is dropped with it, as scoring is paired."
  [scored]
  (let [dv (filter #(seq (get-in % [:f :diverged])) scored)]
    (format "rows excluded because an ensemble seed diverged (bnm-tvk guard): %d row%s%s" (count dv) (if (= 1 (count dv)) "" "s")
            (if (seq dv)
              (str ", " (clojure.string/join " " (for [[iso g] (sort-by key (group-by :iso dv))]
                                                   (str iso " seeds " (clojure.string/join "," (sort (distinct (mapcat #(get-in % [:f :diverged]) g))))
                                                        " from o " (apply min (map :o g))))))
              ""))))

(defn report
  "The registered test's tables and verdicts from the scored rows."
  [scored seeds bounds]
  (let [A (set (for [s seeds] [:model s])) Ac (set (for [s seeds] [:model-cond s]))
        need (concat [:persistence :damped :restart :restart-cond] A Ac)
        rows (filter (fn [r] (every? #(number? (get-in r [:f %])) need)) scored)
        sel (fn [split out h] (filter #(and (= split (:split %)) (= out (first (:outcome %))) (or (nil? h) (= h (:h %)))) rows))
        cmp [["ensemble vs restart" A :restart] ["ensemble vs persistence" A :persistence] ["ensemble vs damped" A :damped]
             ["restart vs persistence" :restart :persistence] ["restart vs damped" :restart :damped]
             ["cond. ensemble vs ensemble" Ac (first (sort-by str A))] ["cond. restart vs restart" :restart-cond :restart]
             ["cond. ensemble vs persistence" Ac :persistence] ["cond. restart vs persistence" :restart-cond :persistence]]
        ci (fn [rs m x] (bootstrap rs m x 2000 20261028))
        lines (atom [])
        say (fn [& xs] (let [l (apply str xs)] (println l) (swap! lines conj l)))]
    (say (format "rows scored (all models present): %d of %d" (count rows) (count scored)))
    (say (diverged-line scored))
    (doseq [split [:test :design] out [:nl :debt]]
      (say (format "%s %s: rows h3 %d, h5 %d, countries %d" (name split) (name out) (count (sel split out 3)) (count (sel split out 5))
                   (count (distinct (map :iso (sel split out nil))))))
      (doseq [[label m x] cmp
              :let [r (fn [h] (let [rs (sel split out h)] (when (seq rs) (ci rs m x))))
                    f (fn [[p lo hi]] (format "%+.3f [%+.3f, %+.3f]" p lo hi))]]
        (say (format "  %-30s both %s   h3 %s   h5 %s" label (some-> (r nil) f) (some-> (r 3) f) (some-> (r 5) f)))))
    (let [pooled (fn [m x] (into {} (for [out [:nl :debt]] [out (ci (sel :test out nil) m x)])))
          adopt (verdict (pooled A :restart) bounds)
          best (if (= :pass adopt) A :restart)
          vs-p (pooled best :persistence) vs-d (pooled best :damped)
          horizon (let [v [(verdict vs-p bounds) (verdict vs-d bounds)]]
                    (cond (every? #{:pass} v) :pass (some #{:worse} v) :worse (every? #{:equivalent} v) :equivalent :else :inconclusive))]
      (say "verdict, adoption (ensemble vs restart, both outcomes): " (name adopt))
      (say "verdict, the SFC core at 3-5 years (" (if (= :pass adopt) "ensemble" "restart") " vs persistence and damped reversion, both outcomes): " (name horizon)))
    @lines))

(defn -main [mode & args]
  (let [opts (into {} (for [[k v] (partition-all 2 args)] [k v]))
        members (parse-long (get opts "--members" "32"))
        seed (parse-long (get opts "--seed" "1"))
        out (get opts "--out")
        isos (some-> (get opts "--isos") (clojure.string/split #","))
        all (= "true" (get opts "--all"))
        panel (sfc/read-panel panel-path)
        kn (sfc/read-keen keen-path)]
    (case mode
      "review"
      (let [rows (vec (for [iso (or isos (countries panel))]
                        (let [r (review-row panel kn iso {:members members :seed seed})]
                          (println (format "%s %d seed %d  model %s  restart %.4f  persistence %.4f  innov %.2f -> %.2f  infl %.2f"
                                           iso (:origin r) seed
                                           (if (:diverged r) (str "diverged " (:t (:diverged r)) " " (sort (map name (:reasons (:diverged r)))))
                                               (format "%.4f" (double (:model r))))
                                           (:restart r) (:persistence r)
                                           (double (or (:innovation r) Double/NaN)) (double (or (:after r) Double/NaN))
                                           (double (or (:inflation r) Double/NaN))))
                          (flush)
                          r)))]
        (when out (spit out (with-out-str (pp/pprint rows)))))
      "mask"
      (let [rows (mask panel all) by (group-by (juxt :split :h) rows)]
        (println "rows by split and horizon:" (into (sorted-map) (for [[k v] by] [k (count v)])))
        (println "test countries:" (count (distinct (map :iso (filter #(= :test (:split %)) rows))))
                 "test origins:" (count (distinct (map (juxt :iso :o) (filter #(= :test (:split %)) rows))))))
      "power"
      (let [rows (filter #(= :test (:split %)) (mask panel all))]
        (doseq [[label sel] [["net lending" #(= :nl (first (:outcome %)))] ["private debt" #(= :debt (first (:outcome %)))]]]
          (println label (count (filter sel rows)) "rows; pass rate by true skill:" (power (filter sel rows) {}))))
      "test"
      (let [seeds (mapv parse-long (clojure.string/split (get opts "--seeds" "1,2,3") #","))
            rows (mask panel all)
            isos (or isos (sort (distinct (map :iso rows))))
            scored (vec (for [iso isos
                              :let [rs (filter #(= iso (:iso %)) rows)
                                    fs (forecasts panel kn iso rs {:seeds seeds :members members})
                                    _ (println iso (count rs) "rows") _ (flush)]
                              r rs]
                          (assoc r :f (fs [(:o r) (:h r) (:outcome r)]))))]
        (when out (spit out (with-out-str (pp/pprint scored)))))
      "score"
      (let [seeds (mapv parse-long (clojure.string/split (get opts "--seeds" "1,2,3") #","))
            files (clojure.string/split (get opts "--in") #",")
            scored (vec (mapcat #(read-string (slurp %)) files))
            bounds {:nl (parse-double (get opts "--bound-nl")) :debt (parse-double (get opts "--bound-debt"))}
            ls (report scored seeds bounds)]
        (when out (spit out (str (clojure.string/join "\n" ls) "\n")))))
    (shutdown-agents)))
