# T9: do the extra dimensions make the forecasts more robust?

Bead bnm-3qu.10. Written 23 October 2026 for trial T9. Not registered
yet. Only the design years were looked at: every row read has its outcome
window closed by 2002. No outcome after 2002 was read or scored.

Every trial so far measured the mean score. Trials 45, T1, T2, T3 and T4
found the stock-flow states, the material base, the links and the
warnings level, or equivalent to nothing, over the base. T8b showed that
the base's calibration drifts. A dimension can still earn its place if it
holds skill up when other inputs fail or the regime shifts, without
raising the mean. This trial measures that.

## Design

tools/bnm/robustness.clj (`bnm.robustness`). It requires bnm.attribution
and reuses its machinery: the year-node rows, the states and their
readers, the coverage-matched placebo, the fixed-effect logit
(`fit-fe`), the within-year matching and the scaler.

**Rows.** The year node of t44-world-norel, as in T4: year-end origins t,
country rows, the outcome an onset within (t, t + 2]. The ten design
kinds of T4 (protest and violence surges start in 1979 and can't be
scored in every era, and food price shocks have no design rows).

**Models.** Each is a logit with a fixed effect per origin year, fitted on
the same rows, scored within each kind-year (shifted to M0's level, so
only where the onsets fall counts). Skill is M0's mean loss less the
model's, kinds weighted equally. Log score is primary, Brier for the
record.

| Route | Base | Base + D | Placebo |
|---|---|---|---|
| lattice (criteria 1, 3, 4) | B: a + b logit p, p the lattice's forecast (T4's M_B') | B+D: B plus the states (M_B'S) | B+P': B plus T4's coverage-matched placebo (M_B'P') |
| gauges (criterion 2; 1, 3, 4 for the record) | G: a logit on the lattice year node's named gauges and the event streams | G+D | G+P' |

D, the extra dimensions, is T4's thirteen states: T1's ten (credit gap,
Keen debt gap, debt service, reserves, youth bulge, elite
overproduction, anocracy, food import exposure, fuel price gap,
technology gap) and the three material levels (arable land, grain use,
power capacity). Trial 45's three chosen states were the own parts of
those same three levels, so they are covered as levels. Each enters with
its missing indicator, read as T4 reads it. P' gives each country its own
missing indicators and another country's values, so robustness that comes
from having more inputs, or from where the data are, is credited to the
placebo too.

**Why a gauge base for dropout.** The lattice's nodes learn online: every
origin is scored and then learned from, so there is no "scoring time" at
which an input can be knocked out with the weights held. Doing it inside
the lattice would mean a world rerun per scenario (hours each) with a
change to the lattice's code. So dropout uses an attribution-style
approximation. G is a ridged logit (same `fit-fe`) on what the lattice's
year node reads, as bnm.lattice-political builds it:

- slow, 29 inputs: the year row of bnm.political-gauges (V-Dem's regime,
  repression, exclusion and the military; Maddison's income and growth;
  material capabilities; WDI's infant mortality, working-age share,
  tertiary enrolment, inflation and food inflation; WID's top 10 and
  bottom 50; the fiscal series), data year t − 1;
- events, 5 inputs: GDELT/ICEWS protest (3 and 12 months), violence (3
  and 12) and coercion (12) at the year end, as bnm.named reads them for
  the quarter nodes, whose forecasts carry them into the year node;
- history, 8 inputs: the kind's own (log years since the last onset,
  counts in 10 and 30 years), four political kinds' onsets in 10 years,
  and the world's share of countries with an onset in three years.

Unknown gauges enter at the fitted rows' centre (0 after scaling), as the
lattice does. There are no missing indicators on the gauges. Banking
crises use the same gauges here, while the lattice gives them their own
(bnm.lattice-banking), so for that kind G is a rougher stand-in.

**Eras.** The design origins 1960-2000 are cut into four eras at 1960,
1971, 1982 and 1993. Each era holds the origins whose window lies inside
it (1960-68, 1971-79, 1982-90, 1993-2000) and is scored by a fit on the
other three, so no fitted outcome year is a scored one (tested). In the
registered run the fit is on 1960-2002 (origins 1960-2000) and the scored
origins 2002-2020 (outcomes 2003-2022) are sliced into four eras at 2002,
2007, 2012 and 2017.

