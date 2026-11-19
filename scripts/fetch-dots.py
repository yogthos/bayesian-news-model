"""Fetch IMF Direction of Trade Statistics (DOTS), annual goods exports (FOB)
and imports (CIF), from DBnomics (dataset IMF/DOT), into an empty directory.

  python3 -I scripts/fetch-dots.py data/raw/slow/imf-dots

Two slices, each saved as raw DBnomics JSON pages (<slice>-<offset>.json):
  partner  every reporter's trade with RU, CN, US, B0 (EU, changing
           composition), W00 (world) and SUH (former USSR);
  reporter RU, CN and US's own reports against every partner (for mirror
           data).
Only stdlib; the downloaded pages are read as JSON, never executed.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

API = "https://api.db.nomics.world/v22/series/IMF/DOT"
UA = "generic-research-client"
SLICES = {
    "partner": {"FREQ": ["A"], "INDICATOR": ["TXG_FOB_USD", "TMG_CIF_USD"],
                "COUNTERPART_AREA": ["RU", "CN", "US", "B0", "W00", "SUH"]},
    "reporter": {"FREQ": ["A"], "INDICATOR": ["TXG_FOB_USD", "TMG_CIF_USD"],
                 "REF_AREA": ["RU", "CN", "US"]},
}


def get(url):
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:  # retry transient failures
            print("retry", attempt, e, file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise SystemExit("failed: " + url)


def main(out):
    for name, dims in SLICES.items():
        offset, total = 0, None
        while total is None or offset < total:
            q = urllib.parse.urlencode({"observations": 1, "limit": 1000, "offset": offset,
                                        "dimensions": json.dumps(dims, separators=(",", ":"))})
            body = get(API + "?" + q)
            d = json.loads(body)
            total = d["series"]["num_found"]
            n = len(d["series"]["docs"])
            with open(os.path.join(out, "%s-%05d.json" % (name, offset)), "wb") as f:
                f.write(body)
            print(name, offset, n, total, file=sys.stderr)
            if n == 0:
                break
            offset += n
            time.sleep(1)


if __name__ == "__main__":
    main(sys.argv[1])
