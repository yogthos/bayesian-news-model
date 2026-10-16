#!/usr/bin/env python3
"""Normalize the slow sources in data/raw/slow/ (see scripts/fetch-slow.sh) into

  data/slow/series.tsv     unit variable freq period_end available value
  data/slow/variables.tsv  variable domain transform freq source description
  data/slow/events.tsv     kind unit date detail     (catalogues for the onset evaluation)

unit is an ISO3 country code or "world". period_end is the last day of the
period a value covers; available is when it would have been known (period end
plus a publication lag), the date before which training must not see it.
Standard library only.

  python3 -I scripts/slow-normalize.py
"""
import csv, io, json, math, os, re, sys, zipfile, calendar
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data", "raw", "slow")
OUT = os.path.join(ROOT, "data", "slow")
csv.field_size_limit(1 << 30)

# --- countries ------------------------------------------------------------------------------

ISO2, M49, NAMES = {}, {}, {}
for line in open(os.path.join(ROOT, "data", "ref", "countryInfo.txt"), encoding="utf-8"):
    if line.startswith("#"):
        continue
    f = line.rstrip("\n").split("\t")
    ISO2[f[0]] = f[1]
    M49[int(f[2])] = f[1]
    NAMES[f[4].lower()] = f[1]
edn = open(os.path.join(ROOT, "resources", "bnm", "countries.edn"), encoding="utf-8").read()
for iso3, names in re.findall(r':iso3 "([A-Z]{3})".*?:names \[([^\]]*)\]', edn, re.S):
    for n in re.findall(r'"([^"]*)"', names):
        NAMES[n.lower()] = iso3
ISO3 = set(ISO2.values())
NAMES.update({  # names the sources use that GeoNames and M49 don't
    "russia (soviet union)": "RUS", "soviet union": "RUS", "russia": "RUS", "yemen (north yemen)": "YEM",
    "yemen arab republic": "YEM", "serbia (yugoslavia)": "SRB", "yugoslavia": "SRB", "myanmar (burma)": "MMR",
    "dr congo (zaire)": "COD", "congo, democratic republic of (zaire)": "COD", "cambodia (kampuchea)": "KHM",
    "zimbabwe (rhodesia)": "ZWE", "madagascar (malagasy)": "MDG", "ivory coast": "CIV", "cote d'ivoire": "CIV",
    "burkina faso (upper volta)": "BFA", "iran (persia)": "IRN", "turkey (ottoman empire)": "TUR",
    "korea, people's republic of": "PRK", "korea, republic of": "KOR", "south korea": "KOR", "north korea": "PRK",
    "vietnam (north vietnam)": "VNM", "vietnam, democratic republic of": "VNM", "republic of vietnam": "VNM",
    "german federal republic": "DEU", "germany": "DEU", "german democratic republic": "DEU",
    "united states of america": "USA", "united kingdom": "GBR", "bosnia-herzegovina": "BIH",
    "macedonia": "MKD", "macedonia (former yugoslav republic of)": "MKD", "east timor": "TLS", "timor-leste": "TLS",
    "swaziland": "SWZ", "eswatini": "SWZ", "tanzania/tanganyika": "TZA", "tanzania": "TZA", "gambia": "GMB",
    "cape verde": "CPV", "congo": "COG", "south sudan": "SSD", "kosovo": "XKX", "taiwan": "TWN", "hong kong": "HKG",
    "laos": "LAO", "syria": "SYR", "libya": "LBY", "moldova": "MDA", "bolivia": "BOL", "venezuela": "VEN",
    "czech republic": "CZE", "slovakia": "SVK", "belarus (byelorussia)": "BLR", "kyrgyz republic": "KGZ",
    "sri lanka (ceylon)": "LKA", "surinam": "SUR", "korea": "KOR", "mainland china": "CHN", "brunei": "BRN",
    "bahamas": "BHS", "the gambia": "GMB", "papua new guinea": "PNG", "united arab emirates": "ARE",
    "rumania": "ROU", "italy/sardinia": "ITA", "zanzibar": "TZA", "solomon islands": "SLB",
})


def iso3_of_name(name):
    if name is None:
        return None
    return NAMES.get(name.strip().lower())


# --- dates ------------------------------------------------------------------------------------

def month_end(y, m):
    return "%04d-%02d-%02d" % (y, m, calendar.monthrange(y, m)[1])


