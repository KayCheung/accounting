---
name: content-machine
description: Create social media posts, newsletters, and marketing content calibrated to your voice and platform.
---

# Content Machine

Create social posts, newsletters, and marketing copy that respects platform mechanics — truncation points, algorithm signals, and hook physics — not just "good writing."

## When to Use

- Social posts (X/Twitter, LinkedIn, Instagram, TikTok captions, Threads)
- Newsletters, blog posts, content calendars, cross-platform repurposing

## When NOT to Use

- Cold outreach (cold-email-writer) · Paid ad copy (ad-creative) · Research reports (deep-research) · SEO audits (seo-auditor)

## Step 1: Voice Analysis

Ask for 3-5 existing posts. Extract: avg sentence length, contraction usage, emoji density, POV (I/we/you), signature phrases. If none exist, ask for 2 creators they want to sound like and use `webFetch` to pull recent posts as voice reference.

## Step 2: Platform Mechanics (2025-2026 Specs)

### LinkedIn — 3,000 char max, but truncation is what matters

- **"...see more" cutoff: ~140 chars desktop, ~110 chars mobile.** 57%+ of LinkedIn traffic is mobile — write the hook for 110 chars.
- **Algorithm weights:** comments count ~2x likes; dwell time is a primary signal; first 60-120 min engagement velocity determines reach ceiling.
- **Optimal length:** 800-1,000 chars (not 3,000). Short paragraphs (1-2 lines) + white space increase dwell time.
- **Structure:** Hook (110 chars, no throat-clearing like "I wanted to share...") → story/insight → single question CTA. Reply to every comment in the first hour to extend the test window.
- **Hashtags:** 3-5 max, at the very end. LinkedIn deprioritized hashtag discovery.

### X/Twitter — 280 chars (free), 25,000 chars (Premium)

- Long posts on Premium truncate at ~280 chars in the feed — the hook rule still applies.
- **Thread structure:** Tweet 1 = the full promise ("How I went from X to Y in Z — thread 🧵"). Each tweet must stand alone for retweets. Last tweet = CTA + loop back to tweet 1.
- Line breaks double engagement vs. wall-of-text.

### Instagram — 2,200 char caption, ~125 chars visible before "...more"

- **Hashtags: 3-5, not 30.** Instagram's @creators account officially reversed the old advice; 20+ hashtags now reads as spam and can suppress reach. Put them inline or at the end, not in a comment.
- First line = hook. Emoji as bullet points scan faster than dashes on mobile.

### TikTok captions — 4,000 chars (up from 2,200)

- TikTok is now a search engine — ~40% of Gen Z searches here before Google. Front-load keywords in the caption for TikTok SEO. The caption is indexed; use it for terms your video doesn't say out loud.

### Newsletters — Optimize for clicks, not opens

- **Apple Mail Privacy Protection (MPP) inflates open rates by ~18 percentage points.** Apple Mail is ~46% market share and pre-fetches tracking pixels. A "42% open rate" in 2025 ≈ a 24% open rate in 2020. **Track click rate (benchmark: ~2%) and CTOR (10-20%) instead.**
- **Subject line:** 30-50 chars. Avoid "Free," ALL CAPS, multiple "!!!" — spam filter triggers. B2B: longer, specific subject lines outperform short clever ones.
- **Preview/preheader text:** adds ~6pp to open rate when used — but Gmail's Gemini now auto-generates previews, so don't rely on controlling it. Write the first sentence of the body as a second hook.
- One primary CTA. Every additional CTA cuts click rate.

## Step 3: Hook Formulas (Named Patterns)

Don't say "write a hook" — pick a pattern:

| Pattern | Template | Why it works |
|---|---|---|
| **Contrarian** | "Everyone says X. Here's why that's wrong." | Cognitive dissonance forces resolution |
| **Curiosity gap** | "I tried X for 30 days. Day 17 broke me." | Open loop — brain needs closure |
| **Specificity signal** | "$47,212 in 90 days. Here's the exact stack." | Odd numbers read as true, round numbers read as marketing |
| **Negative hook** | "3 mistakes that cost me [outcome]" | Loss aversion > gain seeking |
| **Callout** | "If you're a [role] still doing X, read this." | Self-selection = higher-intent readers |
| **Slippery slope** | "It started with one Slack message." | Narrative momentum |
| **Permission** | "Unpopular opinion: [take]" | Pre-frames disagreement as expected |

**Banned openers:** "I'm excited to share," "Hey everyone," "As a [title]," "In today's fast-paced world."

## Step 4: Repurposing Waterfall

One long-form piece → 6+ assets:

1. **Blog post** (1,500 words) →
2. **X thread** (extract each H2 as a tweet, intro = hook) →
3. **LinkedIn post** (pick the single most contrarian point, 800 chars) →
4. **LinkedIn carousel** (each H2 = 1 slide; carousels get highest dwell time) →
5. **Newsletter section** (add personal context + behind-the-scenes) →
6. **Instagram carousel** (same slides, 1080×1350, 4:5) →
7. **TikTok/Reel script** (the hook + the #1 takeaway in 30 sec)

Build repurposing scripts in Python when batch-processing: parse markdown H2s → split into platform templates → enforce char limits programmatically.

## Content Frameworks

- **PAS** (Problem → Agitate → Solve) — best for conversion-focused posts
- **BAB** (Before → After → Bridge) — best for transformation stories
- **AIDA** (Attention → Interest → Desire → Action) — best for launches
- **SLAP** (Stop → Look → Act → Purchase) — best for short-form (Reels/TikTok captions)

## Validation

Before delivering, verify: char counts against platform limits (count programmatically, don't eyeball), hook fits in the truncation window, no banned openers, one CTA per piece.

## Limitations

- Cannot post to platforms or access analytics
- Cannot generate images/video (use media-generation skill)
- Voice matching quality scales with example count
