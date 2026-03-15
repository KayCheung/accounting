请执行提示词 Review。

步骤：
1. 读取 `docs/review/prompt-review.md` 作为 Review 规范
2. 判断用户提供的内容类型：
   - 如果是 `docs/prompt/step-XX-xxx.md` 文件 → 按 Part A（Step 文件）规范检查
   - 如果是四段式 Prompt 文本 → 按 Part B（四段式 Prompt）规范检查
3. 按规范逐一检查
4. 输出：总结 + 必须修正项 + 建议优化项 + 通过项

使用方式：
- `/pr @docs/prompt/step-08-account-opening.md` → Review 指定 Step 文件
- `/pr` 后粘贴四段式 Prompt 文本 → Review 该 Prompt











---

```

# Review 某个 Step 文件
/pr @docs/prompt/step-08-account-opening.md

# Review 四段式 Prompt（直接粘贴在后面）
/pr
【任务】实现自动化开户...
【输入】...
【输出】...
【不要做】...

```