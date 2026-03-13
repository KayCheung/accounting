---
name: seo-auditor
description: Audit websites for SEO issues and optimize content for search engine visibility.
---

# SEO Auditor & Content Optimizer

Audit websites for technical SEO issues, analyze on-page optimization, and provide actionable recommendations to improve search engine visibility and rankings.

## When to Use

- User wants an SEO audit of their website
- User asks how to improve search rankings
- User wants to optimize content for specific keywords
- User needs meta tag, title, or description improvements
- User wants to compare their SEO against competitors

## When NOT to Use

- Paid advertising strategy (use ad-creative skill)
- Social media content creation (use content-machine skill)
- General competitive analysis without SEO focus (use competitive-analysis)
- Building pages at scale for SEO (use programmatic-seo skill)

## Methodology

### Audit Priority Order

1. **Crawlability & Indexation** (can Google find and index it?)
2. **Technical Foundations** (is the site fast and functional?)
3. **On-Page Optimization** (is content optimized?)
4. **Content Quality** (does it deserve to rank?)
5. **Authority & Links** (does it have credibility?)

### Step 1: Crawlability & Indexation

**Robots.txt:**

- Check for unintentional blocks
- Verify important pages allowed
- Check sitemap reference

**XML Sitemap:**

- Exists and accessible
- Contains only canonical, indexable URLs
- Updated regularly

**Site Architecture:**

- Important pages within 3 clicks of homepage
- Logical hierarchy
- No orphan pages (pages with no internal links)

**Index Status:**

- site:domain.com check
- Compare indexed vs. expected page count

**Indexation Issues:**

- Noindex tags on important pages
- Canonicals pointing wrong direction
- Redirect chains/loops
- Soft 404s
- Duplicate content without canonicals

**Canonicalization:**

- All pages have canonical tags
- HTTP → HTTPS canonicals
- www vs. non-www consistency
- Trailing slash consistency

### Step 2: Technical Foundations

**Core Web Vitals (2025-2026):**

- **LCP** (Largest Contentful Paint): < 2.5s
- **INP** (Interaction to Next Paint): < 200ms — replaced FID in 2025 as the responsiveness metric
- **CLS** (Cumulative Layout Shift): < 0.1

**Speed Factors:**

- Server response time (TTFB)
- Image optimization and modern formats (WebP)
- JavaScript execution and bundle size
- CSS delivery
- Caching headers and CDN usage
- Font loading strategy

**Mobile-Friendliness:**

- Responsive design (not separate m. site)
- Tap target sizes
- Viewport configured
- No horizontal scroll
- Mobile-first indexing readiness

**Security:**

- HTTPS across entire site
- Valid SSL certificate
- No mixed content
- HSTS header

**URL Structure:**

- Readable, descriptive URLs
- Keywords where natural
- Consistent structure (lowercase, hyphen-separated)
- No unnecessary parameters

**Note:** Google now excludes pages returning non-200 status codes (4xx, 5xx) from the rendering queue entirely — critical for SPAs.

### Step 3: On-Page SEO

**Title Tags:**

- Unique per page, 50-60 characters
- Primary keyword near beginning
- Compelling and click-worthy
- Brand name at end
- Common issues: duplicates, too long/short, keyword stuffing, missing

**Meta Descriptions:**

- Unique per page, 150-160 characters
- Includes primary keyword
- Clear value proposition with CTA
- Common issues: duplicates, auto-generated, no compelling reason to click

**Heading Structure:**

- One H1 per page containing primary keyword
- Logical hierarchy (H1 → H2 → H3, no skipping)
- Headings describe content, not used just for styling

**Content Optimization:**

- Keyword in first 100 words
- Related keywords naturally used
- Sufficient depth for topic
- Answers search intent
- Better than current top-ranking competitors

**Image Optimization:**

- Descriptive file names and alt text
- Compressed file sizes, modern formats (WebP)
- Lazy loading, responsive images

**Internal Linking:**

