<!-- 文件路径：src/views/config/rule/components/RuleExampleDialog.vue -->
<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1000px"
    top="5vh"
    destroy-on-close
    :close-on-click-modal="false"
    class="rule-example-dialog"
  >
    <div v-if="rule" class="example-container">
      <!-- 顶部规则元信息与流程提示卡片 -->
      <div class="rule-meta-banner">
        <div class="meta-row">
          <span class="meta-label">业务标识：</span>
          <el-tag size="small" type="primary" class="mr-2">
            业务线: {{ rule.businessCode }}
          </el-tag>
          <el-tag size="small" type="success" class="mr-2">
            交易编码: {{ rule.tradingCode }}
          </el-tag>
          <el-tag size="small" type="info" class="mr-2">
            支付渠道: {{ rule.payChannel }}
          </el-tag>
          <el-tag size="small" :type="rule.requirePreFreeze === 1 ? 'warning' : 'info'" class="mr-2">
            {{ rule.requirePreFreeze === 1 ? '模式：须先预冻结入账' : '模式：直接记账入账' }}
          </el-tag>
          <el-tag size="small" :type="rule.isOpenAccount ? 'success' : 'info'">
            {{ rule.isOpenAccount ? '支持自动开户' : '关闭自动开户' }}
          </el-tag>
        </div>

        <!-- 预冻结模式温馨指引 -->
        <el-alert
          v-if="rule.requirePreFreeze === 1"
          type="warning"
          :closable="false"
          show-icon
          class="mt-3 flow-alert"
          title="【需先预冻结】入账机制提示："
          description="该记账规则开启了预冻结控制。业务方调用链路为：① 先调用 /accounting/journal/freeze 锁定出金方资金；② 业务终态达成后，调用 /accounting/journal/submit 并传入预冻结号 (origFreezeNo) 执行正式核销入账；③ 若业务中途取消，调用 /accounting/journal/unfreeze 进行解冻释放。"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          class="mt-3 flow-alert"
          title="【直接入账】机制提示："
          description="该记账规则属于直接入账类型。业务方直接调用 /accounting/journal/submit 接口提交业务记账流水，系统将即时完成规则匹配、凭证生成与实时过账。"
        />
      </div>

      <!-- 快速调试配置参数栏 -->
      <div class="config-bar">
        <el-form inline size="small" class="config-form">
          <el-form-item label="服务 Host">
            <el-input
              v-model="customHost"
              placeholder="如 http://api.accounting.example.com"
              style="width: 280px;"
              @change="refreshExamples"
            />
          </el-form-item>
          <el-form-item label="示例交易金额">
            <el-input-number
              v-model="customAmount"
              :min="0.01"
              :precision="2"
              :step="100"
              style="width: 150px;"
              @change="refreshExamples"
            />
          </el-form-item>
          <el-form-item>
            <el-button :icon="RefreshRight" @click="handleRegenerateTraceNo">
              重置示例参数
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 接口步骤切换（预冻结模式包含多个步骤） -->
      <div v-if="rule.requirePreFreeze === 1" class="step-nav-bar">
        <el-radio-group v-model="activeStep" size="default">
          <el-radio-button value="submit">
            步骤二：提交入账核销 (/submit)
          </el-radio-button>
          <el-radio-button value="freeze">
            步骤一：业务资金预冻结 (/freeze)
          </el-radio-button>
          <el-radio-button value="unfreeze">
            异常处理：预冻结全额撤销 (/unfreeze)
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 接口目标地址看板 -->
      <div class="endpoint-board">
        <span class="method-badge">POST</span>
        <span class="endpoint-url">{{ currentEndpointUrl }}</span>
        <span class="endpoint-desc">({{ currentActionDesc }})</span>
      </div>

      <!-- 语言切换与代码展示卡片 -->
      <div class="code-showcase-card">
        <div class="card-header">
          <el-tabs v-model="activeLanguage" class="language-tabs">
            <el-tab-pane label="cURL" name="curl" />
            <el-tab-pane label="JSON" name="json" />
            <el-tab-pane label="Java (HttpClient)" name="java" />
            <el-tab-pane label="JavaScript / Fetch" name="javascript" />
            <el-tab-pane label="Go (net/http)" name="go" />
            <el-tab-pane label="Python (requests)" name="python" />
          </el-tabs>

          <el-button
            size="small"
            :icon="copied ? Check : CopyDocument"
            :type="copied ? 'success' : 'primary'"
            class="copy-btn"
            @click="handleCopyCurrentCode"
          >
            {{ copied ? '已复制到剪贴板' : '复制代码' }}
          </el-button>
        </div>

        <div class="code-viewport">
          <pre class="code-content"><code>{{ currentCodeText }}</code></pre>
        </div>
      </div>

      <!-- 核心入参规范与字典对照 -->
      <div class="params-spec-card mt-3">
        <el-collapse v-model="activeCollapse">
          <el-collapse-item title="查看本规则关键入参字段规范说明 (Field Specification)" name="spec">
            <el-table :data="paramSpecData" border size="small" class="spec-table">
              <el-table-column prop="field" label="字段名称" width="160">
                <template #default="{ row }">
                  <span class="spec-field">{{ row.field }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.type }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="{ row }">
                  <span :class="row.required ? 'text-danger font-bold' : 'text-gray'">
                    {{ row.required ? '是' : '否' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="ruleValue" label="本规则对应值" width="160">
                <template #default="{ row }">
                  <span class="spec-val">{{ row.ruleValue }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="业务约束与说明" min-width="260" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" :icon="CopyDocument" @click="handleCopyCurrentCode">
          复制当前 {{ activeLanguage.toUpperCase() }} 代码
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { RefreshRight, CopyDocument, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { RuleResponse } from '@/api/rule'
import {
  generateRuleExample,
  getFormattedTradeTime,
  getSampleTraceNo,
  type GeneratedRuleExample
} from '../utils/exampleCodeGenerator'

const props = defineProps<{
  modelValue: boolean
  rule: RuleResponse | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val)
})

const dialogTitle = computed(() => {
  if (!props.rule) return '业务方接入代码示例'
  return `业务方接入示例 · 【${props.rule.ruleName}】`
})

// 用户自定义调节参数
const customHost = ref('http://api.accounting.example.com')
const customAmount = ref(1000.0)
const currentTraceNo = ref(getSampleTraceNo('TR'))
const currentTradeTime = ref(getFormattedTradeTime())

// 状态管理
const activeStep = ref<'submit' | 'freeze' | 'unfreeze'>('submit')
const activeLanguage = ref<'curl' | 'json' | 'java' | 'javascript' | 'go' | 'python'>('curl')
const activeCollapse = ref<string[]>(['spec'])
const copied = ref(false)

// 当前生成的成套示例
const generatedExample = ref<GeneratedRuleExample | null>(null)

// 刷新示例代码
function refreshExamples() {
  if (!props.rule) return
  generatedExample.value = generateRuleExample(props.rule, {
    host: customHost.value,
    amount: customAmount.value,
    traceNo: currentTraceNo.value,
    tradeTime: currentTradeTime.value
  })
}

// 重新生成流水号与时间
function handleRegenerateTraceNo() {
  currentTraceNo.value = getSampleTraceNo('TR')
  currentTradeTime.value = getFormattedTradeTime()
  refreshExamples()
  ElMessage.info('已重新生成样例流水号与交易时间')
}

// 监听 rule 变动
watch(
  () => props.rule,
  (newRule) => {
    if (newRule) {
      activeStep.value = 'submit'
      currentTraceNo.value = getSampleTraceNo('TR')
      currentTradeTime.value = getFormattedTradeTime()
      refreshExamples()
    }
  },
  { immediate: true }
)

// 当前目标 Endpoint
const currentEndpointUrl = computed(() => {
  if (!generatedExample.value) return ''
  if (activeStep.value === 'freeze') {
    return generatedExample.value.freezeUrl
  }
  if (activeStep.value === 'unfreeze') {
    return generatedExample.value.unfreezeUrl
  }
  return generatedExample.value.submitUrl
})

// 当前操作描述
const currentActionDesc = computed(() => {
  if (activeStep.value === 'freeze') {
    return 'POST 业务资金预冻结（锁定出金方资金，返回 freezeNo）'
  }
  if (activeStep.value === 'unfreeze') {
    return 'POST 预冻结撤销（根据原预冻结跟踪号全额解冻释放）'
  }
  return props.rule?.requirePreFreeze === 1
    ? 'POST 提交记账流水（传入 origFreezeNo 核销预冻结并完成入账）'
    : 'POST 提交记账流水（直接匹配规则、生成凭证并实时过账）'
})

// 当前展示的代码文本
const currentCodeText = computed(() => {
  if (!generatedExample.value) return ''
  let codes = generatedExample.value.submitCodes
  if (activeStep.value === 'freeze') {
    codes = generatedExample.value.freezeCodes
  } else if (activeStep.value === 'unfreeze') {
    codes = generatedExample.value.unfreezeCodes
  }

  return codes[activeLanguage.value] || ''
})

// 复制代码到剪贴板
async function handleCopyCurrentCode() {
  if (!currentCodeText.value) return
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(currentCodeText.value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = currentCodeText.value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copied.value = true
    ElMessage.success(`已成功复制 ${activeLanguage.value.toUpperCase()} 调用代码`)
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (err) {
    ElMessage.error('复制失败，请手动选中文本复制')
  }
}

// 参数规范表格数据
const paramSpecData = computed(() => {
  if (!props.rule) return []
  const r = props.rule
  const fundsTypes = (r.entries || []).map((e) => e.fundsType).filter(Boolean)
  const uniqueFundsTypes = Array.from(new Set(fundsTypeTypes(fundsTypes)))

  return [
    {
      field: 'traceNo',
      type: 'String',
      required: true,
      ruleValue: '由调用方生成',
      desc: '外部系统交易唯一流水号，长度≤64。全局唯一幂等键，重复提交将返回幂等拦截。'
    },
    {
      field: 'traceSeq',
      type: 'Integer',
      required: false,
      ruleValue: '0',
      desc: '同笔流水子序号，默认填 0。'
    },
    {
      field: 'businessCode',
      type: 'String',
      required: true,
      ruleValue: r.businessCode,
      desc: '业务线代码，必须与本规则严格一致，用于记账引擎精确定位会计规则。'
    },
    {
      field: 'tradingCode',
      type: 'String',
      required: true,
      ruleValue: r.tradingCode,
      desc: '交易编码，必须与本规则严格一致。'
    },
    {
      field: 'payChannel',
      type: 'String',
      required: true,
      ruleValue: r.payChannel,
      desc: '支付渠道编码，如 BANK / CASH / ALIPAY 等，必须与规则一致。'
    },
    {
      field: 'tradeType',
      type: 'Integer',
      required: true,
      ruleValue: '1',
      desc: '交易类型：1-正常入账，2-退款，3-冲账等。'
    },
    {
      field: 'amount',
      type: 'BigDecimal',
      required: true,
      ruleValue: customAmount.value.toFixed(2),
      desc: '交易总金额（大于0，保留2位或更多小数）。必须与 details 各分项金额之和严格相等。'
    },
    {
      field: 'tradeTime',
      type: 'String',
      required: true,
      ruleValue: currentTradeTime.value,
      desc: '交易发生时间，格式规范：YYYY-MM-DDTHH:mm:ss。'
    },
    {
      field: 'origFreezeNo',
      type: 'String',
      required: r.requirePreFreeze === 1,
      ruleValue: r.requirePreFreeze === 1 ? '步骤一返回的 freezeNo' : '无需传入',
      desc:
        r.requirePreFreeze === 1
          ? '原预冻结单号。本规则要求预冻结，此参数必填，入账时系统自动核销该预冻结资金。'
          : '非预冻结规则无需传此参数。'
    },
    {
      field: 'details',
      type: 'Array',
      required: true,
      ruleValue: `包含 ${uniqueFundsTypes.join(', ')}`,
      desc: '交易明细列表。包含 customerId、customerType、itemCode、fundsType 与 amount。'
    },
    {
      field: 'details[].fundsType',
      type: 'String',
      required: true,
      ruleValue: uniqueFundsTypes.join(' / '),
      desc: '款项类型。必须与当前记账规则分录明细中配置的款项类型完全匹配，否则将抛出分录匹配异常。'
    }
  ]
})

function fundsTypeTypes(arr: string[]): string[] {
  return arr.length > 0 ? arr : ['PRINCIPAL']
}
</script>

<style scoped lang="scss">
.rule-example-dialog {
  :deep(.el-dialog__body) {
    padding: 16px 20px;
    background-color: var(--el-bg-color-page, #f8f9fa);
  }
}

.example-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-meta-banner {
  background: var(--el-bg-color, #ffffff);
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);

  .meta-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;

    .meta-label {
      font-size: 13px;
      font-weight: bold;
      color: var(--el-text-color-primary, #303133);
    }
  }

  .flow-alert {
    border-radius: 4px;
    font-size: 13px;
  }
}

.config-bar {
  background: var(--el-bg-color, #ffffff);
  padding: 10px 16px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);

  .config-form {
    margin-bottom: -18px;
  }
}

.step-nav-bar {
  display: flex;
  justify-content: flex-start;
}

.endpoint-board {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #2b313c;
  padding: 8px 14px;
  border-radius: 6px;
  color: #fff;
  font-family: monospace;

  .method-badge {
    background: #409eff;
    color: #fff;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .endpoint-url {
    font-size: 14px;
    color: #a8d5ff;
    word-break: break-all;
  }

  .endpoint-desc {
    font-size: 12px;
    color: #909399;
    font-family: sans-serif;
  }
}

.code-showcase-card {
  background: #1e1e1e;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 16px;
    background: #252526;
    border-bottom: 1px solid #333333;

    .language-tabs {
      :deep(.el-tabs__header) {
        margin: 0;
        border-bottom: none;
      }
      :deep(.el-tabs__item) {
        color: #969696;
        height: 42px;
        line-height: 42px;
        font-size: 13px;
        font-family: monospace;

        &.is-active {
          color: #ffffff;
          font-weight: bold;
        }
      }
      :deep(.el-tabs__active-bar) {
        background-color: #409eff;
      }
    }

    .copy-btn {
      font-size: 12px;
    }
  }

  .code-viewport {
    max-height: 420px;
    overflow-y: auto;
    overflow-x: auto;
    padding: 14px 18px;

    .code-content {
      margin: 0;
      color: #d4d4d4;
      font-family: 'Fira Code', 'Cascadia Code', Consolas, Monaco, monospace;
      font-size: 13px;
      line-height: 1.6;
      white-space: pre;
    }
  }
}

.params-spec-card {
  background: var(--el-bg-color, #ffffff);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  padding: 0 14px;

  :deep(.el-collapse) {
    border: none;
  }
  :deep(.el-collapse-item__header) {
    font-size: 13px;
    font-weight: bold;
    color: var(--el-text-color-primary, #303133);
    border-bottom: none;
  }
  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
  }

  .spec-field {
    font-family: monospace;
    font-weight: bold;
    color: #409eff;
  }

  .spec-val {
    font-family: monospace;
    color: #67c23a;
  }

  .text-danger {
    color: var(--el-color-danger, #f56c6c);
  }
  .font-bold {
    font-weight: bold;
  }
  .text-gray {
    color: #909399;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
