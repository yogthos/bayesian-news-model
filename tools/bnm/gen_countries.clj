(ns bnm.gen-countries
  "Builds resources/bnm/countries.edn from GeoNames countryInfo.txt (ISO and
  FIPS codes, names) and the UN M49 table (regions), and
  resources/bnm/provinces.edn from GeoNames admin1CodesASCII.txt and the
  ADM1 rows of allCountries.txt (first-level subdivisions and the names they
  go by), all under data/ref/.

    jolt -A:tools -M -m bnm.gen-countries"
  (:require [bnm.csv :as csv]
            [bnm.geo :as geo]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn slug [s]
  (-> s str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))

(defn name-key
  "The form a country name is matched in: lower case, letters and digits only."
  [s]
  (-> s str/lower-case (str/replace #"[^a-z0-9]+" " ") str/trim))

(defn geonames [path]
  (with-open [r (io/reader path)]
    (->> (line-seq r)
         (remove #(str/starts-with? % "#"))
         (map #(str/split % #"\t" -1))
         (mapv (fn [f] {:iso2 (f 0) :iso3 (f 1) :fips (f 3) :name (f 4)}))
         (filterv #(seq (:iso3 %))))))

;; M49 omits or leaves blank a few places the event data does name.
(def m49-patches
  {"TWN" {:region "Asia" :subregion "Eastern Asia"}
   "XKX" {:region "Europe" :subregion "Southern Europe"}
   "ANT" {:region "Americas" :subregion "Caribbean"}
   "SCG" {:region "Europe" :subregion "Southern Europe"}})

;; Event coders use these codes for places ISO calls something else.
(def code-aliases
  {"ROM" "ROU" "TMP" "TLS" "ZAR" "COD" "KSV" "XKX" "KOS" "XKX" "BUR" "MMR"
   "YUG" "SRB" "SCG" "SRB" "PSE" "PSE" "WBG" "PSE" "GZS" "PSE" "ZAI" "COD"
   "DDR" "DEU" "FRG" "DEU" "SUN" "RUS" "CSK" "CZE" "YMD" "YEM" "VDR" "VNM"
   "RVN" "VNM" "EAZ" "TZA"})

;; CAMEO actor codes for regions rather than countries, mapped onto our nodes.
(def cameo-regions
  {"AFR" "r/africa" "ASA" "r/asia" "EUR" "r/europe" "LAM" "r/americas"
   "NMR" "s/northern-america" "SAM" "s/south-america" "CRB" "s/caribbean"
   "CAM" "s/central-america" "MEA" "s/western-asia" "PGS" "s/western-asia"
   "NAF" "s/northern-africa" "WAF" "s/western-africa" "EAF" "s/eastern-africa"
   "CFR" "s/middle-africa" "SAF" "s/southern-africa" "SAS" "s/southern-asia"
   "SEA" "s/south-eastern-asia" "CAS" "s/central-asia" "EEU" "s/eastern-europe"
   "SCN" "s/northern-europe" "BLK" "s/southern-europe" "CAU" "s/western-asia"
   "WEU" "s/western-europe" "OCN" "r/oceania"})

;; Names the event datasets use that neither table spells the same way.
(def name-aliases
  {"russia" "RUS" "iran" "IRN" "syria" "SYR" "north korea" "PRK" "south korea" "KOR"
   "korea north" "PRK" "korea south" "KOR" "democratic people s republic of korea" "PRK"
   "republic of korea" "KOR" "vietnam" "VNM" "viet nam" "VNM" "laos" "LAO" "bolivia" "BOL"
   "venezuela" "VEN" "tanzania" "TZA" "moldova" "MDA" "taiwan" "TWN"
   "palestine" "PSE" "occupied palestinian territory" "PSE" "palestinian territory" "PSE"
   "state of palestine" "PSE" "gaza" "PSE" "west bank" "PSE"
   "democratic republic of congo" "COD" "democratic republic of the congo" "COD"
   "congo drc" "COD" "congo kinshasa" "COD" "drc" "COD" "dr congo" "COD" "zaire" "COD"
   "republic of congo" "COG" "republic of the congo" "COG" "congo brazzaville" "COG"
   "congo" "COG" "ivory coast" "CIV" "cote d ivoire" "CIV" "c te d ivoire" "CIV"
   "united states" "USA" "united states of america" "USA" "usa" "USA" "us" "USA"
   "united kingdom" "GBR" "uk" "GBR" "great britain" "GBR" "britain" "GBR"
   "czech republic" "CZE" "czechia" "CZE" "macedonia" "MKD" "north macedonia" "MKD"
   "swaziland" "SWZ" "eswatini" "SWZ" "burma" "MMR" "myanmar" "MMR"
   "east timor" "TLS" "timor leste" "TLS" "cape verde" "CPV" "kosovo" "XKX"
   "brunei" "BRN" "micronesia" "FSM" "vatican" "VAT" "holy see" "VAT"
   "bosnia and herzegovina" "BIH" "bosnia" "BIH" "turkey" "TUR" "turkiye" "TUR"
   "t rkiye" "TUR" "serbia and montenegro" "SRB" "yugoslavia" "SRB" "sudan" "SDN"
   "south sudan" "SSD" "gambia" "GMB" "the gambia" "GMB" "bahamas" "BHS" "the bahamas" "BHS"
   "saint kitts and nevis" "KNA" "saint lucia" "LCA" "saint vincent and the grenadines" "VCT"
   "sao tome and principe" "STP" "s o tom and pr ncipe" "STP" "curacao" "CUW"
   "netherlands antilles" "ANT" "reunion" "REU" "macau" "MAC" "macao" "MAC"
   "hong kong" "HKG" "falkland islands" "FLK" "western sahara" "ESH"
   "united arab emirates" "ARE" "uae" "ARE" "soviet union" "RUS" "ussr" "RUS"
   "east germany" "DEU" "west germany" "DEU" "south vietnam" "VNM"})

(defn build [geonames-path m49-path]
  (let [m49 (into {} (for [row (csv/read-maps m49-path \,)
                           :when (seq (:alpha-3 row))]
                       [(:alpha-3 row)
                        (let [inter (:intermediate-region row)]
                          {:m49-name (:name row)
                           :region (:region row)
                           ;; "Sub-Saharan Africa" and "Latin America" are too broad
                           ;; to stand for one place; M49's intermediate regions are not
                           :subregion (if (seq inter) inter (:sub-region row))})]))
        countries (->> (geonames geonames-path)
                       (keep (fn [{:keys [iso3] :as g}]
                               (let [m (merge (m49 iso3) (m49-patches iso3))]
                                 (when (seq (:region m))
                                   (merge (dissoc g :m49-name)
                                          {:region (str "r/" (slug (:region m)))
                                           :subregion (str "s/" (slug (:subregion m)))
                                           :names (vec (distinct (remove nil? [(:name g) (:m49-name m)])))})))))
                       (sort-by :iso3)
                       vec)
        subregions (into (sorted-map)
                         (for [c countries]
                           [(:subregion c) {:region (:region c)}]))
        regions (into (sorted-map) (for [c countries] [(:region c) {}]))
        by-name (merge (into {} (for [c countries n (:names c)] [(name-key n) (:iso3 c)]))
                       name-aliases)]
    {:countries countries
     :subregions subregions
     :regions regions
     :code-aliases code-aliases
     :cameo-regions cameo-regions
     :fips (into (sorted-map) (for [c countries :when (seq (:fips c))] [(:fips c) (:iso3 c)]))
     :names (into (sorted-map) by-name)}))

(defn provinces
  "From GeoNames admin1CodesASCII.txt, whose codes are FIPS 10-4 subdivision
  codes for most countries as GDELT's are, and the ADM1 rows of
  allCountries.txt cut to admin1-names.tsv (code, name, ASCII name, alternate
  names): {:names {iso3 {code name}} :keys {iso3 {match-key code}}}, where the
  match keys are every ASCII spelling, under `bnm.geo/province-key`, that
  names one province of its country and no other."
  [admin1-path alt-path countries]
  (let [iso3-of (into {} (for [c countries] [(:iso2 c) (:iso3 c)]))
        split-code (fn [code] (let [[iso2 adm] (str/split code #"\." 2)] [(iso3-of iso2) adm]))
        names (with-open [r (io/reader admin1-path)]
                (reduce (fn [acc line]
                          (let [[code nm ascii] (str/split line #"\t" -1)
                                [iso3 adm] (split-code code)]
                            (if iso3 (assoc-in acc [iso3 adm] (if (seq ascii) ascii nm)) acc)))
                        (sorted-map) (line-seq r)))
        spellings (with-open [r (io/reader alt-path)]
                    (doall
                     (for [line (line-seq r)
                           :let [[code nm ascii alts] (str/split line #"\t" -1)
                                 [iso3 adm] (split-code code)]
                           :when (get-in names [iso3 adm])
                           s (concat [nm ascii] (str/split (or alts "") #","))
                           :when (and (seq s) (re-matches #"[\x20-\x7e]+" s))
                           :let [k (geo/province-key s)]
                           :when (> (count k) 2)]
                       [iso3 k adm])))
        ;; and the names admin1CodesASCII gives, for provinces with no ADM1 row
        spellings (concat spellings (for [[iso3 m] names [adm nm] m] [iso3 (geo/province-key nm) adm]))
        codes-of (reduce (fn [acc [iso3 k adm]] (update-in acc [iso3 k] (fnil conj #{}) adm))
                         {} spellings)]
    {:names names
     :keys (into (sorted-map)
                 (for [[iso3 m] codes-of]
                   [iso3 (into (sorted-map) (for [[k adms] m :when (= 1 (count adms))] [k (first adms)]))]))}))

(defn -main [& _]
  (let [data (build "data/ref/countryInfo.txt" "data/ref/m49.csv")
        provs (provinces "data/ref/admin1CodesASCII.txt" "data/ref/admin1-names.tsv" (:countries data))]
    (with-open [w (io/writer "resources/bnm/provinces.edn")]
      (binding [*out* w]
        (println ";; Generated by bnm.gen-countries from GeoNames admin1 codes. Do not edit.")
        (pp/pprint provs)))
    (println (reduce + (map count (vals (:names provs)))) "provinces,"
             (reduce + (map count (vals (:keys provs)))) "spellings")
    (with-open [w (io/writer "resources/bnm/countries.edn")]
      (binding [*out* w]
        (println ";; Generated by bnm.gen-countries from GeoNames and UN M49. Do not edit.")
        (pp/pprint data)))
    (println (count (:countries data)) "countries,"
             (count (:subregions data)) "subregions,"
             (count (:regions data)) "regions")))