- Important pages well-linked with descriptive anchor text
- No broken internal links
- No orphan pages

**Keyword Targeting (per page):**

- Clear primary keyword target
- Title, H1, URL aligned with keyword
- Content satisfies search intent
- Not competing with other pages (cannibalization)

### Step 4: Content Quality — E-E-A-T Signals

**Experience:** First-hand experience demonstrated, original insights/data, real examples
**Expertise:** Author credentials visible, accurate and detailed information, properly sourced claims
**Authoritativeness:** Recognized in the space, cited by others, industry credentials
**Trustworthiness:** Accurate information, transparent about business, contact info available, privacy policy, HTTPS

### Step 5: Bot Governance & AI Readiness

- Review robots.txt to differentiate between beneficial retrieval agents (OAI-SearchBot, Googlebot) and non-beneficial training scrapers
- Use structured data (schema.org) as the language of LLMs
- Use "BLUF" (Bottom Line Up Front) formatting to help content get cited in AI Overviews

**Schema Markup Detection Warning:** `webFetch` and `curl` cannot reliably detect structured data — many CMS plugins inject JSON-LD via client-side JavaScript. Never report "no schema found" based solely on webFetch. Recommend using Google Rich Results Test or browser DevTools for accurate schema verification.

### Step 6: Competitor SEO Comparison

- Search for target keywords and analyze top-ranking pages
- Identify content gaps and opportunities
- Compare meta tags, content depth, structure, and E-E-A-T signals

## Common Issues by Site Type

**SaaS/Product Sites:** Product pages lack content depth, blog not integrated with product pages, missing comparison/alternative pages, thin feature pages
**E-commerce:** Thin category pages, duplicate product descriptions, missing product schema, faceted navigation creating duplicates
**Content/Blog Sites:** Outdated content not refreshed, keyword cannibalization, no topical clustering, poor internal linking
**Local Business:** Inconsistent NAP, missing local schema, no Google Business Profile optimization

## Output Format

### SEO Audit Report Structure

```text

# SEO Audit Report: [Website]

## Executive Summary

- Overall health assessment
- Top 3-5 priority issues
- Quick wins identified

## Critical Issues (Fix Immediately)
| Issue | Page | Impact | Evidence | Fix |
|-------|------|--------|----------|-----|

## High-Impact Improvements
| Issue | Page | Impact | Evidence | Fix |
|-------|------|--------|----------|-----|

## Quick Wins
| Opportunity | Page | Potential Impact |
|------------|------|-----------------|

## Page-by-Page Analysis

### [Page URL]

- **Title**: Current | Recommended
- **Meta Description**: Current | Recommended
- **H1**: Current | Recommended
- **Content Score**: X/10
- **Issues**: [list]

## Prioritized Action Plan

1. Critical fixes (blocking indexation/ranking)
2. High-impact improvements
3. Quick wins (easy, immediate benefit)
4. Long-term recommendations

```

## Tools

**Free:** Google Search Console, Google PageSpeed Insights, Rich Results Test (use for schema validation — it renders JavaScript), Mobile-Friendly Test, Schema Validator
**Paid (if available):** Screaming Frog, Ahrefs / Semrush, Sitebulb

## Best Practices

1. **Prioritize by impact** -- fix critical issues before optimizing nice-to-haves
2. **Write for humans first** -- keyword-stuffed content hurts rankings
3. **Check actual SERPs** -- search for target keywords to understand what Google currently rewards
4. **Focus on search intent** -- match content type to what users actually want
5. **Monitor competitors** -- see what top-ranking pages do well and identify gaps

## Limitations

- Cannot access Google Search Console or Analytics data
- Cannot measure actual page speed (use Google Lighthouse separately)
- Cannot check backlink profiles (recommend Ahrefs, Semrush, or Moz)
- Cannot run full site crawls (recommend Screaming Frog or Sitebulb)
- Cannot guarantee ranking improvements -- SEO involves many factors
- Cannot access pages behind authentication or paywalls
