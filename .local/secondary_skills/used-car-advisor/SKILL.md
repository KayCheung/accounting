---
name: used-car-advisor
description: Evaluate used car listings, estimate fair prices, and guide purchasing decisions.
---

# Used Car Advisor

Evaluate used car listings, estimate fair value, flag known-problem models, and coach negotiation.

## When to Use

- "Find me the best [car] under $X" — search for real listings first, then evaluate
- Evaluating a specific listing or comparing options
- "Is this a fair price?" / "What's wrong with this model year?"
- Negotiation prep before contacting a dealer

## When NOT to Use

- New car purchasing, repair diagnosis, insurance (use insurance-optimizer)

## Listing Search Workflow

When a user asks to find a car, follow these steps in order:

**Step 1: Search for real listings across multiple platforms.** Use specific price, mileage, and location filters. Present actual listings with real prices — never lead with ballpark estimates.

**Step 2: Collect and organize results.** For each listing, capture: year/make/model/trim, price, mileage, location, dealer vs. private, and listing URL.

**Step 3: Evaluate and compare.** Apply the Fair Price Method (below) and flag any Known Problem Models or Red Flags.

**Step 4: Provide pre-filtered search links** so the user can browse themselves (e.g., `cars.com/shopping/ferrari-california/price-under-80000/`). Don't just link site homepages.

## Research Sources (use webSearch/webFetch)

### Inventory / Listing Search

| Source | Best for | Notes |
|--------|----------|-------|
| `cargurus.com` | Deal ratings, days-on-market | Shows "great/good/fair/overpriced" vs. market |
| `cars.com` | Pre-filtered URLs by price/make | Supports URL filters like `/price-under-80000/` |
| `autotrader.com` | Broad dealer inventory | Good trim-level filtering |
| `edmunds.com` | Listings + TMV pricing | Combines inventory with valuation |
| `autolist.com` | Savings vs. market average | Aggregates from multiple sources |
| Facebook Marketplace | Private sellers | Often cheaper than dealers; search by region |
| `carsforsale.com` | Broader dealer network | Good for less common models |
| Bring a Trailer (`bringatrailer.com`) | Specialty/enthusiast/exotic cars | Auction format with sold-price history |
| Hemmings (`hemmings.com`) | Classic and collector cars | Specialty listings |
| DuPont Registry (`dupontregistry.com`) | Luxury and exotic cars | High-end inventory |
| `preowned.ferrari.com` | CPO Ferraris | Factory-certified pre-owned |

### Valuation & Reliability Research

| Need | Source | Query pattern |
|------|--------|---------------|
| Recalls by VIN | `nhtsa.gov/recalls` | Fetch directly with 17-char VIN |
| Complaint clusters | `carcomplaints.com` | `"[year] [model] problems site:carcomplaints.com"` |
| Repair cost estimates | `repairpal.com` | `"[model] [repair] cost repairpal"` |
| Fair market value | KBB, Edmunds, `cargurus.com` (shows days-on-market + deal rating) | |
| TSBs (technical service bulletins) | `nhtsa.gov` or `"[model] TSB [symptom]"` | |
| Long-term reliability | `dashboard-light.com`, Consumer Reports (paywalled — search `"consumer reports [model] reliability reddit"` for summaries) | |

## Known Problem Models — Flag These Immediately

When user mentions any of these, warn before discussing price:

| Avoid | Years | Issue | Failure cost |
|-------|-------|-------|--------------|
| Ford Focus/Fiesta | 2011-2016 | PowerShift DCT — shudder, slip, class-action settled | $3-4k transmission |
| Nissan Altima/Sentra/Rogue/Pathfinder/Versa | 2013-2017 (worst) | Jatco CVT — overheats, limp mode, ~120k mi lifespan | $3.5-5k |
| Subaru (most) | 2012-2017 | CVT (warranty extended); also head gasket <2012 | $2-7k |
| BMW/Audi/Mercedes | Any out of warranty | Normal wear = premium parts/labor; depreciates 50%+ by yr 5 | Budget $2-3k/yr |
| Hyundai/Kia 2.0/2.4L Theta II | 2011-2019 | Rod bearing failure, engine seizure (recall) | Engine replacement |
| Chevy Cruze | 2011-2015 | Coolant leaks, turbo failure | $1-2k recurring |

**CVT buying rule:** Demand transmission fluid service records. No CVT service by 60k mi (4-cyl) or 80k mi (6-cyl) → walk away. Metal belt on metal pulleys — old fluid = cooked transmission.

