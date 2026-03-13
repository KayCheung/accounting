---
name: react-vite
description: Guidelines for building React + Vite web apps in the pnpm monorepo with design subagent delegation.
---

Always follow these guidelines when building a React + Vite web application:

## Architecture

- Follow modern web application patterns and best-practices.
- Put as much of the app in the frontend as possible. The backend should only be responsible for data persistence and making API calls.
- Minimize the number of files. Collapse similar components into a single file.
- If the app is complex and requires functionality that can't be done in a single request, it is okay to stub out the backend and implement the frontend first.

## Frontend


- Load the `design` skill and call `generateFrontend()` via the `code_execution_tool` to build the frontend. Do not pass any design-specific recommendations (colors, fonts, layout). Only pass feature descriptions and backend context. The frontend generator has much better taste than you


## Approach

Use the `pnpm-workspace` skill as the source of truth for shared monorepo rules. When you touch backend code, follow the `pnpm-workspace` skill's references:

- `references/openapi.md` for contract-first OpenAPI + codegen
- `references/server.md` for `artifacts/api-server/src/routes/` conventions
- `references/db.md` for `lib/db/src/schema/` and Drizzle guidance


1. Create the artifact and read the `design` skill
2. Define the OpenAPI contract in `lib/api-spec/openapi.yaml`, then run `pnpm --filter @workspace/api-spec run codegen`.
3. Call `generateFrontend()` immediately after codegen, following the `design` skill's `generateFrontend()` rules:
    - Pass only product features and backend context via `implementationNotes`.
    - Pass a short abstract mood via `designStyle` when it helps (for example "clean minimal" or "dark mode professional"). Do not reference specific products or brands.
    - Pass generated API files via `relevantFiles`, plus existing theme/UI files when they are relevant.
    - Do not describe colors, fonts, layout, or other visual implementation details.
    - Do not spend time reading the codegen output before calling `generateFrontend()`; start the frontend job first, then do the remaining backend work while it runs.
4. While the frontend generates, do backend work in parallel:
    - First, run `grep "^export const" lib/api-zod/src/generated/api.ts` to see the exact Zod schema export names. Never assume or guess generated names — they are derived from OpenAPI operation IDs and are not predictable.
    - Add DB schema in `lib/db/src/schema/` when needed, then run `pnpm --filter @workspace/db run push`.
    - Implement API routes in `artifacts/api-server/src/routes/`.
    - Seed example data if the app needs it.
5. Wait for the frontend generation to finish — by this point your backend is done too.

7. Fix any integration issues (restart workflow and refresh logs)
8. Present the artifact — show it to the user.
9. Call `suggestDeploy()` — prompt the user to publish their app so it's live and accessible.

Important Notes:


- Frontend generation runs in the background — call `generateFrontend()` as soon as codegen is complete
- Do not waste time reading or exploring the codegen files before calling `generateFrontend()` — this will lead to a slower build time
- For subsequent design iterations or visual fixes after the initial build, use the design subagent (`subagent(specialization="DESIGN")`)

- Do not read unnecessary files. When building this artifact, you are not building the frontend so reading the generated react hooks is a waste of time and context
- After presenting the artifact, call `suggestDeploy()` so the user knows their app is ready to publish
- Follow the service access and routing rules from the `pnpm-workspace` skill.

## SEO

- Ensure every page has a unique, descriptive title tag (e.g., "Product Name - Category | Site Name")
- Add meta descriptions that summarize page content concisely
- Implement Open Graph tags for better social media sharing appearance

## References

If you are touching the frontend, read these files. If you are launching a design subagent, add the full file path to these files so it reads them before implementing the frontend

- `references/hover_and_elevation.md` - Use this reference when adding or changing hover/active/toggle interaction behavior, elevation effects, or overflow-sensitive interactive styling.
- `references/shadcn_component_rules.md` - Use this reference when building or modifying UI with Shadcn components (especially Button, Card, Badge, Avatar, and Textarea).
- `references/layout_and_spacing.md` - Use this reference when structuring page layouts, sections, spacing rhythm, and component alignment.
- `references/sidebar_rules.md` - Use this reference when building or modifying a sidebar.
- `references/visual_style_and_contrast.md` - Use this reference when choosing contrast, borders, shadows, pane/panel treatment, and hero image presentation.
- `references/frontend_general_rules.md` - Use this reference to learn about frontend setup, best practices, and styling
