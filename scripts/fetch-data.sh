#!/usr/bin/env bash
# Download historical event data into data/raw/<source>/.
# Idempotent: existing files are skipped, so it can be re-run to resume.
#
#   scripts/fetch-data.sh [gdelt|polecat|icews|acled|all]
#
# ACLED needs a registered myACLED account: export ACLED_EMAIL and ACLED_PASSWORD.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/data/raw"
DV="https://dataverse.harvard.edu/api"
JOBS="${JOBS:-4}"

dataverse_files() { # doi -> "id<TAB>filename" lines
  curl -sf "$DV/datasets/:persistentId/?persistentId=$1" |
    python3 -I -c 'import json,sys
for f in json.load(sys.stdin)["data"]["latestVersion"]["files"]:
    d = f["dataFile"]; print(d["id"], d["filename"], sep="\t")'
}

fetch_dataverse() { # doi dir filename-regex
  local doi="$1" dir="$2" pattern="$3"
  mkdir -p "$dir"
  dataverse_files "$doi" | grep -iE "$pattern" | while IFS=$'\t' read -r id name; do
    # ?format=original undoes Dataverse's ingest of .tab files
    if [[ ! -s "$dir/$name" ]]; then
      echo "fetch $name"
      curl -sfL --retry 5 -o "$dir/$name.part" "$DV/access/datafile/$id?format=original" &&
        mv "$dir/$name.part" "$dir/$name"
    fi
  done
}

fetch_gdelt() {
  local dir="$RAW/gdelt"
  mkdir -p "$dir"
  curl -sf https://data.gdeltproject.org/events/index.html |
    grep -oiE 'HREF="[0-9]{4,8}(\.export\.CSV)?\.zip"' | sed -E 's/HREF="(.*)"/\1/I' |
    sort -u |
    xargs -P "$JOBS" -I{} sh -c '[ -s "$1/$2" ] || { curl -sfL --retry 5 -o "$1/$2.part" "https://data.gdeltproject.org/events/$2" && mv "$1/$2.part" "$1/$2" && echo "fetch $2"; }' _ "$dir" {}
}

fetch_polecat() { fetch_dataverse doi:10.7910/DVN/AJGVIT "$RAW/polecat" '.'; }
fetch_icews()   { fetch_dataverse doi:10.7910/DVN/28075 "$RAW/icews" 'events'; }

fetch_acled() {
  : "${ACLED_EMAIL:?set ACLED_EMAIL}" "${ACLED_PASSWORD:?set ACLED_PASSWORD}"
  local dir="$RAW/acled" token page year
  mkdir -p "$dir"
  token=$(curl -sf -X POST https://acleddata.com/oauth/token \
            --data-urlencode "username=$ACLED_EMAIL" --data-urlencode "password=$ACLED_PASSWORD" \
            -d grant_type=password -d client_id=acled | python3 -I -c 'import json,sys;print(json.load(sys.stdin)["access_token"])')
  for year in $(seq 1997 "$(date +%Y)"); do
    page=1
    while :; do
      local out="$dir/acled-$year-$(printf %04d "$page").csv"
      if [[ ! -s "$out" ]]; then
        curl -sf --retry 5 -H "Authorization: Bearer $token" -o "$out.part" \
          "https://acleddata.com/api/acled/read?_format=csv&year=$year&limit=5000&page=$page" && mv "$out.part" "$out"
      fi
      # header-only page means the year is exhausted
      [[ $(wc -l < "$out") -le 1 ]] && { rm -f "$out"; break; }
      echo "acled $year page $page"
      page=$((page + 1))
    done
  done
}

case "${1:-all}" in
  gdelt) fetch_gdelt ;; polecat) fetch_polecat ;; icews) fetch_icews ;; acled) fetch_acled ;;
  all) fetch_polecat; fetch_icews; fetch_gdelt ;;
  *) echo "usage: $0 [gdelt|polecat|icews|acled|all]" >&2; exit 2 ;;
esac
