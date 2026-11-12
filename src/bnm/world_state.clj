(ns bnm.world-state
  "The world node's state (docs/RECURSIVE.md §3; recursive step 2, loop
  audit Round 2): world prices, volatility, credit and the dollar, each a
  monthly series with its publication lag, read as of a forecast origin t.

  A series is {:entries [[period available value] ...] :lag months}:
  months as integers, values already transformed (logs for prices). At
  origin t a gauge reads the period t - lag, the last its source has
  published by t when the lag is right. Every read goes through a reader
  that records it and throws when the value it reads was published after t
  (Test 3b): a lag declared shorter than the data's own availability is
  refused, never read early. A value not in the data is unknown (nil).

  The gauges (`gauge-names`), what the year and half-decade nodes read as
  their :world-gauges group, directly and times the place's exposure:
    food12, food24   world food prices over 12 and 24 months (log change)
    brent12          Brent over 12 months
    vix              the VIX (log level)
    credit3          world credit: the mean three-year change in credit to
                     GDP over the countries with data (BIS), as known
    policy12         the US policy rate over 12 months (points)
    usd12            the broad dollar over 12 months (from 2006)
    food12-x-food, food24-x-food   times food imports (share of trade)
    brent12-x-energy               times net fuel imports
    credit3-x-open, vix-x-open     times trade openness
  The world node's state is the same series (`world-node`), replacing the
  share of states with any onset (loop audit issue 10).")

(defn world-state
  "{id {:entries [[period available value] ...] :lag months}} -> the
  world state's series, each {:by (sorted-map period [available value])
  :lag}."
  [series]
  (into {} (for [[id {:keys [entries lag]}] series]
             [id {:lag (long (or lag 0))
                  :by (into (sorted-map) (for [[p a v] entries] [(long p) [(long a) (double v)]]))}])))

(defn reader
  "A reader of world state `ws` at origin t, recording every read."
  [ws t]
  {:ws ws :origin (long t) :reads (atom [])})

(defn reads "Every read the reader made, in order." [r] @(:reads r))

(defn read-at
  "The value of series `id` for `period`, or nil when the data have none;
  recorded. Throws when that value was published after the reader's
  origin."
  [{:keys [ws origin reads]} id period]
  (when-let [[avail v] (get-in ws [id :by (long period)])]
    (when (> avail origin)
      (throw (ex-info "bnm.world-state: a read of a value published after the origin"
                      {:series id :period period :available avail :origin origin})))
    (swap! reads conj {:dir :world :series id :period (long period) :known-at avail})
    v))

(defn- latest-period "The period a series' gauge reads at the origin: t less its lag." [r id]
  (- (:origin r) (get-in r [:ws id :lag] 0)))

(defn level
  "Series `id` at the period its lag says is the latest published."
  [r id]
  (when (get-in r [:ws id]) (read-at r id (latest-period r id))))

(defn change
  "Series `id` over `months`: its latest value less the one `months`
  before; nil when either is unknown."
  [r id months]
  (when (get-in r [:ws id])
    (let [p (latest-period r id)
          a (read-at r id p)
          b (read-at r id (- p months))]
      (when (and a b) (- a b)))))

(def gauge-names
  [:food12 :food24 :brent12 :vix :credit3 :policy12 :usd12
   :food12-x-food :food24-x-food :brent12-x-energy :credit3-x-open :vix-x-open])

(def price-names "The direct world gauges: the world node's prices." (subvec gauge-names 0 7))

(defn- x* [a b] (when (and a b) (* (double a) (double b))))

(defn gauges
  "{:x [values in gauge-names' order] :names gauge-names :reads} of world
  state `ws` at origin t, the exposed ones through `exposure` ({:food
  :energy :open}, fractions; nil or a nil entry leaves its products
  unknown)."
  [ws t exposure]
  (let [r (reader ws t)
        food12 (change r :food 12)
        food24 (change r :food 24)
        brent12 (change r :brent 12)
        vix (level r :vix)
        credit3 (level r :credit)
        policy12 (change r :policy 12)
        usd12 (change r :usd 12)
        {:keys [food energy open]} exposure]
    {:x [food12 food24 brent12 vix credit3 policy12 usd12
         (x* food12 food) (x* food24 food) (x* brent12 energy) (x* credit3 open) (x* vix open)]
     :names gauge-names
     :reads (reads r)}))

(defn world-node
  "The world node [:world \"001\" scale] at origin t (bnm.node's
  contract): its :prices, the direct world gauges as known at t, with
  what it read; the hazard part comes from its children (bnm.compose),
  not from a target of its own."
  [ws t scale]
  (let [{:keys [x reads]} (gauges ws t nil)]
    {:id [:world "001" scale] :t (long t)
     :stocks {} :flows {} :buffers {} :hazard {} :tension []
     :prices (into {} (keep (fn [[k v]] (when (some? v) [k v])) (map vector price-names x)))
     :reads reads
     :known (reduce max Long/MIN_VALUE (cons Long/MIN_VALUE (map :known-at reads)))
     :flags #{}}))
