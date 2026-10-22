# Economic character and the international division of labour (research and design, October 2026)

Epic bnm-3ka. The starting point is Julius Krein, "The Value of Nothing:
Capital versus Growth" (American Affairs 5(3), Fall 2021,
https://americanaffairsjournal.org/2021/08/the-value-of-nothing-capital-versus-growth/).
Its claims are taken as hypotheses to test, not as structure to build in
(docs/PRINCIPLES.md 2, 5):

- K1, capital over growth. The US economy is organised to maximise asset
  values and returns on capital, independent of growth: buybacks, hurdle
  rates near 15% far above the cost of capital, and asset-light firms that
  keep the IP rents and outsource production ("Nikefication", G. Davis).
- K2, migration. Capital-intensive production fails those hurdle rates and
  moves to East Asia, where state-directed or growth-oriented firms accept
  lower returns (Pettis: capital is the cheapest input in China and the
  dearest in the US; Milanović: political against liberal-meritocratic
  capitalism).
- K3, complementarity. The two systems need each other. Offshoring props
  up valuations, and trade and capital imbalances are the cost.
- K4, strategic regret. "Commodity" sectors given up on financial grounds
  (telecom equipment, fabs, machine tools, pharmaceutical precursors) turn
  out to be strategically essential.
- K5, divergence. Equity values came apart from growth and earnings
  (Greenwald, Lettau & Ludvigson: about 40% of the 1989-2017 rise in equity
  value came from a reallocation of rents away from labour).

The project adds two things. It tracks the economy's character on A5 cells
(principle 11), next to the located resources and processing plants, where
a correlation is expected. It also asks the geopolitical and development
question: who rises, who falls, and who fights over it.

This document covers the literature, what is on disk, exploratory numbers,
a design and ranked steps. No project code has been written. The
exploratory scripts are outside the repository (the session scratchpad,
econ/: spine_shares.py, baci_cap.py and baci_show.py, displacement.py,
regime.py, divergence.py, wid_beta.py, cinc.py, xsec.py).

## Summary

- **The US and UK spine shows the pattern, but so does everyone rich.**
  Manufacturing's share of value added fell from 24% to 10% in the US and
  from 33% to 9% in Britain (1965-2020). It also fell in France (22% to
  10%) and Japan (28% to 19%). Germany (30% to 18%) and Korea (peak 24%,
  then 18%) fell less. Finance's share in the US and UK rose within each
  source. Between sources, the long-run WIOD's US finance share is about
  half of BEA's (the 1999/2000 join is flagged as a break at a ratio of
  1.63), so levels across 2000 are not comparable. Of the fixed 25
  spine countries' manufacturing value added, the US share went from 47%
  to 22% and China's from 4% to 34%. Japan lost more than the US after
  1995 (24% to 9%).
- **Cross-country, the size of the finance sector does not predict
  deindustrialisation.** Within WIOD 2016 (43 countries, 2000-2014), the
  change in the manufacturing share regressed on the 2000 finance share,
  log income and the initial manufacturing share gives -0.08 (t -1.3). The
  heavy-industry version (chemicals, metals, machinery and transport
  equipment) gives -0.084 (t -2.0), but Luxembourg alone produces it.
  Without Luxembourg the estimates are -0.006 (t 0.0) and -0.11 (t -0.9).
  Income carries what signal there is (Rodrik). Finance's value-added
  share is the wrong measure of K1. Financialisation is a behaviour of
  non-financial firms (payouts, portfolio income, hurdle rates), not the
  size of the banking sector. The index needs those measures (below).
- **Export shares in capital-intensive goods moved to China, Korea and
  Taiwan, from Germany and Japan as much as from the US and UK.** BACI
  HS92, share of world export value, 1995 to 2024 (US+UK; CN+KR+TW;
  DE+JP):

      telephone line equipment 8517      22 -> 3;   11 -> 65;  23 -> 3
      integrated circuits 8542           26 -> 5;   17 -> 58;  24 -> 7
      machine tools 8456-8463            15 -> 6;    9 -> 29;  44 -> 32
      antibiotics 2941                   20 -> 5;   11 -> 48;  12 -> 2
      heterocyclic compounds 2933        18 -> 5;    3 -> 28;  20 -> 5
      hot-rolled steel 7208               7 -> 3;   13 -> 40;  17 -> 18
      cargo ships 8901                    1 -> 1;   19 -> 61;  47 -> 14
      generating sets 8502               38 -> 14;   1 -> 33;  18 -> 15
      turbojets, gas turbines 8411       52 -> 45;   2 -> 4;    8 -> 8
      aircraft 8802                      40 -> 26;   0 -> 2;    7 -> 15
      medicaments, dosed 3004            19 -> 12;   1 -> 3;   16 -> 15

  This is K4's list: telecom equipment, chips, machine tools and
  pharmaceutical precursors. The sectors that stayed in the US are the
  high-margin, oligopolistic, IP-heavy ones (jet engines, aircraft, dosed
  medicines). That fits "keep the rents, shed the capital". But Japan and
  Germany lost as much in chips, ships and telecom equipment, and they are
  not financialised in Krein's sense. So migration alone does not identify
  financialisation. Any test of K2 needs the non-financialised rich
  countries as the control.
- **Processing moved away from the resource, and toward China.** The
  dissimilarity D between mine and processing country shares (0 means each
  country processes its own ore; 1 means none does), from BGS 1975-2023:
  cobalt 0.36 (1975; 0.21 before BGS's 1972-75 ore-content rows were repaired), 0.43 (2000), 0.90 (2023), with China's refined share
  going from 0 to 80% while its mine share never passed 7%; copper refining 0.31 to
  0.55, China 3% to 47% (mine 8%); alumina from bauxite 0.39 to 0.48 and
  aluminium 0.76 to 0.69 (China 2% to 59%); tin 0.23 to 0.49; nickel 0.32
  to 0.46 (2010), then back to 0.37 as Indonesia's export ban (2014, 2020)
  pulled smelting home (Indonesia 41% of processing in 2023). Steel from
  iron ore stays near 0.5. China mines much of its own ore (low grade),
  and the displacement there is Japan's and Korea's, not new. The one
  reversal (nickel) is a resource-nationalist policy, which is the
  development story the cell layer should see.
- **The naive regime cluster finds income, not Krein's axis.** k-means on
  1995-2019 means for 69 countries (investment share, PWT's return on
  capital, labour share, fixed investment, current account, private credit,
  finance and manufacturing shares) puts the US, UK, China, Korea, Germany
  and Japan in one cluster. The other two clusters are the offshore centres
  (Hong Kong, Luxembourg, Singapore) and middle-income commodity and Latin
  American economies. PC1 (32%) is capital deepening plus credit. Learning
  a regime needs the features that separate the poles: public against
  private wealth, the state's share of investment, payouts against
  investment. The first is already on disk.
- **Public wealth is where the systems differ; asset inflation is
  everywhere.** WID's private wealth to national income rose in every
  economy from 1980 to 2020: US 3.3 to 6.3, UK 3.0 to 6.1, China 0.9 to
  6.4, Korea 3.2 to 6.2, Germany 3.4 to 6.5. Net public wealth is what
  separates them: US -0.36 and UK -0.63 of national income in 2020, against
  China +3.0 and Korea +2.7 (Germany +0.4, Japan +0.4). That is
  Milanović's distinction in one number. K5's divergence is not specific to
  financialised economies.
- **The US equity divergence is real and dates from 1989.** Shiller: from
  1960 to 1989, real prices rose 0.6% a year against real earnings at 1.9%.
  From 1989 to 2017, prices rose 4.9% a year against earnings at 2.5%, and
  PE10 went from 15 to 28. PWT's US labour share fell from 0.65 (1970) to
  0.59 (2019). PWT's realised return on capital is not lower in China (8 to
  11%) than in the US (8%). Pettis's claim is about the cost of capital to
  favoured borrowers (financial repression), not average returns.
  Krein's 15% hurdle against an economy-wide 8% is consistent with that.
- **Material capability crossed before output.** COW CINC: China passed
  the US around 1995 (16.2% against 14.3% in 2000; 23.1% against 13.3% in
  2016). It holds half the world's steel (49.6%, US 4.8%), while US military
  spending is still 40% of the world's. CINC over-weights population and
  steel (Beckley 2018), but the production-against-finance gap the
  geopolitical hypotheses need is there in the data.
