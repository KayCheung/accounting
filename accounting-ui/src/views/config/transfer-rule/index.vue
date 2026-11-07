<!-- 文件路径：src/views/config/transfer-rule/index.vue -->
<template>
  <div class="transfer-rule-page">
    <!-- 顶部检索与操作栏卡片 -->
    <div class="fin-card filter-card">
      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="规则编码/名称">
          <el-input
            v-model="queryForm.keyword"
            placeholder="输入规则编码或名称..."
            clearable
            style="width: 220px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="结转类型">
          <el-select
            v-model="queryForm.transferType"
            placeholder="全部类型"
            clearable
            style="width: 150px;"
            @change="handleSearch"
          >
            <el-option label="损益结转" :value="1" />
            <el-option label="成本结转" :value="2" />
            <el-option label="自定义结转" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="结转周期">
          <el-select
            v-model="queryForm.periodCycle"
            placeholder="全部周期"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option label="每日/日结" :value="1" />
            <el-option label="月末/月结" :value="2" />
            <el-option label="季末/季结" :value="3" />
            <el-option label="年末/年结" :value="4" />
            <el-option label="仅手动/自定义" :value="5" />
          </el-select>
        </el-form-item>

        <el-form-item label="自动结转">
          <el-select
            v-model="queryForm.autoTransfer"
            placeholder="全部"
            clearable
            style="width: 130px;"
            @change="handleSearch"
          >
            <el-option label="支持自动结转" :value="true" />
            <el-option label="仅限手动结转" :value="false" />
          </el-select>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 130px;"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            查询
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <div class="action-btn-group">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新增结转规则
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 规则表格卡片 -->
    <div class="fin-card table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        class="fin-table"
      >
        <el-table-column prop="executeOrder" label="顺序" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain" class="order-tag">
              {{ row.executeOrder }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="ruleCode" label="规则编码" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-code" @click="copyText(row.ruleCode)">{{ row.ruleCode }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />

        <el-table-column prop="transferType" label="结转类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getTransferTypeTag(row.transferType)" effect="light">
              {{ row.transferTypeDesc || '损益结转' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="periodCycle" label="结转周期" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getPeriodCycleTag(row.periodCycle)" effect="plain" size="small">
              {{ row.periodCycleDesc || formatPeriodCycle(row.periodCycle) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="autoTransfer" label="自动结转" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.autoTransfer ? 'success' : 'info'" size="small">
              {{ row.autoTransfer ? '支持自动' : '仅手动' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="sourceSubjectCode" label="源科目范围 (通配符)" width="160">
          <template #default="{ row }">
            <el-tag type="warning" effect="plain" class="pattern-tag">
              {{ row.sourceSubjectCode }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="targetSubjectCode" label="目标科目" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.targetSubjectCode }}</span>
            <span v-if="row.targetSubjectName" class="subject-name">({{ row.targetSubjectName }})</span>
          </template>
        </el-table-column>

        <el-table-column prop="transferDirectionDesc" label="结转方向" width="170">
          <template #default="{ row }">
            <span class="direction-text">
              <el-icon class="dir-icon"><Right /></el-icon>
              {{ row.transferDirectionDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="2"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>


        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-popconfirm
              title="确定删除该期末结转规则吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small" :icon="Delete">
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页栏 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNo"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </div>

    <!-- 规则说明卡片（吸纳原型指引） -->
    <div class="fin-card guide-card">
      <div class="guide-header">
        <el-icon class="guide-icon"><InfoFilled /></el-icon>
        <span>期末结转规则财务说明与配置指南</span>
      </div>
      <div class="guide-body">
        <div class="guide-item">
          <span class="guide-title">损益结转：</span>
          <span class="guide-desc">将损益类科目（收入 60*、费用/成本 64*/66* 等）的期末发生额轧差结转至所有者权益类的“本年利润 (410301)”，实现损益科目期末余额归零。</span>
        </div>
        <div class="guide-item">
          <span class="guide-title">成本结转：</span>
          <span class="guide-desc">将制造费用、直接人工等成本类科目结转至生产成本或产成品科目，用于核算产品期末资产价值。</span>
        </div>
        <div class="guide-item">
          <span class="guide-title">通配符机制：</span>
          <span class="guide-desc">支持星号通配符模式匹配多级科目，如 <code>60*</code> 匹配所有 60 开头的科目，<code>6*</code> 匹配所有损益科目。结转时将自动遍历有非零余额的账户批量生成借贷平衡凭证。</span>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑规则弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑期末结转规则' : '新增期末结转规则'"
      width="640px"
      destroy-on-close
      class="rule-dialog"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="130px"
        class="rule-form"
      >
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input
            v-model="formData.ruleCode"
            placeholder="如: TR_REV_PROFIT"
            :disabled="isEdit"
            maxlength="32"
            show-word-limit
          />
          <div class="form-tip">大写字母与下划线组成，创建后不可修改</div>
        </el-form-item>

        <el-form-item label="规则名称" prop="ruleName">
          <el-input
            v-model="formData.ruleName"
            placeholder="如: 主营业务收入损益结转"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="结转类型" prop="transferType">
          <el-radio-group v-model="formData.transferType">
            <el-radio :value="1">损益结转</el-radio>
            <el-radio :value="2">成本结转</el-radio>
            <el-radio :value="3">自定义结转</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="源科目通配符" prop="sourceSubjectCode">
          <el-input
            v-model="formData.sourceSubjectCode"
            placeholder="如: 60* 或 6401*"
            maxlength="32"
          />
          <div class="shortcut-pills">
            <span class="label">常用快捷填入：</span>
            <el-tag size="small" class="pill" @click="formData.sourceSubjectCode = '60*'">60* 营业收入</el-tag>
            <el-tag size="small" class="pill" @click="formData.sourceSubjectCode = '64*'">64* 营业成本</el-tag>
            <el-tag size="small" class="pill" @click="formData.sourceSubjectCode = '66*'">66* 期间费用</el-tag>
          </div>
        </el-form-item>

        <el-form-item label="目标科目编码" prop="targetSubjectCode">
          <el-input
            v-model="formData.targetSubjectCode"
            placeholder="输入末级目标科目编码，如: 410301"
            maxlength="32"
          />
          <div class="shortcut-pills">
            <span class="label">常用快捷填入：</span>
            <el-tag size="small" class="pill" @click="formData.targetSubjectCode = '410301'">410301 本年利润</el-tag>
            <el-tag size="small" class="pill" @click="formData.targetSubjectCode = '410401'">410401 利润分配</el-tag>
          </div>
        </el-form-item>

        <el-form-item label="结转周期" prop="periodCycle">
          <el-radio-group v-model="formData.periodCycle">
            <el-radio :value="1">每日 (日结)</el-radio>
            <el-radio :value="2">月末 (月结)</el-radio>
            <el-radio :value="3">季末 (季结)</el-radio>
            <el-radio :value="4">年末 (年结)</el-radio>
            <el-radio :value="5">仅手动</el-radio>
          </el-radio-group>
          <div class="form-tip">日切与定时调度仅在该周期到达时自动执行</div>
        </el-form-item>

        <el-form-item label="自动结转支持" prop="autoTransfer">
          <div class="switch-field-wrapper">
            <el-switch
              v-model="formData.autoTransfer"
              active-text="开启自动"
              inactive-text="仅限手动"
            />
            <div class="form-tip">开启后系统在到达该周期时自动执行；关闭后仅支持财务手工触发</div>
          </div>
        </el-form-item>

        <el-form-item label="结转方向" prop="transferDirection">
          <el-radio-group v-model="formData.transferDirection">
            <el-radio :value="1">借方余额结转到贷方 (费用/支出类)</el-radio>
            <el-radio :value="2">贷方余额结转到借方 (收入/收益类)</el-radio>
          </el-radio-group>
        </el-form-item>


        <el-form-item label="执行优先级" prop="executeOrder">
          <div class="priority-field-inline">
            <el-input-number v-model="formData.executeOrder" :min="1" :max="9999" />
            <span class="form-tip inline-tip">数值越小越先执行（建议收入结转=10，费用结转=20）</span>
          </div>
        </el-form-item>

        <el-form-item label="摘要模板" prop="summaryTemplate">
          <el-input
            v-model="formData.summaryTemplate"
            placeholder="如: {year}年{month}月主营业务收入结转"
            maxlength="128"
          />
          <div class="form-tip">支持变量占位符：{year} 代表年份，{month} 代表月份</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search,
  RefreshRight,
  Plus,
  Refresh,
  Edit,
  Delete,
  Right,
  InfoFilled
} from '@element-plus/icons-vue'
import {
  getTransferRulePage,
  createTransferRule,
  updateTransferRule,
  updateTransferRuleStatus,
  deleteTransferRule,
  type TransferRuleItem,
  type TransferRuleCreateRequest,
  type TransferRuleUpdateRequest
} from '@/api/transfer'

// 检索表单
const queryForm = reactive({
  keyword: '',
  transferType: undefined as number | undefined,
  periodCycle: undefined as number | undefined,
  autoTransfer: undefined as boolean | undefined,
  status: undefined as number | undefined
})

const loading = ref(false)
const tableData = ref<TransferRuleItem[]>([])

const pagination = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

// 弹窗状态与表单
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentEditId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref()

const formData = reactive({
  ruleCode: '',
  ruleName: '',
  transferType: 1,
  sourceSubjectCode: '',
  targetSubjectCode: '',
  transferDirection: 2,
  summaryTemplate: '{year}年{month}月期末结转',
  executeOrder: 10,
  autoTransfer: true,
  periodCycle: 2
})

const formRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  transferType: [{ required: true, message: '请选择结转类型', trigger: 'change' }],
  periodCycle: [{ required: true, message: '请选择结转周期', trigger: 'change' }],
  sourceSubjectCode: [{ required: true, message: '请输入源科目通配符', trigger: 'blur' }],
  targetSubjectCode: [{ required: true, message: '请输入目标科目编码', trigger: 'blur' }],
  transferDirection: [{ required: true, message: '请选择结转方向', trigger: 'change' }]
}

function getTransferTypeTag(type: number): 'primary' | 'warning' | 'info' {
  if (type === 1) return 'primary'
  if (type === 2) return 'warning'
  return 'info'
}

function getPeriodCycleTag(cycle?: number): '' | 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (cycle) {
    case 1: return 'primary'
    case 2: return 'success'
    case 3: return 'warning'
    case 4: return 'danger'
    default: return 'info'
  }
}

function formatPeriodCycle(cycle?: number): string {
  switch (cycle) {
    case 1: return '每日/日结'
    case 2: return '月末/月结'
    case 3: return '季末/季结'
    case 4: return '年末/年结'
    case 5: return '仅手动'
    default: return '月末/月结'
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getTransferRulePage({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      ruleCode: queryForm.keyword ? queryForm.keyword.trim() : undefined,
      ruleName: queryForm.keyword ? queryForm.keyword.trim() : undefined,
      transferType: queryForm.transferType,
      periodCycle: queryForm.periodCycle,
      autoTransfer: queryForm.autoTransfer,
      status: queryForm.status
    })
    tableData.value = res.list || res.records || []
    pagination.total = res.total || 0
  } catch (err: any) {
    ElMessage.error(err.message || '加载结转规则列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNo = 1
  fetchData()
}

function handleReset() {
  queryForm.keyword = ''
  queryForm.transferType = undefined
  queryForm.periodCycle = undefined
  queryForm.autoTransfer = undefined
  queryForm.status = undefined
  handleSearch()
}

function openCreateDialog() {
  isEdit.value = false
  currentEditId.value = null
  Object.assign(formData, {
    ruleCode: '',
    ruleName: '',
    transferType: 1,
    sourceSubjectCode: '',
    targetSubjectCode: '410301',
    transferDirection: 2,
    summaryTemplate: '{year}年{month}月期末结转',
    executeOrder: 10,
    autoTransfer: true,
    periodCycle: 2
  })
  dialogVisible.value = true
}

function openEditDialog(row: TransferRuleItem) {
  isEdit.value = true
  currentEditId.value = row.id
  Object.assign(formData, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    transferType: row.transferType,
    sourceSubjectCode: row.sourceSubjectCode,
    targetSubjectCode: row.targetSubjectCode,
    transferDirection: row.transferDirection,
    summaryTemplate: row.summaryTemplate || '',
    executeOrder: row.executeOrder || 10,
    autoTransfer: row.autoTransfer ?? true,
    periodCycle: row.periodCycle ?? 2
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    if (isEdit.value && currentEditId.value) {
      const updateData: TransferRuleUpdateRequest = {
        ruleName: formData.ruleName,
        transferType: formData.transferType,
        sourceSubjectCode: formData.sourceSubjectCode,
        targetSubjectCode: formData.targetSubjectCode,
        transferDirection: formData.transferDirection,
        summaryTemplate: formData.summaryTemplate,
        executeOrder: formData.executeOrder,
        autoTransfer: formData.autoTransfer,
        periodCycle: formData.periodCycle
      }
      await updateTransferRule(currentEditId.value, updateData)
      ElMessage.success('结转规则修改成功')
    } else {
      const createData: TransferRuleCreateRequest = {
        ruleCode: formData.ruleCode,
        ruleName: formData.ruleName,
        transferType: formData.transferType,
        sourceSubjectCode: formData.sourceSubjectCode,
        targetSubjectCode: formData.targetSubjectCode,
        transferDirection: formData.transferDirection,
        summaryTemplate: formData.summaryTemplate,
        executeOrder: formData.executeOrder,
        autoTransfer: formData.autoTransfer,
        periodCycle: formData.periodCycle
      }
      await createTransferRule(createData)
      ElMessage.success('结转规则创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    submitting.value = false
  }
}


async function handleStatusChange(row: TransferRuleItem) {
  try {
    await updateTransferRuleStatus(row.id, row.status)
    ElMessage.success(`规则 [${row.ruleName}] 已${row.status === 1 ? '启用' : '停用'}`)
  } catch (err: any) {
    row.status = row.status === 1 ? 2 : 1 // 回滚状态
    ElMessage.error(err.message || '切换状态失败')
  }
}

async function handleDelete(row: TransferRuleItem) {
  try {
    await deleteTransferRule(row.id)
    ElMessage.success('规则删除成功')
    fetchData()
  } catch (err: any) {
    ElMessage.error(err.message || '删除规则失败')
  }
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success(`已复制: ${text}`)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.transfer-rule-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fin-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.filter-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.search-form {
  margin-bottom: -18px;
}

.action-btn-group {
  display: flex;
  gap: 8px;
}

.mono-code {
  font-family: var(--fin-font-mono, 'JetBrains Mono', Consolas, monospace);
  font-weight: 600;
  color: #1d39c4;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.mono {
  font-family: var(--fin-font-mono, 'JetBrains Mono', Consolas, monospace);
  font-weight: 500;
}

.subject-name {
  color: #595959;
  font-size: 13px;
  margin-left: 4px;
}

.order-tag {
  font-weight: 600;
}

.pattern-tag {
  font-family: var(--fin-font-mono, 'JetBrains Mono', Consolas, monospace);
  font-weight: 600;
}

.direction-text {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #262626;

  .dir-icon {
    color: #1890ff;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.guide-card {
  background: #f0f5ff;
  border: 1px solid #d6e4ff;

  .guide-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    color: #1d39c4;
    font-size: 14px;
    margin-bottom: 10px;

    .guide-icon {
      font-size: 16px;
    }
  }

  .guide-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 13px;
    line-height: 1.6;
    color: #434343;

    .guide-title {
      font-weight: 600;
      color: #1f1f1f;
    }

    code {
      background: #e6f7ff;
      color: #096dd9;
      padding: 1px 6px;
      border-radius: 4px;
      font-family: var(--fin-font-mono, monospace);
    }
  }
}

.rule-form {
  .form-tip {
    font-size: 12px;
    color: #8c8c8c;
    margin-top: 4px;
    line-height: 1.5;
  }

  .switch-field-wrapper {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }

  .priority-field-inline {
    display: flex;
    align-items: center;
    gap: 12px;

    .inline-tip {
      margin-top: 0;
      white-space: nowrap;
    }
  }

  .shortcut-pills {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
    flex-wrap: wrap;

    .label {
      font-size: 12px;
      color: #595959;
    }

    .pill {
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: #1890ff;
        color: #ffffff;
      }
    }
  }
}
</style>
