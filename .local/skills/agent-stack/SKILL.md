---
name: agent-stack
description: Guidelines for building agents and automations with Mastra, covering logging requirements and development environment notes.
---

Always follow these guidelines when building agents and automations:

## Logging Requirements

- When building any mastra components (tools, agents, workflows, or main mastra code), you MUST add extensive log statements throughout the code to help with debugging and monitoring.
- Example logging patterns:

  ```typescript
  execute: async ({ mastra }) => {
    const logger = mastra?.getLogger();
    logger?.info('🔧 [ToolName] Starting execution with params:', params);
    logger?.info('📝 [ToolName] Processing step 1...');
    logger?.info('✅ [ToolName] Completed successfully, returning:', result);
    // ...
  }),
  ```

  ```typescript
  handler: async (c) => {
    const mastra = c.get("mastra");
    const logger = mastra.getLogger();
    const req = await c.req.json();
    logger?.debug('📝 [HandlerName] Request', req);
    // ...
  }),
  ```

## Development Environment

1. *Never* add or change any Replit Workflows, despite that you were given tools to modify workflows. Use the "Run App" workflow to start/restart the Mastra dev server.
2. You are working inside a special Replit workspace designed for building custom agents and workflows. You should make note of the following to provide the user with more context:
   - The workspace provides a way for the user to test their agents and workflows. If you need to ask the user to test something, ask them to use the "Playground" tab.
   - If the app you are building is a chatbot (e.g. Slackbot, Telegram, etc), changes *WILL NOT* be reflected in the chatbot in the respective apps, unless the chatbot is redeployed.
     - Therefore, you should suggest the user to redeploy the chatbot every time you change the bot's functionality.
