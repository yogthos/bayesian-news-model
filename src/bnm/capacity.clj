(ns bnm.capacity
  "Capacity from investment (bead bnm-4hv.11; docs/COUPLED-SYSTEMS.md 5.2a):
  investment by industry, financed by credit and retained profits, and the
  part of it that completes into capacity in a later year, through a time
  to build.

  Time to build (`build-time`): a prior over how many whole years a project
  takes, per EU KLEMS industry, with its sources and range. Spending is
  spread evenly over the build (an assumption: real outlays follow an S
  curve), so a build of T years puts 1/T of its spending in each of the T
  years up to and including the year it completes. The completion weights
  (`completion-weights`) are the share of a year's spending that completes
  L years later, over the prior: w_L = sum_T p(T) [L < T] / T. They sum
  to 1.

  The completion rate of an industry in year y (`completion-rate`) is the
  investment already made that completes in y, as a share of the capital
  stock: sum over L >= 1 of w_L rate(y - L), rate = I / K(opening). y's
  own spending (L = 0) is not in it: it is not known when y is forecast.
  It is undefined unless every year the weights reach is present.

  Financing (`financed-investment`): an industry's investment is its
  retained profits plus its external finance. The NFC sector's gross
  saving (B8G) is split by each industry's share of gross operating
  surplus; its net borrowing (-B9) by the credit shares, a section's share
  of NFC credit (Müller-Verner) split within the section by surplus, the
  sections without a measured share taking the rest by surplus. Real
  estate (L: its surplus is mostly imputed rent), finance (K) and the
  public industries (O-Q) are not NFC industries here; real estate's
  credit leaves with it."
  )

;; -- time to build ---------------------------------------------------------------------------

(def build-time
  "Priors over the years a project takes from the start of spending to
  first output, {industry {:p {years probability} :range [lo hi] :mean m
  :sources [...]}}; :default for an industry without its own."
  (let [mk (fn [p sources]
             {:p p :range [(apply min (keys p)) (apply max (keys p))]
              :mean (reduce + (map (fn [[t q]] (* t q)) p)) :sources sources})]
    {:default
     (mk {1 0.3 2 0.5 3 0.2}
         ["Koeva (2000), IMF WP/00/138, 'The facts about time-to-build': new plants of Compustat firms, from press and trade reports, take about two years in most industries, unaffected by the cycle"
          "Kydland & Prescott (1982), Econometrica 50(6): four quarters, the business-cycle calibration"])
     "B"
     (mk {2 0.2 3 0.35 4 0.25 5 0.2}
         ["IEA (2021), The Role of Critical Minerals in Clean Energy Transitions, chart 'Global average lead times from discovery to production, 2010-2019': 16.5 years in all, of which about 1.8 construction planning and 2.6 construction to production (the split as Statista reproduces the chart)"
          "S&P Global Market Intelligence (2024): mines started in 2020-23 took 17.9 years from discovery; the construction part is the prior's, exploration comes before any GFCF counted here"])
     "C19"
     (mk {2 0.15 3 0.35 4 0.3 5 0.1 6 0.1}
         ["greenfield refineries: Irving Oil and BP's Eider Rock (Saint John, New Brunswick), construction planned 2011 to start-up 2015 (4 years), re-planned in November 2008 as two phases over 8 years (Petroleum News, 7 Dec 2008), then shelved: a plan, not a build"
          "most capacity change is expansion and debottlenecking, nearer Koeva's two years: the mode is 3"])
     "C20"
     (mk {1 0.2 2 0.5 3 0.3}
         ["Koeva (2000): about two years in most industries, chemicals among them"])
     "C24-C25"
     (mk {1 0.2 2 0.45 3 0.25 4 0.1}
         ["Koeva (2000): about two years"
          "electric arc furnaces: ArcelorMittal Calvert 24 months (S&P Global, 12 Aug 2020); US Steel Fairfield about 2 years (AIST); Hebei Yongyang 14-15 months (Kallanish); Tata Port Talbot 3 years and more (GMK Center, 2026)"
          "aluminium smelters: Alcoa Fjardaal, construction 2004 to first metal 2007 (3 years)"])
     "D-E"
     (mk {2 0.1 3 0.25 4 0.3 5 0.15 6 0.1 7 0.1}
         ["EIA, Assumptions to AEO2022, Electricity Market Module, Table 3 (March 2022): lead times 2 years (solar PV, combustion turbines), 3 (combined cycle, onshore wind), 4 (coal, hydro, offshore wind, biomass), 6 (nuclear)"
          "observed: GEM Integrated Power Tracker (September 2026), capacity under construction over mean additions 2021-2025 (Little's law, scripts/time-to-build.py): all 4.4 years; coal 4.2, oil and gas 4.4, wind 3.6, solar 3.1, nuclear 12.9, hydro 13.7. These read long: the pipelines grew, and past additions against today's pipeline overstate the time. Adjusted for the growth (constant rate, fixed build time): all 2.6, coal 2.5, oil and gas 3.1, wind 3.1, solar 1.2; nuclear and hydro not steady"])}))