**Safe defaults:** Toyota Corolla/Camry, Honda Civic/Accord (Honda's CVT is the exception — reliable), Mazda3/6, Lexus anything. Toyota Prius 2009-2020: 5/5 CR reliability every year.

## Exotic & Luxury Cars — Different Rules Apply

Exotic and high-performance cars (Ferrari, Lamborghini, Porsche, McLaren, Aston Martin, Maserati, etc.) operate on completely different cost structures. Flag this immediately when a user asks about these brands.

| Item | Mainstream | Exotic/Luxury |
|------|-----------|---------------|
| Pre-purchase inspection | $100-200 | $300-500+ (specialist required) |
| Annual maintenance budget | $500-1,500 | $5,000-10,000+ |
| Transmission repair | $2-5K | $15-25K+ |
| Engine work | $3-8K | $15-50K+ |
| Brake job (all 4) | $300-800 | $3,000-8,000 (carbon ceramics: $15K+) |
| Tires | $400-800/set | $1,500-4,000/set |

**Exotic car buying rules:**

- Always use a marque specialist for PPI, not a general mechanic
- Search sold listings on Bring a Trailer for real transaction prices — asking prices on exotics are often negotiable by 10-20%
- Check brand-specific CPO programs (Ferrari Approved, Porsche CPO, Lamborghini Selezione) — CPO warranty can save tens of thousands
- Service history is everything. Incomplete records on an exotic = walk away
- Join model-specific forums (FerrariChat, Rennlist, Lamborghini-Talk) for ownership cost reality checks

## Listing Red Flags

| Flag | What it means |
|------|---------------|
| Salvage/rebuilt/"clean rebuilt" title | Totaled once. Insurance may refuse comprehensive. -40% value |
| Price 20%+ below CarGurus "great deal" | Scam or undisclosed damage |
| "Runs great, just needs [X]" | If it were cheap to fix, they'd have fixed it |
| Odometer ends in 000 / round number | Possible rollback; cross-check Carfax mileage entries |
| No cold-start video on request | Hiding startup rattle (timing chain) or blue smoke (rings) |
| Dealer add-ons mandatory (nitrogen tires, VIN etching, "protection package") | Junk fees — refuse or walk |

## Fair Price Method

1. Pull KBB private-party + Edmunds TMV + 3 comparable CarGurus listings (same trim, ±15k miles, ±100 mi radius)
2. Average them, then adjust: below-avg miles +5-10%, accident on Carfax -10-25%, one-owner +$500, new tires +$400-800
3. Check days-on-market (CarGurus shows this). >45 days = dealer is motivated
4. Subtract cost of any needed work (use RepairPal estimates)

## Negotiation Playbook

**Before contact:**

- Get pre-approved financing from a credit union — removes dealer's biggest profit lever
- Find 2-3 competing listings for the same model. Screenshot them.

**The ask:**

- Email/text only until price is locked. Never negotiate on the lot.
- Ask for **out-the-door (OTD) price** — tax, title, doc fee, everything. "What's the OTD on stock #____?" Refuse to discuss monthly payments.
- Open at 10-15% below ask (dealer) or 15-20% (private). Anchor with KBB private-party number even at a dealer.
- After your offer: **stop talking.** First to fill silence loses.

**Timing:** Last 2-3 days of the month, weekday morning. Salespeople under quota will eat margin. (Caveat: if they already hit quota this does nothing.)

**Trade-in:** Get it appraised separately (CarMax/Carvana give free written offers). Never let them bundle trade-in value into the purchase — it obscures both numbers.

**Before signing:** Read every line of the buyer's order. Reject "market adjustment," "dealer prep," mandatory add-ons. Verify VIN on contract = VIN on door jamb.

## Pre-Purchase Inspection — Non-Negotiable

$100-200 at an independent shop ($300-500 for exotics — use a marque specialist). NOT the seller's mechanic. Any seller who refuses → walk. Specifically request:

- Compression test (engine health) — all cylinders within 10% of each other
- Scan for stored/pending OBD codes (some sellers clear codes before showing)
- Lift inspection: frame rust, leaks, CV boot tears, uneven tire wear (alignment/suspension)
- **Cold start** — arrive before the seller warms it up. Listen for rattle (timing chain), watch for blue smoke (oil burning) or white smoke that lingers (head gasket)

## Regional Search Strategy

Regional pricing differences can be significant — always search multiple regions:

| Region | Tends to be cheaper for | Watch out for |
|--------|------------------------|---------------|
| Midwest / South | Luxury & exotic cars (lower demand) | Hail damage (Midwest), flood titles (Gulf states) |
| Southwest (AZ, NV, NM) | Older cars (no rust) | Sun damage to paint/interior |
| Northeast / Rust Belt | Nothing specific | Frame rust, salt corrosion — get underbody inspection |
| Florida | High supply of luxury/exotic | Flood damage, hurricane salvage titles |
| California | High supply overall | Higher prices due to demand; emissions compliance |

**Tip:** Search nationwide first to establish fair market price, then narrow by region. A $5K price difference can justify shipping ($500-1,500 depending on distance).

## Comparison Web App

After finding the best options, **build a web app** that displays a visual comparison page so the user can evaluate listings side-by-side.

### Each listing card should show

- **Car image** — use the actual photo scraped from the listing URL. **Never generate, synthesize, or use placeholder images.** Only display real photos from the source listing. If no image can be scraped, show a text-only card instead.
- **Year / Make / Model / Trim**
- **Price** (with deal rating if available: great/good/fair/overpriced)
- **Mileage**
- **Location** (city, state, distance from user)
- **Dealer vs. private seller**
- **Key flags** (known problems, red flags, or positive signals like one-owner/no accidents)
- **Direct link** to the original listing

### Layout

- Card grid or side-by-side layout, sortable by price, mileage, or deal rating
- Highlight the best-value picks visually (border, badge, or background color)
- Include a summary section at the top with the recommendation and why

Always generate the comparison page alongside the text-based analysis — the web app is a visual complement, not a replacement for the detailed evaluation.

## Limitations

- Can't pull VIN history directly (user must buy Carfax/AutoCheck, ~$25-40)
- Regional price variance is real — rust-belt vs. southwest vs. PNW differ 10-15%
- Not a substitute for a physical PPI
