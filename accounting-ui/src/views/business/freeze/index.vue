<template>
  <div class="freeze-management-page">
    <!-- ==================== 顶部检索卡片 ==================== -->
    <div class="fin-card search-card">
      <el-form :inline="true" :model="searchForm" class="freeze-search-form">
        <el-form-item label="账户编号">
          <el-autocomplete
            v-model="searchForm.accountNo"
            :fetch-suggestions="queryAccountSuggestions"
            placeholder="输入或联想账号"
            clearable
            style="width: 200px;"
            @select="handleSelectAccount"
            @keyup.enter="handleSearch"
          >
            <template #default="{ item }">
              <div class="autocomplete-item">
                <span class="acct-no">{{ item.accountNo }}</span>
                <span class="acct-name">{{ item.accountName }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>

        <el-form-item label="冻结编号">
          <el-input
            v-model="searchForm.freezeId"
            placeholder="FRZ开头的单号"
            clearable
            style="width: 190px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="冻结状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in FREEZE_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="创建时间">
          <el-date-picker
            v-model="createDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 230px;"
            @change="handleDateRangeChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
          <el-button type="success" :icon="Plus" @click="openFreezeModal">
            申请资金冻结
          </el-button>
          <el-button :icon="Refresh" :loading="loading" @click="fetchData">
            刷新
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ==================== 数据表格卡片 ==================== -->
    <div class="fin-card table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        size="small"
        class="fin-table"
        empty-text="暂无资金冻结记录"
      >
        <el-table-column type="index" label="序号" width="50" align="center" />

        <el-table-column prop="freezeId" label="冻结单号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="code-tag highlight" @click="openDetailDrawer(row)">
              {{ row.freezeId }}
            </span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              class="cell-copy-btn"
              title="复制冻结单号"
              @click="copyText(row.freezeId, '冻结单号')"
            />
          </template>
        </el-table-column>

        <el-table-column prop="accountNo" label="关联账户编号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="account-link" @click="jumpToBalance(row.accountNo)">
              {{ row.accountNo }}
            </span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              class="cell-copy-btn"
              title="复制账号"
              @click="copyText(row.accountNo, '账户编号')"
            />
          </template>
        </el-table-column>

        <el-table-column prop="origFreezeAmount" label="初始冻结金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="freeze-amount-text">
              <AmountDisplay :value="row.origFreezeAmount ?? row.freezeAmount" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="freezeAmount" label="剩余冻结金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="freeze-amount-text">
              <AmountDisplay :value="row.freezeAmount" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="unfrozenAmount" label="已解冻金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="unfrozen-amount-text">
              <AmountDisplay :value="row.unfrozenAmount ?? 0" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="deductedAmount" label="已扣款金额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="deducted-amount-text">
              <AmountDisplay :value="row.deductedAmount ?? 0" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small">
              {{ row.status === 1 ? '冻结中' : '已解冻' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="expireTime" label="失效到期时间" width="160" align="center">
          <template #default="{ row }">
            <div class="expire-time-cell">
              <span>{{ formatDateTime(row.expireTime) }}</span>
              <el-tag
                v-if="isExpired(row.expireTime) && row.status === 1"
                type="warning"
                size="small"
                effect="dark"
                class="expired-badge"
              >
                已过期
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="冻结申请时间" width="160" align="center">
          <template #default="{ row }">
            <span>{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="summary" label="冻结原因 / 摘要" min-width="180" show-overflow-tooltip />

        <!-- 操作列：统一使用 MoreFilled "..." 紧凑下拉菜单 (70px) -->
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click">
              <el-button link type="primary" :icon="MoreFilled" class="action-more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="Tickets" @click="openDetailDrawer(row)">
                    详情档案
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 1"
                    :icon="Unlock"
                    @click="openUnfreezeModal(row)"
                  >
                    <span style="color: #67c23a;">资金解冻</span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 1"
                    :icon="CreditCard"
                    @click="openDeductModal(row)"
                  >
                    <span style="color: #f56c6c;">冻结扣款</span>
                  </el-dropdown-item>
                  <el-dropdown-item :icon="Wallet" divided @click="jumpToBalance(row.accountNo)">
                    查账户余额
                  </el-dropdown-item>
                  <el-dropdown-item :icon="CopyDocument" @click="copyText(row.freezeId, '冻结单号')">
                    复制单号
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页器 -->
      <div class="pagination-footer">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          size="small"
          @size-change="handleSearch"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- ==================== 弹窗 1: 申请资金冻结 ==================== -->
    <el-dialog
      v-model="freezeModalVisible"
      title="申请资金冻结"
      width="540px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="freezeFormRef"
        :model="freezeForm"
        :rules="freezeFormRules"
        label-width="100px"
        class="dialog-form"
      >
        <el-form-item label="账户编号" prop="accountNo">
          <el-autocomplete
            v-model="freezeForm.accountNo"
            :fetch-suggestions="queryAccountSuggestions"
            placeholder="输入或选择需冻结的账户编号"
            clearable
            style="width: 100%"
            @select="handleFreezeAccountSelected"
            @blur="fetchAccountAvailableBalance"
          >
            <template #default="{ item }">
              <div class="autocomplete-item">
                <span class="acct-no">{{ item.accountNo }}</span>
                <span class="acct-name">{{ item.accountName }}</span>
              </div>
            </template>
          </el-autocomplete>
          <!-- 账户可用余额即时看板提示 -->
          <div v-if="accountBalanceInfo" class="account-avail-box">
            <span class="avail-title">账户户名：</span>
            <span class="avail-name">{{ accountBalanceInfo.accountName || '-' }}</span>
            <span class="avail-divider">|</span>
            <span class="avail-title">可用余额：</span>
            <span class="avail-amount">
              <AmountDisplay :value="accountBalanceInfo.availableBalance" prefix="¥ " />
            </span>
          </div>
        </el-form-item>

        <el-form-item label="冻结金额" prop="freezeAmount">
          <el-input-number
            v-model="freezeForm.freezeAmount"
            :min="0.01"
            :precision="2"
            :step="100"
            placeholder="请输入冻结金额"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="失效时间" prop="expireTime">
          <el-date-picker
            v-model="freezeForm.expireTime"
            type="datetime"
            placeholder="选择过期时间（默认长期有效）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <div class="form-tip">留空则默认长期有效（2099-12-31）</div>
        </el-form-item>

        <el-form-item label="冻结原因" prop="reason">
          <el-input
            v-model="freezeForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入冻结原因，如：异常交易风险排查、履约保证金在途锁定、司法协同等"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="freezeModalVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitFundFreeze">
            确认冻结
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗 2: 资金解冻 ==================== -->
    <el-dialog
      v-model="unfreezeModalVisible"
      title="资金解冻办理"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-if="actionTarget" class="action-target-banner">
        <div class="target-row">
          <span class="label">冻结单号：</span>
          <span class="value mono">{{ actionTarget.freezeId }}</span>
        </div>
        <div class="target-row">
          <span class="label">关联账户：</span>
          <span class="value mono">{{ actionTarget.accountNo }}</span>
        </div>
        <div class="target-row">
          <span class="label">初始冻结：</span>
          <span class="value">
            <AmountDisplay :value="actionTarget.origFreezeAmount ?? actionTarget.freezeAmount" prefix="¥ " />
          </span>
        </div>
        <div class="target-row">
          <span class="label">当前剩余冻结：</span>
          <span class="value amount-highlight">
            <AmountDisplay :value="actionTarget.freezeAmount" prefix="¥ " />
          </span>
        </div>
      </div>

      <el-form
        ref="unfreezeFormRef"
        :model="unfreezeForm"
        :rules="unfreezeFormRules"
        label-width="100px"
        class="dialog-form"
      >
        <el-form-item label="解冻金额" prop="unfreezeAmount">
          <div class="amount-input-row">
            <el-input-number
              v-model="unfreezeForm.unfreezeAmount"
              :min="0.01"
              :max="actionTarget?.freezeAmount || 999999999"
              :precision="2"
              :step="100"
              placeholder="请输入解冻金额"
              style="width: 100%"
            />
            <el-button type="primary" link @click="fillFullUnfreeze">全额解冻</el-button>
          </div>
        </el-form-item>

        <el-form-item label="解冻说明" prop="reason">
          <el-input
            v-model="unfreezeForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入解冻说明或审批凭据号"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="unfreezeModalVisible = false">取消</el-button>
          <el-button type="success" :loading="submitting" @click="submitFundUnfreeze">
            确认解冻
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗 3: 冻结扣款 ==================== -->
    <el-dialog
      v-model="deductModalVisible"
      title="冻结额度扣款办理"
      width="540px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        title="重要警告：冻结扣款将直接扣减该账户的冻结子账户与主账户总余额，并自动生成正式财务记账凭证与会计分录，该操作不可逆，请核对执行公函或审批单号！"
        type="error"
        :closable="false"
        show-icon
        class="deduct-alert"
      />

      <div v-if="actionTarget" class="action-target-banner mt-3">
        <div class="target-row">
          <span class="label">冻结单号：</span>
          <span class="value mono">{{ actionTarget.freezeId }}</span>
        </div>
        <div class="target-row">
          <span class="label">扣款账户：</span>
          <span class="value mono">{{ actionTarget.accountNo }}</span>
        </div>
        <div class="target-row">
          <span class="label">初始冻结：</span>
          <span class="value">
            <AmountDisplay :value="actionTarget.origFreezeAmount ?? actionTarget.freezeAmount" prefix="¥ " />
          </span>
        </div>
        <div class="target-row">
          <span class="label">当前可扣额度：</span>
          <span class="value amount-highlight">
            <AmountDisplay :value="actionTarget.freezeAmount" prefix="¥ " />
          </span>
        </div>
      </div>

      <el-form
        ref="deductFormRef"
        :model="deductForm"
        :rules="deductFormRules"
        label-width="100px"
        class="dialog-form"
      >
        <el-form-item label="扣款金额" prop="deductAmount">
          <div class="amount-input-row">
            <el-input-number
              v-model="deductForm.deductAmount"
              :min="0.01"
              :max="actionTarget?.freezeAmount || 999999999"
              :precision="2"
              :step="100"
              placeholder="请输入扣划金额"
              style="width: 100%"
            />
            <el-button type="danger" link @click="fillFullDeduct">全部扣除</el-button>
          </div>
        </el-form-item>

        <el-form-item label="扣款原因" prop="reason">
          <el-input
            v-model="deductForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入扣款原因、法院执行裁定书文号或业务违约扣款审批单据号"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="deductModalVisible = false">取消</el-button>
          <el-button type="danger" :loading="submitting" @click="submitFreezeDeduct">
            确认扣款执行
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 全景档案抽屉 ==================== -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="资金冻结记录全景档案"
      size="520px"
      destroy-on-close
    >
      <div v-if="selectedRecord" class="drawer-detail-content">
        <div class="detail-header-card">
          <div class="detail-id-row">
            <span>冻结编号：</span>
            <span class="mono-code">{{ selectedRecord.freezeId }}</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyText(selectedRecord.freezeId, '冻结单号')"
            />
          </div>
          <div class="detail-status-tag">
            <el-tag :type="selectedRecord.status === 1 ? 'danger' : 'info'">
              {{ selectedRecord.status === 1 ? '冻结中' : '已解冻' }}
            </el-tag>
          </div>
        </div>

        <el-descriptions title="资金核心属性" :column="1" border size="small" class="detail-desc">
          <el-descriptions-item label="初始冻结金额">
            <span class="drawer-amount">
              <AmountDisplay :value="selectedRecord.origFreezeAmount ?? selectedRecord.freezeAmount" prefix="¥ " />
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="剩余冻结金额">
            <span class="drawer-amount" :class="{ 'text-muted': selectedRecord.freezeAmount === 0 }">
              <AmountDisplay :value="selectedRecord.freezeAmount" prefix="¥ " />
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="累计已解冻金额">
            <span class="unfrozen-amount-text">
              <AmountDisplay :value="selectedRecord.unfrozenAmount ?? 0" prefix="¥ " />
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="累计已扣款金额">
            <span class="deducted-amount-text">
              <AmountDisplay :value="selectedRecord.deductedAmount ?? 0" prefix="¥ " />
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="关联账户编号">
            <span class="mono">{{ selectedRecord.accountNo }}</span>
            <el-button
              link
              type="primary"
              size="small"
              class="ml-1"
              @click="jumpToBalance(selectedRecord.accountNo)"
            >
              查余额
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="失效到期时间">
            <span>{{ formatDateTime(selectedRecord.expireTime) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交易发生时间">
            <span>{{ formatDateTime(selectedRecord.tradeTime) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="记录创建时间">
            <span>{{ formatDateTime(selectedRecord.createTime) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="冻结原因 / 摘要">
            <span>{{ selectedRecord.summary || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 快捷操作区 -->
        <div v-if="selectedRecord.status === 1" class="drawer-actions">
          <el-button type="success" :icon="Unlock" @click="handleDrawerUnfreeze">
            资金解冻
          </el-button>
          <el-button type="danger" :icon="CreditCard" @click="handleDrawerDeduct">
            冻结扣款
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Search,
  RefreshRight,
  Refresh,
  Plus,
  CopyDocument,
  MoreFilled,
  Tickets,
  Unlock,
  CreditCard,
  Wallet
} from '@element-plus/icons-vue'
import {
  getFreezePage,
  freezeFund,
  unfreezeFund,
  deductFromFreeze,
  searchAccountCandidates,
  getAccountBalance,
  FREEZE_STATUS_OPTIONS,
  type FreezeRecordItem
} from '@/api/freeze'

const router = useRouter()

// 检索表单
const searchForm = reactive({
  accountNo: '',
  freezeId: '',
  status: undefined as number | undefined
})
const createDateRange = ref<[string, string] | null>(null)

// 表格数据与分页
const tableData = ref<FreezeRecordItem[]>([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// 操作目标行
const actionTarget = ref<FreezeRecordItem | null>(null)
const selectedRecord = ref<FreezeRecordItem | null>(null)
const detailDrawerVisible = ref(false)

// 提交中状态
const submitting = ref(false)

// ==================== 1. 冻结弹窗状态 ====================
const freezeModalVisible = ref(false)
const freezeFormRef = ref<FormInstance>()
const accountBalanceInfo = ref<any>(null)
const freezeForm = reactive({
  accountNo: '',
  freezeAmount: undefined as number | undefined,
  expireTime: '',
  reason: ''
})
const freezeFormRules: FormRules = {
  accountNo: [{ required: true, message: '请输入账户编号', trigger: 'blur' }],
  freezeAmount: [
    { required: true, message: '请输入冻结金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value || value <= 0) {
          callback(new Error('冻结金额必须大于0'))
        } else if (accountBalanceInfo.value && value > accountBalanceInfo.value.availableBalance) {
          callback(new Error(`冻结金额不能超过账户可用余额 ¥ ${accountBalanceInfo.value.availableBalance}`))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  reason: [{ required: true, message: '请输入冻结原因说明', trigger: 'blur' }]
}

// ==================== 2. 解冻弹窗状态 ====================
const unfreezeModalVisible = ref(false)
const unfreezeFormRef = ref<FormInstance>()
const unfreezeForm = reactive({
  freezeId: '',
  unfreezeAmount: undefined as number | undefined,
  reason: ''
})
const unfreezeFormRules: FormRules = {
  unfreezeAmount: [{ required: true, message: '请输入解冻金额', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入解冻说明', trigger: 'blur' }]
}

// ==================== 3. 扣款弹窗状态 ====================
const deductModalVisible = ref(false)
const deductFormRef = ref<FormInstance>()
const deductForm = reactive({
  freezeId: '',
  deductAmount: undefined as number | undefined,
  reason: ''
})
const deductFormRules: FormRules = {
  deductAmount: [{ required: true, message: '请输入扣划金额', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入扣款原因或执行文号', trigger: 'blur' }]
}

// 自动完成输入建议
async function queryAccountSuggestions(queryString: string, cb: (results: any[]) => void) {
  try {
    const res = await searchAccountCandidates(queryString || '')
    const list = (res.list || []).map((item: any) => ({
      accountNo: item.accountNo,
      accountName: item.accountName
    }))
    cb(list)
  } catch {
    cb([])
  }
}

function handleSelectAccount(item: any) {
  searchForm.accountNo = item.accountNo
  handleSearch()
}

// 冻结弹窗中选择账号
function handleFreezeAccountSelected(item: any) {
  freezeForm.accountNo = item.accountNo
  fetchAccountAvailableBalance()
}

// 获取待冻结账户的可用余额与户名
async function fetchAccountAvailableBalance() {
  const acc = freezeForm.accountNo?.trim()
  if (!acc) {
    accountBalanceInfo.value = null
    return
  }
  try {
    const res = await getAccountBalance(acc)
    accountBalanceInfo.value = res
  } catch {
    accountBalanceInfo.value = null
  }
}

// 主表格分页查询
async function fetchData() {
  loading.value = true
  const startDate = createDateRange.value ? createDateRange.value[0] : undefined
  const endDate = createDateRange.value ? createDateRange.value[1] : undefined

  try {
    const res = await getFreezePage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      accountNo: searchForm.accountNo?.trim() || undefined,
      freezeId: searchForm.freezeId?.trim() || undefined,
      status: searchForm.status,
      startDate,
      endDate
    })
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handlePageChange(p: number) {
  pageNo.value = p
  fetchData()
}

function handleDateRangeChange() {
  handleSearch()
}

function handleReset() {
  searchForm.accountNo = ''
  searchForm.freezeId = ''
  searchForm.status = undefined
  createDateRange.value = null
  handleSearch()
}

// 打开冻结弹窗
function openFreezeModal() {
  accountBalanceInfo.value = null
  freezeForm.accountNo = ''
  freezeForm.freezeAmount = undefined
  freezeForm.expireTime = ''
  freezeForm.reason = ''
  freezeModalVisible.value = true
}

// 提交资金冻结
async function submitFundFreeze() {
  if (!freezeFormRef.value) return
  await freezeFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await freezeFund({
        accountNo: freezeForm.accountNo.trim(),
        freezeAmount: freezeForm.freezeAmount!,
        expireTime: freezeForm.expireTime ? freezeForm.expireTime : undefined,
        reason: freezeForm.reason.trim()
      })
      ElMessage.success('资金冻结申请成功')
      freezeModalVisible.value = false
      fetchData()
    } catch {
      // 错误由全局拦截器提示
    } finally {
      submitting.value = false
    }
  })
}

// 打开解冻弹窗
function openUnfreezeModal(row: FreezeRecordItem) {
  actionTarget.value = row
  unfreezeForm.freezeId = row.freezeId
  unfreezeForm.unfreezeAmount = row.freezeAmount
  unfreezeForm.reason = ''
  unfreezeModalVisible.value = true
}

function fillFullUnfreeze() {
  if (actionTarget.value) {
    unfreezeForm.unfreezeAmount = actionTarget.value.freezeAmount
  }
}

// 提交资金解冻
async function submitFundUnfreeze() {
  if (!unfreezeFormRef.value) return
  await unfreezeFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await unfreezeFund({
        freezeId: unfreezeForm.freezeId,
        unfreezeAmount: unfreezeForm.unfreezeAmount!,
        reason: unfreezeForm.reason.trim()
      })
      ElMessage.success('资金解冻成功')
      unfreezeModalVisible.value = false
      if (detailDrawerVisible.value) {
        detailDrawerVisible.value = false
      }
      fetchData()
    } catch {
      // 错误提示
    } finally {
      submitting.value = false
    }
  })
}

// 打开扣款弹窗
function openDeductModal(row: FreezeRecordItem) {
  actionTarget.value = row
  deductForm.freezeId = row.freezeId
  deductForm.deductAmount = row.freezeAmount
  deductForm.reason = ''
  deductModalVisible.value = true
}

function fillFullDeduct() {
  if (actionTarget.value) {
    deductForm.deductAmount = actionTarget.value.freezeAmount
  }
}

// 提交冻结扣款
async function submitFreezeDeduct() {
  if (!deductFormRef.value) return
  await deductFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await deductFromFreeze({
        freezeId: deductForm.freezeId,
        deductAmount: deductForm.deductAmount!,
        reason: deductForm.reason.trim()
      })
      ElMessage.success('冻结资金扣款成功，已生成财务记账凭证')
      deductModalVisible.value = false
      if (detailDrawerVisible.value) {
        detailDrawerVisible.value = false
      }
      fetchData()
    } catch {
      // 错误提示
    } finally {
      submitting.value = false
    }
  })
}

// 抽屉详情
function openDetailDrawer(row: FreezeRecordItem) {
  selectedRecord.value = row
  detailDrawerVisible.value = true
}

function handleDrawerUnfreeze() {
  if (selectedRecord.value) {
    openUnfreezeModal(selectedRecord.value)
  }
}

function handleDrawerDeduct() {
  if (selectedRecord.value) {
    openDeductModal(selectedRecord.value)
  }
}

// 路由跳转至查余额
function jumpToBalance(accountNo: string) {
  if (!accountNo) return
  router.push({
    path: '/business/balance',
    query: { accountNo }
  })
}

// 复制文字工具
function copyText(text: string, label: string) {
  if (!text) return
  if (navigator && navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success(`${label}已复制到剪贴板`)
    }).catch(() => {
      fallbackCopy(text, label)
    })
  } else {
    fallbackCopy(text, label)
  }
}

function fallbackCopy(text: string, label: string) {
  const el = document.createElement('textarea')
  el.value = text
  document.body.appendChild(el)
  el.select()
  document.execCommand('copy')
  document.body.removeChild(el)
  ElMessage.success(`${label}已复制到剪贴板`)
}

// 判断是否已过期
function isExpired(expireTime: string): boolean {
  if (!expireTime) return false
  return new Date(expireTime).getTime() < Date.now()
}

function formatDateTime(val: string) {
  if (!val) return '-'
  return val.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.freeze-management-page {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fin-card {
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  padding: 16px 20px;
}

.search-card {
  padding-bottom: 8px;
}

.freeze-search-form {
  margin-bottom: 0;
}

.autocomplete-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.autocomplete-item .acct-no {
  font-family: var(--fin-font-mono, monospace);
  font-weight: 600;
  color: #303133;
}

.autocomplete-item .acct-name {
  color: #909399;
  font-size: 12px;
}

.code-tag {
  font-family: var(--fin-font-mono, monospace);
  color: #303133;
}

.code-tag.highlight {
  cursor: pointer;
  color: var(--el-color-primary);
  font-weight: 600;
}

.code-tag.highlight:hover {
  text-decoration: underline;
}

.account-link {
  font-family: var(--fin-font-mono, monospace);
  color: #303133;
  cursor: pointer;
}

.account-link:hover {
  color: var(--el-color-primary);
  text-decoration: underline;
}

.cell-copy-btn {
  margin-left: 4px;
}

.freeze-amount-text {
  font-weight: 600;
  color: #fa8c16;
}

.unfrozen-amount-text {
  font-weight: 500;
  color: #52c41a;
}

.deducted-amount-text {
  font-weight: 500;
  color: #ff4d4f;
}

.expire-time-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.expired-badge {
  transform: scale(0.85);
}

.action-more-btn {
  padding: 4px 6px;
  height: 28px;
  border-radius: 4px;
}

.action-more-btn:hover {
  background-color: var(--el-color-primary-light-9);
}

.pagination-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 弹窗与表单 */
.dialog-form {
  padding: 8px 10px 0 0;
}

.account-avail-box {
  margin-top: 6px;
  background: #f4f8fd;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.avail-title {
  color: #909399;
}

.avail-name {
  color: #303133;
  font-weight: 500;
}

.avail-divider {
  color: #dcdfe6;
}

.avail-amount {
  color: #2b70f2;
  font-weight: 600;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.action-target-banner {
  background: #f8f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 14px;
}

.target-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  line-height: 1.8;
}

.target-row .label {
  color: #909399;
  width: 100px;
}

.target-row .value {
  color: #303133;
  font-weight: 500;
}

.target-row .amount-highlight {
  color: #fa8c16;
  font-size: 15px;
  font-weight: 600;
}

.amount-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.deduct-alert {
  margin-bottom: 12px;
}

/* 抽屉样式 */
.drawer-detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header-card {
  background: #f8f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-id-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}

.mono-code {
  font-family: var(--fin-font-mono, monospace);
  font-weight: 600;
  color: var(--el-color-primary);
}

.drawer-amount {
  font-size: 16px;
  font-weight: 700;
  color: #fa8c16;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.mono {
  font-family: var(--fin-font-mono, monospace);
}

.mt-3 {
  margin-top: 12px;
}

.ml-1 {
  margin-left: 6px;
}
</style>
