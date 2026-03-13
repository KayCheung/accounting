---
name: translation
description: Translate text and documents between languages with cultural and contextual adaptation
---

# Translation

Translate text, documents, and content between languages with attention to context, tone, and cultural nuance.

## When to Use

- User wants text translated between languages
- User needs a document, email, or webpage localized
- User wants to understand foreign-language content
- User needs culturally adapted content (not just word-for-word translation)
- User wants multilingual versions of their content

## When NOT to Use

- Code localization / i18n implementation (use create-artifact skill)
- Writing original content in a specific language (use content-machine skill)

## Methodology

### Step 1: Understand the Context

Before translating, clarify:

- **Source and target language(s)**
- **Content type**: Casual text, business document, marketing copy, technical/legal, creative/literary
- **Audience**: Who will read the translation? (formal vs. informal register)
- **Purpose**: Information transfer, marketing impact, legal accuracy, or cultural adaptation?
- **Tone**: Should the translation match the original tone or adapt for the target culture?

### Step 2: Translation Approach

**Choose the right approach for the content type:**

| Content Type | Approach | Priority |
|-------------|----------|----------|
| **Technical/Legal** | Precise, terminology-consistent | Accuracy over fluency |
| **Business** | Professional, clear | Balance accuracy and fluency |
| **Marketing/Creative** | Transcreation — adapt the message | Impact over literal accuracy |
| **Casual/Chat** | Natural, colloquial | Fluency over formality |
| **UI/UX** | Concise, action-oriented | Brevity and clarity |

### Step 3: Translate

**Translation principles:**

- Translate meaning, not words — convey the intent, not a word-for-word mapping
- Preserve tone and register — formal stays formal, casual stays casual
- Handle idioms properly — find equivalent expressions in the target language, don't translate literally
- Maintain formatting — preserve bullet points, headers, bold/italic, links
- Keep proper nouns as-is unless there's a standard localized form
- Preserve technical terms — use established translations from the field, don't invent new ones

### Formality — The T-V Distinction

Many languages force you to pick a formality register for "you." Get this wrong and the whole translation reads off. **Ask the user or default to formal for business.**

| Language | Informal (T) | Formal (V) | Notes |
|----------|-------------|------------|-------|
| Spanish | tú / vos | usted | LatAm vs. Spain differ; Argentina uses *vos* |
| French | tu | vous | *vous* is also plural — context disambiguates |
| German | du | Sie | *Sie* is capitalized; verbs conjugate differently |
| Portuguese | tu / você | o senhor / a senhora | Brazil: *você* is default-neutral |
| Russian | ты | вы | |
| Japanese | — | — | No T-V; uses **keigo** — three honorific systems (*teineigo* polite, *sonkeigo* respectful, *kenjōgo* humble). In-group vs out-group matters more than hierarchy: you humble *your own boss* when talking to a client. |
| Korean | 반말 | 존댓말 | Six speech levels; verb endings change completely |

### Locale Data — Don't Guess, Use CLDR

Dates, numbers, and currencies follow **Unicode CLDR** (Common Locale Data Repository — what every OS and browser uses). Don't hardcode formats; look them up per locale:

| Locale | Date | Number | Notes |
|--------|------|--------|-------|
| en-US | 3/14/2026 | 1,234.56 | 12-hour clock default |
| en-GB | 14/03/2026 | 1,234.56 | 24-hour clock |
| de-DE | 14.03.2026 | 1.234,56 | Comma is decimal separator |
| fr-FR | 14/03/2026 | 1 234,56 | Thin-space thousands separator |
| ja-JP | 2026年3月14日 | 1,234.56 | Year-month-day, era names possible (令和8年) |
| ar-SA | ١٤/٠٣/٢٠٢٦ | ١٬٢٣٤٫٥٦ | RTL, Arabic-Indic digits, Hijri calendar option |

In Python: `babel.dates.format_date(d, locale='de_DE')` handles this correctly.

### Text Expansion — Plan for It

English → other languages changes length. Critical for UI strings, button labels, and anything with character limits:

| Target | Expansion vs. English | Watch out for |
|--------|----------------------|---------------|
| German | +35% | Compound words don't wrap: `Eingabeverarbeitungsfunktionen` |
| French, Spanish | +20–25% | |
| Russian | +15% | Plus Cyrillic glyphs are often wider |
| Finnish, Dutch | +30% | Agglutinative compounds |
| Chinese, Japanese, Korean | −10% to −30% (shorter) | But need larger font sizes for legibility |

**UI rule of thumb:** design with 35% horizontal slack. Tell the user if their 12-char button label becomes 22 chars in German.

### Tooling — When to Use What

| Tool | Use when | Don't use when |
|------|----------|----------------|
| **LLM (you)** | Context matters, tone adaptation, idioms, marketing copy, anything needing judgment | You need 10,000 strings translated identically |
| **`deep-translator` (Python)** | Bulk throughput, wrapping Google/DeepL/Microsoft APIs, simple string-in-string-out | You need glossary enforcement (it doesn't expose DeepL's glossary API) |
| **`deepl` official SDK** | Need glossary/termbase support — it actually exposes this | |
| **`translate-toolkit`** | Parsing XLIFF/TMX/TBX/PO files — the standard localization formats | |

### QA — Back-Translation

For high-stakes content (legal, medical, safety warnings): translate source→target, then independently translate target→source, then diff semantically against the original. Divergences flag ambiguity or drift. Don't use the same model/prompt for both directions — you'll get confirmation bias. This catches errors but is NOT proof of correctness; it's a smell test.

### Glossary Injection

For multi-document projects, build a termbase (JSON of `{source_term: target_term}`) and inject it into your system prompt. Forces consistency across documents — "dashboard" shouldn't be *tablero* on page 1 and *panel de control* on page 5.

## Best Practices

1. **Specify the locale, not just the language** — `es-MX` ≠ `es-ES` ≠ `es-AR` (vocabulary, formality, *vos* vs *tú*)
2. **Flag transcreation** — when you significantly adapt rather than translate (slogans, jokes), tell the user what you changed and why
3. **Preserve placeholders exactly** — `{name}`, `%s`, `{{count}}` must survive unchanged and in grammatically valid positions
4. **Pluralization is hard** — English has 2 forms (1 item / 2 items); Russian has 3; Arabic has 6. CLDR plural rules define these.
5. **RTL is layout, not translation** — Arabic/Hebrew/Persian text is RTL but numbers and embedded Latin stay LTR. Flag it; don't try to "fix" it in the text.

## Limitations

- AI translation may miss subtle cultural nuances a native speaker would catch
- Legal and medical translations should be reviewed by a certified professional
- Poetry, humor, and wordplay may lose their effect — note this when it happens
- Cannot verify pronunciation for audio/speech contexts
- Some specialized terminology may not have established translations