- **Power.** Most of these claims are about a handful of countries (the US,
  UK, China, Korea, Taiwan, Germany, Japan) and one or two transitions. The
  power audit (docs/PHASE-CHANGES.md) finds that the event lattice detects
  OR 1.30 per SD at best for a country state, and 1.77 for a world series.
  A financialisation stock tested against crashes or banking crises on
  some 40 countries with the data would need OR around 2. So the design
  leans on attribution and description: shift-share, product panels with
  thousands of rows, cell panels with country-year fixed effects. Event
  tests are run only where the units are many.

## 1. Measuring financialisation by country and year

### Literature

- Krippner, G. (2005). "The financialization of the American economy."
  Socio-Economic Review 3(2): 173-208.
  https://doi.org/10.1093/SER/mwi008. Krippner (2011), Capitalizing on
  Crisis, Harvard UP. She defines financialisation as profits accruing
  through financial channels rather than trade and production. Two
  measures: finance's share of corporate profits (FIRE against
  manufacturing, BEA), and non-financial firms' portfolio income (interest,
  dividends and capital gains) against their cash flow. Both are US-only
  from BEA and the IRS Corporation Source Book.
- Epstein, G. (ed.) (2005). Financialization and the World Economy. Edward
  Elgar. https://doi.org/10.4337/9781845426255. Defines financialisation
  as "the increasing role of financial motives, financial markets, financial
  actors and financial institutions". In it, Epstein & Jayadev measure the
  rentier share: the income of the financial sector plus interest income
  of non-financial owners, over national income, for OECD countries from
  1960. National accounts can rebuild this from property income (D4) by
  sector.
- Stockhammer, E. (2004). "Financialisation and the slowdown of
  accumulation." Cambridge Journal of Economics 28(5): 719-741.
  https://doi.org/10.1093/cje/beh032. Shareholder value as a change in
  management's objective. Non-financial firms' interest and dividend
  income (rentier income) lowers accumulation in the US, UK and France
  (1960s-1990s time series; for Germany the result is weak).
- Orhangazi, Ö. (2008). "Financialisation and capital accumulation in the
  non-financial corporate sector." Cambridge Journal of Economics 32(6):
  863-886. https://doi.org/10.1093/cje/ben009. Firm panel (Compustat,
  1973-2003): financial payments (interest, dividends, buybacks) and
  financial income both lower investment, most in large firms.
- Lazonick, W. (2014). "Profits without prosperity." Harvard Business
  Review 92(9): 46-55.
  https://hbr.org/2014/09/profits-without-prosperity. The S&P 500 paid out
  91% of net income in 2003-2012 (54% buybacks, 37% dividends). "Retain
  and reinvest" became "downsize and distribute". Compustat-based.
- Davis, G. F. (2016). The Vanishing American Corporation. Berrett-Koehler.
  Source of "Nikefication": asset-light firms that design and market and
  contract out production. Davis, L. E. (2016). "Identifying the
  'financialization' of the nonfinancial corporation in the U.S. economy."
  Journal of Post Keynesian Economics 39(1): 115-141.
  https://doi.org/10.1080/01603477.2016.1136564. Balance-sheet
  decomposition (financial assets, shareholder payouts) by firm size.
- Svirydzenka, K. (2016). "Introducing a New Broad-based Index of
  Financial Development." IMF WP/16/5.
  https://www.imf.org/external/pubs/ft/wp/2016/wp1605.pdf. Depth, access
  and efficiency of institutions and markets, 183 countries, 1980-. It
  measures development, not financialisation: deep finance and
  shareholder-value governance are different things. It is useful as a
  control so that financialisation is not just "rich".
- Philippon, T. (2015). "Has the US Finance Industry Become Less
  Efficient?" American Economic Review 105(4): 1408-1438.
  https://doi.org/10.1257/aer.20120578. The unit cost of intermediation
  (finance income over intermediated assets) stayed near 2% for 130 years.
  Finance's share of GDP grew with the quantity of intermediation, not
  efficiency. Bazot (2018, JEEA) extends the measure to Europe.
- Greenwald, D., Lettau, M. & Ludvigson, S. (2025). "How the Wealth Was
  Won: Factor Shares as Market Fundamentals." Journal of Political Economy
  133(4): 1083-1132. NBER WP 25769 (2019):
  https://www.nber.org/papers/w25769. Equity values in a model with
  shocks to the factor share, discount rates and growth. Of the 1989-2017
  rise in real per-capita equity value, about 40% came from the factor
  share shifting to shareholders (earlier drafts say 43-44%, the
  non-financial-sector version 54%), about 25% from growth, and the rest
  from lower rates and risk prices. From 1952 to 1988 growth explains more
  than all of it. US only. The method (a state-space decomposition) is
  portable to any country with equity values, earnings and the labour
  share.
- Also: Duménil & Lévy (2004), Capital Resurgent; Lapavitsas (2013),
  Profiting Without Producing; Van der Zwan (2014, Socio-Economic Review
  12(1)) for a survey; Karwowski, Shabani & Stockhammer (2017,
  Competition & Change) for an empirical cross-country taxonomy (household,
  NFC and asset-price financialisation are different things in different
  countries).

### What is open, for many countries, since 1960-1990

| measure | concept (whose) | coverage | source | open | on disk |
|---|---|---|---|---|---|
| finance VA share | size of finance (Philippon, Krippner) | 25 countries 1965-, 43 2000-14, ~76 2015-20 | I-O spine | yes | yes |
| finance VA share, detail | same | OECD ~40 1970-; UN ~200 1990- (ISIC K) | OECD SNA Table 6A; UNdata official country data | yes | no |
| market cap / GDP; value traded / GDP | asset-market size | ~100 countries 1975/1988- | World Bank GFDD; WDI CM.MKT.LCAP.GD.ZS | yes (CC BY 4.0) | no |
| private credit / GDP | depth; debt (Keen) | 40+ countries 1940-; 190 1960- | BIS total credit; WDI FS.AST.PRVT; Müller-Verner | yes | yes |
| household and NFC debt | debt-led growth | 15-174 countries 1950- | IMF GDD | yes | yes |
| NFC net lending, dividends paid (D42), property income received (D4) | payouts against investment; rentier income (Epstein-Jayadev, Stockhammer) | ~35 OECD 1995- (some 1970-) | OECD Table 720 non-financial sector accounts; Eurostat nasa_10_nf_tr | yes | no |
| payout ratio (dividends + buybacks over earnings) | Lazonick | firm level | Worldscope or Compustat Global (paid). Open proxies: D42/B2 above; US Fed Z.1 net equity issuance (FRED NCBCEBQ027S) | partial | no |
| profit and labour share; profit rate | distribution (GLL; Keen) | 183 countries 1950-2019 | PWT 11 labsh; EPWT 7 r, LabShare | yes | yes |
| investment share | accumulation (Stockhammer) | 180 countries 1950- | PWT csh_i; GMD finv_GDP | yes | yes |
| realised return on capital | the "capital is dear" side | 180 countries | PWT irr | yes | yes |
| equity returns, dividend yield, house prices | asset values | 18 countries 1870-2020 | JST R6 | yes | yes |
| private and public wealth / national income | asset values against output | ~40 countries 1970- (US, UK, FR, DE, JP 1870/1950-) | WID wpweali999, wgweali999, wnweali999 | yes | yes (full zip) |
| CAPE | valuation | US 1871- | Shiller | yes | yes |
| CAPE, other countries | valuation | 26 markets 1979/1982- | Barclays/MSCI/Star Capital (paid); open: JST dividend yields, WID wealth ratios | no | no |
| financial development index | control | 183 countries 1980- | IMF FD index (Svirydzenka) | yes (IMF site blocks scripts: manual) | no |
| external assets and liabilities | coupling (K3) | 210 countries 1970-2022 | Lane & Milesi-Ferretti External Wealth of Nations | yes | no |

What this gives: a country-year financialisation stock can be built from
open data for about 35-40 countries from 1995 (sector accounts), about 18
from 1870 (JST), and 40 from 1970 (WID). The NFC measures (payouts,
portfolio income) are the ones that match K1. They are thin before 1995
outside the US.

## 2. Production migration and deindustrialisation

### Literature

- Rodrik, D. (2016). "Premature deindustrialization." Journal of Economic
  Growth 21(1): 1-33. https://doi.org/10.1007/s10887-015-9122-3. The
  manufacturing employment share peaks at lower incomes and lower levels
  for late developers. Deindustrialisation is mostly a function of income
  and trade, the baseline any financialisation effect must beat.
