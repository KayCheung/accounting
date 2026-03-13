---
name: programmatic-seo
description: Build SEO-optimized pages at scale using templates and data (directories, comparisons, locations, integrations)
---

# Programmatic SEO

Build SEO-optimized pages at scale using templates and data. Create page generators that target keyword patterns and produce unique, valuable content for each variation.

## When to Use

- User wants to create many SEO-driven pages from a template (e.g., "[product] vs [competitor]", "[service] in [city]")
- User mentions programmatic SEO, template pages, directory pages, location pages, or comparison pages at scale
- User wants to build an "alternatives to X" page set, integrations directory, or glossary
- User has a data set they want to turn into individual landing pages

## When NOT to Use

- Auditing existing SEO issues (use seo-auditor skill)
- Writing a single blog post or landing page (use content-machine skill)
- One-off competitive analysis (use competitive-analysis skill)

## Core Principles

1. **Unique value per page** — Every page must provide value specific to that page, not just swapped variables in a template
2. **Proprietary data wins** — Hierarchy: proprietary > product-derived > user-generated > licensed > public (weakest)
3. **Subfolders, not subdomains** — `yoursite.com/templates/resume/` not `templates.yoursite.com/resume/`
4. **Match search intent** — Pages must actually answer what people are searching for
5. **Quality over quantity** — 100 great pages beat 10,000 thin ones

## Content Authenticity — Don't Hallucinate Business Data

When building programmatic SEO for **the user's own company**, you will not have access to their internal data (customer stories, case studies, testimonials, product metrics, pricing, team bios, etc.). **Do not fabricate this information.**

**Before generating any company-specific content, ask the user for:**

- Customer names, logos, or testimonials they want featured
- Case study data (metrics, outcomes, quotes)
- Product-specific details (features, pricing tiers, integrations list)
- Any proprietary data that should populate template variables

**If the user hasn't provided this data, default to safe content patterns:**

- Industry research and statistics (sourced via `webSearch`)
- General descriptions of the problem/solution category
- Feature explanations based on what's publicly visible on their site (use `webFetch` on their domain)
- Placeholder blocks clearly marked `[INSERT: customer testimonial]` or `[INSERT: case study metrics]`
- Comparison data pulled from public sources (G2, Capterra reviews via `webSearch`)

**Never generate:** fake customer quotes, fabricated ROI numbers, invented case studies, made-up testimonials, or fictional company metrics. These damage trust and can create legal liability.

For **generic/research topics** (e.g., "[city] cost of living", "[tool A] vs [tool B]", glossary terms), use `webSearch` to gather real data and cite sources.

## Proven Playbooks (Real Traffic Numbers)

| Playbook | URL pattern | Who does it | Scale |
|---|---|---|---|
| **Integrations** | `/apps/[A]/integrations/[B]` | **Zapier** — ~56k pages, 5.8M+ monthly organic visits, ranks for 1.3M keywords. Proprietary data (triggers/templates per app pair) no one else can replicate. | N² combinations |
| **Conversions** | `/currency-converter/[from]-to-[to]-rate` | **Wise** — 8.5M pages across locale subfolders, 60M+ monthly visits. Live exchange-rate data + fee calculators = unique value per page. | N² × locales |
| **Locations** | `/Restaurants-[city]`, `/[cuisine]-Restaurants-[city]`, `/Restaurants-[neighborhood]` | **Tripadvisor** — 700M+ pages, 226M+ monthly visits. UGC reviews keep pages fresh; layered matrix (city × cuisine × neighborhood). | city × category × modifier |
| **Data profiles** | `/[city-slug]` | **Nomad List** — cost-of-living, internet speed, safety scores per city. Pages are pure data tables — minimal prose, high value. | N entities |
| **Comparisons** | `/[A]-vs-[B]`, `/alternatives/[A]` | **G2, Capterra** — "vs" pages + "alternatives" pages, populated by user reviews. | N² / 2 |
| **Templates** | `/templates/[type]` | **Canva, Notion** — each template is a landing page. | N types |
| **Glossary** | `/learn/[term]` | **Ahrefs, HubSpot** — definition pages cluster topical authority. | N terms |
| **Personas** | `/[product]-for-[audience]` | "CRM for real estate agents" | N × M |

**The test:** If your data doesn't meaningfully change between page variations, don't build it. Zapier works because Slack+Asana genuinely differs from Slack+Trello. "Plumber in Austin" vs "Plumber in Dallas" with identical boilerplate = thin content penalty.

Layer playbooks for long-tail: Tripadvisor's "Best Italian Restaurants in Chinatown NYC" = curation × cuisine × neighborhood.

## Implementation

### Step 1: Keyword Pattern Research

- Identify the repeating structure and variables
- Count how many unique combinations exist
- Validate demand: aggregate search volume, distribution (head vs. long tail), trend direction

### Step 2: Data Requirements

- What data populates each page?
- Is it first-party, scraped, licensed, or public?
- How is it updated and maintained?

### Step 3: Template Design

**Page structure:**

- H1 with target keyword
- Unique intro (not just variables swapped — conditional content based on data)
- Data-driven sections with original insights/analysis per page
- Related pages / internal links
- CTAs appropriate to intent

**Ensuring uniqueness — critical to avoid thin content penalties:**

