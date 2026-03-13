---
name: invoice-generator
description: Generate professional invoices as React web apps with auto-scaling layout and pixel-perfect PDF export via Puppeteer.
---

# Invoice Generator

Build invoices as React web artifacts that auto-scale to fit the page, then generate pixel-perfect PDFs via Puppeteer. The web page is the single source of truth — the PDF is a screenshot of it.

## Before You Start Building — Gather Information First

**Do NOT start building the invoice until you have enough information to populate real line items and details.** An invoice with placeholder data is useless.

### If the user provides complete invoice details

Go ahead and start building immediately. You have what you need.

### If the user asks to "make me an invoice" without providing details

You MUST ask clarifying questions before writing any code. Ask about:

1. **Seller info** — Business name, address, email, phone, logo (if any), VAT/tax ID (if applicable)
2. **Client info** — Client/company name, address, contact email, VAT number (if B2B in EU)
3. **Line items** — Description of each service/product, quantity, rate/price per unit
4. **Payment terms** — Net 30, due on receipt, etc. + preferred payment method (bank transfer, PayPal, Stripe, etc.)
5. **Invoice number** — Do they have an existing numbering scheme, or should you start one?
6. **Dates** — Invoice date, service/delivery date (if different), due date
7. **Tax** — What tax rate applies? (Sales tax, VAT, none?) This depends on jurisdiction.
8. **Where is the seller based?** — Determines required legal fields, page size (US = Letter, everyone else = A4), and tax handling

### How to ask

Start with the essentials:

> "To create your invoice, I need a few details:
>
> 1. Your business name and address (the seller)
> 2. Who you're billing — client name and address
> 3. What you're billing for — list each item/service with the quantity and price
> 4. Payment terms — when is it due, and how should they pay?"

Then follow up for tax details, numbering, branding, etc. based on what they share.

### If the invoice feels incomplete

If the user gives vague descriptions like "consulting work," push for specifics: *"Can you break that down into specific deliverables? e.g., 'Website redesign — 3 revision rounds' at $5,000. Specific line items look more professional and reduce client pushback."*

## Flag Guesses and Inferred Details

If you had to guess or infer any details — tax rates, payment terms, invoice numbers, dates — you MUST tell the user what you assumed. After presenting the first draft, explicitly list anything you weren't sure about. For example:

> "A few things I assumed — let me know if any need adjusting:
>
> - I used invoice number INV-2026-0001 — do you have an existing numbering scheme?
> - I set the tax rate to 0% since you didn't mention taxes — should I add sales tax or VAT?
> - I set payment terms to Net 30 with a due date of April 13 — is that right?"

Do NOT silently present fabricated details as fact. Getting invoice details wrong can cause real payment and legal issues.

## Build Order — PDF First, Website Last

Follow this exact order so the user gets fast results:

1. **Build the web artifact** with all invoice data and generate the PDF
2. **Present the PDF to the user in chat** — show the generated PDF immediately so they can see their invoice right away
3. **Finish the web app** — make sure the web preview looks correct and is browsable

The user cares most about seeing their invoice quickly. Get the PDF into their hands first. The web app is a bonus for previewing and iterating.

## Architecture

```text
artifacts/<client>-invoice/
  client/src/pages/Invoice.tsx   # Invoice data + component with auto-scale
  client/src/index.css           # Print-ready styles (A4 or Letter)
scripts/src/generate-invoice.ts  # Puppeteer PDF generator
output/                          # Generated PDF
```

### How it works

1. **Web page** renders the invoice at exactly 8.5in x 11in (Letter) or 210mm x 297mm (A4) with CSS
2. **Auto-scale hook** measures content height vs available height after fonts load; if content overflows, it applies `transform: scale()` to shrink everything to fit — content is never clipped
3. **PDF generation** uses Puppeteer to load the live web page, apply the same scale calculation, then `page.pdf()` with exact page dimensions
4. **Multi-page support** — unlike resumes, invoices can span multiple pages if the line items are long. For invoices with more than ~20 line items, don't aggressively shrink to one page — instead let the content flow naturally across pages with repeating table headers on each page

## Page Length Rules

- **Most invoices should fit on one page.** The auto-scale handles this for typical invoices.
- **It's OK to go multi-page** if the invoice has many line items. Don't shrink text to an unreadable size just to force everything onto one page.
- **If going multi-page**: repeat the table header (Description | Qty | Rate | Amount) on each page, and put the totals section on the final page.
- **Always put payment instructions and totals on the last page** so the client sees how much they owe and how to pay without hunting.

## Required Fields by Jurisdiction

**EU (VAT Directive 2006/112/EC, Article 226) — legally mandatory:**

