<template>
  <div class="balance-query-page">
    <!-- ==================== 顶部检索卡片 ==================== -->
    <div class="fin-card search-card">
      <el-form :inline="true" :model="searchForm" class="balance-search-form">
        <el-form-item label="账户编号" required>
          <el-autocomplete
            v-model="searchForm.accountNo"
            :fetch-suggestions="queryAccountSuggestions"
            placeholder="输入或检索账户编号"
            clearable
            style="width: 280px;"
            @select="handleSelectAccount"
            @keyup.enter="handleSearchAll"
          >
            <template #default="{ item }">
              <div class="autocomplete-item">
                <span class="acct-no">{{ item.accountNo }}</span>
                <span class="acct-name">{{ item.accountName }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loadingAggregate" @click="handleSearchAll">
            查询
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
          <el-button
            v-if="currentAccountNo"
            :icon="Refresh"
            :loading="loadingAggregate || loadingDetails || loadingFreeze"
            @click="refreshCurrentData"
          >
            刷新数据
          </el-button>
        </el-form-item>

        <!-- 快速示例账号推荐 -->
        <div v-if="accountCandidates.length > 0" class="quick-candidates">
          <span class="quick-label">快捷检索：</span>
          <el-tag
            v-for="item in accountCandidates.slice(0, 4)"
            :key="item.accountNo"
            class="candidate-tag"
            effect="plain"
            @click="quickSelectAccount(item.accountNo)"
          >
            {{ item.accountName || item.accountNo }} ({{ item.accountNo.slice(-6) }})
          </el-tag>
        </div>
      </el-form>
    </div>

    <!-- ==================== 资产看板区域 ==================== -->
    <div v-if="aggregateData" class="fin-card overview-card">
      <!-- 账户基本元信息条 -->
      <div class="account-meta-banner">
        <div class="meta-left">
          <div class="account-title-row">
            <span class="account-name">{{ aggregateData.accountName || '未命名账户' }}</span>
            <el-tag size="small" type="info" effect="light" class="account-no-tag">
              <span class="mono">{{ aggregateData.accountNo }}</span>
            </el-tag>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              title="复制账号"
              @click="copyText(aggregateData.accountNo, '账号')"
            />
          </div>

          <div class="meta-tags-row">
            <span class="meta-item">
              <span class="meta-label">会计科目:</span>
              <span class="meta-value">{{ aggregateData.subjectCode }}</span>
            </span>
            <span class="meta-divider">|</span>
            <span class="meta-item">
              <span class="meta-label">币种:</span>
              <span class="meta-value">{{ aggregateData.currency || 'CNY' }}</span>
            </span>
            <span class="meta-divider">|</span>
            <span class="meta-item">
              <span class="meta-label">余额方向:</span>
              <el-tag :type="aggregateData.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
                {{ aggregateData.balanceDirection === 1 ? '借方 (Dr)' : '贷方 (Cr)' }}
              </el-tag>
            </span>
            <span class="meta-divider">|</span>
            <span class="meta-item">
              <span class="meta-label">账户状态:</span>
              <el-tag :type="getStatusTagType(aggregateData.status)" size="small">
                {{ aggregateData.statusDesc || getStatusLabel(aggregateData.status) }}
              </el-tag>
            </span>
            <span class="meta-divider">|</span>
            <span class="meta-item">
              <span class="meta-label">风控状态:</span>
              <el-tag :type="getRiskTagType(aggregateData.riskStatus)" size="small">
                {{ aggregateData.riskStatusDesc || getRiskLabel(aggregateData.riskStatus) }}
              </el-tag>
            </span>
          </div>
        </div>

        <div class="meta-right">
          <span class="query-time-label">数据时效：</span>
          <span class="query-time-val">{{ formatDateTime(aggregateData.queryTime) }}</span>
        </div>
      </div>

      <!-- 4 维关键资产卡片 -->
      <div class="kpi-grid">
        <div class="kpi-card total">
          <div class="kpi-header">
            <el-icon class="kpi-icon"><Wallet /></el-icon>
            <span class="kpi-title">主账户总余额</span>
          </div>
          <div class="kpi-value">
            <AmountDisplay :value="aggregateData.totalBalance" prefix="¥ " />
          </div>
          <div class="kpi-tip">可用子账户 + 冻结子账户</div>
        </div>

        <div class="kpi-card available">
          <div class="kpi-header">
            <el-icon class="kpi-icon"><Coin /></el-icon>
            <span class="kpi-title">可用子账户余额</span>
          </div>
          <div class="kpi-value">
            <AmountDisplay :value="aggregateData.availableBalance" prefix="¥ " />
          </div>
          <div class="kpi-tip">子账户 1 (可实时扣划与支付出金)</div>
        </div>

        <div class="kpi-card frozen">
          <div class="kpi-header">
            <el-icon class="kpi-icon"><Lock /></el-icon>
            <span class="kpi-title">冻结子账户余额</span>
          </div>
          <div class="kpi-value">
            <AmountDisplay :value="aggregateData.frozenBalance" prefix="¥ " />
          </div>
          <div class="kpi-tip">子账户 2 (在途锁定与司法冻结资金)</div>
        </div>

        <div class="kpi-card buffer">
          <div class="kpi-header">
            <el-icon class="kpi-icon"><Timer /></el-icon>
            <span class="kpi-title">缓冲待入账预估</span>
          </div>
          <div class="kpi-value">
            <AmountDisplay :value="aggregateData.bufferEstimate" prefix="¥ " />
          </div>
          <div class="kpi-tip">缓冲队列待过账汇总 (预估变动)</div>
        </div>
      </div>
    </div>

    <!-- 未查询空状态引导 -->
    <div v-else-if="!loadingAggregate" class="fin-card empty-guide-card">
      <el-empty description="请输入账户编号并点击查询，即可调取账户实时聚合资产看板与完整历史变动流水">
        <template #image>
          <div class="empty-icon-box">
            <el-icon :size="56" color="#909399"><Search /></el-icon>
          </div>
        </template>
      </el-empty>
    </div>

    <!-- ==================== 变动流水与冻结记录选项卡 ==================== -->
    <div v-if="currentAccountNo" class="fin-card tabs-card">
      <el-tabs v-model="activeTab" class="balance-sub-tabs" @tab-change="handleTabChange">
        <!-- ==================== Tab 1: 交易流水变动明细 ==================== -->
        <el-tab-pane label="交易变动明细" name="details">
          <!-- 筛选表单 -->
          <el-form :inline="true" :model="detailFilter" class="sub-filter-form">
            <el-form-item label="会计日期">
              <el-date-picker
                v-model="detailDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 230px;"
                @change="handleDateRangeChange"
              />
            </el-form-item>

            <el-form-item label="交易类别">
              <el-select
                v-model="detailFilter.tradeType"
                placeholder="全部类别"
                clearable
                style="width: 130px;"
                @change="loadDetails(1)"
              >
                <el-option
                  v-for="item in TRADE_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="借贷方向">
              <el-select
                v-model="detailFilter.debitCredit"
                placeholder="全部方向"
                clearable
                style="width: 120px;"
                @change="loadDetails(1)"
              >
                <el-option
                  v-for="item in DEBIT_CREDIT_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="loadingDetails" @click="loadDetails(1)">
                筛选
              </el-button>
              <el-button :icon="RefreshRight" @click="handleResetDetailFilter">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 交易流水明细表格 -->
          <el-table
            v-loading="loadingDetails"
            :data="detailList"
            border
            stripe
            size="small"
            class="fin-table"
            empty-text="暂无匹配的交易流水变动记录"
          >
            <el-table-column type="index" label="序号" width="50" align="center" />

            <el-table-column prop="voucherNo" label="凭证编号" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="code-tag highlight" @click="openAuditDrawer(row)">
                  {{ row.voucherNo }}
                </span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="CopyDocument"
                  class="cell-copy-btn"
                  @click="copyText(row.voucherNo, '凭证号')"
                />
              </template>
            </el-table-column>

            <el-table-column prop="accountingDate" label="会计日期" width="105" align="center" />

            <el-table-column prop="tradeTime" label="交易时间" width="145" align="center">
              <template #default="{ row }">
                <span>{{ formatDateTime(row.tradeTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="tradeType" label="交易类别" width="95" align="center">
              <template #default="{ row }">
                <el-tag :type="getTradeTypeTagType(row.tradeType)" size="small">
                  {{ row.tradeTypeDesc || getTradeTypeLabel(row.tradeType) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="debitCredit" label="借贷方向" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.debitCredit === 1 ? 'primary' : 'warning'" size="small" effect="dark">
                  {{ row.debitCredit === 1 ? '借方' : '贷方' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="changeDirection" label="变动方向" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.changeDirection === 1 ? 'success' : 'info'" size="small">
                  {{ row.changeDirection === 1 ? '+ 增加' : '- 减少' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="preBalance" label="交易前余额" min-width="125" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.preBalance" prefix="¥ " />
              </template>
            </el-table-column>

            <el-table-column prop="amount" label="交易金额" min-width="130" align="right">
              <template #default="{ row }">
                <span :class="['trans-amount', row.changeDirection === 1 ? 'is-increase' : 'is-decrease']">
                  <AmountDisplay :value="row.amount" prefix="¥ " />
                </span>
              </template>
            </el-table-column>

            <el-table-column prop="postBalance" label="交易后余额" min-width="125" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.postBalance" prefix="¥ " />
              </template>
            </el-table-column>

            <el-table-column prop="summary" label="摘要说明" min-width="150" show-overflow-tooltip />

            <!-- 操作列：统一使用 MoreFilled "..." 紧凑下拉菜单 (70px) -->
            <el-table-column label="操作" width="70" fixed="right" align="center">
              <template #default="{ row }">
                <el-dropdown trigger="click">
                  <el-button link type="primary" :icon="MoreFilled" class="action-more-btn" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :icon="Tickets" @click="openAuditDrawer(row)">
                        审计凭据
                      </el-dropdown-item>
                      <el-dropdown-item :icon="CopyDocument" @click="copyText(row.voucherNo, '凭证编号')">
                        复制凭证号
                      </el-dropdown-item>
                      <el-dropdown-item v-if="row.txnNo" :icon="CopyDocument" @click="copyText(row.txnNo, '事务号')">
                        复制事务号
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页组件 -->
          <div class="pagination-footer">
            <el-pagination
              v-model:current-page="detailPageNo"
              v-model:page-size="detailPageSize"
              :total="detailTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              size="small"
              @size-change="loadDetails(1)"
              @current-change="(page: number) => loadDetails(page)"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== Tab 2: 资金冻结记录 ==================== -->
        <el-tab-pane label="资金冻结记录" name="freeze">
          <!-- 筛选表单 -->
          <el-form :inline="true" :model="freezeFilter" class="sub-filter-form">
            <el-form-item label="冻结状态">
              <el-select
                v-model="freezeFilter.status"
                placeholder="全部状态"
                clearable
                style="width: 130px;"
                @change="loadFreezeRecords(1)"
              >
                <el-option
                  v-for="item in FREEZE_STATUS_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="loadingFreeze" @click="loadFreezeRecords(1)">
                筛选
              </el-button>
              <el-button :icon="RefreshRight" @click="handleResetFreezeFilter">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 冻结记录表格 -->
          <el-table
            v-loading="loadingFreeze"
            :data="freezeList"
            border
            stripe
            size="small"
            class="fin-table"
            empty-text="暂无资金冻结记录"
          >
            <el-table-column type="index" label="序号" width="50" align="center" />

            <el-table-column prop="freezeId" label="冻结编号" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="code-tag highlight">{{ row.freezeId }}</span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :icon="CopyDocument"
                  class="cell-copy-btn"
                  @click="copyText(row.freezeId, '冻结编号')"
                />
              </template>
            </el-table-column>

            <el-table-column prop="freezeAmount" label="冻结金额" min-width="130" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.freezeAmount" prefix="¥ " />
              </template>
            </el-table-column>

            <el-table-column prop="status" label="状态" width="95" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small">
                  {{ row.status === 1 ? '冻结中' : '已解冻' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="expireTime" label="过期到期时间" width="155" align="center">
              <template #default="{ row }">
                <span>{{ formatDateTime(row.expireTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="tradeTime" label="交易发生时间" width="155" align="center">
              <template #default="{ row }">
                <span>{{ formatDateTime(row.tradeTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="createTime" label="记录创建时间" width="155" align="center">
              <template #default="{ row }">
                <span>{{ formatDateTime(row.createTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="summary" label="冻结原因 / 摘要" min-width="180" show-overflow-tooltip />

            <!-- 操作列 -->
            <el-table-column label="操作" width="70" fixed="right" align="center">
              <template #default="{ row }">
                <el-dropdown trigger="click">
                  <el-button link type="primary" :icon="MoreFilled" class="action-more-btn" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :icon="CopyDocument" @click="copyText(row.freezeId, '冻结编号')">
                        复制编号
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页组件 -->
          <div class="pagination-footer">
            <el-pagination
              v-model:current-page="freezePageNo"
              v-model:page-size="freezePageSize"
              :total="freezeTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              size="small"
              @size-change="loadFreezeRecords(1)"
              @current-change="(page: number) => loadFreezeRecords(page)"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 交易变动流水深层审计抽屉 ==================== -->
    <el-drawer
      v-model="auditDrawerVisible"
      title="交易流水全景审计档案"
      size="560px"
      destroy-on-close
    >
      <div v-if="selectedDetail" class="audit-drawer-content">
        <!-- 凭据核心指标卡 -->
        <div class="audit-header-banner">
          <div class="audit-title-text">
            <span>凭证编号：</span>
            <span class="mono-code">{{ selectedDetail.voucherNo }}</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyText(selectedDetail.voucherNo, '凭证号')"
            />
          </div>
          <div class="audit-tag-badges">
            <el-tag :type="getTradeTypeTagType(selectedDetail.tradeType)" size="small">
              {{ selectedDetail.tradeTypeDesc || getTradeTypeLabel(selectedDetail.tradeType) }}
            </el-tag>
            <el-tag :type="selectedDetail.debitCredit === 1 ? 'primary' : 'warning'" size="small">
              {{ selectedDetail.debitCredit === 1 ? '借方 (Dr)' : '贷方 (Cr)' }}
            </el-tag>
            <el-tag :type="selectedDetail.changeDirection === 1 ? 'success' : 'info'" size="small">
              {{ selectedDetail.changeDirection === 1 ? '增加' : '减少' }}
            </el-tag>
          </div>
        </div>

        <!-- 金额变动对账卡片 -->
        <div class="audit-balance-card">
          <div class="audit-balance-row">
            <div class="bal-col">
              <div class="bal-label">交易前余额</div>
              <div class="bal-val">
                <AmountDisplay :value="selectedDetail.preBalance" prefix="¥ " />
              </div>
            </div>
            <div class="bal-divider">
              <span class="operator">{{ selectedDetail.changeDirection === 1 ? '+' : '-' }}</span>
            </div>
            <div class="bal-col">
              <div class="bal-label">本次变动金额</div>
              <div class="bal-val trans-highlight">
                <AmountDisplay :value="selectedDetail.amount" prefix="¥ " />
              </div>
            </div>
            <div class="bal-divider">
              <span class="operator">=</span>
            </div>
            <div class="bal-col">
              <div class="bal-label">交易后余额</div>
              <div class="bal-val post-highlight">
                <AmountDisplay :value="selectedDetail.postBalance" prefix="¥ " />
              </div>
            </div>
          </div>
        </div>

        <!-- 详细结构属性 -->
        <el-descriptions title="凭证与事务关联" :column="1" border size="small" class="audit-desc-table">
          <el-descriptions-item label="分录流水号 (Entry ID)">
            <span class="mono">{{ selectedDetail.entryId || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="事务编号 (Txn No)">
            <span class="mono">{{ selectedDetail.txnNo || '-' }}</span>
            <el-button
              v-if="selectedDetail.txnNo"
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyText(selectedDetail.txnNo, '事务编号')"
            />
          </el-descriptions-item>
          <el-descriptions-item label="系统跟踪号 (Trace No)">
            <span class="mono">{{ selectedDetail.traceNo || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="账户编号 (Account No)">
            <span class="mono">{{ selectedDetail.accountNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="科目编码 (Subject Code)">
            <span class="mono">{{ selectedDetail.subjectCode }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="业务线编码 (Business)">
            <span>{{ selectedDetail.businessCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交易编码 (Trading)">
            <span>{{ selectedDetail.tradingCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="会计日期">
            <span>{{ selectedDetail.accountingDate || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交易发生时间">
            <span>{{ formatDateTime(selectedDetail.tradeTime) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="币种">
            <span>{{ selectedDetail.currency || 'CNY' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="摘要说明">
            <span>{{ selectedDetail.summary || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 原始审计报文折叠面板 -->
        <el-collapse class="audit-json-collapse">
          <el-collapse-item title="原始分录 JSON 审计报文" name="json">
            <pre class="json-code-box">{{ JSON.stringify(selectedDetail, null, 2) }}</pre>
            <div class="json-copy-bar">
              <el-button
                size="small"
                :icon="CopyDocument"
                @click="copyText(JSON.stringify(selectedDetail, null, 2), 'JSON报文')"
              >
                复制原始 JSON
              </el-button>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  RefreshRight,
  Refresh,
  CopyDocument,
  Wallet,
  Coin,
  Lock,
  Timer,
  Tickets,
  MoreFilled
} from '@element-plus/icons-vue'
import {
  getAggregateBalance,
  getAccountDetails,
  getFreezeRecords,
  searchAccountCandidates,
  TRADE_TYPE_OPTIONS,
  DEBIT_CREDIT_OPTIONS,
  FREEZE_STATUS_OPTIONS,
  type AggregateBalanceResponse,
  type AccountDetailItem,
  type FreezeRecordItem
} from '@/api/balance'

const route = useRoute()

// 检索表单
const searchForm = reactive({
  accountNo: ''
})

// 当前已加载查询成功的账户编号
const currentAccountNo = ref('')

// 候选账户列表（用于联想与快速示例）
interface AccountCandidate {
  accountNo: string
  accountName: string
}
const accountCandidates = ref<AccountCandidate[]>([])

// 聚合看板数据
const aggregateData = ref<AggregateBalanceResponse | null>(null)
const loadingAggregate = ref(false)

// 选项卡状态
const activeTab = ref('details')

// ==================== Tab 1: 交易流水明细 ====================
const detailDateRange = ref<[string, string] | null>(null)
const detailFilter = reactive({
  tradeType: undefined as number | undefined,
  debitCredit: undefined as number | undefined
})
const detailList = ref<AccountDetailItem[]>([])
const detailTotal = ref(0)
const detailPageNo = ref(1)
const detailPageSize = ref(10)
const loadingDetails = ref(false)

// ==================== Tab 2: 资金冻结记录 ====================
const freezeFilter = reactive({
  status: undefined as number | undefined
})
const freezeList = ref<FreezeRecordItem[]>([])
const freezeTotal = ref(0)
const freezePageNo = ref(1)
const freezePageSize = ref(10)
const loadingFreeze = ref(false)

// ==================== 审计凭据抽屉 ====================
const auditDrawerVisible = ref(false)
const selectedDetail = ref<AccountDetailItem | null>(null)

// 自动完成输入建议
async function queryAccountSuggestions(queryString: string, cb: (results: AccountCandidate[]) => void) {
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

function handleSelectAccount(item: AccountCandidate) {
  searchForm.accountNo = item.accountNo
  handleSearchAll()
}

function quickSelectAccount(accNo: string) {
  searchForm.accountNo = accNo
  handleSearchAll()
}

// 统一查询所有模块（聚合看板 + 激活的选项卡）
async function handleSearchAll() {
  const accNo = searchForm.accountNo?.trim()
  if (!accNo) {
    ElMessage.warning('请输入或选择账户编号')
    return
  }

  currentAccountNo.value = accNo
  await loadAggregateBalance(accNo)

  // 同步重置并加载子标签数据
  detailPageNo.value = 1
  freezePageNo.value = 1
  if (activeTab.value === 'details') {
    loadDetails(1)
  } else {
    loadFreezeRecords(1)
  }
}

// 刷新当前数据
async function refreshCurrentData() {
  if (!currentAccountNo.value) return
  await loadAggregateBalance(currentAccountNo.value)
  if (activeTab.value === 'details') {
    loadDetails(detailPageNo.value)
  } else {
    loadFreezeRecords(freezePageNo.value)
  }
  ElMessage.success('账户资产与流水数据已刷新')
}

// 重置查询
function handleReset() {
  searchForm.accountNo = ''
  currentAccountNo.value = ''
  aggregateData.value = null
  detailList.value = []
  detailTotal.value = 0
  freezeList.value = []
  freezeTotal.value = 0
}

// 加载聚合余额
async function loadAggregateBalance(accountNo: string) {
  loadingAggregate.value = true
  try {
    const res = await getAggregateBalance(accountNo)
    aggregateData.value = res
  } catch {
    aggregateData.value = null
  } finally {
    loadingAggregate.value = false
  }
}

// 加载明细
async function loadDetails(page = 1) {
  if (!currentAccountNo.value) return
  detailPageNo.value = page
  loadingDetails.value = true

  const startDate = detailDateRange.value ? detailDateRange.value[0] : undefined
  const endDate = detailDateRange.value ? detailDateRange.value[1] : undefined

  try {
    const res = await getAccountDetails({
      pageNo: detailPageNo.value,
      pageSize: detailPageSize.value,
      accountNo: currentAccountNo.value,
      startDate,
      endDate,
      tradeType: detailFilter.tradeType,
      debitCredit: detailFilter.debitCredit
    })
    detailList.value = res.list || []
    detailTotal.value = res.total || 0
  } catch {
    detailList.value = []
    detailTotal.value = 0
  } finally {
    loadingDetails.value = false
  }
}

function handleDateRangeChange() {
  loadDetails(1)
}

function handleResetDetailFilter() {
  detailDateRange.value = null
  detailFilter.tradeType = undefined
  detailFilter.debitCredit = undefined
  loadDetails(1)
}

// 加载冻结记录
async function loadFreezeRecords(page = 1) {
  if (!currentAccountNo.value) return
  freezePageNo.value = page
  loadingFreeze.value = true

  try {
    const res = await getFreezeRecords({
      pageNo: freezePageNo.value,
      pageSize: freezePageSize.value,
      accountNo: currentAccountNo.value,
      status: freezeFilter.status
    })
    freezeList.value = res.list || []
    freezeTotal.value = res.total || 0
  } catch {
    freezeList.value = []
    freezeTotal.value = 0
  } finally {
    loadingFreeze.value = false
  }
}

function handleResetFreezeFilter() {
  freezeFilter.status = undefined
  loadFreezeRecords(1)
}

// 标签页切换
function handleTabChange(tabName: any) {
  if (!currentAccountNo.value) return
  if (tabName === 'details' && detailList.value.length === 0) {
    loadDetails(1)
  } else if (tabName === 'freeze' && freezeList.value.length === 0) {
    loadFreezeRecords(1)
  }
}

// 抽屉审计详情
function openAuditDrawer(row: AccountDetailItem) {
  selectedDetail.value = row
  auditDrawerVisible.value = true
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

// 状态字典标签映射
function getStatusTagType(status: number) {
  switch (status) {
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status: number) {
  switch (status) {
    case 1: return '正常'
    case 2: return '冻结'
    case 3: return '注销'
    default: return '未知'
  }
}

function getRiskTagType(riskStatus: number) {
  switch (riskStatus) {
    case 1: return 'success'
    case 2:
    case 3: return 'warning'
    case 4: return 'danger'
    default: return 'info'
  }
}

function getRiskLabel(riskStatus: number) {
  switch (riskStatus) {
    case 1: return '正常'
    case 2: return '止入'
    case 3: return '止出'
    case 4: return '止入止出'
    default: return '正常'
  }
}

function getTradeTypeTagType(tradeType: number) {
  switch (tradeType) {
    case 1: return 'success'
    case 2: return 'warning'
    case 3: return 'danger'
    case 4: return 'primary'
    default: return 'info'
  }
}

function getTradeTypeLabel(tradeType: number) {
  const found = TRADE_TYPE_OPTIONS.find(t => t.value === tradeType)
  return found ? found.label : '未知'
}

function formatDateTime(val: string) {
  if (!val) return '-'
  return val.replace('T', ' ').substring(0, 19)
}

// 初始化加载快捷候选账号和处理 URL 传参
onMounted(async () => {
  try {
    const res = await searchAccountCandidates('')
    accountCandidates.value = (res.list || []).map((item: any) => ({
      accountNo: item.accountNo,
      accountName: item.accountName
    }))
  } catch {
    // 忽略预取失败
  }

  // 若 URL 查询参数带了 accountNo，则直接触发查询
  const queryAcc = route.query.accountNo as string
  if (queryAcc) {
    searchForm.accountNo = queryAcc
    handleSearchAll()
  }
})
</script>

<style scoped>
.balance-query-page {
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

.balance-search-form {
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

.quick-candidates {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

.quick-label {
  font-size: 12px;
  color: #909399;
}

.candidate-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.candidate-tag:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  transform: translateY(-1px);
}

/* 概览卡片 */
.overview-card {
  background: #ffffff;
}

.account-meta-banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 16px;
}

.account-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.account-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.account-no-tag {
  font-size: 13px;
  padding: 0 8px;
}

.meta-tags-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: #909399;
}

.meta-value {
  font-weight: 500;
  color: #303133;
}

.meta-divider {
  color: #dcdfe6;
}

.meta-right {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.query-time-val {
  color: #606266;
  font-weight: 500;
  font-family: var(--fin-font-mono, monospace);
}

/* 4 维 KPI 卡片网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  border-radius: 8px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.kpi-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.kpi-icon {
  font-size: 16px;
}

.kpi-title {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}

.kpi-tip {
  font-size: 12px;
  color: #909399;
}

.kpi-card.total {
  border-color: #d9ecff;
  background: #f4f8fd;
}
.kpi-card.total .kpi-icon,
.kpi-card.total .kpi-value {
  color: #2b70f2;
}

.kpi-card.available {
  border-color: #e1f3d8;
  background: #f6fbf4;
}
.kpi-card.available .kpi-icon,
.kpi-card.available .kpi-value {
  color: #52c41a;
}

.kpi-card.frozen {
  border-color: #faecd8;
  background: #fdfaf5;
}
.kpi-card.frozen .kpi-icon,
.kpi-card.frozen .kpi-value {
  color: #fa8c16;
}

.kpi-card.buffer {
  border-color: #efdbff;
  background: #fbf8fe;
}
.kpi-card.buffer .kpi-icon,
.kpi-card.buffer .kpi-value {
  color: #722ed1;
}

/* 空状态卡片 */
.empty-guide-card {
  padding: 40px 0;
  display: flex;
  justify-content: center;
}

.empty-icon-box {
  margin-bottom: 8px;
}

/* 选项卡区域 */
.tabs-card {
  padding: 12px 18px;
}

.sub-filter-form {
  margin-bottom: 12px;
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

.cell-copy-btn {
  margin-left: 4px;
}

.trans-amount {
  font-weight: 600;
}

.trans-amount.is-increase {
  color: #2b70f2;
}

.trans-amount.is-decrease {
  color: #e6a23c;
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

/* 抽屉样式 */
.audit-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audit-header-banner {
  background: #f8f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
}

.audit-title-text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}

.mono-code {
  font-family: var(--fin-font-mono, monospace);
  font-weight: 600;
  color: var(--el-color-primary);
}

.audit-tag-badges {
  display: flex;
  gap: 8px;
}

.audit-balance-card {
  background: #ffffff;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 12px;
}

.audit-balance-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
}

.bal-col {
  text-align: center;
}

.bal-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.bal-val {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.trans-highlight {
  color: #2b70f2;
}

.post-highlight {
  color: #52c41a;
}

.bal-divider .operator {
  font-size: 18px;
  font-weight: bold;
  color: #909399;
}

.audit-desc-table {
  margin-top: 4px;
}

.audit-json-collapse {
  margin-top: 6px;
}

.json-code-box {
  background: #282c34;
  color: #abb2bf;
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  padding: 10px;
  border-radius: 4px;
  overflow: auto;
  max-height: 240px;
}

.json-copy-bar {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.mono {
  font-family: var(--fin-font-mono, monospace);
}
</style>
