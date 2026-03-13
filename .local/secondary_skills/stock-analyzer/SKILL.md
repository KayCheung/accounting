---
name: stock-analyzer
description: Analyze stocks and companies with fundamental analysis, technical indicators, and risk assessment
---

# Stock & Investment Analyzer

Analyze stocks, companies, and investment opportunities using financial market data. Provide company profiles, technical analysis, fundamental analysis, and portfolio insights.

## When to Use

- User wants to analyze a specific stock or company
- User asks about financial metrics, earnings, or valuations
- User wants to compare investment options
- User needs portfolio analysis or allocation advice
- User asks about market trends or sector performance

## When NOT to Use

- Tax-specific questions (use tax-reviewer skill)
- Personal budgeting (use budget-planner skill)
- Insurance coverage (use insurance-optimizer skill)

## Data Sources (Use These — Don't Guess)

**Python libs (run directly, no API key):**

```python
import yfinance as yf
t = yf.Ticker("AAPL")
t.info              # P/E, market cap, beta, 52w range, margins
t.financials        # income statement (4yr)
t.balance_sheet     # debt, cash, equity
t.cashflow          # FCF, capex
t.history(period="1y")  # OHLCV for technicals
t.institutional_holders # 13F ownership

```

**Screening:** `finvizfinance` lib — filter S&P 500 by sector/valuation/signals. Finviz.com directly for heatmaps and insider tables.

**Primary filings:** Start from the EDGAR filing index: `webFetch("https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK={ticker}&type=10-K")`. This returns a list of filings — find the most recent 10-K and `webFetch` its "Documents" link to reach the actual filing. Read Item 1A (Risk Factors) and Item 7 (MD&A) — this is where management admits problems.

**Insider activity:** `webFetch("http://openinsider.com/screener?s={ticker}")` — look for **cluster buys** (multiple execs buying same week) and **P-code** open-market purchases (insider paid cash at market price — strongest signal). Ignore option exercises (M-code) and 10b5-1 scheduled sales.

**Short interest:** webSearch `"{ticker} short interest fintel"` — >20% of float = crowded short, squeeze risk either direction.

## Research First — Mandatory Before Any Output

**Never show financials, tables, or a report to the user without thoroughly researching first.** Before generating any Excel model, PDF, or web preview, you must:

1. **Load the `deep-research` skill** for comprehensive web research. This is not optional — every stock analysis must use deep research to gather real data before producing any deliverable.
2. **Pull actual financials** from yfinance AND cross-reference with SEC EDGAR filings (10-K, 10-Q). Do not rely on a single source.
3. **Search for every company mentioned** — if the user's request involves multiple companies or peers, pull financials on ALL of them, not just the primary ticker.
4. **Bias towards tables and numbers from actual public filings.** Every financial figure in the report must be traceable to a real source (SEC filing, earnings release, or yfinance data pull). Do not estimate or round when real numbers are available.

If you cannot verify a financial figure from at least one real source, flag it explicitly as unverified. Never present guessed or hallucinated numbers as fact.

## Methodology

### Step 1: Pull the Data (Python)

Run `yfinance` to get fundamentals + 1yr price history. Compute 50/200 SMA, RSI(14), and current price vs 52w high. Takes 10 lines of pandas.

### Step 2: Fundamental Analysis

**Valuation (compare to sector median, not S&P):**

- P/E — meaningless alone; flag if >2× sector median
- PEG — <1.0 = growth at reasonable price; >2.0 = priced for perfection
- EV/EBITDA — better than P/E for capital-intensive or leveraged cos
- P/S — only metric for unprofitable growth; >20× = needs hypergrowth to justify
- FCF yield (FCF/market cap) — >5% = genuinely cheap; negative = burning cash

**Quality red lines (practitioner heuristics):**

- Revenue growing but FCF shrinking → earnings quality problem, dig into receivables
- Debt/EBITDA >4× → one bad year from covenant breach
- Gross margin compressing 3+ quarters → losing pricing power
- Stock-based comp >15% of revenue → dilution machine (common in SaaS)
- Goodwill >50% of assets → acquisition-heavy, writedown risk

### Step 3: Technical Context (Not Prediction)

Compute in pandas — don't just describe:

- Price vs 50/200 SMA: below both = downtrend, don't catch knives
- Golden cross (50 crosses above 200) = trend confirmation, not entry signal
- RSI(14): >70 overbought / <30 oversold — only useful at extremes + divergence
- Volume: moves on 2×+ avg volume are real; low-volume moves fade
- % off 52w high: >30% drawdown in an uptrending market = something broke

### Step 4: The Retail Edge — Signals Institutions Ignore

- **Insider cluster buys** (OpenInsider): 3+ insiders open-market buying within 2 weeks is the single highest-conviction public signal. Research shows insider buys outperform; sells mean nothing (taxes/divorces/yachts).
- **Buying the dip**: insider P-code purchase after >10% drop = management disagrees with the market
- **Short squeeze setup**: short interest >20% + days-to-cover >5 + any positive catalyst
- **Unusual options**: webSearch `"{ticker} unusual options activity"` — large OTM call sweeps before earnings sometimes leak info