(defn prior
  "The time-to-build prior of `industry`, the default when it has none."
  [industry]
  (get build-time industry (:default build-time)))

(defn completion-weights
  "{L w}: the share of a year's spending that completes L years later, when
  a build of T years (probability p(T)) spreads its spending evenly."
  [p]
  (let [tmax (apply max (keys p))]
    (into (sorted-map)
          (for [l (range tmax)]
            [l (reduce + 0.0 (for [[t q] p :when (< l t)] (/ (double q) t)))]))))

(defn mean-lag
  "The mean years from a unit of spending to completion."
  [p]
  (reduce + 0.0 (map (fn [[l w]] (* l w)) (completion-weights p))))

;; -- investment into completions -----------------------------------------------------------

(defn investment-rate
  "I / K, the year's investment over the opening capital stock; nil unless
  both are there and K is positive."
  [i k0]
  (when (and i k0 (pos? (double k0))) (/ (double i) (double k0))))

(defn completion-rate
  "The investment made before year `y` that completes in y, over the
  capital stock: sum over L >= 1 of w_L rates(y - L), with the completion
  weights of prior `p`; nil when a year it reaches is missing, or when
  nothing is in the pipeline (a one-year build)."
  [p rates y]
  (let [ws (filter (fn [[l w]] (and (pos? l) (pos? w))) (completion-weights p))]
    (when (seq ws)
      (reduce (fn [acc [l w]]
                (if-let [r (get rates (- y l))]
                  (+ acc (* w (double r)))
                  (reduced nil)))
              0.0 ws))))

;; -- financing ------------------------------------------------------------------------------

(def nfc-industries
  "The EU KLEMS industries financing is split over: every industry but the
  manufacturing aggregate C, finance (K), real estate (L) and the public
  industries (O-Q)."
  ["A" "B" "C10-C12" "C13-C15" "C16-C18" "C19" "C20" "C21" "C22-C23" "C24-C25" "C26" "C27" "C28"
   "C29-C30" "C31-C33" "D-E" "F" "G" "H" "I" "J" "M-N" "R-S"])

(def credit-section
  "The Müller-Verner section an NFC industry's credit is measured in."
  (merge {"A" "A" "B" "B" "F" "F" "G" "G" "I" "I"}
         (into {} (for [i nfc-industries :when (.startsWith ^String i "C")] [i "C"]))))

(defn financed-investment
  "{industry LCU}: retained profits (`nfc` \"b8g\") split by gross operating
  surplus `gos` {industry value} over the NFC industries, plus external
  finance (-\"nl\") split by `shares`, Müller-Verner's credit shares
  {group share} of total private credit (NFC, sections), or by surplus
  when shares or their NFC total are missing. nil without B8G, B9 or any
  surplus."
  [nfc gos shares]
  (let [b8g (get nfc "b8g") nl (get nfc "nl")
        g (into {} (for [i nfc-industries :let [v (get gos i)] :when (and v (pos? (double v)))] [i (double v)]))
        tot (reduce + 0.0 (vals g))]
    (when (and b8g nl (pos? tot))
      (let [ext (- (double nl))
            nfc-share (get shares "NFC")
            cs (when (and nfc-share (pos? (double nfc-share)))
                 (into {} (for [s ["A" "B" "C" "F" "G" "I" "L"] :let [v (get shares s)] :when v] [s (/ (double v) nfc-share)])))
            by-sec (group-by credit-section (keys g))
            covered (into {} (for [[s v] cs :when (and (not= s "L") (seq (get by-sec s)))] [s v]))
            others (remove #(contains? covered (credit-section %)) (keys g))
            others-tot (reduce + 0.0 (map g others))
            rest (max 0.0 (- 1.0 (reduce + 0.0 (vals covered)) (get cs "L" 0.0)))
            credit-w (fn [i]
                       (if-not cs
                         (/ (g i) tot)
                         (if-let [c (get covered (credit-section i))]
                           (* c (/ (g i) (reduce + 0.0 (map g (get by-sec (credit-section i))))))
                           (if (pos? others-tot) (* rest (/ (g i) others-tot)) 0.0))))]
        (into {} (for [i (keys g)] [i (+ (* (double b8g) (/ (g i) tot)) (* ext (credit-w i)))]))))))
