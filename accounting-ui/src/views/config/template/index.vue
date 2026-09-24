<template>
  <div class="template-management-page">
    <!-- 顶部检索与操作卡片 -->
    <div class="fin-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="模板名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="支持模板名称搜索"
            clearable
            style="width: 200px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="业务线">
          <el-select
            v-model="searchForm.businessCode"
            placeholder="全部"
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
        <el-form-item label="客户类型">
          <el-select
            v-model="searchForm.customerType"
            placeholder="全部"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in CUSTOMER_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in TEMPLATE_STATUS_OPTIONS"
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
          新建开户模板
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 开户模板列表卡片（支持展开查看多个会计科目账户） -->
    <div class="fin-card">
      <BaseTable
        :data="filteredGroupList"
        :loading="loading"
        :show-pagination="false"
        empty-text="暂无开户模板数据"
        row-key="id"
      >
        <!-- 展开行：展示当前模板下配置的所有会计科目账户 -->
        <el-table-column type="expand" width="45">
          <template #default="{ row }">
            <div class="sub-accounts-box">
              <div class="sub-accounts-header">
                <span class="sub-title">【{{ row.templateName }}】关联开户科目明细（共 {{ row.items.length }} 个账户）</span>
              </div>
              <el-table :data="row.items" border size="small" class="nested-table">
                <el-table-column type="index" label="序号" width="55" align="center" />
                <el-table-column prop="subjectCode" label="会计科目" min-width="190">
                  <template #default="subScope">
                    <span class="code-tag highlight">{{ subScope.row.subjectCode }}</span>
                    <span class="subject-name-text">{{ getSubjectName(subScope.row.subjectCode) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="accountType" label="账户类型" min-width="140">
                  <template #default="subScope">
                    <span class="code-tag">{{ subScope.row.accountType }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="currency" label="币种" width="90" align="center" />
                <el-table-column prop="balanceDirection" label="余额方向" width="90" align="center">
                  <template #default="subScope">
                    <el-tag :type="subScope.row.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
                      {{ subScope.row.balanceDirection === 1 ? '借方' : '贷方' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="acctNoRule" label="账号生成规则" min-width="190" show-overflow-tooltip>
                  <template #default="subScope">
                    <span class="rule-mono">{{ subScope.row.acctNoRule }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="acctNameRule" label="户名生成规则" min-width="190" show-overflow-tooltip>
                  <template #default="subScope">
                    <span class="rule-mono">{{ subScope.row.acctNameRule }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="templateName" label="模板名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="template-name-cell">{{ row.templateName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="businessCode" label="业务线" min-width="150">
          <template #default="{ row }">
            <span class="code-tag">{{ getBusinessLabel(row.businessCode) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerType" label="客户类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getCustomerTypeMeta(row.customerType)?.tagType || 'info'" size="small">
              {{ getCustomerTypeMeta(row.customerType)?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置科目账户数" width="130" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small" effect="plain">
              {{ row.items.length }} 个账户科目
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="autoOpen" label="自动开户" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.autoOpen ? 'success' : 'info'" size="small">
              {{ row.autoOpen ? '支持' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusMeta(row.status)?.tagType || 'info'" size="small">
              {{ getStatusMeta(row.status)?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :icon="View"
              @click="openDetailDialog(row)"
            >
              详情
            </el-button>
            <el-button
              type="primary"
              link
              size="small"
              :icon="Edit"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 1 || row.status === 3"
              type="success"
              link
              size="small"
              :icon="Check"
              @click="handleEnableGroup(row)"
            >
              启用
            </el-button>
            <ActionButton
              v-else-if="row.status === 2"
              type="danger"
              link
              size="small"
              danger
              :icon="CircleClose"
              confirm-type="danger"
              confirm-title="停用模板确认"
              :confirm-message="`确定要停用开户模板【${row.templateName}】吗？停用后将无法基于此模板自动开户。`"
              :on-click="() => handleDisableGroup(row)"
            >
              停用
            </ActionButton>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- 新建 / 编辑开户模板弹窗 -->
    <BaseDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑开户模板' : '新增开户模板'"
      width="1060px"
      :confirm-loading="dialogSubmitLoading"
      @confirm="submitForm"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="100px"
        label-position="right"
      >
        <!-- 模板主信息卡片 -->
        <div class="dialog-section-card base-info-card">
          <div class="section-title">模板基础信息</div>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="模板名称" prop="templateName">
                <el-input
                  v-model="formModel.templateName"
                  placeholder="如: 芒好贷自营24贷款账户"
                  maxlength="32"
                  show-word-limit
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="业务线编码" prop="businessCode">
                <el-select
                  v-model="formModel.businessCode"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="从字典选择或输入业务线"
                  :disabled="isEdit"
                  style="width: 100%;"
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
            <el-col :span="8">
              <el-form-item label="客户类型" prop="customerType">
                <el-select
                  v-model="formModel.customerType"
                  placeholder="请选择客户类型"
                  :disabled="isEdit"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="item in CUSTOMER_TYPE_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="模板状态" prop="status">
                <el-radio-group v-model="formModel.status" class="status-radio-group">
                  <el-radio :value="1">待启用</el-radio>
                  <el-radio :value="2">启用</el-radio>
                  <el-radio v-if="isEdit" :value="3">停用</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="自动开户" prop="autoOpen">
                <el-switch
                  v-model="formModel.autoOpen"
                  size="small"
                  active-text="支持自动开户"
                  inactive-text="关闭"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 模板内多个会计科目账户配置明细列表 -->
        <div class="dialog-section-card">
          <div class="section-title-row">
            <span class="section-title">开户科目配置明细</span>
            <el-button type="primary" size="small" :icon="Plus" @click="addItemRow">
              新增明细项
            </el-button>
          </div>

          <!-- 规则变量说明提示条（两行展示） -->
          <div class="rule-hint-box">
            <div class="rule-hint-line">
              <span class="hint-title">
                <el-icon><InfoFilled /></el-icon> 账号规则可用:
              </span>
              <span class="variable-tag"><code>{accountType}</code> 账户类型</span>
              <span class="variable-tag"><code>{currency}</code> 币种</span>
              <span class="variable-tag"><code>{balanceDirection}</code> 借贷方向(1/2)</span>
              <span class="variable-tag"><code>{ownerType}</code> 客户类型(1/2/99)</span>
              <span class="variable-tag"><code>{subjectCode}</code> 科目编码</span>
              <span class="variable-tag"><code>{yyyyMMdd}</code> 日期</span>
              <span class="variable-tag"><code>{seq}</code> / <code>{seq1}</code>~<code>{seqN}</code> 自增序号(指定长度)</span>
            </div>
            <div class="rule-hint-line">
              <span class="hint-title">
                <el-icon><InfoFilled /></el-icon> 户名规则可用:
              </span>
              <span class="variable-tag"><code>{accountTypeName}</code> 账户类型名</span>
              <span class="variable-tag"><code>{currencyName}</code> 币种名</span>
              <span class="variable-tag"><code>{directionName}</code> 借贷方向名(借/贷)</span>
              <span class="variable-tag"><code>{ownerTypeName}</code> 客户类型名(个人/企业)</span>
              <span class="variable-tag"><code>{subjectName}</code> 科目名</span>
              <span class="variable-tag"><code>{ownerName}</code> 客户名</span>
              <span class="variable-tag"><code>{ownerId}</code> 客户号</span>
            </div>
          </div>

          <!-- 账户配置明细表 -->
          <el-table
            :data="formModel.items"
            border
            size="small"
            class="detail-edit-table"
            empty-text="请点击上方“新增明细项”添加要开户的会计科目账户"
          >
            <el-table-column type="index" label="序号" width="55" align="center" />

            <!-- 会计科目选择 -->
            <el-table-column label="会计科目" min-width="210">
              <template #default="{ row, $index }">
                <el-select
                  v-model="row.subjectCode"
                  filterable
                  clearable
                  placeholder="选择末级允许开户科目"
                  style="width: 100%;"
                  :disabled="isEdit && !!row.id"
                  @change="(val: string) => onSubjectChange(val, row)"
                >
                  <el-option
                    v-for="sub in leafSubjects"
                    :key="sub.subjectCode"
                    :label="`[${sub.subjectCode}] ${sub.subjectName}`"
                    :value="sub.subjectCode"
                    :disabled="isSubjectSelected(sub.subjectCode, $index)"
                  />
                </el-select>
              </template>
            </el-table-column>

            <!-- 账户类型（来自字典表） -->
            <el-table-column label="账户类型" min-width="160">
              <template #default="{ row }">
                <el-select
                  v-model="row.accountType"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="请选择账户类型"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="item in accountTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <!-- 币种（来自字典表） -->
            <el-table-column label="币种" width="120" align="center">
              <template #default="{ row }">
                <el-select
                  v-model="row.currency"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="币种"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="item in currencyOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <!-- 借贷方向 -->
            <el-table-column label="借贷方向" width="105" align="center">
              <template #default="{ row }">
                <el-select v-model="row.balanceDirection" style="width: 100%;">
                  <el-option :value="1" label="借方" />
                  <el-option :value="2" label="贷方" />
                </el-select>
              </template>
            </el-table-column>

            <!-- 账号规则 -->
            <el-table-column label="账号生成规则" min-width="190">
              <template #default="{ row }">
                <el-input
                  v-model="row.acctNoRule"
                  placeholder="如: {accountType}-{currency}-{seq5}"
                  maxlength="32"
                  clearable
                />
              </template>
            </el-table-column>

            <!-- 户名规则 -->
            <el-table-column label="户名生成规则" min-width="190">
              <template #default="{ row }">
                <el-input
                  v-model="row.acctNameRule"
                  placeholder="如: {ownerName}-{accountTypeName}"
                  maxlength="32"
                  clearable
                />
              </template>
            </el-table-column>

            <!-- 操作 -->
            <el-table-column label="操作" width="65" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  size="small"
                  :icon="Delete"
                  title="删除该明细"
                  @click="removeItemRow($index)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
    </BaseDialog>

    <!-- 查看模板详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="客户开户模板详情"
      width="960px"
      destroy-on-close
    >
      <div v-if="currentDetailGroup" class="detail-container">
        <!-- 主信息 -->
        <el-descriptions :column="3" border size="default">
          <el-descriptions-item label="模板名称">
            {{ currentDetailGroup.templateName }}
          </el-descriptions-item>
          <el-descriptions-item label="业务线">
            <span class="code-tag">{{ getBusinessLabel(currentDetailGroup.businessCode) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="客户类型">
            <el-tag :type="getCustomerTypeMeta(currentDetailGroup.customerType)?.tagType || 'info'" size="small">
              {{ getCustomerTypeMeta(currentDetailGroup.customerType)?.label || '未知' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="自动开户">
            <el-tag :type="currentDetailGroup.autoOpen ? 'success' : 'info'" size="small">
              {{ currentDetailGroup.autoOpen ? '支持自动开户' : '关闭' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模板状态">
            <el-tag :type="getStatusMeta(currentDetailGroup.status)?.tagType || 'info'" size="small">
              {{ getStatusMeta(currentDetailGroup.status)?.label || '未知' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="配置账户数">
            <el-tag type="success" size="small">
              共 {{ currentDetailGroup.items.length }} 个会计科目账户
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 开户账户科目明细表 -->
        <div class="detail-sub-section">
          <div class="sub-section-title">开户科目账户配置列表</div>
          <el-table :data="currentDetailGroup.items" border size="small">
            <el-table-column type="index" label="序号" width="55" align="center" />
            <el-table-column prop="subjectCode" label="会计科目" min-width="190">
              <template #default="{ row }">
                <span class="code-tag highlight">{{ row.subjectCode }}</span>
                <span class="subject-name-text">{{ getSubjectName(row.subjectCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="accountType" label="账户类型" min-width="140">
              <template #default="{ row }">
                <span class="code-tag">{{ row.accountType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="currency" label="币种" width="90" align="center" />
            <el-table-column prop="balanceDirection" label="余额方向" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
                  {{ row.balanceDirection === 1 ? '借方' : '贷方' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="acctNoRule" label="账号生成规则" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="rule-mono">{{ row.acctNoRule }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="acctNameRule" label="户名生成规则" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="rule-mono">{{ row.acctNameRule }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="() => {
            detailVisible = false
            if (currentDetailGroup) openEditDialog(currentDetailGroup)
          }"
        >
          前往编辑
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Search,
  RefreshRight,
  Plus,
  Refresh,
  Edit,
  View,
  Check,
  CircleClose,
  Delete,
  InfoFilled
} from '@element-plus/icons-vue'
import {
  getTemplatePage,
  saveTemplateGroup,
  disableTemplate,
  updateTemplate,
  CUSTOMER_TYPE_OPTIONS,
  TEMPLATE_STATUS_OPTIONS,
  type TemplateResponse,
  type TemplateGroupItem,
  type TemplateItemRequest
} from '@/api/template'
import { getDictByType } from '@/api/dict'
import { querySubjectPage, type SubjectItem } from '@/api/subject'
import type { SelectOption } from '@/api/types'
import { toast } from '@/utils/toast'
import BaseTable from '@/components/common/BaseTable/index.vue'
import BaseDialog from '@/components/common/BaseDialog/index.vue'
import ActionButton from '@/components/common/ActionButton/index.vue'

// 检索表单状态
const searchForm = reactive({
  keyword: '',
  businessCode: '',
  customerType: undefined as number | undefined,
  status: undefined as number | undefined
})

// 表格数据与状态
const loading = ref(false)
const rawDataList = ref<TemplateResponse[]>([])

// 详情查看状态
const detailVisible = ref(false)
const currentDetailGroup = ref<TemplateGroupItem | null>(null)

// 弹窗表单状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogSubmitLoading = ref(false)
const formRef = ref<FormInstance>()

// 末级可用科目列表缓存
const leafSubjects = ref<SubjectItem[]>([])

// 字典选项列表（业务线、账户类型、币种）
const businessCodeOptions = ref<SelectOption[]>([])
const accountTypeOptions = ref<SelectOption[]>([])
const currencyOptions = ref<SelectOption[]>([])

// 默认兜底字典备选项（当字典接口尚无数据时保证可用）
const DEFAULT_BUSINESS_CODES: SelectOption[] = [
  { label: '芒好贷自营24 (LOAN_24)', value: 'LOAN_24' },
  { label: '芒好贷-蚂蚁 (LOAN_ANT)', value: 'LOAN_ANT' },
  { label: '芒好贷自营36 (LOAN_36)', value: 'LOAN_36' },
  { label: '芒好贷-360智信 (LOAN_360)', value: 'LOAN_360' },
  { label: '购车宝 (CAR_LOAN)', value: 'CAR_LOAN' },
  { label: '支付结算 (PAYMENT)', value: 'PAYMENT' },
  { label: '存款业务 (DEPOSIT)', value: 'DEPOSIT' }
]

const DEFAULT_ACCOUNT_TYPES: SelectOption[] = [
  { label: '贷款本金', value: '贷款本金' },
  { label: '贷款逾期本金', value: '贷款逾期本金' },
  { label: '利息账户', value: '利息账户' },
  { label: '罚息账户', value: '罚息账户' },
  { label: '担保费账户', value: '担保费账户' },
  { label: '基本户', value: '基本户' },
  { label: '专用账户', value: '专用账户' },
  { label: '结算账户', value: '结算账户' },
  { label: '现金账户 (CASH)', value: 'CASH' },
  { label: '存款账户 (DEPOSIT)', value: 'DEPOSIT' }
]

const DEFAULT_CURRENCIES: SelectOption[] = [
  { label: '人民币 (CNY)', value: 'CNY' },
  { label: '美元 (USD)', value: 'USD' },
  { label: '欧元 (EUR)', value: 'EUR' },
  { label: '港币 (HKD)', value: 'HKD' }
]

// 编辑表单数据模型
const formModel = reactive({
  templateName: '',
  businessCode: '',
  customerType: 1 as number,
  autoOpen: true,
  status: 2 as number,
  items: [] as TemplateItemRequest[]
})

const formRules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  businessCode: [{ required: true, message: '请选择或输入业务线编码', trigger: 'change' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }]
}

// 辅助展示函数
function getCustomerTypeMeta(type?: number) {
  return CUSTOMER_TYPE_OPTIONS.find((item) => item.value === type)
}

function getStatusMeta(status?: number) {
  return TEMPLATE_STATUS_OPTIONS.find((item) => item.value === status)
}

function getSubjectName(subjectCode: string): string {
  const sub = leafSubjects.value.find((s) => s.subjectCode === subjectCode)
  return sub ? sub.subjectName : ''
}

function getBusinessLabel(businessCode: string): string {
  const target = businessCodeOptions.value.find((b) => b.value === businessCode)
  return target ? target.label : businessCode
}

function isSubjectSelected(subjectCode: string, currentIndex: number): boolean {
  return formModel.items.some((item, idx) => idx !== currentIndex && item.subjectCode === subjectCode)
}

// 从字典表加载业务线、账户类型和币种
async function loadDictionaries() {
  try {
    // 1. 业务线字典
    let bizList = await getDictByType('business_code')
    if (!bizList || bizList.length === 0) {
      bizList = await getDictByType('BUSINESS_CODE')
    }
    if (bizList && bizList.length > 0) {
      businessCodeOptions.value = bizList.map((item) => ({
        label: `${item.dictName} (${item.dictCode})`,
        value: item.dictCode
      }))
    } else {
      businessCodeOptions.value = [...DEFAULT_BUSINESS_CODES]
    }
  } catch {
    businessCodeOptions.value = [...DEFAULT_BUSINESS_CODES]
  }

  try {
    // 2. 账户类型字典
    let actList = await getDictByType('account_type')
    if (!actList || actList.length === 0) {
      actList = await getDictByType('ACCOUNT_TYPE')
    }
    if (actList && actList.length > 0) {
      accountTypeOptions.value = actList.map((item) => ({
        label: item.dictName,
        value: item.dictCode
      }))
    } else {
      accountTypeOptions.value = [...DEFAULT_ACCOUNT_TYPES]
    }
  } catch {
    accountTypeOptions.value = [...DEFAULT_ACCOUNT_TYPES]
  }

  try {
    // 3. 币种字典
    let currList = await getDictByType('currency')
    if (!currList || currList.length === 0) {
      currList = await getDictByType('CURRENCY')
    }
    if (currList && currList.length > 0) {
      currencyOptions.value = currList.map((item) => ({
        label: `${item.dictName} (${item.dictCode})`,
        value: item.dictCode
      }))
    } else {
      currencyOptions.value = [...DEFAULT_CURRENCIES]
    }
  } catch {
    currencyOptions.value = [...DEFAULT_CURRENCIES]
  }
}

// 将后端单条记录平铺数据聚合成以“模板”为维度的组结构
const groupList = computed<TemplateGroupItem[]>(() => {
  const map = new Map<string, TemplateGroupItem>()
  for (const item of rawDataList.value) {
    const key = `${item.businessCode}____${item.customerType}____${item.templateName}`
    if (!map.has(key)) {
      map.set(key, {
        id: item.id,
        templateName: item.templateName,
        businessCode: item.businessCode,
        customerType: item.customerType,
        autoOpen: item.autoOpen,
        status: item.status,
        items: []
      })
    }
    map.get(key)!.items.push(item)
  }
  return Array.from(map.values())
})

// 根据顶部搜索条件对组列表做前端过滤
const filteredGroupList = computed<TemplateGroupItem[]>(() => {
  return groupList.value.filter((grp) => {
    if (searchForm.keyword) {
      const kw = searchForm.keyword.trim().toLowerCase()
      const matchName = grp.templateName.toLowerCase().includes(kw)
      const matchSubject = grp.items.some(
        (i) => i.subjectCode.toLowerCase().includes(kw) || getSubjectName(i.subjectCode).toLowerCase().includes(kw)
      )
      if (!matchName && !matchSubject) return false
    }
    if (searchForm.businessCode) {
      if (grp.businessCode.toLowerCase() !== searchForm.businessCode.trim().toLowerCase()) {
        return false
      }
    }
    if (searchForm.customerType !== undefined && searchForm.customerType !== null) {
      if (grp.customerType !== searchForm.customerType) return false
    }
    if (searchForm.status !== undefined && searchForm.status !== null) {
      if (grp.status !== searchForm.status) return false
    }
    return true
  })
})

// 加载末级可用科目供下拉选择
async function loadLeafSubjects() {
  try {
    const res = await querySubjectPage({
      pageNo: 1,
      pageSize: 500,
      leaf: true,
      status: 1
    })
    const list = res.list || res.records || []
    leafSubjects.value = list.filter((s) => s.allowOpenAccount)
  } catch {
    leafSubjects.value = []
  }
}

// 加载开户模板全量数据
async function loadData() {
  loading.value = true
  try {
    const res = await getTemplatePage({
      pageNo: 1,
      pageSize: 1000
    })
    rawDataList.value = res.list || res.records || []
  } catch {
    rawDataList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // filteredGroupList 响应式自动过滤
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.businessCode = ''
  searchForm.customerType = undefined
  searchForm.status = undefined
}

// 查看模板详情弹窗
function openDetailDialog(row: TemplateGroupItem) {
  currentDetailGroup.value = row
  detailVisible.value = true
}

// 打开新建模板弹窗
function openCreateDialog() {
  isEdit.value = false
  formModel.templateName = ''
  formModel.businessCode = searchForm.businessCode || ''
  formModel.customerType = searchForm.customerType || 1
  formModel.autoOpen = true
  formModel.status = 2
  formModel.items = [
    {
      subjectCode: '',
      accountType: '贷款本金',
      currency: 'CNY',
      balanceDirection: 1,
      acctNoRule: '{accountType}-{currency}-{seq5}',
      acctNameRule: '{ownerName}-{accountTypeName}'
    }
  ]
  dialogVisible.value = true
}

// 打开编辑模板弹窗
function openEditDialog(row: TemplateGroupItem) {
  isEdit.value = true
  formModel.templateName = row.templateName
  formModel.businessCode = row.businessCode
  formModel.customerType = row.customerType
  formModel.autoOpen = row.autoOpen
  formModel.status = row.status
  formModel.items = row.items.map((item) => ({
    id: item.id,
    subjectCode: item.subjectCode,
    accountType: item.accountType,
    currency: item.currency || 'CNY',
    balanceDirection: item.balanceDirection,
    acctNoRule: item.acctNoRule,
    acctNameRule: item.acctNameRule
  }))
  dialogVisible.value = true
}

// 增减明细项
function addItemRow() {
  formModel.items.push({
    subjectCode: '',
    accountType: accountTypeOptions.value.length > 0 ? (accountTypeOptions.value[0].value as string) : 'CASH',
    currency: 'CNY',
    balanceDirection: 1,
    acctNoRule: '{accountType}-{currency}-{seq5}',
    acctNameRule: '{ownerName}-{accountTypeName}'
  })
}

function removeItemRow(index: number) {
  if (formModel.items.length <= 1) {
    toast.warning('开户模板必须至少保留一个会计科目账户明细！')
    return
  }
  formModel.items.splice(index, 1)
}

function onSubjectChange(val: string, row: TemplateItemRequest) {
  const sub = leafSubjects.value.find((s) => s.subjectCode === val)
  if (sub) {
    if (!row.accountType) {
      row.accountType = sub.subjectName
    }
    if (sub.debitCredit) {
      row.balanceDirection = sub.debitCredit
    }
  }
}

// 启用整个模板
async function handleEnableGroup(row: TemplateGroupItem) {
  try {
    for (const item of row.items) {
      await updateTemplate(item.id, { status: 2 })
    }
    toast.success(`模板【${row.templateName}】已成功启用！`)
    loadData()
  } catch {
    // 拦截器统一处理
  }
}

// 停用整个模板
async function handleDisableGroup(row: TemplateGroupItem) {
  try {
    for (const item of row.items) {
      await disableTemplate(item.id)
    }
    toast.success(`模板【${row.templateName}】已停用！`)
    loadData()
  } catch {
    // 拦截器统一处理
  }
}

// 提交表单保存整个模板及其所有科目账户明细
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (!formModel.items || formModel.items.length === 0) {
      toast.warning('请至少添加一个要开户的会计科目账户！')
      return
    }

    const subjectSet = new Set<string>()
    for (let i = 0; i < formModel.items.length; i++) {
      const item = formModel.items[i]
      if (!item.subjectCode) {
        toast.warning(`第 ${i + 1} 行未选择会计科目！`)
        return
      }
      if (subjectSet.has(item.subjectCode)) {
        toast.warning(`第 ${i + 1} 行科目编码【${item.subjectCode}】重复，一个模板中科目不可重复！`)
        return
      }
      subjectSet.add(item.subjectCode)

      if (!item.accountType) {
        toast.warning(`第 ${i + 1} 行未选择账户类型！`)
        return
      }
      if (!item.currency) {
        toast.warning(`第 ${i + 1} 行未选择币种！`)
        return
      }
      if (!item.acctNoRule) {
        toast.warning(`第 ${i + 1} 行未填写账号生成规则！`)
        return
      }
      if (!item.acctNameRule) {
        toast.warning(`第 ${i + 1} 行未填写户名生成规则！`)
        return
      }
    }

    dialogSubmitLoading.value = true
    try {
      await saveTemplateGroup({
        templateName: formModel.templateName,
        businessCode: formModel.businessCode,
        customerType: formModel.customerType,
        autoOpen: formModel.autoOpen,
        status: formModel.status,
        items: formModel.items.map((i) => ({
          id: i.id,
          subjectCode: i.subjectCode,
          accountType: i.accountType,
          currency: i.currency || 'CNY',
          balanceDirection: i.balanceDirection,
          acctNoRule: i.acctNoRule,
          acctNameRule: i.acctNameRule
        }))
      })

      toast.success(isEdit.value ? '开户模板更新成功！' : '开户模板创建成功！')
      dialogVisible.value = false
      loadData()
    } finally {
      dialogSubmitLoading.value = false
    }
  })
}

// 页面初始化
onMounted(() => {
  loadData()
  loadLeafSubjects()
  loadDictionaries()
})
</script>

<style scoped>
.template-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-form {
  margin-bottom: 8px;
}

.action-bar {
  display: flex;
  gap: 12px;
  padding-top: 4px;
}

.template-name-cell {
  font-weight: 500;
  color: #303133;
}

.code-tag {
  font-family: var(--fin-font-mono, monospace);
  font-size: 13px;
  color: #606266;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;

  &.highlight {
    color: #409eff;
    background: #ecf5ff;
    font-weight: 500;
  }
}

.subject-name-text {
  margin-left: 8px;
  color: #606266;
  font-size: 13px;
}

.rule-mono {
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  color: #303133;
}

.sub-accounts-box {
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
  margin: 4px 0;
}

.sub-accounts-header {
  margin-bottom: 8px;
  display: flex;
  align-items: center;

  .sub-title {
    font-size: 13px;
    font-weight: 600;
    color: #409eff;
  }
}

.nested-table {
  background: #fff;
}

.dialog-section-card {
  background: #fdfdfd;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 18px;
  margin-bottom: 12px;
}

.base-info-card {
  .el-form-item {
    margin-bottom: 14px;
  }

  .el-row:last-child .el-form-item {
    margin-bottom: 0;
  }

  .status-radio-group {
    display: flex;
    align-items: center;

    .el-radio {
      margin-right: 12px;

      &:last-child {
        margin-right: 0;
      }
    }
  }
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.rule-hint-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #f4f7fa;
  border: 1px solid #e2e7ee;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12px;

  .rule-hint-line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .hint-title {
    font-weight: 600;
    color: #409eff;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    min-width: 105px;
  }

  .variable-tag {
    color: #606266;

    code {
      font-family: var(--fin-font-mono, monospace);
      background: #e9eef3;
      padding: 1px 4px;
      border-radius: 3px;
      color: #303133;
    }
  }
}

.detail-edit-table {
  width: 100%;
}

.detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-sub-section {
  .sub-section-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 10px;
  }
}
</style>