- Baldwin, R. (2016). The Great Convergence: Information Technology and
  the New Globalization. Belknap/Harvard UP. ICT lowered the cost of moving
  ideas, so G7 firms unbundled production and sent stages to nearby
  low-wage countries ("Factory Asia"), with their know-how. The G7 share
  of world manufacturing fell from about two-thirds (1990) to under half.
  This is a rival to K2: migration driven by coordination costs, not
  hurdle rates.
- Autor, D., Dorn, D. & Hanson, G. (2013). "The China Syndrome." American
  Economic Review 103(6): 2121-2168. https://doi.org/10.1257/aer.103.6.2121;
  (2016) "The China Shock", Annual Review of Economics 8: 205-240. Import
  exposure by US commuting zone, instrumented by other rich countries'
  imports from China. The instrument is the design for the cell layer:
  local exposure equals local industry mix times national import growth.
- Gutiérrez, G. & Philippon, T. (2017). "Investmentless Growth: An
  Empirical Investigation." Brookings Papers on Economic Activity, Fall:
  89-169. https://www.brookings.edu/bpea-articles/investmentless-growth-an-empirical-investigation/.
  US investment is weak against Tobin's q since 2000. Concentration and
  short-termist ownership (quasi-indexers) explain it, not intangibles or
  financial constraints. That is K1 by firm data.
- Schwartz, H. M. (2019). "American hegemony: intellectual property rights,
  dollar centrality, and infrastructural power." Review of International
  Political Economy 26(3): 490-519.
  https://doi.org/10.1080/09692290.2019.1597754; and (2022) "Global
  secular stagnation and the rise of intellectual property monopoly", RIPE
  29(5): 1448-1476. US firms that control IP capture a disproportionate
  share of global profits in global commodity chains. Those profits hold up
  valuations of dollar assets. Surplus economies suppress domestic demand
  and recycle dollars. This is K3 stated as a mechanism.
- Pettis, M. & Klein, M. C. (2020). Trade Wars Are Class Wars. Yale UP.
  Surpluses come from suppressed household income shares in China and
  Germany (wages, transfers, financial repression). Deficits are the
  counterpart, absorbed by economies with open capital accounts (the US).
  Capital inflows push up asset prices and debt, not investment.
- Also: Felipe, Mehta & Rhee (2019, Cambridge J. Econ.) on
  manufacturing's share of employment; Fort, Pierce & Schott (2018, JEP)
  on US manufacturing jobs; Pisano & Shih (2009, HBR, "Restoring American
  competitiveness") on the "industrial commons" lost with outsourcing, the
  K4 mechanism.

### How to measure world output shares by product and capital intensity

- **EXIOBASE (1995-2022; we hold 1995, 2011 and 2022 pxp, and the 2011
  hybrid).** Gross output by country and product in money (pxp) and, for
  2011, in tonnes. Capital intensity per product comes from its value
  added split into compensation and operating surplus, and from the
  capital extension (EXIOBASE 3.8 has none; KLEMS gives capital stocks by
  industry). Output shares by product for 48 regions, Taiwan inside "Rest
  of Asia". The essentials plan (docs/ESSENTIALS.md) already proposes five
  more years.
- **BACI (1995-2024, on disk).** Export value and tonnes by exporter and
  HS6, about 5,000 products. Exports are not output, but for traded
  manufactures the world export share is a fair proxy, and BACI has the
  most products and years. Taiwan is code 490 ("Other Asia, nes", S19),
  which must be mapped by hand.
- **USGS MYB and BGS (on disk).** Physical output of materials by stage
  (mine, smelter, refinery), 1970-2024 (BGS) and 1990-2022 (MYB). These
  give processing shares in tonnes, free of price.
- **UNIDO INDSTAT.** Value added, output, employment and investment by
  ISIC 2- to 4-digit, 166 countries, 1963-. Under an open data policy since
  2022 (https://stat.unido.org/), downloaded by selection from the portal.
  Free; the licence terms need checking. It fills the spine's gaps (no
  China detail before 1995 outside LR-WIOD's back-cast; no Taiwan, Vietnam
  or Indonesia before 2000).
- **The spine (on disk).** Twenty-one sectors, value added and gross
  output, 1965-2020, current USD. Use within source; the joins at 2000 and
  2015 carry break flags.
- **Capital intensity.** Rank products by capital per worker or capital
  over value added in a reference country, not the exporter's own (the
  exporter's is endogenous): the NBER-CES Manufacturing Database (US,
  6-digit NAICS, 1958-2018, open) and EU KLEMS (capital stocks by
  industry). Concord to HS6 with the UNSD/WITS HS-ISIC tables. Upstreamness
  (Antràs et al. 2012) from the ICIO tables is a second axis: processing
  stages sit upstream.

## 3. Investment regime and state direction

- Milanović, B. (2019). Capitalism, Alone. Belknap/Harvard UP. Liberal
  meritocratic capitalism (the US) against political capitalism (China,
  Vietnam, Singapore). In the second, a technocratic bureaucracy with
  discretionary law directs capital, and growth is its legitimacy. As
  labels these are features to learn, not to assign: an efficient
  bureaucracy, weak rule of law, autonomy of the state.
- Developmental state: Amsden, A. (1989), Asia's Next Giant: South Korea
  and Late Industrialization, Oxford UP (subsidies traded for performance
  standards, "getting prices wrong"); Wade, R. (1990), Governing the
  Market, Princeton UP (Taiwan: leading the market); Chang, H.-J. (2002),
  Kicking Away the Ladder, Anthem (today's rich countries industrialised
  with protection and directed credit); Johnson, C. (1982), MITI and the
  Japanese Miracle, Stanford UP.
- Measures:
  - investment/GDP: PWT csh_i, GMD finv_GDP (on disk);
  - public against private capital: IMF Investment and Capital Stock
    Dataset (ICSD, 2021 release, about 170 countries 1960-2019; general
    government, private and PPP investment and capital stocks; open from
    data.imf.org and the IMF infrastructure governance site, IMF pages
    refuse scripts, so manual). https://data.imf.org/en/datasets/IMF.FAD:ICSD;
  - net public wealth / national income: WID (on disk). It reads public
    land, SOE equity and debt together: China +3.0, US -0.36 in 2020;
  - the SOE share of investment or output: China's NBS yearbooks (state
    holding share of fixed asset investment, by province). For other
    countries, the IMF Fiscal Monitor 2020 SOE chapter and the OECD SOE
    database (snapshots, not panels). No open cross-country panel exists;
  - the cost of capital against its return: PWT irr (realised return);
    lending and deposit rates (WDI FR.INR.LEND, FR.INR.DPST) and the real
    deposit rate as financial repression (Lardy 2008; Pettis); the
    capital account (Chinn-Ito kaopen, on disk);
  - directed credit: Müller-Verner sectoral credit (on disk): the share of
    credit to manufacturing and construction against households and real
    estate. Their finding (Müller & Verner 2024, ReStud), that household
    and non-tradable credit predicts busts while tradable-sector credit
    does not, is itself a regime contrast.
- **Can a regime be learned?** Yes, but not from the obvious features
  (the k-means above found income). The design (section 7a) learns a
  latent factor model on the features that separate the poles: public
  against private wealth, the state's share of investment, payouts against
  investment, deposit rates against growth, the current account, and the
  household consumption share (Pettis). It is identified by sign
  restrictions stated as priors, not by labelling countries. The labels
  (Milanović's, Hall & Soskice's varieties of capitalism) are kept for
  validation only.

## 4. Coupling and imbalances

- Bernanke, B. (2005). "The global saving glut and the U.S. current account
  deficit." Sandridge Lecture.
  https://www.federalreserve.gov/boarddocs/speeches/2005/200503102/.
  Surplus countries' saving, through reserves, finances US deficits and
  lowers US yields.
- Gourinchas, P.-O. & Rey, H. (2007). "From World Banker to World Venture
  Capitalist: US External Adjustment and the Exorbitant Privilege." In
  Clarida (ed.), G7 Current Account Imbalances, NBER/U. Chicago Press.
  https://www.nber.org/papers/w11563. The US borrows safe and invests
  risky, earning an excess return of about 2-3% a year on its external
  position (the exorbitant privilege).
- Borio, C. & Disyatat, P. (2011). "Global imbalances and the financial
  crisis: Link or no link?" BIS WP 346. https://www.bis.org/publ/work346.htm.
  Gross flows (European banks) mattered for 2008 more than net
  imbalances: test gross as well as net.
- Obstfeld, M. & Rogoff, K. (2009). "Global Imbalances and the Financial
  Crisis: Products of Common Causes." Asia Economic Policy Conference.
