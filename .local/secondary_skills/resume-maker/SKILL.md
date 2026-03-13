---
name: resume-maker
description: Build a professional resume web app with viewable HTML page and downloadable PDF/DOCX files. Use when the user asks to create, generate, or build a resume, CV, or curriculum vitae with web preview and file exports.
---

# Resume Maker

Build a resume as a React web page backed by a generation script that produces pixel-perfect PDF and DOCX files from a single data source. The web page reads spacing values from the same JSON so the HTML preview closely matches the generated documents.

## Before You Start Building — Gather Information First

**Do NOT start building the resume until you have enough information to write meaningful, specific content.** A resume with placeholder text or vague bullets is useless. You need real details to create something valuable.

### If the user provides a complete resume or detailed background

Go ahead and start building immediately. You have what you need.

### If the user asks to "make me a resume" without providing details

You MUST ask clarifying questions before writing any code. Ask about:

1. **Target role** — What job or type of role are they applying for? (This shapes the entire resume's framing)
2. **Work experience** — For each role: job title, company name, dates, location, and what they accomplished (not just responsibilities — ask for specific achievements, numbers, outcomes)
3. **Education** — Degree(s), school(s), graduation year(s), honors/GPA if notable
4. **Skills** — Technical skills, tools, languages, frameworks, certifications
5. **Contact info** — Full name, email, phone, LinkedIn, location, portfolio/website
6. **Optional extras** — Publications, talks, awards, volunteer work, projects

### How to ask

Don't dump all questions at once. Start with the most critical:

> "To build you a great resume, I need some info. Let's start with the basics:
>
> 1. What's your full name and contact info (email, phone, LinkedIn, location)?
> 2. What type of role are you targeting?
> 3. Walk me through your work history — for each job, give me the title, company, dates, and your biggest accomplishments with specific numbers if possible."

Then follow up for education, skills, and anything else based on what they share. If they give vague bullets like "managed a team," push back and ask for specifics: *"How large was the team? What did you deliver? Any measurable outcomes?"*

### If the resume feels thin

If the user doesn't have enough experience to fill a page, ask about additional material: volunteer work, side projects, open-source contributions, certifications, coursework, publications, talks, or awards. Proactively suggest these categories — don't just leave the page half-empty.

### If the user provides a job posting

Ask them for their background details, then tailor the resume to match the posting's keywords and requirements. Mirror the posting's language.

## Build Order — Files First, Website Last

Follow this exact order so the user gets fast, tangible results:

1. **Write the generation script** (`generate-resume-files.ts`) with all resume content and run it to produce the PDF, DOCX, and `resume-data.json`
2. **Present the PDF to the user in chat** — show the generated PDF file immediately so they can see their resume right away
3. **Present the DOCX file to the user in chat** — make the DOCX available for download so they can grab it
4. **Build the web app last** — set up the React artifact and API routes that serve the resume page and download endpoints

The user cares most about seeing their resume quickly. The PDF and DOCX are the primary deliverables — get those into the user's hands first. The web app is a nice-to-have that comes after. Do NOT spend time building the web app before the user has seen their PDF and downloaded their DOCX.

## Architecture

```text
scripts/src/generate-resume-files.ts   ← single source of truth for content + layout
  └─ output/
       ├─ resume-data.json             ← consumed by the web frontend
       ├─ <name>-resume.pdf            ← downloadable PDF
       └─ <name>-resume.docx           ← downloadable DOCX

artifacts/<name>/src/pages/resume.tsx   ← React page, reads resume-data.json via API
artifacts/api-server/src/routes/download.ts ← serves PDF, DOCX, and JSON
```

### Data flow

1. `generate-resume-files.ts` holds all resume content in a `getResumeData()` function.
2. The script renders the PDF with jsPDF, measures content height, and auto-adjusts spacing to fill exactly one US Letter page (612×792pt, 36pt margins).
3. It writes the computed spacing values into `resume-data.json` alongside the content.
4. The React page fetches `resume-data.json` at runtime and uses the spacing values (converted pt→px via `96/72`) to render a matching HTML preview.

## Content Rules

- **Summary/blurb**: If you include a personal summary at the top, keep it to 2 sentences max. Long plaintext paragraphs waste prime real estate and recruiters skip them.
- **One page hard limit**: The resume MUST fit on exactly one US Letter page (612×792pt). The auto-fit algorithm handles this, but be mindful when writing content — more bullets doesn't mean better.

## Flag Guesses and Inferred Details

If you had to guess or infer any details — dates, job titles, specific contributions, metrics, technologies — you MUST tell the user what you made up. After presenting the first draft, explicitly list anything you weren't sure about and ask if they want to correct it. For example:

> "I made a few assumptions I want to flag:
>
> - I estimated your dates at Company X as 2021–2023 — are those right?
> - I wrote that you 'reduced API latency by 40%' based on your mention of performance work — is that accurate, or should I adjust the number/framing?
> - I guessed TypeScript and Python for your skills — anything to add or remove?"

Do NOT silently present fabricated details as fact. The user trusts you to be honest about what you know vs. what you inferred.

## Iteration and Changes

When the user requests changes (rewording bullets, adding/removing sections, reordering, etc.):

1. Make the requested changes in `generate-resume-files.ts`
2. **Re-run the generation script** to produce updated PDF, DOCX, and JSON
3. **Verify the output still fits on one page** — if the changes pushed content past the page boundary, the auto-fit algorithm should handle it, but visually confirm. If content is getting clipped or the scale factor is too aggressive (text becoming unreadably small), trim lower-priority bullets or reduce spacing before delivering.
4. **Re-present the updated PDF and DOCX** to the user in chat

Never deliver an updated resume without re-running the generation script. The PDF/DOCX must always reflect the latest content. Every iteration cycle ends with the user seeing a fresh PDF and having a fresh DOCX to download.

## Critical Unit Rules

### DOCX (`docx` npm package)

The `docx` package uses two different unit systems — mixing them up causes 10× sizing bugs:

- **`TextRun.size`** = **half-points**: `ptToHalfPt = pt * 2` (e.g., 11pt → 22)
- **Spacing, margins, page dimensions** = **twips**: `ptToTwip = pt * 20` (e.g., 36pt → 720)

Always define separate converter functions:

```typescript
const ptToHalfPt = (pt: number) => Math.round(pt * 2);
const ptToTwip = (pt: number) => Math.round(pt * 20);
```

### jsPDF baseline rule

`doc.text(text, x, y)` draws text with `y` as the **baseline** — the text body extends upward from y. When placing a horizontal rule after text:

- **Wrong**: `y += 2` after drawing a rule — text overlaps the line
- **Right**: `y += lineHeight` after drawing a rule — full clearance before next text

```typescript
doc.line(MARGIN, y, PAGE_W - MARGIN, y);
y += lineHeight;  // NOT y += 4
```

### Web CSS alignment

When using `borderBottom` as a section divider:

- Put the border directly on the element (e.g., `h2`) rather than a separate `<div>`
- Use `paddingBottom` to separate text from the line
- Use `marginBottom` with at least `lineHeight` to separate the line from content below

## One-Page Auto-Fit Algorithm

The generation script measures content height, then adjusts spacing to fill the target:

```text
1. Render with BASE_SPACING → measure finalY
2. If finalY > TARGET_Y: shorten bullets until it fits
3. If finalY < TARGET_Y: distribute slack across sectionGap, roleGap, bulletGap, lineHeight
4. Re-render with adjusted spacing → write final files
```

Slack distribution weights: `sectionGap: 3, roleGap: 2, bulletGap: 1, lineHeight: 0.5`

## Skeleton: Generation Script

```typescript
import { jsPDF } from "jspdf";
import {
  Document, Packer, Paragraph, TextRun, BorderStyle,
  TabStopPosition, TabStopType, AlignmentType,
} from "docx";
import fs from "fs";
import path from "path";

// --- Interfaces ---
interface ResumeRole {
  title: string;
  company: string;
  location: string;
  startDate: string;
  endDate: string;
  bullets: string[];
}

interface Spacing {
  sectionGap: number;
  roleGap: number;
  bulletGap: number;
  lineHeight: number;
  bodyFontSize: number;
  headlineFontSize: number;
}

interface ResumeData {
  name: string;
  headline: string;
  contact: { email: string; phone: string; location: string; linkedin: string; website: string };
  summary: string;
  roles: ResumeRole[];
  skills: { category: string; items: string }[];
  education: { degree: string; school: string; location: string; dates: string }[];
  spacing: Spacing;
}

// --- Constants ---
const PAGE_W = 612;   // US Letter width in points
const PAGE_H = 792;   // US Letter height in points
const MARGIN = 36;     // 0.5 inch margins
const CONTENT_W = PAGE_W - 2 * MARGIN;
const TARGET_Y = PAGE_H - MARGIN;

const BASE_SPACING: Spacing = {
  sectionGap: 8,
  roleGap: 4,
  bulletGap: 1,
  lineHeight: 14,
  bodyFontSize: 11,
  headlineFontSize: 11,
};

// --- Unit converters (DOCX) ---
const ptToHalfPt = (pt: number) => Math.round(pt * 2);   // TextRun.size
const ptToTwip = (pt: number) => Math.round(pt * 20);     // spacing/margins

// --- Resume data ---
function getResumeData(): Omit<ResumeData, "spacing"> {
  return {
    name: "FULL NAME",
    headline: "Title | Specialty",
    contact: {
      email: "email@example.com",
      phone: "",
      location: "City, State",
      linkedin: "linkedin.com/in/handle",
      website: "example.com",
    },
    summary: "Professional summary paragraph.",
    roles: [
      {
        title: "Job Title",
        company: "Company",
        location: "City, State",
        startDate: "MM/YYYY",
        endDate: "Present",
        bullets: ["Achievement or responsibility"],
      },
    ],
    skills: [
      { category: "Category", items: "Skill1, Skill2, Skill3" },
    ],
    education: [
      {
        degree: "Degree",
        school: "University",
        location: "City, Country",
        dates: "YYYY - YYYY",
      },
    ],
  };
}

// --- PDF rendering ---
function renderPDF(doc: jsPDF, data: Omit<ResumeData, "spacing">, spacing: Spacing): number {
  let y = MARGIN;
  const { bodyFontSize, headlineFontSize, lineHeight, sectionGap, roleGap, bulletGap } = spacing;

  // Header
  doc.setFont("helvetica", "bold");
  doc.setFontSize(20);
  doc.text(data.name, PAGE_W / 2, y, { align: "center" });
  y += 16;

  doc.setFont("helvetica", "normal");
  doc.setFontSize(headlineFontSize);
  doc.text(data.headline, PAGE_W / 2, y, { align: "center" });
  y += lineHeight;

  // Contact line
  const contactParts = [data.contact.location, data.contact.email, data.contact.linkedin, data.contact.website].filter(Boolean);
  doc.setFontSize(9);
  doc.text(contactParts.join("  |  "), PAGE_W / 2, y, { align: "center" });
  y += lineHeight + 2;

  // Divider — use lineHeight gap after rule, NOT a small constant
  doc.setDrawColor(0);
  doc.setLineWidth(0.5);
  doc.line(MARGIN, y, PAGE_W - MARGIN, y);
  y += lineHeight;

  // Summary
  doc.setFont("helvetica", "normal");
  doc.setFontSize(bodyFontSize);
  const summaryLines = doc.splitTextToSize(data.summary, CONTENT_W) as string[];
  for (const line of summaryLines) {
    doc.text(line, MARGIN, y);
    y += lineHeight;
  }

  // Section header helper
  function renderSectionHeader(title: string) {
    y += sectionGap;
    doc.setFont("helvetica", "bold");
    doc.setFontSize(11);
    doc.text(title.toUpperCase(), MARGIN, y);
    y += 3;
    doc.setLineWidth(0.3);
    doc.line(MARGIN, y, PAGE_W - MARGIN, y);
    y += lineHeight;  // full lineHeight after rule
  }

  // Experience, Skills, Education sections...
  // (render each section using the spacing values)

  return y;
}

// --- DOCX rendering ---
function buildDocx(data: Omit<ResumeData, "spacing">, spacing: Spacing): Document {
  const { bodyFontSize, headlineFontSize, lineHeight, sectionGap, roleGap, bulletGap } = spacing;

  // Use ptToHalfPt() for ALL TextRun.size values
  // Use ptToTwip() for ALL spacing.before, spacing.after, margins, page dimensions

  const doc = new Document({
    sections: [{
      properties: {
        page: {
          size: { width: ptToTwip(PAGE_W), height: ptToTwip(PAGE_H) },
          margin: {
            top: ptToTwip(MARGIN),
            bottom: ptToTwip(MARGIN),
            left: ptToTwip(MARGIN),
            right: ptToTwip(MARGIN),
          },
        },
      },
      children: [
        // Build paragraphs here using the data
      ],
    }],
  });

  return doc;
}

// --- Auto-fit and generate ---
async function main() {
  const data = getResumeData();
  const outputDir = path.resolve(import.meta.dirname, "..", "output");
  if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });

  // Measure with base spacing
  let spacing = { ...BASE_SPACING };
  let doc = new jsPDF({ unit: "pt", format: "letter" });
  let finalY = renderPDF(doc, data, spacing);

  // Auto-fit: distribute slack
  const slack = TARGET_Y - finalY;
  if (slack > 0) {
    const weights = { sectionGap: 3, roleGap: 2, bulletGap: 1, lineHeight: 0.5 };
    const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
    // Count occurrences of each spacing type to distribute evenly
    // Adjust spacing values proportionally
  }

  // Final render
  doc = new jsPDF({ unit: "pt", format: "letter" });
  finalY = renderPDF(doc, data, spacing);

  // Write files
  const pdfBuffer = Buffer.from(doc.output("arraybuffer"));
  fs.writeFileSync(path.join(outputDir, "person-name-resume.pdf"), pdfBuffer);

  const docxDoc = buildDocx(data, spacing);
  const docxBuffer = await Packer.toBuffer(docxDoc);
  fs.writeFileSync(path.join(outputDir, "person-name-resume.docx"), docxBuffer);

  const jsonData: ResumeData = { ...data, publications: [], spacing };
  fs.writeFileSync(path.join(outputDir, "resume-data.json"), JSON.stringify(jsonData, null, 2));

  console.log("All files generated successfully!");
}

main().catch(console.error);
```

## Skeleton: React Resume Page

```tsx
import { useState, useEffect } from "react";

const PT_TO_PX = 96 / 72;

interface ResumeData { /* same interfaces as generation script */ }

function SectionHeader({ title, lineHeightPx }: { title: string; lineHeightPx: number }) {
  return (
    <h2 style={{
      fontSize: "11pt",
      fontWeight: 700,
      textTransform: "uppercase",
      letterSpacing: "0.5px",
      margin: 0,
      padding: 0,
      paddingBottom: "4px",
      marginBottom: `${lineHeightPx}px`,
      borderBottom: "1px solid #1a1a2e",
      color: "#1a1a2e",
    }}>
      {title}
    </h2>
  );
}

export default function ResumePage() {
  const [data, setData] = useState<ResumeData | null>(null);

  useEffect(() => {
    const basePath = import.meta.env.BASE_URL;
    fetch(`${basePath}api/resume-data`)
      .then((r) => r.json())
      .then(setData);
  }, []);

  if (!data) return <p>Loading...</p>;

  const sp = data.spacing;
  const sectionGapPx = sp.sectionGap * PT_TO_PX;
  const roleGapPx = sp.roleGap * PT_TO_PX;
  const bulletGapPx = sp.bulletGap * PT_TO_PX;
  const lineHeightPx = sp.lineHeight * PT_TO_PX;
  const bodyFontPx = sp.bodyFontSize * PT_TO_PX;

  return (
    <div style={{ background: "#f0f0f0", minHeight: "100vh" }}>
      <div style={{
        width: "816px",       // 8.5in at 96dpi
        minHeight: "1056px",  // 11in at 96dpi
        margin: "0 auto",
        background: "#fff",
        padding: "48px",      // 0.5in margins at 96dpi
        fontFamily: "'Calibri', 'Arial', sans-serif",
        fontSize: `${bodyFontPx}px`,
        lineHeight: `${lineHeightPx}px`,
      }}>
        {/* Header, summary, experience, skills, education */}
        {/* Use SectionHeader with lineHeightPx for each section */}
        {/* Use spacing values from sp for all gaps */}
      </div>
    </div>
  );
}
```

## Skeleton: API Routes

```typescript
import { Router } from "express";
import path from "path";
import fs from "fs";

const router = Router();
const outputDir = path.resolve(import.meta.dirname, "..", "..", "..", "..", "output");

router.get("/download/pdf", (_req, res) => {
  const filePath = path.join(outputDir, "person-name-resume.pdf");
  if (!fs.existsSync(filePath)) {
    res.status(404).json({ error: "PDF not found. Run the generation script first." });
    return;
  }
  res.setHeader("Content-Disposition", "attachment; filename=person-name-resume.pdf");
  res.setHeader("Content-Type", "application/pdf");
  res.sendFile(filePath);
});

router.get("/download/docx", (_req, res) => {
  const filePath = path.join(outputDir, "person-name-resume.docx");
  if (!fs.existsSync(filePath)) {
    res.status(404).json({ error: "DOCX not found. Run the generation script first." });
    return;
  }
  res.setHeader("Content-Disposition", "attachment; filename=person-name-resume.docx");
  res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  res.sendFile(filePath);
});

router.get("/resume-data", (_req, res) => {
  const filePath = path.join(outputDir, "resume-data.json");
  if (!fs.existsSync(filePath)) {
    res.status(404).json({ error: "Resume data not found. Run the generation script first." });
    return;
  }
  res.json(JSON.parse(fs.readFileSync(filePath, "utf-8")));
});

export default router;
```

## Common Pitfalls

| Problem | Cause | Fix |
|---------|-------|-----|
| DOCX text is 10× too large | Used `ptToTwip` (×20) for `TextRun.size` | Use `ptToHalfPt` (×2) for font sizes |
| PDF text overlaps horizontal rule | Used `y += 4` after `doc.line()` | Use `y += lineHeight` after any rule |
| Web section header line cuts through text | Border on a sibling `<div>` instead of the `<h2>` | Put `borderBottom` on the `<h2>` with `paddingBottom` |
| Downloads fail in Replit preview iframe | Proxy blocks file downloads in iframe | Use `target="_blank"` on download links, or present files directly |
| Web spacing doesn't match PDF | Hardcoded px values in CSS | Convert all pt values via `PT_TO_PX = 96/72` |

## Dependencies

- `jspdf` — PDF generation
- `docx` — DOCX generation
- `tsx` — TypeScript script runner (dev dependency)
