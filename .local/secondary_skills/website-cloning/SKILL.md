---
name: website-cloning
description: Clone any website as a deployable React + Vite web app with real scraped content (images, text, structure, colors, fonts). Use when the user asks to clone, replicate, copy, or rebuild an existing website.
---

# Website Cloning

Clone a website's design and layout into a React + Vite web app using real scraped content from the target site.

## Legitimate Use Policy

**Before cloning, you MUST confirm the user's intent is legitimate.** Ask the user directly:

1. **"Is this your own website or your client's website?"** — Cloning your own site (e.g., to rebuild on a new stack, create a staging copy, or migrate platforms) is always fine.
2. **"What is this clone for?"** — Acceptable purposes include:
   - Rebuilding your own site on a new framework
   - Creating a design reference/inspiration starting point (with significant modifications planned)
   - Learning how a layout or component works
   - Building a staging/test version of a site you own
   - Migrating a client's site to a new platform with their permission

**REFUSE to proceed if any of these apply:**

- The user wants to impersonate another business or individual
- The clone will be used to collect credentials, payment info, or personal data from visitors who believe they're on the original site (phishing)
- The user wants to create a lookalike site to redirect or steal traffic from the original
- The clone copies trademarked branding (logos, brand names) of a business the user does not own, without plans to replace them
- The user explicitly states intent to deceive visitors about who operates the site

**When in doubt, ask.** A simple "What's this clone for?" usually clarifies intent. Most users have perfectly legitimate reasons — rebuilding their own site, learning from good design, or migrating platforms. Don't be overly suspicious, but do confirm before proceeding.

**Required modifications for non-owned sites:** If the user is cloning a site they don't own (for design inspiration), remind them to:

- Replace all logos, brand names, and trademarks with their own
- Replace product data, pricing, and business-specific content
- Change contact information, social links, and legal pages
- Treat the clone as a design template, not a finished product

## Overview

This skill uses Playwright (system Chromium) to scrape a target website's visual structure, content, images, colors, fonts, and layout — then builds a faithful React + Vite clone using that data. The clone uses real CDN image URLs, real text, real navigation links, and real design tokens extracted from the live site.

## Prerequisites

- **Chromium**: Use the system Chromium at the Nix store path. Run `find /nix/store -name chromium -type f 2>/dev/null | head -5` to locate the exact path. Cache it for all subsequent scripts.
- **Playwright**: Install via `pip install playwright` (no need for `playwright install` — use the system Chromium directly via `executable_path`).
- **Artifact**: Use the `create-artifact` skill to scaffold a React + Vite web app artifact before building components.

## Phase 1: Visual Reconnaissance

Capture a full-page screenshot and extract design tokens before scraping content.

```python
from playwright.sync_api import sync_playwright

def recon(url, chromium_path, out_dir="clone"):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=chromium_path)
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        page.goto(url, wait_until="networkidle", timeout=60000)

        # Force lazy content to load by scrolling the full page
        page.evaluate("""
          async () => {
            await new Promise(r => {
              let y = 0;
              const t = setInterval(() => {
                window.scrollBy(0, 300);
                y += 300;
                if (y >= document.body.scrollHeight) { clearInterval(t); r(); }
              }, 100);
            });
          }
        """)
        page.wait_for_timeout(3000)
        page.evaluate("window.scrollTo(0, 0)")
        page.wait_for_timeout(1000)

        # Full-page screenshot for visual reference
        page.screenshot(path=f"{out_dir}/full_page.png", full_page=True)

        # Extract design tokens
        tokens = page.evaluate("""
          () => {
            const body = document.body;
            const cs = getComputedStyle(body);
            return {
              bgColor: cs.backgroundColor,
              textColor: cs.color,
              fontFamily: cs.fontFamily,
              fontSize: cs.fontSize,
              // Extract CSS custom properties from :root
              cssVars: [...document.styleSheets].flatMap(sheet => {
                try {
                  return [...sheet.cssRules].filter(r => r.selectorText === ':root')
                    .flatMap(r => [...r.style].map(prop => [prop, r.style.getPropertyValue(prop)]));
                } catch { return []; }
              })
            };
          }
        """)
        # Save tokens as JSON
        import json
        open(f"{out_dir}/tokens.json", "w").write(json.dumps(tokens, indent=2))
        browser.close()
```

