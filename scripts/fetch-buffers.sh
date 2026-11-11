#!/usr/bin/env bash
# Downloads for the buffer model's wider field of data (docs/PLAN.md 1.8,
# bead bnm-56c.5): reserves and external financing, energy stocks, bank
# funding, US investment pipelines. Each source goes to its own directory
# under data/raw/buffers/ with a SOURCE.txt (written by hand after the
# download; this script records the files' sizes and sha256 in FILES.txt).
#
#   scripts/fetch-buffers.sh [group ...]     groups: imf wdi eia eurostat bis bea census sec
#
# Everything here is open: no account, no key, no form. A generic
# User-Agent is used; no personal details are sent.
set -euo pipefail
cd "$(dirname "$0")/.."
RAW=data/raw/buffers
UA="bnm-data-fetch/1.0"
get() { # url dest
  mkdir -p "$(dirname "$2")"
  if [ -s "$2" ]; then echo "have $2"; return 0; fi
  echo "get $1 -> $2"
  curl -fsSL --retry 3 --retry-delay 5 -A "$UA" -o "$2.part" "$1"
  [ -s "$2.part" ] || { echo "empty: $1"; rm -f "$2.part"; return 1; }  # a 204 is not a file
  check "$2.part" || { echo "not data: $1"; rm -f "$2.part"; return 1; }
  mv "$2.part" "$2"
}
check() { # an error page served with 200 (Eurostat does this when busy) is not data
  case "$1" in
    *.gz.part) gzip -t "$1" 2>/dev/null ;;
    *.zip.part|*.xlsx.part) unzip -tq "$1" >/dev/null 2>&1 ;;
    *) ! head -c 200 "$1" | grep -qi '<!doctype html\|<html' ;;
  esac
}
imf() { # agency flow version dir
  local d="$RAW/$4"
  get "https://api.imf.org/external/sdmx/2.1/dataflow/$1/$2/$3?references=descendants" "$d/${2}_structure.xml"
  if [ ! -s "$d/$2.csv.gz" ]; then
    echo "get IMF $2 -> $d/$2.csv.gz"
    curl -fsSL --retry 3 -A "$UA" -H 'Accept: application/vnd.sdmx.data+csv;version=1.0.0' \
      "https://api.imf.org/external/sdmx/2.1/data/$1,$2,$3/all?detail=dataonly" | gzip -c > "$d/$2.csv.gz.part"
    mv "$d/$2.csv.gz.part" "$d/$2.csv.gz"
  fi
}
files() { # dir: sizes and hashes
  (cd "$RAW/$1" && find . -type f ! -name SOURCE.txt ! -name FILES.txt ! -name '*.part' | sort | while read -r f; do
     printf '%s  %12s  %s\n' "$(shasum -a 256 "$f" | cut -d' ' -f1)" "$(wc -c < "$f" | tr -d ' ')" "${f#./}"; done) > "$RAW/$1/FILES.txt"
}