### Step 5: Comparative Table

Build a pandas DataFrame with peers side-by-side: P/E, PEG, rev growth, gross margin, FCF yield, debt/EBITDA. The outlier in either direction is your thesis. **Pull yfinance data for every peer company** — do not leave cells blank or use estimates when real data is available. Every company in the comparison must have actual financials pulled and verified.

## Step 6: Web Research — Find Existing Analyst Reports and News

**Use web search aggressively via the `deep-research` skill.** Before writing the report, gather real external research to cite:

```text
webSearch("[ticker] analyst report 2026")
webSearch("[ticker] earnings analysis site:seekingalpha.com")
webSearch("[ticker] bull case bear case site:seekingalpha.com OR site:fool.com")
webSearch("[company] investor presentation 2026 filetype:pdf")
webSearch("[ticker] price target consensus")
webSearch("[ticker] industry outlook [sector]")
webSearch("[company] competitive landscape")
webSearch("[ticker] short interest thesis")
```

**Source hierarchy (cite all of these in the report):**

| Source | What you get | How to cite |
|--------|-------------|-------------|
| **SEC EDGAR** (10-K, 10-Q, 8-K) | Primary financials, risk factors, MD&A | "Source: [Company] 10-K FY2025, Item 7" |
| **Earnings call transcripts** | Management commentary, guidance | "Source: Q4 2025 Earnings Call, CEO remarks" |
| **Sell-side research** (via SeekingAlpha, TipRanks) | Price targets, consensus estimates | "Source: TipRanks consensus, 12 analysts" |
| **Industry reports** | TAM, growth rates, competitive dynamics | "Source: [Firm] [Industry] Report, [Date]" |
| **Company investor presentations** | Management's own bull case, KPIs | "Source: [Company] Investor Day 2025" |
| **News** (Reuters, Bloomberg, CNBC) | Catalysts, M&A, regulatory | "Source: Reuters, [Date]" |

Use `webFetch` to pull actual content from SeekingAlpha articles, earnings transcripts, and investor presentations. Extract specific data points, quotes, and estimates to cite in the report.

## Build Order — Excel First, Then PDF Report, Website Last

Follow this exact order so the user gets fast, tangible results. You MUST produce **both** the Excel model AND the PDF report — they are not optional or interchangeable.

### Step 1: Build the Excel financial model

**Load the `excel-generator` skill** and generate a professional `.xlsx` file containing all financial models, comp tables, DCF spreadsheets, and data-heavy outputs. The Excel file is the working analytical model — it should have real formulas, charts, conditional formatting, and data validation. Present the Excel file to the user in chat immediately after generating it.

### Step 2: Generate the PDF research report

Write a generation script (`generate-report.ts`) that produces a polished, multi-page equity research PDF using **jsPDF**. This is the primary deliverable — the report the user would hand to someone. **Do not output a markdown summary as a substitute. Do not skip the PDF.** The PDF must be generated and presented to the user in chat before building any web app.

### Step 3: Build the web app last

Build a React web artifact that renders the same report data as an HTML preview — a page-by-page view that visually matches the PDF. The web app is a nice-to-have preview that comes after the PDF and Excel are already in the user's hands. **Do NOT spend time on the web app before the user has seen their PDF and Excel file.**

The user cares most about the Excel model and PDF report. Get those into the user's hands first. The web app is a visual complement, not a replacement.

## Excel Output — Financial Models & Data Tables

The `.xlsx` file should include:

- DCF model with editable assumptions (WACC, terminal growth, revenue CAGR)
- Comparable company analysis table with live formulas
- Historical financials (income statement, balance sheet, cash flow) — 4+ years
- Peer comparison metrics (P/E, EV/EBITDA, revenue growth, margins)
- Charts: revenue trend, margin trends, valuation multiples
- Conditional formatting on key metrics (green/red for above/below thresholds)

## PDF Report — Professional Research Report (Sell-Side Format)

The PDF should look like a sell-side initiation note from Goldman, Morgan Stanley, or JP Morgan. **This is mandatory — every stock analysis must produce this PDF.**

### Report Structure

**Page 1 — Cover / Executive Summary:**

- Company name, ticker, exchange, current price, market cap
- **Rating**: Buy / Hold / Sell with price target and upside/downside %
- **Investment thesis** in 3-4 bullet points (the "elevator pitch")
- Key metrics snapshot: P/E, EV/EBITDA, revenue growth, FCF yield
- A **1-year price chart** (generated via matplotlib/plotly, embedded as image)

**Pages 2-3 — Investment Thesis:**

- Bull case (with probability weighting if possible)
- Bear case (required — what kills this trade?)
- Key catalysts with expected timeline
- Competitive positioning / moat analysis

**Pages 3-4 — Financial Analysis:**

- Revenue breakdown by segment (with a **stacked bar chart**)
- Margin trends over 4+ quarters (with a **line chart**)
- FCF bridge / waterfall
- Balance sheet health (debt maturity, liquidity)
- Peer comparison table (pulled from yfinance for 3-5 peers)

**Page 5 — Valuation:**