def add_months(date, k):
    y, m = int(date[:4]), int(date[5:7])
    m0 = y * 12 + m - 1 + k
    return month_end(m0 // 12, m0 % 12 + 1)


def period_end(freq, label):
    """2001 -> 2001-12-31; 2001-Q2 -> 2001-06-30; 2001-05 -> 2001-05-31."""
    label = str(label).strip()
    if freq == "a":
        return month_end(int(label[:4]), 12)
    if freq == "q":
        y, q = re.match(r"(\d{4})-?Q(\d)", label).groups()
        return month_end(int(y), int(q) * 3)
    return month_end(int(label[:4]), int(label[5:7]))


# --- output --------------------------------------------------------------------------------------

VARS = {}
ROWS = []
EVENTS = []


def var(name, transform, freq, source, desc):
    VARS[name] = (name.split("/")[0], transform, freq, source, desc)


def emit(unit, name, freq, pend, lag, value):
    if unit is None or value is None:
        return
    try:
        v = float(value)
    except (TypeError, ValueError):
        return
    if math.isnan(v) or math.isinf(v):
        return
    ROWS.append((unit, name, freq, pend, add_months(pend, lag), v))


def num(s):
    try:
        v = float(s)
        return None if math.isnan(v) else v
    except (TypeError, ValueError):
        return None


# --- sources --------------------------------------------------------------------------------------

FRED = {
    # id: (unit, variable, transform, description)
    "DCOILBRENTEU": ("world", "energy/oil_brent", "log", "Brent crude, USD/bbl"),
    "DCOILWTICO": ("world", "energy/oil_wti", "log", "WTI crude, USD/bbl"),
    "DHHNGSP": ("world", "energy/gas_us", "log", "Henry Hub natural gas"),
    "PNGASEUUSDM": ("world", "energy/gas_europe", "log", "European natural gas"),
    "PNGASJPUSDM": ("world", "energy/gas_asia", "log", "Japan LNG"),
    "PCOALAUUSDM": ("world", "energy/coal", "log", "Australian coal"),
    "PNRGINDEXM": ("world", "energy/price_index", "log", "IMF energy price index"),
    "PCOPPUSDM": ("world", "commodities/copper", "log", "copper"),
    "PALUMUSDM": ("world", "commodities/aluminium", "log", "aluminium"),
    "PNICKUSDM": ("world", "commodities/nickel", "log", "nickel"),
    "PIORECRUSDM": ("world", "commodities/iron_ore", "log", "iron ore"),
    "PALLFNFINDEXM": ("world", "commodities/all_index", "log", "IMF all commodities index"),
    "PWHEAMTUSDM": ("world", "food/wheat", "log", "wheat"),
    "PMAIZMTUSDM": ("world", "food/maize", "log", "maize"),
    "PRICENPQUSDM": ("world", "food/rice", "log", "rice"),
    "PSOYBUSDM": ("world", "food/soybeans", "log", "soybeans"),
    "PSUGAISAUSDM": ("world", "food/sugar", "log", "sugar"),
    "PFOODINDEXM": ("world", "food/price_index", "log", "IMF food price index"),
    "FEDFUNDS": ("USA", "finance/policy_rate", "id", "policy rate"),
    "DGS10": ("USA", "finance/yield_10y", "id", "10-year Treasury"),
    "DGS2": ("USA", "finance/yield_2y", "id", "2-year Treasury"),
    "DGS3MO": ("USA", "finance/yield_3m", "id", "3-month Treasury"),
    "T10Y3M": ("USA", "finance/curve_10y3m", "id", "10y minus 3m"),
    "T10Y2Y": ("USA", "finance/curve_10y2y", "id", "10y minus 2y"),
    "BAA10Y": ("USA", "finance/spread_baa", "id", "Baa corporate minus 10y"),
    "AAA10Y": ("USA", "finance/spread_aaa", "id", "Aaa corporate minus 10y"),
    "VIXCLS": ("world", "finance/vix", "log", "VIX equity volatility"),
    "NASDAQCOM": ("USA", "markets/nasdaq", "log", "NASDAQ composite"),
    "SP500": ("USA", "markets/sp500", "log", "S&P 500 (FRED keeps ten years)"),
    "DTWEXBGS": ("world", "finance/usd_broad", "log", "broad trade-weighted dollar"),
    "MORTGAGE30US": ("USA", "finance/mortgage_rate", "id", "30-year mortgage rate"),
    "CSUSHPINSA": ("USA", "finance/house_prices", "log", "Case-Shiller home prices"),
    "TDSP": ("USA", "finance/dsr_households", "id", "household debt service, % income"),
    "NFCI": ("USA", "finance/conditions", "id", "Chicago Fed financial conditions"),
    "STLFSI4": ("USA", "finance/stress", "id", "St. Louis Fed financial stress"),
    "DRTSCILM": ("USA", "finance/lending_standards", "id", "net banks tightening C&I standards"),
    "INDPRO": ("USA", "economy/industrial_production", "log", "industrial production"),
    "CPIAUCSL": ("USA", "economy/cpi", "log", "consumer prices"),
    "UNRATE": ("USA", "economy/unemployment", "id", "unemployment rate"),
    "PAYEMS": ("USA", "economy/payrolls", "log", "nonfarm payrolls"),
    "M2SL": ("USA", "finance/m2", "log", "M2 money"),
    "TOTALSL": ("USA", "finance/consumer_credit", "log", "consumer credit"),
    "BUSLOANS": ("USA", "finance/business_loans", "log", "C&I loans"),
    "USREC": ("USA", "economy/recession", "id", "NBER recession indicator"),
    "UMCSENT": ("USA", "economy/consumer_sentiment", "id", "Michigan consumer sentiment"),
    "HOUST": ("USA", "economy/housing_starts", "log", "housing starts"),
    "IPG3344S": ("USA", "technology/semiconductor_production", "log", "semiconductor and electronic component production"),
    "TSIFRGHT": ("USA", "trade/freight_index", "log", "freight transportation services index"),
    "RAILFRTCARLOADSD11": ("USA", "trade/rail_carloads", "log", "rail freight carloads"),
    "IR": ("USA", "trade/import_prices", "log", "import price index"),
    "IQ": ("USA", "trade/export_prices", "log", "export price index"),
    "BOPGSTB": ("USA", "trade/goods_services_balance", "id", "trade balance"),
}


def fred():
    for sid, (unit, name, tr, desc) in FRED.items():
        path = os.path.join(RAW, "fred", sid + ".csv")
        if not os.path.exists(path):
            continue
        by_month = defaultdict(list)
        for r in csv.reader(open(path)):
            if len(r) < 2 or not re.match(r"\d{4}-\d{2}-\d{2}", r[0]):
                continue
            v = num(r[1])
            if v is not None:
                by_month[r[0][:7]].append(v)
        freq = "m"
        for ym, vs in by_month.items():
            emit(unit, name, freq, period_end("m", ym), 0 if len(vs) > 2 else 1, sum(vs) / len(vs))
        var(name, tr, freq, "FRED " + sid, desc)


WB = {
    "NY.GDP.MKTP.KD.ZG": ("economy/gdp_growth", "id", "GDP growth, %"),
    "NY.GDP.PCAP.KD": ("economy/gdp_per_capita", "log", "GDP per capita, constant USD"),
    "NY.GDP.MKTP.CD": ("economy/gdp_usd", "log", "GDP, current USD"),
    "FP.CPI.TOTL.ZG": ("economy/inflation", "id", "consumer price inflation, %"),
    "SL.UEM.TOTL.ZS": ("economy/unemployment", "id", "unemployment, %"),
    "SL.UEM.1524.ZS": ("people/youth_unemployment", "id", "youth unemployment, %"),
    "FS.AST.PRVT.GD.ZS": ("finance/private_credit_gdp", "id", "domestic credit to private sector, % GDP"),
    "FM.LBL.BMNY.GD.ZS": ("finance/broad_money_gdp", "id", "broad money, % GDP"),
    "GC.DOD.TOTL.GD.ZS": ("debt/government_gdp", "id", "central government debt, % GDP"),
    "DT.DOD.DECT.GN.ZS": ("debt/external_gni", "id", "external debt, % GNI"),
    "BN.CAB.XOKA.GD.ZS": ("debt/current_account_gdp", "id", "current account, % GDP"),
    "BX.KLT.DINV.WD.GD.ZS": ("debt/fdi_gdp", "id", "FDI inflows, % GDP"),
    "BX.TRF.PWKR.DT.GD.ZS": ("debt/remittances_gdp", "id", "remittances, % GDP"),
    "FI.RES.TOTL.MO": ("debt/reserves_months", "id", "reserves, months of imports"),
    "PA.NUS.FCRF": ("finance/fx_per_usd", "log", "exchange rate, LCU per USD"),
    "NE.TRD.GNFS.ZS": ("trade/openness", "id", "trade, % GDP"),
    "TX.VAL.FUEL.ZS.UN": ("energy/fuel_exports_share", "id", "fuel, % of merchandise exports"),
    "TM.VAL.FUEL.ZS.UN": ("energy/fuel_imports_share", "id", "fuel, % of merchandise imports"),
    "TX.VAL.FOOD.ZS.UN": ("food/exports_share", "id", "food, % of merchandise exports"),
    "TM.VAL.FOOD.ZS.UN": ("food/imports_share", "id", "food, % of merchandise imports"),
    "TX.VAL.MMTL.ZS.UN": ("trade/metal_exports_share", "id", "ores and metals, % of merchandise exports"),
    "TX.VAL.TECH.MF.ZS": ("technology/hightech_exports_share", "id", "high-tech, % of manufactured exports"),
    "NV.IND.MANF.ZS": ("economy/manufacturing_share", "id", "manufacturing, % GDP"),
    "NV.AGR.TOTL.ZS": ("economy/agriculture_share", "id", "agriculture, % GDP"),
    "NV.SRV.TOTL.ZS": ("economy/services_share", "id", "services, % GDP"),
    "EG.IMP.CONS.ZS": ("energy/import_dependence", "id", "net energy imports, % of use"),
    "EG.USE.PCAP.KG.OE": ("energy/use_per_capita", "log", "energy use per capita"),
    "EG.ELC.ACCS.ZS": ("energy/electricity_access", "id", "access to electricity, %"),
    "EG.FEC.RNEW.ZS": ("energy/renewables_share", "id", "renewables, % of final energy"),
    "NY.GDP.TOTL.RT.ZS": ("energy/resource_rents_gdp", "id", "natural resource rents, % GDP"),
    "AG.PRD.FOOD.XD": ("food/production_index", "log", "food production index"),
    "AG.YLD.CREL.KG": ("food/cereal_yield", "log", "cereal yield, kg/ha"),
    "SN.ITK.DEFC.ZS": ("food/undernourishment", "id", "undernourishment, %"),
    "SP.POP.TOTL": ("people/population", "log", "population"),
    "SP.POP.1564.TO.ZS": ("people/working_age_share", "id", "population 15-64, %"),
    "SP.URB.TOTL.IN.ZS": ("people/urban_share", "id", "urban population, %"),
    "SP.DYN.IMRT.IN": ("people/infant_mortality", "log", "infant mortality per 1000"),
    "SI.POV.GINI": ("people/gini", "id", "Gini index"),
    "SP.POP.GROW": ("people/population_growth", "id", "population growth, %"),
    "IP.PAT.RESD": ("technology/patents_resident", "log1p", "patent applications, residents"),
    "IP.PAT.NRES": ("technology/patents_nonresident", "log1p", "patent applications, nonresidents"),
    "GB.XPD.RSDV.GD.ZS": ("technology/rd_gdp", "id", "R&D, % GDP"),
    "IT.NET.USER.ZS": ("technology/internet_users", "id", "internet users, %"),
    "IT.CEL.SETS.P2": ("technology/mobile_subscriptions", "id", "mobile subscriptions per 100"),
    "IP.JRN.ARTC.SC": ("technology/articles", "log1p", "scientific and technical articles"),
    "MS.MIL.XPND.GD.ZS": ("state/military_gdp", "id", "military expenditure, % GDP"),
    "GC.TAX.TOTL.GD.ZS": ("state/tax_gdp", "id", "tax revenue, % GDP"),
}


def worldbank():
    for code, (name, tr, desc) in WB.items():
        path = os.path.join(RAW, "worldbank", code + ".json")
        if not os.path.exists(path):
            continue
        d = json.load(open(path))
        if not isinstance(d, list) or len(d) < 2 or not d[1]:
            continue
        for r in d[1]:
            iso = r.get("countryiso3code")
            if iso in ISO3 and r.get("value") is not None:
                emit(iso, name, "a", period_end("a", r["date"]), 9, r["value"])
        var(name, tr, "a", "World Bank WDI " + code, desc)


def code(s):
    return (s or "").split(":")[0].strip()


def bis_rows(name):
    path = os.path.join(RAW, "bis", name + ".zip")
    if not os.path.exists(path):
        return
    z = zipfile.ZipFile(path)
    with z.open(z.namelist()[0]) as f:
        rd = csv.reader(io.TextIOWrapper(f, encoding="utf-8"))
        header = [h.split(":")[0] for h in next(rd)]
        for r in rd:
            yield dict(zip(header, r))


def bis_freq(f):
    return {"Q": "q", "M": "m", "A": "a"}.get(code(f))


def bis():
    def area(r, k):
        return ISO2.get(code(r.get(k)))
    for r in bis_rows("WS_CREDIT_GAP"):
        if code(r["TC_BORROWERS"]) != "P" or code(r["TC_LENDERS"]) != "A":
            continue
        t = code(r["CG_DTYPE"])
        name = {"C": "finance/credit_gap", "A": "finance/credit_gdp"}.get(t)
        if name:
            emit(area(r, "BORROWERS_CTY"), name, "q", period_end("q", r["TIME_PERIOD"]), 4, r["OBS_VALUE"])
    var("finance/credit_gap", "id", "q", "BIS credit-to-GDP gaps", "credit-to-GDP gap (private non-financial), pp")
    var("finance/credit_gdp", "id", "q", "BIS credit-to-GDP gaps", "private non-financial credit, % GDP")
    for r in bis_rows("WS_DSR"):
        name = {"P": "finance/dsr_private", "H": "finance/dsr_households", "N": "finance/dsr_corporate"}.get(code(r["DSR_BORROWERS"]))
        if name:
            emit(area(r, "BORROWERS_CTY"), name, "q", period_end("q", r["TIME_PERIOD"]), 4, r["OBS_VALUE"])
    var("finance/dsr_private", "id", "q", "BIS debt service ratios", "debt service ratio, private non-financial")
    var("finance/dsr_households", "id", "q", "BIS debt service ratios", "debt service ratio, households")
    var("finance/dsr_corporate", "id", "q", "BIS debt service ratios", "debt service ratio, non-financial corporations")
    for r in bis_rows("WS_SPP"):
        if code(r["FREQ"]) == "Q" and code(r["VALUE"]) == "R" and code(r["UNIT_MEASURE"]) == "628":
            emit(area(r, "REF_AREA"), "finance/real_house_prices", "q", period_end("q", r["TIME_PERIOD"]), 4, r["OBS_VALUE"])
    var("finance/real_house_prices", "log", "q", "BIS property prices", "real residential property prices")
    for r in bis_rows("WS_TC"):
        if (code(r["FREQ"]) != "Q" or code(r["TC_LENDERS"]) != "A" or code(r["UNIT_TYPE"]) != "770"
                or code(r["TC_ADJUST"]) != "A"):
            continue
        name = {"H": "finance/household_credit_gdp", "N": "finance/corporate_credit_gdp",
                "G": "debt/government_credit_gdp"}.get(code(r["TC_BORROWERS"]))
        if name:
            emit(area(r, "BORROWERS_CTY"), name, "q", period_end("q", r["TIME_PERIOD"]), 4, r["OBS_VALUE"])
    var("finance/household_credit_gdp", "id", "q", "BIS total credit", "household credit, % GDP")
    var("finance/corporate_credit_gdp", "id", "q", "BIS total credit", "non-financial corporate credit, % GDP")
    var("debt/government_credit_gdp", "id", "q", "BIS total credit", "government debt (credit), % GDP")
    for r in bis_rows("WS_CBPOL"):
        if code(r["FREQ"]) == "M":
            emit(area(r, "REF_AREA"), "finance/policy_rate", "m", period_end("m", r["TIME_PERIOD"]), 0, r["OBS_VALUE"])
    var("finance/policy_rate", "id", "m", "BIS policy rates; FRED for the US", "central bank policy rate")
    for r in bis_rows("WS_LONG_CPI"):
        f = bis_freq(r["FREQ"])
        if f == "m" and code(r["UNIT_MEASURE"]) == "771":
            emit(area(r, "REF_AREA"), "economy/inflation_monthly", "m", period_end("m", r["TIME_PERIOD"]), 1, r["OBS_VALUE"])
    var("economy/inflation_monthly", "id", "m", "BIS consumer prices", "consumer price inflation, year on year, %")
    for r in bis_rows("WS_EER"):
        if code(r.get("FREQ")) == "M" and code(r.get("EER_TYPE")) == "R" and code(r.get("EER_BASKET")) == "B":
            emit(area(r, "REF_AREA"), "finance/reer", "m", period_end("m", r["TIME_PERIOD"]), 1, r["OBS_VALUE"])
    var("finance/reer", "log", "m", "BIS effective exchange rates", "real effective exchange rate, broad")


def oecd():
    path = os.path.join(RAW, "oecd", "cli.csv")
    if os.path.exists(path):
        for r in csv.DictReader(open(path)):
            if r["REF_AREA"] in ISO3:
                emit(r["REF_AREA"], "economy/leading_indicator", "m", period_end("m", r["TIME_PERIOD"]), 1, r["OBS_VALUE"])
        var("economy/leading_indicator", "id", "m", "OECD CLI", "composite leading indicator, amplitude adjusted")
    path = os.path.join(RAW, "oecd", "finmark.csv")
    if os.path.exists(path):
        names = {"SHARE": ("markets/share_prices", "log", "share prices index"),
                 "IRLT": ("finance/yield_long", "id", "long-term government bond yield"),
                 "IR3TIB": ("finance/rate_3m", "id", "3-month interbank rate")}
        for r in csv.DictReader(open(path)):
            n = names.get(r["MEASURE"])
            if n and r["FREQ"] == "M" and r["REF_AREA"] in ISO3:
                emit(r["REF_AREA"], n[0], "m", period_end("m", r["TIME_PERIOD"]), 1, r["OBS_VALUE"])
        for n in names.values():
            var(n[0], n[1], "m", "OECD financial indicators", n[2])


OWID_ENERGY = {
    "primary_energy_consumption": ("energy/primary_consumption", "log1p", "primary energy consumption, TWh"),
    "oil_production": ("energy/oil_production", "log1p", "oil production, TWh"),
    "gas_production": ("energy/gas_production", "log1p", "gas production, TWh"),
    "coal_production": ("energy/coal_production", "log1p", "coal production, TWh"),
    "oil_consumption": ("energy/oil_consumption", "log1p", "oil consumption, TWh"),
    "gas_consumption": ("energy/gas_consumption", "log1p", "gas consumption, TWh"),
    "fossil_share_energy": ("energy/fossil_share", "id", "fossil fuels, % of primary energy"),
    "renewables_share_elec": ("energy/renewables_share_electricity", "id", "renewables, % of electricity"),
    "electricity_demand": ("energy/electricity_demand", "log1p", "electricity demand, TWh"),
}
OWID_CO2 = {
    "co2": ("climate/co2", "log1p", "CO2 emissions, Mt"),
    "co2_per_capita": ("climate/co2_per_capita", "id", "CO2 per capita, t"),
    "ghg_per_capita": ("climate/ghg_per_capita", "id", "greenhouse gases per capita, t CO2e"),
}


def owid():
    for fname, table in (("energy.csv", OWID_ENERGY), ("co2.csv", OWID_CO2)):
        path = os.path.join(RAW, "owid", fname)
        if not os.path.exists(path):
            continue
        for r in csv.DictReader(open(path, encoding="utf-8")):
            iso = r["iso_code"]
            unit = iso if iso in ISO3 else ("world" if r["country"] == "World" else None)
            if unit is None:
                continue
            for col, (name, tr, desc) in table.items():
                emit(unit, name, "a", period_end("a", r["year"]), 9, r.get(col))
        for col, (name, tr, desc) in table.items():
            var(name, tr, "a", "Our World in Data", desc)


def jodi():
    picks = {
        ("oil_primary.zip", "TOTCRUDE", "INDPROD", "KBD"): ("energy/crude_production", "kb/d crude oil production"),
        ("oil_primary.zip", "TOTCRUDE", "CLOSTLV", "KBBL"): ("energy/crude_stocks", "closing crude stocks, kbbl"),
        ("oil_primary.zip", "TOTCRUDE", "TOTIMPSB", "KBD"): ("energy/crude_imports", "crude imports, kb/d"),
        ("oil_primary.zip", "TOTCRUDE", "TOTEXPSB", "KBD"): ("energy/crude_exports", "crude exports, kb/d"),
        ("oil_secondary.zip", "TOTPRODS", "TOTDEMO", "KBD"): ("energy/oil_demand", "oil products demand, kb/d"),
        ("gas.zip", "NATGAS", "INDPROD", "TJ"): ("energy/gas_production_monthly", "natural gas production, TJ"),
        ("gas.zip", "NATGAS", "MAINTOT", "TJ"): ("energy/gas_demand", "natural gas demand, TJ"),
    }
    for fname in ("oil_primary.zip", "oil_secondary.zip", "gas.zip"):
        path = os.path.join(RAW, "jodi", fname)
        if not os.path.exists(path):
            continue
        z = zipfile.ZipFile(path)
        with z.open(z.namelist()[0]) as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="utf-8", errors="replace")):
                p = picks.get((fname, r["ENERGY_PRODUCT"], r["FLOW_BREAKDOWN"], r["UNIT_MEASURE"]))
                if p:
                    emit(ISO2.get(r["REF_AREA"]), p[0], "m", period_end("m", r["TIME_PERIOD"]), 2, r["OBS_VALUE"])
    for (_, _, _, _), (name, desc) in picks.items():
        var(name, "log1p", "m", "JODI", desc)


