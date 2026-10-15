(ns bnm.time
  "Calendar arithmetic on YYYYMMDD strings, without a date library.
  Days are counted from 1970-01-01 (Howard Hinnant's civil-day algorithms).
  The model's time step is a bin of whole days; bin 0 starts at the epoch.")

(defn days-from-civil
  "Days since 1970-01-01 of the date y-m-d."
  ^long [^long y ^long m ^long d]
  (let [y (if (<= m 2) (dec y) y)
        era (quot (if (>= y 0) y (- y 399)) 400)
        yoe (- y (* era 400))
        doy (+ (quot (+ (* 153 (+ m (if (> m 2) -3 9))) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn civil-from-days
  "[y m d] of the day `z` days after 1970-01-01."
  [^long z]
  (let [z (+ z 719468)
        era (quot (if (>= z 0) z (- z 146096)) 146097)
        doe (- z (* era 146097))
        yoe (quot (- doe (quot doe 1460) (- (quot doe 36524)) (quot doe 146096)) 365)
        y (+ yoe (* era 400))
        doy (- doe (- (+ (* 365 yoe) (quot yoe 4)) (quot yoe 100)))
        mp (quot (+ (* 5 doy) 2) 153)
        d (inc (- doy (quot (+ (* 153 mp) 2) 5)))
        m (if (< mp 10) (+ mp 3) (- mp 9))]
    [(if (<= m 2) (inc y) y) m d]))

(defn day
  "Days since the epoch of a \"YYYYMMDD\" string."
  ^long [^String s]
  (days-from-civil (Long/parseLong (subs s 0 4))
                   (Long/parseLong (subs s 4 6))
                   (Long/parseLong (subs s 6 8))))

(defn date-str
  "The \"YYYYMMDD\" string of a day number."
  [^long z]
  (let [[y m d] (civil-from-days z)]
    (format "%04d%02d%02d" y m d)))

(defn bin-of ^long [^long day ^long bin-days]
  (Math/floorDiv day bin-days))

(defn bin-start
  "The first day of bin `b`."
  ^long [^long b ^long bin-days]
  (* b bin-days))

(defn year-of-day ^long [^long z] (first (civil-from-days z)))

(defn year-start-day ^long [^long y] (days-from-civil y 1 1))
