---
name: product-manager
description: Create PRDs, write user stories, prioritize features, and plan product roadmaps.
---

# Product Manager

Write PRDs, user stories, and roadmaps. Prioritize features. Default to real templates from top product orgs, not textbook generics.

## When to Use

- PRD, spec, or one-pager needed
- Backlog prioritization
- User stories + acceptance criteria
- Roadmap planning

## When NOT to Use

- Technical architecture (core agent capabilities)
- User research / discovery (design-thinker)

## PRD Formats — Pick by Context

The three most-copied templates in tech. Ask the user their team size and culture, then pick:

### Amazon PR/FAQ ("Working Backwards")

Write the press release *before* building. Used for every Amazon product since 2004 (AWS, Kindle, Prime). Format:

- **Press release (1 page, strict)**: Headline (`[Company] announces [product] to enable [customer] to [benefit]`), sub-headline, dated intro paragraph, problem paragraph (3-4 problems max), solution paragraph, customer quote, how to get started.
- **Internal FAQ**: Every hard question a VP would ask. "What's the BOM?" "Why won't [competitor] crush this?" "What's the failure mode?"

Why it works: the press-release frame forces customer language and ruthlessly exposes when you can't articulate the benefit. If the PR is boring, the product probably is too.

Source templates: Colin Bryar (ex-Bezos chief of staff) at `coda.io/@colin-bryar/working-backwards`, Ian McAllister's LinkedIn template, `github.com/Green-Software-Foundation/pr-faqs` for a real org using PR/FAQ on GitHub.

**Best for**: Big bets, new product lines, when you need exec alignment before committing eng resources.

### Intercom "Intermission" (1-page hard limit)

Paul Adams (VP Product): "An Intermission must always fit on a printed A4 page. If it does not, you haven't a clear enough view of the problem yet." Sections:

- **Problem**: What's broken, why now, links to customer conversations
- **Job Stories** (Intercom invented these — replaces user stories): `When [situation], I want to [motivation], so I can [expected outcome]`. Situation > persona. "When I'm on-call at 3am and an alert fires" beats "As a DevOps engineer."
- **Success criteria**: Qualitative + quantitative
- **NO solution section** — solutions go in Figma, not the PRD

**Best for**: Feature-level work, fast-moving teams, when scope creep is the enemy.

### Linear's Project Spec

Nan Yu (Head of Product at Linear). Short, outcome-focused: Problem → Proposed solution → Success metrics → Non-goals → Open questions. Non-goals are load-bearing — explicitly listing what you're *not* building is the single most effective scope-creep prevention.

**Best for**: Eng-heavy teams already in Linear, projects with clear shape.

Full template collection: `hustlebadger.com/what-do-product-teams-do/prd-template-examples/` (Figma, Asana, Shape Up, Lenny's 1-Pager all compared).

### Cross-template patterns (from analysis of 13+ company PRDs)

1. Problem strictly before solution — every high-performing template enforces this
2. Explicit "Non-goals" section — second most common element
3. Living docs — version + changelog, not write-once

## Prioritization

**RICE** (Intercom's framework — the de facto standard):

- **Reach**: Users affected per quarter. Use real numbers from analytics, not guesses.
- **Impact**: 3 = massive, 2 = high, 1 = medium, 0.5 = low, 0.25 = minimal
- **Confidence**: 100% = data-backed, 80% = strong intuition, 50% = guessing. This multiplier is what makes RICE better than ICE — it punishes wishful thinking.
- **Effort**: Person-months, all functions (PM + design + eng + QA)
- Score = (R × I × C) / E. Build as a spreadsheet — agent can generate CSV.

**When NOT to use RICE**: When effort estimates are garbage (early-stage), when one item is existential (just do it), when the list is >30 items (you have a strategy problem, not a prioritization problem).

**Cost of Delay / WSJF** (SAFe framework): (User value + Time criticality + Risk reduction) / Job size. Better than RICE when timing/sequencing matters (regulatory deadlines, market windows).

**Kano**: Survey users on each feature twice — "how would you feel if we had this?" and "how would you feel if we didn't?" Cross-tab reveals Basic/Performance/Delighter/Indifferent. Reference: `foldingburritos.com/blog/kano-model` for the full method + survey template.

## Roadmap Format

Now / Next / Later (GOV.UK popularized this — intentionally vague on dates to avoid roadmap-as-contract):

```text

## Theme: [one strategic bet this quarter]

### Now (committed, in flight)
| Initiative | Owner | Success metric | Status |

### Next (committed, not started)
| Initiative | Why now | Dependency |

### Later (directional, not committed)

- [bullets only — dates here are lies]

```

Public roadmap examples to reference: `github.com/github/roadmap` (GitHub's own), Buffer's transparent roadmap, Linear's changelog.

## Acceptance Criteria

Gherkin syntax (Given/When/Then) — directly executable as test cases:

```text
Given [precondition]
When [action]
Then [observable result]
And [additional result]

```

One scenario per acceptance criterion. If you can't write it as Given/When/Then, the requirement is ambiguous.

## Limitations

- Cannot integrate with Jira/Linear/Asana — deliver as markdown for copy-paste
- Cannot access user analytics — ask user for reach/retention numbers before RICE scoring
- Templates are starting points; teams should adapt