- Klein & Pettis (2020), Schwartz (2019) above: the class and IP versions
  of the coupling.
- Data:
  - bilateral trade: BACI (on disk);
  - bilateral portfolio holdings: IMF CPIS (2001-, semi-annual since
    2013), open from data.imf.org;
  - direct investment positions: IMF CDIS (2009-), open;
  - external positions: Lane & Milesi-Ferretti, External Wealth of
    Nations (1970-2022), open (Brookings/IMF);
  - cross-border bank claims: BIS LBS (on disk, 17.9 GB zipped);
  - TIC data: US Treasury holdings by country (open; China's custodial
    holdings sit in Belgium and the UK, a known bias).
  CPIS has known gaps: China reports little, and offshore centres hide
  ultimate owners (Coppola, Maggiori, Neiman & Schreger 2021, QJE
  "Redrawing the Map of Global Capital Flows", restate by nationality;
  their data are at https://globalcapitalallocation.com).

## 5. The geopolitical dimension

- Arrighi, G. (1994). The Long Twentieth Century. Verso. Systemic cycles
  of accumulation (Genoese, Dutch, British, American): each ends in a
  financial expansion (M-C-M' gives way to M-M') when material expansion
  hits its limits. The "autumn" of a hegemon, in which a rising producer
  holds surplus and the declining hegemon holds finance. That is
  K1-K3 in the long run, and it predicts the financial expansion before
  the transfer of hegemony.
- Kennedy, P. (1987). The Rise and Fall of the Great Powers. Random House.
  Relative economic (productive) strength leads military power, with a
  lag. Imperial overstretch: commitments outrun the productive base.
- Power transition: Organski, A.F.K. (1958), World Politics; Organski &
  Kugler (1980), The War Ledger, U. Chicago Press; Tammen et al. (2000),
  Power Transitions, Chatham House. War is likeliest when a dissatisfied
  challenger reaches parity with the dominant power. Tests: Kim (1992,
  World Politics), Lemke & Werner (1996, ISQ); de Soysa, Oneal & Park
  (1997, JCR) find parity matters, though the challenger's
  dissatisfaction is hard to measure. Lemke & Reed (1998) and Lemke (2002)
  on regional hierarchies.
- Capability measures: COW National Material Capabilities 6.0 (CINC: iron
  and steel, primary energy, military spending and personnel, total and
  urban population; 1816-2016; Singer, Bremer & Stuckey 1972; on disk).
  Beckley, M. (2018). "The Power of Nations: Measuring What Matters."
  International Security 43(2): 7-44. Gross measures overstate populous
  poor countries. He proposes GDP x GDP per capita (net resources).
  Financial power has no standard measure. Candidates: currency share of
  reserves (IMF COFER), of SWIFT payments and of international debt (BIS);
  the net international investment position; the external excess return.
- Tests linking a production-finance gap to tension: none we know of as a
  country-year panel. Nearby: trade dependence and MIDs (Oneal & Russett
  1997 and the liberal-peace panels; Barbieri 1996 for the opposite);
  economic sanctions in the Global Sanctions Data Base (Felbermayr et al.
  2020, European Economic Review; 1950-2023; free after a request form);
  export restrictions in the OECD inventory (on disk, 2009-) and Global
  Trade Alert (2008-, free with terms); interstate events in
  ICEWS/GDELT/POLECAT (on disk); and dyadic MIDs (COW, on disk to 2014).
  The great-power transitions are a handful (Britain to the US, the
  US-USSR rivalry, the US-China relation). So any test on great powers is
  descriptive or attribution. A dyad-year test on all pairs (the
  production-finance gap between a pair against their export restrictions
  and sanctions) has the units for power, but it measures a general
  disposition, not the US-China case.

## 6. Cells

### Open gridded economic data

| data | what | years | resolution | licence | notes |
|---|---|---|---|---|---|
| Kummu, Taka & Guillaume (2018), Sci. Data 5:180004, https://doi.org/10.1038/sdata.2018.4 | GDP per capita PPP, HDI, GDP | 1990-2015 | 5 arcmin (GDP pc), admin-1 for HDI | CC BY 4.0 (Dryad) | subnational where reported, national elsewhere |
| Kummu, Kosonen & Masoumzadeh Sayyar (2025), Sci. Data 12:178, https://doi.org/10.1038/s41597-025-04487-x | GDP per capita PPP, admin-2 downscaled (43,501 units), raster | 1990-2022 | admin-2 and 5 arcmin | CC BY 4.0 (Zenodo 10.5281/zenodo.10976733) | machine-learned downscaling (test R² 0.80); uses night lights, so not independent of them |
| Li, Zhou, Zhao & Zhao (2020), Sci. Data 7:168, https://doi.org/10.1038/s41597-020-0510-y | harmonised DMSP + VIIRS night lights | 1992-2018 (extended versions to 2023 on figshare) | 30 arcsec | CC BY 4.0 | the DMSP-VIIRS join is a calibrated break |
| GHSL (Pesaresi et al., JRC, R2023A), https://ghsl.jrc.ec.europa.eu | built-up surface (residential and non-residential), built volume, population, degree of urbanisation | 1975-2030, five-year epochs | 100 m - 1 km | CC BY 4.0 | the non-residential built-up layer is the closest open proxy for industrial and commercial land |
| CIESIN Global Gridded Relative Deprivation Index v1 (2022), SEDAC | relative deprivation (child dependency, infant mortality, subnational HDI, night lights, built-up) | about 2010-2020 composite | 30 arcsec | CC BY 4.0 | Earthdata login; one period |
| Nordhaus G-Econ 4.0 (Nordhaus 2006, PNAS 103(10): 3510-3517) | gross cell product | 1990, 1995, 2000, 2005 | 1° | open (Yale) | coarse; built from subnational accounts |
| EDGAR v8 (JRC; Crippa et al.) | CO2 and pollutants by sector (energy, industrial combustion, iron and steel, chemicals, non-metallic minerals, refineries, transport) | 1970-2022 annual | 0.1° | CC BY 4.0 | proxies: industry located from plant inventories and population, so it partly re-reads our own plants |
| Gridded manufacturing | none global. Nearest: EDGAR's industry sectors, GHSL non-residential, the SFI and GEM plant databases, and national gridded or county data (US County Business Patterns, China firm censuses, EU ARDECO NUTS3 GVA by sector, 1980-) | | | | ARDECO gives EU NUTS3 GVA by six sectors: a validation set |

Night lights track GDP where statistics are poor (Henderson, Storeygard
& Weil 2012, AER 102(2); Chen & Nordhaus 2011, PNAS) and expose inflated
GDP in autocracies (Martinez 2022, JPE 130(10)). That matters here,
because the state-directed pole includes the countries whose statistics
are doubted.

### Profiling a cell

Each cell gets measured stocks, not a label:
- extraction: mining area (Maus 2022, Tang & Werner 2023), mines and
  deposits by commodity (USGS MYB facilities 2024, MRDS), oil and gas fields
  (OGIM, PETRODATA), cropland and suitability (GAEZ);
- processing: smelters and refineries (MYB facilities), oil refineries and
  LNG (OGIM, EIA, Wikipedia list), steel, cement and ethylene with start
  years (SFI, GEM), power plants (GPPD);
- manufacturing: EDGAR industrial combustion and process emissions per
  year, GHSL non-residential built-up net of the processing plants'
  footprints;
- services and density: population and built volume (GHSL), night lights
  per person, gridded GDP per capita;
- development: GDP per capita, HDI (where reported), relative
  deprivation, light growth.
The character is a learned mixture: non-negative matrix factorisation of
the cells' standardised stocks into a few archetypes, whose loadings are
read afterwards (likely extraction, processing, manufacturing, metropolitan
services, agrarian). The check is accounting (principle 8): the cells'
archetype weights, aggregated to countries through the crosswalk, should
reproduce each country's spine sector mix in value added. Where they do
not, the weights are what is wrong. The EU's NUTS3 sector GVA (ARDECO) and
US county data are held-out validation.

### Resource location against processing location

- Gereffi, G. (1999). "International trade and industrial upgrading in the
  apparel commodity chain." Journal of International Economics 48(1):
  37-70. Gereffi, Humphrey & Sturgeon (2005). "The governance of global
  value chains." Review of International Political Economy 12(1): 78-104.
  Upgrading: process, product, functional, chain. For resources, it means
  moving from ore to concentrate to metal to fabrication. Humphrey & Schmitz
  (2002, Regional Studies) find lead firms block functional upgrading.
- Resource-based industrialisation: Auty, R. (1990), Resource-Based
  Industrialization: Sowing the Oil in Eight Developing Countries, Oxford
  UP (state-led processing projects mostly lost money); Owens & Wood
  (1997, World Development 25(9)): land-abundant countries can
  industrialise through processing primary products, but only once skills
  pass a threshold; Morris, Kaplinsky & Kaplan (2012, Resources Policy),
  "One thing leads to another": linkages from commodities.
- Dutch disease and the resource curse: Corden & Neary (1982, Economic
  Journal 92(368): 825-848); Sachs & Warner (2001, European Economic Review);
  van der Ploeg (2011, Journal of Economic Literature 49(2)). Export bans to
  force processing: Indonesia's nickel ore ban (2014, 2020) is the clean
  case. Our BGS numbers show it reversing the displacement.
- Hausmann, Hwang & Rodrik (2007, Journal of Economic Growth 12(1)), "What
  you export matters"; Hidalgo & Hausmann (2009, PNAS 106(26)), economic
  complexity: what a place can make next depends on what it makes. On
  cells, this is the development path.

## 7. Exploratory numbers

Scripts are in the session scratchpad's econ/ directory, run with the
scratchpad venv and -I. Everything reads data/raw and data/derived.

### 7a. The I-O spine (spine_shares.py)

Value added shares, %; the 2000 and 2015 rows start a new source:

    finance (FIN)   1965  1980  1990  1995 | 2000  2005  2014 | 2015  2020
      USA            1.9   2.3   2.9   3.5 |  7.3   7.6   7.0 |  7.7   8.6
      GBR            3.8   4.8   5.4   6.1 |  5.6   8.0   8.2 |  8.1   8.8
      DEU            3.3   4.3   4.7   4.6 |  4.4   5.3   4.1 |  4.4   4.0
      JPN            4.5   5.2   5.9   6.0 |  5.7   6.2   4.7 |  4.3   4.3
      KOR            2.0   5.8   5.8   6.9 |  5.7   6.5   5.6 |  5.8   5.7
      CHN            5.7   1.6   5.5   4.6 |  4.1   3.3   6.1 |  8.2   7.7
    manufacturing (the twelve spine sectors, ELEC included)
      USA           26.7  21.4  18.4  17.6 | 16.4  14.5  13.4 | 13.2  12.3
      GBR           36.5  26.8  22.8  21.3 | 16.9  12.7  11.3 | 11.1  10.3
      DEU           34.9  29.7  28.1  22.6 | 23.6  23.0  23.2 | 23.2  21.1
      JPN           31.3  28.2  26.8  23.3 | 21.6  21.1  19.2 | 22.0  21.2
      KOR           15.5  24.4  27.3  27.6 | 29.7  29.0  31.1 | 29.5  26.9
      CHN           24.8  34.7  30.9  34.1 | 32.2  32.5  29.6 | 29.1  28.1

The US and UK finance shares rise within each source. Germany's and
Japan's do not after 2005, and China's doubles after 2005. Real estate
and business services (BUS) rise everywhere (US 15 to 28, Germany 10 to
25, Korea 5 to 20): the service shift is general. At the 1999/2000 join
the US finance level jumps by a ratio of 1.63 (spine-overlap.csv flags it).
LR-WIOD's US finance value added is about half BEA's finance and
insurance (0.44-0.60, 1965-1999). Step 0 (bead bnm-3ka.1,
docs/STOCK-FLOW.md "Finance rows") found it in the source, not our
scripts: LR-WIOD builds the US from BEA's pre-2003 make-use tables, which
leave FISIM with its users, while its other US industries match BEA.
WIOD 2016 and ICIO match BEA, so the jump is the definition changing. Use
data/derived/stocks/econ-structure.csv (finance and manufacturing shares
spliced to BEA, OECD and EU KLEMS) for levels across the joins. The
spine's manufacturing runs above the national accounts' (US 1.05 in
LR-WIOD, 1.12 in WIOD 2016, 1.19 in ICIO), since PAPER holds all of
ISIC4 J58, software publishing included. China's 1965-1980 rows are
back-cast (the backcast column) and not to be read.

An earlier version of these tables left ELEC (electrical and optical
equipment) out of manufacturing (a bug in the scratchpad's
spine_shares.py and xsec.py); the manufacturing rows here include it.

Share of the 25 fixed countries' manufacturing value added (current USD):

            1965  1980  1990  1995  2000  2005  2010  2014  2020
      USA   46.8  29.0  27.7  26.4  32.2  28.7  23.5  22.8  22.4
      JPN    6.9  14.8  21.2  24.9  19.2  14.5  12.1   8.3   8.8
      DEU    9.5  11.6  10.8   9.9   8.0   9.0   8.1   7.9   6.7
      CHN    4.1   5.0   3.0   4.9   7.4  11.1  22.3  29.8  34.1
      KOR    0.1   0.7   1.7   2.6   2.9   3.6   3.6   3.9   3.6

Exchange rates move these (the dollar's 1985 peak, the yen's 1995 peak).
The real-volume version (spine-volumes.csv) is the next check.

### 7b. Does finance predict deindustrialisation across countries? (xsec.py)

2000-2014, the 43 WIOD 2016 countries. OLS with HC1 standard errors,
outcome the change in percentage points:

    d manufacturing ~ finance 2000 + log GDP pc 2000 + manufacturing 2000
      (a) WIOD 2016, manufacturing without ELEC (the first version)
        all 43:       finance -0.080 (t -1.29), log income -0.39 (t -0.97)
        without LUX:  finance -0.006 (t -0.04), log income -0.44 (t -1.08)
      (b) WIOD 2016, ELEC included
        all 43:       finance +0.009 (t +0.08), log income -0.88 (t -1.60)
        without LUX:  finance +0.210 (t +0.87), log income -1.00 (t -1.73)
      (c) corrected shares (econ-structure, data years; OECD for 35,
          BEA for the US, WIOD 2016 spliced or as is for 6)
        all 43:       finance +0.031 (t +0.26), log income -1.16 (t -1.87)
        without LUX:  finance +0.238 (t +0.88), log income -1.29 (t -1.94)
    d heavy (CHEM, METAL, MACH, TRANSEQ) ~ same, WIOD 2016
        all 43:       finance -0.084 (t -2.02), log income -0.51 (t -2.13)
        without LUX:  finance -0.112 (t -0.85), log income -0.50 (t -2.14)
    d manufacturing ~ d finance + ...: (a) -0.19 (t -0.75), (b) -0.34
      (t -1.05), (c) -0.69 (t -1.98)

The -0.08 of the first version came partly from a bug: the scratchpad's
xsec.py (and spine_shares.py) listed eleven manufacturing sectors and left
out ELEC, so the outcome missed electrical and optical equipment. With
ELEC, and on the corrected shares, the
coefficient is about zero. No support for K1 through the finance sector's
size. With n = 42 and 14 years, only a large effect would show. The
change in finance is weakly negative on the corrected shares (t -2.0),
the one hint, and a co-movement, not a cause. Krein's mechanism runs through
non-financial firms' payouts and hurdle rates, which this regression does
not see.

### 7c. Export shares by product (baci_cap.py, baci_show.py)

The summary's table, from BACI HS92 V202601. Further rows (1995 to 2024;
US+UK, CN+KR+TW):

    computers 8471                  26 -> 7;    12 -> 53
    transmission apparatus 8525     24 -> 4;    10 -> 54
    diodes, transistors, PV 8541    15 -> 3;    12 -> 46
    cold-rolled steel 7209           5 -> 5;    14 -> 39
    cars 8703                       13 -> 10;    3 -> 17
    rare-earth compounds 2846       16 -> 4;    36 -> 17 (China 35 -> 16, after
                                                export quotas; processing
                                                moved into magnets)
    unwrought aluminium 7601         6 -> 3;     3 -> 2 (China consumes its own:
                                                its 59% of output is not exported)
    refined copper 7403              7 -> 1;     2 -> 5

