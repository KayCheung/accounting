# step-05-java-b · 凭证域 + 规则域自定义 Mapper

## 任务目标

在 Step 3 生成的基础 Mapper 之上，补充凭证域和规则域业务所需的自定义查询方法。

## 凭证域自定义方法清单

| # | Mapper | 方法 | XML 位置 | 说明 | 索引 |
|---|--------|------|----------|------|------|
| 1 | AccountingVoucherMapper | `selectWithEntries(voucherNo)` | AccountingVoucherMapper.xml | 按凭证号查询凭证 | uk_voucher_no (const) |
| 2 | AccountingVoucherMapper | `selectByTraceNo(traceNo)` | AccountingVoucherMapper.xml | 按系统跟踪号查询凭证 | uk_trace_no (ref) |
| 3 | AccountingVoucherMapper | `selectReversalByOrig(origVoucherNo)` | AccountingVoucherMapper.xml | 按原凭证号查询红冲凭证 | idx_orig_voucher_no (ref) |
| 4 | AccountingVoucherEntryMapper | `selectPendingPosting(voucherNo)` | AccountingVoucherEntryMapper.xml | 查询待过账/过账失败的分录 | idx_voucher_no (ref) |
| 5 | AccountingVoucherEntryMapper | `selectByVoucherNo(voucherNo)` | AccountingVoucherEntryMapper.xml | 按凭证号查询分录列表 | idx_voucher_no (ref) |
| 6 | AccountingVoucherAuxiliaryMapper | `selectByEntryId(entryId)` | AccountingVoucherAuxiliaryMapper.xml | 按分录ID查询辅助核算项 | uk_entry_id (ref) |
| 7 | AccountingVoucherAuxiliaryMapper | `selectByVoucherNo(voucherNo)` | AccountingVoucherAuxiliaryMapper.xml | 按凭证号查询辅助核算项 | idx_voucher_no (ref) |
| 8 | AccountingVoucherAttachmentMapper | `selectByVoucherNo(voucherNo)` | AccountingVoucherAttachmentMapper.xml | 按凭证号查询附件 | idx_voucher_no (ref) |

## 规则域自定义方法清单

| # | Mapper | 方法 | XML 位置 | 说明 | 索引 |
|---|--------|------|----------|------|------|
| 1 | AccountingRuleMapper | `selectByBusinessKey(businessCode, tradingCode, payChannel)` | AccountingRuleMapper.xml | 按业务键查询规则 | uk_accounting_rule (const) |
| 2 | AccountingRuleMapper | `selectEnabledRules()` | AccountingRuleMapper.xml | 查询已启用的规则（status=2） | 小表全表扫描可接受 |
| 3 | AccountingRuleDetailMapper | `selectWithAuxiliary(ruleId)` | AccountingRuleDetailMapper.xml | 按规则ID查询明细含辅助核算项 | uk_rule_id (ref) |
| 4 | BufferPostingDetailMapper | `selectBySharding(sharding, status)` | BufferPostingDetailMapper.xml | 按分片值查询缓冲记账明细（支持状态过滤） | idx_create_time |

## Repository 封装

- `AccountingVoucherRepository`: 封装凭证、分录、辅助核算项、附件查询
- `AccountingRuleRepository`: 封装规则、规则明细、辅助核算项、缓冲记账查询

## 单测覆盖

- `AccountingVoucherRepositoryTest`: 凭证联查返回分录和辅助核算项；分录待过账查询
- `AccountingRuleRepositoryTest`: 缓冲扫描分片查询结果互不重叠
