# Vibe Coding Universal Configuration
#
本目录提供一套跨 Vibe Coding 工具通用的配置体系，可被 Cursor、GitHub Copilot、CodeGeeX、Amazon Q、Kiro 等代码助手复用。
#
## 使用方式（通用）
1. 将本目录作为项目内的“AI 工作约束与上下文来源”。
2. 在你的 Vibe Coding 工具中，把以下文件内容粘贴/引用到它支持的“项目规则/工作区规则/系统提示入口”等位置：
   - ai-persona.yaml
   - tech-context.yaml
   - execution-rules.yaml
   - dev-standard.yaml
   - business-rules.md
   - steering/ 下的指南与映射表
3. 当切换工具时，优先只调整各文件中的“工具自定义补充区”。
#
## 目录索引
- steering/：工具适配引导配置（适配规则、切换指引、参数映射）
- templates/：通用提示词模板库（按场景分类）
- version/：版本管理与兼容清单
- ignore-files.list：通用忽略清单（类 .gitignore 语法）


