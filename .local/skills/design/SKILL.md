---
name: design
description: Delegate design tasks to a specialized design subagent. Use subagent with specialization="DESIGN" for synchronous execution or startAsyncSubagent for background execution.
---

# Design Skill


This skill provides two approaches for frontend design work:

1. **`generateFrontend()`** — Fast background frontend generation via `code_execution_tool`. Use this for the **initial build** of a frontend only for react-vite artifacts. Do not use this for any other artifact. It generates a complete, production-ready React frontend in the background while you continue working on the backend.
2. **Design subagent** — A specialized subagent with access to file operations, media generation, web search, and frontend tooling. Use this for **design iterations, fixes, and refinements** after the initial build.

## generateFrontend() — Initial Frontend Build

Use `generateFrontend()` through the `code_execution_tool` to generate the entire frontend in the background. This is significantly faster than launching a design subagent for the first build.

### When to Use

- Initial frontend creation — generating all pages, components, hooks, and styles from scratch
- When the OpenAPI spec and codegen are already done and you need a complete frontend built fast
- For react-vite artifacts only

### When NOT to Use

- Design iterations or visual refinements after the initial build (use the design subagent instead)
- Fixing specific frontend bugs or tweaking individual components (use file editing tools or the design subagent)
- For artifacts like Expo, Slides, Animations, etc.

### generateFrontend(options)

Call this via `code_execution_tool`. It runs in the background — use `wait_for_background_tasks` to check when it's done.

**Parameters:**

- `designStyle` (str, optional): Visual style hint (e.g. "clean minimal", "dark mode professional", "bold colorful startup")
- `implementationNotes` (str, optional): Backend context the frontend needs to know — auth flows, external API integrations, special data patterns
- `artifactPath` (str, **required**): Path to the frontend artifact in the monorepo (e.g. "artifacts/web-app"). This controls where generated files are written — without it, files go to `client/` at the workspace root instead of inside your artifact
- `relevantFiles` (list[str], optional): File paths to read and include as context for generation. Pass generated hooks, existing components, CSS theme files, etc. The generator reads these files and uses their contents to produce accurate, well-integrated code

**Returns:** Dict with job status

```json
{
    "status": "started",
    "jobId": "frontend-happy-tiger",
    "description": "generating frontend components"
}
```

**Example:**

```javascript
const result = await generateFrontend({
    designStyle: "clean minimal with dark mode, professional feel",
    implementationNotes: "REST API at /api, uses JWT auth, has real-time updates via SSE",
    artifactPath: "artifacts/web-app",
    relevantFiles: [
        "lib/api-client-react/src/generated/api.ts",
        "lib/api-client-react/src/generated/api.schemas.ts",
        "artifacts/[react-vite-slug-name]/components/ui/button.tsx",
        "artifacts/[react-vite-slug-name]/src/components/ui/select.tsx"
    ]
});
console.log(result);
// Then continue building the backend while frontend generates
```

### Best Practices for generateFrontend()

1. **Always pass `artifactPath`** — this tells the generator where to write files. Without it, files land in `client/` at the root instead of inside your artifact (e.g. `artifacts/notes-app/src/`).
2. **Call immediately after codegen** — run `pnpm --filter @workspace/api-spec run codegen` first, then call `generateFrontend()` right away. Pass the generated files via `relevantFiles` so the generator has the API hooks and schemas as context. Do not waste time reading codegen output yourself before calling.
3. **Keep working while it runs** — build the backend, set up the database, implement API handlers while the frontend generates in the background.
3. **Trust the output** — the generated frontend is production-ready. Fix integration issues after it completes rather than reviewing every file.
4. **Pass abstract style moods, not product names** — use short visual style hints like "clean minimal", "bold colorful", "dark mode professional", or "playful startup". Do not reference specific products (e.g. "inspired by Linear"), and do not specify colors, fonts, layout details, or design-specific terminology.
5. **Ensure all requirements are met** - The generateFrontend callback will write a file to: `artifacts/notes-app/requirements.yaml` where it specifies what libraries it used and are required. Packages installation and image generation also happens in the background.
6. **index.css** - When you are calling generateFrontend for the first time, add a note in the implementationNotes that the index css is a placeholder. It should use the same format but the current index.css just has placeholder colors but the format must be respected

