"""Sea routes between countries over the MARNET network and the chokepoints
they pass (bead bnm-1pq.10, docs/ESSENTIALS.md section 8 step 9). Research
tooling; bnm.chokepoints reads what this writes.

    python -I scripts/routes.py energy     # BACI HS92 crude, LNG, gas, oil products, bilateral
    python -I scripts/routes.py portwatch  # PortWatch tanker tonnes by port and year
    python -I scripts/routes.py routes     # routes.csv, routes-years.csv, routes-modes.csv, ...
    python -I scripts/routes.py --selftest

Network. MARNET as searoute-py packages it (data/raw/material/
sea-routes-marnet/searoute-py/marnet_searoute.geojson, Eurostat GISCO, from
the Oak Ridge global shipping lanes of c.2000 with AIS lines around Europe):
every consecutive coordinate pair is an undirected edge weighted by its
great-circle length. One connected component of 9,686 nodes. The Arctic
passages (searoute's 'northwest' tag: the Northwest Passage and the
Northern Sea Route) are removed, as searoute does by default; otherwise
Shanghai-Rotterdam goes over the pole.

Chokepoints. An edge passes a chokepoint if it carries one of the
chokepoint's searoute passage tags, or if it crosses one of the gate lines
below (drawn across the strait from coast to coast and checked against
plots of the network). The names follow PortWatch's 28 chokepoints; 20 are
resolved. Kerch is a gate across the narrows: MARNET has one lane into the
Sea of Azov, and the Azov ports (Rostov, Azov, Taganrog, Yeysk, Temryuk,
Mariupol, Berdyansk; Port Kavkaz at the strait's Azov end) snap north of it,
Taman and Kerch town south. The other eight (Bohai, Ombai, Torres,
Balabac, Mindoro, Yucatan, Windward, Mona) sit on lane hubs of the
generalised network or in archipelagos where its lanes cross land, so a
gate cannot tell passing from touching; they are left out. None carries a
large share of a major importer's essentials: Bohai is inside China's
coastal trade (Tianjin, Dalian), Yucatan, Windward and Mona carry US Gulf
and Caribbean trade with alternatives a day away, and Ombai, Torres,
Balabac and Mindoro are the bypasses of Lombok, Malacca and the South China
Sea lanes, which are resolved. Two are wider than their PortWatch
point: bosporus is the Turkish straits (Bosporus or Dardanelles tags) and
magellan is the Magellan strait together with the Drake passage round Cape
Horn. 'oresund' is a gate across the Kattegat, so it is every Baltic
entrance (Oresund and the Belts), not the Oresund alone.

Ports. A country's ports are PortWatch's (2,065 ports, AIS 2019-): each
port's share of the country's maritime trade value (share_country_maritime_
export for the exporting side, _import for the importing side), times the
share of the port's vessel calls in the ship class that carries the cargo
(tanker; dry bulk; container + general cargo + ro-ro). Ports under 2% of
the country's class weight are dropped (at most 10 kept) and the weights
renormalised. A pair's route is a mix over port pairs, each weighted by
the two ports' weights times exp(-(km - kmin) / 2000), kmin the pair's
shortest port-pair route: the ports a country uses come from its trade,
and a cargo leaves from the coast facing its destination (Russian crude to
Korea from Nakhodka, not the Baltic; US imports from China at Los
Angeles, from Germany at the East and Gulf coasts). The decay length,
2,000 km, was set before any exposure was computed and not tuned.

Tanker exports (crude, LNG, oil products) are different: a country loads
them where its fields, pipelines and terminals are, whatever the buyer, so
the decay cannot pick the export port (it sent US crude to Japan from
Valdez, 6% of US tanker calls, because the Gulf Coast is 11,000 km further).
The tanker export ports are weighted by PortWatch's export_tanker tonnes
(Daily_Ports_Data summed by port and year, `routes.py portwatch`; ports
under 2% dropped, at most 10), and which port serves which buyer is a
gravity model per exporter and year: the flow from port p to importer i is
a_p x K(p, i) x b_i, with each port's tonnage share and each importer's
seaborne tonnes of crude, LNG and products (BACI) held (Sinkhorn), and
K(p, i) = sum over i's ports q of w_q exp(-km(p, q) / 2,000). So the US
Gulf Coast ships what it ships and Asia gets most of it; Kozmino's ESPO
blend goes to China, Japan and Korea, and the Baltic's Urals to Europe and
(from 2022) India. The decay then picks only among the importer's ports
for each export port. Saudi Arabia's split between Ras Tanura, Juaymah and
Jubail (inside Hormuz) and Yanbu and Rabigh (Red Sea, the East-West
pipeline) and the UAE's between Ruwais, Das and Jebel Ali and Fujairah
(Gulf of Oman, the Habshan-Fujairah line) come from these tonnes, by year:
the Red Sea ports (Yanbu, Rabigh, Jeddah) 23% of Saudi tanker tonnes in
2019, 18-20% in 2021-23, 25% in 2024 (the Bab-el-Mandeb attacks); Fujairah
35-43% of the UAE's. Fujairah is also a storage and
bunkering hub (its tanker imports nearly equal its exports), so its export
tonnes overstate the pipeline crude it loads; EIA (June 2025) gives the
line's capacity, 1.8 mb/d, not its throughput, so no cited split replaces
it. Countries whose PortWatch ports have no tanker tonnes keep the
call-share weights.

Years. PortWatch's tonnes start in 2019: a BACI data year t uses year t's
tonnes for 2019-2025 and 2019's for 1995-2018. That is a look-ahead in the
port structure for data years 1995-2018 (exposure years 1997-2020): e.g.
US crude exports were near zero before 2016 (the export ban) and Corpus
Christi's terminals date from 2018-20, Yanbu's share before 2019 is
assumed equal to 2019's. The buyers (columns) are each year's own.
routes-years.csv has the tanker routes of each data year; routes.csv's
tanker rows pool 2019-2023 (tonnes and buyers). Bulk and general cargo keep
the pooled port weights and the decay over port pairs, so their structure
is fixed across years (the look-ahead noted in bnm.chokepoints).

PortWatch misses some terminals: Kozmino (the ESPO terminal, 30-46 Mt a
year) is added from Transneft's figures (SUPPLEMENT). Its tanker "exports"
are cargo leaving a port, domestic included: Valdez (Swanport, 17-20 Mt of
Alaskan crude a year, nearly all to the US West Coast) is dropped
(DOMESTIC); kept, the gravity model sent it to the nearest foreign buyers,
about a fifth of US tanker cargo to Japan, Korea and China. Coastal
cargo at other ports (Houston to the US East Coast) stays in their tonnes.

Each port is snapped to the nearest network node
(distance recorded); a port over 300 km from the network is not used, nor
any Caspian port (Aktau, Baku, Turkmenbashi: MARNET reaches the Caspian
through the Volga-Don canal, which the oil does not take). Countries without PortWatch ports:
landlocked ones (and the Caspian ones) go through the transit port in
TRANSIT (documented choices: the main corridor in the World Bank / UNCTAD
landlocked-country corridor literature), BACI's old aggregates through
ALIAS, and the rest (small islands, North Korea, Greenland ...) through the
searoute-py port (ports.geojson, UN/LOCODE) nearest the country's Natural
Earth label point. Transshipment is not modelled: a route is port to port.

Maritime flag. BACI does not record the mode. A pair is non-maritime when
the countries share a land border, or are within three land borders and
their Natural Earth label points are within 2,000 km (intra-European trade,
the US, Canada and Mexico, the Gulf states among themselves, southern
Africa). Borders are between each country's core: its Natural Earth
polygons of at least a quarter of its largest polygon's area, so overseas
and small exclaves (French Guiana, Ceuta and Melilla, Gibraltar, Northern
Ireland, Alaska, Kaliningrad, Cabinda) make no land border. Pairs in
MARITIME_PAIRS are maritime whatever the rule says: some are near by land
but cross a sea or a third country no corridor carries their trade through
(Turkey with Egypt, Israel and Ukraine, Finland-Estonia, China with
Bangladesh and Thailand); the rest share a border that trade does not cross by land (the
Himalaya, Amazonia, Borneo, New Guinea; Egypt and Saudi Arabia across the
Red Sea) and stay maritime; their borders do not count as land routes for
third countries either (Singapore-Indonesia is not near-land via Borneo). Borders are
current, 2020s ones. The flag is a pair property, except where a
product's mode is known (maritime_share, routes-modes.csv): LNG is shipped
also between land neighbours (Qatar to Kuwait; Russia's Sakhalin and Yamal
LNG to China) unless a side is landlocked, and Russian crude to China from
2011 is shipped beyond the capacity of the ESPO spur (15 Mt/yr 2011-17, 30
from 2018) and the Kazakh transit (10 Mt/yr from 2014): 44% of it in 2019,
60% in 2023. Known limitations, kept non-maritime: Russia-China coal,
oil products, iron ore, timber and fertiliser (rail and Pacific ports
both), Canadian crude to the US (pipeline, with some Gulf Coast tankers),
Arctic routes (Yamal LNG over the Northern Sea Route in summer is routed
round Europe: the Arctic passages are removed), and every other land
pair's goods (all by land).

Outputs (data/derived/supply/):
  routes.csv          exporter,importer,class,maritime,reason,routed,distance_km,chokepoints
      class tanker | bulk | general; routed: the share of the port-pair
      weight with a route (0 when a side has no port); distance_km: the
      weighted mean route length; chokepoints: name=share;... the share
      of the port-pair weight whose route passes the chokepoint. Written
      for every ordered pair of BACI countries, maritime or not (the
      routes of non-maritime pairs are there for reference).
  routes-closed.csv   exporter,importer,class,closed,affected,stranded,extra_km
      for each maritime pair and each chokepoint its route uses: the route
      recomputed with the chokepoint's edges removed. affected: share of
      the port-pair weight through it; stranded: the share with no other
      route (Gulf ports with Hormuz closed); extra_km: the weighted mean
      extra length over the affected port pairs that have one.
  routes-years.csv    exporter,importer,class,year,maritime,reason,routed,distance_km,chokepoints
      tanker routes of each BACI data year (pairs with seaborne tonnes that
      year): export ports by the year's PortWatch tonnes, gravity on the
      year's buyers.
  routes-modes.csv    exporter,importer,product,year,maritime
      the seaborne share of a pair's product where it is not the pair flag.
  route-ports.csv     iso,class,side,port,portname,source,weight,lon,lat,snap_km
      (tanker export rows: the 2019-2023 tonnes, source portwatch-tonnes)
  hs6-energy-pairs.csv.gz  hs,year,known,hs6,exporter,importer,value,tonnes
      (energy step) BACI HS92 bilateral flows of 270900 (crude), 271111
      (LNG), 271121 (pipeline gas), 271000 (oil products), known = year + 2
      as in scripts/baci.py.

Inputs beyond routes: data/raw/material/imf-portwatch/port-tanker-year.csv
(`routes.py portwatch`: portid,portname,ISO3,year,export_tanker,
import_tanker,portcalls_tanker,days; metric tonnes, summed on the server).

Licences: MARNET EUPL-1.2 / searoute-py Apache-2.0; PortWatch IMF terms
(attribution); Natural Earth public domain; BACI Etalab 2.0.
"""
import csv
import gzip
import heapq
import importlib.util
import json
import math
import os
import sys
import time
from collections import defaultdict, deque

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EARTH_KM = 6371.0088

