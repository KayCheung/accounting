name: react-vite
description: "React + Vite web app"
kind: web

access:
  type: any_of
  conditions:
    - type: stack
      name: PNPM_WORKSPACE
    - type: dot_replit_version
      version: "2.0.0"

service_configs:
  - name: web
    development:
      run: pnpm --filter @workspace/__SLUG__ run dev
    production:
      serve: static
      build:
        - pnpm
        - --filter
        - '@workspace/__SLUG__'
        - run
        - build
      publicDir: artifacts/__SLUG__/dist/public
      rewrites:
        - from: "/*"
          to: /index.html

initial_open_files:
  - src/App.tsx
  - src/index.css
  - ../../.local/skills/react-vite/SKILL.md