def faostat():
    path = os.path.join(RAW, "faostat", "cpi.zip")
    if os.path.exists(path):
        z = zipfile.ZipFile(path)
        names = {"23013": ("food/food_cpi", "log", "food consumer price index"),
                 "23014": ("food/food_inflation", "id", "food price inflation, %"),
                 "23012": ("economy/cpi_general", "log", "consumer price index")}
        months = {m: i for i, m in enumerate(calendar.month_name) if m}
        with z.open("ConsumerPriceIndices_E_All_Data_(Normalized).csv") as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="latin-1")):
                n = names.get(r["Item Code"])
                m = months.get(r["Months"])
                if n and m:
                    iso = M49.get(int(r["Area Code (M49)"].strip("'") or 0))
                    emit(iso, n[0], "m", month_end(int(r["Year"]), m), 3, r["Value"])
        for n in names.values():
            var(n[0], n[1], "m", "FAOSTAT consumer prices", n[2])
    path = os.path.join(RAW, "faostat", "crops.zip")
    if os.path.exists(path):
        z = zipfile.ZipFile(path)
        member = [n for n in z.namelist() if n.endswith("All_Data_(Normalized).csv")][0]
        items = {"Cereals, primary": "cereals", "Wheat": "wheat", "Maize (corn)": "maize", "Rice": "rice"}
        with z.open(member) as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="latin-1")):
                it = items.get(r["Item"])
                if it and r["Element"] in ("Production", "Yield"):
                    iso = M49.get(int(r["Area Code (M49)"].strip("'") or 0))
                    name = "food/%s_%s" % (it, "production" if r["Element"] == "Production" else "yield")
                    emit(iso, name, "a", period_end("a", r["Year"]), 12, r["Value"])
        for it in items.values():
            var("food/%s_production" % it, "log1p", "a", "FAOSTAT crops", it + " production, t")
            var("food/%s_yield" % it, "log1p", "a", "FAOSTAT crops", it + " yield")