- Sequential invoice number (gaps must be documented — auditors **will** assess VAT on missing numbers)
- Invoice date + date of supply (if different)
- Seller's full name, address, and **VAT number**
- Customer's name and address (and VAT number if B2B)
- Description of goods/services, quantity/extent
- Unit price excluding VAT, VAT rate per line, VAT amount **in the member state's currency** (even if invoice is in USD)
- **Reverse charge:** if selling B2B cross-border within EU, charge 0% VAT and add the notation `"Reverse charge — VAT to be accounted for by the recipient (Art. 196, Directive 2006/112/EC)"`. Include the customer's VAT number (validate via VIES).

**US — no federal invoice law.** Sequential numbering is best practice (IRS wants unique IDs for audit trail) but not legally required. Sales tax rules vary by state; many services are untaxed.

**Numbering scheme:** `{PREFIX}-{YYYY}-{SEQ:04d}` e.g. `INV-2026-0042`. Prefix can distinguish clients or entities. Never reuse or skip; if you void one, keep the voided record.

## Payment Terms Glossary

| Term | Meaning | Typical use |
|------|---------|-------------|
| Due on receipt | Pay immediately | Small amounts, new clients |
| Net 30 | Due 30 days from invoice date | Standard B2B |
| Net 60 / Net 90 | 60/90 days | Large enterprise (push back on this) |
| 2/10 Net 30 | 2% discount if paid in 10 days, else full in 30 | Incentivize fast payment |
| EOM | Due end of month | |
| 1.5% monthly late fee | Compounds on overdue balance | Check local usury caps — often ~18% APR max |

## Building the Invoice

### Invoice.tsx — React Component

The invoice component should include:

- `INVOICE_DATA` object at the top holding all content — line items, client info, seller info, tax rates, payment terms, dates
- `useEffect` with `scaleToFit()` that measures `scrollHeight` vs available height and applies `transform: scale()` if content overflows
- `document.fonts.ready.then(scaleToFit)` ensures scaling runs after web fonts load
- Multiple `setTimeout` calls as fallback for slow font/layout

**Layout structure:**

```tsx
const INVOICE_DATA = {
  invoiceNumber: "INV-2026-0042",
  issueDate: "2026-03-14",
  dueDate: "2026-04-13",
  seller: {
    name: "Your Company",
    address: "123 Main St, City, ST 12345",
    email: "billing@company.com",
    vatNumber: "", // EU only
  },
  client: {
    name: "Client Corp",
    address: "456 Oak Ave, City, ST 67890",
    vatNumber: "", // EU B2B only
  },
  items: [
    { description: "Homepage redesign — 3 rounds of revisions", qty: 1, rate: 5000 },
    { description: "Logo design and brand kit", qty: 1, rate: 2500 },
  ],
  taxRate: 0, // 0.20 for 20% VAT, etc.
  currency: "USD",
  paymentTerms: "Net 30",
  paymentInstructions: "Bank: ... | ACH Routing: ... | Account: ...",
  notes: "",
  lateFeePolicy: "1.5% monthly interest on overdue balances",
};
```

**Component structure:**

```text
<header>  Logo + "INVOICE" title + invoice number/dates  </header>
<section class="parties">  Two columns: From (seller) / Bill To (client)  </section>
<table>  Description | Qty | Rate | Amount  — with calculated line totals  </table>
<section class="totals">  Subtotal / Tax (X%) / Total Due — right-aligned, bold total  </section>
<footer>  Payment instructions · Bank/IBAN/SWIFT or payment link · Late fee policy  </footer>
```

The component should compute subtotal, tax, and total from the items array. Display the due date as an actual date ("Due: April 13, 2026") not just the payment term.

### index.css — Stylesheet

Key patterns:

- `@page { size: letter; margin: 0; }` for print (swap `letter` for `A4` for non-US)
- `.invoice-page` is exactly `8.5in x 11in` with `overflow: hidden`
- `@media print` hides Replit banners/iframes
- Clean sans-serif font (Inter, system fonts) — invoices should look professional but modern
- Table styling: light header row, subtle row borders, right-aligned numbers
- Totals section: right-aligned, bold total due with clear visual hierarchy
- Logo: `max-height: 48px` at top-left

### generate-invoice.ts — PDF Generator

```typescript
import puppeteer from "puppeteer-core";
import * as fs from "fs";
import * as path from "path";

const outDir = path.resolve(import.meta.dirname, "..", "..", "output");
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

// UPDATE THESE for each invoice:
// 1. Find Chromium path: ls /nix/store/*chromium*/bin/chromium
// 2. Set INVOICE_URL to the web artifact's URL
// 3. Set OUTPUT_NAME for the filename
const CHROMIUM_PATH = "/nix/store/FIND_YOUR_PATH/bin/chromium";
const INVOICE_URL = `https://${process.env.REPLIT_DEV_DOMAIN}/ARTIFACT-SLUG`;
const OUTPUT_NAME = "INV-2026-0042";