Key extractions:

- Background color, text color, font families
- CSS custom properties / design tokens
- Full-page screenshot as visual reference

## Phase 2: Deep Content Scrape

Extract all content from the rendered page. Critical: modern sites are SPAs — the raw HTML is often empty. You MUST use Playwright's `page.evaluate()` to extract content from the rendered DOM.

```python
def scrape_content(url, chromium_path, out_dir="clone"):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=chromium_path)
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        page.goto(url, wait_until="networkidle", timeout=60000)

        # IMPORTANT: Scroll the full page to trigger lazy loading
        for _ in range(8):
            page.evaluate("window.scrollBy(0, 1500)")
            page.wait_for_timeout(1500)
        page.evaluate("window.scrollTo(0, 0)")
        page.wait_for_timeout(1000)

        data = page.evaluate("""
          () => {
            const result = {};

            // 1. Top banner / announcement bar
            // Look for common patterns: fixed top bars, rotating promos
            const banner = document.querySelector(
              '[class*="banner"], [class*="announcement"], [class*="promo-bar"], [class*="top-bar"]'
            );
            if (banner) {
              result.banner = {
                bgColor: getComputedStyle(banner).backgroundColor,
                text: banner.innerText.trim(),
                slides: [...banner.querySelectorAll('[class*="slide"], [class*="message"]')].map(s => ({
                  text: s.innerText.trim(),
                  link: s.querySelector('a')?.href || ''
                }))
              };
            }

            // 2. Header / Navigation
            const header = document.querySelector('header') || document.querySelector('[class*="header"], nav');
            if (header) {
              result.header = {
                height: header.offsetHeight,
                bgColor: getComputedStyle(header).backgroundColor,
                navLinks: [...header.querySelectorAll('a')].map(a => ({
                  text: a.innerText.trim(),
                  href: a.getAttribute('href') || ''
                })).filter(l => l.text && l.text.length < 50)
              };
            }

            // 3. All visible sections in DOM order
            const main = document.querySelector('main') || document.body;
            result.sections = [...main.children].map(child => {
              const rect = child.getBoundingClientRect();
              if (rect.height < 20) return null;
              const cs = getComputedStyle(child);
              if (cs.display === 'none' || cs.visibility === 'hidden') return null;
              return {
                tag: child.tagName.toLowerCase(),
                classes: child.className.toString().slice(0, 200),
                top: Math.round(rect.top + window.scrollY),
                height: Math.round(rect.height),
                bg: cs.backgroundColor,
                bgImage: cs.backgroundImage !== 'none' ? cs.backgroundImage : null,
                text: child.innerText.slice(0, 1500),
                images: [...child.querySelectorAll('img')].slice(0, 30).map(img => ({
                  src: img.src,
                  alt: img.alt,
                  w: img.offsetWidth,
                  h: img.offsetHeight
                })).filter(i => i.src && i.w > 30),
                links: [...child.querySelectorAll('a')].slice(0, 30).map(a => ({
                  text: a.innerText.trim(),
                  href: a.getAttribute('href') || ''
                })).filter(l => l.text)
              };
            }).filter(Boolean);

            // 4. Product/card data (e-commerce sites)
            const productLinks = document.querySelectorAll('a[href*="/product"], a[href*="/shop"], a[href*="/item"]');
            const seen = new Set();
            result.products = [...productLinks].map(link => {
              const href = (link.getAttribute('href') || '').split('?')[0];
              if (seen.has(href) || !href) return null;
              seen.add(href);
              const img = link.querySelector('img');
              const heading = link.querySelector('h2, h3, h4');
              const spans = link.querySelectorAll('span, div');
              let price = '';
              for (const s of spans) {
                if (s.innerText.match(/^\\$\\d/)) price = s.innerText.trim();
              }
              return {
                href,
                image: img?.src || '',
                imageAlt: img?.alt || '',
                title: heading?.innerText?.trim() || '',
                price,
                fullText: link.innerText.trim().slice(0, 300)
              };
            }).filter(Boolean);

            // 5. Footer
            const footer = document.querySelector('footer');
            if (footer) {
              result.footer = {
                text: footer.innerText.trim(),
                links: [...footer.querySelectorAll('a')].map(a => ({
                  text: a.innerText.trim(),
                  href: a.href
                })).filter(l => l.text),
                socialLinks: [...footer.querySelectorAll(
                  'a[href*="instagram"], a[href*="tiktok"], a[href*="pinterest"], a[href*="facebook"], a[href*="twitter"], a[href*="youtube"], a[href*="linkedin"]'
                )].map(a => ({ href: a.href }))
              };
            }

            // 6. Fonts (from Google Fonts links or @font-face)
            const fontLinks = [...document.querySelectorAll('link[href*="fonts.googleapis"], link[href*="fonts.gstatic"]')]
              .map(l => l.href);
            result.fonts = fontLinks;

            return result;
          }
        """)

        import json
        open(f"{out_dir}/content.json", "w").write(json.dumps(data, indent=2))
        browser.close()
```

