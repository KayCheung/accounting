---
name: ad-creative
description: Design static ad creatives for social media and display advertising campaigns.
---

# Ad Creative Maker

Design static ad creatives for social media ads, display banners, and digital advertising campaigns. Build production-ready ads via the design subagent and present them as iframes on the canvas.

## When to Use

- User needs ad creatives for Facebook, Instagram, LinkedIn, Google Display, or TikTok
- User wants banner ads or display advertising assets
- User needs multiple ad variants for A/B testing
- User wants ad copy and visual design together
- User wants to iterate on ad creative based on performance data

## When NOT to Use

- Organic social media content (use content-machine skill)
- Video ads or animated content (use storyboard skill for planning)
- Full landing pages (use create-artifact skill)

## Methodology

### Step 1: Creative Brief

Gather these inputs:

- **Platform & Format**: Which platform? (Google Ads, Meta, LinkedIn, TikTok, X/Twitter) Which format? (Search RSAs, feed, stories, display)
- **Objective**: Awareness, consideration, or conversion?
- **Target audience**: Who will see this ad? What stage of awareness? (Problem-aware, solution-aware, product-aware)
- **Key message**: Single most important thing to communicate
- **CTA**: What action should the viewer take?
- **Brand assets**: Logo, colors, fonts, product images
- **Performance data** (if iterating): Which headlines/descriptions are performing best/worst? What angles have been tested?

### Step 2: Platform Specifications (2025-2026)

**Enforce these programmatically.** Count characters in code, don't eyeball.

#### Meta (Facebook/Instagram) — Visual

| Placement | Dimensions | Safe zone (keep critical elements inside) |
|---|---|---|
| Feed (square) | 1080×1080 | ~100px margin all edges |
| Feed (portrait) — **preferred** | 1080×1350 (4:5) | 4:5 outperforms 1:1 on CTR; mobile-first |
| Stories | 1080×1920 | Top 14% (270px) + bottom 20% (380px) = dead zones |
| Reels | 1080×1920 | Top 14% + **bottom 35% (670px)** — like/comment/share UI is taller |
| **Universal safe core** | **1010×1280 centered** | Design inside this box → works everywhere |

Upload at 2x pixel density (2160×2160 for a 1080 slot) for Retina sharpness. JPG/PNG, 30MB max.

**20% text rule: officially removed** — Meta no longer rejects text-heavy images, but the delivery algorithm still quietly throttles them. Keep text minimal; move details to primary text.

#### Meta — Text

| Element | Limit | Notes |
|---|---|---|
| Primary text | 125 chars visible feed / **~72 chars visible in Reels** | Write for 72 |
| Headline | 40 chars rec | Below image |
| Description | 30 chars rec | Often hidden on mobile |

#### Google Ads

**Responsive Search Ads (RSA):**

| Element | Limit | Qty | Rule |
|---|---|---|---|
| Headlines | 30 chars | up to 15, min 3 | Each must work standalone — Google combines randomly |
| Descriptions | 90 chars | up to 4, min 2 | Complement, don't repeat headlines |
| Display path | 15 chars × 2 | — | |

Pin sparingly — pinning drops Ad Strength and limits ML optimization. Supply ≥5 headlines + ≥5 descriptions for ~10% more conversions at same CPA.

**Responsive Display Ads (RDA) — default Display format:**

| Asset | Spec | Qty |
|---|---|---|
| Landscape image | 1200×628 (1.91:1) | up to 15 total |
| Square image | 1200×1200 (1:1) | required |
| Portrait image | 1200×1500 (4:5) | optional, expands inventory |
| Logo square | 1200×1200 (128 min) | up to 5 |
| Logo wide | 1200×300 (4:1) | optional |
| Short headline | 30 chars | up to 5 |
| Long headline | 90 chars | 1 |
| Description | 90 chars | up to 5 |

All images ≤5MB, JPG/PNG only (no GIF in RDA). Keep file size <150KB for fast load.

**Highest-inventory static sizes** (if uploading fixed banners): 300×250 (medium rectangle — most served, works desktop+mobile), 728×90 (leaderboard), 320×50 (mobile banner), 300×600 (half-page — premium CPM, high CTR), 336×280.

**Performance Max:** same asset pool serves across Search/Display/YouTube/Gmail/Maps/Discover. Upload all 3 image ratios + a YouTube video (≤30s) or Google auto-generates one — don't let it.

#### LinkedIn Ads

| Element | Limit |
|---|---|
| Intro text | 150 chars rec (600 max) |
| Headline | 70 chars rec (200 max) |
| Image | 1200×627 (1.91:1) or 1200×1200 |

#### TikTok Ads

1080×1920, 9:16. Ad text: 100 char max (~80 visible). For Spark Ads (boosting organic creator posts), get the authorization code from the creator — Spark Ads outperform In-Feed Ads on engagement because they retain organic engagement metrics.

### Step 3: Define Angles