# minimal reader for .xlsx (Office Open XML) workbooks
NSX = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
       "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}


def xlsx(path):
    z = zipfile.ZipFile(path)
    ss = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NSX):
            ss.append("".join(t.text or "" for t in si.iter("{%s}t" % NSX["m"])))
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    out = {}
    for s in wb.find("m:sheets", NSX):
        t = rels[s.get("{%s}id" % NSX["r"])].lstrip("/")
        t = t if t.startswith("xl/") else "xl/" + t
        rows = []
        for row in ET.fromstring(z.read(t)).iter("{%s}row" % NSX["m"]):
            cells = {}
            for c in row.findall("m:c", NSX):
                v = c.find("m:v", NSX)
                ref = re.match(r"[A-Z]+", c.get("r")).group(0)
                i = 0
                for ch in ref:
                    i = i * 26 + ord(ch) - 64
                if c.get("t") == "inlineStr":
                    val = "".join(x.text or "" for x in c.iter("{%s}t" % NSX["m"]))
                elif v is None:
                    continue
                elif c.get("t") == "s":
                    val = ss[int(v.text)]
                else:
                    val = v.text
                cells[i - 1] = val
            if cells:
                rows.append([cells.get(i) for i in range(max(cells) + 1)])
        out[s.get("name")] = rows
    return out


