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
;; L'Ecuyer's MRG32k3a (1999): two combined multiple recursive generators,
;; period about 2^191, among the best tested generators for simulation. It is
;; specified in double arithmetic (every product stays below 2^53), which jolt
;; compiles to unboxed flonum code: several times faster here than any
;; generator on 64-bit integers.

(defn rng
  "A generator seeded with `seed`: a six-slot mutable state."
  [seed]
  (let [seed (Math/abs (long seed))
        a (double (inc (mod seed 4294967086)))
        b (double (inc (mod (+ (* (mod seed 1000003) 7919) 13) 4294944442)))]
    (double-array [a 12345.0 a b 12345.0 b])))

(defn next-double!
  "A uniform double in (0, 1) from generator `g`, advancing it."
  ^double [^doubles g]
  (let [p1 (- (* 1403580.0 (aget g 1)) (* 810728.0 (aget g 0)))
        p1 (- p1 (* 4294967087.0 (Math/floor (/ p1 4294967087.0))))
        p2 (- (* 527612.0 (aget g 5)) (* 1370589.0 (aget g 3)))
        p2 (- p2 (* 4294944443.0 (Math/floor (/ p2 4294944443.0))))]
    (aset g 0 (aget g 1)) (aset g 1 (aget g 2)) (aset g 2 p1)
    (aset g 3 (aget g 4)) (aset g 4 (aget g 5)) (aset g 5 p2)
    (if (<= p1 p2)
      (* (+ (- p1 p2) 4294967087.0) 2.328306549295727688e-10)
      (* (- p1 p2) 2.328306549295727688e-10))))

(defn normal!
  "A standard normal draw (Box-Muller)."
  ^double [^doubles g]
  (let [u (Math/max tiny (next-double! g))
        v (next-double! g)]
    (* (Math/sqrt (* -2.0 (Math/log u))) (Math/cos (* 2.0 Math/PI v)))))

(defn gamma!
  "A Gamma(shape, scale) draw (Marsaglia-Tsang)."
  ^double [^doubles g ^double shape ^double scale]
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

(defn beta! ^double [^doubles g ^double a ^double b]
  (let [x (gamma! g a 1.0) y (gamma! g b 1.0)]
    (/ x (Math/max tiny (+ x y)))))

(defn poisson!
  "A Poisson(lambda) draw: inversion for small means, a normal
  approximation above 50."
  ^long [^doubles g ^double lambda]
  (cond
    (<= lambda 0.0) 0
    (> lambda 50.0) (Math/max 0 (Math/round (+ lambda (* (Math/sqrt lambda) (normal! g)))))
    :else (let [l (Math/exp (- lambda))]
            (loop [k 0 p (next-double! g)]
              (if (<= p l) k (recur (inc k) (* p (next-double! g))))))))

(defn overdispersed!
  "A draw with mean `mean` and variance `disp` × mean: a Gamma whose shape
  is mean/disp. Continuous, since the model's counts are normalized."
  ^double [^doubles g ^double mean ^double disp]
  (if (<= mean 0.0)
    0.0
    (let [d (Math/max 1e-6 disp)]
      (gamma! g (/ mean d) d))))

(defn neg-binomial!
  "A count with mean `mean` and variance `phi` × mean: Poisson for phi at
  most one, else a Poisson whose rate is Gamma with shape mean/(phi-1)."
  ^double [^doubles g ^double mean ^double phi]
  (cond
    (<= mean 0.0) 0.0
    (<= phi (+ 1.0 1e-9)) (double (poisson! g mean))
    :else (let [r (/ mean (- phi 1.0))]
            (double (poisson! g (gamma! g r (/ mean r)))))))

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

(defn solve-spd
  "x with a x = b for a symmetric positive definite n × n matrix `a` (row
  major), by Cholesky decomposition. Leaves `a` and `b` as they were."
  ^doubles [^doubles a ^doubles b ^long n]
  (let [^doubles l (double-array (* n n))
        ^doubles x (aclone b)]
    (dotimes [i n]
      (dotimes [j (inc i)]
        (let [s (loop [k 0 s (aget a (+ (* i n) j))]
                  (if (< k j) (recur (inc k) (- s (* (aget l (+ (* i n) k)) (aget l (+ (* j n) k))))) s))]
          (aset l (+ (* i n) j)
                (if (= i j)
                  (Math/sqrt (Math/max s 1e-300))
                  (/ s (aget l (+ (* j n) j))))))))
    ;; forward: l y = b, then back: l' x = y
    (dotimes [i n]
      (aset x i (/ (loop [k 0 s (aget x i)]
                     (if (< k i) (recur (inc k) (- s (* (aget l (+ (* i n) k)) (aget x k)))) s))
                   (aget l (+ (* i n) i)))))
    (loop [i (dec n)]
      (when (>= i 0)
        (aset x i (/ (loop [k (inc i) s (aget x i)]
                       (if (< k n) (recur (inc k) (- s (* (aget l (+ (* k n) i)) (aget x k)))) s))
                     (aget l (+ (* i n) i))))
        (recur (dec i))))
    x))

(defn normal-quantile
  "The standard normal quantile at p in (0, 1): Acklam's rational
  approximation, relative error below 1.15e-9."
  ^double [^double p]
  (let [a [-3.969683028665376e+01 2.209460984245205e+02 -2.759285104469687e+02
           1.383577518672690e+02 -3.066479806614716e+01 2.506628277459239e+00]
        b [-5.447609879822406e+01 1.615858368580409e+02 -1.556989798598866e+02
           6.680131188771972e+01 -1.328068155288572e+01]
        c [-7.784894002430293e-03 -3.223964580411365e-01 -2.400758277161838e+00
           -2.549732539343734e+00 4.374664141464968e+00 2.938163982698783e+00]
        d [7.784695709041462e-03 3.224671290700398e-01 2.445134137142996e+00 3.754408661907416e+00]
        poly (fn [cs x] (reduce (fn [acc ci] (+ (* acc x) ci)) 0.0 cs))
        lo 0.02425]
    (cond
      (< p lo) (let [q (Math/sqrt (* -2.0 (Math/log p)))]
                 (/ (poly c q) (+ (* (poly d q) q) 1.0)))
      (> p (- 1.0 lo)) (let [q (Math/sqrt (* -2.0 (Math/log (- 1.0 p))))]
                         (- (/ (poly c q) (+ (* (poly d q) q) 1.0))))
      :else (let [q (- p 0.5) r (* q q)]
              (/ (* (poly a r) q) (+ (* (poly b r) r) 1.0))))))
