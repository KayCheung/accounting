---
name: apartment-finder
description: Find apartments for rent, evaluate listings, detect scams, analyze neighborhoods, and compare rental options. Use when the user asks to find, evaluate, or compare apartments or rental housing.
---

# Apartment Finder

Find real listings, evaluate them, detect scams, vet neighborhoods, and flag lease traps.

## When to Use

- Finding apartments in a specific city/area matching user criteria
- Evaluating a specific listing (especially Craigslist/FB Marketplace)
- Comparing units on true cost, not sticker rent
- Lease review before signing

## When NOT to Use

- Home buying; active lease disputes (use legal-contract)

## Search Methodology

When a user asks to find apartments, run a multi-site search and return specific units with addresses, prices, and direct links. Users do not want generic search page links — they want listings they can act on.

### Search Process

1. Determine criteria from the user: city/neighborhood, bedrooms, max budget, preferences (waterfront, views, parking, pets, etc.)
2. Search all major sites in parallel using `webSearch`:
   - **Zillow** (`zillow.com [city] [beds] bedroom [type] for rent [neighborhood] [preferences]`)
   - **Apartments.com** (`apartments.com [city] [beds] bedroom [neighborhood] rent`)
   - **Redfin** (`redfin.com [city] [beds] bedroom [type] for rent [neighborhood]`)
   - **Craigslist** (`craigslist [city] [beds] bedroom [type] [neighborhood] rent`)
3. Fetch actual listing pages using `webFetch` on the most relevant result URLs to extract specific unit data (addresses, prices, sqft, features)
4. Present individual units — each listing needs: address, unit number, price, beds/baths, sqft, key features, and a direct link

### Listing Type Preferences

Prefer individual units over managed-building "starting at" prices. Large apartment complexes advertise teaser "starting at" rates that are often for unavailable or least-desirable units. When possible:

- Search for condos for rent, townhouses for rent, and "by owner" listings
- Filter by property type = condo/townhouse on Redfin and Zillow
- On Craigslist, use the "housing by owner" filter
- On Apartments.com, look for "for rent by owner" listings
- Present the actual asking price for the specific unit, not "starting at" ranges
- When large buildings are included, note that the listed price is a floor and the user should expect higher for desirable units/floors.

### Price Verification — Unit vs. Building

Listing sites often show a building's lowest "starting at" price that may be for a different unit type, floor plan, or even bedroom count than what the user asked for. Before presenting any listing:

1. **Confirm the price is for the right bedroom count.** A building page might say "$3,450+" but that's for a 1BR — the 2BR starts at $5,200. Always drill into the specific floor plan.
2. **Confirm the unit actually exists at that price.** "Starting at" prices on Apartments.com, Zillow building pages, and RentCafe are often for a single unit or a unit that's already leased. If you can see individual unit prices on the page, quote those instead.
3. **Quote specific unit prices when available.** If a building has Unit 4B at $5,250 and Unit 7A at $5,800, present those individually — don't say "$5,250+". The user can evaluate each one.
4. **Flag uncertainty.** If you can only find a building-wide range and not a specific unit price, say so explicitly: "Building lists 2BRs from $X,XXX but specific unit pricing requires contacting leasing office."
5. **Watch for furnished vs. unfurnished.** Some sites (Blueground, Zeus, corporate housing) list furnished units at 30-50% premiums. Always note if a price includes furnishing.

### Output Format

Present listings in tiers based on value relative to the user's budget:

```text
**[Address, Unit #]** — **$X,XXX/mo** | Xbd/Xba | X,XXX sq ft
- [Key feature 1], [key feature 2], [key feature 3]
- [Why this is good value / what stands out]
- [Direct link to listing](URL)
```

Group into:

- **Best Value** — significantly under budget, punches above its price
- **Strong Mid-Range** — solid quality at a fair price within budget
- **Top of Budget** — premium units near the ceiling that justify the spend

After the listings, include:

- A "Top Picks" summary (2-3 sentences on the best overall deals and why)
- Filtered search links for each site so the user can monitor new listings
- True cost notes (parking, utilities, fees) relevant to the market

### Scam Warnings on Presented Listings

When presenting any listing, actively scan for red flags and warn the user inline. Do not present a suspicious listing without a warning attached.

Auto-flag if any of these are true:

