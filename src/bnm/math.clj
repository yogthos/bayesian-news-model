(ns bnm.math
  "Numerics the host does not provide: log-gamma, a seedable generator, and
  samplers for the distributions the model uses. The generator is a value, so
  a forecast run with the same seed is the same forecast.")

(set! *unchecked-math* true)

(def ^:const tiny 1e-12)

(defn log-gamma
  "ln Γ(x) for x > 0 (Lanczos, g=7, n=9; about 15 significant digits)."
  ^double [^double x]
  (if (< x 0.5)
    ;; reflection
    (- (Math/log (/ Math/PI (Math/abs (Math/sin (* Math/PI x)))))
       (log-gamma (- 1.0 x)))
    (let [x (- x 1.0)
          g 7.0
          cs [0.99999999999980993 676.5203681218851 -1259.1392167224028
              771.32342877765313 -176.61502916214059 12.507343278686905
              -0.13857109526572012 9.9843695780195716e-6 1.5056327351493116e-7]
          a (loop [i 1 a (double (first cs))]
              (if (< i 9)
                (recur (inc i) (+ a (/ (double (nth cs i)) (+ x i))))
                a))
          t (+ x g 0.5)]
      (+ (* 0.5 (Math/log (* 2.0 Math/PI)))
         (* (+ x 0.5) (Math/log t))
         (- t)
         (Math/log a)))))

(defn log-beta ^double [^double a ^double b]
  (- (+ (log-gamma a) (log-gamma b)) (log-gamma (+ a b))))

(defn logit ^double [^double p]
  (Math/log (/ p (- 1.0 p))))

(defn expit ^double [^double x]
  (/ 1.0 (+ 1.0 (Math/exp (- x)))))

(defn clamp ^double [^double lo ^double hi ^double x]
  (Math/max lo (Math/min hi x)))

;; --- a seedable generator ------------------------------------------------------------
;; L'Ecuyer's combined linear congruential generator (1988): period about
;; 2.3e18, and every intermediate fits a Chez fixnum, which keeps it an order of
;; magnitude faster here than a 64-bit generator, whose wrap-around overflows
;; into bignums.

(defn rng
  "A generator seeded with `seed`: a two-slot mutable state."
  [seed]
  (let [seed (Math/abs (long seed))]
    (long-array [(inc (mod seed 2147483562))
                 (inc (mod (+ (* (mod seed 1000003) 7919) 13) 2147483398))])))

(defn next-double!
  "A uniform double in (0, 1) from generator `g`, advancing it."
  ^double [^longs g]
  (let [s1 (rem (* 40014 (aget g 0)) 2147483563)
        s2 (rem (* 40692 (aget g 1)) 2147483399)
        z (- s1 s2)
        z (if (< z 1) (+ z 2147483562) z)]
    (aset g 0 s1)
    (aset g 1 s2)
    (* (double z) 4.656613057391769e-10)))

(defn normal!
  "A standard normal draw (Box-Muller)."
  ^double [^longs g]
  (let [u (Math/max tiny (next-double! g))
        v (next-double! g)]
    (* (Math/sqrt (* -2.0 (Math/log u))) (Math/cos (* 2.0 Math/PI v)))))

(defn gamma!
  "A Gamma(shape, scale) draw (Marsaglia-Tsang)."
  ^double [^longs g ^double shape ^double scale]
  (cond
    (<= shape 0.0) 0.0
    (< shape 1.0)
    ;; boost: Gamma(a) = Gamma(a+1) * U^(1/a)
    (* (gamma! g (+ shape 1.0) scale)
       (Math/pow (Math/max tiny (next-double! g)) (/ 1.0 shape)))
    :else
    (let [d (- shape (/ 1.0 3.0))
          c (/ 1.0 (Math/sqrt (* 9.0 d)))]
      (loop []
        (let [x (normal! g)
              v (+ 1.0 (* c x))]
          (if (<= v 0.0)
            (recur)
            (let [v (* v v v)
                  u (next-double! g)]
              (if (or (< u (- 1.0 (* 0.0331 x x x x)))
                      (< (Math/log (Math/max tiny u))
                         (+ (* 0.5 x x) (* d (- 1.0 (- v (Math/log v)))))))
                (* d v scale)
                (recur)))))))))

(defn beta! ^double [^longs g ^double a ^double b]
  (let [x (gamma! g a 1.0) y (gamma! g b 1.0)]
    (/ x (Math/max tiny (+ x y)))))

(defn poisson!
  "A Poisson(lambda) draw: inversion for small means, a normal
  approximation above 50."
  ^long [^longs g ^double lambda]
  (cond
    (<= lambda 0.0) 0
    (> lambda 50.0) (Math/max 0 (Math/round (+ lambda (* (Math/sqrt lambda) (normal! g)))))
    :else (let [l (Math/exp (- lambda))]
            (loop [k 0 p (next-double! g)]
              (if (<= p l) k (recur (inc k) (* p (next-double! g))))))))

(defn overdispersed!
  "A draw with mean `mean` and variance `disp` × mean: a Gamma whose shape
  is mean/disp. Continuous, since the model's counts are normalized."
  ^double [^longs g ^double mean ^double disp]
  (if (<= mean 0.0)
    0.0
    (let [d (Math/max 1e-6 disp)]
      (gamma! g (/ mean d) d))))

(defn quantile
  "The q-quantile of the sorted double array `xs` (linear interpolation)."
  ^double [xs ^double q]
  (let [n (alength xs)]
    (if (zero? n)
      Double/NaN
      (let [pos (* q (dec n))
            i (long (Math/floor pos))
            f (- pos i)]
        (if (>= (inc i) n)
          (aget xs (dec n))
          (+ (* (- 1.0 f) (aget xs i)) (* f (aget xs (inc i)))))))))