**Ridge.** One precision for the gauges, chosen first on G, then one for
the states in each of B+D, B+P', G+D and G+P', given G's, each from 1, 10,
..., 10,000 by the rotation's pooled within log score. They are written
to data/runs/sequencing/robustness-t9-penalties.edn and read from it for
the registered run.

**Intervals.** 90%, from 1,000 bootstrap draws of blocks of two
consecutive origin years, seed 1. The fits are held, so the intervals
cover the scored years' sampling and not the refit. Every statistic,
including the calibration slopes, is recomputed in each draw.

## Criteria

1. **Worst case.** For each era × kind cell, skill(B+D) − skill(B). The
   minimum over the cells (minimax) and their 10th percentile (lowest
   decile), the same for the placebo, and each less the placebo's. The
   worst era (kinds pooled) and the worst kind (eras pooled) are
   reported beside them. A minimum of noisy cells is negative even for a
   useless input, so the contrast with the placebo, which has the same
   number of inputs and the same coverage, is what can be read.
2. **Input dropout** (gauge route). At scoring time, with the fits held, a
   group of the base's inputs enters as unknown:
   - (a) events: the five event-stream gauges;
   - (b) slow: the 29 year-row gauges (V-Dem, accounts, WDI);
   - (c) dark: for each scored year, a random 20% of the countries have
     their event and year-row gauges unknown. Their onset histories are
     kept, because onsets are still observed. The loss is averaged over
     ten draws of the dark countries;
   - (d) lag2: every publication lag doubled. V-Dem is read two years
     late, not one. The accounts are read four years late, not two. Each
     WDI and event-stream value is published at available + (available −
     period), held so that no period is known before an earlier one. The
     onset catalogues' known-lag is doubled for the histories. The event
     streams have no recorded lag (published the month they cover), so
     doubling leaves them as they are.
   The states are left intact in every scenario: the question is whether
   they hold skill up when the base's inputs fail. Statistic: the
   degradation ratio, (skill lost by G+D) / (skill lost by G), and the
   placebo's (skill lost by G+P') / (skill lost by G). Below 1 means D
   cushions the loss. The ratio is undefined, and unstable, where G loses
   little.
3. **Calibration stability.** In each era, the slope b of logit(y) =
   c(kind, year) + b·η, with η the model's log odds, pooled over kinds,
   one level per kind-year. The drift is the population SD of b over the
   four eras. Statistic: drift(B+D) − drift(B), and the same against the
   placebo. The mean |b − 1| is reported for the record.
