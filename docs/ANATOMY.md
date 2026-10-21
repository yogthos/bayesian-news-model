# The anatomy of known crises

Before training anything: how did the systems we know failed get there?
`bnm.anatomy` lines every known banking crisis up at its onset and asks how
each dimension stood in the years before, against the calm (periods more
than five from any crisis), in calm standard deviations (d). Only crises
before 2003, so nothing here comes from the years the models are scored on.
Superposed epochs are descriptive and in sample: they say what the known
failures had in common, not what forecasts out of sample. The full tables
are in data/runs/anatomy/anatomy.txt.

## Annual: 75 crises in 18 countries, 1870-2002 (Macrohistory)

| dimension (against the country's own decade) | -10 | -5 | -3 | -2 | -1 | 0 | +2 |
|---|---|---|---|---|---|---|---|
| credit | +0.25 | +0.16 | +0.25 | +0.46 | +0.51 | +0.42 | +0.14 |
| credit against the wage share | -0.08 | +0.10 | +0.31 | +0.33 | +0.31 | +0.09 | -0.25 |
| house prices | -0.17 | +0.01 | +0.16 | +0.32 | +0.25 | +0.12 | -0.15 |
| investment | +0.07 | +0.05 | +0.22 | +0.30 | +0.12 | -0.17 | -0.54 |
| current account | -0.06 | +0.10 | -0.18 | -0.28 | -0.24 | -0.25 | +0.10 |
| public debt | -0.08 | -0.19 | -0.23 | -0.14 | -0.17 | +0.07 | +0.28 |
| wage share | +0.32 | +0.09 | -0.01 | +0.08 | +0.19 | +0.33 | +0.51 |
| breadth of displacement | +0.10 | -0.00 | +0.09 | +0.04 | +0.17 | +0.23 | -0.19 |

## Quarterly: 130 crises of the modern catalogue, 1974-2002

(fewer with data at each lag: 9-26 for the national financial series)

| dimension | -10 | -7 | -4 | -2 | -1 | 0 |
|---|---|---|---|---|---|---|
| credit gap | +0.37 | +0.50 | +0.60 | +0.70 | +0.75 | +0.74 |
| credit, three-year change | +0.23 | +0.48 | +0.62 | +0.62 | +0.61 | +0.68 |
| household credit and house prices booming together | +0.98 | +1.46 | +1.01 | +0.55 | +0.44 | +0.19 |
| house prices, three-year change | +0.18 | +0.45 | +0.46 | +0.25 | +0.14 | +0.00 |
| equity volatility | +0.81 | +0.25 | +1.65 | +1.86 | +1.99 | +0.81 |
| the curve (long less short rate) | -0.03 | -0.42 | -0.62 | -0.69 | -0.48 | -0.29 |
| the VIX | -0.82 | -1.10 | -0.65 | -0.66 | -0.77 | -0.74 |

## What the known failures share

1. **A slow accumulation, years long.** Credit stands above its own decade
   ten years out and keeps building; the credit gap climbs steadily for
   the whole window. Small excesses add up: the quantity.
2. **A contradiction that opens: credit outrunning wages.** Credit against
   the wage share is the first to stand out (three years before) and
   closes after the crisis, as wages' share rises and credit falls back.
   Investment rises with it, then collapses after.
3. **The boom peaks before the break.** Household credit and house prices
   booming together peak seven to eight quarters before the onset and are
   already falling when it comes; house prices turn first. The crisis
   follows the turn of the boom, not its height: a stretched stock with a
   reversing flow (the Macrohistory study's stock-times-flow, docs/
   INVARIANTS.md).
4. **Tightening and fragility.** The curve flattens from seven quarters out;
   equity volatility jumps a year before (a system starting to flicker);
   the current account worsens two to three years before.
5. **A calm surface.** The world's volatility is low before crises (the VIX
   0.7-1.1 deviations below the calm): accumulation under an untroubled
   surface.
6. **The resolution.** After the onset, credit falls back, investment
   collapses, wages' share rises and public debt jumps: the losses are
   taken on by the state.

Smooth operation, by contrast, is the calm itself: credit moving with
wages, no boom to turn, a normal curve, steady volatility.

## What it means for the contradictions in the model

- **Contradictions are directional.** What matters is credit running ahead
  of wages, not the size of their gap either way. The trial 37 tensions
  accumulated the absolute gap, so a calm country with wages ahead of
  credit looked as tense as one in a credit boom; their accumulations stood
  below the calm before crises. They should accumulate the excess in the
  direction that strains the system (its positive part), resetting at the
  resolution.
- **The turn is the trigger.** A stretched stock whose flow reverses (credit
  gap high while house prices or the boom are falling from their peak) is
  the moment of transformation; the level alone keeps rising into and past
  the crisis.
- **Tension accumulates at the year, triggers show at the quarter.** The
  slow contradictions (credit against wages, the credit gap) build over
  years; the triggers (the turn of the boom, the curve, volatility) show in
  quarters. The year node should carry the accumulation down to the
  quarter; the quarter node watches for the turn.
- **A calm surface is not stability.** Low world volatility goes with
  accumulation, not against it.

## The contradictions measured with their direction (October 2026)

