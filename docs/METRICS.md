# Code metrics gate

CI (.github/workflows/ci.yml) runs the tests and jolt-metrics on every push and pull request. jolt-metrics reports complexity, near-duplicate definitions, recurring code shapes that one function could replace, and unused code; settings in `.jolt-metrics.edn`.

The gate (`bnm.metrics-gate`) is a ratchet against `metrics-baseline.edn`, the findings the code already has. A change fails CI when it adds:
- a near-duplicate pair of definitions (jolt-metrics' compression distance at or under 0.35);
- a critical function (a complexity, length or nesting limit past its critical band);
- an unused require or an unused private function;
- growth of more than 2% in the large recurring patterns' total saving (patterns whose extraction would save at least 150 syntax nodes). A pattern's text shifts as its uses come and go, so the gate holds the total, not the texts; new texts are listed for review.

A finding is identified by its file and definition (a pattern by its text), never by line, so moving code is not new. Removing findings passes; the gate lists them so the baseline can be lowered.

Locally:

```sh
jolt-metrics . /tmp/report.edn --format edn --no-git
jolt -A:tools -M -m bnm.metrics-gate /tmp/report.edn            # the gate
jolt -A:tools -M -m bnm.metrics-gate /tmp/report.edn --update   # after removing findings, or to accept one on purpose
```

Accepting a new finding with `--update` belongs in its own commit that says why. Pinned versions: jolt v0.8.27, jolt-metrics v0.6.0 (bump both in the workflow's env).