- DCF model summary (show assumptions: WACC, terminal growth, revenue CAGR)
- Comparable company analysis table
- Historical valuation range (P/E or EV/EBITDA band chart)
- Price target derivation

**Page 6 — Technical Analysis:**

- Price chart with 50/200 SMA overlay (generated via matplotlib)
- Volume analysis
- Key support/resistance levels
- RSI chart

**Page 7 — Risks:**

- Ranked by probability × impact
- Regulatory, competitive, execution, macro risks
- Specific to this company, not generic boilerplate

**Final Page — Sources:**

- Full citation list with dates for every external source referenced
- Disclaimer / not financial advice

### Charts and Visualizations

Generate charts using **matplotlib** or **plotly** in Python, save as PNG, and embed in both the PDF and the web preview:

```python
import matplotlib.pyplot as plt
import yfinance as yf

# Price chart with SMAs
df = yf.Ticker("AAPL").history(period="1y")
fig, ax = plt.subplots(figsize=(10, 5))
ax.plot(df.index, df['Close'], label='Price', color='#1a1a2e')
ax.plot(df.index, df['Close'].rolling(50).mean(), label='50 SMA', color='#e94560', linestyle='--')
ax.plot(df.index, df['Close'].rolling(200).mean(), label='200 SMA', color='#0f3460', linestyle='--')
ax.set_title("AAPL — 1 Year Price History")
ax.legend()
ax.grid(alpha=0.3)
fig.savefig("price_chart.png", dpi=150, bbox_inches='tight')

# Revenue by segment bar chart
# Margin trend line chart
# Valuation band chart
# RSI chart
```

Generate **at least 4 charts** for the report: price history with SMAs, revenue/margin trends, peer valuation comparison, and one more relevant to the thesis.

### PDF Generation (jsPDF)

Use **jsPDF** (same approach as the resume skill) to generate the PDF with explicit point-based layout:

- `new jsPDF({ unit: "pt", format: "letter" })` — US Letter: 612×792pt
- Use 36pt margins (0.5in). Content area: 540w × 720h points.
- **Track Y position** as you render each element. When the next element would exceed `PAGE_H - MARGIN`, call `doc.addPage()` and reset Y to the top margin. Never let content silently overflow — always check before rendering.
- Embed chart PNGs via `doc.addImage()` — scale each chart to fit the content width while respecting remaining page height. If a chart won't fit on the current page, start a new page.
- Add a **header** (company name, ticker, page number) and **footer** on each page by hooking into page creation.
- Use a clean sans-serif font, navy/dark blue headers, and consistent spacing.

### Avoiding Blank Pages — Two Common Causes

Blank pages in multi-page jsPDF reports almost always come from one of two bugs:

1. **Footer hijacking the cursor.** If you draw a footer at the bottom of the page (e.g., y=720) and leave the cursor there, the next section thinks it's already at the bottom and forces a new page — leaving the previous page blank except for the footer. **Fix:** save the Y position before drawing the footer, then restore it afterward so content flow isn't disrupted.

2. **Double page breaks.** If you have both automatic page breaks (when content nears the bottom) and manual `doc.addPage()` calls at section transitions, both can fire in sequence — producing a blank page between them. **Fix:** before any manual page break, check whether a fresh page was already added (e.g., track an `isNewPage` flag). Only add a page if you're not already on a fresh one.

**Required before presenting:** After generating the PDF, verify there are no blank pages before showing it to the user. Open the generated PDF and check every page for content. If any page is blank, fix the page-break logic and regenerate. Do not present a PDF with blank pages.

### Web Preview — Page-by-Page HTML That Matches the PDF

The React web artifact reads the same report data (via a JSON endpoint, same pattern as the resume skill) and renders an HTML version that **visually mirrors the PDF page-by-page**. Each "page" in the web preview should be a fixed-size container (816×1056px — US Letter at 96dpi) with the same margins, typography, and chart placement as the PDF.

### Styling Guidelines

- **Header bar** on each page with company name, ticker, and page number
- **Data tables** with alternating row shading, right-aligned numbers
- **Charts** at full column width with clear titles and axis labels
- **Callout boxes** for key insights ("Management guided 15% revenue growth in Q4 call")
- **Source citations** as footnotes or inline parenthetical references
- **Professional typography**: 11pt body, 14pt section headers, consistent spacing

## Best Practices

1. **Timestamp everything** — state data pull date; yfinance prices are ~15min delayed
2. **Sector-relative only** — a 30 P/E is cheap in software, expensive in utilities
3. **Label facts vs thesis** — "FCF yield is 6%" (fact) vs "undervalued" (opinion)
4. **Bear case required** — every analysis must include: what kills this trade?
5. **Position sizing reality** — no single stock >5% for most retail portfolios; if conviction demands 20%, the conviction is the problem

## Limitations & Disclaimer

- **This is NOT financial advice.** Informational analysis only. User is responsible for all investment decisions. Recommend consulting a licensed financial advisor before acting.
- yfinance scrapes Yahoo — occasionally breaks, data may lag filings
- Cannot access Bloomberg/FactSet/real-time Level 2
- Cannot execute trades or provide personalized portfolio allocation
- Past performance does not indicate future results; all equities can go to zero
