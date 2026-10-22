# Relations: the hypothesis registry

Every edge the world model may read (meta-synthesis: each a hypothesis, its source, its type, its prior, and after each run the weight it earned). Generated from data/runs/sequencing/graph.edn; weights are added by bnm.world-run.

| edge | source | type | prior (mean, variance) | earned weight |
|---|---|---|---|---|
| banking_crisis → currency_crisis | data | exciting | 0.2, 0.1 | (not yet run) |
| banking_crisis → growth_collapse | data | exciting | 0.2, 0.1 | (not yet run) |
| banking_crisis → inflation_crisis | data | exciting | 0.2, 0.1 | (not yet run) |
| banking_crisis → sovereign_default | data | exciting | 0.2, 0.1 | (not yet run) |
| conflict_onset → coup | data | exciting | 0.2, 0.1 | (not yet run) |
| conflict_onset → violence_surge | data | exciting | 0.2, 0.1 | (not yet run) |
| conflict_onset → war_onset | data | exciting | 0.2, 0.1 | (not yet run) |
| coup → conflict_onset | theory | exciting | 0.2, 0.1 | (not yet run) |
| currency_crisis → banking_crisis | theory | exciting | 0.2, 0.1 | (not yet run) |
| currency_crisis → inflation_crisis | data | exciting | 0.2, 0.1 | (not yet run) |
| growth_collapse → conflict_onset | theory | exciting | 0.2, 0.1 | (not yet run) |
| growth_collapse → coup | theory | exciting | 0.2, 0.1 | (not yet run) |
| growth_collapse → protest_surge | theory | exciting | 0.2, 0.1 | (not yet run) |
| growth_collapse → sovereign_default | data | exciting | 0.2, 0.1 | (not yet run) |
| inflation_crisis → banking_crisis | data | inhibiting | -0.2, 0.1 | (not yet run) |
| inflation_crisis → coup | theory | exciting | 0.2, 0.1 | (not yet run) |
| protest_surge → coup | theory | exciting | 0.2, 0.1 | (not yet run) |
| sovereign_default → banking_crisis | theory | exciting | 0.2, 0.1 | (not yet run) |
| sovereign_default → fiscal_consolidation | theory | exciting | 0.2, 0.1 | (not yet run) |
| sovereign_default → inflation_crisis | data | exciting | 0.2, 0.1 | (not yet run) |
| violence_surge → conflict_onset | theory | exciting | 0.2, 0.1 | (not yet run) |
| violence_surge → war_onset | data | exciting | 0.2, 0.1 | (not yet run) |