- Conditional content blocks that vary based on data attributes
- Calculated or derived data (not just raw display)
- Editorial commentary unique to each entity
- User-generated content where possible

### Step 4: Internal Linking Architecture

**Hub and spoke model:**

- Hub: Main category page (e.g., "/integrations/")
- Spokes: Individual programmatic pages (e.g., "/integrations/slack-asana/")
- Cross-links between related spokes

Every page must be reachable from the main site. Include XML sitemap and breadcrumbs with structured data.

### Step 5: Indexation Strategy

- Prioritize high-volume patterns for initial crawling
- Noindex very thin variations rather than publishing them
- Manage crawl budget (separate sitemaps by page type)
- Monitor indexation rate in Search Console

### Step 6: Build the Page Generator (Framework Patterns)

**Rendering strategy decision:**

| Page count | Data freshness | Strategy |
|---|---|---|
| <1,000 | Rarely changes | **SSG** — pre-render everything at build |
| 1,000-100,000 | Changes daily/weekly | **ISR** — pre-render popular subset, generate rest on-demand + cache |
| 100,000+ or live data | Real-time (prices, rates) | **ISR with short revalidate** or SSR |

SSG is fastest but build time scales linearly — 50k pages can mean 30+ min builds. ISR is the pSEO sweet spot: instant deploys, pages generate on first request then cache.

**Next.js App Router pattern (`app/[category]/[slug]/page.tsx`):**

```tsx
// Pre-render popular combos at build, generate rest on-demand
export async function generateStaticParams() {
  const popular = await db.query('SELECT slug FROM entities ORDER BY search_volume DESC LIMIT 500');
  return popular.map(e => ({ slug: e.slug }));
  // Return [] to skip build-time generation entirely — all pages ISR-on-demand
}

export const dynamicParams = true;  // allow slugs NOT in the list above (generate + cache on first hit)
export const revalidate = 3600;     // re-generate page at most once/hour when requested

export async function generateMetadata({ params }) {
  const { slug } = await params;
  const entity = await getEntity(slug);
  return {
    title: `${entity.name} — ${entity.category} | Brand`,
    description: entity.summary,
    alternates: { canonical: `https://site.com/${entity.category}/${slug}` },
  };
}

export default async function Page({ params }) {
  const { slug } = await params;
  const entity = await getEntity(slug);
  if (!entity) notFound();  // 404 — don't serve thin pages for bad slugs
  // ... render template with entity data
}

```

**Critical ISR rules:** `generateStaticParams` is NOT re-run on revalidation. Must return an array (even `[]`) or the route becomes fully dynamic. Set `dynamicParams = false` only if you want 404s for anything not pre-generated. `fetch()` calls inside `generateStaticParams` are automatically deduplicated across layouts/pages.

**For nested routes** (`/apps/[appA]/integrations/[appB]`): child `generateStaticParams` receives parent params — generate appB list *per* appA rather than the full N² matrix upfront.

**Astro alternative** (better for content-heavy, less-interactive pages):

```js
// src/pages/[category]/[slug].astro
export async function getStaticPaths() {
  const entities = await loadEntities();
  return entities.map(e => ({ params: { category: e.cat, slug: e.slug }, props: { entity: e } }));
}

```

Astro ships zero JS by default — better Core Web Vitals for pure content pages. No native ISR; use on-demand rendering + CDN cache headers (`Cache-Control: s-maxage=3600, stale-while-revalidate`).

**Sitemaps at scale:** Google's limit is 50,000 URLs per sitemap file. Use `next-sitemap` (Next.js) or custom generation to shard into `sitemap-1.xml`, `sitemap-2.xml`... referenced by a sitemap index. For ISR sites, generate sitemaps server-side from the DB, not at build time. **Warning:** Google will NOT index all pages immediately — indexation at scale takes weeks/months. Prioritize high-volume slugs in the first sitemap.

## Quality Checks

### Pre-Launch

- [ ] Each page provides unique value beyond variable substitution
- [ ] Answers search intent for the target keyword
- [ ] Unique titles and meta descriptions per page
- [ ] Proper heading structure and schema markup
- [ ] Page speed acceptable
- [ ] Connected to site architecture (no orphan pages)
- [ ] In XML sitemap and crawlable

### Post-Launch Monitoring

Track: indexation rate, rankings by page type, traffic, engagement metrics, conversion rate

Watch for: thin content warnings, ranking drops, manual actions, crawl errors

## Common Mistakes

- **Thin content**: Just swapping city names in identical content (Google will deindex)
- **Keyword cannibalization**: Multiple pages targeting the same keyword
- **Over-generation**: Creating pages with no search demand
- **Poor data quality**: Outdated or incorrect information erodes trust
- **Ignoring UX**: Pages that exist for Google but not for users

## Output Format

Deliver both a strategy document and the actual implementation:

1. **Strategy**: Opportunity analysis, chosen playbook(s), keyword patterns, data sources, page count estimate
2. **Template**: URL structure, title/meta templates, content outline, schema markup
3. **Implementation**: Working web application that generates and serves the pages, deployed and accessible

## Limitations

- Cannot access Search Console data to monitor indexation
- Cannot check existing backlink profiles
- Data quality depends on the source — always validate before publishing
- Cannot guarantee rankings — SEO involves many factors beyond on-page optimization