## Phase 3: Image URL Verification

**Critical step.** Scraped image URLs are frequently truncated, expired, or incorrect. Always verify every image URL before using it.

```python
import subprocess

def verify_images(urls):
    """Returns dict of url -> status_code. Fix any non-200."""
    results = {}
    for url in urls:
        try:
            r = subprocess.run(
                ['curl', '-s', '-o', '/dev/null', '-w', '%{http_code}', '-L', url],
                capture_output=True, text=True, timeout=10
            )
            results[url] = r.stdout.strip()
        except:
            results[url] = 'TIMEOUT'
    return results
```

### Common Image URL Problems & Fixes

| Problem | Cause | Fix |
|---------|-------|-----|
| Truncated filename | Playwright serialized long filename | Re-scrape with full `img.src` extraction |
| Wrong extension | Site serves `.png` but URL says `.jpg` | Check actual Content-Type header |
| Missing query params | Shopify/CDN URLs need `&width=` / `&crop=` | Add sizing params back |
| 403 Forbidden | Hotlink protection | Download image locally to `public/images/` |
| Expired signed URL | Temporary CDN token | Download and serve locally |

### Re-scrape strategy for broken images

If URLs are truncated, run a targeted re-scrape that extracts the full `img.src` property:

```python
# Target specific broken images by their alt text or position
data = page.evaluate("""
  () => [...document.querySelectorAll('img')]
    .filter(img => img.offsetWidth > 30)
    .map(img => ({
      src: img.src,              // Full URL from DOM
      srcset: img.srcset || '',  // May have higher-res versions
      alt: img.alt,
      width: img.offsetWidth,
      top: Math.round(img.getBoundingClientRect().top + window.scrollY)
    }))
""")
```

### Upgrading image resolution

CDN images often have size params you can modify:

```python
# Shopify CDN
url = url.replace("width=100", "width=800").replace("height=100", "height=800")

# Sanity CDN
url = url.replace("w=100", "w=1200").replace("h=100", "h=1200")

# General pattern: find width/height params and increase them
import re
url = re.sub(r'width=\d+', 'width=800', url)
url = re.sub(r'height=\d+', 'height=800', url)
```

## Phase 4: Build the Clone

### Project structure

```text
artifacts/{clone-name}/
  client/src/
    components/
      TopBanner.tsx       # Announcement/promo bar
      Header.tsx          # Logo + nav + icons
      HeroSection.tsx     # Hero images/video
      ProductCarousel.tsx # Scrollable product cards
      EditorialSections.tsx # Full-width editorial imagery
      Footer.tsx          # Footer columns + newsletter + social
    pages/
      home.tsx            # Assembles all components with real data
    index.css             # Design tokens, fonts, utilities
```

