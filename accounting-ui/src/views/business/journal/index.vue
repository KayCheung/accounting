<template>
  <div class="journal-management-page">
    <!-- ==================== 顶部 4 维核心流水状态看板 ==================== -->
    <div class="kpi-grid">
      <div class="kpi-card total-card">
        <div class="kpi-icon-box">
          <el-icon><Tickets /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">流水总笔数</div>
          <div class="kpi-value">{{ totalCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">当前查询范围内的业务记账流水</div>
        </div>
      </div>

      <div class="kpi-card processing-card">
        <div class="kpi-icon-box">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">处理中流水</div>
          <div class="kpi-value warning-text">{{ processingCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">凭证生成或过账处理中</div>
        </div>
      </div>

      <div class="kpi-card success-card">
        <div class="kpi-icon-box">
          <el-icon><CircleCheckFilled /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">记账成功</div>
          <div class="kpi-value success-text">{{ successCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">全流程入账完成并过账成功</div>
        </div>
      </div>

      <div class="kpi-card failed-card">
        <div class="kpi-icon-box">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">记账失败与异常</div>
          <div class="kpi-value danger-text">{{ failedCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">可支持一键重试或撤销冲正</div>
        </div>
      </div>
    </div>

    <!-- ==================== 主体工作台：Tabs 切换 ==================== -->
    <div class="fin-card main-workspace-card">
      <el-tabs v-model="activeTab" class="journal-tabs" @tab-change="handleTabChange">
        <!-- Tab 1: 业务流水记录 (Business Record) -->
        <el-tab-pane label="业务流水记录 (t_business_record)" name="journal">
          <!-- 检索表单 -->
          <el-form :inline="true" :model="journalSearchForm" class="journal-search-form">
            <el-form-item label="跟踪号">
              <el-input
                v-model="journalSearchForm.traceNo"
                placeholder="TRC系统跟踪号"
                clearable
                style="width: 180px;"
                @keyup.enter="handleSearchJournal"
              />
            </el-form-item>

            <el-form-item label="业务线">
              <el-select
                v-model="journalSearchForm.businessCode"
                placeholder="全部业务线"
                clearable
                style="width: 160px;"
                @change="handleSearchJournal"
              >
                <el-option
                  v-for="item in businessCodeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="交易类别">
              <el-select
                v-model="journalSearchForm.tradeType"
                placeholder="全部类别"
                clearable
                style="width: 140px;"
                @change="handleSearchJournal"
              >
                <el-option
                  v-for="item in JOURNAL_TRADE_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="流水状态">
              <el-select
                v-model="journalSearchForm.status"
                placeholder="全部状态"
                clearable
                style="width: 130px;"
                @change="handleSearchJournal"
              >
                <el-option
                  v-for="item in JOURNAL_STATUS_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="原冻结号">
              <el-input
                v-model="journalSearchForm.origFreezeNo"
                placeholder="关联预冻结单号"
                clearable
                style="width: 160px;"
                @keyup.enter="handleSearchJournal"
              />
            </el-form-item>

            <el-form-item label="会计日期">
              <el-date-picker
                v-model="journalDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="截止"
                value-format="YYYY-MM-DD"
                style="width: 230px;"
                @change="handleSearchJournal"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="journalLoading" @click="handleSearchJournal">
                查询
              </el-button>
              <el-button :icon="RefreshRight" @click="handleResetJournal">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 业务流水表格 -->
          <el-table
            v-loading="journalLoading"
            :data="journalList"
            border
            stripe
            class="fin-table"
            empty-text="暂无业务流水记录"
          >
            <el-table-column prop="traceNo" label="系统跟踪号" min-width="180">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="openOverview(row.traceNo)">
                  {{ row.traceNo }}
                </el-link>
              </template>
            </el-table-column>

            <el-table-column prop="amount" label="交易金额" width="130" align="right">
              <template #default="{ row }">
                <span class="amount-text">¥ {{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="businessCode" label="业务线" min-width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ getBusinessLabel(row.businessCode) }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="tradingCode" label="交易编码" width="130" show-overflow-tooltip />

            <el-table-column prop="payChannel" label="支付渠道" width="100" align="center" />

            <el-table-column prop="tradeTypeDesc" label="交易类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getTradeTypeTag(row.tradeType)" size="small">
                  {{ row.tradeTypeDesc || '正常' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="status" label="处理状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getJournalStatusTag(row.status)" size="small">
                  {{ row.statusDesc || getJournalStatusDesc(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="origFreezeNo" label="原预冻结单号" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.origFreezeNo" class="freeze-tag">{{ row.origFreezeNo }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>

            <el-table-column prop="accountingDate" label="会计日期" width="110" align="center" />

            <el-table-column prop="tradeTime" label="交易时间" width="170" show-overflow-tooltip />

            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="View" @click="openOverview(row.traceNo)">
                  全景总览
                </el-button>

                <el-button
                  v-if="row.status === 3"
                  link
                  type="danger"
                  size="small"
                  :icon="Refresh"
                  @click="handleRetry(row.traceNo)"
                >
                  重试
                </el-button>

                <el-button
                  v-if="row.status === 2 && row.tradeType !== 3 && row.tradeType !== 6"
                  link
                  type="warning"
                  size="small"
                  :icon="Back"
                  @click="handleRollback(row.traceNo)"
                >
                  冲账回滚
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 业务流水分页 -->
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="journalPageNo"
              v-model:page-size="journalPageSize"
              :total="journalTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="loadJournalData"
              @current-change="loadJournalData"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 账务事务记录 (Transaction) -->
        <el-tab-pane label="账务事务记录 (t_transaction)" name="transaction">
          <!-- 事务检索表单 -->
          <el-form :inline="true" :model="txSearchForm" class="journal-search-form">
            <el-form-item label="事务编号">
              <el-input
                v-model="txSearchForm.txnNo"
                placeholder="TXN全局事务号"
                clearable
                style="width: 190px;"
                @keyup.enter="handleSearchTx"
              />
            </el-form-item>

            <el-form-item label="跟踪号">
              <el-input
                v-model="txSearchForm.traceNo"
                placeholder="关联TRC系统跟踪号"
                clearable
                style="width: 190px;"
                @keyup.enter="handleSearchTx"
              />
            </el-form-item>

            <el-form-item label="事务状态">
              <el-select
                v-model="txSearchForm.status"
                placeholder="全部状态"
                clearable
                style="width: 130px;"
                @change="handleSearchTx"
              >
                <el-option
                  v-for="item in TRANSACTION_STATUS_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="会计日期">
              <el-date-picker
                v-model="txDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 300px;"
                @change="handleSearchTx"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :icon="Search" :loading="txLoading" @click="handleSearchTx">
                查询
              </el-button>
              <el-button :icon="RefreshRight" @click="handleResetTx">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 账务事务表格 -->
          <el-table
            v-loading="txLoading"
            :data="txList"
            border
            stripe
            class="fin-table"
            empty-text="暂无账务事务记录"
          >
            <el-table-column prop="txnNo" label="全局事务编号" min-width="190">
              <template #default="{ row }">
                <span class="txn-no-badge">{{ row.txnNo }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="traceNo" label="系统跟踪号" min-width="180">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="openOverview(row.traceNo)">
                  {{ row.traceNo }}
                </el-link>
              </template>
            </el-table-column>

            <el-table-column prop="amount" label="事务金额" width="130" align="right">
              <template #default="{ row }">
                <span class="amount-text">¥ {{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="currency" label="币种" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.currency || 'CNY' }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="accountingDate" label="会计日期" width="110" align="center" />

            <el-table-column prop="relateAccountCount" label="关联分户数" width="110" align="center">
              <template #default="{ row }">
                <span class="count-badge">{{ row.relateAccountCount || 0 }} 户</span>
              </template>
            </el-table-column>

            <el-table-column prop="status" label="事务状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getTxStatusTag(row.status)" size="small">
                  {{ row.statusDesc || getTxStatusDesc(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="failReason" label="失败原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.failReason" class="danger-text">{{ row.failReason }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>

            <el-table-column prop="finishTime" label="完成时间" width="170" show-overflow-tooltip />

            <el-table-column label="操作" width="130" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="View" @click="openOverview(row.traceNo)">
                  全景总览
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 事务分页 -->
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="txPageNo"
              v-model:page-size="txPageSize"
              :total="txTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="loadTxData"
              @current-change="loadTxData"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 全流程全景总览抽屉 (Panoramic Overview Drawer) ==================== -->
    <el-drawer
      v-model="drawerVisible"
      title="记账全生命周期全景看板"
      size="850px"
      destroy-on-close
      class="overview-drawer"
    >
      <div v-loading="overviewLoading" class="drawer-body">
        <template v-if="overviewData">
          <!-- 1. 全链路流转步骤条 (Steps) -->
          <div class="overview-section steps-section">
            <div class="section-title">
              <el-icon><Operation /></el-icon>
              <span>记账流转阶段</span>
              <el-tag :type="getStageTag(overviewData.processStage)" size="small" style="margin-left: 10px;">
                {{ overviewData.stageDesc }} ({{ overviewData.progressPercent }}%)
              </el-tag>
            </div>

            <el-steps :active="getStepIndex(overviewData.processStage)" :process-status="getStepStatus(overviewData.processStage)" finish-status="success" align-center>
              <el-step title="业务流水接收" description="流水已校验持久化" />
              <el-step title="规则匹配&凭证" description="推导会计科目与分录" />
              <el-step title="过账引擎调度" description="实时/异步/缓冲过账" />
              <el-step :title="overviewData.processStage === 'FAILED' ? '记账失败' : '最终入账成功'" :description="overviewData.processStage === 'FAILED' ? '可人工重试/回滚' : '双子账户余额已更新'" />
            </el-steps>
          </div>

          <!-- 异常警报条 -->
          <div v-if="overviewData.processStage === 'FAILED'" class="alert-box">
            <el-alert
              type="error"
              :closable="false"
              show-icon
              title="记账执行发生异常，当前流水处于失败挂起状态"
            >
              <template #default>
                <div class="alert-desc">
                  <div>失败详情：{{ overviewData.transaction?.failReason || '底层凭证或过账校验失败' }}</div>
                  <div class="alert-actions">
                    <el-button
                      v-if="overviewData.canRetry"
                      type="primary"
                      size="small"
                      :icon="Refresh"
                      @click="handleRetry(overviewData.record?.traceNo)"
                    >
                      立即重试记账
                    </el-button>
                    <el-button
                      v-if="overviewData.canRollback"
                      type="danger"
                      size="small"
                      :icon="Back"
                      @click="handleRollback(overviewData.record?.traceNo)"
                    >
                      冲正与回滚
                    </el-button>
                  </div>
                </div>
              </template>
            </el-alert>
          </div>

          <!-- 2. 业务流水要素卡片 -->
          <div v-if="overviewData.record" class="overview-section">
            <div class="section-title">
              <el-icon><Document /></el-icon>
              <span>业务流水要素</span>
              <el-tag
                v-if="overviewData.record.businessCode === 'MANUAL' || overviewData.record.traceNo?.startsWith('MVA')"
                type="info"
                size="small"
                style="margin-left: 10px;"
              >
                手工记账直录凭证
              </el-tag>
            </div>
            <el-descriptions :column="2" border size="small" class="custom-desc">
              <el-descriptions-item label="系统跟踪号">{{ overviewData.record.traceNo }}</el-descriptions-item>
              <el-descriptions-item label="流水序号">{{ overviewData.record.traceSeq || 1 }}</el-descriptions-item>
              <el-descriptions-item label="交易金额">
                <span class="highlight-amount">¥ {{ formatMoney(overviewData.record.amount) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="会计日期">{{ overviewData.record.accountingDate }}</el-descriptions-item>
              <el-descriptions-item label="业务线 / 交易码">
                {{ getBusinessLabel(overviewData.record.businessCode) }} / {{ overviewData.record.tradingCode || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="交易类型">
                <el-tag :type="getTradeTypeTag(overviewData.record.tradeType)" size="small">
                  {{ overviewData.record.tradeTypeDesc || '正常' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="原预冻结流水">
                {{ overviewData.record.origFreezeNo || (overviewData.record.businessCode === 'MANUAL' ? '手工凭证无需冻结' : '无预冻结 (直接记账)') }}
              </el-descriptions-item>
              <el-descriptions-item label="交易时间">{{ overviewData.record.tradeTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="业务摘要" :span="2">{{ overviewData.record.summary || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 3. 关联事务卡片 -->
          <div v-if="overviewData.transaction" class="overview-section">
            <div class="section-title">
              <el-icon><Share /></el-icon>
              <span>账务事务信息</span>
            </div>
            <el-descriptions :column="2" border size="small" class="custom-desc">
              <el-descriptions-item label="全局事务编号">{{ overviewData.transaction.txnNo }}</el-descriptions-item>
              <el-descriptions-item label="事务状态">
                <el-tag :type="getTxStatusTag(overviewData.transaction.status)" size="small">
                  {{ overviewData.transaction.statusDesc }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="涉及分户账户数">{{ overviewData.transaction.relateAccountCount }} 户</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ overviewData.transaction.finishTime || '处理中' }}</el-descriptions-item>
              <el-descriptions-item v-if="overviewData.transaction.failReason" label="失败原因" :span="2">
                <span class="danger-text">{{ overviewData.transaction.failReason }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 4. 关联预冻结明细（如有） -->
          <div v-if="overviewData.freeze" class="overview-section">
            <div class="section-title">
              <el-icon><Lock /></el-icon>
              <span>预冻结/规则冻结明细</span>
            </div>
            <el-descriptions :column="2" border size="small" class="custom-desc">
              <el-descriptions-item label="冻结单号">{{ overviewData.freeze.freezeId }}</el-descriptions-item>
              <el-descriptions-item label="冻结账户">{{ overviewData.freeze.accountNo }}</el-descriptions-item>
              <el-descriptions-item label="当前冻结金额">¥ {{ formatMoney(overviewData.freeze.freezeAmount) }}</el-descriptions-item>
              <el-descriptions-item label="原始冻结金额">¥ {{ formatMoney(overviewData.freeze.origFreezeAmount) }}</el-descriptions-item>
              <el-descriptions-item label="已解冻金额">¥ {{ formatMoney(overviewData.freeze.unfrozenAmount) }}</el-descriptions-item>
              <el-descriptions-item label="累计扣减金额">¥ {{ formatMoney(overviewData.freeze.deductedAmount) }}</el-descriptions-item>
              <el-descriptions-item label="冻结状态">
                <el-tag size="small">{{ overviewData.freeze.statusDesc }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="自动到期解冻时间">{{ overviewData.freeze.expireTime || '长期/手动控制' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 5. 记账凭证与分录明细 -->
          <div class="overview-section">
            <div class="section-title">
              <el-icon><Tickets /></el-icon>
              <span>记账凭证与借贷分录 (Accounting Voucher)</span>
            </div>

            <div v-if="!overviewData.vouchers || overviewData.vouchers.length === 0" class="empty-hint">
              <el-empty description="暂未生成记账凭证" :image-size="80" />
            </div>

            <div v-for="voucher in overviewData.vouchers" :key="voucher.voucherNo" class="voucher-box">
              <div class="voucher-header">
                <div class="voucher-title">
                  <span class="voucher-badge">凭证: {{ voucher.voucherNo }}</span>
                  <el-tag size="small" type="success">{{ voucher.voucherType || '机制凭证' }}</el-tag>
                  <el-tag size="small" :type="voucher.status === 3 ? 'success' : (voucher.status === 4 ? 'danger' : 'warning')">
                    {{ voucher.statusDesc }}
                  </el-tag>
                </div>
                <div class="voucher-amount">
                  总金额: ¥ {{ formatMoney(voucher.amount) }}
                </div>
              </div>

              <!-- 分录明细表 -->
              <el-table :data="voucher.entries" border size="small" class="entry-table">
                <el-table-column prop="rowNum" label="行号" width="60" align="center" />

                <el-table-column prop="debitCredit" label="方向" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.debitCredit === 1 ? 'primary' : 'warning'" size="small">
                      {{ row.debitCredit === 1 ? '借 (Debit)' : '贷 (Credit)' }}
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="subjectCode" label="科目编码" width="130" />

                <el-table-column prop="accountNo" label="分户账号" min-width="150" show-overflow-tooltip />

                <el-table-column prop="amount" label="分录金额" width="120" align="right">
                  <template #default="{ row }">
                    ¥ {{ formatMoney(row.amount) }}
                  </template>
                </el-table-column>

                <el-table-column label="过账通道" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.unilateral === 1" size="small" type="success">实时过账</el-tag>
                    <el-tag v-else-if="row.buffered === 1" size="small" type="warning">缓冲入账</el-tag>
                    <el-tag v-else size="small" type="info">异步入账</el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="status" label="分录状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 2 ? 'success' : (row.status === 3 ? 'danger' : 'info')">
                      {{ row.statusDesc }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <!-- 6. 异步消息与缓冲入账汇总 (如有) -->
          <div v-if="overviewData.postingSummary" class="overview-section">
            <div class="section-title">
              <el-icon><Connection /></el-icon>
              <span>并行/异步/缓冲过账调度监控</span>
            </div>
            <div class="stat-summary-bar">
              <span class="stat-item">实时分录: {{ overviewData.postingSummary.realtimeSuccessCount }}/{{ overviewData.postingSummary.realtimeTotal }}</span>
              <span class="stat-item">异步分录: {{ overviewData.postingSummary.asyncSuccessCount }}/{{ overviewData.postingSummary.asyncTotal }}</span>
              <span class="stat-item">缓冲分录: {{ overviewData.postingSummary.bufferSuccessCount }}/{{ overviewData.postingSummary.bufferTotal }}</span>
            </div>

            <!-- 本地消息列表 -->
            <div v-if="overviewData.postingSummary.asyncMessages && overviewData.postingSummary.asyncMessages.length > 0" class="sub-table-box">
              <div class="sub-table-title">RocketMQ 本地消息投递明细</div>
              <el-table :data="overviewData.postingSummary.asyncMessages" border size="small">
                <el-table-column prop="messageId" label="消息ID" width="160" show-overflow-tooltip />
                <el-table-column prop="topic" label="Topic" width="130" />
                <el-table-column prop="tag" label="Tag" width="100" />
                <el-table-column prop="retryCount" label="重试次数" width="90" align="center" />
                <el-table-column prop="statusDesc" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 2 ? 'success' : (row.status === 3 ? 'danger' : 'warning')">
                      {{ row.statusDesc }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="nextRetryTime" label="下次重试时间" min-width="150" />
              </el-table>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button
            v-if="overviewData?.canRetry"
            type="primary"
            :icon="Refresh"
            @click="handleRetry(overviewData!.record.traceNo)"
          >
            重试记账
          </el-button>
          <el-button
            v-if="overviewData?.canRollback && overviewData?.record?.traceNo"
            type="danger"
            :icon="Back"
            @click="handleRollback(overviewData.record.traceNo)"
          >
            冲账回滚
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  Search,
  RefreshRight,
  Refresh,
  View,
  Back,
  Tickets,
  Clock,
  CircleCheckFilled,
  WarningFilled,
  Document,
  Share,
  Lock,
  Operation,
  Connection
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { toast } from '@/utils/toast'
import {
  getJournalPage,
  getTransactionPage,
  getJournalOverview,
  retryJournal,
  rollbackJournal,
  JOURNAL_STATUS_OPTIONS,
  TRANSACTION_STATUS_OPTIONS,
  JOURNAL_TRADE_TYPE_OPTIONS,
  type JournalRecordItem,
  type TransactionRecordItem,
  type JournalOverviewResponse,
  type JournalPageQueryRequest,
  type TransactionPageQueryRequest
} from '@/api/journal'
import { getDictByType } from '@/api/dict'

// ==================== 业务线字典数据 ====================
const businessCodeOptions = ref<Array<{ label: string; value: string }>>([])
const businessMap = ref<Record<string, string>>({})

async function loadDicts() {
  try {
    let bizRes = await getDictByType('business_code')
    if (!bizRes || bizRes.length === 0) {
      bizRes = await getDictByType('BUSINESS_CODE')
    }
    if (bizRes && bizRes.length > 0) {
      businessCodeOptions.value = bizRes.map(d => {
        businessMap.value[d.dictCode] = d.dictName
        return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
      })
    }
  } catch (err) {
    console.error('加载业务线字典失败', err)
  }
}

function getBusinessLabel(code?: string): string {
  if (!code) return '-'
  return businessMap.value[code] ? `${businessMap.value[code]} (${code})` : code
}

// ==================== 顶部 KPI 计数 ====================
const totalCount = ref(0)
const processingCount = ref(0)
const successCount = ref(0)
const failedCount = ref(0)

// ==================== Active Tab ====================
const activeTab = ref<'journal' | 'transaction'>('journal')

function handleTabChange(tab: string | number) {
  if (tab === 'journal') {
    loadJournalData()
  } else {
    loadTxData()
  }
}

// ==================== Tab 1: 业务流水 ====================
const journalLoading = ref(false)
const journalList = ref<JournalRecordItem[]>([])
const journalTotal = ref(0)
const journalPageNo = ref(1)
const journalPageSize = ref(20)
const journalDateRange = ref<[string, string] | null>(null)

const journalSearchForm = reactive<JournalPageQueryRequest>({
  traceNo: '',
  businessCode: '',
  tradeType: '',
  status: '',
  origFreezeNo: ''
})

async function loadJournalData() {
  journalLoading.value = true
  try {
    const params: JournalPageQueryRequest = {
      pageNo: journalPageNo.value,
      pageSize: journalPageSize.value,
      traceNo: journalSearchForm.traceNo?.trim() || undefined,
      businessCode: journalSearchForm.businessCode?.trim() || undefined,
      tradeType: journalSearchForm.tradeType !== '' ? Number(journalSearchForm.tradeType) : undefined,
      status: journalSearchForm.status !== '' ? Number(journalSearchForm.status) : undefined,
      origFreezeNo: journalSearchForm.origFreezeNo?.trim() || undefined,
      startDate: journalDateRange.value ? journalDateRange.value[0] : undefined,
      endDate: journalDateRange.value ? journalDateRange.value[1] : undefined
    }

    const res = await getJournalPage(params)
    const list = res.list || res.records || []
    journalList.value = list
    journalTotal.value = res.total || 0

    // 更新 KPI 指标
    totalCount.value = res.total || 0
    let proc = 0
    let succ = 0
    let fail = 0
    list.forEach(item => {
      if (item.status === 1) proc++
      else if (item.status === 2) succ++
      else if (item.status === 3) fail++
    })
    processingCount.value = proc
    successCount.value = succ
    failedCount.value = fail
  } catch (err: any) {
    console.error('加载业务流水失败', err)
  } finally {
    journalLoading.value = false
  }
}

function handleSearchJournal() {
  journalPageNo.value = 1
  loadJournalData()
}

function handleResetJournal() {
  journalSearchForm.traceNo = ''
  journalSearchForm.businessCode = ''
  journalSearchForm.tradeType = ''
  journalSearchForm.status = ''
  journalSearchForm.origFreezeNo = ''
  journalDateRange.value = null
  journalPageNo.value = 1
  loadJournalData()
}

// ==================== Tab 2: 账务事务 ====================
const txLoading = ref(false)
const txList = ref<TransactionRecordItem[]>([])
const txTotal = ref(0)
const txPageNo = ref(1)
const txPageSize = ref(20)
const txDateRange = ref<[string, string] | null>(null)

const txSearchForm = reactive<TransactionPageQueryRequest>({
  txnNo: '',
  traceNo: '',
  status: ''
})

async function loadTxData() {
  txLoading.value = true
  try {
    const params: TransactionPageQueryRequest = {
      pageNo: txPageNo.value,
      pageSize: txPageSize.value,
      txnNo: txSearchForm.txnNo?.trim() || undefined,
      traceNo: txSearchForm.traceNo?.trim() || undefined,
      status: txSearchForm.status !== '' ? Number(txSearchForm.status) : undefined,
      startDate: txDateRange.value ? txDateRange.value[0] : undefined,
      endDate: txDateRange.value ? txDateRange.value[1] : undefined
    }

    const res = await getTransactionPage(params)
    txList.value = res.list || res.records || []
    txTotal.value = res.total || 0
  } catch (err: any) {
    console.error('加载账务事务失败', err)
  } finally {
    txLoading.value = false
  }
}

function handleSearchTx() {
  txPageNo.value = 1
  loadTxData()
}

function handleResetTx() {
  txSearchForm.txnNo = ''
  txSearchForm.traceNo = ''
  txSearchForm.status = ''
  txDateRange.value = null
  txPageNo.value = 1
  loadTxData()
}

// ==================== 全景总览抽屉 (Drawer) ====================
const drawerVisible = ref(false)
const overviewLoading = ref(false)
const overviewData = ref<JournalOverviewResponse | null>(null)

async function openOverview(traceNo: string) {
  if (!traceNo) return
  drawerVisible.value = true
  overviewLoading.value = true
  overviewData.value = null
  try {
    const data = await getJournalOverview(traceNo)
    overviewData.value = data
  } catch (err: any) {
    console.error('加载流水全景档案失败', err)
    toast.error('加载流水全景档案失败: ' + (err.message || '系统错误'))
  } finally {
    overviewLoading.value = false
  }
}

// 重试记账
async function handleRetry(traceNo: string) {
  try {
    await ElMessageBox.confirm(`确认针对流水 [${traceNo}] 触发记账重试？将重新生成凭证并执行过账。`, '失败重试确认', {
      type: 'warning',
      confirmButtonText: '立即重试',
      cancelButtonText: '取消'
    })
    const res = await retryJournal(traceNo)
    toast.success('重试记账执行完成')
    if (drawerVisible.value) {
      overviewData.value = res
    }
    loadJournalData()
  } catch (err: any) {
    if (err !== 'cancel') {
      console.error('重试记账失败', err)
    }
  }
}

// 冲账回滚
async function handleRollback(traceNo: string) {
  try {
    const { value: reason } = await ElMessageBox.prompt(`请输入流水 [${traceNo}] 的冲账回滚原因：`, '流水冲账回滚', {
      confirmButtonText: '确认冲正',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：用户撤销交易 / 业务系统冲正'
    })
    const res = await rollbackJournal(traceNo, reason || 'MANUAL_ROLLBACK')
    toast.success('流水冲账回滚完成，已生成反向冲销凭证')
    if (drawerVisible.value) {
      overviewData.value = res
    }
    loadJournalData()
  } catch (err: any) {
    if (err !== 'cancel') {
      console.error('冲账回滚失败', err)
    }
  }
}

// ==================== 辅助格式化函数 ====================
function formatMoney(amount: number | string | undefined | null): string {
  if (amount === undefined || amount === null || amount === '') return '0.00'
  const val = Number(amount)
  if (isNaN(val)) return '0.00'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function getJournalStatusTag(status: number): '' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 1: return 'warning'
    case 2: return 'success'
    case 3: return 'danger'
    default: return ''
  }
}

function getJournalStatusDesc(status: number): string {
  switch (status) {
    case 1: return '处理中'
    case 2: return '记账成功'
    case 3: return '记账失败'
    default: return '未知'
  }
}

function getTxStatusTag(status: number): '' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 1: return 'warning'
    case 2: return 'success'
    case 3: return 'danger'
    default: return ''
  }
}

function getTxStatusDesc(status: number): string {
  switch (status) {
    case 1: return '处理中'
    case 2: return '成功'
    case 3: return '失败'
    default: return '未知'
  }
}

function getTradeTypeTag(type: number): '' | 'primary' | 'warning' | 'danger' | 'info' {
  switch (type) {
    case 1: return 'primary'
    case 2: return 'warning'
    case 3: return 'danger'
    case 4: return 'info'
    case 5: return 'warning'
    case 6: return 'info'
    default: return 'primary'
  }
}

function getStepIndex(stage: string): number {
  switch (stage) {
    case 'RECORDED': return 1
    case 'VOUCHERED': return 2
    case 'POSTING': return 2
    case 'SUCCESS': return 4
    case 'FAILED': return 3
    default: return 1
  }
}

function getStepStatus(stage: string): 'wait' | 'process' | 'finish' | 'error' | 'success' {
  if (stage === 'FAILED') return 'error'
  if (stage === 'SUCCESS') return 'success'
  return 'process'
}

function getStageTag(stage: string): '' | 'warning' | 'success' | 'danger' | 'info' {
  if (stage === 'SUCCESS') return 'success'
  if (stage === 'FAILED') return 'danger'
  if (stage === 'POSTING') return 'warning'
  return 'info'
}

onMounted(() => {
  loadDicts()
  loadJournalData()
})
</script>

<style scoped>
.journal-management-page {
  padding: 16px;
  background-color: var(--el-bg-color-page, #f5f7fa);
  min-height: calc(100vh - 84px);
}

/* KPI 看板网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  padding: 18px 20px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.kpi-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
}

.total-card .kpi-icon-box { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.processing-card .kpi-icon-box { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.success-card .kpi-icon-box { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.failed-card .kpi-icon-box { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }

.kpi-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.kpi-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.kpi-unit {
  font-size: 13px;
  font-weight: normal;
  color: #909399;
}

.kpi-desc {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 2px;
}

.warning-text { color: #e6a23c; }
.success-text { color: #67c23a; }
.danger-text { color: #f56c6c; }

/* 主工作台卡片 */
.main-workspace-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px 20px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.journal-search-form {
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
}

.amount-text {
  font-family: 'Roboto Mono', Monaco, monospace;
  font-weight: 600;
  color: #2c3e50;
}

.freeze-tag {
  background: #fdf6ec;
  color: #e6a23c;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.txn-no-badge {
  font-family: 'Roboto Mono', Monaco, monospace;
  font-weight: 600;
  color: #409eff;
}

.count-badge {
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  color: #606266;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 抽屉样式 */
.drawer-body {
  padding: 10px 16px;
}

.overview-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.section-title .el-icon {
  margin-right: 6px;
  font-size: 18px;
  color: #409eff;
}

.steps-section {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #f0f0f0;
}

.alert-box {
  margin-bottom: 20px;
}

.alert-desc {
  margin-top: 6px;
}

.alert-actions {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.custom-desc {
  margin-bottom: 10px;
}

.highlight-amount {
  font-size: 16px;
  font-weight: bold;
  color: #f56c6c;
}

.voucher-box {
  background: #fcfcfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 16px;
  padding: 12px;
}

.voucher-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.voucher-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.voucher-badge {
  font-family: 'Roboto Mono', Monaco, monospace;
  font-weight: 600;
  color: #303133;
}

.voucher-amount {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.stat-summary-bar {
  display: flex;
  gap: 20px;
  background: #f4f4f5;
  padding: 10px 16px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}

.stat-item {
  font-weight: 600;
}

.sub-table-box {
  margin-top: 12px;
}

.sub-table-title {
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 6px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