Accumulated as strain (each tension's positive part, decaying 0.7 a year,
reset at each crisis), the contradictions now do what the theory says:
they build before the known crises and are released at the onset. The
absolute measure had them below the calm before crises.

| (annual, Macrohistory countries) | -4 | -3 | -2 | -1 | 0 | turn |
|---|---|---|---|---|---|---|
| business credit (unused column) | +0.60 | +0.77 | +1.15 | +1.04 | +0.78 | -4 |
| household credit ahead of wages, strain | +0.17 | +0.41 | +0.56 | +0.58 | -0.84 | -3 |
| house prices ahead of rents, strain | +0.06 | +0.12 | +0.34 | +0.44 | -0.71 | -2 |
| loan-to-deposit ratio (unused column) | +0.04 | +0.25 | +0.51 | +0.45 | +0.47 | -2 |
| credit ahead of wages, strain | +0.05 | +0.15 | +0.27 | +0.31 | -0.74 | -1 |
| turn: credit stretched, equity off its peak | +0.38 | +0.09 | +0.14 | +0.44 | +0.82 | -1 |
| WDI credit ahead of TFP growth, strain (8 crises) | +0.24 | +0.61 | +0.79 | +1.06 | -0.31 | -3 |

Across all countries (66 crises of the modern catalogue with Penn World
Table and WDI data, mostly emerging markets, 1974-2002), productivity
growth is depressed for a decade before crises (-0.4 to -0.5), but credit
relative to its own decade is not elevated, and credit ahead of
productivity is flat: many of these crises do not follow the credit boom's
path. The anatomy differs by type of crisis (a domestic credit boom, an
external or currency crisis), which is what the crisis-mechanics data
(ESRB cause flags, Laeven-Valencia details) would let the model separate.

## The downloaded dimensions (October 2026)

From the sources in data/raw/slow/DOWNLOADS-2026-10-21.md (scripts/
lattice-data.py), on the modern catalogue's crises of 1974-2002 in every
country with data:

| | -10 | -5 | -3 | -1 | 0 | n |
|---|---|---|---|---|---|---|
| bank equity ahead of nonfinancial, strain (BVX) | +0.30 | +0.41 | +1.05 | +1.20 | -0.32 | 29 |
| bank equity, three-year return (BVX) | +0.25 | +0.51 | +1.44 | +0.64 | -0.45 | 30 |
| bank equity crashes in the decade before (BVX) | +0.39 | +0.43 | +0.43 | +0.79 | +1.35 | 40 |
| top 10% income share (WID) | -0.13 | +0.30 | +0.49 | +0.50 | +0.39 | 101 |
| bottom 50% income share (WID) | -0.11 | -0.38 | -0.35 | -0.40 | -0.31 | 101 |
| top incomes ahead of the bottom half, strain (WID) | -0.27 | +0.02 | +0.23 | +0.33 | -0.38 | 101 |
| top 1% wealth share (WID) | -0.21 | +0.13 | +0.23 | +0.39 | +0.30 | 99 |
| profit rate, three-year change (EPWT) | -0.03 | -0.24 | -0.03 | -0.14 | -0.19 | 103 |
| financial distress, 0-15 (Romer & Romer) | -0.07 | +1.60 | +1.36 | +2.31 | +5.65 | 14 |

- **Finance ahead of production** in market prices: bank equity outruns
  nonfinancial equity for a decade, peaks three years out and is falling
  when the crisis comes (the turn); the strain is released at the onset.
- **Small crises feed large ones**: bank equity crashes in the decade before
  stand above the calm, and graded distress rises from seven years out.
- **Distribution polarizes**: the top's share rises and the bottom half's
  falls for six years before, the top's wealth concentrating; the strain
  between them builds from five years out and is released at the crisis.
- **Profitability weakens** for most of the decade before.
- Household and corporate debt (IMF) and sectoral credit (Mueller-Verner)
  barely cover crises before 2003, so they cannot be judged here.

A flaw found on the way: series a source holds nearly flat (WID's imputed
years) made any change look enormous against their own decade; stretches
are now floored at 2% of the level and capped at 5.

## By type, and the other crises (Laeven & Valencia 2018)

The 2018 database's Table 1 (data/raw/slow/lv2018, parsed from the paper's
text layer by scripts/lv2018-dates.py) dates currency and sovereign crises
beside the banking ones. Of the modern catalogue's 130 banking crises of
1974-2002, 41 are twin (a currency crisis within a year), 89 domestic:

- **twin crises** follow finance running ahead of production for eight
  years (bank equity ahead of nonfinancial, strain +1.0 to +1.4), bank
  equity crashes accumulating, productivity falling, credit pulling
  ahead of productivity late;
- **domestic crises** follow polarization (the top's income share ahead of
  the bottom half's, from three years out), rising graded distress from
  six years out, and bank equity ahead of nonfinancial only late.

The type is known only in hindsight; what is known before is the other
crises: currency crises in the three and ten years before and sovereign
crises in the ten years before stand +0.2 to +0.33 above the calm from
four years out. Laeven & Valencia's own dates end in 2017, so the year
node reads currency crises by their rule (a depreciation against the
dollar of 30% or more, 10 points above the year before's, once per five
years) from WDI's exchange rates: 84% of these fall within a year of one
of theirs, and they find 81% of theirs.