### Design token mapping

Extract these from the scrape and set as CSS custom properties:

```css
:root {
  --background: /* body background-color from scrape */;
  --foreground: /* body color from scrape */;
  --border: /* border color observed */;
  --top-banner: /* banner background-color */;
  --font-sans: /* primary font family */;
  --font-serif: /* heading/display font */;
}
```

### Data architecture

Keep scraped product/content data in the page file (e.g., `home.tsx`) as typed arrays, not in separate JSON files. This keeps the clone self-contained and avoids fetch complexity:

```tsx
const products = [
  {
    image: "https://cdn.shopify.com/...",  // Verified CDN URL
    name: "PRODUCT NAME",
    badge: "BEST-SELLER",
    retailPrice: "$192",
    salePrice: "$144",
    href: "/products/slug"
  },
  // ...
];
```

### Font loading

Add Google Fonts in `index.html`:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500&family=EB+Garamond:ital@0;1&display=swap" rel="stylesheet">
```

## Phase 5: Validation Checklist

After building, verify:

1. **All images load** — Run the URL verification script against every image URL in your components
2. **No console errors** — Check browser console logs via the refresh logs tool
3. **Responsive layout** — Test at 1440px (desktop), 768px (tablet), 375px (mobile)
4. **Visual fidelity** — Compare your clone's screenshot against the scraped `full_page.png`
5. **Real content** — No placeholder text ("Lorem ipsum"), no stock photos (Unsplash), no made-up prices
6. **Scroll behavior** — Sticky header, smooth scroll, proper z-indexing
7. **Hover states** — Image zoom, link opacity changes, button transitions

## Gotchas & Lessons Learned

### SPA sites (React, Next.js, Shopify)

- The raw HTML (`curl` output) is typically empty — just a `<div id="root">` or `<div id="app">`
- You MUST use Playwright with `wait_until="networkidle"` and scroll the page before extracting
- Content is rendered client-side — only `page.evaluate()` can access it

### Lazy-loaded content

- Scroll the ENTIRE page before extracting. Use multiple scroll passes with delays (see example below)
- Some content loads only when scrolled into viewport — a single `scrollTo(bottom)` may not trigger it

```python
for _ in range(8):
    page.evaluate("window.scrollBy(0, 1500)")
    page.wait_for_timeout(1500)
```

### Image URL truncation

- The most common scrape failure. Playwright's DOM serialization and JSON output can silently truncate very long URLs
- Always verify with `curl -s -o /dev/null -w '%{http_code}'` before using any URL
- When truncated: re-scrape specifically targeting that image's `img.src` property

### Hotlink protection

- Some sites block external embedding of their images (403 responses)
- Solution: Download images to `public/images/` and serve them locally
- This is also a good fallback for any URL that might expire

### Dynamic pricing / variant data

- Product prices and variant names often render via JavaScript after the card enters viewport
- Extract `innerText` from the product link container — prices are usually in nested spans
- Check for `text-decoration: line-through` to identify retail vs sale prices

### CDN URL patterns

- **Shopify**: `cdn.shopify.com/s/files/...?width=X&height=Y&crop=center`
- **Sanity**: `cdn.sanity.io/images/{project}/{dataset}/{hash}.{ext}?w=X&h=Y&q=80&auto=format`
- **Contentful**: `images.ctfassets.net/{space}/{id}/{name}?w=X&h=X`
- **Cloudinary**: `res.cloudinary.com/{cloud}/image/upload/w_X,h_Y/{path}`

## Quick Start

```bash
# 1. Find Chromium
find /nix/store -name chromium -type f 2>/dev/null | head -5

# 2. Install Playwright
pip install playwright

# 3. Run recon
python3 scripts/recon.py https://target-site.com

# 4. Run deep scrape
python3 scripts/scrape_content.py https://target-site.com

# 5. Verify images
python3 scripts/verify_images.py

# 6. Create artifact and build components
# Use create-artifact skill, then build components from scraped data

# 7. Final verification
# Check all images load, no console errors, responsive layout
```