MARNET = "data/raw/material/sea-routes-marnet/searoute-py/marnet_searoute.geojson"
SR_PORTS = "data/raw/material/sea-routes-marnet/searoute-py/ports.geojson"
PW_PORTS = "data/raw/material/imf-portwatch/PortWatch_ports_database.csv"
PW_TONNES = "data/raw/material/imf-portwatch/port-tanker-year.csv"
TONNES_FIRST, TONNES_LAST = 2019, 2025  # full PortWatch years
NE_ADMIN0 = "data/raw/geo/natural-earth/ne_10m_admin_0_countries.geojson"
COUNTRIES = "resources/bnm/countries.edn"
OUT = "data/derived/supply"

RESTRICTED = {"northwest"}

# name -> (PortWatch portid or None, searoute passage tags, gate polylines [(lon, lat) ...])
CHOKEPOINTS = {
    "suez": ("chokepoint1", ["suez"], []),
    "panama": ("chokepoint2", ["panama"], []),
    "bosporus": ("chokepoint3", ["bosporus", "dardanelles"], []),
    "bab-el-mandeb": ("chokepoint4", ["babalmandab"], []),
    "malacca": ("chokepoint5", [], [[(102.95, 2.15), (102.35, 0.95)]]),
    "hormuz": ("chokepoint6", ["ormuz"], []),
    "cape-of-good-hope": ("chokepoint7", [], [[(20.0, -34.83), (20.0, -85.0)]]),
    "gibraltar": ("chokepoint8", ["gibraltar"], []),
    "dover": ("chokepoint9", [], [[(1.33, 51.13), (1.62, 50.87)]]),
    "oresund": ("chokepoint10", [], [[(10.3, 56.6), (12.85, 56.6)]]),
    "taiwan": ("chokepoint11", [], [[(119.8, 25.45), (120.95, 24.85)]]),
    "korea": ("chokepoint12", [], [[(129.05, 35.08), (129.3, 34.65), (129.4, 34.1), (129.95, 33.5)]]),
    "tsugaru": ("chokepoint13", [], [[(140.6, 41.1), (140.6, 41.75)]]),
    "luzon": ("chokepoint14", [], [[(120.85, 21.95), (121.2, 18.55)]]),
    "lombok": ("chokepoint15", [], [[(115.7, -8.5), (116.05, -8.5)]]),
    "sunda": ("chokepoint19", ["sunda"], []),
    "makassar": ("chokepoint20", [], [[(117.0, -1.0), (119.5, -1.0)]]),
    "magellan": ("chokepoint21", [], [[(-69.3, -52.2), (-69.3, -52.7)], [(-66.0, -55.0), (-66.0, -85.0)]]),
    "bering": ("chokepoint26", ["bering"], []),
    # across the narrows between Kerch and the Chushka spit, south of Port Kavkaz: MARNET's
    # one lane into the Sea of Azov (the Black Sea hub at 36.4E 45.0N to 36.86E 45.62N)
    # crosses it at about 36.59E; no other edge does
    "kerch": ("chokepoint28", [], [[(36.40, 45.25), (36.80, 45.25)]]),
}
NAMES = list(CHOKEPOINTS)

CLASSES = {  # class -> PortWatch vessel-count columns
    "tanker": ["vessel_count_tanker"],
    "bulk": ["vessel_count_dry_bulk"],
    "general": ["vessel_count_container", "vessel_count_general_cargo", "vessel_count_RoRo"],
}
MIN_WEIGHT, MAX_PORTS, MAX_SNAP_KM, DECAY_KM = 0.02, 10, 300.0, 2000.0
CASPIAN = (46.0, 55.5, 36.0, 47.5)  # lon, lon, lat, lat: MARNET reaches it by the Volga-Don; no port there is used


def in_caspian(lon, lat):
    return CASPIAN[0] <= lon <= CASPIAN[1] and CASPIAN[2] <= lat <= CASPIAN[3]

# landlocked (and Caspian-only) country -> PortWatch transit port
TRANSIT = {
    "AFG": "port543",   # Karachi
    "AND": "port118",   # Barcelona
    "ARM": "port1016",  # Poti
    "AUT": "port446",   # Hamburg
    "AZE": "port162",   # Ceyhan (Botas, BTC crude terminal)
    "BDI": "port278",   # Dar es Salaam
    "BFA": "port4",     # Abidjan
    "BIH": "port912",   # Ploce (Neum has no port)
    "BLR": "port579",   # Klaipeda
    "BOL": "port1082",  # Arica
    "BTN": "port207",   # Kolkata
    "BWA": "port311",   # Durban
    "CAF": "port299",   # Douala
    "CHE": "port1114",  # Rotterdam
    "CZE": "port446",   # Hamburg
    "ETH": "port294",   # Djibouti
    "HUN": "port592",   # Koper
    "KAZ": "port833",   # Novorossiysk (CPC)
    "KGZ": "port644",   # Lianyungang
    "LAO": "port1197",  # Laem Chabang
    "LIE": "port1114",  # Rotterdam
    "LSO": "port311",   # Durban
    "LUX": "port57",    # Antwerp
    "MDA": "port843",   # Odesa
    "MKD": "port1293",  # Thessaloniki
    "MLI": "port272",   # Dakar
    "MNG": "port1297",  # Tianjin
    "MWI": "port784",   # Nacala
    "NER": "port265",   # Cotonou
    "NPL": "port207",   # Kolkata
    "PRY": "port184",   # Buenos Aires
    "PSE": "port73",    # Ashdod
    "RWA": "port278",   # Dar es Salaam
    "SMR": "port1004",  # Ravenna
    "SRB": "port260",   # Constanta
    "SSD": "port757",   # Mombasa
    "SVK": "port446",   # Hamburg
    "SWZ": "port702",   # Maputo
    "TCD": "port299",   # Douala
    "TJK": "port107",   # Bandar Abbas
    "TKM": "port107",   # Bandar Abbas
    "UGA": "port757",   # Mombasa
    "UZB": "port107",   # Bandar Abbas
    "VAT": "port1004",  # Ravenna (Rome's nearest listed: Civitavecchia absent)
    "XKX": "port312",   # Durres
    "ZMB": "port278",   # Dar es Salaam
    "ZWE": "port137",   # Beira
}

