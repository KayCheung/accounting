# Multi-Artifact Creation

Read this reference when the user's request requires building multiple artifacts. It covers sequencing, parallelism, and common pitfalls.

## Core Principle

Never be idle — maximize parallelism by filling every wait window with productive work.

## Sequencing

### Phase 1 — Foundation

1. Write the OpenAPI spec covering ALL planned artifacts upfront — this is the single source of truth for every artifact's API contract.
2. Run codegen, then create the first artifact — `createArtifact()` will guide you to the artifact's skill with build instructions.
3. Some artifact types (like react-vite) launch an async frontend build that creates an idle window. If you have an idle window, use it to build the shared backend for ALL planned artifacts in one pass. If the artifact is built synchronously by the main loop (like expo or slides), build it completely — frontend and backend together — then move on.
4. If you know upcoming artifacts will need generated images (slide images, app icons, etc.), kick off that async generation now rather than waiting until you start building those artifacts. Image generation runs in the background and should overlap with other work.

### Phase 2 — Next artifact

- If you have an idle window from Phase 1 (async frontend still running), create the next artifact and build it now. This fills the wait time with productive work.
- If there is no idle window (first artifact was built synchronously), simply move on to the next artifact after completing the first.
- Read the artifact's skill only when you start building it — not earlier. Skills are large and will consume context you need for the current build.
- Repeat until all artifacts are built.

### Phase 3 — Converge and present

- If any async frontend build is still running, wait for it and fix any issues (missing imports, broken references).
- Finalize any remaining work on all artifacts.
- Restart all artifact workflows in a single parallel batch — not one at a time.
- Check logs after restarting to catch issues before presenting to the user.
- Present all artifacts to the user and call `suggestDeploy()`.

## Ordering Rule

When choosing which artifact to create first, prefer the one with an async frontend build — this maximizes the idle window available for building other artifacts and the shared backend. Currently, `react-vite` is the only artifact type with an async frontend build (`generateFrontend()`). All other types (expo, slides, video-js) are built synchronously.

## Batching

Batch independent operations **within the same artifact** into parallel tool calls:

- Write multiple files for the same artifact in one batch instead of separate calls.
- Read multiple files in one batch when you need context from several files.
- Restart all workflows in parallel, not one at a time.
- Batch image generation calls together when you need multiple images.

**Do NOT build two artifacts simultaneously.** Build one artifact completely, then move to the next. The only reason to start the next artifact before finishing the current one is if you are idle waiting for an async build (like generateFrontend) to complete.

## Visual Consistency

When building subsequent artifacts, carry over brand context from earlier artifacts — colors, fonts, theme, branding — so all artifacts feel visually cohesive. For example, if delegating a video to a design subagent, pass the website's theme/colors so the video matches.

## Design Subagent Limitations

The design subagent is useful for media generation (images, videos) and design iterations after an initial build. However, it cannot produce good results for:

- **React Native / Expo UI** — build mobile app frontends directly in the main loop
- **Slides** — build slide content directly in the main loop
- **react-vite initial builds** — use `generateFrontend()` instead (the react-vite skill covers this)

Only delegate to the design subagent for artifact types where it is effective (check the artifact's skill for guidance).

## Avoiding Wasted Work

- **Don't load artifact skills upfront.** Avoid reading artifact skills (slides, video-js, expo, react-vite, etc.) until you are actively building that artifact. Each skill is large and loading multiple skills at once will fill your context with instructions you can't act on yet. Read each skill just-in-time — when you start building that artifact.

- **Don't read files exploratorily.** Avoid reading scaffolded files that you won't use (e.g., ErrorBoundary, ErrorFallback). Only read files you need for the work you're doing right now.

## Example — Web App + Mobile App

1. Create react-vite artifact first (its skill uses generateFrontend, creating an idle window) → OpenAPI (covering BOTH web + mobile) → codegen → launch generateFrontend
2. While generateFrontend runs: build all shared backend routes for both artifacts
3. Backend done, generateFrontend still running → create expo artifact → build it completely (expo is built synchronously by the main loop)
4. generateFrontend finishes → fix issues → restart all workflows in parallel → check logs → present both

## Critical Rules

- Write the OpenAPI spec and shared backend to serve ALL planned artifacts from the start — do not make separate backend passes per artifact.
- Do NOT create the next artifact until the shared backend is complete (Phase 2). The backend must be in place before building the next artifact's frontend.
- Do NOT read skills for future artifacts early — only read each artifact's skill when you start building it. Loading all skills upfront wastes context and degrades quality.
- At least one artifact MUST use `previewPath: "/"`. If no artifact is at the root path, users will see a blank page when they open the project. Assign `/` to the most important user-facing artifact.
