---
name: ai-recruiter
description: Source and evaluate candidates with job analysis, search strategies, specific candidate profiles, and outreach templates.
---

# AI Recruiter

Help source and evaluate candidates for open roles. Analyze job descriptions, build search strategies, find specific candidate profiles, and draft outreach messages.

## When to Use

- User needs to hire for a role and wants sourcing strategy
- User wants to improve a job description
- User needs interview questions for a specific role
- User wants candidate evaluation criteria
- User wants to find specific candidate profiles for a role

## When NOT to Use

- Sales prospecting (use find-customers skill)
- General market research (use deep-research skill)
- Writing job-related content (use content-machine skill)

## Workflow — Follow This Order

### Step 0: Research First, Then Ask Questions

Before producing any output, always do two things in this order:

**0a. Search for the role and company.**
If the user names a company or role, use `webSearch` to find:

- The actual job posting (check Ashby, Lever, Greenhouse, the company careers page)
- Latest company details: funding, valuation, headcount, ARR, recent news
- Competitor landscape for the role

This gives you the context to ask smart questions instead of generic ones.

**0b. Ask the user clarifying questions.**
Do not assume details. Ask about:

- Which specific role (if the company has multiple open positions, list them as choices)
- Seniority level
- Location / remote policy
- What candidate background matters most (domain-specific vs. open to adjacent backgrounds)
- Whether competitors are fair game for sourcing
- Any specific gaps on the team they're trying to fill (e.g., growth, technical, enterprise, design)
- Any other preferences (e.g., founder background, specific skills)

Only proceed to output after you have answers.

### Step 1: Calibrate the Role

Split requirements into three buckets — be ruthless, most JDs list nice-to-haves as must-haves and shrink the pool 80%:

- **Must-have** (3-4 max): Deal-breakers. Can't do the job without these on day one.
- **Learnable in 90 days**: Most "required" skills belong here.
- **Pedigree signals**: School, FAANG experience, etc. — these filter for bias, not ability. Drop them unless there's a specific reason.

**Comp research:** `webSearch: "levels.fyi [role] [company tier]"` or `"[role] salary [city] site:glassdoor.com"`. For startups, `webSearch: "Pave [role] equity benchmarks"`. Keep comp in the internal strategy doc for reference but do NOT include it in outreach templates by default.

### Step 2: Build Boolean Search Strings

Boolean-savvy recruiters fill roles ~23% faster (LinkedIn 2023 data). LinkedIn Recruiter caps each field at ~300 chars — split across Title and Keywords rather than cramming one field.

**Core pattern — put role in Title, skills in Keywords:**

```text
Title: ("staff engineer" OR "senior engineer" OR "tech lead" OR "principal")
Keywords: (Rust OR Go OR "distributed systems") AND (Kubernetes OR k8s) NOT (manager OR director OR intern)
```

**Synonym rings — the #1 missed tactic.** Titles fragment massively across companies:

```text
("product manager" OR "product owner" OR "PM" OR "program manager" OR "product lead")
("data scientist" OR "ML engineer" OR "machine learning engineer" OR "applied scientist" OR "research scientist")
("SRE" OR "site reliability" OR "devops engineer" OR "platform engineer" OR "infrastructure engineer")
```

**Impact-verb trick** — surface doers, not title-holders:

```text
("built" OR "shipped" OR "launched" OR "scaled" OR "led migration" OR "0 to 1")
```

**X-ray search (Google, bypasses LinkedIn limits):**

```text
site:linkedin.com/in ("staff engineer" OR "principal engineer") "rust" "san francisco" -recruiter -hiring
```

### Step 3: Provide Direct LinkedIn Search Links

Always generate at least 5 clickable LinkedIn search URLs that the user can open directly in their browser. These should be pre-built with URL-encoded keywords, location filters, and relevant company/skill terms.

**URL format:**

```text
https://www.linkedin.com/search/results/people/?keywords=URL_ENCODED_KEYWORDS&geoUrn=%5B%22GEO_ID%22%5D&origin=FACETED_SEARCH
```

**Common geo IDs:**

- SF Bay Area: `102095887`
- New York: `103644278`
- US: `103644278`
- London: `90009496`

Create separate links for different search angles:

1. Candidates at direct competitors
2. Candidates with the specific skill/background the user prioritized
3. Candidates at adjacent companies in the space
4. Candidates at tier 2/3 companies (bigger pool)
5. Broader keyword search for passive candidates

### Step 4: Find Specific Candidate Profiles

Always use `webSearch` with `site:linkedin.com/in` queries to find specific named candidates. Search multiple angles:

- PMs/engineers at competitor companies
- People with the specific background the user asked for (e.g., founder experience, UI expertise)
- People at adjacent companies in the same space

Present candidates in a table with:

- Name
- Current role
- Why they fit (1 sentence)
- Hyperlinked LinkedIn profile URL

Aim for 10-15 specific profiles, organized into tiers (e.g., direct competitors, adjacent companies, broader pool).

### Step 5: Source Beyond LinkedIn

LinkedIn InMail response rates have dropped from 30%+ to 10-13% over 5 years as the platform saturated. Diversify:

