# step-01-governance · 开发契约与规范

## 资源声明
| 类型 | 文件 | 说明 |
|------|------|------|
| 本 Step 产出 | `docs/ai-rules/general.md` | 本 Step 创建 |
| 本 Step 产出 | `docs/ai-rules/java.md` | 本 Step 创建 |
| 本 Step 产出 | `docs/ai-rules/accounting.md` | 本 Step 创建 |

---

## 1. 任务目标（Mission）
制定项目开发核心契约与规范，明确 AI 三层规则、VC 上手标准，
完成 Prompt 目录初始化及全员对齐，为后续开发奠定统一基础。

---

## 2. 详细子任务列表

### 2.1 制定 Rules 三层规则文件
- `docs/ai-rules/general.md`：角色定义、技术栈、AI 输出格式约束
- `docs/ai-rules/java.md`：分层架构、异常处理、事务边界、BigDecimal 强制规范
- `docs/ai-rules/accounting.md`：幂等键、借贷平衡、锁顺序、领域术语速查

### 2.2 制作《Vibe Coding 一页纸快速上手》
- Prompt 四段式模板（任务 / 输入 / 输出 / 不要做）
- 会话拆分原则 + 上下文管理规则
- 「复述确认」技巧：要求 AI 先复述任务再执行，避免理解偏差

### 2.3 初始化 `docs/prompt/` 目录
- 按规范创建目录结构
- 创建全部 Step 占位文件，保持格式统一

### 2.4 全员对齐会 + VC 完成首个 Prompt 练习
- TL 主持 30 分钟对齐会，明确核心约束，解答疑问
- VC 按四段式模板完成首个 Prompt 练习，TL 当场确认是否符合规范

---

## 3. 完成标准（Checklist）

- [ ] `docs/ai-rules/` 下三个规则文件就位且内容完整
- [ ] 《Vibe Coding 一页纸快速上手》制作完成并同步至团队
- [ ] `docs/prompt/` 目录及全部 Step 占位文件创建完成
- [ ] 全员对齐会完成，核心约束确认无异议
- [ ] VC 完成首个 Prompt 练习，符合四段式规范

---

## 4. 下一步行动

进入 **Step 2 · Project Initialization**，详见 `docs/prompt/step-02-project-init.md`。
