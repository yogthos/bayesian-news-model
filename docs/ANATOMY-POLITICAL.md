# The anatomy of known political onsets

As docs/ANATOMY.md for banking crises: every known onset of each political
kind before 2003 lined up at its year, each year gauge against the calm
(years more than five from any onset of the kind in the country), in calm
standard deviations (d). `bnm.anatomy-political`; the full tables are in
data/runs/anatomy/anatomy-political.txt. Descriptive and in sample, on
years no test scores.

## The catalogues

| kind | onsets before 2003 | countries | from | long catalogue before the modern |
|---|---|---|---|---|
| conflict_onset | 595 | 118 | 1816 | Correlates of War intra-state wars to 1945 (wars only: every one reached 1000 battle deaths), UCDP from 1946 |
| war_onset | 378 | 87 | 1816 | the same wars to 1945, UCDP's 1000-death escalations from 1946 |
| coup | 614 | 109 | 1875 | Archigos removals by the military or other government actors to 1944 (successful only), Cline (attempts too) from 1945 |
| protest_surge | 90 | 45 | 1980 | none (GDELT from 1979) |
| violence_surge | 411 | 128 | 1980 | none |

Each state is covered from its entry into the state system (Gleditsch-Ward).
The new sources are in data/raw/slow/cow-intra, archigos and maddison
(scripts/political-long.py writes data/derived/political-long.csv,
political-states.csv and political-annual.csv).

## What the known onsets share

1. **Traps, not accumulation.** The strongest regularity by far is the
   kind's own past: onsets in the previous ten years stand 3-4 deviations
   above the calm for conflicts, wars and coups, and the other kinds' past
   onsets stand above it too (coups 0.5 before conflicts, conflicts 0.4
   before coups). The conflict trap and the coup trap.
2. **Structure.** Poverty (log GDP per head -0.6), infant mortality (+0.7
   to +1.2), the military in the executive (+0.6, and +1.0 to +1.2 before
   coups), exclusion by social and economic group (+0.5 to +0.8),
   neopatrimonial rule, weak rule of law, repression: all stand apart
   from the calm a decade before and barely move. Countries at risk are a
   different kind of country, more than countries in a different moment.
3. **Pressure building against growth, not released at the onset.** As
   strain (each tension's positive part, decaying 0.7 a year), from ten
   years before to the year before:
   - population growth ahead of output growth: +0.18 to +0.30 before
     conflicts, +0.25 to +0.45 before coups, +0.40 to +0.64 before
     violence surges;
   - urban growth ahead of output growth: +0.30 to +0.41 before conflicts,
     +0.23 to +0.32 before wars, +0.29 to +0.54 before coups;
   - inflation ahead of growth: from about zero to +0.22 to +0.27 before
     conflicts, wars and coups, to +0.68 before violence surges;
   - democracy ahead of the rule of law (democratization outrunning
     institutions): +0.28 to +0.49 before coups, +0.15 to +0.35 before
     protest surges, +0.32 to +0.71 before violence surges;
   - education ahead of growth (elite overproduction): -0.22 to +0.33
     before protest surges, +0.15 to +0.45 before violence surges;
   - social exclusion ahead of democracy: standing at +0.6 to +0.7 before
     wars, +0.4 to +0.55 before conflicts and coups;
   - the top's income ahead of the bottom half's: +0.29 to +0.41 before
     coups.
   **No gap closes after the onset**: every one stays where it was or
   widens over the next two years. A banking crisis resolves its
   contradiction (credit falls back against wages); a coup or a war does
   not. A first pass reset the strain at each onset, as for banking, and
   showed a "release" that was the reset's own artefact; with it, the
   country in a trap had its strain wiped by every onset and the lattice's
   strain ran against its forecasts in the smoke checks (r -0.2 to -0.7).
   The political lattice never resets: strain only decays.
4. **Growth falls before.** Output growth over three years falls from five
   years before wars and violence surges (-0.2 to -0.46).
5. **Protest is different.** Protest surges come in richer, more
   democratic, less unequal places with lower infant mortality; the
   strains that build are democracy ahead of the rule of law and
   education ahead of growth, not inequality (the top-against-bottom
   strain stands below the calm).
6. **One turn.** Before coups, participation stretched while democracy
   falls from its three-year peak stands +0.2 to +0.38 above the calm
   (closure after mobilization). The other candidate turns (growth
   reversing after a boom, Davies's J-curve; backsliding in a middle
   regime) show nothing.

## What it means for the political lattice

- The year nodes carry the history and structural groups with priors
  where the literature and the anatomy agree: past onsets (the traps),
  poverty, infant mortality, exclusion, the military's place.
- Tensions per kind are the strains in point 3, the turn for coups only,
  accumulated without resolution.
- Whether the contradictions add out of sample is the question trial 40
  asks.