def epu():
    path = os.path.join(RAW, "epu", "countries.xlsx")
    if not os.path.exists(path):
        return
    rows = next(iter(xlsx(path).values()))
    header = rows[0]
    units = []
    for h in header[2:]:
        if h in ("GEPU_current", "GEPU_ppp"):
            units.append("world" if h == "GEPU_current" else None)
        else:
            units.append(iso3_of_name(re.sub(r"\s*\(.*\)$", "", h or "")) or
                         {"SCMP China": None, "Mainland China": "CHN"}.get(h))
    for r in rows[1:]:
        y, m = num(r[0]), num(r[1]) if len(r) > 1 else None
        if y is None or m is None:
            continue
        for j, unit in enumerate(units):
            if unit and len(r) > j + 2:
                emit(unit, "uncertainty/policy", "m", month_end(int(y), int(m)), 1, r[j + 2])
    var("uncertainty/policy", "log", "m", "Baker, Bloom & Davis EPU", "economic policy uncertainty index")


def climate():
    path = os.path.join(RAW, "climate", "gistemp_global.csv")
    if os.path.exists(path):
        lines = open(path).read().splitlines()[1:]
        rd = csv.reader(lines)
        header = next(rd)
        for r in rd:
            for m in range(1, 13):
                v = num(r[m]) if len(r) > m else None
                emit("world", "climate/global_temperature", "m", month_end(int(r[0]), m), 1, v)
        var("climate/global_temperature", "id", "m", "NASA GISTEMP v4", "global land-ocean temperature anomaly, C")
    path = os.path.join(RAW, "climate", "oni.txt")
    if os.path.exists(path):
        seasons = ["DJF", "JFM", "FMA", "MAM", "AMJ", "MJJ", "JJA", "JAS", "ASO", "SON", "OND", "NDJ"]
        for line in open(path).read().splitlines()[1:]:
            f = line.split()
            if len(f) == 4 and f[0] in seasons:
                m = seasons.index(f[0]) + 2          # the season's last month
                y = int(f[1]) + (1 if m > 12 else 0)
                emit("world", "climate/enso", "m", month_end(y, (m - 1) % 12 + 1), 1, f[3])
        var("climate/enso", "id", "m", "NOAA CPC ONI", "Oceanic Nino Index (El Nino > 0.5, La Nina < -0.5)")
    # country temperature and precipitation, as anomalies from each calendar month's
    # 1931-1960 mean: a baseline from before any origin
    for v, name, desc in (("tas", "climate/temperature_anomaly", "temperature anomaly from 1931-60, C"),
                          ("pr", "climate/precipitation_anomaly", "log precipitation ratio to 1931-60")):
        d = os.path.join(RAW, "climate", "cru_" + v)
        if not os.path.isdir(d):
            continue
        for fname in os.listdir(d):
            try:
                data = json.load(open(os.path.join(d, fname)))["data"]
            except (ValueError, KeyError):
                continue
            if not isinstance(data, dict):
                continue
            for iso, series in data.items():
                if iso not in ISO3 or not isinstance(series, dict):
                    continue
                base = defaultdict(list)
                for ym, x in series.items():
                    if x is not None and 1931 <= int(ym[:4]) <= 1960:
                        base[int(ym[5:7])].append(x)
                clim = {m: sum(xs) / len(xs) for m, xs in base.items() if xs}
                for ym, x in series.items():
                    m = int(ym[5:7])
                    if x is None or m not in clim:
                        continue
                    a = x - clim[m] if v == "tas" else math.log((x + 1.0) / (clim[m] + 1.0))
                    emit(iso, name, "m", month_end(int(ym[:4]), m), 12, a)
        var(name, "id", "m", "CRU TS 4.07 via World Bank CCKP", desc)