- Price is 30%+ below neighborhood average for its size/type — add: "Price is well below market. Verify this is real before sending any money."
- Listing source is Craigslist or FB Marketplace with no verifiable landlord identity — add: "Listed on [platform] with no identity verification. Confirm ownership via county assessor before touring."
- Photos look professional but listing is "by owner" at a suspiciously low price — add: "Reverse-image-search the photos to confirm they aren't stolen from another listing."
- Listing says "no background check" or "no credit check" — add: "Legitimate landlords almost always run background/credit checks. Proceed with caution."
- Any mention of mailing keys, wiring money, or inability to show the unit in person — add: "DO NOT send money. This matches a known scam pattern."

For every Craigslist/FB listing presented, append this standard note:

> Craigslist/FB listings have no identity verification. Before sending any deposit: tour the unit in person, verify the owner via county property records, and pay only by check to a verified business entity. Never pay via Zelle/Venmo/CashApp/wire/gift card.

For individual condo/owner listings from any site, note:

> Private owner listing — verify ownership at [county] county assessor's website before signing anything or sending money.

### Search Queries That Work

Tailor queries to each site's strengths:

| Site | Best query pattern | What it finds well |
|------|-------------------|-------------------|
| Zillow | `[city] [neighborhood] [beds] bedroom condo for rent` | Individual owner-listed condos, specific addresses |
| Redfin | `[city] [neighborhood] [beds] bedroom [condo/apartment] for rent` | Condos for rent with MLS-quality data, specific units |
| Apartments.com | `[city] [neighborhood] [beds] bedrooms` | Both complexes and individual units, good amenity filters |
| Craigslist | `[city metro] [beds]br [neighborhood] [preferences]` | Private owner deals, below-market gems, fast turnover |
| Homes.com | `[city] [neighborhood] condos for rent [beds] bedrooms` | Individual condo units with detailed pricing |

Run at least 2 rounds of searches: a broad initial sweep, then targeted follow-ups on the most promising neighborhoods/buildings using `webFetch` on specific listing pages.

### Identifying "Underpriced" Listings

When the user wants deals or value:

- Search for average rent in the target neighborhood using `webSearch`
- Flag any listing priced 15%+ below neighborhood average for its size
- Look for: older buildings with recent renovations, move-in concessions (1 month free, weeks free), private owners pricing to fill quickly (vacant 30+ days), winter/off-season listings
- Check for concession-adjusted effective rent (e.g., 1 month free on 12-month lease = ~8% discount)

## Scam Detection — Named Patterns

Craigslist and FB Marketplace have no identity verification — scammers exploit this. FB is worse now because aged accounts with fake friends buy false trust.

**The "overseas landlord" script** (most common): Owner is abroad for work/military/missionary trip → can't show unit but will "mail keys" after deposit → below-market rent → no background check needed → pushes Zelle/Venmo/CashApp → disappears. If any two of these appear together, stop.

**The hijacked listing:** Scammer copies photos/description from a real Zillow/Realtor.com listing, reposts at ~50% of real rent. **Verification:** reverse-image-search the photos (images.google.com); webSearch the address on Zillow — if it's listed for sale or at a much higher rent, the cheap listing is fake.

**The "Zelle stopped working" escalation:** After partial Zelle payment, claims a technical issue and pivots to gift cards. Gift cards = 100% scam, zero exceptions.

| Hard rule | Why |
|-----------|-----|
| Never pay via Zelle/Venmo/CashApp/wire/crypto/gift card for deposit | Irreversible. Legitimate landlords accept checks to a business entity |
| Never pay before in-person tour of the actual unit | "Video tour" is not sufficient |
| Ask a question only someone who's been inside can answer | "What brand is the stove?" "Which wall has the thermostat?" Scammers can't answer |
| Verify owner via county assessor website | webSearch `"[county name] property records"` — public and free. Name should match |
| Reverse-search the "landlord's" profile photo | Stolen photos are standard |

**FB profile tells:** <50 friends OR hundreds of friends all from one foreign country; account created recently; no posts before the last few months.

## Neighborhood Research (webSearch/webFetch)