---

## Design Subagent — Iterations & Refinements



Delegate design-focused tasks to a specialized design subagent with access to file operations, media generation, web search, and frontend tooling.
It is important you tell the design subagent which folder/files it should work within.
It has access to the entire project but it should be given very clear instructions about where it should work either through the task description (if it's a folder) or through relevant files (if it's a few files).
Trust the design subagent's execution of visual details (fonts, colors, spacing). However, when delegating variation generation, include context about what the component does and which dimensions would be most valuable to explore. The design subagent needs to understand the design problem, not just the file path.
If there are specific function or variable names you require the agent to use, outline them in the task description.

### When to Use the Design Subagent

- Design iterations, visual refinements, and polish after the initial frontend build
- Tasks involving image generation, video generation, or stock imagery
- Redesigning specific components or sections with creative direction
- When you need a subagent with design-specific capabilities (image/video generation, stock images, web search)

### When NOT to Use the Design Subagent

- Initial frontend generation (use `generateFrontend()` instead — it's faster)
- General coding tasks without a design focus
- Simple file edits or read-only operations (use tools directly)
- Analysis, planning, or debugging tasks (use code_review skill instead)

### Available Functions


### subagent(task, specialization="DESIGN", relevantFiles)

Launch a design subagent synchronously. Blocks until the subagent completes and returns the result.
**IMPORTANT**: You must include specialization="DESIGN" when you call the design subagent, otherwise it will initialize a general subagent

**Parameters:**

- `task` (str, required): Description of the design task to execute
- `specialization` (str, required): Must be `"DESIGN"` for design tasks
- `relevantFiles` (list[str], optional): File paths the subagent should access

**Returns:** Dict with task results

```json
{
    "success": true,
    "message": "Task summary",
    "subagentAlias": "subagent_design_1",
    "result": "Full task output..."
}
```

**Example:**

```javascript
const result = await subagent({
    task: "Redesign the landing page hero section with a modern gradient background, update typography to use Inter font, and generate a hero illustration",
    specialization: "DESIGN",
    relevantFiles: ["src/pages/landing.tsx", "src/styles/globals.css"]
});
console.log(result);
```

### startAsyncSubagent(task, specialization="DESIGN", relevantFiles)

Launch a design subagent asynchronously in the background. Returns immediately without waiting for completion. Use `waitForBackgroundTasks` to collect results later.

**Parameters:**

- `task` (str, required): Description of the design task to execute
- `specialization` (str, required): Must be `"DESIGN"` for design tasks
- `relevantFiles` (list[str], optional): File paths the subagent should access

**Returns:** Immediately with acknowledgment. Results come via `waitForBackgroundTasks`.

**Example:**

```javascript
await startAsyncSubagent({
    task: "Create a responsive navbar with mobile hamburger menu and smooth animations",
    specialization: "DESIGN",
    relevantFiles: ["src/components/Navbar.tsx", "src/styles/navbar.css"]
});
```


### Best Practices

1. **Be specific about visual requirements**: Include details like colors, fonts, layout, responsive behavior
2. **Include relevant files**: Pass CSS files, component files, and asset directories so the subagent has context
3. **Trust the results**: Design subagent outputs should generally be trusted
4. **Ideally, include design-relevant context files in `relevantFiles`**: The design subagent produces better results when it starts with key project files already open. Consider passing:
   - The main CSS/theme file (e.g., `client/src/index.css`, `src/globals.css`)
   - The API contract or generated hooks (e.g., `shared/schema.ts`, `lib/api-spec/openapi.yaml`)
   - 1-2 existing page or component files as pattern examples


### Design Subagent Capabilities

The design subagent has access to:

- File operations (read, write, edit, glob, grep)
- Bash commands
- Package management
- LSP diagnostics
- Web search and web fetch
- Image generation
- Video generation
- Stock image search

The design subagent does **NOT**:

- Run or restart workflows
- Check workflow/console logs
- Preview/test the app (that's your job as main agent)
- Spawn nested subagents