OPENALEX = {"C154945302": "ai", "C119857082": "machine_learning", "C108225325": "semiconductors",
            "C555008776": "batteries", "C41291067": "photovoltaics", "C2779305189": "genome_editing",
            "C2779615549": "vaccines", "C58053490": "quantum_computing", "C110875604": "internet",
            "C34413123": "robotics"}


def openalex():
    tpath = os.path.join(RAW, "openalex", "total.json")
    if not os.path.exists(tpath):
        return
    total = {int(g["key"]): g["count"] for g in json.load(open(tpath))["group_by"] if g["key"].isdigit()}
    for cid, field in OPENALEX.items():
        path = os.path.join(RAW, "openalex", cid + ".json")
        if not os.path.exists(path):
            continue
        for g in json.load(open(path)).get("group_by", []):
            y = int(g["key"]) if g["key"].isdigit() else None
            if y and 1950 <= y and total.get(y):
                emit("world", "technology/research_" + field, "a", period_end("a", y), 12,
                     1e6 * g["count"] / total[y])
        var("technology/research_" + field, "log1p", "a", "OpenAlex", field + " works per million works")


# --- catalogues --------------------------------------------------------------------------------------

def gw_names():
    path = os.path.join(RAW, "ucdp", "iisystem.dat")
    out = {}
    if os.path.exists(path):
        for line in open(path, encoding="latin-1"):
            f = line.rstrip("\n").split("\t")
            if len(f) >= 3:
                out[f[0].strip()] = iso3_of_name(f[2])
    return out


