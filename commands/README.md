# 代码Review


## 使用时直接在 Claude Code 输入：
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
临时指定文件     →  方案一（手动 git diff）
CI 集成         →  方案二（脚本 /scripts/review.sh，接入流水线）
```


# 提示词Review

## Review 某个 Step 文件
/pr @docs/prompt/step-08-account-opening.md

## Review 四段式 Prompt（直接粘贴在后面）

```
/pr
【任务】实现自动化开户...
【输入】...
【输出】...
【不要做】...
```