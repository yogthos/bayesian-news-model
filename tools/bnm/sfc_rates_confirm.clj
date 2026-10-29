(ns bnm.sfc-rates-confirm
  "The later confirmation of the rate channel (bead bnm-4hv.16;
  docs/COUPLED-SYSTEMS.md 9h, \"Amendment to the later confirmation\"):
  rates-frozen.edn scored against the first OECD Table 14/720 vintage on
  or after 1 November 2027, nothing re-run.

    jolt -A:tools -M -m bnm.sfc-rates-confirm shape --vintage DIR --vintage-date YYYY-MM-DD --out SHAPE
    python -I scripts/sfc-rates-null.py data/derived/sfc/rates-test.edn SHAPE --split test --m rates \\
        --x estimated --sims 2000 --seed 20271101 --edn-out NULL
    jolt -A:tools -M -m bnm.sfc-rates-confirm score --vintage DIR --vintage-date YYYY-MM-DD \\
        --shape SHAPE --null NULL [--out FILE]
    jolt -A:tools -M -m bnm.sfc-rates-confirm published

  DIR holds scripts/sfc-data.py's panel.csv, panel-holdout.csv and
  panel-holdout2.csv built from the vintage (SFC_T14, SFC_T720, SFC_OUT).
  --frozen FILE overrides data/derived/sfc/rates-frozen.edn; its SHA-256
  must be 9h's. published lists the frozen rows whose targets the
  freeze-time panels already had (presence only, no values)."
  (:require [bnm.sfc :as sfc]
            [bnm.sfc-horizon-run :as h]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def frozen-path "data/derived/sfc/rates-frozen.edn")
(def frozen-sha256 "0339880a359941334046d651966facd9d2473811fd205c77143111997eb7ccaf")
(def vintage-from "2027-11-01")
(def excluded-countries #{"BGR" "ROU"})
(def excluded-rows
  "Rows whose targets were published when the forecasts were frozen
  (debt dated by the stocks): [iso o h outcome]."
  #{["KOR" 2024 1 [:debt]] ["NZL" 2023 1 [:debt]]})
(def min-countries 5)
(def delta 0.05)
(def seed 20271101)
(def models [:rates :estimated :persistence :damped :restart])
(def cells [[:h1 :nl] [:h1 :debt] [:h35 :nl] [:h35 :debt]])

;; -- SHA-256 (FIPS 180-4) ---------------------------------------------------------------------

(def ^:private K
  [0x428a2f98 0x71374491 0xb5c0fbcf 0xe9b5dba5 0x3956c25b 0x59f111f1 0x923f82a4 0xab1c5ed5
   0xd807aa98 0x12835b01 0x243185be 0x550c7dc3 0x72be5d74 0x80deb1fe 0x9bdc06a7 0xc19bf174
   0xe49b69c1 0xefbe4786 0x0fc19dc6 0x240ca1cc 0x2de92c6f 0x4a7484aa 0x5cb0a9dc 0x76f988da
   0x983e5152 0xa831c66d 0xb00327c8 0xbf597fc7 0xc6e00bf3 0xd5a79147 0x06ca6351 0x14292967
   0x27b70a85 0x2e1b2138 0x4d2c6dfc 0x53380d13 0x650a7354 0x766a0abb 0x81c2c92e 0x92722c85
   0xa2bfe8a1 0xa81a664b 0xc24b8b70 0xc76c51a3 0xd192e819 0xd6990624 0xf40e3585 0x106aa070
   0x19a4c116 0x1e376c08 0x2748774c 0x34b0bcb5 0x391c0cb3 0x4ed8aa4a 0x5b9cca4f 0x682e6ff3
   0x748f82ee 0x78a5636f 0x84c87814 0x8cc70208 0x90befffa 0xa4506ceb 0xbef9a3f7 0xc67178f2])

(def ^:private H0 [0x6a09e667 0xbb67ae85 0x3c6ef372 0xa54ff53a 0x510e527f 0x9b05688c 0x1f83d9ab 0x5be0cd19])

(defn sha256-hex
  "Lower-case hex SHA-256 of byte array `bs`."
  [bs]
  (let [n (alength bs)
        total (* 64 (quot (+ n 9 63) 64))
        bits (* 8 n)
        m32 0xffffffff
        byte-at (fn [i] (cond (< i n) (bit-and (long (aget bs i)) 0xff)
                              (= i n) 0x80
                              (>= i (- total 8)) (bit-and (unsigned-bit-shift-right bits (* 8 (- total 1 i))) 0xff)
                              :else 0))
        rotr (fn [x k] (bit-and (bit-or (unsigned-bit-shift-right x k) (bit-shift-left x (- 32 k))) m32))
        w (long-array 64)
        hs (long-array H0)]
    (loop [blk 0]
      (when (< blk total)
        (dotimes [t 16]
          (let [i (+ blk (* 4 t))]
            (aset w t (bit-or (bit-shift-left (byte-at i) 24) (bit-shift-left (byte-at (+ i 1)) 16)
                              (bit-shift-left (byte-at (+ i 2)) 8) (byte-at (+ i 3))))))
        (loop [t 16]
          (when (< t 64)
            (let [a (aget w (- t 15)) b (aget w (- t 2))
                  s0 (bit-xor (rotr a 7) (rotr a 18) (unsigned-bit-shift-right a 3))
                  s1 (bit-xor (rotr b 17) (rotr b 19) (unsigned-bit-shift-right b 10))]
              (aset w t (bit-and (+ (aget w (- t 16)) s0 (aget w (- t 7)) s1) m32))
              (recur (inc t)))))
        (loop [t 0 a (aget hs 0) b (aget hs 1) c (aget hs 2) d (aget hs 3)
               e (aget hs 4) f (aget hs 5) g (aget hs 6) hh (aget hs 7)]
          (if (< t 64)
            (let [S1 (bit-xor (rotr e 6) (rotr e 11) (rotr e 25))
                  ch (bit-xor (bit-and e f) (bit-and (bit-xor e m32) g))
                  t1 (bit-and (+ hh S1 ch (K t) (aget w t)) m32)
                  S0 (bit-xor (rotr a 2) (rotr a 13) (rotr a 22))
                  mj (bit-xor (bit-and a b) (bit-and a c) (bit-and b c))
                  t2 (bit-and (+ S0 mj) m32)]
              (recur (inc t) (bit-and (+ t1 t2) m32) a b c (bit-and (+ d t1) m32) e f g))
            (doseq [[i v] (map-indexed vector [a b c d e f g hh])]
              (aset hs i (bit-and (+ (aget hs i) v) m32)))))
        (recur (+ blk 64))))
    (apply str (map #(format "%08x" %) (vec hs)))))

(defn- file-bytes [path]
  (java.nio.file.Files/readAllBytes (java.nio.file.Paths/get (str path) (into-array String []))))

(defn sha256-file [path] (sha256-hex (file-bytes path)))

(defn read-frozen
  "The frozen rows, after checking the file's SHA-256 against `expected`;
  a mismatch is refused."
  [path expected]
  (let [bs (file-bytes path) got (sha256-hex bs)]
    (when-not (= got expected)
      (throw (ex-info (str path "'s SHA-256 is " got ", not the registered " expected "; refusing to score")
                      {:path path :sha256 got :expected expected})))
    (read-string (String. bs "UTF-8"))))

(defn check-date
  "The vintage's download date, YYYY-MM-DD, refused before 2027-11-01."
  [d]
  (when-not (and (string? d) (re-matches #"\d{4}-\d{2}-\d{2}" d))
    (throw (ex-info (str "the vintage date must be YYYY-MM-DD, got " (pr-str d)) {:date d})))
  (when (neg? (compare d vintage-from))
    (throw (ex-info (str "vintage date " d " is before " vintage-from "; refusing to score") {:date d})))
  d)

;; -- rows ---------------------------------------------------------------------------------------

(defn row-key [r] [(:iso r) (:o r) (:h r) (:outcome r)])

(defn- target [panel {:keys [iso o h outcome]}]
  (if (= :nl (first outcome)) (h/nl-data panel iso (+ o h) (second outcome)) (h/debt-data panel iso (+ o h))))

(defn published-at-freeze
  "The rows whose target `panel` (as known at the freeze) already has: net
  lending dated by the flows, private debt by the measured stocks."
  [rows panel]
  (filterv #(some? (target panel %)) rows))

(defn vintage-panel
  "The vintage's panel: panel.csv, panel-holdout.csv and panel-holdout2.csv
  in `dir`, merged as rates-freeze merges them."
  [dir]
  (let [f (fn [n] (let [p (io/file dir n)]
                    (when-not (.exists p) (throw (ex-info (str "the vintage lacks " p) {:file (str p)})))
                    (sfc/read-panel (str p))))]
    (merge (f "panel.csv") (f "panel-holdout.csv") (f "panel-holdout2.csv"))))

(defn attach-data
  "`rows` with :data the vintage's target (nil where it lacks one)."
  [rows panel]
  (mapv #(assoc % :data (target panel %)) rows))

(defn absent
  "Countries, the excluded ones aside, with no target in the vintage."
  [rows]
  (vec (sort (for [[iso rs] (group-by :iso rows)
                   :when (and (not (excluded-countries iso)) (not-any? #(number? (:data %)) rs))]
               iso))))

(defn scorable
  "The rows the amendment scores: excluded countries and rows out, h2 and h4
  dropped, only rows with a target and every model's forecast."
  [rows]
  (filterv (fn [r] (and (not (excluded-countries (:iso r)))
                        (not (excluded-rows (row-key r)))
                        (#{1 3 5} (:h r))
                        (number? (:data r))
                        (every? #(number? (get-in r [:f %])) models)))
           rows))

(defn cell-of [r] [(if (= 1 (:h r)) :h1 :h35) (if (= :nl (first (:outcome r))) :nl :debt)])

(defn shape-text
  "The scored rows' shape (no values), as scripts/sfc-rates-null.py reads it."
  [rows]
  (str "[" (str/join "\n " (for [r (sort-by (juxt :iso :o :h #(str (:outcome %))) rows)]
                             (format "{:iso %s, :o %d, :h %d, :outcome %s}" (pr-str (:iso r)) (:o r) (:h r) (pr-str (:outcome r)))))
       "]\n"))

;; -- verdicts -----------------------------------------------------------------------------------

(defn- skill [rows m x]
  (let [sse (fn [k] (reduce + 0.0 (map #(let [e (- (double (get-in % [:f k])) (double (:data %)))] (* e e)) rows)))
        sx (sse x)]
    (when (pos? sx) (- 1.0 (/ (sse m) sx)))))

(defn cell-verdict
  "A cell's verdict from its interval [point lo hi] and its country count."
  [[_ lo hi] n]
  (cond (< n min-countries) :too-few
        (> lo (- delta)) :non-inferior
        (< hi (- delta)) :inferior
        :else :inconclusive))

(defn overall
  "The overall verdict from {cell verdict}: inferior when any cell is;
  non-inferior when both h1 cells are and every other cell is or has too
  few countries; else inconclusive."
  [vs]
  (cond (some #{:inferior} (vals vs)) :inferior
        (and (= :non-inferior (vs [:h1 :nl])) (= :non-inferior (vs [:h1 :debt]))
             (every? #{:non-inferior :too-few} (vals vs))) :non-inferior
        :else :inconclusive))

(defn- cell-name [[hg o]] (str (if (= hg :h1) "h1" "h3-5") " " (name o)))

(defn confirm
  "Cells and verdicts from scored rows and the null {[hg outcome] [q05
  q95]}: {:cells {cell {:rows :countries :ci :verdict :boot :baselines}}
  :overall :lines}."
  [rows null]
  (let [f (fn [[p lo hi]] (if lo (format "%+.3f [%+.3f, %+.3f]" p lo hi) (if p (format "%+.3f" p) "-")))
        cs (into {} (for [c cells
                          :let [rs (filter #(= c (cell-of %)) rows)
                                n (count (distinct (map :iso rs)))
                                p (when (seq rs) (skill rs :rates :estimated))
                                [q05 q95] (get null c)
                                _ (when (and p (>= n min-countries) (not (and q05 q95)))
                                    (throw (ex-info (str "the null has no quantiles for " (cell-name c)) {:cell c})))
                                ci (when (and p q05) [p (- p q95) (- p q05)])
                                big (>= n min-countries)]]
                      [c {:rows (count rs) :countries n :ci ci
                          :verdict (if (and big ci) (cell-verdict ci n) :too-few)
                          :boot (when (and big p) (h/bootstrap rs :rates :estimated 2000 seed))
                          :baselines (into {} (for [x [:persistence :damped :restart]]
                                                [x (when (seq rs) (if big (h/bootstrap rs :rates x 2000 seed) [(skill rs :rates x)]))]))}]))
        vs (into {} (for [[c v] cs] [c (:verdict v)]))
        ov (overall vs)
        judged (filter #(not= :too-few (vs %)) cells)
        few (filter #(= :too-few (vs %)) cells)]
    {:cells cs :overall ov
     :lines (vec (concat
                  (for [c cells :let [{:keys [rows countries ci verdict boot baselines]} (cs c)]]
                    (str (format "%-10s rows %d, countries %d; rates vs estimated %s (null interval); %s" (cell-name c) rows countries
                                 (f ci) (if (= verdict :too-few) "inconclusive, too few countries" (name verdict)))
                         (format "%n           reported: countries bootstrap %s; vs persistence %s, damped %s, Keen's restart %s"
                                 (f boot) (f (:persistence baselines)) (f (:damped baselines)) (f (:restart baselines)))))
                  [(str "verdict: " (name ov)
                        "; rests on: " (if (seq judged) (str/join ", " (map cell-name judged)) "no cell")
                        (when (seq few) (str "; too few countries: " (str/join ", " (for [c few] (str (cell-name c) " (" (:countries (cs c)) ")"))))))]))}))

;; -- the run ------------------------------------------------------------------------------------

(defn- prepare [frozen sha vintage date]
  (let [fr (read-frozen frozen sha)
        date (check-date date)
        rows (attach-data fr (vintage-panel vintage))]
    {:frozen fr :date date :rows rows :scored (scorable rows)}))

(defn write-shape!
  "Writes the scored rows' shape for the null script; returns it."
  [frozen sha vintage date out]
  (let [{:keys [scored]} (prepare frozen sha vintage date) t (shape-text scored)]
    (spit out t)
    t))

(defn score
  "The confirmation: refuses a frozen file whose SHA-256 is not `sha`, a
  vintage date before 2027-11-01, and a shape file other than the scored
  rows' (the null must be simulated on the rows scored). `null` {[hg
  outcome] [q05 q95]}. confirm's result, :lines headed by the run's
  facts."
  [frozen sha vintage date shape-path null]
  (let [{:keys [frozen date rows scored]} (prepare frozen sha vintage date)
        t (shape-text scored)]
    (when-not (= t (slurp shape-path))
      (throw (ex-info (str "the shape in " shape-path " is not the scored rows'; rerun shape and the null") {:shape shape-path})))
    (let [r (confirm scored null)
          hd [(str "vintage downloaded " date "; rates-frozen.edn SHA-256 " sha " (checked)")
              (format "frozen rows %d; excluded countries %s; excluded rows %s; h2 and h4 rows dropped %d"
                      (count frozen) (str/join " " (sort excluded-countries))
                      (str/join " " (sort-by str (filter (set (map row-key frozen)) excluded-rows)))
                      (count (filter #(#{2 4} (:h %)) frozen)))
              (format "scored rows %d (target in the vintage, every model present); countries absent from the vintage: %s"
                      (count scored) (if-let [a (seq (absent rows))] (str/join " " a) "none"))]]
      (update r :lines #(vec (concat hd %))))))

(defn -main [mode & args]
  (let [opts (into {} (for [[k v] (partition-all 2 args)] [k v]))
        frozen (get opts "--frozen" frozen-path)]
    (case mode
      "shape"
      (let [t (write-shape! frozen frozen-sha256 (get opts "--vintage") (get opts "--vintage-date") (get opts "--out"))]
        (println (count (re-seq #"\{:iso" t)) "rows scored; shape written to" (get opts "--out")))
      "score"
      (let [{:keys [lines]} (score frozen frozen-sha256 (get opts "--vintage") (get opts "--vintage-date")
                                   (get opts "--shape") (read-string (slurp (get opts "--null"))))]
        (doseq [l lines] (println l))
        (when-let [out (get opts "--out")] (spit out (str (str/join "\n" lines) "\n"))))
      "published"
      (let [fr (read-frozen frozen frozen-sha256)
            d "data/derived/sfc/"
            p (merge (sfc/read-panel (str d "panel.csv")) (sfc/read-panel (str d "panel-holdout.csv"))
                     (sfc/read-panel (str d "panel-holdout2.csv")))]
        (doseq [r (published-at-freeze fr p)]
          (println (row-key r) "target" (+ (:o r) (:h r))))))
    (shutdown-agents)))