Before writing individual copy, establish 3-5 distinct angles — different reasons someone would click:

| Category | Example |
|----------|---------|
| Pain point | "Stop wasting time on X" |
| Outcome | "Achieve Y in Z days" |
| Social proof | "Join 10,000+ teams who..." |
| Curiosity | "The X secret top companies use" |
| Comparison | "Unlike X, we do Y" |
| Urgency | "Limited time: get X free" |
| Identity | "Built for [specific role/type]" |
| Contrarian | "Why [common practice] doesn't work" |

### Step 4: Design Principles

**Visual hierarchy (read order):** 1) Hero element → 2) Benefit/offer → 3) CTA → 4) Logo (corner, small).

**Rules:**

- <20 words total on the image — move everything else to primary text
- One focal point. If the eye doesn't know where to land in 0.5s, it's too busy.
- High contrast text/background — verify WCAG 4.5:1 minimum (use `chroma.contrast()` if building programmatically)
- CTA button: contrasting color, rounded corners, verb-first ("Get the guide" not "Learn more")
- Faces looking *toward* the CTA increase click-through (gaze cueing)
- Keep file <150KB for display; Meta accepts up to 30MB but slow loads hurt auction performance

### Step 5: Generate Variations

For each angle, generate multiple variations. Vary:

- **Word choice** — synonyms, active vs. passive
- **Specificity** — numbers vs. general claims ("Cut reporting time 75%" beats "Save time")
- **Tone** — direct vs. question vs. command
- **Structure** — short punch vs. full benefit statement

**Strong headlines:** Specific over vague. Benefits over features. Active voice. Include numbers when possible ("3x faster," "in 5 minutes").

**Strong descriptions:** Complement headlines, don't repeat them. Add proof points, handle objections ("No credit card required"), reinforce CTAs.

### Step 6: Validate and Deliver

Before presenting, check every piece against character limits. Flag anything over and provide a trimmed alternative. Include character counts.

```text

## Angle: [Pain Point — Manual Reporting]

### Headlines (30 char max)

1. "Stop Building Reports by Hand" (29)
2. "Automate Your Weekly Reports" (28)
3. "Reports in 5 Min, Not 5 Hrs" (27)

### Descriptions (90 char max)

1. "Marketing teams save 10+ hours/week with automated reporting. Start free." (73)
2. "Connect your data sources once. Get automated reports forever. No code required." (80)

```

### Step 7: Build Ad Creatives — Design Subagents + Canvas

**Launch a separate design subagent for each variation in parallel** using `startAsyncSubagent`. Each subagent builds one ad variation as a standalone HTML page. This is much faster than building them sequentially.

#### Canvas Layout

**Default to square iframes (1080x1080) for ad creatives** so they fully fit on the canvas without clipping. The HTML inside uses `100vw`/`100vh` to fill the iframe regardless of size. Group by angle in rows, with a landing page iframe alongside:

```text
x=0,    y=0:    [Label: "Angle 1: [Pain Point] — Ad"]
x=0,    y=40:   [Iframe: angle1-ad]              (1080x1080)

x=1200, y=0:    [Label: "Angle 1: [Pain Point] — Landing Page"]
x=1200, y=40:   [Iframe: angle1-landing]         (1280x720)

x=0,    y=1220: [Label: "Angle 2: [Social Proof] — Ad"]
x=0,    y=1260: [Iframe: angle2-ad]              (1080x1080)

x=1200, y=1220: [Label: "Angle 2: [Social Proof] — Landing Page"]
x=1200, y=1260: [Iframe: angle2-landing]         (1280x720)

x=0,    y=2440: [Label: "Angle 3: [Outcome] — Ad"]
x=0,    y=2480: [Iframe: angle3-ad]              (1080x1080)

x=1200, y=2440: [Label: "Angle 3: [Outcome] — Landing Page"]
x=1200, y=2480: [Iframe: angle3-landing]         (1280x720)
```

#### Parallel design subagent delegation

**Launch all variations simultaneously.** Each subagent gets one ad + its landing page. Use `startAsyncSubagent` for each, then `waitForBackgroundTasks` to collect results.

```javascript
// Launch all 3 angles in parallel — each subagent builds one angle's assets
await startAsyncSubagent({
    task: `Create a production-ready ad creative and matching landing page for the following angle.

Brand: [name]
Colors: Primary [hex], Secondary [hex], Accent [hex]
Fonts: [display font], [body font] (load from Google Fonts)
Logo: [path or description]
User's stated style preferences: [include any specific preferences the user mentioned]

**Angle: [Pain Point]**
- Headline: "[headline text]"
- CTA: "[cta text]"

Build these files:

1. **angle1-ad.html** (square, 1:1) — the actual ad creative
2. **angle1-landing.html** (desktop, 1280px wide) — a mock marketing landing page this ad would link to. Include hero section, value props, social proof, and CTA. This should look like a real product page, not a wireframe.