| Data | Source |
|------|--------|
| Crime by block | `crimemapping.com`, `spotcrime.com`, or `[city] police department crime map` |
| Walk/Transit/Bike scores | `walkscore.com/[address]` |
| Noise (flight paths, highways, rail) | `howloud.com` |
| School ratings | `greatschools.org` (only include if the user asks about schools) |
| Flood risk | `floodfactor.com` (FEMA maps underestimate) |
| Cell coverage | `coveragemap.com` — dead zones are real |
| Sex offender registry | `nsopw.gov` |
| Street-level vibe | Google Street View — check image date, walk the block virtually |

**IRL check:** Visit at 10pm on a Friday and 7am on a weekday. Noise, parking, and neighbor quality only show up in person.

## Lease Red Flags

| Clause | What it actually means | Response |
|--------|------------------------|----------|
| "Joint and several liability" | Roommate skips → you owe 100% of rent, not your share. Landlord can evict all of you for one person's non-payment | Unavoidable in most shared leases — choose roommates like you'd choose a business partner. Get a side agreement in writing |
| Automatic renewal with 60+ day notice | Miss the window → locked into another full year | Calendar the notice deadline the day you sign |
| "As-is" / waives repair duty | Landlord disclaims habitability | Illegal in most states — habitability can't be waived. Cross out |
| Landlord entry without notice | Violates quiet enjoyment | 24-48hr written notice is standard in most states |
| Tenant pays all legal fees regardless of outcome | You lose even if you win | Negotiate to "prevailing party" |
| Non-refundable "deposit" | Deposits are refundable by definition in most states | Fees can be non-refundable; deposits cannot. Get it renamed |

## True Cost Comparison

Rent is 60-80% of actual cost. Build the full picture:

| Line | Notes |
|------|-------|
| Base rent | |
| Utilities not included | Ask for last tenant's avg bills. Electric heat in cold climate can add $200+/mo |
| Parking | Can be $100-400/mo in cities |
| Pet rent + pet deposit | Often $25-75/mo + $200-500 upfront |
| Renters insurance | ~$15-25/mo, often required |
| Laundry | In-unit vs coin-op = ~$40/mo difference |
| Commute delta | 20 extra min each way × gas/transit × 22 days |
| **Move-in:** first + last + security (often 1 mo) + app fee ($25-100) + broker fee (0-15% annual rent in NYC/Boston) | |

Always present true monthly cost alongside base rent when comparing listings.

## Rent Negotiation

Works best in soft markets or with private landlords (corporate property managers have less flexibility).

- **Leverage:** longer lease term (18-24 mo), move-in during winter (Nov-Feb = lowest demand), proof of income >3× rent, 750+ credit, offer to start lease immediately on a vacant unit
- **Ask for:** $50-100/mo off, one month free (amortize it), waived app/admin fee, free parking spot, waived pet deposit
- **Data:** pull 3 comps from Zillow/Apartments.com at lower rent, bring printouts

## Before Move-In

- Video walkthrough with timestamps — every wall, inside every cabinet, run every faucet. Email it to yourself AND the landlord same-day
- Test: all outlets, water pressure hot+cold, all appliances, HVAC in both modes, every window lock, smoke detectors

## Interactive Map — Web App Visualization

After gathering listings, **build a web app** that displays all found apartments on an interactive map so the user can visually compare locations.

### Requirements

- **Color-coded markers** by tier: green = Best Value, blue = Strong Mid-Range, orange = Top of Budget, red = flagged/suspicious
- **Popup on each marker** showing: address, price, beds/baths, sqft, key features, and a link to the listing
- **Sidebar or panel** with the full listing list — clicking a listing pans/zooms to its marker

### Geocoding Addresses

Use the free Nominatim API (OpenStreetMap) to convert addresses to lat/lng — no API key required:

```text
https://nominatim.openstreetmap.org/search?q={url_encoded_address}&format=json&limit=1
```

Rate limit: max 1 request/second. Batch geocode all listings before building the map, and hardcode the coordinates into the app.

Always generate the map alongside the text-based listing output — the map is a visual complement, not a replacement for the detailed listings.

## Limitations

- Cannot access live MLS/listing feeds directly — use `webSearch` and `webFetch` to pull from public listing sites
- Listing data changes daily — always note the search date and tell users to verify availability
- Ownership verification requires user to check county records
- Lease law varies by state — flag issues, recommend local tenant-rights org for specifics
