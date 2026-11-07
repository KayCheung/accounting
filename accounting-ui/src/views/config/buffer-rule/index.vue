<template>
  <div class="buffer-rule-page">
    <!-- 顶部检索与操作卡片 -->
    <div class="fin-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="业务线">
          <el-select
            v-model="searchForm.businessCode"
            placeholder="全部业务线"
            clearable
            filterable
            style="width: 180px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in businessCodeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="缓冲模式">
          <el-select
            v-model="searchForm.bufferMode"
            placeholder="全部模式"
            clearable
            style="width: 160px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in BUFFER_MODE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="规则状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in RULE_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
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

      <div class="action-bar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新建缓冲规则
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 缓冲规则数据表格卡片 -->
    <div class="fin-card">
      <BaseTable
        :data="ruleList"
        :loading="loading"
        :show-pagination="true"
        :total="total"
        :current-page="searchForm.pageNo"
        :page-size="searchForm.pageSize"
        empty-text="暂无缓冲入账规则数据"
        row-key="id"
        @page-change="handlePageChange"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />

        <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="rule-name-cell">{{ row.ruleName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="bufferMode" label="缓冲入账模式" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip :content="getBufferModeDesc(row.bufferMode)" placement="top">
              <el-tag :type="getBufferModeTag(row.bufferMode)" size="small" effect="light">
                {{ getBufferModeLabel(row.bufferMode) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="businessCode" label="业务线" min-width="120">
          <template #default="{ row }">
            <span class="code-tag">{{ getBusinessLabel(row.businessCode) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="tradingCode" label="交易编码" min-width="130">
          <template #default="{ row }">
            <span class="code-tag">{{ getTradingLabel(row.tradingCode) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="payChannel" label="支付渠道" width="110" align="center">
          <template #default="{ row }">
            <span class="code-tag">{{ getPayChannelLabel(row.payChannel) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="作用对象 (科目 / 账户)" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.subjectCode" class="target-row">
              <span class="target-label">科目:</span>
              <span class="code-tag highlight">{{ row.subjectCode }}</span>
              <span class="subject-name-text">{{ getSubjectName(row.subjectCode) }}</span>
            </div>
            <div v-if="row.accountNo" class="target-row" :class="{ 'mt-1': row.subjectCode }">
              <span class="target-label">账户:</span>
              <span class="account-no-mono">{{ row.accountNo }}</span>
              <el-button
                link
                type="primary"
                size="small"
                :icon="CopyDocument"
                class="copy-btn"
                @click="copyText(row.accountNo)"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="debitCredit" label="借贷方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.debitCredit === 1 ? 'primary' : 'warning'" size="small" effect="dark">
              {{ row.debitCredit === 1 ? '借方' : '贷方' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="生效时间区间" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="time-range-text">
              {{ formatTime(row.effectiveTime) }} ~ {{ formatTime(row.expirationTime) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusMeta(row.status).tagType" size="small">
              {{ getStatusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 操作列：统一使用 MoreFilled "..." 紧凑下拉菜单 (70px) -->
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click">
              <el-button link type="primary" :icon="MoreFilled" class="more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="View" @click="openDetailDrawer(row)">
                    详情档案
                  </el-dropdown-item>
                  <el-dropdown-item :icon="Edit" @click="openEditDialog(row)">
                    编辑规则
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 1 || row.status === 3"
                    :icon="VideoPlay"
                    @click="handleEnable(row)"
                  >
                    启用规则
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 2"
                    :icon="VideoPause"
                    @click="handleDisable(row)"
                  >
                    停用规则
                  </el-dropdown-item>
                  <el-dropdown-item :icon="DocumentCopy" divided @click="handleCopyRule(row)">
                    复制新建
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- 创建 / 编辑缓冲入账规则弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="780px"
      destroy-on-close
      :close-on-click-modal="false"
      class="buffer-rule-dialog"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="110px"
        size="default"
      >
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input
                v-model="formModel.ruleName"
                placeholder="请输入规则名称，如：放款现金逐条缓冲"
                maxlength="32"
                show-word-limit
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="缓冲入账模式" prop="bufferMode">
              <el-select
                v-model="formModel.bufferMode"
                placeholder="选择缓冲模式"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in BUFFER_MODE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                  <div class="mode-option-item">
                    <span>{{ item.label }}</span>
                    <span class="mode-desc">{{ item.desc }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="业务线" prop="businessCode">
              <el-select
                v-model="formModel.businessCode"
                placeholder="选择所属业务线"
                filterable
                style="width: 100%;"
                :disabled="isEditMode"
              >
                <el-option
                  v-for="item in businessCodeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="交易编码" prop="tradingCode">
              <el-select
                v-model="formModel.tradingCode"
                placeholder="选择交易编码"
                filterable
                allow-create
                default-first-option
                style="width: 100%;"
                :disabled="isEditMode"
              >
                <el-option
                  v-for="item in tradingCodeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="支付渠道" prop="payChannel">
              <el-select
                v-model="formModel.payChannel"
                placeholder="选择支付渠道"
                filterable
                allow-create
                default-first-option
                style="width: 100%;"
                :disabled="isEditMode"
              >
                <el-option
                  v-for="item in payChannelOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="借贷方向" prop="debitCredit">
              <el-radio-group v-model="formModel.debitCredit">
                <el-radio-button :value="1">借方 (Debit)</el-radio-button>
                <el-radio-button :value="2">贷方 (Credit)</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="target-alert-box mb-3">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="作用对象匹配原则：会计科目与账户编号至少填其一。优先精确匹配账户级规则，未匹配到时降级匹配科目级规则。"
          />
        </div>

        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="会计科目 (末级)">
              <el-select
                v-model="formModel.subjectCode"
                placeholder="选择末级会计科目（选填）"
                filterable
                clearable
                style="width: 100%;"
              >
                <el-option
                  v-for="item in subjectOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="账户编号">
              <el-input
                v-model="formModel.accountNo"
                placeholder="输入特定账户编号（选填）"
                clearable
                maxlength="32"
              />
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="生效时间区间" required>
              <el-date-picker
                v-model="formTimeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="生效起始时间"
                end-placeholder="失效截止时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存缓冲规则
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 规则详情抽屉（全景档案） -->
    <el-drawer
      v-model="drawerVisible"
      title="缓冲入账规则全景档案"
      size="620px"
      destroy-on-close
    >
      <div v-if="detailRecord" class="rule-detail-container">
        <el-descriptions title="规则核心属性" :column="2" border size="small">
          <el-descriptions-item label="规则ID">{{ detailRecord.id }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ detailRecord.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="缓冲入账模式">
            <el-tag :type="getBufferModeTag(detailRecord.bufferMode)" size="small">
              {{ getBufferModeLabel(detailRecord.bufferMode) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusMeta(detailRecord.status).tagType" size="small">
              {{ getStatusMeta(detailRecord.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="业务线">
            <span class="code-tag">{{ getBusinessLabel(detailRecord.businessCode) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交易编码">
            <span class="code-tag">{{ getTradingLabel(detailRecord.tradingCode) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="支付渠道">
            <span class="code-tag">{{ getPayChannelLabel(detailRecord.payChannel) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="借贷方向">
            <el-tag :type="detailRecord.debitCredit === 1 ? 'primary' : 'warning'" size="small">
              {{ detailRecord.debitCredit === 1 ? '借方' : '贷方' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="会计科目" :span="2">
            <template v-if="detailRecord.subjectCode">
              <span class="code-tag highlight">{{ detailRecord.subjectCode }}</span>
              <span class="subject-name-text">{{ getSubjectName(detailRecord.subjectCode) }}</span>
            </template>
            <span v-else class="text-placeholder">未限定会计科目</span>
          </el-descriptions-item>
          <el-descriptions-item label="账户编号" :span="2">
            <template v-if="detailRecord.accountNo">
              <span class="account-no-mono">{{ detailRecord.accountNo }}</span>
              <el-button
                link
                type="primary"
                size="small"
                :icon="CopyDocument"
                class="copy-btn ml-1"
                @click="copyText(detailRecord.accountNo)"
              />
            </template>
            <span v-else class="text-placeholder">未限定特定账户</span>
          </el-descriptions-item>
          <el-descriptions-item label="生效时间" :span="2">
            {{ formatTime(detailRecord.effectiveTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="失效时间" :span="2">
            {{ formatTime(detailRecord.expirationTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="mt-4">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="`【${getBufferModeLabel(detailRecord.bufferMode)}】说明：${getBufferModeDesc(detailRecord.bufferMode)}`"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Search,
  RefreshRight,
  Plus,
  Refresh,
  Edit,
  View,
  MoreFilled,
  VideoPlay,
  VideoPause,
  DocumentCopy,
  CopyDocument
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getBufferRulePage,
  getBufferRuleById,
  createBufferRule,
  updateBufferRule,
  enableBufferRule,
  disableBufferRule,
  BUFFER_MODE_OPTIONS,
  RULE_STATUS_OPTIONS,
  type BufferRuleResponse,
  type BufferRuleQueryRequest
} from '@/api/buffer-rule'
import { getDictByType } from '@/api/dict'
import { querySubjectPage } from '@/api/subject'

interface OptionItem {
  label: string
  value: string
  name?: string
}

// ===== 字典与下拉选项 =====
const businessCodeOptions = ref<OptionItem[]>([])
const tradingCodeOptions = ref<OptionItem[]>([])
const payChannelOptions = ref<OptionItem[]>([])
const subjectOptions = ref<OptionItem[]>([])

const subjectMap = ref<Record<string, string>>({})
const businessMap = ref<Record<string, string>>({})
const tradingMap = ref<Record<string, string>>({})
const payChannelMap = ref<Record<string, string>>({})

// ===== 列表检索状态 =====
const loading = ref(false)
const total = ref(0)
const ruleList = ref<BufferRuleResponse[]>([])
const searchForm = ref<BufferRuleQueryRequest>({
  pageNo: 1,
  pageSize: 10,
  businessCode: '',
  bufferMode: undefined,
  status: undefined
})

// ===== 新建/编辑弹窗状态 =====
const dialogVisible = ref(false)
const isEditMode = ref(false)
const currentEditId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

interface BufferRuleFormState {
  ruleName: string
  bufferMode: number
  businessCode: string
  tradingCode: string
  payChannel: string
  subjectCode: string
  accountNo: string
  debitCredit: number
}

const formModel = ref<BufferRuleFormState>({
  ruleName: '',
  bufferMode: 1,
  businessCode: '',
  tradingCode: '',
  payChannel: '',
  subjectCode: '',
  accountNo: '',
  debitCredit: 1
})

const formTimeRange = ref<[string, string]>(['2026-01-01 00:00:00', '2099-12-31 23:59:59'])

const formRules: FormRules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' },
    { max: 32, message: '规则名称最多32个字符', trigger: 'blur' }
  ],
  bufferMode: [{ required: true, message: '请选择缓冲入账模式', trigger: 'change' }],
  businessCode: [{ required: true, message: '请选择业务线', trigger: 'change' }],
  tradingCode: [{ required: true, message: '请选择或输入交易编码', trigger: 'change' }],
  payChannel: [{ required: true, message: '请选择或输入支付渠道', trigger: 'change' }],
  debitCredit: [{ required: true, message: '请选择借贷方向', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  return isEditMode.value ? '编辑缓冲入账规则' : '新建缓冲入账规则'
})

// ===== 详情抽屉状态 =====
const drawerVisible = ref(false)
const detailRecord = ref<BufferRuleResponse | null>(null)

// ===== 辅助方法 =====
const getBufferModeLabel = (mode: number) => {
  return BUFFER_MODE_OPTIONS.find(m => m.value === mode)?.label || `模式${mode}`
}

const getBufferModeTag = (mode: number): 'primary' | 'warning' | 'danger' | 'info' => {
  const found = BUFFER_MODE_OPTIONS.find(m => m.value === mode)
  return (found?.tagType as any) || 'info'
}

const getBufferModeDesc = (mode: number) => {
  return BUFFER_MODE_OPTIONS.find(m => m.value === mode)?.desc || ''
}

const getStatusMeta = (status: number) => {
  return RULE_STATUS_OPTIONS.find(s => s.value === status) || { label: '未知', tagType: 'info' }
}

const getBusinessLabel = (code: string) => {
  return businessMap.value[code] || code
}

const getTradingLabel = (code: string) => {
  return tradingMap.value[code] || code
}

const getPayChannelLabel = (code: string) => {
  return payChannelMap.value[code] || code
}

const getSubjectName = (code: string) => {
  return subjectMap.value[code] || ''
}

const formatTime = (timeStr?: string) => {
  if (!timeStr) return '--'
  return timeStr.replace('T', ' ').slice(0, 19)
}

const copyText = (text?: string) => {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 加载字典与科目数据
const loadDictsAndSubjects = async () => {
  try {
    const [bizRes, tradeRes, payRes, subRes] = await Promise.all([
      getDictByType('business_code'),
      getDictByType('trading_code'),
      getDictByType('pay_channel'),
      querySubjectPage({ pageSize: 500, status: 1 })
    ])

    businessCodeOptions.value = (bizRes || []).map(d => {
      businessMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    tradingCodeOptions.value = (tradeRes || []).map(d => {
      tradingMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    payChannelOptions.value = (payRes || []).map(d => {
      payChannelMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    subjectOptions.value = (subRes.list || []).filter(s => s.leaf).map(s => {
      subjectMap.value[s.subjectCode] = s.subjectName
      return {
        label: `[${s.subjectCode}] ${s.subjectName}`,
        value: s.subjectCode,
        name: s.subjectName
      }
    })
  } catch (err) {
    console.error('加载系统字典与科目失败', err)
  }
}

// 查询列表数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getBufferRulePage(searchForm.value)
    ruleList.value = res.list || []
    total.value = res.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '加载缓冲规则列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.value.pageNo = 1
  loadData()
}

const handleReset = () => {
  searchForm.value = {
    pageNo: 1,
    pageSize: 10,
    businessCode: '',
    bufferMode: undefined,
    status: undefined
  }
  loadData()
}

const handlePageChange = (page: number, size: number) => {
  searchForm.value.pageNo = page
  searchForm.value.pageSize = size
  loadData()
}

// 打开创建弹窗
const openCreateDialog = () => {
  isEditMode.value = false
  currentEditId.value = null
  formModel.value = {
    ruleName: '',
    bufferMode: 1,
    businessCode: businessCodeOptions.value[0]?.value || 'PAYMENT',
    tradingCode: 'CASH_PAY',
    payChannel: 'CASH',
    subjectCode: '',
    accountNo: '',
    debitCredit: 1
  }
  formTimeRange.value = ['2026-01-01 00:00:00', '2099-12-31 23:59:59']
  dialogVisible.value = true
}

// 打开编辑弹窗
const openEditDialog = async (row: BufferRuleResponse) => {
  if (row.status === 2) {
    ElMessageBox.confirm(
      '已启用的缓冲规则正在参与生产交易分流，直接修改可能导致正在执行的交易无法准确路由到缓冲队列。需先停用规则后方可编辑，是否立即停用该规则？',
      '提示',
      {
        confirmButtonText: '停用并编辑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      try {
        await disableBufferRule(row.id)
        ElMessage.success('缓冲规则已停用，现在可以进行编辑')
        await loadData()
        const refreshed = await getBufferRuleById(row.id)
        doOpenEdit(refreshed)
      } catch (err: any) {
        ElMessage.error(err?.message || '停用缓冲规则失败')
      }
    }).catch(() => {})
    return
  }

  try {
    const detail = await getBufferRuleById(row.id)
    doOpenEdit(detail)
  } catch (err: any) {
    ElMessage.error(err?.message || '获取缓冲规则详情失败')
  }
}

const doOpenEdit = (detail: BufferRuleResponse) => {
  isEditMode.value = true
  currentEditId.value = detail.id
  formModel.value = {
    ruleName: detail.ruleName,
    bufferMode: detail.bufferMode,
    businessCode: detail.businessCode,
    tradingCode: detail.tradingCode,
    payChannel: detail.payChannel,
    subjectCode: detail.subjectCode || '',
    accountNo: detail.accountNo || '',
    debitCredit: detail.debitCredit
  }
  formTimeRange.value = [
    formatTime(detail.effectiveTime),
    formatTime(detail.expirationTime)
  ]
  dialogVisible.value = true
}

// 复制规则快速新建
const handleCopyRule = async (row: BufferRuleResponse) => {
  try {
    const detail = await getBufferRuleById(row.id)
    isEditMode.value = false
    currentEditId.value = null
    formModel.value = {
      ruleName: `${detail.ruleName}-副本`,
      bufferMode: detail.bufferMode,
      businessCode: detail.businessCode,
      tradingCode: detail.tradingCode,
      payChannel: detail.payChannel,
      subjectCode: detail.subjectCode || '',
      accountNo: detail.accountNo || '',
      debitCredit: detail.debitCredit
    }
    formTimeRange.value = [
      formatTime(detail.effectiveTime),
      formatTime(detail.expirationTime)
    ]
    dialogVisible.value = true
    ElMessage.info('已复制缓冲规则配置，请确认时间区间与科目后保存')
  } catch (err: any) {
    ElMessage.error(err?.message || '复制缓冲规则失败')
  }
}

// 保存表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  if (!formModel.value.subjectCode && !formModel.value.accountNo) {
    ElMessage.error('会计科目与账户编号必须至少指定其一！')
    return
  }

  if (!formTimeRange.value || formTimeRange.value.length < 2) {
    ElMessage.error('请选择生效时间区间')
    return
  }

  const effectiveTime = formTimeRange.value[0]
  const expirationTime = formTimeRange.value[1]

  submitting.value = true
  try {
    if (isEditMode.value && currentEditId.value) {
      await updateBufferRule(currentEditId.value, {
        ruleName: formModel.value.ruleName,
        bufferMode: formModel.value.bufferMode,
        subjectCode: formModel.value.subjectCode || '',
        accountNo: formModel.value.accountNo || '',
        debitCredit: formModel.value.debitCredit,
        effectiveTime,
        expirationTime
      })
      ElMessage.success('缓冲入账规则更新成功')
    } else {
      await createBufferRule({
        ruleName: formModel.value.ruleName,
        bufferMode: formModel.value.bufferMode,
        businessCode: formModel.value.businessCode,
        tradingCode: formModel.value.tradingCode,
        payChannel: formModel.value.payChannel,
        subjectCode: formModel.value.subjectCode || '',
        accountNo: formModel.value.accountNo || '',
        debitCredit: formModel.value.debitCredit,
        effectiveTime,
        expirationTime
      })
      ElMessage.success('缓冲入账规则创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存缓冲规则失败')
  } finally {
    submitting.value = false
  }
}

// 启用规则
const handleEnable = async (row: BufferRuleResponse) => {
  try {
    await enableBufferRule(row.id)
    ElMessage.success(`缓冲规则【${row.ruleName}】已成功启用`)
    loadData()
  } catch (err: any) {
    ElMessage.error(err?.message || '启用缓冲规则失败')
  }
}

// 停用规则
const handleDisable = (row: BufferRuleResponse) => {
  ElMessageBox.confirm(
    `确定要停用缓冲入账规则【${row.ruleName}】吗？停用后新的交易将不再路由进入缓冲处理，已在缓冲队列中的流水不受影响。`,
    '停用确认',
    {
      confirmButtonText: '确定停用',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await disableBufferRule(row.id)
      ElMessage.success(`缓冲规则【${row.ruleName}】已成功停用`)
      loadData()
    } catch (err: any) {
      ElMessage.error(err?.message || '停用缓冲规则失败')
    }
  }).catch(() => {})
}

// 查看详情抽屉
const openDetailDrawer = async (row: BufferRuleResponse) => {
  try {
    detailRecord.value = await getBufferRuleById(row.id)
    drawerVisible.value = true
  } catch (err: any) {
    ElMessage.error(err?.message || '获取规则详情失败')
  }
}

onMounted(async () => {
  await loadDictsAndSubjects()
  loadData()
})
</script>

<style scoped>
.buffer-rule-page {
  padding: 16px;
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

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.code-tag {
  font-family: var(--fin-font-mono, monospace);
  background-color: #f4f4f5;
  color: #606266;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.code-tag.highlight {
  background-color: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.rule-name-cell {
  font-weight: 500;
  color: #303133;
}

.more-btn {
  padding: 4px 8px;
  font-size: 14px;
}

.target-row {
  display: flex;
  align-items: center;
  font-size: 12px;
}

.target-label {
  color: #909399;
  margin-right: 4px;
  font-size: 11px;
}

.subject-name-text {
  margin-left: 6px;
  color: #303133;
  font-size: 12px;
}

.account-no-mono {
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  color: #303133;
}

.copy-btn {
  padding: 0 4px;
  font-size: 12px;
  color: #909399;
}

.copy-btn:hover {
  color: #409eff;
}

.time-range-text {
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  color: #606266;
}

.mode-option-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.mode-desc {
  font-size: 11px;
  color: #909399;
}

.target-alert-box {
  margin-bottom: 14px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.rule-detail-container {
  padding: 4px 12px;
}
</style>
