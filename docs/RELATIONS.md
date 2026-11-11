# Relations: the hypothesis registry

Every edge the world model may read (meta-synthesis: each a hypothesis, its source, its type, its prior, and after each run the weight it earned). The 22 candidate edges are data/runs/sequencing/graph.edn (docs/SEQUENCING.md).

Updated 10 November 2026 from the runs of trials 42 to 45 (docs/PHASE-CHANGES.md). The weight is the edge's group in the model averaging at the child's country year node at the end of 2022, the last scored origin year, from each run's relations.tsv, with the form the edge passed. A dash is an edge the trial's graph did not keep.

- **Trial 42** (t42-world): all 22 edges, a group per parent in the model averaging (the parent's state, its onset excursions, its state through executive constraints). No edge's weight is above 0.004. Result: a loss.
- **Trial 43** (t43-world, graph-t43.edn): twelve edges kept on the design years, one input each, each nested in the node's own model. Result: level.
- **Trial 44** (t44-world, graph-t44.edn): seven edges after the amendment, own parts where chosen, with early warnings. Result: level.
- **Trial 45** (t45-world, graph-t45.edn): five edges, diagnostics clustered by year, with one warning and the stock-flow states. Result: level.
- **Trial 45, supported edges only** (t45fix2-world, graph-t45-0cf-fix.edn): the two edges the diagnostic keeps after the warm-up fix (bnm-0cf); a sensitivity run after the outcome. Result: level.

No edge is in the base of record (t48-world-norel, the world without relations): edges that earned weight at their nodes did not improve the forecasts out of sample.

| edge | source | type | prior (mean, variance) | trial 42 | trial 43 | trial 44 | trial 45 | trial 45, supported |
|---|---|---|---|---|---|---|---|---|
| banking_crisis → currency_crisis | data | exciting | 0.2, 0.1 | <0.001 | destab 0.40 | - | - | - |
| banking_crisis → growth_collapse | data | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| banking_crisis → inflation_crisis | data | exciting | 0.2, 0.1 | <0.001 | e1 0.20 | - | - | - |
| banking_crisis → sovereign_default | data | exciting | 0.2, 0.1 | 0.002 | - | - | - | - |
| conflict_onset → coup | data | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| conflict_onset → violence_surge | data | exciting | 0.2, 0.1 | <0.001 | post3 0.58 | post3 0.29 | post3 0.29 | - |
| conflict_onset → war_onset | data | exciting | 0.2, 0.1 | <0.001 | post3 0.96 | post3 0.87 | post3 0.89 | post3 0.96 |
| coup → conflict_onset | theory | exciting | 0.2, 0.1 | <0.001 | post3 0.28 | - | - | - |
| currency_crisis → banking_crisis | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| currency_crisis → inflation_crisis | data | exciting | 0.2, 0.1 | <0.001 | post3 0.03 | post3-own 0.22 | - | - |
| growth_collapse → conflict_onset | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| growth_collapse → coup | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| growth_collapse → protest_surge | theory | exciting | 0.2, 0.1 | <0.001 | e5 0.57 | destab 0.99 | level-own 0.20 | - |
| growth_collapse → sovereign_default | data | exciting | 0.2, 0.1 | 0.004 | - | - | - | - |
| inflation_crisis → banking_crisis | data | inhibiting | -0.2, 0.1 | <0.001 | e1 0.00 | - | e1 0.00 | e1 0.00 |
| inflation_crisis → coup | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| protest_surge → coup | theory | exciting | 0.2, 0.1 | <0.001 | post3 0.81 | post3 0.89 | - | - |
| sovereign_default → banking_crisis | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| sovereign_default → fiscal_consolidation | theory | exciting | 0.2, 0.1 | <0.001 | - | - | - | - |
| sovereign_default → inflation_crisis | data | exciting | 0.2, 0.1 | <0.001 | post3 0.24 | - | - | - |
| violence_surge → conflict_onset | theory | exciting | 0.2, 0.1 | <0.001 | post3 0.08 | post3 0.53 | - | - |
| violence_surge → war_onset | data | exciting | 0.2, 0.1 | <0.001 | post3 0.00 | post3 0.07 | post3 0.07 | - |