async function generatePDF() {
  const browser = await puppeteer.launch({
    executablePath: CHROMIUM_PATH,
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 816, height: 1056 });
  await page.goto(INVOICE_URL, { waitUntil: "networkidle0", timeout: 30000 });

  await page.waitForSelector(".invoice-page", { timeout: 10000 });
  await new Promise((r) => setTimeout(r, 2000));

  // Remove Replit dev UI and auto-scale content to fit one page
  await page.evaluate(() => {
    document.querySelectorAll('[class*="replit"], [class*="banner"], [id*="replit"], [id*="banner"], iframe').forEach(
      (el) => (el as HTMLElement).remove()
    );
    document.body.style.background = "#fff";
    document.body.style.margin = "0";
    document.body.style.padding = "0";

    const pg = document.querySelector(".invoice-page") as HTMLElement;
    if (!pg) return;
    pg.style.margin = "0";
    pg.style.boxShadow = "none";
    pg.style.width = "8.5in";
    pg.style.height = "11in";
    pg.style.overflow = "hidden";
    pg.style.position = "relative";

    const inner = pg.children[0] as HTMLElement;
    if (!inner) return;

    // Reset any existing transform to measure true height
    inner.style.transform = "none";
    inner.style.width = "100%";

    const padTop = parseFloat(getComputedStyle(pg).paddingTop);
    const padBot = parseFloat(getComputedStyle(pg).paddingBottom);
    const availH = pg.clientHeight - padTop - padBot;
    const contentH = inner.scrollHeight;

    // Scale down if content overflows available space
    if (contentH > availH) {
      const scale = availH / contentH;
      inner.style.transformOrigin = "top left";
      inner.style.transform = `scale(${scale})`;
      inner.style.width = `${100 / scale}%`;
    }
  });

  await new Promise((r) => setTimeout(r, 300));

  const pdfPath = path.join(outDir, `${OUTPUT_NAME}.pdf`);
  await page.pdf({
    path: pdfPath,
    width: "8.5in",
    height: "11in",
    printBackground: true,
    margin: { top: "0", bottom: "0", left: "0", right: "0" },
    preferCSSPageSize: false,
  });

  await browser.close();
  console.log("PDF saved:", pdfPath);
  return pdfPath;
}

await generatePDF();
console.log("Done!");
```

## Preventing Content Cutoff

The most common issue is content getting clipped at the bottom. Use a **two-layer defense**:

1. **React side** (for browser preview): The `scaleToFit` useEffect measures `scrollHeight` vs `clientHeight - padding` and applies CSS `transform: scale()`. This runs on mount, resize, font load, and via timeouts.

2. **Puppeteer side** (for PDF): The `page.evaluate()` block does the exact same measurement and scaling. This is necessary because Puppeteer's PDF renderer may not execute React effects reliably.

Both use the same formula:

```text
availH = container.clientHeight - paddingTop - paddingBottom
scale = availH / content.scrollHeight
content.style.transform = `scale(${scale})`
content.style.width = `${100 / scale}%`  // compensate for horizontal shrink
```

## Iteration and Changes

When the user requests changes (line items, amounts, branding, payment terms, etc.):

1. Make the requested changes in `Invoice.tsx`
2. **Re-run the PDF generation script** to produce an updated PDF
3. **Verify the output looks correct** — check that content isn't clipped, numbers are right-aligned, totals are correct
4. **Re-present the updated PDF** to the user in chat

Never deliver an updated invoice without re-generating the PDF. Every iteration cycle ends with the user seeing a fresh PDF.

## Best Practices

1. **Show the due date as an actual date** — "Due: April 13, 2026" not just "Net 30" (clients miscount)
2. **Specific line items** — "Homepage redesign — 3 rounds of revisions" not "Design services"
3. **Payment instructions on the invoice itself** — bank details, IBAN/SWIFT for international, payment link
4. **Ask the user's jurisdiction before building** — it changes required fields, page size, and tax display
5. **Right-align all numbers** — amounts, quantities, rates, totals
6. **Bold the total due** — it should be the most visually prominent number on the page

## Limitations

- Cannot send invoices, process payments, or track payment status
- Tax calculation is flat-rate per invoice — doesn't handle mixed VAT rates per line or US multi-state nexus
- Not a substitute for accounting software; no ledger integration