g_imf() {
  imf IMF.STA IL 13.0.1 imf-il   # international liquidity: reserves, reserves template (monthly)
  imf IMF.STA FA 8.0.0 imf-fa    # Fund accounts: credit outstanding to the Fund by member (monthly)
  imf IMF.STA IRFCL 12.0.0 imf-irfcl  # reserves data template: usable, pledged, predetermined drains (monthly)
  files imf-il; files imf-fa; files imf-irfcl
}
g_wdi() { # WDI bulk csv per indicator (annual)
  for i in FI.RES.TOTL.CD FI.RES.XGLD.CD FI.RES.TOTL.MO DT.DOD.DSTC.CD DT.DOD.DSTC.IR.ZS DT.DOD.DECT.CD \
           DT.TDS.DECT.CD DT.TDS.DECT.EX.ZS BM.GSR.GNFS.CD NY.GNP.MKTP.CD; do
    get "https://api.worldbank.org/v2/en/indicator/$i?downloadformat=csv" "$RAW/wdi-external/$i.zip"
  done
  files wdi-external
}
g_eia() {
  get https://www.eia.gov/opendata/bulk/manifest.txt "$RAW/eia-bulk/manifest.txt"
  get https://www.eia.gov/opendata/bulk/PET.zip "$RAW/eia-bulk/PET.zip"
  get https://www.eia.gov/opendata/bulk/NG.zip "$RAW/eia-bulk/NG.zip"
  get https://ir.eia.gov/ngs/wngsr.csv "$RAW/eia-bulk/wngsr.csv"
  files eia-bulk
  get https://www.eia.gov/electricity/data/eia860m/xls/september_generator2026.xlsx "$RAW/eia-860m/september_generator2026.xlsx"
  files eia-860m
}
g_eurostat() {
  for t in nrg_stk_gasm nrg_stk_oilm nrg_cb_gasm; do
    get "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/$t?format=TSV&compressed=true" "$RAW/eurostat-energy-stocks/$t.tsv.gz"
  done
  files eurostat-energy-stocks
}
g_bis() {
  get https://data.bis.org/static/bulk/WS_GLI_csv_flat.zip "$RAW/bis-funding/WS_GLI.zip"
  get https://data.bis.org/static/bulk/WS_CBS_PUB_csv_flat.zip "$RAW/bis-funding/WS_CBS_PUB.zip"
  files bis-funding
}
g_bea() {
  for f in NipaDataA.txt NipaDataQ.txt SeriesRegister.txt TablesRegister.txt; do
    get "https://apps.bea.gov/national/Release/TXT/$f" "$RAW/bea-nipa/$f"
  done
  files bea-nipa
  for f in FixedAssets.txt SeriesRegister.txt TablesRegister.txt; do
    get "https://apps.bea.gov/national/FixedAssets/Release/TXT/$f" "$RAW/bea-fixed-assets/$f"
  done
  files bea-fixed-assets
}
g_census() {
  for f in privsatime totsatime pubsatime; do
    get "https://www.census.gov/construction/c30/xlsx/$f.xlsx" "$RAW/census-c30/$f.xlsx"
  done
  files census-c30
}
g_sec() { # XBRL frames: one value per filer per concept per calendar year (2009 on)
  local d="$RAW/sec-frames"
  for c in PaymentsToAcquirePropertyPlantAndEquipment DepreciationDepletionAndAmortization DepreciationAndAmortization \
           NetCashProvidedByUsedInOperatingActivities Revenues; do
    for y in $(seq 2009 2025); do
      get "https://data.sec.gov/api/xbrl/frames/us-gaap/$c/USD/CY$y.json" "$d/$c/CY$y.json" || echo "miss $c CY$y"
      sleep 0.3
    done
  done
  for c in LongTermDebt LongTermDebtNoncurrent Assets; do
    for y in $(seq 2009 2025); do
      get "https://data.sec.gov/api/xbrl/frames/us-gaap/$c/USD/CY${y}Q4I.json" "$d/$c/CY${y}Q4I.json" || echo "miss $c CY${y}Q4I"
      sleep 0.3
    done
  done
  # industry (SIC) per filer: sub.txt of the financial statement data sets, one quarter in three years
  for q in 2010q1 2013q1 2016q1 2019q1 2022q1 2025q1; do
    if [ ! -s "$d/sub/$q-sub.txt" ]; then
      get "https://www.sec.gov/files/dera/data/financial-statement-data-sets/$q.zip" "$d/sub/$q.zip"
      shasum -a 256 "$d/sub/$q.zip" >> "$d/sub/zips.sha256"; wc -c "$d/sub/$q.zip" >> "$d/sub/zips.sha256"
      unzip -p "$d/sub/$q.zip" sub.txt > "$d/sub/$q-sub.txt" && rm "$d/sub/$q.zip"
    fi
    sleep 1
  done
  files sec-frames
}

groups=("$@"); [ ${#groups[@]} -eq 0 ] && groups=(imf wdi eia eurostat bis bea census sec)
for g in "${groups[@]}"; do "g_$g"; done
echo done
