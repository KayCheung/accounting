#!/bin/bash
# 用法：./scripts/review.sh [branch] [type]
# type: code（默认）或 prompt
# 示例：./scripts/review.sh feature/step-08 code

BRANCH=${1:-HEAD}
TYPE=${2:-code}
RULE_FILE="docs/review/${TYPE}-review.md"
DIFF_FILE="/tmp/fin-core-review.diff"

git diff main..."$BRANCH" -- '*.java' '*.yml' '*.xml' > "$DIFF_FILE"

echo "===== Review 规范 =====" > /tmp/fin-core-review-input.txt
cat "$RULE_FILE" >> /tmp/fin-core-review-input.txt
echo "" >> /tmp/fin-core-review-input.txt
echo "===== 本次变更 diff =====" >> /tmp/fin-core-review-input.txt
cat "$DIFF_FILE" >> /tmp/fin-core-review-input.txt

echo "✅ Review 输入已生成：/tmp/fin-core-review-input.txt"
echo "📋 直接在 Claude Code 中运行：@/tmp/fin-core-review-input.txt"