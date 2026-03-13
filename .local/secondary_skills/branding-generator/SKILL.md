---
name: branding-generator
description: Create brand identity kits with color palettes, typography, logo concepts, and brand guidelines.
---

# Branding Generator

Create brand identity kits. Interview the user, research the space, then deliver 3 distinct brand directions with visual assets.

## When to Use

- "I need branding / a brand identity / brand kit"
- Color palettes, typography, visual identity from scratch
- Rebranding or brand refresh

## When NOT to Use

- Full UI design (use design skill) · Slide decks (use slides skill)

## Step 1: Brand Interview

Conduct this like a real branding agency discovery session. Ask these questions **conversationally, not as a wall of text** — adapt based on answers, ask follow-ups, go deeper where it matters. Group into 2-3 messages max.

### Round 1 — The Business

- What does your company/product do, in one sentence?
- Who is your target audience? (Be specific — age, role, lifestyle, not just "everyone")
- What problem do you solve that nobody else does?
- What's your pricing position? (Budget / mid-market / premium / luxury)

### Round 2 — The Feeling

- Name 3 brands you admire (any industry) and what you admire about them
- If your brand were a person, how would they dress? How would they speak?
- What emotions should someone feel when they see your brand for the first time?
- What's the one word you'd want people to associate with you?
- Any colors, styles, or aesthetics you absolutely hate?

### Round 3 — Practical Constraints

- Do you have any existing brand assets (logo, colors, fonts) you want to keep?
- Where will this brand primarily live? (Web app, mobile app, physical product, social media, print)
- Any industry conventions you need to follow — or deliberately break?
- Competitor URLs or screenshots? (If provided, extract their palettes with colorthief for contrast analysis)

**Do not proceed until you have solid answers.** Push back if answers are vague — "everyone" is not a target audience, "clean and modern" is not a personality.

## Step 2: Research

After the interview, do targeted research before generating directions:

- **Competitor analysis** — If the user named competitors or you can infer them, search for their visual identities. Extract color palettes from screenshots using colorthief. Note what's common in the space so you can differentiate.
- **Mood/reference gathering** — Search for visual references matching the interview answers (e.g., "minimalist premium SaaS branding", "bold playful fintech design").
- **Industry conventions** — What do users in this space expect? Where is there room to stand out?

## Step 3: Generate 3 Brand Directions

**Always present exactly 3 distinct directions.** Each should feel like a different creative team's pitch — not slight variations.

For each direction, provide:

1. **Concept name & 1-2 sentence narrative** — the strategic thinking behind this direction
2. **Color palette** — primary, secondary, accent with hex + OKLCH values. Include neutral scale (50-900) tinted toward the primary hue. Verify WCAG AA contrast for all text/background pairs.
3. **Typography** — display + body font pairing from Google Fonts with rationale
4. **Voice** — 3-5 adjectives defining how the brand speaks, plus an example headline
5. **Visual mood** — overall aesthetic description (photography style, illustration approach, texture usage). Reference 2-3 real-world brands that share elements.

## Step 4: User Picks a Direction

Present all 3 and ask the user to pick one or mix elements. Don't proceed to assets until they approve.

## Step 5: Deliver the Brand Kit

Once a direction is approved, **delegate to the design subagent** (`subagent` with `specialization="DESIGN"`) to build polished visual boards. Embed them as iframes on the canvas.

### Deliverables

**Board 1 — Color & Typography:** Color swatches with hex + OKLCH values, shade ramps (50-900), typography specimen at heading/body/caption sizes with Google Fonts loaded, contrast audit table, dark mode variant.

**Board 2 — Logo Concepts:** 3-4 logo variations (wordmark, icon+text, icon-only) built as **inline SVG**. Show on light and dark backgrounds at multiple sizes. Include SVG source for export.

**Board 3 — Brand in Action:** A realistic mock landing page using the brand's colors, fonts, and voice. Should look like a real product page, not a wireframe.

**Board 4 — Brand Guidelines:** Color usage rules, typography hierarchy, voice & tone guidelines, 1-2 sample applications (business card, social post).

### Exportable Tokens

Also provide in chat:

- **CSS custom properties** — `--primary: #2563eb; --primary: oklch(60% 0.18 250);`
- **Tailwind config** — `colors: { primary: { 50: '...', ..., 900: '...' } }`

## Color Science

- Work in OKLCH color space (perceptually uniform — same L = same perceived lightness across hues)
- Use color harmony from OKLCH hue space: complementary (H+180°), analogous (H±30°), triadic (H±120°), split-comp (H+150°/H+210°)
- Generate shade ramps by stepping L linearly in OKLCH — avoids the muddy-middle problem of RGB interpolation
- WCAG 2.2 AA: 4.5:1 contrast for normal text, 3:1 for large text and UI components
- Use chroma-js or apcach for programmatic contrast verification
- Dark mode: backgrounds at `oklch(15-20% 0.01 H)` not pure black. Desaturate brand colors slightly (reduce C by ~0.02).

## Limitations

- Logo concepts are starting points — final production logos should be refined with a dedicated designer
- Fonts limited to Google Fonts / open-source unless user provides custom fonts