def ucdp():
    path = os.path.join(RAW, "ucdp", "acd.zip")
    if not os.path.exists(path):
        return
    gw = gw_names()
    z = zipfile.ZipFile(path)
    with z.open(z.namelist()[0]) as f:
        rows = list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8")))
    episodes, wars = set(), set()
    for r in rows:
        locs = [gw.get(g.strip()) for g in r["gwno_loc"].split(",")]
        names = [iso3_of_name(n) for n in r["location"].split(",")]
        units = {u for u in locs + names if u}
        for u in units:
            episodes.add((u, r["start_date2"], r["conflict_id"], r["type_of_conflict"]))
            if r["intensity_level"] == "2":
                wars.add((u, r["year"], r["conflict_id"]))
    for u, d, cid, t in episodes:
        EVENTS.append(("conflict_onset", u, d, "conflict %s type %s" % (cid, t)))
    # a war year is an escalation the first year a conflict reaches it in a run of years
    seen = defaultdict(set)
    for u, y, cid in wars:
        seen[(u, cid)].add(int(y))
    for (u, cid), ys in seen.items():
        for y in sorted(ys):
            if y - 1 not in ys:
                EVENTS.append(("war_onset", u, "%d-07-01" % y, "conflict %s reaches 1000 deaths" % cid))


