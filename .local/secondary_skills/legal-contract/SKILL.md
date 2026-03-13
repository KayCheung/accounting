---
name: legal-contract
description: Draft and review legal documents like NDAs, contracts, and lease agreements with plain-language explanations.
---

# Legal Contract Assistant

Draft and review common legal documents including NDAs, service agreements, freelancer contracts, and lease reviews. Provide plain-language explanations and flag potential issues.

**IMPORTANT DISCLAIMER: This provides general information and templates only. It does NOT constitute legal advice. Always consult a qualified attorney for legal matters.**

## When to Use

- User needs a basic NDA, service agreement, or freelancer contract
- User wants a plain-language review of a contract they received
- User needs to understand specific legal terms or clauses
- User wants a lease or rental agreement reviewed for red flags

## When NOT to Use

- Complex litigation or regulatory compliance
- Employment law disputes
- International trade agreements
- Anything involving criminal law
- Situations requiring jurisdiction-specific legal analysis

## Open-Source Template Libraries (Use These First)

**Never draft from scratch.** Start from committee-vetted open-source agreements released under CC BY 4.0:

| Source | Documents | Style | Get it |
|--------|-----------|-------|--------|
| **Bonterms** | Mutual NDA, Cloud Terms (SaaS), SLA, DPA, PSA, AI Standard Clauses | US; "cover page + standard terms" | `github.com/Bonterms` |
| **Common Paper** | Mutual NDA, Cloud Service Agreement, DPA, Design Partner Agreement | US; standards committee of 40+ attorneys | `commonpaper.com/standards` |
| **oneNDA** | NDA (777 words), oneDPA | UK/EU; strict variable-only edits | `onenda.org` |

**Workflow:** `webFetch("https://bonterms.com/forms/mutual-nda/")` → extract the standard terms → build a cover page with the user's deal-specific variables (parties, effective date, term, governing law, jurisdiction). Don't modify the body; that's the whole point of standards.

## Red-Flag Language to Grep For

When reviewing, search the document text for these exact phrases — each is a known risk pattern:

| Phrase | Why it's dangerous | Suggested fix |
|--------|-------------------|---------------|
| `"any and all claims"` | Unlimited indemnity scope | "claims arising directly from [Party]'s breach of Section X" |
| `"indemnify, defend, and hold harmless"` | "Hold harmless" blocks your counterclaims even if they caused the loss | Strike "and hold harmless"; keep "indemnify and defend" |
| `"sole discretion"` / `"absolute discretion"` | One party can act arbitrarily (block settlements, reject deliverables) | "consent not to be unreasonably withheld, conditioned, or delayed" |
| `"including but not limited to"` in IP assignment | Open-ended IP grab beyond deliverables | Enumerate specific deliverables; add "excluding pre-existing IP" |
| No liability cap stated | Courts default to **unlimited** liability | "Aggregate liability capped at fees paid in the 12 months preceding the claim" |
| `"time is of the essence"` | Any delay = material breach | Delete, or limit to payment obligations only |
| Indemnity **carved out** of liability cap | Your cap doesn't protect you where exposure is highest | "Indemnification obligations are subject to the cap in Section X" |
| Auto-renewal with <30-day opt-out window | Easy to miss; locked in another term | 60–90 day notice window; email notice permitted |
| `"perpetual"` + `"irrevocable"` license | Can never be revoked even after breach | Term-limited; terminable on material breach |

**Indemnity forms (escalating risk):** *Limited* = you cover only your own negligence. *Intermediate* = everything except their sole negligence. *Broad* = you cover losses **even when caused entirely by them**. Flag intermediate and broad as Critical.

## Playbook Checks (What Harvey/Spellbook Actually Run)

AI contract tools run a fixed checklist per document type. For a **Service Agreement** run these checks and grade each Pass/Flag/Missing:

1. Is there a liability cap? Is it mutual? Is it tied to fees paid (1x, 2x)?
2. Is indemnity mutual or one-way? Subject to the cap or carved out?
3. Does IP assignment exclude contractor's pre-existing tools/libraries?
4. Is there a cure period (typically 30 days) before termination for breach?
5. Are "consequential damages" (lost profits, lost data) excluded? Mutually?
6. Payment terms: Net 30 or better? Late fee specified?
7. Can the client terminate for convenience? If so, is there a kill fee?
8. Governing law + venue: neutral, or the other party's home court?

