;; The equations, parameters, tables and initial conditions are ported from
;; WorldDynamics.jl (https://github.com/worlddynamics/WorldDynamics.jl),
;; MIT License, Copyright (c) 2022 Emanuele Natale, Pierluigi Crescenzi,
;; Paulo Bruno Serafim. Line tags are Meadows et al.'s Appendix A
;; (Dynamics of Growth in a Finite World, 1974), as WorldDynamics' are.

(ns bnm.world3
  "World3-03 (Meadows, Randers & Meadows 2004), the standard run
  (scenario 1), as a stock-flow model (bnm.stockflow): population in four
  age cohorts, industrial and service capital, agricultural land and its
  fertility, nonrenewable resources, persistent pollution, and the
  perceptions and delays between them. World3-03 is World3 (1972) with some
  tables and parameters revised (World3-91, then -03). The perceptions and
  delays start at their inputs' values in 1900, as Vensim's SMOOTH, DLINF3
  and DELAY3 do (WorldDynamics.jl starts them from values worked out on
  World3's 1972 tables instead, `initial`, which is 2-5% off Vensim by
  2100). Run by Euler steps of half a year from 1900 to 2100, it is
  Vensim's solution to its six printed digits."
  (:require [bnm.stockflow :as sf]))

(def parameters-1972
  "World3's (1972) constants, flat (one :pyear for every sector)."
  {;; population
   :len 28.0 :sfpc 230.0 :hsid 20.0 :iphst 1940.0 :rlt 30.0 :pet 4000.0 :mtfn 12.0
   :lpd 20.0 :zpgt 4000.0 :dcfsn 4.0 :sad 20.0 :ieat 3.0 :fcest 4000.0
   ;; capital
   :pyear 1975.0 :icor1 3.0 :icor2 3.0 :alic1 14.0 :alic2 14.0 :iet 4000.0 :iopcd 400.0
   :fioac1 0.43 :fioac2 0.43 :alsc1 20.0 :alsc2 20.0 :scor1 1.0 :scor2 1.0 :lfpf 0.75 :lufdt 2.0
   ;; agriculture
   :palt 3.2e9 :lfh 0.7 :pl 0.1 :alai1 2.0 :alai2 2.0 :lyf1 1.0 :lyf2 1.0 :io70 7.9e11
   :sd 0.07 :alln 6000.0 :uildt 10.0 :ilf 600.0 :fspd 2.0
   ;; nonrenewable resources
   :nri 1e12 :nruf1 1.0 :nruf2 1.0
   ;; persistent pollution
   :ppgf1 1.0 :ppgf21 1.0 :ppgf22 1.0 :swat 0.0 :frpm 0.02 :imef 0.1 :imti 10.0 :fipm 0.001
   :amti 1.0 :pptd1 20.0 :pptd2 20.0 :ppol70 1.36e8 :ahl70 1.5
   ;; human welfare and ecological footprint (World3-03)
   :oy 1.0 :gdpu 1.0 :rlgdp 24.0 :rhgdp 9508.0 :tl 1.91 :hup 4.0 :hgha 1e9})

(def parameters
  "World3-03's constants: World3's with World3-91's revisions."
  (merge parameters-1972 {:alln 1000.0 :dcfsn 3.8}))

(def tables-1972
  "World3's (1972) tables, {name [ys [lo hi]]}: ys on an even grid across
  [lo hi] (TABHL)."
  {;; population
   :m1 [[0.0567 0.0366 0.0243 0.0155 0.0082 0.0023 0.001] [20.0 80.0]] ; Line 4.1 Appendix A
   :m2 [[0.0266 0.0171 0.0110 0.0065 0.0040 0.0016 0.0008] [20.0 80.0]] ; Line 8.1 Appendix A
   :m3 [[0.0562 0.0373 0.0252 0.0171 0.0118 0.0083 0.006] [20.0 80.0]] ; Line 12.1 Appendix A
   :m4 [[0.13 0.11 0.09 0.07 0.06 0.05 0.04] [20.0 80.0]] ; Line 16.1 Appendix A
   :lmf [[0.0 1.0 1.2 1.3 1.35 1.4] [0.0 5.0]] ; Line 20.1 Appendix A
   :hsapc [[0.0 20.0 50.0 95.0 140.0 175.0 200.0 220.0 230.0] [0.0 2000.0]] ; Line 21.1 Appendix A
   :lmhs1 [[1.0 1.1 1.4 1.6 1.7 1.8] [0.0 100.0]] ; Line 24.1 Appendix A
   :lmhs2 [[1.0 1.4 1.6 1.8 1.95 2.0] [0.0 100.0]] ; Line 25.1 Appendix A
   :fpu [[0.0 0.2 0.4 0.5 0.58 0.65 0.72 0.78 0.8] [0.0 16e9]] ; Line 26.1 Appendix A
   :cmi [[0.5 0.05 -0.1 -0.08 -0.02 0.05 0.1 0.15 0.2] [0.0 1600.0]] ; Line 27.1 Appendix A
   :lmp [[1.0 0.99 0.97 0.95 0.9 0.85 0.75 0.65 0.55 0.4 0.2] [0.0 100.0]] ; Line 29.1 Appendix A
   :fm [[0.0 0.2 0.4 0.6 0.8 0.9 1.0 1.05 1.1] [0.0 80.0]] ; Line 34.1 Appendix A
   :cmple [[3.0 2.1 1.6 1.4 1.3 1.2 1.1 1.05 1.0] [0.0 80.0]] ; Line 35.1 Appendix A
   :sfsn [[1.25 1.0 0.9 0.8 0.75] [0.0 800.0]] ; Line 39.1 Appendix A
   :frsn [[0.5 0.6 0.7 0.85 1.0] [-0.2 0.2]] ; Line 41.1 Appendix A
   :fce [[0.75 0.85 0.9 0.95 0.98 0.99 1.0] [0.0 3.0]] ; Line 45.2 Appendix A
   :fsafc [[0.0 0.005 0.015 0.025 0.03 0.035] [0.0 10.0]] ; Line 48.1 Appendix A
   ;; capital
   :fioacv [[0.3 0.32 0.34 0.36 0.38 0.43 0.73 0.77 0.81 0.82 0.83] [0.0 2.0]] ; Line 59.1 Appendix A
   :isopc1 [[40.0 300.0 640.0 1000.0 1220.0 1450.0 1650.0 1800.0 2000.0] [0.0 1600.0]] ; Line 61.1 Appendix A
   :isopc2 [[40.0 300.0 640.0 1000.0 1220.0 1450.0 1650.0 1800.0 2000.0] [0.0 1600.0]] ; Line 62.1 Appendix A
   :fioas1 [[0.3 0.2 0.1 0.05 0.0] [0.0 2.0]] ; Line 64.1 Appendix A
   :fioas2 [[0.3 0.2 0.1 0.05 0.0] [0.0 2.0]] ; Line 65.1 Appendix A
   :jpicu [[0.37 0.18 0.12 0.09 0.07 0.06] [50.0 800.0]] ; Line 75.1 Appendix A
   :jpscu [[1.1 0.6 0.35 0.2 0.15 0.15] [50.0 800.0]] ; Line 77.1 Appendix A
   :jph [[2.0 0.5 0.4 0.3 0.27 0.24 0.2 0.2] [2.0 30.0]] ; Line 79.1 Appendix A
   :cuf [[1.0 0.9 0.7 0.3 0.1 0.1] [1.0 11.0]] ; Line 83.2 Appendix A
   ;; agriculture
   :ifpc1 [[230.0 480.0 690.0 850.0 970.0 1070.0 1150.0 1210.0 1250.0] [0.0 1600.0]] ; Line 90.1 Appendix A
   :ifpc2 [[230.0 480.0 690.0 850.0 970.0 1070.0 1150.0 1210.0 1250.0] [0.0 1600.0]] ; Line 91.1 Appendix A
   :fioaa1 [[0.4 0.2 0.1 0.025 0.0 0.0] [0.0 2.5]] ; Line 94.1 Appendix A
   :fioaa2 [[0.4 0.2 0.1 0.025 0.0 0.0] [0.0 2.5]] ; Line 95.1 Appendix A
   :dcph [[100000.0 7400.0 5200.0 3500.0 2400.0 1500.0 750.0 300.0 150.0 75.0 50.0] [0.0 1.0]] ; Line 97.1 Appendix A
   :lymc [[1.0 3.0 3.8 4.4 4.9 5.4 5.7 6.0 6.3 6.6 6.9 7.2 7.4 7.6 7.8 8.0 8.2 8.4 8.6 8.8 9.0 9.2 9.4 9.6 9.8 10.0]
          [0.0 1000.0]] ; Line 102.1 Appendix A
   :lymap1 [[1.0 1.0 0.7 0.4] [0.0 30.0]] ; Line 106.1 Appendix A
   :lymap2 [[1.0 1.0 0.7 0.4] [0.0 30.0]] ; Line 107.1 Appendix A
   :fiald [[0.0 0.05 0.15 0.3 0.5 0.7 0.85 0.95 1.0] [0.0 2.0]] ; Line 108.1 Appendix A
   :mlymc [[0.075 0.03 0.015 0.011 0.009 0.008 0.007 0.006 0.005 0.005 0.005 0.005 0.005 0.005 0.005 0.005]
           [0.0 600.0]] ; Line 111.1 Appendix A
   :llmy1 [[1.2 1.0 0.63 0.36 0.16 0.055 0.04 0.025 0.015 0.01] [0.0 9.0]] ; Line 114.1 Appendix A
   :llmy2 [[1.2 1.0 0.63 0.36 0.16 0.055 0.04 0.025 0.015 0.01] [0.0 9.0]] ; Line 115.1 Appendix A
   :uilpc [[0.005 0.008 0.015 0.025 0.04 0.055 0.07 0.08 0.09] [0.0 1600.0]] ; Line 117.1 Appendix A
   :lfdr [[0.0 0.1 0.3 0.5] [0.0 30.0]] ; Line 122.1 Appendix A
   :lfrt [[20.0 13.0 8.0 4.0 2.0 2.0] [0.0 0.1]] ; Line 125.1 Appendix A
   :falm [[0.0 0.04 0.07 0.09 0.1] [0.0 4.0]] ; Line 126.1 Appendix A
   ;; nonrenewable resources
   :pcrum [[0.0 0.85 2.6 4.4 5.4 6.2 6.8 7.0 7.0] [0.0 1600.0]] ; Line 132.1 Appendix A
   :fcaor1 [[1.0 0.9 0.7 0.5 0.2 0.1 0.05 0.05 0.05 0.05 0.05] [0.0 1.0]] ; Line 135.1 Appendix A
   :fcaor2 [[1.0 0.9 0.7 0.5 0.2 0.1 0.05 0.05 0.05 0.05 0.05] [0.0 1.0]] ; Line 136.1 Appendix A
   ;; persistent pollution
   :ahlm [[1.0 11.0 21.0 31.0 41.0] [1.0 1001.0]] ; Line 145.1 Appendix A
   ;; human welfare index (World3-03)
   :lei [[0.0 0.16 0.33 0.5 0.67 0.84 1.0] [25.0 85.0]]
   :ei [[0.0 0.81 0.88 0.92 0.95 0.98 0.99 1.0] [0.0 7000.0]]
   :gdppc [[120.0 600.0 1200.0 1800.0 2500.0 3200.0] [0.0 1000.0]]})

(def tables
  "World3-03's tables: World3's with World3-91's and -03's revisions."
  (merge-with (fn [[_ r] ys] [ys r]) tables-1972
              {:lmf [0.0 1.0 1.43 1.5 1.5 1.5]
               :lmhs2 [1.0 1.5 1.9 2.0 2.0 2.0]
               :fm [0.0 0.2 0.4 0.6 0.7 0.75 0.79 0.84 0.87]
               :sfsn [1.25 0.94 0.715 0.59 0.5]
               :lymc [1.0 3.0 4.5 5.0 5.3 5.6 5.9 6.1 6.35 6.6 6.9 7.2 7.4 7.6 7.8 8.0 8.2 8.4 8.6 8.8 9.0 9.2 9.4 9.6 9.8 10.0]
               :pcrum [0.0 0.85 2.6 3.4 3.8 4.1 4.4 4.7 5.0]}))

(defn lookup "Table `k` of `tables` at x (TABHL)." ^double [tables k x]
  (let [[ys r] (or (tables k) (throw (ex-info (str "bnm.world3: no table " k) {:table k})))]
    (sf/table x ys r)))

(defn initial
  "The initial values, as WorldDynamics.jl's: the levels as Appendix A's,
  and the perceptions and delays (each by its output's name; :ppapr the
  pollution delay's outflow) started from values worked out on World3's
  1972 parameters and tables at a population of 1.61e9, ppolx 1, and the
  pollution delay from 1900's values of WorldDynamics' stand-in time
  series. `p` gives :nri."
  ([] (initial parameters))
  ([p]
   (let [p72 parameters-1972 tab (partial lookup tables-1972)
         pop 1.61e9 ppolx 1.0 frsn 0.82                        ; Line 41.2 Appendix A
         sopc (/ 1.5e11 pop) hsapc (tab :hsapc sopc)
         lmf (tab :lmf (/ (/ 4e11 pop) (:sfpc p72)))
         iopc (/ 0.7e11 pop)
         le (* (:len p72) lmf (tab :lmhs1 hsapc) (tab :lmp ppolx)
               (- 1.0 (* (tab :cmi iopc) (tab :fpu pop))))
         mtf (* (:mtfn p72) (tab :fm le))
         dtf (* (:dcfsn p72) frsn (tab :sfsn iopc) (tab :cmple le))
         fcapc (* (tab :fsafc (- (/ mtf dtf) 1.0)) sopc)
         iopc0 (/ 6.65e10 pop)
         ;; WorldDynamics' stand-ins at 1900: pcrum 17e-2, pop 16e8, aiph 6.6, al 9e8
         ppgr (* (:ppgf1 p72) (+ (* 0.17 1.6e9 (:frpm p72) (:imef p72) (:imti p72))
                                 (* 6.6 9e8 (:fipm p72) (:amti p72))))]
     {:p1 65e7 :p2 70e7 :p3 19e7 :p4 6e7                      ; Lines 2.2, 6.2, 10.2, 14.2 Appendix A
      :ehspc hsapc                                            ; Line 22 Appendix A
      :ple le                                                 ; Line 37 Appendix A
      :diopc iopc0                                            ; Line 40 Appendix A
      :aiopc iopc0                                            ; Line 43 Appendix A
      :fcfpc fcapc                                            ; Line 46 Appendix A
      :ic 2.1e11                                              ; Line 52.2 Appendix A
      :sc 1.44e11                                             ; Line 67.2 Appendix A
      :lufd 1.0                                               ; Line 82 Appendix A
      :al 0.9e9                                               ; Line 85.2 Appendix A
      :pal 2.3e9                                              ; Line 86.2 Appendix A
      :ai 5e9                                                 ; Line 99.1 Appendix A
      :uil 8.2e6                                              ; Line 120.2 Appendix A
      :lfert 600.0                                            ; Line 121.2 Appendix A
      :pfr 1.0                                                ; Line 128.1 Appendix A
      :nr (double (:nri p))                                   ; Line 129.2 Appendix A
      :ppapr ppgr                                             ; Line 141 Appendix A
      :ppol 2.5e7})))                                         ; Line 142.1 Appendix A

(defn- build [opts i]
   (let [tab (partial lookup (merge tables (:tables opts)))
         clip (fn [v a b k] (sf/clip a b (v :t) (v k)))]
     (sf/combine
      (sf/model {:params (merge parameters (:params opts))})
      ;; population, four cohorts (Pop4)
      (sf/model
       {:stocks (into {} (for [k [:p1 :p2 :p3 :p4]] [k {:init (i k)}]))
        :flows
        {:br {:to :p1 :rate (fn [v] (sf/clip (v :dr) (/ (* (v :tf) (v :p2) 0.5) (v :rlt)) (v :t) (v :pet)))} ; Line 30 Appendix A
         :d1 {:from :p1 :rate (fn [v] (* (v :p1) (v :m1)))}                       ; Line 3 Appendix A
         :mat1 {:from :p1 :to :p2 :rate (fn [v] (/ (* (v :p1) (- 1.0 (v :m1))) 15.0))} ; Line 5 Appendix A
         :d2 {:from :p2 :rate (fn [v] (* (v :p2) (v :m2)))}                       ; Line 7 Appendix A
         :mat2 {:from :p2 :to :p3 :rate (fn [v] (/ (* (v :p2) (- 1.0 (v :m2))) 30.0))} ; Line 9 Appendix A
         :d3 {:from :p3 :rate (fn [v] (* (v :p3) (v :m3)))}                       ; Line 11 Appendix A
         :mat3 {:from :p3 :to :p4 :rate (fn [v] (/ (* (v :p3) (- 1.0 (v :m3))) 20.0))} ; Line 13 Appendix A
         :d4 {:from :p4 :rate (fn [v] (* (v :p4) (v :m4)))}}                      ; Line 15 Appendix A
        :aux
        {:pop (fn [v] (+ (v :p1) (v :p2) (v :p3) (v :p4)))                        ; Line 1 Appendix A
         :m1 (fn [v] (tab :m1 (v :le)))                                           ; Line 4 Appendix A
         :m2 (fn [v] (tab :m2 (v :le)))                                           ; Line 8 Appendix A
         :m3 (fn [v] (tab :m3 (v :le)))                                           ; Line 12 Appendix A
         :m4 (fn [v] (tab :m4 (v :le)))                                           ; Line 16 Appendix A
         :dr (fn [v] (+ (v :d1) (v :d2) (v :d3) (v :d4)))                         ; Line 17 Appendix A
         ;; death rate
         :cdr (fn [v] (/ (* 1000.0 (v :dr)) (v :pop)))                            ; Line 18 Appendix A
         :le (fn [v] (* (v :len) (v :lmf) (v :lmhs) (v :lmp) (v :lmc)))           ; Line 19 Appendix A
         :lmf (fn [v] (tab :lmf (/ (v :fpc) (v :sfpc))))                          ; Line 20 Appendix A
         :hsapc (fn [v] (tab :hsapc (v :sopc)))                                   ; Line 21 Appendix A
         ;; Vensim's World3-03 takes lmhs2 only after iphst, not at it (CLIP's >=)
         :lmhs (fn [v] (if (> (v :t) (v :iphst)) (v :lmhs2) (v :lmhs1)))          ; Line 23 Appendix A
         :lmhs1 (fn [v] (tab :lmhs1 (v :ehspc)))                                  ; Line 24 Appendix A
         :lmhs2 (fn [v] (tab :lmhs2 (v :ehspc)))                                  ; Line 25 Appendix A
         :fpu (fn [v] (tab :fpu (v :pop)))                                        ; Line 26 Appendix A
         :cmi (fn [v] (tab :cmi (v :iopc)))                                       ; Line 27 Appendix A
         :lmc (fn [v] (- 1.0 (* (v :cmi) (v :fpu))))                              ; Line 28 Appendix A
         :lmp (fn [v] (tab :lmp (v :ppolx)))                                      ; Line 29 Appendix A
         ;; birth rate
         :cbr (fn [v] (/ (* 1000.0 (v :br)) (v :pop)))                            ; Line 31 Appendix A
         :tf (fn [v] (min (v :mtf) (+ (* (v :mtf) (- 1.0 (v :fce))) (* (v :dtf) (v :fce))))) ; Line 32 Appendix A
         :mtf (fn [v] (* (v :mtfn) (v :fm)))                                      ; Line 33 Appendix A
         :fm (fn [v] (tab :fm (v :le)))                                           ; Line 34 Appendix A
         :dtf (fn [v] (* (v :dcfs) (v :cmple)))                                   ; Line 35 Appendix A
         :cmple (fn [v] (tab :cmple (v :ple)))                                    ; Line 36 Appendix A
         :dcfs (fn [v] (clip v 2.0 (* (v :dcfsn) (v :frsn) (v :sfsn)) :zpgt))      ; Line 38 Appendix A
         :sfsn (fn [v] (tab :sfsn (v :diopc)))                                    ; Line 39 Appendix A
         :frsn (fn [v] (tab :frsn (v :fie)))                                      ; Line 41 Appendix A
         :fie (fn [v] (/ (- (v :iopc) (v :aiopc)) (v :aiopc)))                    ; Line 42 Appendix A
         :nfc (fn [v] (- (/ (v :mtf) (v :dtf)) 1.0))                              ; Line 44 Appendix A
         :fce (fn [v] (clip v 1.0 (tab :fce (v :fcfpc)) :fcest))                  ; Line 45 Appendix A
         :fcapc (fn [v] (* (v :fsafc) (v :sopc)))                                 ; Line 47 Appendix A
         :fsafc (fn [v] (tab :fsafc (v :nfc)))}})                                 ; Line 48 Appendix A
      (sf/smooth :ehspc :hsapc :hsid (i :ehspc))                                   ; Line 22 Appendix A
      (sf/smooth-n :ple :le :lpd 3 (i :ple))                                       ; Line 37 Appendix A
      (sf/smooth-n :diopc :iopc :sad 3 (i :diopc))                                 ; Line 40 Appendix A
      (sf/smooth :aiopc :iopc :ieat (i :aiopc))                                    ; Line 43 Appendix A
      (sf/smooth-n :fcfpc :fcapc :hsid 3 (i :fcfpc))                               ; Line 46 Appendix A
      ;; capital: industry, services, jobs
      (sf/model
       {:stocks {:ic {:init (i :ic)} :sc {:init (i :sc)}}                         ; Lines 52, 67 Appendix A
        :flows
        {:icir {:to :ic :rate (fn [v] (* (v :io) (v :fioai)))}                    ; Line 55 Appendix A
         :icdr {:from :ic :rate (fn [v] (/ (v :ic) (v :alic)))}                   ; Line 53 Appendix A
         :scir {:to :sc :rate (fn [v] (* (v :io) (v :fioas)))}                    ; Line 66 Appendix A
         :scdr {:from :sc :rate (fn [v] (/ (v :sc) (v :alsc)))}}                  ; Line 68 Appendix A
        :aux
        {:iopc (fn [v] (/ (v :io) (v :pop)))                                      ; Line 49 Appendix A
         :io (fn [v] (/ (* (v :ic) (- 1.0 (v :fcaor)) (v :cuf)) (v :icor)))       ; Line 50 Appendix A
         :icor (fn [v] (clip v (v :icor2) (v :icor1) :pyear))                     ; Line 51 Appendix A
         :alic (fn [v] (clip v (v :alic2) (v :alic1) :pyear))                     ; Line 54 Appendix A
         :fioai (fn [v] (- 1.0 (v :fioaa) (v :fioas) (v :fioac)))                 ; Line 56 Appendix A
         :fioac (fn [v] (clip v (v :fioacv) (v :fioacc) :iet))                    ; Line 57 Appendix A
         :fioacc (fn [v] (clip v (v :fioac2) (v :fioac1) :pyear))                 ; Line 58 Appendix A
         :fioacv (fn [v] (tab :fioacv (/ (v :iopc) (v :iopcd))))                  ; Line 59 Appendix A
         :isopc (fn [v] (clip v (v :isopc2) (v :isopc1) :pyear))                  ; Line 60 Appendix A
         :isopc1 (fn [v] (tab :isopc1 (v :iopc)))                                 ; Line 61 Appendix A
         :isopc2 (fn [v] (tab :isopc2 (v :iopc)))                                 ; Line 62 Appendix A
         :fioas (fn [v] (clip v (v :fioas2) (v :fioas1) :pyear))                  ; Line 63 Appendix A
         :fioas1 (fn [v] (tab :fioas1 (/ (v :sopc) (v :isopc))))                  ; Line 64 Appendix A
         :fioas2 (fn [v] (tab :fioas2 (/ (v :sopc) (v :isopc))))                  ; Line 65 Appendix A
         :alsc (fn [v] (clip v (v :alsc2) (v :alsc1) :pyear))                     ; Line 69 Appendix A
         :so (fn [v] (/ (* (v :sc) (v :cuf)) (v :scor)))                          ; Line 70 Appendix A
         :sopc (fn [v] (/ (v :so) (v :pop)))                                      ; Line 71 Appendix A
         :scor (fn [v] (clip v (v :scor2) (v :scor1) :pyear))                     ; Line 72 Appendix A
         :j (fn [v] (+ (v :pjis) (v :pjas) (v :pjss)))                            ; Line 73 Appendix A
         :pjis (fn [v] (* (v :ic) (v :jpicu)))                                    ; Line 74 Appendix A
         :jpicu (fn [v] (* 1e-3 (tab :jpicu (v :iopc))))                          ; Line 75 Appendix A
         :pjss (fn [v] (* (v :sc) (v :jpscu)))                                    ; Line 76 Appendix A
         :jpscu (fn [v] (* 1e-3 (tab :jpscu (v :sopc))))                          ; Line 77 Appendix A
         :pjas (fn [v] (* (v :jph) (v :al)))                                      ; Line 78 Appendix A
         :jph (fn [v] (tab :jph (v :aiph)))                                       ; Line 79 Appendix A
         :lf (fn [v] (* (+ (v :p2) (v :p3)) (v :lfpf)))                           ; Line 80 Appendix A
         :luf (fn [v] (/ (v :j) (v :lf)))                                         ; Line 81 Appendix A
         :cuf (fn [v] (tab :cuf (v :lufd)))}})                                    ; Line 83 Appendix A
      (sf/smooth :lufd :luf :lufdt (i :lufd))                                      ; Line 82 Appendix A
      ;; agriculture
      (sf/model
       {:stocks {:al {:init (i :al)} :pal {:init (i :pal)}                        ; Lines 85, 86 Appendix A
                 :uil {:init (i :uil)} :lfert {:init (i :lfert)}}                 ; Lines 120, 121 Appendix A
        :flows
        {:ldr {:from :pal :to :al :rate (fn [v] (/ (* (v :tai) (v :fiald)) (v :dcph)))} ; Line 96 Appendix A
         :ler {:from :al :rate (fn [v] (/ (v :al) (v :all)))}                     ; Line 116 Appendix A
         :lrui {:from :al :to :uil :rate (fn [v] (max 0.0 (/ (- (v :uilr) (v :uil)) (v :uildt))))} ; Line 119 Appendix A
         :lfr {:to :lfert :rate (fn [v] (/ (- (v :ilf) (v :lfert)) (v :lfrt)))}  ; Line 124 Appendix A
         :lfd {:from :lfert :rate (fn [v] (* (v :lfert) (v :lfdr)))}}            ; Line 123 Appendix A
        :aux
        {:lfc (fn [v] (/ (v :al) (v :palt)))                                      ; Line 84 Appendix A
         :f (fn [v] (* (v :ly) (v :al) (v :lfh) (- 1.0 (v :pl))))                 ; Line 87 Appendix A
         :fpc (fn [v] (/ (v :f) (v :pop)))                                        ; Line 88 Appendix A
         :ifpc (fn [v] (clip v (v :ifpc2) (v :ifpc1) :pyear))                     ; Line 89 Appendix A
         :ifpc1 (fn [v] (tab :ifpc1 (v :iopc)))                                   ; Line 90 Appendix A
         :ifpc2 (fn [v] (tab :ifpc2 (v :iopc)))                                   ; Line 91 Appendix A
         :tai (fn [v] (* (v :io) (v :fioaa)))                                     ; Line 92 Appendix A
         :fioaa (fn [v] (clip v (v :fioaa2) (v :fioaa1) :pyear))                  ; Line 93 Appendix A
         :fioaa1 (fn [v] (tab :fioaa1 (/ (v :fpc) (v :ifpc))))                    ; Line 94 Appendix A
         :fioaa2 (fn [v] (tab :fioaa2 (/ (v :fpc) (v :ifpc))))                    ; Line 95 Appendix A
         :dcph (fn [v] (tab :dcph (/ (v :pal) (v :palt))))                        ; Line 97 Appendix A
         :cai (fn [v] (* (v :tai) (- 1.0 (v :fiald))))                            ; Line 98 Appendix A
         :alai (fn [v] (clip v (v :alai2) (v :alai1) :pyear))                     ; Line 100 Appendix A
         :aiph (fn [v] (/ (* (v :ai) (- 1.0 (v :falm))) (v :al)))                 ; Line 101 Appendix A
         :lymc (fn [v] (tab :lymc (v :aiph)))                                     ; Line 102 Appendix A
         :ly (fn [v] (* (v :lyf) (v :lfert) (v :lymc) (v :lymap)))                ; Line 103 Appendix A
         :lyf (fn [v] (clip v (v :lyf2) (v :lyf1) :pyear))                        ; Line 104 Appendix A
         :lymap (fn [v] (clip v (v :lymap2) (v :lymap1) :pyear))                  ; Line 105 Appendix A
         :lymap1 (fn [v] (tab :lymap1 (/ (v :io) (v :io70))))                     ; Line 106 Appendix A
         :lymap2 (fn [v] (tab :lymap2 (/ (v :io) (v :io70))))                     ; Line 106 Appendix A
         :fiald (fn [v] (tab :fiald (/ (v :mpld) (v :mpai))))                     ; Line 108 Appendix A
         :mpld (fn [v] (/ (v :ly) (* (v :dcph) (v :sd))))                         ; Line 109 Appendix A
         :mpai (fn [v] (/ (* (v :alai) (v :ly) (v :mlymc)) (v :lymc)))            ; Line 110 Appendix A
         :mlymc (fn [v] (tab :mlymc (v :aiph)))                                   ; Line 111 Appendix A
         :all (fn [v] (* (v :alln) (v :llmy)))                                    ; Line 112 Appendix A
         :llmy (fn [v] (clip v (v :llmy2) (v :llmy1) :pyear))                     ; Line 113 Appendix A
         :llmy1 (fn [v] (tab :llmy1 (/ (v :ly) (v :ilf))))                        ; Line 114 Appendix A
         :llmy2 (fn [v] (tab :llmy2 (/ (v :ly) (v :ilf))))                        ; Line 115 Appendix A
         :uilpc (fn [v] (tab :uilpc (v :iopc)))                                   ; Line 117 Appendix A
         :uilr (fn [v] (* (v :uilpc) (v :pop)))                                   ; Line 118 Appendix A
         :lfdr (fn [v] (tab :lfdr (v :ppolx)))                                    ; Line 122 Appendix A
         :lfrt (fn [v] (tab :lfrt (v :falm)))                                     ; Line 125 Appendix A
         :falm (fn [v] (tab :falm (v :pfr)))                                      ; Line 126 Appendix A
         :fr (fn [v] (/ (v :fpc) (v :sfpc)))}})                                   ; Line 127 Appendix A
      (sf/smooth :ai :cai :alai (i :ai))                                          ; Line 99 Appendix A
      (sf/smooth :pfr :fr :fspd (i :pfr))                                         ; Line 128 Appendix A
      ;; nonrenewable resources
      (sf/model
       {:stocks {:nr {:init (i :nr)}}                                             ; Line 129 Appendix A
        :flows {:nrur {:from :nr :rate (fn [v] (* (v :pop) (v :pcrum) (v :nruf)))}} ; Line 130 Appendix A
        :aux
        {:nruf (fn [v] (clip v (v :nruf2) (v :nruf1) :pyear))                     ; Line 131 Appendix A
         :pcrum (fn [v] (tab :pcrum (v :iopc)))                                   ; Line 132 Appendix A
         :nrfr (fn [v] (/ (v :nr) (v :nri)))                                      ; Line 133 Appendix A
         :fcaor (fn [v] (clip v (v :fcaor2) (v :fcaor1) :pyear))                  ; Line 134 Appendix A
         :fcaor1 (fn [v] (tab :fcaor1 (v :nrfr)))                                 ; Line 135 Appendix A
         :fcaor2 (fn [v] (tab :fcaor2 (v :nrfr)))}})                              ; Line 136 Appendix A
      ;; persistent pollution, through a third-order material delay (DELAY3)
      (sf/delay-n :ppapr :ppgr :pptd 3 {:init (i :ppapr) :to :ppol})            ; Line 141 Appendix A
      (sf/model
       {:stocks {:ppol {:init (i :ppol)}}                                         ; Line 142 Appendix A
        :flows
        {:ppasr {:from :ppol :rate (fn [v] (/ (v :ppol) (* 1.4 (v :ahl))))}}      ; Line 144 Appendix A
        :aux
        {:ppgr (fn [v] (* (+ (v :ppgio) (v :ppgao)) (v :ppgf)))                   ; Line 137 Appendix A
         :ppgf (fn [v] (clip v (v :ppgf2) (v :ppgf1) :pyear))                     ; Line 138 Appendix A
         :ppgf2 (fn [v] (sf/switch (v :ppgf21) (v :ppgf22) (v :swat)))            ; Line 138 Appendix A
         :ppgio (fn [v] (* (v :pcrum) (v :pop) (v :frpm) (v :imef) (v :imti)))    ; Line 139 Appendix A
         :ppgao (fn [v] (* (v :aiph) (v :al) (v :fipm) (v :amti)))                ; Line 140 Appendix A
         :pptd (fn [v] (clip v (v :pptd2) (v :pptd1) :pyear))                     ; Line 141.1 Appendix A
         :ppolx (fn [v] (/ (v :ppol) (v :ppol70)))                                ; Line 143 Appendix A
         :ahlm (fn [v] (tab :ahlm (v :ppolx)))                                    ; Line 145 Appendix A
         :ahl (fn [v] (* (v :ahl70) (v :ahlm)))}})                                ; Line 146 Appendix A
      ;; shares of output, human welfare and ecological footprint
      (sf/model
       {:aux
        {:foa (fn [v] (/ (* 0.22 (v :f)) (+ (* 0.22 (v :f)) (v :so) (v :io))))   ; Line 147 Appendix A
         :foi (fn [v] (/ (v :io) (+ (* 0.22 (v :f)) (v :so) (v :io))))           ; Line 148 Appendix A
         :fos (fn [v] (/ (v :so) (+ (* 0.22 (v :f)) (v :so) (v :io))))           ; Line 149 Appendix A
         :hwi (fn [v] (/ (+ (v :lei) (v :ei) (v :gdpi)) 3.0))
         :lei (fn [v] (tab :lei (/ (v :le) (v :oy))))
         :ei (fn [v] (tab :ei (/ (v :gdppc) (v :gdpu))))
         :gdpi (fn [v] (/ (Math/log10 (/ (v :gdppc) (v :rlgdp))) (Math/log10 (/ (v :rhgdp) (v :rlgdp)))))
         :gdppc (fn [v] (tab :gdppc (/ (v :iopc) (v :gdpu))))
         :hef (fn [v] (/ (+ (v :alggha) (v :ulgha) (v :algha)) (v :tl)))
         :algha (fn [v] (/ (* (v :ppgr) (v :hup)) (v :hgha)))
         :alggha (fn [v] (/ (v :al) (v :hgha)))
         :ulgha (fn [v] (/ (v :uil) (v :hgha)))}}))))

(def ^:private delays
  "The perceptions and delays Vensim starts in equilibrium with their
  inputs (SMOOTH, DLINF3, DELAY3); lufd and pfr are SMOOTHI's from 1, ai
  from 5e9."
  [:ehspc :ple :diopc :aiopc :fcfpc :ppapr])

(defn model
  "World3-03's standard run as a stock-flow model, World3-03's
  `parameters` its :params (a run may override them). opts: :params and
  :tables (`parameters` and `tables` overridden), :inits (`initial`'s
  values overridden), :start :vensim (the perceptions and delays in
  equilibrium with their inputs at 1900, the default) or :worlddynamics
  (from `initial`'s values)."
  ([] (model {}))
  ([{:keys [inits start] :or {start :vensim} :as opts}]
   (let [wd (initial (merge parameters (:params opts)))
         i (merge (if (= start :vensim)
                    (-> (apply dissoc wd delays) (assoc :nr (fn [v] (v :nri))))
                    wd)
                  inits)]
     (build opts i))))

(defn run
  "World3-03's run (bnm.stockflow/run) of `model` with opts, from :from
  (1900) to :to (2100) by Euler steps of :dt (0.5)."
  ([] (run {}))
  ([opts]
   (sf/run (model opts) (merge {:from 1900.0 :to 2100.0 :dt 0.5} (select-keys opts [:from :to :dt])))))
