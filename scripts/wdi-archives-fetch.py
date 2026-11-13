"""Fetch WDI Database Archives (World Bank source 57) for a few series: every
edition, the four data years up to the edition's year. Writes
<out>/<series>/<version>.json. Usage: python3 -I fetch_wdi_archives.py <out> <series>..."""
import json, os, sys, urllib.request

out = sys.argv[1]
series = sys.argv[2:]
vs = json.load(open(os.path.join(out, "versions.json")))["source"][0]["concept"][0]["variable"]
for s in series:
    os.makedirs(os.path.join(out, s), exist_ok=True)
    for v in [v for v in vs if int(v["id"][:4]) >= 2001]:
        vid = v["id"]
        path = os.path.join(out, s, vid + ".json")
        if os.path.exists(path) and os.path.getsize(path) > 100:
            continue
        y = int(vid[:4])
        years = ";".join("YR%d" % k for k in range(y - 3, y + 1))
        url = ("https://api.worldbank.org/v2/sources/57/country/all/series/%s/version/%s/time/%s"
               "?format=json&per_page=20000" % (s, vid, years))
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=120) as r:
                    body = r.read()
                json.loads(body)
                open(path, "wb").write(body)
                break
            except Exception as e:
                print("retry", s, vid, e, file=sys.stderr)
        print(s, vid, os.path.getsize(path) if os.path.exists(path) else "FAILED", flush=True)