| Channel | Best for | Tactic |
|---------|----------|--------|
| **GitHub** | Engineers | `webFetch` their profile — check contribution graph (consistent > spiky), pinned repos, languages bar, PR review quality on public projects. |
| **GitHub Search** | Niche skills | `site:github.com "location: [city]" language:Rust` or search commits/issues in relevant OSS projects |
| **Stack Overflow** | Deep specialists | Top answerers on niche tags — check profile for contact info |
| **Conference talks** | Senior/staff+ | `webSearch: "[conference name] speakers 2025"` — speakers are pre-vetted for communication skills |
| **Papers/Google Scholar** | ML/research | Co-authors on relevant papers, often with .edu emails |
| **HN "Who wants to be hired"** | Startup-minded | Monthly thread, candidates self-describe, `site:news.ycombinator.com "who wants to be hired"` |
| **Product Hunt** | Builder-types | Makers of top products in the relevant category |
| **Twitter/X** | Thought leaders | Search for people posting about the relevant domain |
| **YC Alumni** | Founder-PMs | Founders whose startups ended and moved into PM/leadership roles |
| **Paid aggregators** | Volume | SeekOut, HireEZ (45+ platforms), Gem, Juicebox/PeopleGPT |

### Step 6: Outreach That Gets Replies

**2025 benchmarks:** Cold InMail averages 10-13% response. Personalized outreach with a specific hook hits 20%+. 86% of candidates ignore generic messages entirely (TalentBoard 2024).

**Structure — 4 sentences max:**

1. **Hook** (why *them*, specifically): "Saw your PR on the Tokio scheduler — the approach to work-stealing was clean."
2. **Why this role matters** (to them, not to you): "We're 12 engineers, pre-Series-B, and the entire storage layer is unowned."
3. **One concrete detail**: Remote policy, a tech problem they'd find interesting, team size, or growth metrics. Avoid listing comp — save that for when they respond.
4. **Low-friction CTA**: "Worth 15 min to hear more?" — not "Let me know if you're open to opportunities."

**Do NOT include compensation in outreach templates.** Comp details belong in the internal strategy section. If a candidate responds, share comp on the first call. Leading with comp in cold outreach can anchor low or signal desperation.

**Subject lines:** Use their project name or the specific tech, not "Opportunity at [Company]." Lowercase, short, looks like a peer wrote it.

**Follow-up:** One bump at day 5 with a *new* piece of info (funding news, a blog post, the hiring manager's name). Never "just following up."

**Generate 3 outreach templates** tailored to different candidate segments (e.g., competitors, adjacent companies, career-changers). Customize the angle for each.

### Step 7: Suggested Interview Questions

Include a short section of suggested interview questions at the bottom of the output. Use behavioral questions (STAR format) over hypothetical ones. Organize by the key criteria identified in Step 1.

Keep it lightweight — 2 questions per criterion, 3-4 criteria max. No scoring rubrics or evaluation matrices unless the user specifically asks for one.

## Hiring Benchmarks

- **Time-to-fill**: 44 days US average (SHRM 2025); tech roles run longer
- **Cost-per-hire**: $6-7k standard tech roles; $12k+ for ML/security/staff+ (Deloitte 2024)
- **Funnel**: Tech roles see ~110 applicants/opening, ~5% get interviews
- **Speed matters**: Top candidates are off-market in 10 days. The interview-to-offer stage is where most teams lose — compressing it cuts time-to-hire by ~26%.
- **LinkedIn Recruiter cost**: $1.6k/yr (Lite) to $10.8k+/yr (Corporate, 150+ InMails/mo)

## Bias Reduction

- Strip unnecessary degree requirements — they filter for socioeconomic background, not skill
- Run JD through `webSearch: "gender decoder job description"` tools — "rockstar," "ninja," "aggressive" skew male applicant pools
- Same questions, same order for every candidate
- Score immediately after each interview, before discussing with other interviewers (anchoring bias)
- Source from non-traditional channels (HN, PH, YC alumni, blogs) to avoid LinkedIn-only pool bias

## Output Structure

The final deliverable should follow this order:

1. **Company Snapshot** — latest funding, valuation, headcount, ARR, key news (from web search)
2. **Role Details** — title, posting link, focus area, seniority, location, key needs (from user answers)
3. **Estimated Comp Range** — internal reference only, not for outreach
4. **Requirements** — must-haves / learnable / pedigree signals to drop
5. **Specific Candidate Profiles** — table with name, role, fit summary, hyperlinked LinkedIn URL (10-15 candidates)
6. **LinkedIn Search Links** — at least 5 clickable URLs the user can open directly
7. **Boolean Search Strings** — for LinkedIn Recruiter and Google X-ray
8. **Sourcing Channels** — beyond LinkedIn (table format)
9. **Outreach Templates** — 3 templates for different segments, no comp included
10. **Sourcing Action Plan** — 2-week day-by-day plan with target funnel
11. **Bias Reduction Checklist**
12. **Suggested Interview Questions** — lightweight, behavioral, organized by key criteria

## Limitations

- Cannot log into LinkedIn Recruiter, SeekOut, Gem, or HireEZ — builds search strings the user pastes in
- Cannot send InMails or emails
- Cannot verify employment history or run background checks
- GitHub analysis via `webFetch` only works for public profiles/repos
- LinkedIn search URLs use public search — results may vary based on the user's LinkedIn account tier