Exports understate where a country uses its own processing (aluminium,
copper). For materials, output shares come from BGS/USGS, not trade.

### 7d. Processing displacement (displacement.py)

BGS World Mineral Statistics production, 1970-2024 (local research use
only, BGS terms). D = 1 - sum over countries of min(resource share,
processing share). "Non-resource" is the processing share in countries
with under 1% of the resource stage.

    pair                     1975   1990   2000   2010   2020   2023   China proc. 1975 -> 2023
    cobalt (mine -> refined)  0.36   0.30   0.43   0.73   0.86   0.90   0% -> 80% (mine ~1%)
    bauxite -> alumina        0.39   0.36   0.31   0.33   0.52   0.48   2% -> 59%
    bauxite -> aluminium      0.76   0.69   0.70   0.66   0.74   0.69   2% -> 59%
    iron ore -> crude steel   0.47   0.51   0.53   0.38   0.54   0.51   4% -> 54%
    copper mine -> refined    0.31*  0.29   0.36   0.41   0.50   0.55   3% -> 47% (mine 8%)
    nickel mine -> refined    0.32*  0.32   0.31   0.46   0.41   0.37   1% -> 28%; Indonesia 41%
    tin mine -> smelter       0.23   0.18   0.19   0.23   0.40   0.49   10% -> 51%
    zinc mine -> slab         0.34   0.39   0.41   0.38   0.42   0.44   3% -> 49%
    (* 1980)

