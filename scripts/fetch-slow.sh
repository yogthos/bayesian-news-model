#!/usr/bin/env bash
# Download the slow, multi-domain series into data/raw/slow/<source>/:
# economy, finance and markets, energy, food and agriculture, trade and supply
# chains, technology and science, climate, and the catalogues of crises and
# conflicts the onset evaluation scores against. All free, no keys.
# Idempotent: existing files are skipped, so it can be re-run to resume;
# FORCE=1 refetches.
#
#   scripts/fetch-slow.sh [fred|worldbank|vdem|imf|pwt|jst|crises|shocks|events|bis|oecd|owid|jodi|faostat|nyfed|epu|climate|openalex|ucdp|esrb|market|all]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/data/raw/slow"
# curl's own user agent: FRED stalls on browser-like ones; set UA to override
UA="${UA:-}"

get() { # url file
  local url="$1" out="$2"
  if [[ -s "$out" && -z "${FORCE:-}" ]]; then return 0; fi
  mkdir -p "$(dirname "$out")"
  if curl -sfL ${UA:+-A "$UA"} --retry 4 --retry-delay 5 --max-time 600 -o "$out.part" "$url"; then
    mv "$out.part" "$out"; echo "fetch $(basename "$out")"
  else
    rm -f "$out.part"; echo "FAILED $url" >&2
  fi
}

# --- economy, finance, markets, energy and commodity prices (FRED, monthly/daily) ---------
FRED_SERIES=(
  # energy
  DCOILBRENTEU DCOILWTICO DHHNGSP PNGASEUUSDM PNGASJPUSDM PCOALAUUSDM
  # metals, food and agricultural commodities
  PCOPPUSDM PALUMUSDM PNICKUSDM PIORECRUSDM PWHEAMTUSDM PMAIZMTUSDM PRICENPQUSDM
  PSOYBUSDM PSUGAISAUSDM PFOODINDEXM PALLFNFINDEXM PNRGINDEXM
  # US and global financial conditions
  FEDFUNDS DGS10 DGS2 DGS3MO T10Y3M T10Y2Y BAA10Y AAA10Y VIXCLS NASDAQCOM SP500
  DTWEXBGS MORTGAGE30US CSUSHPINSA TDSP NFCI STLFSI4 DRTSCILM
  # US real economy and credit
  INDPRO CPIAUCSL UNRATE PAYEMS M2SL TOTALSL BUSLOANS USREC UMCSENT HOUST
  # technology production
  IPG3344S IPB51222S
  # logistics and trade
  TSIFRGHT RAILFRTCARLOADSD11 IR IQ BOPGSTB
)
fetch_fred() {
  for s in "${FRED_SERIES[@]}"; do
    get "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$s" "$RAW/fred/$s.csv"
  done
}

# --- country panels, annual (World Bank WDI) -------------------------------------------------
WB_INDICATORS=(
  # growth, prices, labour
  NY.GDP.MKTP.KD.ZG NY.GDP.PCAP.KD NY.GDP.MKTP.CD FP.CPI.TOTL.ZG SL.UEM.TOTL.ZS SL.UEM.1524.ZS
  # credit, debt, external position
  FS.AST.PRVT.GD.ZS FM.LBL.BMNY.GD.ZS GC.DOD.TOTL.GD.ZS DT.DOD.DECT.GN.ZS BN.CAB.XOKA.GD.ZS
  BX.KLT.DINV.WD.GD.ZS BX.TRF.PWKR.DT.GD.ZS FI.RES.TOTL.MO PA.NUS.FCRF
  # trade structure
  NE.TRD.GNFS.ZS TX.VAL.FUEL.ZS.UN TM.VAL.FUEL.ZS.UN TX.VAL.FOOD.ZS.UN TM.VAL.FOOD.ZS.UN
  TX.VAL.MMTL.ZS.UN TX.VAL.TECH.MF.ZS NV.IND.MANF.ZS NV.AGR.TOTL.ZS NV.SRV.TOTL.ZS
  # energy
  EG.IMP.CONS.ZS EG.USE.PCAP.KG.OE EG.ELC.ACCS.ZS EG.FEC.RNEW.ZS NY.GDP.TOTL.RT.ZS
  # food and agriculture
  AG.PRD.FOOD.XD AG.YLD.CREL.KG SN.ITK.DEFC.ZS
  # people
  SP.POP.TOTL SP.POP.1564.TO.ZS SP.URB.TOTL.IN.ZS SP.DYN.IMRT.IN SI.POV.GINI SP.POP.GROW
  # technology and science
  IP.PAT.RESD IP.PAT.NRES GB.XPD.RSDV.GD.ZS IT.NET.USER.ZS IT.CEL.SETS.P2 IP.JRN.ARTC.SC
  # state
  MS.MIL.XPND.GD.ZS GC.TAX.TOTL.GD.ZS MS.MIL.XPND.CD
  # structure (docs/STRUCTURE.md): rents by kind, the educated young
  NY.GDP.MINR.RT.ZS NY.GDP.PETR.RT.ZS SE.TER.ENRR
  # Worldwide Governance Indicators: government effectiveness (from 1996; biennial to 2002)
  GOV_WGI_GE.EST
)
fetch_worldbank() {
  for i in "${WB_INDICATORS[@]}"; do
    get "https://api.worldbank.org/v2/country/all/indicator/$i?format=json&per_page=30000" "$RAW/worldbank/$i.json"
  done
}

