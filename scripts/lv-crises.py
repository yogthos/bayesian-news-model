"""Laeven & Valencia (2026) systemic banking crises, 1970-2025: Table A1 of
IMF WP/26/94, transcribed from the page images into
scripts/curated/laeven-valencia-2026-table-a1.csv (the OCR'd markdown splits names over rows and
reads some columns out of order, so it was not used). A "5/" beside a
country in the table marks a borderline episode (the paper's footnote 6:
stress that did not meet the definition), kept with borderline=1.

Writes the same rows with ISO3 codes; names the normalizer does not know
are reported, not guessed.

    python3 scripts/lv-crises.py scripts/curated/laeven-valencia-2026-table-a1.csv data/raw/slow/lv/lv2026.csv
"""
import csv
import importlib.util
import os
import sys

spec = importlib.util.spec_from_file_location("norm", os.path.join(os.path.dirname(__file__), "slow-normalize.py"))
norm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(norm)

ALIASES = {
    "turkiye": "TUR", "cote d'ivoire": "CIV", "democratic republic of congo": "COD", "republic of congo": "COG",
    "slovak republic": "SVK", "sao tome and principe": "STP", "bosnia and herzegovina": "BIH",
    "central african republic": "CAF", "dominican republic": "DOM", "north macedonia": "MKD", "russia": "RUS",
    "korea": "KOR", "vietnam": "VNM", "kyrgyz republic": "KGZ", "cape verde": "CPV", "eswatini": "SWZ",
    "netherlands": "NLD", "guinea-bissau": "GNB", "czech republic": "CZE", "moldova": "MDA",
}

if __name__ == "__main__":
    rows = list(csv.DictReader(open(sys.argv[1])))
    bad = []
    with open(sys.argv[2], "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "country", "start", "end", "borderline"])
        for r in rows:
            k = r["country"].lower()
            iso = ALIASES.get(k) or norm.NAMES.get(k)
            if not iso:
                bad.append(r["country"])
                continue
            w.writerow([iso, r["country"], r["start"], r["end"], r["borderline"]])
    print(f"{len(rows)} episodes; unmatched: {bad}", file=sys.stderr)