Displacement rose for the metals whose processing China took up from
2000 (cobalt, copper, tin, zinc, alumina). Aluminium was always displaced
(smelters follow cheap power, not bauxite). Steel's displacement is old
(Japan, Korea, Europe on imported ore). Nickel's reversed after
Indonesia's ore export bans. Iron ore is gross weight (China's ore is low
grade, which inflates its mine share). Rare earths cannot be done this
way: BGS and USGS report oxide (mine) output, not separation and metal.
The separation stage needs the plant list (MYB facilities 2024) or trade
in 2846 and magnets (8505).

### 7e. A learned regime, first try (regime.py)

Above in the summary: k-means and PCA on eight 1995-2019 country means
find income and offshore finance, not the finance-state axis. PC1 (32%) loads
on investment (0.53) and credit (0.48). PC2 (23%) is finance share and
return (+) against manufacturing and labour share (-). China, Korea and
Japan score highest on PC1 (3.3, 2.3, 2.3), the US 1.2. Features that
separate state direction are missing from that run (public wealth, the
SOE investment share, the deposit rate gap). PWT's China labour share is
a constant 0.592 for 1970-2000 (imputed), so it must not enter as data
for those years.

### 7f. Wealth, returns and investment (wid_beta.py, divergence.py)

    private wealth / national income   1970  1990  2007  2020
      US                                3.21  3.69  5.02  6.29
      GB                                2.95  3.88  4.72  6.05
      DE                                2.31  3.96  3.96  6.50
      JP                                3.14  7.35  6.20  6.77
      KR                                2.66  3.67  5.09  6.22
      CN                                0.76  1.65  4.35  6.35
    net public wealth / national income
      US                                0.29  0.20  0.32 -0.36
      GB                                0.61  0.65  0.08 -0.63
      DE                                0.79  0.56  0.21  0.39
      JP                                0.74  1.23  0.89  0.37
      KR                                1.17  1.72  2.20  2.70
      CN                                3.05  2.37  2.26  2.97

    PWT irr      1970  1990  2000  2010  2019
      USA       0.081 0.085 0.081 0.083 0.079
      CHN       0.167 0.093 0.114 0.103 0.079
      KOR       0.199 0.160 0.126 0.103 0.079
      DEU       0.061 0.056 0.053 0.060 0.048
    PWT csh_i
      USA       0.224 0.235 0.271 0.209 0.232
      CHN       0.304 0.309 0.294 0.458 0.432
      KOR       0.246 0.409 0.332 0.355 0.343

Realised returns converge to about 8% by 2019 in the US, China and Korea,
with China investing twice the US share. That is consistent with Pettis's
falling marginal return in China: the same return on twice the
investment. It is not evidence that Chinese capital earns less on
average. K1's hurdle-rate claim is about required returns at the margin,
which no open panel records.

### 7g. Material capability (cinc.py)

    COW NMC 6.0       CINC %            steel %          military spending %
                 USA  CHN  RUS      USA  CHN  RUS       USA  CHN  RUS
    1970        18.1 11.3 17.1     20.1  3.0 19.5      33.1 10.1 32.8
    1990        14.1 11.2 13.0     11.1  8.2 19.0      34.6  0.7 15.4
    2000        14.3 16.2  5.1     12.0 15.0  7.0      36.8  5.1  6.3
    2016        13.3 23.1  3.6      4.8 49.6  4.3      40.2  9.6  3.1

## 8. Design

The layer sits under the event lattice like the other stock-flow work
(docs/STOCK-FLOW.md). Every quantity is a node on the lattice (place,
time scale, dimension), learned and lagged (principles 1, 2, 7). Country
stocks are aggregations of cells where the data are located (principle
11). Where they are national (the accounts), they are national.

### 8a. Country-year stocks