**CRITICAL — viewport-relative sizing for iframes:**
The ad HTML will be embedded in an iframe on a canvas. The content MUST fill the iframe exactly — no overflow, no scrollbars, no clipping. Use this CSS pattern:

\`\`\`css
html, body {
  margin: 0;
  padding: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
.ad-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  /* use vw/vh for all internal sizing too */
}
/* Use vw for font sizes so they scale: e.g., font-size: 5vw; */
/* Use vw/vh for padding/margins: e.g., padding: 3vw; */
\`\`\`

This ensures the ad looks correct at any iframe size — 1080x1080 on canvas, or any other size. **Never use fixed pixel dimensions** (e.g., width: 1080px) for the ad container.

Design rules:
- Each ad must look like a real ad you'd see in your feed — polished, professional, production-ready
- **No gradients** — use flat colors, solid backgrounds, clean color blocking
- Styling must match the brand concept and any specific preferences the user stated (e.g., if they said "minimal" don't make it busy, if they said "bold" don't make it subtle)
- <20 words on the ad image. Visual hierarchy: hero element → benefit → CTA → logo (corner, small)
- High contrast text/background (WCAG 4.5:1 minimum)
- CTA button: contrasting color, rounded corners, verb-first
- Use image generation for hero imagery if no product photo is provided
- Use the brand's actual colors and fonts throughout — the landing page should feel like a continuation of the ad`,
    specialization: "DESIGN",
    relevantFiles: []
});

// Repeat for Angle 2 and Angle 3 in the same message — all 3 launch simultaneously
await startAsyncSubagent({
    task: `... Angle 2: [Social Proof] ...`,
    specialization: "DESIGN",
    relevantFiles: []
});

await startAsyncSubagent({
    task: `... Angle 3: [Outcome] ...`,
    specialization: "DESIGN",
    relevantFiles: []
});

// Wait for all 3 to complete
await waitForBackgroundTasks();
```

After all subagents finish, embed each page as an iframe on the canvas using `apply_canvas_actions`. Tell the user what was created and offer to focus the viewport.

#### Styling rules — no exceptions

- **Viewport-relative sizing.** All ad HTML must use `100vw`/`100vh` for the container and `vw`-based font sizes/padding. Never fixed pixel dimensions for the ad container. The ad must fill whatever iframe it's placed in without clipping or scrollbars.
- **No gradients.** Use flat, solid colors. Color blocking with the brand palette is fine; linear-gradient/radial-gradient is not.
- **Match the concept.** If the user said "minimalist," don't add decorative elements. If they said "bold and energetic," don't make it muted. Re-read the user's stated preferences before delegating.
- **Consistency across angles.** All 3 angles should feel like they're from the same brand — same fonts, same color usage patterns, same visual language. The angles differ in message, not in design system.
- **Landing page = real page.** The mock landing page should look like where the ad actually leads — hero section echoing the ad's message, value props, testimonials/social proof section, and a CTA. Not a wireframe, not a placeholder.

#### Export

The user can screenshot each iframe directly, or open the HTML files in a browser at the desired export size. Since the ads use `vw`/`vh` sizing, they'll adapt to any viewport. For batch export at specific dimensions: `npx playwright screenshot angle1-ad.html --viewport-size=1080,1080`.

## Iterating from Performance Data

When the user provides performance data:

1. **Analyze winners**: Identify winning themes, structures, word patterns, and character utilization in top performers (by CTR, conversion rate, or ROAS)
2. **Analyze losers**: Identify themes that fall flat and common patterns in underperformers
3. **Generate new variations**: Double down on winning themes, extend winning angles, test 1-2 new unexplored angles, avoid patterns from underperformers
4. **Document the iteration**: Track what was learned, what's being tested, and what angles were retired

## Research Before Writing

Use `webSearch` to find examples of top-performing ads in the user's vertical. Search for ad breakdowns, swipe files, and case studies — e.g. `webSearch("[industry] top performing Facebook ads 2026")` or `webSearch("[industry] TikTok ad examples")`. The TikTok Creative Center and Meta Ad Library are useful reference sites but require direct browser interaction to filter; web search can surface articles and analyses that reference their data. Reverse-engineer: what hook, what angle, what visual pattern. Don't guess what works — look it up.

## Common Mistakes

- Writing RSA headlines that only work in sequence (Google combines them randomly — each must stand alone)
- Ignoring the Reels 72-char visible limit (writing for the 125-char feed limit → truncated on Reels)
- All variations = same angle reworded (vary the *psychology*, not the synonyms)
- Placing text in the bottom 35% of a 9:16 ad (covered by UI on every platform)
- Retiring creative before 1,000+ impressions per variant
- Letting Performance Max auto-generate video — always supply your own

## Limitations

- Cannot run or measure ad campaigns
- Cannot access ad platform analytics
- Cannot create animated or video ads
- Image generation requires the media-generation skill
