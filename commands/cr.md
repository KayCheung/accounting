请执行代码 Review。

步骤：
1. 读取 `docs/review/code-review.md` 作为 Review 规范
2. 运行 `git diff main...HEAD -- '*.java' '*.yml' '*.xml'` 获取本次变更
3. 按规范对变更内容逐一检查
4. 输出：总结 + P0/P1/P2 问题列表 + 通过项

如果用户指定了分支（如 `/cr feature/step-08`），则对该分支与 main 的 diff 进行 Review。
```

使用时直接在 Claude Code 输入：
```
/cr
# 或指定分支
/cr feature/step-08
```

Claude Code 会自动读规范、跑 git diff、输出报告，**零摩擦**。

---

## 推荐组合
```
日常 PR Review  →  方案三（/cr 命令，最顺滑）
临时指定文件    →  方案一（手动 git diff）
CI 集成         →  方案二（脚本，接入流水线）