**Financialisation (fin/*).** A dynamic latent factor, one per country,
on these measures:
- NFC payout ratio: dividends paid (D42) plus net equity buybacks, over
  gross operating surplus (OECD Table 720 / Eurostat; Fed Z.1 for the
  US);
- NFC portfolio income: property income received (D4) over gross
  operating surplus (Krippner, Stockhammer);
- the rentier share (Epstein-Jayadev): finance VA plus net property income
  of households and NFCs, over national income;
- investment against profit: gross fixed capital formation of NFCs over
  their operating surplus (Stockhammer; Gutiérrez-Philippon's investment
  gap without q where there is no market data);
- finance VA share (spine, OECD 6A);
- market capitalisation over GDP (GFDD).
The factor is a Bayesian state-space model (a local level per country,
loadings pooled up the place tree, missing measures allowed, so the
40 countries with sector accounts from 1995 inform the 18 with long JST
series). Its sign is fixed by a prior: payouts load positively.
Financial development (the IMF FD index, income) enters as a control
factor, so that fin/* is what is left after depth. Read lagged two years
(sector accounts publish in 12-18 months; principle 7).

**Material base (base/*).** Per country-year:
- manufacturing and heavy-industry VA shares (spine; UNIDO for the rest);
- world output shares by stage for the materials (BGS/MYB, tonnes);
- export shares in capital-intensive products (BACI, weighted by capital
  intensity, below);
- the processing balance: processed output over the country's own
  resource output, per material (above 1: a processor of others' ore;
  below 1: an exporter of ore).
These stocks change only by the flows in 8b (principle 8).

**Investment regime (regime/*).** A second latent factor on: net public
wealth over national income (WID), the public share of investment and
capital (ICSD), the real deposit rate against growth (the repression gap),
household consumption share (GMD), current account (GMD), investment
share (PWT), and the share of credit to tradables against households and
real estate (Müller-Verner, BIS). The prior says public wealth and the
investment share load positively and the household share negatively. The
factor is checked, not labelled: it should separate China, Korea and
Taiwan (and Japan before 1990) from the US and UK at equal income. If it
does not, the regime is not learnable from these data, and that is
reported.

**Asset-growth divergence (div/*).** The contradiction K5 names, in
principle 4's form: asset values against what produces them.
- div/wealth-gap: private wealth over national income against its own
  25-year trend (WID; 40 countries from 1970);
- div/equity-gap: log equity value growth against log earnings or GDP
  growth, smoothed (JST for 18 countries from 1870; Shiller for the US);
- div/factor: a GLL decomposition where the labour share and equity
  values are both held. Equity growth splits into growth, factor share
  and discount rate parts. The factor-share part is the reallocation K5
  measures.
Strain is the gap's accumulation; it resolves at a market crash or a
banking crisis, which the lattice already forecasts. The Keen node
(keen/*: wage share, debt) is the neighbour: the factor-share part of
div/* and Keen's profit share are the same contradiction seen from
assets and from flows. One must not be read twice. The nested test
(below) weighs div/* given keen/*.

### 8b. Production migration as flows

A country's world share in product p moves by a flow, in effect a
Markov share transition. For each product and year, the change in each
exporter's share is a flow between countries, attributed in proportion
to the gainers' and losers' changes (a shift-share decomposition: the
loser's lost share goes to the gainers pro rata). Summed over products
weighted by capital intensity, it gives a country-to-country migration
matrix per year, M[t][i][j]. Stocks (8a's base/*) change only by these
flows. Learned: the flow out of country i in product p is modelled as
f(i's fin/* and regime/*, p's capital intensity, i's income, distance to
the gainer, p's upstreamness). The coefficient on fin/* x capital
intensity is K2. A positive coefficient on regime/* x capital intensity
for the gainer is the other half.

Products: BACI HS6, 1995-2024, about 5,000. Capital intensity from the
NBER-CES database (US 1958-2018, by NAICS, concorded to HS) and EU KLEMS,
fixed at a reference year before the window, so it is not caused by the
migration. EXIOBASE's five-yearly pxp tables give output (not export)
shares for 200 products as a check. BGS/MYB give the material stages in
tonnes.

### 8c. The cell layer, on bnm.cells

Built on the cell API another agent is writing (src/bnm/cells.clj):
- data/derived/cells/crosswalk.csv: cell to country and province, area
  shares;
- aggregation from cells to countries and regions;
- raster and polygon ingest;
- data/derived/cells/assets.csv: plants, mines, fields and refineries on
  cells.
No separate spatial scheme. This layer adds rasters through the ingest and
writes cell-year stocks:

1. **Ingest (raster -> cell, by area-weighted mean or sum through the
   API):** Kummu GDP per capita (1990-2022) and HDI (1990-2015), night
   lights (Li et al. 1992-2018, then VIIRS), GHSL built-up (residential
   and non-residential) and population (five-yearly, interpolated with a
   flag), EDGAR sector emissions (1970-2022), the relative deprivation
   index (one period), and the mining polygons (Maus, Tang & Werner; area
   per cell).
2. **Cell profile (cell/char-*):** the NMF archetype weights of 6c, per
   cell and year where the inputs vary by year (lights, EDGAR, GDP, GHSL
   epochs). The plants and mines come from assets.csv. Those with start
   years (SFI steel, cement and ethylene, GEM summary tables at country
   level, OGIM where dated) enter from their start year; the undated
   (GPPD, MYB facilities 2024) enter only from their vintage (principle 7;
   docs/STOCK-FLOW.md's GPPD warning applies). The check: aggregated
   through the crosswalk, the archetypes reproduce the country's spine
   sector mix (8a). The gap is reported per country.
3. **Displacement on cells (cell/disp-*):** for each material, per year:
   - country level, D as in 7d (BGS/MYB);
   - cell level, the tonnage-weighted mean great-circle distance from
     mine cells to processing cells. Mines are assigned to processing
     countries through BACI's bilateral ore and concentrate flows (HS 26xx)
     and, within a country, to its processing cells by capacity;
   - per processing cell, the share of its feed from abroad (its
     exposure);
   - per mine cell, the share of its output processed abroad (the value
     it does not keep).
   Over time, with dated plants, this is the "processing displacement" as
   a flow on cells: a new smelter in a mine cell shortens the distance (an
   upgrading event); an export ban (OECD inventory, on disk) should
   precede that.
4. **Development paths (cell/path-*):** a cell's trajectory in archetype
   space and its development (light and GDP per capita growth, against its
   country-year: within, principle 6). Path types are learned by
   clustering trajectories (extraction only; extraction to processing;
   processing to manufacturing; services). The question is whether cells
   that add processing to extraction grow faster than cells that only
   extract, in the same country-year (resource-based industrialisation,
   Owens & Wood). The rival is the curse: extraction cells lag.

**Features for the A5 pilot rerun.** The 2026 pilot had only event
coordinates (docs/RESEARCH.md). These cell features would feed the rerun,
each as a within-country change, lagged a year:
- light growth against the country's (local boom or bust);
- deprivation level and GDP per capita growth against the country's;
- extraction weight and mining area, split by lootable commodity (gems,
  gold, oil: Ross 2004, Lujala et al. 2005) for the conflict kinds;
- processing exposure (feed from abroad) and mine-cell value kept, for
  protest and violence around mines (the Indonesian, Congolese and
  Peruvian cases);
- the Autor-Dorn-Hanson style shock: the cell's industry mix times the
  national import surge from the gainers in 8b, for protest in
  deindustrialising cells;
- displacement events: a plant's opening or closing in the cell (from the
  dated asset lists).
They are the cells' "what is in them" that principle 11 asks for before
cells are tried again against provinces.

### 8d. Links into the lattice and the essentials model

- **Essentials (docs/ESSENTIALS.md, bnm.supply).** Supply risk per
  product is P(disruption) x output lost. This layer gives the
  P(disruption) factor two inputs. One is processing concentration by
  stage from 7d, not mine concentration: cobalt refining is 80% China
  while mining is 70% Congo. The other is the political distance between
  the user and the processor. Per country, "financialised economies'
  critical inputs": the share of a country's critical recipe inputs
  (bnm.supply's critical rows) whose processing stage is concentrated in
  a country of the opposite regime pole (regime/* differing by more than a
  threshold learned from the data) is the K4 exposure, written
  supply/regime-exposure. Its rise over 1995-2022 is descriptive. Its use
  is in the outage scenarios (a cut of China's refined cobalt, gallium or
  heterocyclics to the US, EU and Japan).
- **Banking crises and market crashes.** div/* and fin/* as own-country
  states, entered by the nested test against keen/* and the credit gap.
- **Interstate kinds.** A dyadic production-finance gap (8a's base/*
  share against fin/* and the reserve-currency share) for the
  interstate and export-restriction work.
- **Principal contradiction.** The anatomy (docs/ANATOMY.md) can name
  three new tensions: asset values against output (div/*), the
  external deficit against asset inflows (coupling), and investment
  against return (regime/*: China's falling irr on rising investment, the
  overinvestment contradiction Pettis describes).

### 8e. Hypotheses to pre-register

Each is registered in docs/PHASE-CHANGES.md before its test years are
scored, with design years to 2002 where the series allow (most start in
1995; then 1995-2007 design, 2008-2022 test).

**H1 (K2, migration by capital intensity, product panel).** Change in
exporter i's world share of HS6 product p over five years, on fin/*_i x
capital intensity_p, with exporter-year and product-year fixed effects.
Prediction: negative. Control group: the rich non-financialised exporters
(Germany, Japan, Switzerland, the Nordics) are in the panel; the effect
is identified off differences in fin/* at equal income. Placebo: fin/*
from another country of similar income (state-diag's rule: a placebo
matching the gain means the effect is the place's or period's).
Falsified if the coefficient is zero or positive, or matched by its
placebo. Power: about 5,000 products x 40 exporters x 5 windows, clustered
by product and exporter. A 1 SD shift in fin/* moving the share of a 1 SD
more capital-intensive product by 0.05 percentage points over five years
should be detectable, with simulated power to be reported first. The
weak point is the 40 countries: fin/* varies across few of them.
7c already says Japan and Germany lost as much as the US and UK in ships
and chips, so a null is a live outcome.

**H2 (K2, the gainer).** The same panel for gains, on regime/*_i x
capital intensity_p. Prediction: positive. Falsified as H1.

**H3 (the regime is learnable).** The regime/* factor, fitted on
1995-2007, separates China, Korea, Taiwan and Vietnam from the US, UK,
Australia and Canada at equal income: within a log-income band, the
groups' factor means differ by more than 1 SD, and the factor's
correlation with log income is under 0.5 in absolute value. Falsified if
not; the regime is then reported as income plus offshore finance (7e) and
not used. Power: descriptive, one fit; the bootstrap over countries gives
the interval.

**H4 (K5, divergence -> crashes and crises).** div/wealth-gap and
div/equity-gap, own-country states, raise P(market crash) and P(banking
crisis) in the nested comparison over keen/* and the credit gap,
against the other-country placebo. Power: per the audit, the per-kind MDE
for banking crises and crashes is OR 1.9 to above 2.5; on 18-40 countries
lower still. Most likely outcome: not detectable. So H4 is run as an
attribution (docs/T4-ATTRIBUTION.md's method: the state's share of the
base's skill) and reported as such. A null is not evidence of no effect.

**H5 (K3, coupling).** Bilateral: the US and UK deficit with a partner
covaries with the partner's acquisition of US/UK assets (CPIS, 2001-;
External Wealth of Nations net, 1970-), more for partners with a high
regime/*. Descriptive correlation with dyad and year fixed effects.
Falsified if the dyad-level association is zero. Power: about 100
partners x 20 years; adequate for a correlation, not for causation.

**H6 (displacement and development, cells).** Within country-year, mine
cells that gain processing capacity (a dated plant opening) grow faster
in lights and GDP per capita over the next five years than mine cells
that do not, matched on prior growth. Placebo: the same test with the
opening assigned to a random other mine cell in the country. Falsified
if the effect is zero or matched by the placebo. Power: the number of
dated openings in mine cells (SFI steel, cement, MYB and GEM where
dated; probably a few hundred) sets it. Count them before
registering.

**H7 (displacement follows the processor's regime).** Across materials
and years, processing share gains go to countries with higher regime/*,
given energy cost and distance to the ore. Same product-panel logic as
H1-H2, with about 20 materials x 50 years in tonnes. Falsified if
regime/* adds nothing to energy price and distance.

**H8 (K4, essentials exposure).** supply/regime-exposure of the US, UK,
EU and Japan rose from 1995 to 2022. Descriptive: reported as computed,
no test. It enters the outage scenarios.

**H9 (geopolitics, dyad-year).** The production-finance gap between two
countries (material share minus financial share, each a world share)
predicts export restrictions and sanctions between them (OECD inventory,
GSDB, GTA; 2009-), given trade dependence, distance, alliance and regime
type. Dyad-year logit with dyad fixed effects. Power: about 15,000
dyads, but restrictions cluster on a few dozen pairs. For the US-China
pair alone, n = 1: descriptive only, never a test.

### What is deliberately not done

- No regime labels from Milanović or varieties of capitalism enter as data.
- No hurdle-rate series is invented. Required returns are not observed in
  open data. K1's hurdle claim is tested only through its implication
  (H1).
- No transfer of K1-K5 to the event lattice except through H4's nested,
  placebo-checked test.

## 9. Ranked steps (by payoff over cost)

1. **The product migration panel (H1, H2).** BACI is on disk. Capital
   intensity needs the NBER-CES database (small) and the HS concordances
   (in the essentials list). The first version can use the spine for
   fin/* proxies before the factor exists, and must report that. Most
   direct test of K2; tens of thousands of rows. One to two days.
2. **Processing displacement per material (7d -> stocks).** BGS and MYB
   are on disk; add rare-earth separation from MYB facilities and trade in
   2846 and 8505. Feeds the essentials model's concentration factor
   immediately (supply/regime-exposure, H8). One day.
3. **Step 0, the finance rows.** Check the spine's FIN against OECD
   Table 6A and BEA for the 25 LR-WIOD countries; record the level ratio
   per country; fix the source or document the break. Half a day, and
   every finance-share reading depends on it.
4. **The regime factor (H3).** WID (on disk), ICSD (manual download),
   GMD, PWT, Müller-Verner. The decisive check of whether "state-directed"
   is a measurable thing. Two days.
5. **The financialisation factor (fin/*).** Needs the OECD sector accounts
   (Table 720) and GFDD downloads. Two to three days. H1's proper input.
6. **The cell layer (8c), once bnm.cells lands.** Kummu GDP, night lights,
   GHSL, EDGAR ingested through the raster API; NMF profile; the
   accounting check against the spine; then displacement on cells and
   development paths (H6). The largest download and the most work (a week
   or more), and it is what the A5 rerun waits for.
7. **div/* and H4 as attribution.** WID, JST and Shiller are on disk;
   GLL's decomposition is the work. Low power: run after the factors.
8. **Coupling (H5).** CPIS, CDIS and External Wealth of Nations
   downloads; descriptive.
9. **Geopolitics (H9).** GSDB (request form), GTA; COW and the OECD
   inventory are on disk. Descriptive or dyadic; last because the units
   that matter are few.

## 10. Downloads needed

Sizes are approximate; check before fetching.

| data | use | size | licence | how |
|---|---|---|---|---|
| World Bank Global Financial Development Database (GFDD) | market cap, value traded, bank credit, 1960- | about 20-40 MB (xlsx) | CC BY 4.0 | open |
| WDI series via API: CM.MKT.LCAP.GD.ZS, CM.MKT.TRAD.GD.ZS, FR.INR.LEND, FR.INR.DPST, NE.CON.PRVT.ZS | finance size, repression gap | under 5 MB | CC BY 4.0 | open |
| OECD Table 720 non-financial accounts by sector (SDMX), Table 6A VA by industry | NFC payouts, property income, net lending; finance VA | about 100-300 MB | OECD terms (free reuse with attribution) | open |
| Eurostat nasa_10_nf_tr | EU sector accounts, 1995- | about 50 MB | CC BY 4.0 | open |
| US Fed Z.1 (or FRED NCBCEBQ027S, NCBDBIQ027S) | US buybacks, NFC equity issuance | under 50 MB (Z.1 zip); FRED KB | public domain | open |
| BEA GDP by industry and fixed assets by industry | US finance check, industry returns | about 20 MB | public domain | open |
| IMF FD index (Svirydzenka) | control | about 5 MB | IMF terms | manual (IMF pages refuse scripts) |
| IMF ICSD 2021 (IMFInvestmentandCapitalStockDataset2021.xlsx) | public vs private investment and capital | about 5-15 MB | IMF terms | manual |
| IMF CPIS, CDIS | bilateral portfolio and direct investment | about 100-300 MB bulk | IMF terms | manual or API (403 risk) |
| Lane & Milesi-Ferretti External Wealth of Nations (1970-2022) | external positions | about 10 MB | free with citation | open (Brookings) |
| Coppola, Maggiori, Neiman & Schreger restated holdings | nationality-based positions | about 50 MB | free with citation | open (globalcapitalallocation.com) |
| UNIDO INDSTAT 2 (and 4) | manufacturing VA, investment by ISIC, 1963- | about 50-300 MB | open data policy since 2022; terms to check | semi-manual (portal selection) |
| NBER-CES Manufacturing Industry Database (1958-2018) | capital intensity by NAICS | about 30 MB | free | open |
| EU KLEMS 2023 | capital stocks by industry | about 300-600 MB | CC BY 4.0 | open |
| HS6-ISIC/NAICS concordances (WITS, UNSD) | product to industry | under 10 MB | open | open (also in ESSENTIALS' list) |
| Kummu et al. 2025 GDP pc (Zenodo 10976733) | cell GDP per capita 1990-2022 | about 1-3 GB | CC BY 4.0 | open |
| Kummu et al. 2018 GDP and HDI (Dryad) | HDI 1990-2015 | about 1-2 GB | CC BY 4.0 | open |
| Li et al. 2020 harmonised night lights (figshare) and VIIRS annual (EOG) | lights 1992- | about 3-6 GB | CC BY 4.0; EOG needs login | open (EOG manual login) |
| GHSL R2023A built-up S (total and NRES), POP, 1 km | land use, density, 1975-2030 | about 2-5 GB at 1 km | CC BY 4.0 | open |
| EDGAR v8 gridded sector emissions (IND, CHE, IRO, NMM, NFE, REF_TRF, ENE), annual | industry location over time | about 2-5 GB for those sectors | CC BY 4.0 | open |
| CIESIN GGRDI v1 | relative deprivation | about 0.5-1 GB | CC BY 4.0 | manual (Earthdata login) |
| G-Econ 4.0 | gross cell product 1990-2005 | about 10 MB | free | open |
| ARDECO NUTS3 GVA by sector (JRC) | validation of cell profiles | about 50 MB | CC BY 4.0 | open |
| Global Sanctions Data Base (GSDB) | sanctions dyads, 1950-2023 | about 10 MB | free for research | manual (request form) |
| Global Trade Alert | interventions, 2008- | about 100 MB | GTA terms | manual |
| IMF COFER | reserve currency shares | small | IMF terms | manual |
| GEM plant-level trackers (steel, cement, chemicals) | dated processing plants on cells | about 50-200 MB | CC BY 4.0 | manual (already in ESSENTIALS) |

On disk already and used here: the I-O spine, BACI HS92, BGS 1970-2024,
USGS MYB 1990-2022 and facilities 2024, PWT 11, EPWT 7, GMD, WID (full),
JST R6, Shiller, BIS credit, IMF GDD, Müller-Verner, Chinn-Ito, COW NMC and
MIDs, the OECD export restrictions inventory, the mining polygons, OGIM,
SFI, GPPD, GAEZ.

## References not cited inline

- Antràs, P., Chor, D., Fally, T. & Hillberry, R. (2012). "Measuring the
  Upstreamness of Production and Trade Flows." AER P&P 102(3): 412-416.
- Hall, P. & Soskice, D. (eds.) (2001). Varieties of Capitalism. Oxford UP.
- Lardy, N. (2008). "Financial Repression in China." PIIE Policy Brief
  PB08-8.
- Müller, K. & Verner, E. (2024). "Credit Allocation and Macroeconomic
  Fluctuations." Review of Economic Studies 91(6): 3645-3676.
- Ross, M. (2004). "What do we know about natural resources and civil
  war?" Journal of Peace Research 41(3): 337-356. Lujala, P., Gleditsch,
  N. P. & Gilmore, E. (2005). "A diamond curse?" Journal of Conflict
  Resolution 49(4): 538-562.
- Singer, J. D., Bremer, S. & Stuckey, J. (1972). "Capability
  Distribution, Uncertainty, and Major Power War, 1820-1965." In Russett
  (ed.), Peace, War, and Numbers. Sage.