## Review Output Format

```text

# Contract Review: [Document Type]
**NOT LEGAL ADVICE — for informational purposes only. Consult an attorney before signing.**

## Summary
[2–3 sentences: what this is, who it favors, biggest concern]

## Critical — Do Not Sign Without Addressing

1. **[Clause §X.Y]**: [quote the exact language]
   - **Risk**: [plain English]
   - **Suggested redline**: "[replacement text]"

## Warnings — Negotiate If You Have Leverage

## Notes — Standard But Be Aware

## Missing Protections
[clauses that should be here but aren't — e.g., no liability cap, no cure period]

## Overall: [Fair / Favors Counterparty / Consult Attorney Before Signing]

```

## Output: Always Produce PDF & DOCX

**Every drafted contract MUST be delivered as both a PDF and a DOCX file.** Clients need PDF for signing and DOCX for redlining — always provide both.

### Architecture: React + Vite → Puppeteer PDF + python-docx DOCX

Build the contract as a React web artifact first (source of truth for layout), then export:

**PDF via Puppeteer:**

```typescript
// generate-contract.ts
import puppeteer from 'puppeteer-core';

// Find Chromium: ls /nix/store/*chromium*/bin/chromium
const CHROMIUM_PATH = "/nix/store/FIND_YOUR_PATH/bin/chromium";
// Use the artifact's actual URL, not localhost
const CONTRACT_URL = `https://${process.env.REPLIT_DEV_DOMAIN}/ARTIFACT-SLUG`;

const browser = await puppeteer.launch({
  executablePath: CHROMIUM_PATH,
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});
const page = await browser.newPage();
await page.goto(CONTRACT_URL, { waitUntil: 'networkidle0' });
await page.pdf({
  path: 'contract.pdf',
  format: 'Letter',
  printBackground: true,
  margin: { top: '1in', bottom: '1in', left: '1.25in', right: '1.25in' },
});
await browser.close();
```

**DOCX via python-docx:**

```python
from docx import Document
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH

doc = Document()

# Set default font
style = doc.styles['Normal']
font = style.font
font.name = 'Times New Roman'
font.size = Pt(12)

# Set margins
for section in doc.sections:
    section.top_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1.25)
    section.right_margin = Inches(1.25)

# Title
title = doc.add_heading('MUTUAL NON-DISCLOSURE AGREEMENT', level=0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

# Preamble
doc.add_paragraph(
    'This Mutual Non-Disclosure Agreement ("Agreement") is entered into '
    'as of [DATE] by and between:'
)

# Parties table
table = doc.add_table(rows=2, cols=2)
table.style = 'Table Grid'
# ... populate with party details

# Sections with numbered clauses
doc.add_heading('1. Definition of Confidential Information', level=1)
doc.add_paragraph('...')

# Signature block
doc.add_paragraph('\n\n')
sig_table = doc.add_table(rows=4, cols=2)
# ... signature lines with name, title, date

doc.save('contract.docx')
```

### Styling for Legal Documents

- **Font**: Times New Roman 12pt (standard for legal docs) or similar serif
- **Margins**: 1" top/bottom, 1.25" left/right
- **Line spacing**: 1.5 or double-spaced (jurisdiction dependent)
- **Section numbering**: Use hierarchical numbering (1, 1.1, 1.1.1)
- **Page numbers**: Bottom center, "Page X of Y"
- **Headers**: Document title and date on each page
- **Signature blocks**: Two-column layout with lines for signature, printed name, title, date

### Contract Review Output

For **reviews** (not drafting), output the review analysis directly as text using the Review Output Format above. Do not generate PDF/DOCX for reviews — the review is commentary, not a document.

## Drafting Rules

1. **Always include the disclaimer** at the top of every output — this is not legal advice
2. Start from Bonterms/Common Paper, don't invent clause language
3. Quote exact problem text with section numbers, then give replacement language
4. Flag jurisdiction dependencies — non-compete enforceability, anti-indemnity statutes, and consumer protection vary wildly by state/country
5. When stakes are high (>$50K, equity, exclusivity, personal guarantees) → recommend attorney review explicitly

## Limitations

- NOT a substitute for legal advice from a licensed attorney
- Cannot account for jurisdiction-specific laws
- Cannot verify legal enforceability of any clause
- Cannot handle litigation, regulatory filings, or court documents
- Templates are starting points, not final legal documents