def esrb():
    path = os.path.join(RAW, "esrb", "crises.xlsx")
    if not os.path.exists(path):
        return
    for sheet, rows in xlsx(path).items():
        if not sheet.lower().startswith("systemic"):
            continue
        for r in rows[2:]:
            if not r or not r[0] or len(r) < 6:
                continue
            iso = ISO2.get(r[0].strip().upper().replace("UK", "GB").replace("EL", "GR"))
            if r[5] == "1" and r[2] and re.match(r"\d{4}-\d{2}", r[2]):
                EVENTS.append(("banking_crisis", iso, r[2][:7] + "-01", "ESRB systemic crisis"))


# --- run ------------------------------------------------------------------------------------------------

def main():
    os.makedirs(OUT, exist_ok=True)
    steps = [fred, worldbank, bis, oecd, owid, jodi, faostat, epu, climate, openalex, ucdp, esrb]
    for step in steps:
        n0, e0 = len(ROWS), len(EVENTS)
        step()
        print("%-10s %8d values %5d events" % (step.__name__, len(ROWS) - n0, len(EVENTS) - e0), file=sys.stderr)
    # one value per unit, variable and period: the last source wins
    dedup = {}
    for r in ROWS:
        dedup[(r[0], r[1], r[3])] = r
    with open(os.path.join(OUT, "series.tsv"), "w") as f:
        for r in sorted(dedup.values()):
            f.write("%s\t%s\t%s\t%s\t%s\t%.10g\n" % r)
    with open(os.path.join(OUT, "variables.tsv"), "w") as f:
        used = {r[1] for r in dedup.values()}
        for name in sorted(VARS):
            if name in used:
                f.write("\t".join((name,) + VARS[name]) + "\n")
    with open(os.path.join(OUT, "events.tsv"), "w") as f:
        for e in sorted(set(x for x in EVENTS if x[1])):
            f.write("\t".join(e) + "\n")
    print("%d values, %d variables, %d events" % (len(dedup), len({r[1] for r in dedup.values()}), len(EVENTS)),
          file=sys.stderr)


if __name__ == "__main__":
    main()