# BACI codes without a country of their own -> the country whose ports and borders stand in
ALIAS = {"ANT": "CUW", "SCG": "MNE", "ZA1": "ZAF", "PUS": "UMI", "S19": "TWN", "BES": "BES"}

# Natural Earth units without an ISO code (as scripts/cells.py)
NE_UNITS = {"KOS": "XKX", "SOL": "SOM", "CYN": "CYP", "CNM": "CYP", "ESB": "GBR", "WSB": "GBR",
            "USG": "CUB", "KAS": "IND", "KAB": "KAZ", "IOA": "AUS", "CSI": "AUS", "ATC": "AUS",
            "CLP": "FRA", "BRI": "BRA", "ALD": "ALA", "PSX": "PSE", "SDS": "SSD", "SAH": "ESH"}

# share a border, trade by sea
MARITIME_PAIRS = {frozenset(p) for p in [
    ("CHN", "IND"), ("CHN", "PAK"), ("CHN", "AFG"), ("IND", "MMR"), ("IDN", "MYS"), ("IDN", "PNG"),
    ("BRA", "PER"), ("BRA", "COL"), ("BRA", "VEN"), ("BRA", "GUY"), ("BRA", "SUR"), ("VEN", "GUY"),
    ("COL", "PER"), ("SUR", "GUY"), ("EGY", "SAU"),
    # no border, near by land (three borders, 2,000 km), but the land route crosses a
    # third country no corridor carries this trade through: across a sea (Turkey with
    # Egypt, Israel, Ukraine; Ukraine-Georgia; Finland-Estonia; the UAE-Iran; Norway-
    # Poland) or round Myanmar, Laos and Malaysia (China with Bangladesh and Thailand;
    # Singapore with Thailand and Cambodia; Hong Kong-Thailand)
    ("EGY", "TUR"), ("ISR", "TUR"), ("TUR", "UKR"), ("GEO", "UKR"), ("EST", "FIN"), ("ARE", "IRN"),
    ("NOR", "POL"), ("BGD", "CHN"), ("CHN", "THA"), ("SGP", "THA"), ("KHM", "SGP"), ("HKG", "THA")]}
NEAR_HOPS, NEAR_KM = 3, 2000.0


# ---------------------------------------------------------------- geometry

def haversine(a, b):
    lo1, la1, lo2, la2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_KM * math.asin(min(1.0, math.sqrt(h)))


def norm_lon(x):
    x = ((x + 180.0) % 360.0) - 180.0
    return x


def _orient(a, b, c):
    v = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    return (v > 1e-12) - (v < -1e-12)


def segments_cross(p1, p2, q1, q2):
    """Do the closed segments p1p2 and q1q2 meet (planar lon/lat)?"""
    o1, o2, o3, o4 = _orient(p1, p2, q1), _orient(p1, p2, q2), _orient(q1, q2, p1), _orient(q1, q2, p2)
    if o1 != o2 and o3 != o4:
        return True

    def on(a, b, c):
        return min(a[0], b[0]) - 1e-12 <= c[0] <= max(a[0], b[0]) + 1e-12 and \
            min(a[1], b[1]) - 1e-12 <= c[1] <= max(a[1], b[1]) + 1e-12
    return (o1 == 0 and on(p1, p2, q1)) or (o2 == 0 and on(p1, p2, q2)) or \
        (o3 == 0 and on(q1, q2, p1)) or (o4 == 0 and on(q1, q2, p2))


def crosses_gate(a, b, gate):
    if abs(a[0] - b[0]) > 180:  # an edge over the antimeridian: no gate there
        return False
    return any(segments_cross(a, b, g1, g2) for g1, g2 in zip(gate, gate[1:]))


# ---------------------------------------------------------------- network

class Net:
    """Undirected weighted graph: coords [(lon, lat)], adj [{j: km}], emask {(i, j): bits} (i < j)."""

    def __init__(self, edges, chokepoints, names):
        """edges: [(a, b, tag)] as coordinate pairs; chokepoints: name -> (id, tags, gates)."""
        self.names = list(names)
        self.index, self.coords, self.adj, self.emask = {}, [], [], {}
        tag_bits = defaultdict(int)
        for k, n in enumerate(self.names):
            for t in chokepoints[n][1]:
                tag_bits[t] |= 1 << k
        gates = [(k, g) for k, n in enumerate(self.names) for g in chokepoints[n][2]]
        for a, b, tag in edges:
            i, j = self._node(a), self._node(b)
            if i == j:
                continue
            w = haversine(self.coords[i], self.coords[j])
            m = tag_bits.get(tag, 0)
            for k, g in gates:
                if crosses_gate(self.coords[i], self.coords[j], g):
                    m |= 1 << k
            key = (min(i, j), max(i, j))
            if key in self.emask:
                w = min(w, self.adj[i][j])
                m |= self.emask[key]
            self.adj[i][j] = self.adj[j][i] = w
            self.emask[key] = m

    def _node(self, c):
        c = (round(norm_lon(c[0]), 6), round(c[1], 6))
        if c[0] == 180.0:
            c = (-180.0, c[1])
        i = self.index.get(c)
        if i is None:
            i = self.index[c] = len(self.coords)
            self.coords.append(c)
            self.adj.append({})
        return i

    def mask(self, i, j):
        return self.emask[(min(i, j), max(i, j))]

    def nearest(self, p):
        """(node, km) nearest to point p."""
        best, bk = None, float("inf")
        for i, c in enumerate(self.coords):
            if abs(c[1] - p[1]) > 8 and bk < 900:
                continue
            d = haversine(c, p)
            if d < bk:
                best, bk = i, d
        return best, bk

    def dijkstra(self, src, closed=0):
        """{node: (km, chokepoint bits)} of the shortest routes from src,
        avoiding edges whose bits meet `closed`. Ties keep the first found."""
        dist, bits = {src: 0.0}, {src: 0}
        done = set()
        heap = [(0.0, src)]
        while heap:
            d, i = heapq.heappop(heap)
            if i in done:
                continue
            done.add(i)
            for j, w in self.adj[i].items():
                m = self.mask(i, j)
                if m & closed:
                    continue
                nd = d + w
                if nd < dist.get(j, float("inf")) - 1e-9:
                    dist[j] = nd
                    bits[j] = bits[i] | m
                    heapq.heappush(heap, (nd, j))
        return {i: (dist[i], bits[i]) for i in dist}


def load_marnet(path):
    with open(path) as f:
        fc = json.load(f)
    edges = []
    for ft in fc["features"]:
        tag = (ft.get("properties") or {}).get("passage")
        if tag in RESTRICTED:
            continue
        g = ft["geometry"]
        lines = [g["coordinates"]] if g["type"] == "LineString" else g["coordinates"]
        for cs in lines:
            edges.extend((a, b, tag) for a, b in zip(cs, cs[1:]))
    return edges


def bits_names(bits, names):
    return [n for k, n in enumerate(names) if bits >> k & 1]


# ---------------------------------------------------------------- ports

def port_weights(rows, side, cls, min_weight=MIN_WEIGHT, max_ports=MAX_PORTS):
    """[(port, weight)] of one country: share of the country's maritime
    trade on `side` ('export' | 'import') times the port's share of calls in
    the class's ships; under min_weight dropped, at most max_ports, sums to 1.
    rows: dicts with portid, share_export, share_import, total and the
    class's counts. Falls back to trade shares alone when no port has
    calls of the class, and to equal weights when no port has a share."""
    cols = CLASSES[cls]
    raw = []
    for r in rows:
        s = r["share_" + side] or 0.0
        tot = r["total"] or 0.0
        c = sum(r.get(k) or 0.0 for k in cols)
        raw.append((r["portid"], s, s * (c / tot if tot > 0 else 0.0)))
    w = [(p, x) for p, _, x in raw]
    if sum(x for _, x in w) <= 0:
        w = [(p, s) for p, s, _ in raw]
    if sum(x for _, x in w) <= 0:
        w = [(p, 1.0) for p, _, _ in raw]
    tot = sum(x for _, x in w)
    if tot <= 0:
        return []
    return _trim([(p, x / tot) for p, x in w], min_weight, max_ports)