# --- regimes (V-Dem country-year core, yearly, published each March) ------------------------
fetch_vdem() {
  get "https://v-dem.net/media/datasets/V-Dem-CY-Core-v16_csv.zip" "$RAW/vdem/V-Dem-CY-Core-v16_csv.zip"
}

# --- commodity terms of trade by country (IMF, Gruss & Kebhaj; via DBnomics), monthly from 1980
fetch_imf() {
  get 'https://api.db.nomics.world/v22/series/IMF/PCTOT?limit=1000&offset=0&observations=1&dimensions=%7B%22FREQ%22%3A%5B%22M%22%5D%2C%22INDICATOR%22%3A%5B%22xm_gdp%22%5D%2C%22TYPE%22%3A%5B%22R_FW_IX%22%5D%7D' "$RAW/imf/ctot.json"
}

# --- credit cycle, property, debt service, policy rates (BIS, quarterly/monthly) -----------
fetch_bis() {
  for d in WS_CREDIT_GAP WS_TC WS_DSR WS_SPP WS_CBPOL WS_LONG_CPI WS_EER WS_XRU; do
    get "https://data.bis.org/static/bulk/${d}_csv_flat.zip" "$RAW/bis/$d.zip"
  done
}

# --- leading indicators (OECD, monthly) ------------------------------------------------------
fetch_oecd() {
  get "https://sdmx.oecd.org/public/rest/data/OECD.SDD.STES,DSD_STES@DF_CLI,4.1/.M.LI...AA...H?startPeriod=1960-01&format=csvfile" "$RAW/oecd/cli.csv"
  # share prices, short and long rates, exchange rates
  get "https://sdmx.oecd.org/public/rest/data/OECD.SDD.STES,DSD_STES@DF_FINMARK,4.0/all?startPeriod=1960-01&dimensionAtObservation=AllDimensions&format=csvfile" "$RAW/oecd/finmark.csv"
}

# --- energy and emissions by country (Our World in Data, annual) -----------------------------
fetch_owid() {
  get "https://raw.githubusercontent.com/owid/energy-data/master/owid-energy-data.csv" "$RAW/owid/energy.csv"
  get "https://raw.githubusercontent.com/owid/co2-data/master/owid-co2-data.csv" "$RAW/owid/co2.csv"
}

# --- oil production, demand and stocks by country (JODI, monthly) ----------------------------
fetch_jodi() {
  get "https://www.jodidata.org/_resources/files/downloads/oil-data/world_primary_csv.zip" "$RAW/jodi/oil_primary.zip"
  get "https://www.jodidata.org/_resources/files/downloads/oil-data/world_secondary_csv.zip" "$RAW/jodi/oil_secondary.zip"
  get "https://www.jodidata.org/_resources/files/downloads/gas-data/jodi_gas_csv_beta.zip" "$RAW/jodi/gas.zip"
}

# --- food prices by country and crop production (FAOSTAT) ------------------------------------
fetch_faostat() {
  get "https://bulks-faostat.fao.org/production/ConsumerPriceIndices_E_All_Data_(Normalized).zip" "$RAW/faostat/cpi.zip"
  get "https://bulks-faostat.fao.org/production/Production_Crops_Livestock_E_All_Data_(Normalized).zip" "$RAW/faostat/crops.zip"
}

# --- global supply chain pressure (NY Fed, monthly) ------------------------------------------
# a legacy .xls the normalizer can't read without extra libraries; kept for reference
fetch_nyfed() {
  get "https://www.newyorkfed.org/medialibrary/research/interactives/gscpi/downloads/gscpi_data.xlsx" "$RAW/nyfed/gscpi.xlsx"
}

# --- economic policy uncertainty (Baker, Bloom & Davis; monthly) -----------------------------
fetch_epu() {
  get "https://www.policyuncertainty.com/media/Global_Policy_Uncertainty_Data.xlsx" "$RAW/epu/global.xlsx"
  get "https://www.policyuncertainty.com/media/All_Country_Data.xlsx" "$RAW/epu/countries.xlsx"
}

