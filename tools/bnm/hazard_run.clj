(ns bnm.hazard-run
  "The registered run of the pooled hazard and the scanner (docs/PHASE-
  CHANGES.md, \"Registered, 11 November 2026: one pooled hazard over the
  buffer invariants, and the scanner\"; bead bnm-56c.6). Run once:

    jolt -A:tools -M -m bnm.hazard-run [--out data/derived/hazard] [--reps 1000]

  writes <out>/report.txt (every test, per family), <out>/preds.tsv (the
  rolling-origin and leave-one-family-out scores), <out>/hidden.tsv (1.7's
  flags), and the forward ranking to registered/ledger/2026-Q4/scanner.tsv
  with its manifest scanner.edn (new files beside the issue)."
  (:require [augur.logit :as lg]
            [augur.math :as m]
            [bnm.dev-folds :as df]
            [bnm.episodes :as ep]
            [bnm.hazard :as hz]
            [bnm.hazard-data :as hd]
            [bnm.scanner :as scan]
            [bnm.sfc-rates-confirm :as sfc]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.string :as str]))

(def seed 56)
(def blocks "Refit years; each block scores its year and the next four." [1980 1985 1990 1995 2000 2005 2010 2015 2020])
(def last-test {12 2024 24 2023})
(def eras [["1980-1994" 1980 1994] ["1995-2007" 1995 2007] ["2008-2024" 2008 2024]])
(def issue {:quarter "2026-Q4" :date "2026-11-11" :as-of (+ 2026.0 (/ 10.0 12.0) (/ 10.0 365.25)) :month (+ (* 12 2026) 10)})
(def physical #{"commodity_stockout" "energy_break"})
(def affected-codes #{"GBR" "GEO" "COD" "COG" "BFA" "BES"})

(defn- pmapv
  "f over xs on up to `n` threads, in order."
  [n f xs]
  (let [xs (vec xs) parts (partition-all (max 1 (long (Math/ceil (/ (count xs) (double n))))) xs)]
    (vec (mapcat deref (doall (map (fn [p] (future (mapv f p))) parts))))))

;; --- features ------------------------------------------------------------------------------

(defn- strip-revised
  "The series without the roles a revised vintage fills."
  [rec series]
  (apply dissoc series (:revised-roles rec)))

(defn feature-table
  "{[id year] features} for every record and origin year (as of 1
  January), `series-of` giving a record's series."
  [records series-of years]
  (into {} (pmapv 10 (fn [rec]
                       (let [s (series-of rec)]
                         (into {} (for [y years
                                        :let [f (scan/stock-features rec s (double y))]
                                        :when (hz/measured? f)]
                                    [[(:id rec) y] f]))))
                  records)))

(defn shuffled
  "A record's series with each one's values shuffled in time (each series
  its own stream of augur.math/rng-mixed seed, decoy d, record i)."
  [series d i]
  (into {} (map-indexed
            (fn [j [role os]]
              (let [g (m/rng-mixed seed d i j)
                    vs (mapv second (sort-by first (map (fn [o] [(m/next-double! g) (:v o)]) os)))]
                [role (mapv #(assoc %1 :v %2) os vs)]))
            (sort-by key series))))

;; --- rows ----------------------------------------------------------------------------------------

(defn model-rows
  "Labelled rows with features: each panel row joined to its family's
  record at its place."
  [labels records ftab]
  (let [idx (into {} (for [r records f (:families r)] [[f (get-in r [:holder :place])] r]))]
    (vec (for [l labels
               :let [rec (idx [(:family l) (:place l)])
                     f (when rec (ftab [(:id rec) (:year l)]))]
               :when f]
           (assoc l :id (:id rec) :f f)))))

(defn- block-of [y] (last (filter #(<= % y) blocks)))

(defn- in-block? [w Y y] (and (<= Y y (+ Y 4)) (<= y (last-test w))))

(defn- test-episodes [w Y]
  (into #{} (mapcat #(df/episodes-of {:lo (dec (* 12 %)) :hi (+ (* 12 %) w -1)} df/global-episodes)) (range Y (+ Y 5))))

(defn train-set
  "The rows a block's fit trains on: hz/trainable at the block's first
  origin, without the families `out`, and without rows whose label touches
  a global episode the block's scored windows touch."
  [rows w labels Y out]
  (let [eps (test-episodes w Y)]
    (filterv #(and (not (contains? out (:family %)))
                   (empty? (set/intersection eps (df/episodes-of {:lo (dec (* 12 (:year %))) :hi (+ (* 12 (:year %)) w -1)} df/global-episodes))))
             (hz/trainable rows (* 12 Y) w labels))))

(defn- fit-rows [rows w labels]
  (mapv #(hash-map :f (:f %) :y (hz/label % w labels) :family (:family %)) rows))

(defn block-fits
  "{Y model} for each block, trained without the families `out`."
  [rows w labels out]
  (into {} (pmapv 9 (fn [Y] [Y (hz/fit (fit-rows (train-set rows w labels Y out) w labels))]) blocks)))

;; --- measures ---------------------------------------------------------------------------------------

(defn- auc-of [rs k] (hz/auc (map k rs) (map :y rs)))

(defn summary
  "AUC with its cluster interval, recall and precision at the top 10%,
  base rate, n and positives of scored rows {:p :y :place}."
  [rs reps]
  (let [rs (vec rs)]
    {:n (count rs) :pos (count (filter :y rs))
     :base (when (seq rs) (/ (count (filter :y rs)) (double (count rs))))
     :auc (auc-of rs :p)
     :auc-ci (hz/interval (hz/cluster-draws rs :place #(auc-of % :p) reps seed))
     :recall10 (hz/recall-at (map :p rs) (map :y rs) 0.1)
     :recall10-ci (hz/interval (hz/cluster-draws rs :place #(hz/recall-at (map :p %) (map :y %) 0.1) reps seed))}))

(defn- diff-ci [rs ka kb reps]
  (let [d #(let [a (auc-of % ka) b (auc-of % kb)] (when (and a b) (- a b)))]
    {:diff (d rs) :ci (hz/interval (hz/cluster-draws rs :place d reps seed))}))

;; --- baselines ------------------------------------------------------------------------------------------

(def main-role [:stock :claims :price :inflow :outflow])

(defn level-of
  "The log of the latest known value of a record's main series as of t."
  [rec series t]
  (let [role (or (:displace rec) (first (filter series main-role)))
        o (peek (scan/known-view (get series role) t false))]
    (when (and o (pos? (double (:v o)))) (Math/log (double (:v o))))))

(defn- logit-fit
  "augur.logit/fit on rows {:x :y}; nil without both outcomes."
  [rows k]
  (when (and (some :y rows) (some (complement :y) rows))
    (lg/fit (map #(assoc % :w 1.0) rows) k {:prior 1.0})))

(defn channel-x
  "The channel inputs of a record as of t: for each role, the log of its
  latest known value (the value itself when not positive) and its change
  over a year, with missing indicators."
  [rec series t]
  (vec (cons 1.0
             (mapcat (fn [role]
                       (let [v (scan/known-view (get series role) t (contains? scan/flow-roles role))
                             a (peek v) b (when (>= (count v) 2) (v (- (count v) 2)))
                             lv (fn [o] (let [x (double (:v o))] (if (pos? x) (Math/log x) x)))]
                         (if a
                           [(lv a) (if b (- (lv a) (lv b)) 0.0) 0.0 (if b 0.0 1.0)]
                           [0.0 0.0 1.0 1.0])))
                     (sort (keys series))))))

(defn last12-index
  "{place sorted [known month]} of every episode's first known month, and
  the same by [place family]."
  []
  (let [eps (vals (group-by :episode (ep/episodes)))
        one (map first eps)]
    {:any (reduce (fn [m r] (update m (:place r) (fnil conj []) (hz/month-of (:episode_known r)))) {} one)
     :fam (reduce (fn [m r] (update m [(:place r) (:family r)] (fnil conj []) (hz/month-of (:episode_known r)))) {} one)}))

(defn- last12 [ms T] (if (some #(and % (< (- T 12) % (inc T))) ms) 1.0 0.0))

;; --- the run -------------------------------------------------------------------------------------------------

(defn- f3 [x] (if (number? x) (format "%.3f" (double x)) "-"))
(defn- ci [[a b]] (if a (format "[%s, %s]" (f3 a) (f3 b)) "[-]"))

(defn hidden-flags
  "1.7: residuals and gaps between measures, flagged as of `as-of`."
  [as-of]
  (let [il (hd/read-source {:file "data/derived/buffers/reserves-monthly.csv" :key :iso :time :month :value :reserves_ex_gold_usd :lag 2})
        il-total (hd/read-source {:file "data/derived/buffers/reserves-monthly.csv" :key :iso :time :month :value :reserves_total_usd :lag 2})
        wdi (hd/read-source {:file "data/derived/buffers/external-annual.csv" :key :iso :time :year :value :reserves_ex_gold_usd :lag 12})
        irfcl (hd/read-source {:file "data/derived/buffers/irfcl-monthly.csv" :key :iso :time :month :value :official_reserve_assets :lag 1})
        eia (let [c (get (hd/read-source {:file "data/derived/buffers/energy-us.csv" :place "USA" :time :date :value :value :filter {:series ["crude_ex_spr"] :frequency ["M"]} :lag 2}) "USA")
                  s (into {} (map (juxt :t identity)) (get (hd/read-source {:file "data/derived/buffers/energy-us.csv" :place "USA" :time :date :value :value :filter {:series ["crude_spr"] :frequency ["M"]} :lag 2}) "USA"))]
              (vec (for [o c :let [q (s (:t o))] :when q] (assoc o :v (+ (:v o) (:v q)) :known (max (:known o) (:known q))))))
        jodi (get (hd/read-source {:file "data/derived/buffers/jodi-oil-monthly.csv" :key :iso :time :month :value :kbbl :filter {:product ["CRUDEOIL"] :flow ["CLOSTLV"]} :lag 2}) "USA")
        sfc (let [rows (hd/read-source {:file "data/derived/sfc/flows.csv" :key :iso :time :year :value :value :filter {:item ["nl"] :sector ["hh" "nfc" "fin" "gov" "row"]} :sum true :lag 24})
                  den (hd/read-source {:file "data/derived/sfc/flows.csv" :key :iso :time :year :value :value :filter {:item ["b5g"] :sector ["tot"]} :lag 24})]
              (into {} (for [[p os] rows :let [d (into {} (map (juxt :t :v)) (get den p))]]
                         [p (vec (for [o os :let [g (d (:t o))] :when (and g (pos? g))] (assoc o :v (/ (:v o) g))))])))
        annual (fn [os] (filterv #(< (Math/abs (- (:t %) (Math/rint (:t %)))) 1e-6) os))
        flag (fn [kind place os] (when-let [r (scan/residual-flag os as-of {})] (assoc r :kind kind :place place)))]
    (vec (concat
          (for [[p os] il :let [w (get wdi p)] :when w :let [r (flag "reserves: IMF IL against WDI" p (scan/gap-series (annual os) w))] :when r] r)
          (for [[p os] il-total :let [w (get irfcl p)] :when w :let [r (flag "reserves: IRFCL official assets against IMF IL" p (scan/gap-series w os))] :when r] r)
          (when (and (seq eia) (seq jodi)) (keep identity [(flag "US crude: EIA (incl. SPR) against JODI" "USA" (scan/gap-series eia jodi))]))
          (for [[p os] sfc :let [r (flag "SFC: net lending summed over the five sectors / GNI" p os)] :when r] r)))))

(defn -main [& args]
  (let [opts (into {} (for [[k v] (partition 2 args)] [(keyword (subs k 2)) v]))
        out (or (:out opts) "data/derived/hazard")
        reps (or (some-> (:reps opts) parse-long) 1000)
        report (StringBuilder.)
        say (fn [& xs] (let [s (apply str xs)] (println s) (.append report (str s "\n"))))
        t0 (System/currentTimeMillis)
        _ (println "loading stocks")
        {:keys [records series]} (hd/load-stocks)
        recs (vec records)
        panel-recs (filterv #(seq (:families %)) recs)
        years (range 1950 2027)
        _ (println "features" (count recs) "records")
        ftab (feature-table recs #(series (:id %)) years)
        labels (hd/label-rows)
        rows (model-rows labels recs ftab)
        families (sort (distinct (map :family labels)))
        _ (println "rows" (count rows) (- (System/currentTimeMillis) t0) "ms")
        ;; --- (b) the pooled model on every family; the base for (c), hindcasts and the diagnostic
        full (block-fits rows 12 :main #{})
        preds (fn [fits w labels fam-of]
                (vec (for [r rows :let [Y (block-of (:year r))]
                           :when (and Y (in-block? w Y (:year r)))
                           :let [y (hz/label r w labels)] :when (some? y)]
                       (assoc r :y y :p (hz/predict (fits Y) (:f r) (fam-of r)) :pooled (hz/pooled (fits Y) (:f r))))))
        full-preds (preds full 12 :main :family)
        ;; --- (a) leave one family out
        folds (concat (map (fn [f] [f #{f}]) families) [["physical (commodity + energy)" physical]])
        lofo (into {} (pmapv 2 (fn [[nm out]] [nm (block-fits rows 12 :main out)]) folds))
        lofo24 (into {} (pmapv 2 (fn [[nm out]] [nm (block-fits rows 24 :main out)]) folds))
        ftab-rev (feature-table (filterv #(seq (:revised-roles %)) panel-recs) #(strip-revised % (series (:id %))) years)
        rows-rev (vec (keep (fn [r] (if (contains? (set (map :id (filter #(seq (:revised-roles %)) panel-recs))) (:id r))
                                      (when-let [f (ftab-rev [(:id r) (:year r)])] (assoc r :f f))
                                      r))
                            rows))
        lofo-rev (into {} (pmapv 2 (fn [[nm out]] [nm (block-fits rows-rev 12 :main out)]) folds))
        l12 (last12-index)
        rec-by-id (into {} (map (juxt :id identity)) recs)
        level-x (memoize (fn [id year] (let [v (level-of (rec-by-id id) (series id) (double year))]
                                         [1.0 (or v 0.0) (if v 0.0 1.0)])))
        score-fold (fn [nm out fits w labels rs-all]
                     (let [rs (vec (for [r rs-all :when (contains? out (:family r))
                                         :let [Y (block-of (:year r))]
                                         :when (and Y (in-block? w Y (:year r)))
                                         :let [y (hz/label r w labels)] :when (some? y)]
                                     (assoc r :y y :p (hz/pooled (fits Y) (:f r)))))
                           ;; baselines: the lagged level fitted on the family's own rows, and the last 12 months
                           lvl (into {} (for [Y blocks
                                              :let [tr (filterv #(contains? out (:family %)) (hz/trainable rs-all (* 12 Y) w labels))
                                                    b (logit-fit (mapv #(hash-map :x (level-x (:id %) (:year %)) :y (hz/label % w labels)) tr) 3)]]
                                          [Y b]))
                           rs (mapv (fn [r] (let [b (lvl (block-of (:year r))) T (* 12 (:year r))]
                                              (assoc r :level (if b (lg/predict b (level-x (:id r) (:year r))) 0.5)
                                                     :last12 (last12 (get-in l12 [:any (:place r)]) T)
                                                     :last12-fam (last12 (get-in l12 [:fam [(:place r) (:family r)]]) T))))
                                    rs)]
                       {:fold nm :rows rs :summary (summary rs reps)
                        :level (auc-of rs :level) :last12 (auc-of rs :last12) :last12-fam (auc-of rs :last12-fam)
                        :vs-level (diff-ci rs :p :level reps) :vs-last12 (diff-ci rs :p :last12 reps)}))
        verdict (fn [{:keys [fold summary level last12]}]
                  (let [{:keys [pos auc auc-ci recall10]} summary]
                    (cond (= fold "energy_break") "no verdict (thin: registered)"
                          (< pos 30) (str "no verdict (" pos " positives < 30)")
                          (and auc-ci (> (first auc-ci) 0.5) recall10 (> recall10 0.1)
                               (> auc (or level 0.0)) (> auc (or last12 0.0))) "PASS"
                          :else "FAIL")))
        res-a (mapv (fn [[nm out]] (score-fold nm out (lofo nm) 12 :main rows)) folds)
        res-a24 (mapv (fn [[nm out]] (score-fold nm out (lofo24 nm) 24 :main rows)) folds)
        res-rev (mapv (fn [[nm out]] (score-fold nm out (lofo-rev nm) 12 :main rows-rev)) folds)
        res-res (into {} (for [lb [:all :no-gra]] [lb (score-fold "reserve_crisis" #{"reserve_crisis"} (lofo "reserve_crisis") 12 lb rows)]))]
    (say "Pooled hazard over the buffer invariants: the registered run (docs/PHASE-CHANGES.md, 11 November 2026; bead bnm-56c.6).")
    (say (format "Records %d (%d with a family); model rows (place-family-years with an invariant measured, covered, not ongoing) %d; families %s."
                 (count recs) (count panel-recs) (count rows) (str/join ", " families)))
    (say (format "Rows on codes the gazetteer bug touches (%s): %d, of which positive (12m, all labels) %d; by family %s."
                 (str/join " " (sort affected-codes)) (count (filter #(affected-codes (:place %)) rows))
                 (count (filter #(and (affected-codes (:place %)) (:y12 %)) rows))
                 (frequencies (map :family (filter #(affected-codes (:place %)) rows)))))
    (say "")
    (say "(a) Leave one family out, 12-month window, main labels, scored 1980-2024 (development). AUC [90% country-cluster interval], recall at top 10% [interval], baselines' AUC.")
    (say (format "  %-30s %5s %4s %6s  %-22s %-22s %6s %6s %6s  %-26s %-26s  %s" "fold" "n" "pos" "base" "AUC" "recall@10%" "level" "last12" "l12-fam" "AUC-level" "AUC-last12" "verdict"))
    (doseq [r res-a :let [{:keys [n pos base auc auc-ci recall10 recall10-ci]} (:summary r)]]
      (say (format "  %-30s %5d %4d %6s  %s %-15s %s %-15s %6s %6s %6s  %s %-18s %s %-18s  %s" (:fold r) n pos (f3 base) (f3 auc) (ci auc-ci)
                   (f3 recall10) (ci recall10-ci) (f3 (:level r)) (f3 (:last12 r)) (f3 (:last12-fam r))
                   (f3 (:diff (:vs-level r))) (ci (:ci (:vs-level r))) (f3 (:diff (:vs-last12 r))) (ci (:ci (:vs-last12 r))) (verdict r))))
    (say "  reserve_crisis, secondary labels (GRA-only and RR-only breaks counted): "
         (let [s (:summary (res-res :all))] (format "n %d pos %d AUC %s %s recall@10%% %s" (:n s) (:pos s) (f3 (:auc s)) (ci (:auc-ci s)) (f3 (:recall10 s)))))
    (say "  reserve_crisis, without GRA-only breaks (RR-only counted): "
         (let [s (:summary (res-res :no-gra))] (format "n %d pos %d AUC %s %s recall@10%% %s" (:n s) (:pos s) (f3 (:auc s)) (ci (:auc-ci s)) (f3 (:recall10 s)))))
    (say "  energy_break, each episode's rank among the family's scored rows of its year and among all its scored rows:")
    (let [e (:rows (first (filter #(= "energy_break" (:fold %)) res-a)))]
      (doseq [r (filter :y e)]
        (say (format "    %s %d  p %.4f  rank %d of %d (all years)" (:place r) (:year r) (:p r)
                     (inc (count (filter #(> (:p %) (:p r)) e))) (count e)))))
    (say "")
    (say "(a) 24-month window (reported beside): fold, n, pos, AUC [interval], recall@10%, level, last12")
    (doseq [r res-a24 :let [s (:summary r)]]
      (say (format "  %-30s %5d %4d %s %s %s %s %s  %s" (:fold r) (:n s) (:pos s) (f3 (:auc s)) (ci (:auc-ci s)) (f3 (:recall10 s)) (f3 (:level r)) (f3 (:last12 r)) (verdict r))))
    (say "")
    (say "(a) without revised-vintage invariants (sensitivity): fold, n, pos, AUC [interval], recall@10%, verdict")
    (doseq [r res-rev :let [s (:summary r)]]
      (say (format "  %-30s %5d %4d %s %s %s  %s" (:fold r) (:n s) (:pos s) (f3 (:auc s)) (ci (:auc-ci s)) (f3 (:recall10 s)) (verdict r))))
    ;; --- (b) invariants against channels
    (say "")
    (say "(b) Invariants against channels: the pooled model (family included) and a per-family logistic on its raw series, same blocks; AUC by era; diff [cluster interval].")
    (let [chan (into {} (for [fam families Y blocks
                              :let [tr (filterv #(= fam (:family %)) (hz/trainable rows (* 12 Y) 12 :main))
                                    xs (mapv #(hash-map :x (channel-x (rec-by-id (:id %)) (series (:id %)) (double (:year %))) :y (hz/label % 12 :main)) tr)
                                    k (when (seq xs) (count (:x (first xs))))]]
                          [[fam Y] (when k (logit-fit xs k))]))
          cp (mapv (fn [r] (let [b (chan [(:family r) (block-of (:year r))])]
                             (assoc r :chan (if b (lg/predict b (channel-x (rec-by-id (:id r)) (series (:id r)) (double (:year r)))) 0.5))))
                   full-preds)
          cells (vec (for [fam families [era a z] eras
                           :let [rs (filterv #(and (= fam (:family %)) (<= a (:year %) z)) cp)]
                           :when (and (some :y rs) (some (complement :y) rs))]
                       (merge {:fam fam :era era :n (count rs) :pos (count (filter :y rs)) :pooled (auc-of rs :p) :chan (auc-of rs :chan)}
                              (diff-ci rs :p :chan reps))))]
      (doseq [c cells]
        (say (format "  %-22s %-10s n %5d pos %4d  pooled %s  channel %s  diff %s %s" (:fam c) (:era c) (:n c) (:pos c) (f3 (:pooled c)) (f3 (:chan c)) (f3 (:diff c)) (ci (:ci c)))))
      (let [big (filter #(>= (:pos %) 10) cells)
            era-mean (into {} (for [[era] eras :let [ds (keep #(when (= era (:era %)) (:diff %)) cells)]] [era (when (seq ds) (/ (reduce + ds) (count ds)))]))]
        (say (format "  cells with >= 10 positives: pooled >= channel in %d of %d; era means of the difference (families equal): %s"
                     (count (filter #(>= (:diff %) 0.0) big)) (count big)
                     (str/join ", " (for [[e d] era-mean] (str e " " (f3 d))))))
        (say (str "  invariants hold up (registered rule): "
                  (if (and (every? #(and % (>= % 0.0)) (vals era-mean)) (> (* 2 (count (filter #(>= (:diff %) 0.0) big))) (count big))) "YES" "NO")))))
    ;; --- (c) false alarms
    (say "")
    (say "(c) False alarms: real rows against five decoys each (series shuffled in time), ranked together each year by the rolling-origin pooled model.")
    (let [idx (into {} (map-indexed (fn [i r] [(:id r) i]) panel-recs))
          dtabs (vec (for [d (range 1 6)]
                       (feature-table panel-recs #(shuffled (series (:id %)) d (idx (:id %))) (range 1980 2025))))
          real (mapv #(assoc % :decoy false :p (:pooled %)) full-preds)
          decoys (vec (for [d (range 5) r full-preds :let [f ((dtabs d) [(:id r) (:year r)])] :when f]
                        (assoc r :decoy true :f f :p (hz/pooled (full (block-of (:year r))) f))))
          all (into real decoys)
          top (fn [rs] (vec (mapcat (fn [[_ ys]] (let [s (sort-by (comp - :p) ys) k (long (Math/ceil (* 0.1 (count s))))] (take k s))) (group-by :year rs))))
          prec (fn [rs dec?] (let [t (filter #(= dec? (:decoy %)) (top rs))] (when (seq t) (/ (count (filter :y t)) (double (count t))))))
          stat (fn [rs] (let [a (prec rs false) b (prec rs true)] (when (and a b) (- a b))))
          t (top all)]
      (say (format "  rows: real %d, decoys %d; top decile %d rows, of which decoys %d (share %s against %s in all rows)"
                   (count real) (count decoys) (count t) (count (filter :decoy t)) (f3 (/ (count (filter :decoy t)) (double (count t))))
                   (f3 (/ (count decoys) (double (count all))))))
      (let [d (stat all) iv (hz/interval (hz/cluster-draws all :place stat reps seed))]
        (say (format "  precision in the top decile: real %s, decoys %s; difference %s %s -> %s"
                     (f3 (prec all false)) (f3 (prec all true)) (f3 d) (ci iv) (if (and iv (> (first iv) 0.0)) "PASS" "FAIL")))))
    ;; --- development diagnostic: last 20% of origins
    (say "")
    (let [cut 2016
          m (hz/fit (fit-rows (filterv #(not (contains? #{} (:family %))) (hz/trainable rows (* 12 cut) 12 :main)) 12 :main))
          rs (vec (for [r rows :when (<= cut (:year r) 2024) :let [y (hz/label r 12 :main)] :when (some? y)]
                    (assoc r :y y :p (hz/predict m (:f r) (:family r)))))]
      (say "Development diagnostic (not validation): origins 2016-2024 held out, trained on origins to 2014; AUC by family and pooled.")
      (doseq [[fam rs] (sort-by key (group-by :family rs))]
        (say (format "  %-22s n %5d pos %4d AUC %s" fam (count rs) (count (filter :y rs)) (f3 (auc-of rs :p)))))
      (say (format "  all families: AUC %s" (f3 (auc-of rs :p)))))
    ;; --- the model's coefficients (the last block's fit)
    (say "")
    (say "Coefficients of the 2020 block's fit (shared slope, then family deviations), standardized inputs back on their own scale:")
    (doseq [[c [b ds]] (hz/coefficients (full 2020))]
      (say (format "  %-24s %+.3f   %s" (pr-str c) (double b) (str/join " " (for [[g d] (sort-by key ds)] (format "%s %+.2f" (subs g 0 (min 8 (count g))) (double d)))))))
    ;; --- hindcasts
    (say "")
    (say "Hindcasts (illustrations): rank of each stock among every stock measured at the origin, by the rolling-origin pooled hazard.")
    (let [cases [["asia_1997" [1996 1997] #(and (#{"THA" "IDN" "KOR" "MYS" "PHL"} (get-in % [:holder :place])) (seq (:families %)))]
                 ["food_2007_08" [2006 2007] #(str/starts-with? (:id %) "grain/WLD")]
                 ["sri_lanka_2022" [2021 2022] #(= "LKA" (get-in % [:holder :place]))]
                 ["european_gas_2021_22" [2021] #(#{"gas-storage/EUR"} (:id %))]
                 ["telecom_2001" [2000 2001] #(#{"us-investment/communication-equipment" "us-investment/communication-structures" "investment/USA" "equity/USA"} (:id %))]
                 ["us_shale_2015" [2014 2015] #(#{"us-investment/petroleum-gas-structures" "us-investment/mining-machinery" "gas-storage/USA" "us-crude-commercial/USA"} (:id %))]]]
      (doseq [[nm ys pick] cases y ys
              :let [ranked (scan/rank (full (block-of y)) (for [r recs :let [f (ftab [(:id r) y])] :when f] {:record r :features f}))
                    hits (filter #(pick (:record %)) ranked)]]
        (say (format "  %-22s origin %d (%d stocks): %s" nm y (count ranked)
                     (if (seq hits) (str/join "; " (for [h hits] (format "%s #%d (top %.0f%%)" (:id (:record h)) (:rank h) (* 100 (:pct h)))))
                         "no stock measured")))))
    ;; --- (d) forward
    (say "")
    (let [T (:month issue)
          m (hz/fit (fit-rows (hz/trainable rows T 12 :main) 12 :main))
          as-of (:as-of issue)
          stocks (pmapv 10 (fn [r] {:record r :features (scan/stock-features r (series (:id r)) as-of)}) recs)
          ranked (scan/rank m stocks)
          n (count ranked)
          dec (long (Math/ceil (* 0.1 n)))
          in-top (fn [sector] (filter #(and (= sector (:sector (:record %))) (<= (:rank %) dec)) ranked))
          fcols [:cover1<0.5 :cover1<2 :log-cover3 :drain1 :log-gross :log-claim :rigidity :disp5 :disp10 :disp20 :resonance :log1p-sxf]
          lines (cons (str/join "\t" (concat ["issue" "as_of" "rank" "of" "stock" "place" "name" "families" "sector" "p12"] (map name fcols)))
                      (for [s ranked :let [r (:record s)]]
                        (str/join "\t" (concat [(:quarter issue) (:date issue) (:rank s) n (:id r) (get-in r [:holder :place]) (:name r)
                                                (str/join ";" (:families r)) (or (:sector r) "") (format "%.6f" (:p s))]
                                               (map #(if-let [v (get (:features s) %)] (format "%.4f" (double v)) "") fcols)))))
          path (str "registered/ledger/" (:quarter issue) "/scanner.tsv")]
      (say (format "(d) Forward: the scanner as of %s (the %s issue date), fitted on %d rows known by then; %d stocks ranked, top decile = %d."
                   (:date issue) (:quarter issue) (:n m) n dec))
      (say "  Top 20:")
      (doseq [s (take 20 ranked) :let [r (:record s)]]
        (say (format "  %3d %-42s p %.4f  %s" (:rank s) (:id r) (:p s)
                     (str/join " " (for [c fcols :let [v (get (:features s) c)] :when v] (format "%s=%.2f" (name c) (double v)))))))
      (doseq [[q sector] [["energy stocks" "energy"] ["US investment build-out" "us-investment"]]]
        (let [all (filter #(= sector (:sector (:record %))) ranked) top (in-top sector)]
          (say (format "  Registered question: does any of the %s rank in the top decile? %s (%d of %d in it; best %s)"
                       q (if (seq top) "YES" "NO") (count top) (count all)
                       (if-let [b (first all)] (format "%s at #%d" (:id (:record b)) (:rank b)) "-")))
          (say (str "    " (str/join "; " (for [s all] (format "%s #%d" (:id (:record s)) (:rank s))))))))
      (if (.exists (io/file path))
        (say "  " path " exists: not overwritten (an issue's files are never edited).")
        (let [text (str (str/join "\n" lines) "\n")
              commit (str/trim (:out (sh/sh "git" "rev-parse" "HEAD")))]
          (spit path text)
          (spit (str "registered/ledger/" (:quarter issue) "/scanner.edn")
                (pr-str {:issue (:quarter issue) :written (:date issue) :as-of as-of :code-commit commit
                         :command "jolt -A:tools -M -m bnm.hazard-run" :model "bnm.hazard pooled, 12-month window, main labels"
                         :rows (:n m) :stocks n :files {"scanner.tsv" (sfc/sha256-file path)}}))
          (say "  wrote " path " sha256 " (sfc/sha256-file path)))))
    ;; --- 1.7
    (say "")
    (let [hs (hidden-flags (:as-of issue)) fl (filter :flag hs)]
      (say (format "Hidden buffers (1.7, flags only, as of %s): %d residual series checked, %d flagged (latest |residual| > 2%% and stretched past 2 against its last 10):" (:date issue) (count hs) (count fl)))
      (doseq [h (sort-by (comp - #(or % 0) :z) fl)]
        (say (format "  %-55s %-4s t %.2f  residual %+.3f  z %.2f" (:kind h) (:place h) (double (:t h)) (double (:value h)) (double (:z h)))))
      (io/make-parents (str out "/hidden.tsv"))
      (spit (str out "/hidden.tsv") (str "kind\tplace\tt\tresidual\tz\tflag\n"
                                          (str/join "\n" (for [h hs] (str/join "\t" [(:kind h) (:place h) (:t h) (:value h) (:z h) (:flag h)]))) "\n")))
    (spit (str out "/preds.tsv")
          (str "set\tplace\tfamily\tyear\ty\tp\n"
               (str/join "\n" (concat (for [r full-preds] (str/join "\t" ["rolling" (:place r) (:family r) (:year r) (:y r) (:p r)]))
                                      (for [f res-a r (:rows f)] (str/join "\t" [(str "lofo:" (:fold f)) (:place r) (:family r) (:year r) (:y r) (:p r)]))))
               "\n"))
    (say "")
    (say (format "Run time %.1f minutes." (/ (- (System/currentTimeMillis) t0) 60000.0)))
    (spit (str out "/report.txt") (str report))
    (shutdown-agents)))