def _trim(w, min_weight, max_ports):
    """Shares [(port, x)] summing to one: under min_weight dropped (the largest
    always kept), at most max_ports, renormalised, largest first."""
    w = sorted(w, key=lambda t: -t[1])
    keep = [(p, x) for p, x in w if x >= min_weight][:max_ports] or w[:1]
    tot = sum(x for _, x in keep)
    return [(p, x / tot) for p, x in keep]


def tonnage_years(year):
    """The PortWatch years whose tanker tonnes stand for a BACI data year: the
    year itself within TONNES_FIRST..TONNES_LAST, else the nearest end (so
    1995-2018 use 2019's ports: a look-ahead in the port structure)."""
    return [min(max(year, TONNES_FIRST), TONNES_LAST)]


def tonnage_weights(rows, tonnes, years, min_weight=MIN_WEIGHT, max_ports=MAX_PORTS):
    """[(port, weight)] of a country's tanker export ports by PortWatch's
    export_tanker tonnes summed over `years` (where the country's crude, LNG
    and products are loaded); [] when its ports have none. tonnes: {portid
    {year t}}."""
    raw = [(r["portid"], sum(tonnes.get(r["portid"], {}).get(y, 0.0) for y in years)) for r in rows]
    tot = sum(x for _, x in raw)
    if tot <= 0:
        return []
    return _trim([(p, x / tot) for p, x in raw if x > 0], min_weight, max_ports)


# Tanker terminals PortWatch does not list, with their loadings (t/yr). Kozmino,
# the ESPO pipeline's Pacific terminal (Nakhodka's PortWatch entry is the
# port's own 2 Mt): Transneft 35.1 Mt in 2021 (Reuters via Daily Sabah), about
# 42 Mt in 2022 (Interfax), 42.8 Mt in 2023 and 46 expected for 2024 (S&P
# Global, Jan 2024); 2019-20 at its 30 Mt design capacity (no figure found),
# 2025 as 2024. Sakhalin's terminals (Prigorodnoye LNG and crude, De-Kastri)
# are under-counted in PortWatch's Korsakov and have no supplement.
SUPPLEMENT = {"RUS": [{"portid": "sup:kozmino", "portname": "Kozmino (ESPO terminal)", "lon": 133.05, "lat": 42.73,
                       "tonnes": {2019: 30e6, 2020: 30e6, 2021: 35.1e6, 2022: 42e6, 2023: 42.8e6, 2024: 46e6,
                                  2025: 46e6}}]}


# PortWatch ports whose tanker cargo is domestic, not exported
DOMESTIC = {"port1241"}  # Valdez (Swanport): Alaskan crude to the US West Coast (Jones Act)


def with_supplement(iso, rows, tonnes):
    """(rows, tonnes) with SUPPLEMENT's terminals of the country added and
    the DOMESTIC ports dropped."""
    rows = [r for r in rows if r["portid"] not in DOMESTIC]
    sup = SUPPLEMENT.get(iso)
    if not sup:
        return rows, tonnes
    tonnes = dict(tonnes)
    rows = list(rows)
    for x in sup:
        rows.append({k: v for k, v in x.items() if k != "tonnes"})
        tonnes[x["portid"]] = x["tonnes"]
    return rows, tonnes


def load_tonnes(path):
    """{portid {year export_tanker tonnes}} of PortWatch's daily port data
    summed by year (fetched by `routes.py portwatch`); {} if absent."""
    out = defaultdict(dict)
    if not os.path.exists(path):
        return {}
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            out[r["portid"]][int(r["year"])] = num(r["export_tanker"]) or 0.0
    return dict(out)


def fetch_tonnes(root):
    """PortWatch Daily_Ports_Data summed by port and year on the server
    (export_tanker, import_tanker in metric tonnes, port calls, days) ->
    PW_TONNES."""
    import urllib.parse
    import urllib.request
    url = ("https://services9.arcgis.com/weJ1QsnbMYJlCHdG/arcgis/rest/services/"
           "Daily_Ports_Data/FeatureServer/0/query")
    stats = [{"statisticType": "sum", "onStatisticField": k, "outStatisticFieldName": k}
             for k in ("export_tanker", "import_tanker", "portcalls_tanker")]
    stats.append({"statisticType": "count", "onStatisticField": "ObjectId", "outStatisticFieldName": "days"})
    isos = sorted({r["iso"] for r in load_portwatch(os.path.join(root, PW_PORTS)).values()})
    rows = []
    for iso in isos:  # the whole table at once times out on the server
        off = 0
        while True:
            q = urllib.parse.urlencode({
                "where": f"ISO3 = '{iso}'", "groupByFieldsForStatistics": "portid,portname,ISO3,year",
                "outStatistics": json.dumps(stats), "orderByFields": "portid,year",
                "resultOffset": off, "resultRecordCount": 1000, "f": "json"})
            for attempt in range(6):  # the service fails now and then; retry with a pause
                try:
                    with urllib.request.urlopen(url + "?" + q, timeout=300) as r:
                        d = json.load(r)
                except OSError as ex:
                    d = {"error": str(ex)}
                if "error" not in d:
                    break
                time.sleep(5 * (attempt + 1))
            else:
                raise SystemExit(f"PortWatch {iso}: {d['error']}")
            fs = [ft["attributes"] for ft in d.get("features", [])]
            rows.extend(fs)
            if len(fs) < 1000:
                break
            off += len(fs)
        print(f"  {iso}: {len(rows)} port-years", file=sys.stderr)
    path = os.path.join(root, PW_TONNES)
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        cols = ["portid", "portname", "ISO3", "year", "export_tanker", "import_tanker", "portcalls_tanker", "days"]
        w.writerow(cols)
        for a in rows:
            w.writerow([a[c] for c in cols])
    print(f"wrote {PW_TONNES} ({len(rows)} rows)", file=sys.stderr)


# ---------------------------------------------------------------- modes (per product)

# Pipeline capacity from Russia to China, tonnes a year, from the year it opened.
# ESPO spur Skovorodino-Mohe: 15 Mt/yr from January 2011, 30 Mt/yr from January
# 2018 (second line Mohe-Daqing; Transneft). Rosneft's transit across
# Kazakhstan (Atasu-Alashankou): a 7 Mt/yr swap from January 2014 extended by
# the 2014 CNPC contract to about 10 Mt/yr (200 kb/d; Argus 2014, Enerdata).
ESPO_SPUR = {2011: 15e6, 2018: 30e6}
KAZ_TRANSIT = {2014: 10e6}


def _step(table, year):
    v = 0.0
    for y, x in sorted(table.items()):
        if year >= y:
            v = x
    return v


def maritime_share(e, i, hs6, year, tonnes, maritime):
    """The share of a pair's flow of one HS6 product that goes by sea, where
    it differs from the pair's flag; None where the flag stands.
      LNG (271111) is shipped, also between land neighbours (Qatar to Kuwait,
        Russia's Sakhalin and Yamal LNG to China), unless a side is landlocked
        (TRANSIT: by road or rail from the neighbour's terminal).
      Russian crude to China (270900) from 2011: what the pipelines (ESPO
        spur, Kazakh transit) cannot carry is shipped (Kozmino; the Baltic
        and Black Sea from 2022). Rail after 2011 is ignored. Before 2011
        the pair flag (land: rail and the Kazakh pipeline) stands.
    Other products of land pairs keep the flag (see the module notes)."""
    if hs6 == "271111" and not maritime and e not in TRANSIT and i not in TRANSIT:
        return 1.0
    if (e, i, hs6) == ("RUS", "CHN", "270900") and year >= 2011:
        land = _step(ESPO_SPUR, year) + _step(KAZ_TRANSIT, year)
        return max(0.0, 1.0 - land / tonnes) if tonnes and tonnes > 0 else 0.0
    return None


