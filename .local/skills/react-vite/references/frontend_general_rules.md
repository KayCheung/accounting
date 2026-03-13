## Frontend

- Use `wouter` for routing on the frontend.
  - If you need to add a new page, add them to the `src/pages` directory and register them in `src/App.tsx`.
  - If there are multiple pages, use a sidebar for navigation. Use the `Link` component or the `useLocation` hook from `wouter` instead of modifying the window directly.
- For forms, always use shadcn's `useForm` hook and `Form` component from `@/components/ui/form` which wraps `react-hook-form`.
  - When appropriate, use the `zodResolver` from `@hookform/resolvers/zod` to validate the form data using the appropriate insert schema from `@shared/schema.ts`.
  - Use `.extend` to add validation rules to the insert schema.
  - Remember that the form component is controlled, ensure you pass default values to the `useForm` hook.
- Always use `@tanstack/react-query` when fetching data.
  - When appropriate, ensure you strongly type the query using the appropriate select type from `@shared/schema.ts`.
  - Queries should not define their own queryFn as the default fetcher is already set up to work with the backend.
  - Mutations should use apiRequest from `@lib/queryClient` to make POST/PATCH/DELETE requests to the backend.
    - Always make sure to invalidate the cache by queryKey after a mutation is made. Don't forget to import `queryClient` from `@lib/queryClient`!
    - For hierarchical or variable query keys use an array for cache segments so cache invalidation works properly. That is, do queryKey: ['/api/recipes', id] instead of queryKey: [`/api/recipes/${id}`].
  - Show a loading or skeleton state while queries (via `.isLoading`) or mutations (via `.isPending`) are being made
  - The template uses TanStack Query v5 which only allows the object form for query related functions. e.g. `useQuery({ queryKey: ['key'] })` instead of `useQuery(['key'])`
- Common pitfalls to avoid:
  - The `useToast` hook is exported from `@/hooks/use-toast`.
  - If a form is failing to submit, try logging out `form.formState.errors` to see if there are form validation errors for fields that might not have associated form fields.
  - DO NOT explicitly import React as the existing Vite setup has a JSX transformer that does it automatically.
  - Use `import.meta.env.<ENV_VAR>` to access environment variables on the frontend instead of `process.env.<ENV_VAR>`. Note that variables must be prefixed with `VITE_` in order for the env vars to be available on the frontend.
  - <SelectItem> will throw an error if it has no value prop. Provide a value prop like this <SelectItem value="option1">
- Add a `data-testid` attribute to every HTML element that users can interact with (buttons, inputs, links, etc.) and to elements displaying meaningful information (user data, status messages, dynamic content, key values).
  - Use unique, descriptive identifiers following this pattern:
    - Interactive elements: `{action}-{target}` (e.g., `button-submit`, `input-email`, `link-profile`)
    - Display elements: `{type}-{content}` (e.g., `text-username`, `img-avatar`, `status-payment`)
  - For dynamically generated elements (lists, grids, repeated components), append a unique identifier at the end: `{type}-{description}-{id}`
    - Examples: `card-product-${productId}`, `row-user-${index}`, `text-price-${itemId}`
    - The dynamic identifier can be any unique value (database ID, index, key) as long as it's unique within that group
  - Keep test IDs stable and descriptive of the element's purpose rather than its appearance or implementation details.

## Styling and Theming

- When defining custom properties in `index.css` that will be used by a tailwind config, always use H S% L% (space separated with percentages after Saturation and Lightness) (and do not wrap in hsl()).
  - For example:
     --my-var: 23 10% 23%;
- Analyze the comments inside of `index.css` to determine how to set colors - replacing every `red` placeholder with an appropriate color. Do NOT forget to replace every single instance of `red`. Pay attention to what you see in index.css.
- Use the `@`-prefixed paths to import shadcn components and hooks.
- Use icons from `lucide-react` to signify actions and provide visual cues. Use `react-icons/si` for company logos.
- User may attach assets (images, etc.) in their request.
  - If the user asks you to include attached assets in the app, you can reference them in the frontend with the `@assets/...` import syntax.
  - For example, if the user attached asset is at `attached_assets/example.png`, you can reference it in the frontend with `import examplePngPath from "@assets/example.png"`.

## Dark Mode

1. Set `darkMode: ["class"]` in tailwind.config.ts and define color variables in :root and .dark CSS classes
2. Create ThemeProvider with useState("light"), useEffect to toggle "dark" class on document.documentElement, and localStorage sync
3. When not using utility class names configured in `tailwind.config.ts`, always use explicit light/dark variants for ALL visual properties: `className="bg-white dark:bg-black text-black dark:text-white"`. When using utility classes configured in tailwind config, you can assume these already been configured to automatically adapt to dark mode.