4. **Era spread.** The SD over the eras of the pooled skill. Statistic:
   SD(B+D) / SD(B), and SD(B+D) − SD(B+P').

## Design-year results

data/runs/sequencing/robustness-t9-design.tsv (columns: criterion,
route, stat, scenario, score, value, lo, hi). Ten kinds, within mode, log
score unless stated, 90% intervals. Ridge precisions chosen: 1,000 for
every model (G's gauges, and the states in B+D, B+P', G+D and G+P').

### The mean, for orientation

| | log | Brier |
|---|---|---|
| skill(B), lattice recalibrated | +0.0079 [+0.0066, +0.0093] | +0.00111 [+0.00078, +0.00147] |
| skill(B+D) − skill(B) | +0.0014 [+0.0010, +0.0018] | +0.00022 [+0.00008, +0.00037] |
| skill(B+P') − skill(B) | +0.0008 [+0.0004, +0.0011] | +0.00015 [+0.00003, +0.00026] |
| skill(G), gauge logit | +0.0099 [+0.0089, +0.0108] | +0.00148 [+0.00119, +0.00179] |
| skill(G+D) − skill(G) | +0.0010 [+0.0008, +0.0012] | +0.00014 [+0.00005, +0.00022] |
| skill(G+P') − skill(G) | +0.0005 [+0.0002, +0.0008] | +0.00009 [−0.00000, +0.00019] |

On the four-era rotation the states add more than T4's two-era rotation
found (+0.0008), and about half of it is the placebo's, i.e. coverage
and more inputs. Out of sample T4 found the increment level (+0.0001).
The gauge logit beats the lattice's own forecast in within mode, which
says the year node's "where" is not all it could be; that is for the
record here.

### 1. Worst case (lattice route)

| | D | P' | D − P' |
|---|---|---|---|
| minimax over era × kind cells | −0.0074 [−0.0151, −0.0032] | −0.0019 [−0.0093, −0.0015] | −0.0055 [−0.0087, +0.0022] |
| lowest decile of cells | −0.0015 [−0.0027, −0.0008] | −0.0007 [−0.0018, −0.0006] | −0.0007 [−0.0015, +0.0001] |
| worst era, kinds pooled | +0.0008 [+0.0000, +0.0012] | +0.0001 [−0.0006, +0.0005] | +0.0007 [−0.0002, +0.0013] |
| worst kind, eras pooled | −0.0012 [−0.0040, −0.0007] (growth collapses) | −0.0004 [−0.0021, −0.0001] | −0.0007 [−0.0031, +0.0006] |

Per era, D − B: 1960-68 +0.0013, 1971-79 +0.0020, 1982-90 +0.0014,
1993-2000 +0.0008, every one above zero; the placebo's +0.0010, +0.0011,
+0.0010, +0.0001. Per kind D helps sovereign defaults (+0.0061), currency
crises (+0.0036), conflict onsets (+0.0016), wars and inflation crises
and hurts growth collapses (−0.0012); banking crises, coups, fiscal
consolidations and market crashes are level. The cell minimum is a
single noisy era-kind cell (the states' larger spread makes their worst
cell worse than the placebo's), so the cell minimax and decile are not
informative at this size. The era-level worst case is.

### 2. Input dropout (gauge route)

| Scenario | skill lost by G | ratio, D | ratio, P' | D − P' |
|---|---|---|---|---|
| (a) events | −0.0001 [−0.0002, +0.0000] | (undefined) | (undefined) | |
| (b) slow | +0.0041 [+0.0032, +0.0049] | 0.74 [0.68, 0.79] | 0.85 [0.81, 0.89] | −0.11 [−0.14, −0.08] |
| (c) dark | +0.0009 [+0.0007, +0.0011] | 0.76 [0.70, 0.80] | 0.86 [0.81, 0.89] | −0.10 [−0.13, −0.08] |
| (d) lag2 | +0.0010 [+0.0007, +0.0013] | 0.83 [0.79, 0.87] | 0.93 [0.89, 0.97] | −0.10 [−0.14, −0.07] |

On Brier: slow 0.59 against 0.78 (D − P' −0.19 [−0.29, −0.14]), dark
0.58 against 0.76 (−0.19 [−0.30, −0.13]), lag2 0.84 against 0.96 (−0.12
[−0.38, +0.06]).

- **The states cushion the loss of the base's slow gauges, of a fifth of
  the countries going dark, and of doubled lags**: G+D loses about three
  quarters of what G loses, the placebo about seven eighths. A good part
  of the cushion is coverage and having more inputs (the placebo's ratio
  is below 1 too), but the states' values add a tenth of the base's loss
  past it on log score and a fifth on Brier.
- **The event streams carry nothing on the design years**: knocking
  them out makes G slightly better. GDELT starts in 1979, so the first
  two eras have no event data and the fits learn little from them. The
  ratio is undefined here. In 2003-2022 the event streams are present
  throughout, so this scenario is reported but cannot be judged from the
  design years.

### 3. Calibration stability

Era slopes b (lattice route):

| Era | B | B+D | B+P' |
|---|---|---|---|
| 1960-68 | 0.96 | 1.05 | 1.01 |
| 1971-79 | 0.87 | 0.95 | 0.92 |
| 1982-90 | 0.76 | 0.81 | 0.79 |
| 1993-2000 | 1.29 | 1.21 | 1.26 |
| drift (SD) | 0.199 | 0.148 | 0.172 |

drift(B+D) − drift(B) −0.051 [−0.072, −0.019]; drift(B+D) − drift(B+P')
−0.024 [−0.042, −0.003]; mean |b − 1| 0.13 against 0.18 (−0.049
[−0.078, +0.004]). The states steady the base's calibration across eras,
and by more than the placebo does. On the gauge route it goes the other
way (drift +0.029 [+0.007, +0.047] over G), but G's slopes are all far
above 1 (1.25-1.93: its ridge, chosen for the score, shrinks the gauges
too far), so the gauge route's calibration is a poor stand-in.

### 4. Era spread

SD over eras of the pooled log skill: B 0.0018, B+D 0.0015, ratio 0.81
[0.64, 1.12]; B+D − B+P' −0.00005 [−0.00047, +0.00046]. Level. Four eras
give a spread too noisy to read.

### Reading

On the design years the extra dimensions do something the mean-score
trials could not see. When the base's slow gauges, its coverage or its
timeliness fail, a base with the states loses a quarter less skill, and
about a tenth of the base's loss less than with matched placebo states.
The base's calibration slope drifts less across eras with them. The worst
era is better with them than without (+0.0008 [+0.0000, +0.0012]), but
not clearly better than with the placebo (+0.0007 [−0.0002, +0.0013]).
Worst cells and era spread are too noisy to
say anything with four eras.

Caveats:
- The dropout is on a stand-in for the lattice, not on the lattice. It
  reads the same gauges but fits them in one logit.
- The fits are held in the bootstrap, so the intervals are too narrow by
  the refit's variance. That matters most for the contrasts against the
  placebo, whose intervals are narrow because the draws move both sides
  together.
- The penalties were chosen on the rotation they are scored on (as in
  T4).
- The design increment over the base (+0.0014) did not hold out of
  sample in T4 (+0.0001), so the robustness gains may shrink too.

## For a registered run

    jolt -A:tools -M -m bnm.robustness --fit-from 1960 --fit-to 2002 \
      --from 2002 --to 2022 --eras 2002,2007,2012,2017 \
      --penalties data/runs/sequencing/robustness-t9-penalties.edn \
      --reps 1000 --seed 1 --dark-draws 10 --dark-frac 0.2 \
      --out data/runs/sequencing/robustness-t9-registered.tsv

This fits on origins 1960-2000 (outcomes to 2002) and scores origins
2002-2020, so the scored outcome years are 2003-2022 exactly. `--from` is
the first scored origin and `--to` the last outcome year, as in
bnm.attribution. Origin 2001 would cover 2002 and is refused. The kinds
default to the design's ten.

## What to pre-register

Registered before any outcome after 2002 is read, with the command
above: ten kinds, within mode, log score, penalties fixed from the design
file, 1,000 block-bootstrap draws, one-sided 5% (the end of the 90%
interval). Primary tests, Holm over the five:

1. **Dropout, slow** (gauge route): ratio(D) − ratio(P') < 0, upper end
   below 0 (design −0.11 [−0.14, −0.08]);
2. **Dropout, dark**: the same (design −0.10 [−0.13, −0.08]);
3. **Dropout, lag2**: the same (design −0.10 [−0.14, −0.07]);
4. **Calibration drift** (lattice route): drift(B+D) − drift(B+P') < 0
   (design −0.024 [−0.042, −0.003]);
5. **Worst era** (lattice route): worst-era(D) − worst-era(P') > 0, lower
   end above 0 (design +0.0007 [−0.0002, +0.0013]: level).

For the record: ratio(D) < 1 in each scenario; the events scenario (now
judgeable, since the event streams cover every test year); the cell
minimax and lowest decile; the worst kind; the era spread; drift(B+D) −
drift(B); every statistic on Brier; the gauge route's criteria 1, 3 and
4; the per-era and per-kind numbers.

**Keep the dimension** (for robustness, whatever the mean says) if at
least one of tests 1-4 passes after Holm and nothing shows harm. Harm is
the worst-era statistic worst-era(D) with its lower end below −0.002 log
(T4's equivalence bound on the mean), i.e. an era where the states cost
more than "nothing". Test 5 passing alone is not enough, since it is the
noisiest. If only dropout passes, the states are kept as a fallback
against failing inputs. If only calibration passes, they are kept as a
stabilizer of the base's calibration. Neither is a claim that they
improve the forecast's mean. If none passes, the robustness case for the
stock-flow states and the material levels fails at the year node too.