def num(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def load_portwatch(path):
    out = {}
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            out[r["portid"]] = {
                "portid": r["portid"], "portname": r["portname"], "iso": r["ISO3"],
                "lon": float(r["lon"]), "lat": float(r["lat"]),
                "share_export": num(r["share_country_maritime_export"]),
                "share_import": num(r["share_country_maritime_import"]),
                "total": num(r["vessel_count_total"]),
                **{k: num(r[k]) for cs in CLASSES.values() for k in cs}}
    return out


def iso2_to_iso3(path):
    m, cur = {}, {}
    with open(path) as f:
        for line in f:
            line = line.strip().lstrip("{")
            for key in ("iso2", "iso3"):
                if line.startswith(":" + key + " "):
                    cur[key] = line.split('"')[1]
            if "iso2" in cur and "iso3" in cur:
                m[cur["iso2"]] = cur["iso3"]
                cur = {}
    return m


def load_sr_ports(path, i2):
    with open(path) as f:
        fc = json.load(f)
    out = defaultdict(list)
    for ft in fc["features"]:
        code = ft["properties"].get("port") or ""
        iso = i2.get(code[:2])
        if iso:
            lon, lat = ft["geometry"]["coordinates"][:2]
            out[iso].append({"portid": "sr:" + code, "portname": ft["properties"].get("name") or code,
                             "lon": lon, "lat": lat})
    return out


# ---------------------------------------------------------------- borders

def load_borders(path):
    """iso -> {label: (lon, lat), core: [shapely polygons]} from Natural Earth admin 0."""
    from shapely.geometry import shape
    with open(path) as f:
        fc = json.load(f)
    out = {}
    for ft in fc["features"]:
        p = ft["properties"]
        unit = p["ADM0_A3"]
        iso = NE_UNITS.get(unit) or (p["ISO_A3"] if p["ISO_A3"] != "-99" else
                                     (p["ISO_A3_EH"] if p["ISO_A3_EH"] != "-99" else None))
        if not iso:
            continue
        g = shape(ft["geometry"])
        polys = list(g.geoms) if g.geom_type == "MultiPolygon" else [g]
        e = out.setdefault(iso, {"label": None, "polys": []})
        if unit == iso or e["label"] is None:
            e["label"] = (p["LABEL_X"], p["LABEL_Y"])
        e["polys"].extend(polys)
    for e in out.values():
        big = max(q.area for q in e["polys"])
        e["core"] = [q for q in e["polys"] if q.area >= 0.25 * big]
    return out


def land_neighbours(countries, tol=0.02):
    """{iso: set of isos whose cores touch} (within tol degrees)."""
    from shapely.strtree import STRtree
    polys, owner = [], []
    for iso, e in countries.items():
        for q in e["core"]:
            polys.append(q)
            owner.append(iso)
    tree = STRtree(polys)
    nb = defaultdict(set)
    for k, q in enumerate(polys):
        for j in tree.query(q.buffer(tol)):
            a, b = owner[k], owner[int(j)]
            if a != b and q.distance(polys[int(j)]) <= tol:
                nb[a].add(b)
                nb[b].add(a)
    return nb


def drop_pairs(nb, pairs):
    """nb without the borders in pairs (a maritime pair's border is no land route for others either)."""
    return {a: {b for b in bs if frozenset((a, b)) not in pairs} for a, bs in nb.items()}


def hops(nb, src, limit):
    """{iso: land-border hops from src} up to limit."""
    seen, q = {src: 0}, deque([src])
    while q:
        a = q.popleft()
        if seen[a] >= limit:
            continue
        for b in nb.get(a, ()):
            if b not in seen:
                seen[b] = seen[a] + 1
                q.append(b)
    return seen


def maritime_flag(a, b, nb, labels, near_hops=NEAR_HOPS, near_km=NEAR_KM, maritime_pairs=MARITIME_PAIRS,
                  cache=None):
    """(maritime?, reason): reason border | near-land | sea. cache: {a: hops from a}, optional."""
    if frozenset((a, b)) in maritime_pairs:
        return True, "sea"
    if b in nb.get(a, ()):
        return False, "border"
    cache = {} if cache is None else cache
    if a not in cache:
        cache[a] = hops(nb, a, near_hops)
    h = cache[a].get(b)
    la, lb = labels.get(a), labels.get(b)
    if h is not None and la and lb and haversine(la, lb) <= near_km:
        return False, "near-land"
    return True, "sea"


# ---------------------------------------------------------------- pair routes

def pair_route(ep, ip, table, decay_km=DECAY_KM):
    """One pair over its port pairs. ep, ip: [(node, weight)]; table[node] =
    {node: (km, bits)}. A port pair's weight is wp x wq x exp(-(km - kmin) /
    decay_km), kmin the pair's shortest port-pair route: the trade shares
    say which ports a country uses, the decay that a cargo leaves from the
    side facing its destination. -> (routed, km, {bit: share}, parts):
    routed the trade-share weight with a route, km and shares over the
    decayed weights (summing to one), parts [(w, p, q, km, bits)] with the
    decayed weights normalised."""
    raw, routed = [], 0.0
    for p, wp in ep:
        tp = table[p]
        for q, wq in ip:
            r = tp.get(q)
            if r is not None:
                raw.append((wp * wq, p, q, r[0], r[1]))
                routed += wp * wq
    if not raw:
        return 0.0, None, {}, []
    kmin = min(x[3] for x in raw)
    dec = [(w * math.exp(-(km - kmin) / decay_km) if decay_km else w, p, q, km, b) for w, p, q, km, b in raw]
    tot = sum(x[0] for x in dec)
    parts = [(w / tot, p, q, km, b) for w, p, q, km, b in dec]
    share = defaultdict(float)
    for w, _, _, _, b in parts:
        for k in range(b.bit_length()):
            if b >> k & 1:
                share[k] += w
    return routed, sum(w * km for w, _, _, km, _ in parts), dict(share), parts


def port_kernel(p, ip, table, decay_km, offset):
    """How near export port p is to an importer: sum over its ports q of
    wq x exp(-(km(p, q) - offset) / decay_km), reachable q only (offset keeps
    it from underflowing; a constant per importer does not change gravity)."""
    tp = table[p]
    return sum(wq * math.exp(-(tp[q][0] - offset) / decay_km) for q, wq in ip if q in tp)


def gravity(rows, cols, kern, iters=2000, tol=1e-11):
    """Which export ports serve which importer. rows {port: share of the
    exporter's tanker tonnes}; cols {importer: seaborne tonnes bought};
    kern {importer {port: nearness}}. The flow port -> importer is
    a_p x kern x b_i with the ports' row sums and the importers' column sums
    held (Sinkhorn on the importers that buy): each port ships its tonnage,
    and it goes to the buyers nearest it first. -> {importer {port: share}}
    for every importer some port reaches, a_p x kern normalised (an importer
    that does not buy gets the mix it would). With no buyers a_p is the
    tonnage share."""
    import numpy as np
    ports = sorted(rows)
    imps = [i for i in sorted(kern) if any(kern[i].get(p, 0.0) > 0 for p in ports)]
    if not imps or not ports:
        return {}
    K = np.array([[kern[i].get(p, 0.0) for i in imps] for p in ports])
    r = np.array([rows[p] for p in ports], dtype=float)
    c = np.array([max(cols.get(i, 0.0), 0.0) for i in imps], dtype=float)
    a = r / r.sum()
    pos = c > 0
    if pos.any():
        Kp, cp = K[:, pos], c[pos] / c[pos].sum()
        live = Kp.sum(axis=1) > 0
        if live.any():
            rr = np.where(live, r, 0.0)
            rr = rr / rr.sum()
            b = np.ones(int(pos.sum()))
            for _ in range(iters):
                an = np.where(live, rr / np.maximum(Kp @ b, 1e-300), 0.0)
                b = cp / np.maximum(an @ Kp, 1e-300)
                done = np.max(np.abs(an - a) / np.maximum(an, 1e-300)) < tol
                a = an
                if done:
                    break
    X = a[:, None] * K
    out = {}
    for j, i in enumerate(imps):
        s = X[:, j].sum()
        if s > 0:
            out[i] = {p: float(X[k, j] / s) for k, p in enumerate(ports) if X[k, j] > 0}
    return out


def pair_route_mix(mix, ip, table, decay_km=DECAY_KM):
    """One pair with the exporter's ports fixed: mix {node: share} (from
    gravity); the decay picks among the importer's ports for each export
    port, exp(-(km - kmin_p) / decay_km) times their weights. -> (routed, km,
    {bit: share}, parts) as pair_route; routed the mix share with a route."""
    raw, routed = [], 0.0
    for p, m in mix.items():
        tp = table[p]
        qs = [(q, wq, tp[q]) for q, wq in ip if q in tp]
        if not qs:
            continue
        routed += m * sum(wq for _, wq, _ in qs)
        kmin = min(r[0] for _, _, r in qs)
        ws = [wq * math.exp(-(r[0] - kmin) / decay_km) for _, wq, r in qs]
        t = sum(ws)
        raw.extend((m * w / t, p, q, r[0], r[1]) for (q, _, r), w in zip(qs, ws))
    if not raw:
        return 0.0, None, {}, []
    tot = sum(x[0] for x in raw)
    parts = [(w / tot, p, q, km, b) for w, p, q, km, b in raw]
    share = defaultdict(float)
    for w, _, _, _, b in parts:
        for k in range(b.bit_length()):
            if b >> k & 1:
                share[k] += w
    return routed, sum(w * km for w, _, _, km, _ in parts), dict(share), parts


def closure_stats(parts, k, closed_table):
    """For chokepoint bit k: (affected, stranded, extra_km) over the port pairs whose route passes it."""
    aff = stranded = ext = 0.0
    for w, p, q, km, bits in parts:
        if not bits >> k & 1:
            continue
        aff += w
        alt = closed_table[p].get(q)
        if alt is None:
            stranded += w
        else:
            ext += w * (alt[0] - km)
    reach = aff - stranded
    return aff, stranded, (ext / reach if reach > 1e-12 else None)


# ---------------------------------------------------------------- run

def baci_countries(path):
    out = set()
    with gzip.open(path, "rt") as f:
        next(f)
        last = None
        for line in f:
            pair = line.split(",", 6)[4:6]
            key = (pair[0], pair[1])
            if key != last:
                out.update(pair)
                last = key
    return sorted(out)


TANKER_HS6 = {"270900": "crude-oil", "271111": "lng", "271000": "oil-products"}
BASE_YEARS = range(2019, 2024)  # the years routes.csv's tanker rows pool


def read_tanker_flows(path):
    """{year {(exporter, importer, hs6) tonnes}} of the tanker products in
    hs6-energy-pairs.csv.gz (BACI HS92; flows without tonnes left out)."""
    out = defaultdict(lambda: defaultdict(float))
    with gzip.open(path, "rt") as f:
        next(f)
        for line in f:
            _, y, _, hs6, e, i, _, t = line.rstrip("\n").split(",")
            if hs6 in TANKER_HS6 and t:
                out[int(y)][(e, i, hs6)] += float(t)
    return out


def run_routes(root, out):
    p = lambda x: os.path.join(root, x)
    edges = load_marnet(p(MARNET))
    net = Net(edges, CHOKEPOINTS, NAMES)
    print(f"network: {len(net.coords)} nodes, {len(net.emask)} edges", file=sys.stderr)
    for k, n in enumerate(NAMES):
        ne = sum(1 for m in net.emask.values() if m >> k & 1)
        print(f"  {n}: {ne} edges", file=sys.stderr)
        if ne == 0:
            raise SystemExit(f"chokepoint {n} has no edges")
    pw = load_portwatch(p(PW_PORTS))
    tonnes = load_tonnes(p(PW_TONNES))
    if not tonnes:
        raise SystemExit(f"no {PW_TONNES}: python -I scripts/routes.py portwatch")
    i2 = iso2_to_iso3(p(COUNTRIES))
    sr = load_sr_ports(p(SR_PORTS), i2)
    borders = load_borders(p(NE_ADMIN0))
    nb = drop_pairs(land_neighbours(borders), MARITIME_PAIRS)
    labels = {iso: e["label"] for iso, e in borders.items()}
    countries = baci_countries(p(os.path.join(OUT, "hs2-pairs.csv.gz")))
    print(f"{len(countries)} BACI countries", file=sys.stderr)

    snap = {}

    def snapped(pid, lon, lat):
        if pid not in snap:
            snap[pid] = net.nearest((lon, lat))
        return snap[pid]

    by_iso = defaultdict(list)
    for r in pw.values():
        by_iso[r["iso"]].append(r)
    ports = {}  # (iso, cls, side) -> [(node, w, portid, name, source, lon, lat, km)]
    pw_rows = {}  # iso -> usable PortWatch rows

    def entries(sel, src):
        lst = []
        for r, w in sel:
            node, km = snapped(r["portid"], r["lon"], r["lat"])
            if km <= MAX_SNAP_KM:
                lst.append((node, w, r["portid"], r["portname"], src, r["lon"], r["lat"], km))
        tot = sum(x[1] for x in lst)
        return [(x[0], x[1] / tot) + x[2:] for x in lst] if tot > 0 else []

    for iso in countries:
        base = ALIAS.get(iso, iso)
        rows = [r for r in by_iso.get(base, []) if not in_caspian(r["lon"], r["lat"])
                and snapped(r["portid"], r["lon"], r["lat"])[1] <= MAX_SNAP_KM]
        pw_rows[iso] = rows
        for cls in CLASSES:
            for side in ("export", "import"):
                if base in TRANSIT and not rows:
                    t = pw[TRANSIT[base]]
                    sel, src = [(t, 1.0)], "transit"
                elif rows:
                    ws = dict(port_weights(rows, side, cls))
                    sel, src = [(r, ws[r["portid"]]) for r in rows if r["portid"] in ws], "portwatch"
                elif sr.get(base) and labels.get(base):
                    lab = labels[base]
                    best = min(sr[base], key=lambda r: haversine((r["lon"], r["lat"]), lab))
                    sel, src = [(best, 1.0)], "searoute"
                else:
                    sel, src = [], "none"
                ports[(iso, cls, side)] = entries(sel, src)

    def tanker_export(iso, years):
        """The tanker export ports for some PortWatch years: by export tonnes
        where the country's ports have them, else the call-share weights."""
        rows, tn = with_supplement(iso, pw_rows.get(iso) or [], tonnes)
        ws = dict(tonnage_weights(rows, tn, years)) if rows else {}
        if ws:
            return entries([(r, ws[r["portid"]]) for r in rows if r["portid"] in ws], "portwatch-tonnes")
        return ports[(iso, "tanker", "export")]

    base_tonnage = [y for y in range(TONNES_FIRST, TONNES_LAST + 1) if y in BASE_YEARS]
    tk_years = sorted({tuple(tonnage_years(y)) for y in range(1995, 2025)} | {tuple(base_tonnage)})
    tk_ports = {(iso, ys): tanker_export(iso, list(ys)) for iso in countries for ys in tk_years}
    for iso in countries:
        ports[(iso, "tanker", "export")] = tk_ports[(iso, tuple(base_tonnage))]

    nodes = sorted({x[0] for v in list(ports.values()) + list(tk_ports.values()) for x in v})
    print(f"{len(nodes)} port nodes; routing", file=sys.stderr)
    table = {s: net.dijkstra(s) for s in nodes}

    class Lazy(dict):
        def __init__(self, k):
            self.k = k

        def __missing__(self, s):
            v = self[s] = net.dijkstra(s, closed=1 << self.k)
            return v
    closed = {k: Lazy(k) for k in range(len(NAMES))}
    hop_cache = {}
    flags = {(a, b): maritime_flag(ALIAS.get(a, a), ALIAS.get(b, b), nb, labels, cache=hop_cache)
             for a in countries for b in countries if a != b}

    # tanker: seaborne tonnes by exporter, importer and year, with the per-product modes
    flows = read_tanker_flows(p(os.path.join(OUT, "hs6-energy-pairs.csv.gz")))
    cols = defaultdict(lambda: defaultdict(float))  # (exporter, year) -> {importer: seaborne t}
    os.makedirs(p(out), exist_ok=True)
    n_modes = 0
    with open(p(os.path.join(out, "routes-modes.csv")), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["exporter", "importer", "product", "year", "maritime"])
        for y in sorted(flows):
            for (e, i, hs6), t in sorted(flows[y].items()):
                if (e, i) not in flags:
                    continue
                mar = flags[(e, i)][0]
                sh = maritime_share(e, i, hs6, y, t, mar)
                if sh is not None:
                    w.writerow([e, i, TANKER_HS6[hs6], y, f"{sh:.4f}"])
                    n_modes += 1
                cols[(e, y)][i] += t * (float(mar) if sh is None else sh)
    base_cols = defaultdict(lambda: defaultdict(float))
    for (e, y), c in cols.items():
        if y in BASE_YEARS:
            for i, t in c.items():
                base_cols[e][i] += t

    def ip_of(b):
        return [(x[0], x[1]) for x in ports[(b, "tanker", "import")]]

    def rows_of(lst):
        r = defaultdict(float)
        for x in lst:
            r[x[0]] += x[1]
        return dict(r)

    def chok(share):
        return ";".join(f"{NAMES[k]}={share[k]:.4f}" for k in sorted(share) if share[k] >= 1e-4)

    tanker_base = {}  # (e, i) -> pair_route_mix result
    n_years = 0
    with open(p(os.path.join(out, "routes-years.csv")), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["exporter", "importer", "class", "year", "maritime", "reason", "routed", "distance_km",
                    "chokepoints"])
        for e in countries:
            cand = sorted({x[0] for ys in tk_years for x in tk_ports[(e, ys)]})
            if not cand:
                continue
            kern = {}
            for b in countries:
                if b == e:
                    continue
                ip = ip_of(b)
                kms = [table[q0][q][0] for q0 in cand for q, _ in ip if q in table[q0]]
                if not kms:
                    continue
                off = min(kms)
                kern[b] = {q0: port_kernel(q0, ip, table, DECAY_KM, off) for q0 in cand}
            mix = gravity(rows_of(tk_ports[(e, tuple(base_tonnage))]), base_cols.get(e, {}), kern)
            for b, m in mix.items():
                tanker_base[(e, b)] = pair_route_mix(m, ip_of(b), table)
            for y in sorted(y for (x, y) in cols if x == e):
                c = cols[(e, y)]
                if not any(v > 0 for v in c.values()):
                    continue
                my = gravity(rows_of(tk_ports[(e, tuple(tonnage_years(y)))]), c, kern)
                for b in sorted(c):
                    if c[b] <= 0 or b not in my or (e, b) not in flags:
                        continue
                    routed, km, share, _ = pair_route_mix(my[b], ip_of(b), table)
                    mar, why = flags[(e, b)]
                    w.writerow([e, b, "tanker", y, int(mar), why, f"{routed:.4f}",
                                "" if km is None else f"{km:.0f}", chok(share)])
                    n_years += 1
    print(f"wrote routes-modes.csv ({n_modes}), routes-years.csv ({n_years})", file=sys.stderr)

    with open(p(os.path.join(out, "route-ports.csv")), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["iso", "class", "side", "port", "portname", "source", "weight", "lon", "lat", "snap_km"])
        for (iso, cls, side), lst in sorted(ports.items()):
            for node, wt, pid, name, src, lon, lat, km in lst:
                w.writerow([iso, cls, side, pid, name, src, f"{wt:.4f}", lon, lat, f"{km:.1f}"])
    n_rows = n_closed = 0
    with open(p(os.path.join(out, "routes.csv")), "w", newline="") as f, \
            open(p(os.path.join(out, "routes-closed.csv")), "w", newline="") as g:
        w, wc = csv.writer(f), csv.writer(g)
        w.writerow(["exporter", "importer", "class", "maritime", "reason", "routed", "distance_km", "chokepoints"])
        wc.writerow(["exporter", "importer", "class", "closed", "affected", "stranded", "extra_km"])
        for a in countries:
            for b in countries:
                if a == b:
                    continue
                mar, why = flags[(a, b)]
                for cls in CLASSES:
                    ip = [(x[0], x[1]) for x in ports[(b, cls, "import")]]
                    if cls == "tanker":
                        routed, km, share, parts = tanker_base.get((a, b), (0.0, None, {}, []))
                    else:
                        ep = [(x[0], x[1]) for x in ports[(a, cls, "export")]]
                        routed, km, share, parts = pair_route(ep, ip, table)
                    w.writerow([a, b, cls, int(mar), why, f"{routed:.4f}",
                                "" if km is None else f"{km:.0f}", chok(share)])
                    n_rows += 1
                    if mar and routed > 0:
                        for k in sorted(share):
                            aff, st, ext = closure_stats(parts, k, closed[k])
                            wc.writerow([a, b, cls, NAMES[k], f"{aff:.4f}", f"{st:.4f}",
                                         "" if ext is None else f"{ext:.0f}"])
                            n_closed += 1
    print(f"wrote routes.csv ({n_rows} rows), routes-closed.csv ({n_closed}), route-ports.csv", file=sys.stderr)


ENERGY = {"270900", "271111", "271121", "271000"}


def run_energy(root, out):
    spec = importlib.util.spec_from_file_location("baci", os.path.join(ROOT, "scripts", "baci.py"))
    baci = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(baci)
    import zipfile
    d = os.path.join(root, "data/raw/material/baci")
    zf = sorted(f for f in os.listdir(d) if f.startswith("BACI_HS92_V") and f.endswith(".zip"))[-1]
    z = zipfile.ZipFile(os.path.join(d, zf))
    iso = baci.iso_map(z)
    path = os.path.join(root, out, "hs6-energy-pairs.csv.gz")
    with gzip.open(path + ".part", "wt") as f:
        f.write("hs,year,known,hs6,exporter,importer,value,tonnes\n")
        for year, member in baci.year_files(z, "HS92"):
            df = baci.read_year(z, member, iso)
            df = df[df.hs6.isin(ENERGY)]
            for r in df.itertuples(index=False):
                t = "" if r.tonnes != r.tonnes else f"{r.tonnes:g}"
                f.write(f"HS92,{r.year},{r.year + baci.LAG},{r.hs6},{r.exporter},{r.importer},{r.value:g},{t}\n")
            print(year, len(df), file=sys.stderr)
    os.replace(path + ".part", path)


# ---------------------------------------------------------------- selftest

def selftest():
    # A toy sea: two routes from W to E, a short one through a strait (tagged
    # 'st') and a long one round a cape (crossing a gate); a port P inside a
    # gulf reachable only through the strait.
    cps = {"strait": (None, ["st"], []), "cape": (None, [], [[(5.0, -1.0), (5.0, -10.0)]])}
    names = ["strait", "cape"]
    W, M, E, S1, S2, P = (0, 0), (5, 0), (10, 0), (0, -5), (10, -5), (5, 3)
    edges = [(W, M, "st"), (M, E, None), (W, S1, None), (S1, S2, None), (S2, E, None), (M, P, None)]
    net = Net(edges, cps, names)
    i = lambda c: net.index[c]
    assert net.mask(i(S1), i(S2)) == 2, "the long leg crosses the cape gate"
    assert net.mask(i(W), i(M)) == 1, "the tagged edge is the strait"
    t = net.dijkstra(i(W))
    km, bits = t[i(E)]
    assert bits_names(bits, names) == ["strait"], bits
    assert abs(km - haversine(W, M) - haversine(M, E)) < 1e-6
    tc = net.dijkstra(i(W), closed=1)
    km2, bits2 = tc[i(E)]
    assert bits_names(bits2, names) == ["cape"] and km2 > km, (km2, km)
    assert i(P) not in tc or tc[i(P)][1] & 1 == 0
    # P is reachable without the strait only round the cape and back through M: allowed,
    # since only the strait edge W-M is closed
    assert i(P) in tc
    # pair aggregation: exporter two ports (W 0.75, S1 0.25), importer E
    table = {s: net.dijkstra(s) for s in (i(W), i(S1))}
    routed, mean_km, share, parts = pair_route([(i(W), 0.75), (i(S1), 0.25)], [(i(E), 1.0)], table, decay_km=None)
    assert abs(routed - 1.0) < 1e-12
    assert abs(share[0] - 0.75) < 1e-12, share
    assert share.get(1, 0.0) == 0.0 or abs(share[1] - 0.25) < 1e-12
    assert abs(sum(x[0] for x in parts) - 1.0) < 1e-12
    # with decay the port pair 1,000 km longer weighs exp(-1000 / 2000) less
    _, _, sh2, parts2 = pair_route([(i(W), 0.5), (i(S1), 0.5)], [(i(E), 1.0)], table, decay_km=2000.0)
    dW, dS = table[i(W)][i(E)][0], table[i(S1)][i(E)][0]
    xw, xs = 0.5 * math.exp(-max(0.0, dW - min(dW, dS)) / 2000.0), 0.5 * math.exp(-max(0.0, dS - min(dW, dS)) / 2000.0)
    assert abs(sh2[0] - xw / (xw + xs)) < 1e-12, (sh2, xw, xs)
    # an unreachable port pair lowers routed, not the shares
    r3, _, sh3, _ = pair_route([(i(W), 1.0)], [(i(E), 0.5), (10 ** 9, 0.5)], {i(W): table[i(W)]}, decay_km=None)
    assert abs(r3 - 0.5) < 1e-12 and abs(sh3[0] - 1.0) < 1e-12
    # closure: removing the strait diverts W's share round the cape, extra km positive
    closed_t = {s: net.dijkstra(s, closed=1) for s in (i(W), i(S1))}
    aff, st, ext = closure_stats(parts, 0, closed_t)
    assert abs(aff - 0.75) < 1e-12 and st == 0.0 and ext > 0
    # a cut: a network where the gulf has one way out
    net2 = Net([(W, M, "st"), (M, E, None)], {"strait": (None, ["st"], [])}, ["strait"])
    t2 = {s: net2.dijkstra(s) for s in (net2.index[W],)}
    c2 = {s: net2.dijkstra(s, closed=1) for s in (net2.index[W],)}
    _, _, _, parts2 = pair_route([(net2.index[W], 1.0)], [(net2.index[E], 1.0)], t2)
    aff2, st2, ext2 = closure_stats(parts2, 0, c2)
    assert aff2 == 1.0 and st2 == 1.0 and ext2 is None
    # gate geometry
    assert segments_cross((0, 0), (2, 2), (0, 2), (2, 0))
    assert not segments_cross((0, 0), (1, 1), (2, 0), (3, 1))
    assert not crosses_gate((179, 0), (-179, 0), [(0, -1), (0, 1)])
    # port weights: class share times trade share, small ports dropped, sums to one
    rows = [{"portid": "a", "share_export": 60.0, "share_import": 10.0, "total": 100.0,
             "vessel_count_tanker": 10.0, "vessel_count_dry_bulk": 0.0, "vessel_count_container": 90.0,
             "vessel_count_general_cargo": 0.0, "vessel_count_RoRo": 0.0},
            {"portid": "b", "share_export": 40.0, "share_import": 90.0, "total": 100.0,
             "vessel_count_tanker": 90.0, "vessel_count_dry_bulk": 0.0, "vessel_count_container": 10.0,
             "vessel_count_general_cargo": 0.0, "vessel_count_RoRo": 0.0},
            {"portid": "c", "share_export": 1.0, "share_import": 0.0, "total": 10.0,
             "vessel_count_tanker": 1.0, "vessel_count_dry_bulk": 0.0, "vessel_count_container": 0.0,
             "vessel_count_general_cargo": 0.0, "vessel_count_RoRo": 0.0}]
    wt = dict(port_weights(rows, "export", "tanker"))
    assert abs(wt["b"] - 36.0 / 42.0) < 1e-9 and "c" not in wt and abs(sum(wt.values()) - 1) < 1e-12, wt
    wb = dict(port_weights(rows, "export", "bulk"))
    assert abs(wb["a"] - 0.6) < 1e-9, "no bulk calls anywhere: trade shares"
    # maritime flag: border, near-land within hops and km, an override, the sea
    nb = {"A": {"B"}, "B": {"A", "C"}, "C": {"B"}, "X": {"Y"}, "Y": {"X"}}
    lab = {"A": (0, 0), "B": (5, 0), "C": (10, 0), "D": (0, 40), "X": (100, 0), "Y": (100, 5)}
    assert maritime_flag("A", "B", nb, lab) == (False, "border")
    assert maritime_flag("A", "C", nb, lab) == (False, "near-land"), "two hops, 1,112 km"
    assert maritime_flag("A", "C", nb, lab, near_km=1000.0) == (True, "sea")
    assert maritime_flag("A", "D", nb, lab) == (True, "sea"), "no land route"
    assert maritime_flag("X", "Y", nb, lab, maritime_pairs={frozenset(("X", "Y"))}) == (True, "sea")
    nb2 = drop_pairs(nb, {frozenset(("B", "C"))})
    assert maritime_flag("A", "C", nb2, lab, cache={}) == (True, "sea"), "no land route over a sea-only border"
    assert in_caspian(51.2, 43.6) and not in_caspian(50.2, 26.6)
    # tanker export ports by PortWatch export tonnes over the years asked; none -> []
    tons = {"a": {2019: 60.0, 2020: 0.0}, "b": {2019: 39.0, 2020: 100.0}, "c": {2019: 1.0}}
    tw = dict(tonnage_weights(rows, tons, [2019]))
    assert abs(tw["a"] - 60 / 99) < 1e-12 and "c" not in tw, tw
    assert dict(tonnage_weights(rows, tons, [2020])) == {"b": 1.0}
    assert tonnage_weights(rows, {}, [2019]) == []
    rs, ts = with_supplement("RUS", [], {})
    assert [r["portid"] for r in rs] == ["sup:kozmino"] and ts["sup:kozmino"][2023] == 42.8e6, "Kozmino is not a PortWatch port"
    rs, ts = with_supplement("USA", rows, tons)
    assert rs == rows and ts == tons
    rs, _ = with_supplement("USA", rows + [{"portid": "port1241"}], tons)
    assert rs == rows, "Valdez's tanker cargo is domestic"
    assert tonnage_years(2005) == [2019] and tonnage_years(2021) == [2021] and tonnage_years(2030) == [TONNES_LAST]
    # gravity: a port's exports are fixed by its tonnage and go to the nearest buyers first
    kern = {"X": {"A": 1.0, "B": 0.01}, "Y": {"A": 0.01, "B": 1.0}, "Z": {"A": 0.5, "B": 0.5}}
    mix = gravity({"A": 0.9, "B": 0.1}, {"X": 0.5, "Y": 0.5}, kern)
    xa = 0.5 * mix["X"]["A"] + 0.5 * mix["Y"]["A"]
    assert abs(xa - 0.9) < 1e-6, "row marginal held: port A ships 90%"
    assert abs(mix["Y"]["B"] - 0.2) < 1e-3, "B's 10% all goes to its neighbour Y, the rest of Y from A"
    assert abs(mix["X"]["A"] - 1.0) < 1e-3
    assert set(mix["Z"]) == {"A", "B"} and abs(sum(mix["Z"].values()) - 1) < 1e-12, "a column without trade still gets a mix"
    m2 = gravity({"A": 1.0}, {"X": 1.0}, {"X": {"A": 1.0}, "Y": {}})
    assert "Y" not in m2 and m2["X"] == {"A": 1.0}, "an unreachable importer has no mix"
    m3 = gravity({"A": 0.5, "B": 0.5}, {}, kern)
    assert abs(m3["X"]["A"] - 1.0 / 1.01) < 1e-9, "no trade at all: tonnage times kernel"
    # a fixed exporter-port mix is not reweighted by distance: the decay picks importer ports only
    rmix, _, shm, _ = pair_route_mix({i(W): 0.25, i(S1): 0.75}, [(i(E), 1.0)], table, decay_km=2000.0)
    assert abs(shm[0] - 0.25) < 1e-12 and abs(rmix - 1.0) < 1e-12, shm
    assert abs(port_kernel(i(W), [(i(E), 1.0)], table, 2000.0, 0.0) - math.exp(-dW / 2000.0)) < 1e-12
    # modes: LNG is shipped; Russian crude to China is shipped beyond the pipelines' capacity
    assert maritime_share("RUS", "CHN", "271111", 2019, 1e6, False) == 1.0
    assert maritime_share("QAT", "KWT", "271111", 2019, 1e6, False) == 1.0
    assert maritime_share("RUS", "CHN", "270900", 2019, 80e6, False) == 0.5, "30 Mt ESPO spur + 10 Mt via Kazakhstan"
    assert maritime_share("RUS", "CHN", "270900", 2012, 30e6, False) == 0.5, "15 Mt spur, no Kazakh transit yet"
    assert maritime_share("RUS", "CHN", "270900", 2019, 20e6, False) == 0.0
    assert maritime_share("RUS", "CHN", "270900", 2009, 20e6, False) is None, "before the spur: the pair flag"
    assert maritime_share("RUS", "CHN", "271000", 2019, 1e6, False) is None
    assert maritime_share("USA", "JPN", "270900", 2019, 1e6, True) is None
    assert maritime_share("RUS", "KAZ", "271111", 2019, 1e6, False) is None, "a landlocked buyer takes LNG by road or rail"
    print("routes.py selftest ok")


def main():
    args = sys.argv[1:]
    if "--selftest" in args:
        selftest()
        return
    root = ROOT
    if not args or args[0] not in ("routes", "energy", "portwatch"):
        raise SystemExit(__doc__)
    if args[0] == "portwatch":
        fetch_tonnes(root)
    elif args[0] == "routes":
        run_routes(root, OUT)
    else:
        run_energy(root, OUT)


if __name__ == "__main__":
    main()