# --- climate: global temperature, ENSO, temperature and precipitation by country -------------
fetch_climate() {
  get "https://data.giss.nasa.gov/gistemp/tabledata_v4/GLB.Ts+dSST.csv" "$RAW/climate/gistemp_global.csv"
  get "https://www.cpc.ncep.noaa.gov/data/indices/oni.ascii.txt" "$RAW/climate/oni.txt"
  # CRU TS by country, monthly, from the World Bank climate portal
  local codes
  codes=$(python3 -I -c 'import json,sys
for r in json.load(open(sys.argv[1]))[1]:
    if r["region"]["value"] != "Aggregates": print(r["id"])' <(curl -sfL ${UA:+-A "$UA"} "https://api.worldbank.org/v2/country?format=json&per_page=400"))
  for v in tas pr; do
    for c in $codes; do
      get "https://cckpapi.worldbank.org/cckp/v1/cru-x0.5_timeseries_${v}_timeseries_monthly_1901-2022_mean_historical_cru_ts4.07_mean/$c?_format=json" \
          "$RAW/climate/cru_${v}/$c.json"
    done
  done
}

# --- research attention by field (OpenAlex works per year) -----------------------------------
# concept ids: artificial intelligence, machine learning, semiconductor, battery (electricity),
# photovoltaics, genome editing, vaccine, quantum computer, internet, robotics
OPENALEX_CONCEPTS=(C154945302 C119857082 C108225325 C555008776 C41291067 C2779305189 C2779615549 C58053490 C110875604 C34413123)
fetch_openalex() {
  # all works per year, to read each field as a share of research output
  get "https://api.openalex.org/works?group_by=publication_year&mailto=research@example.org" "$RAW/openalex/total.json"
  for c in "${OPENALEX_CONCEPTS[@]}"; do
    get "https://api.openalex.org/works?filter=concepts.id:$c&group_by=publication_year&mailto=research@example.org" "$RAW/openalex/$c.json"
  done
}

# --- catalogues for the onset evaluation -----------------------------------------------------
fetch_ucdp() {
  get "https://ucdp.uu.se/downloads/ucdpprio/ucdp-prio-acd-251-csv.zip" "$RAW/ucdp/acd.zip"
  # Gleditsch-Ward state codes, for UCDP's locations
  get "http://ksgleditsch.com/data/iisystem.dat" "$RAW/ucdp/iisystem.dat"
}
fetch_esrb() {
  get "https://www.esrb.europa.eu/pub/fcdb/esrb.fcdb20220120.en.xlsx" "$RAW/esrb/crises.xlsx"
}

# --- the trader's tests (docs/TRADING.md): US market returns since 1926, daily and
# monthly, 21 developed markets since 1975 (Ken French); Shiller's CAPE; the GZ
# spread and excess bond premium; long FRED series for spreads, bills, rates, CPI
fetch_market() {
  local d="$RAW/market" ff="https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp"
  get "$ff/F-F_Research_Data_Factors_CSV.zip" "$d/ff_factors.zip"
  get "$ff/F-F_Research_Data_Factors_daily_CSV.zip" "$d/ff_daily.zip"
  get "$ff/F-F_International_Countries.zip" "$d/intl_countries.zip"
  (cd "$d" && unzip -o -q ff_factors.zip && unzip -o -q ff_daily.zip && mkdir -p intl && unzip -o -q intl_countries.zip -d intl)
  get "https://raw.githubusercontent.com/datasets/s-and-p-500/main/data/data.csv" "$d/shiller.csv"
  # Shiller's CAPE to the current month (his own file stops in 2023 in the mirror)
  get "https://www.multpl.com/shiller-pe/table/by-month" "$d/cape.html"
  get "https://www.federalreserve.gov/econres/notes/feds-notes/ebp_csv.csv" "$d/ebp.csv"
  # political economy (docs/REGIMES.md): BEA annual compensation, national income,
  # corporate profits, net nonresidential fixed assets, investment and GDP; the
  # nonfarm labour share; capacity utilization
  for s in SP500 BAA AAA GS10 TB3MS CPIAUCNS UNRATE INDPRO PAYEMS \
           A033RC1A027NBEA A032RC1A027NBEA A051RC1A027NBEA K1NTOTL1ES000 PNFIA GDPA PRS85006173 TCU; do
    get "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$s" "$d/$s.csv"
  done
}

# --- Penn World Table 11 (docs/REGIMES.md): labour share, return on capital ---------------
fetch_pwt() {
  local d="$RAW/pwt"
  get "https://dataverse.nl/api/access/datafile/554030" "$d/pwt110.dta"
  uv run --no-project --with pandas python -I -c "import pandas as pd, sys; d = pd.read_stata(sys.argv[1]); d[['countrycode','year','rgdpna','rnna','labsh','csh_i','irr','pop']].to_csv(sys.argv[2], index=False)" "$d/pwt110.dta" "$d/pwt110.csv"
}

# --- Jordà-Schularick-Taylor Macrohistory R6 (docs/INVARIANTS.md): 18 countries, 1870-2020 ---
fetch_jst() {
  local d="$RAW/jst"
  get "https://www.macrohistory.net/app/download/9834512469/JSTdatasetR6.dta?t=1763503850" "$d/JSTdatasetR6.dta"
  uv run --no-project --with pandas python -I -c "import pandas as pd, sys; pd.read_stata(sys.argv[1]).to_csv(sys.argv[2], index=False)" "$d/JSTdatasetR6.dta" "$d/jst.csv"
}

# --- Reinhart-Rogoff crisis chronology (HBS BFFS, 70 countries, 1800-2016) and Chinn-Ito
# capital account openness (1970-), for the crisis process and the regime (docs/INVARIANTS.md)
fetch_crises() {
  get "https://www.hbs.edu/behavioral-finance-and-financial-stability/Documents/ChartData/MapCharts/20160923_global_crisis_data.xlsx" "$RAW/rr/global_crisis_data.xlsx"
  uv run --no-project --with pandas --with openpyxl python -I scripts/rr-crises.py "$RAW/rr/global_crisis_data.xlsx" "$RAW/rr/onsets.csv"
  get "https://web.pdx.edu/~ito/kaopen_2023.dta" "$RAW/kaopen/kaopen_2023.dta"
  uv run --no-project --with pandas python -I -c "import pandas as pd, sys; pd.read_stata(sys.argv[1]).to_csv(sys.argv[2], index=False)" "$RAW/kaopen/kaopen_2023.dta" "$RAW/kaopen/kaopen.csv"
}

# --- world shock series: NY Fed GSCPI and Caldara-Iacoviello geopolitical risk (both .xls) ---
fetch_shocks() {
  get "https://www.newyorkfed.org/medialibrary/research/interactives/gscpi/downloads/gscpi_data.xlsx" "$RAW/gscpi/gscpi_data.xlsx"
  uv run --no-project --with pandas --with xlrd python -I -c "import pandas as pd, sys; d = pd.read_excel(sys.argv[1], sheet_name='GSCPI Monthly Data', engine='xlrd')[['Date','GSCPI']].dropna(); d['Date'] = pd.to_datetime(d.Date, format='%d-%b-%Y').dt.strftime('%Y-%m-%d'); d.to_csv(sys.argv[2], index=False)" "$RAW/gscpi/gscpi_data.xlsx" "$RAW/gscpi/gscpi.csv"
  get "https://www.matteoiacoviello.com/gpr_files/data_gpr_export.xls" "$RAW/gpr/data_gpr_export.xls"
  uv run --no-project --with pandas --with xlrd python -I -c "import pandas as pd, sys; pd.read_excel(sys.argv[1])[['month','GPRH','GPRHT','GPRHA']].to_csv(sys.argv[2], index=False)" "$RAW/gpr/data_gpr_export.xls" "$RAW/gpr/gpr.csv"
  get "https://fred.stlouisfed.org/graph/fredgraph.csv?id=WTISPLC" "$RAW/market/WTISPLC.csv"
}

# --- coups, Laeven-Valencia banking crises, natural disasters (docs/SLOW-LAYER.md) -----------
# The Cline Center coup data (databank.illinois.edu, IDB-7095878) refuses scripts: download
# "Coup Data 2.2.2.csv" by hand into $RAW/coups/Coup-data-2.2.2.csv. Laeven & Valencia's
# Table A1 (IMF WP/26/94) is transcribed in scripts/curated/. EM-DAT country profiles come from HDX.
fetch_events() {
  python3 scripts/lv-crises.py scripts/curated/laeven-valencia-2026-table-a1.csv "$RAW/lv/lv2026.csv"
  get "https://data.humdata.org/dataset/74163686-a029-4e27-8fbf-c5bfcd13f953/resource/c5ce40d6-07b1-4f36-955a-d6196436ff6b/download/emdat-country-profiles_2026_10_14.xlsx" "$RAW/disasters/emdat-country-profiles.xlsx"
}

ALL=(fred worldbank vdem imf pwt jst crises shocks events bis oecd owid jodi faostat nyfed epu climate openalex ucdp esrb market)
case "${1:-all}" in
  all) for s in "${ALL[@]}"; do "fetch_$s"; done ;;
  *) "fetch_$1" ;;
esac
