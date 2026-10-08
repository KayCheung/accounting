<template>
  <div class="voucher-management-page">
    <!-- ==================== 顶部 4 维核心资产看板 ==================== -->
    <div class="kpi-grid">
      <div class="kpi-card total-card">
        <div class="kpi-icon-box">
          <el-icon><Document /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">凭证总数</div>
          <div class="kpi-value">{{ totalCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">当前查询范围内的记账凭证</div>
        </div>
      </div>

      <div class="kpi-card pending-card">
        <div class="kpi-icon-box">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">待处理凭证</div>
          <div class="kpi-value warning-text">{{ pendingCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">未过账或正在过账中的凭证</div>
        </div>
      </div>

      <div class="kpi-card posted-card">
        <div class="kpi-icon-box">
          <el-icon><CircleCheckFilled /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">已过账凭证</div>
          <div class="kpi-value success-text">{{ postedCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">账户余额与流水已完成过账</div>
        </div>
      </div>

      <div class="kpi-card reversed-card">
        <div class="kpi-icon-box">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-title">红冲与异常</div>
          <div class="kpi-value danger-text">{{ abnormalCount }} <span class="kpi-unit">笔</span></div>
          <div class="kpi-desc">已红冲冲销或过账失败凭证</div>
        </div>
      </div>
    </div>

    <!-- ==================== 多维组合检索表单 ==================== -->
    <div class="fin-card search-card">
      <el-form :inline="true" :model="searchForm" class="voucher-search-form">
        <el-form-item label="凭证编号">
          <el-input
            v-model="searchForm.voucherNo"
            placeholder="凭证号(支持模糊)"
            clearable
            style="width: 190px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="系统跟踪号">
          <el-input
            v-model="searchForm.traceNo"
            placeholder="TRC开头跟踪号"
            clearable
            style="width: 170px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="全局事务号">
          <el-input
            v-model="searchForm.txnNo"
            placeholder="TXN全局事务号"
            clearable
            style="width: 170px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="凭证状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in VOUCHER_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="凭证类型">
          <el-select
            v-model="searchForm.voucherType"
            placeholder="全部类型"
            clearable
            filterable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in voucherTypeOptions"
              :key="item.dictCode"
              :label="item.dictName"
              :value="item.dictCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="业务线">
          <el-select
            v-model="searchForm.businessCode"
            placeholder="全部业务"
            clearable
            filterable
            style="width: 130px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in businessOptions"
              :key="item.dictCode"
              :label="item.dictName"
              :value="item.dictCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="会计日期">
          <el-date-picker
            v-model="accountingDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="起始日期"
            end-placeholder="截止日期"
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
        row-key="id"
        class="fin-table"
        empty-text="暂无记账凭证数据"
      >
        <!-- 展开行：展示当前凭证下所有的借贷分录明细及平衡校验 -->
        <el-table-column type="expand" width="45">
          <template #default="{ row }">
            <div class="expand-entries-box">
              <div class="expand-header">
                <div class="expand-title">
                  <span class="vou-badge">凭证分录</span>
                  <span class="vou-title-text">【{{ row.voucherNo }}】分录明细清单</span>
                  <el-tag size="small" type="info" class="ml-2">
                    业务: {{ getBusinessName(row.businessCode) }} / {{ row.tradingCode || '-' }}
                  </el-tag>
                </div>
                <div class="expand-balance-stat">
                  <span class="stat-item">
                    借方: <b class="debit-text">{{ row.debitCount || 0 }}</b> 笔 / 
                    <b class="debit-text font-mono">¥ {{ formatAmount(row.debitAmount) }}</b>
                  </span>
                  <span class="stat-separator">|</span>
                  <span class="stat-item">
                    贷方: <b class="credit-text">{{ row.creditCount || 0 }}</b> 笔 / 
                    <b class="credit-text font-mono">¥ {{ formatAmount(row.creditAmount) }}</b>
                  </span>
                  <el-tag
                    :type="row.isBalanced ? 'success' : 'danger'"
                    size="small"
                    effect="dark"
                    class="ml-2"
                  >
                    {{ row.isBalanced ? '⚖️ 借贷严格平衡' : '⚠️ 借贷不平' }}
                  </el-tag>
                </div>
              </div>

              <el-table :data="row.entries || []" border size="small" class="nested-entries-table">
                <el-table-column prop="rowNum" label="行号" width="55" align="center" />
                
                <el-table-column prop="debitCredit" label="借贷" width="80" align="center">
                  <template #default="sub">
                    <el-tag
                      :type="sub.row.debitCredit === 1 ? 'primary' : 'warning'"
                      size="small"
                      effect="dark"
                      class="dc-tag"
                    >
                      {{ sub.row.debitCredit === 1 ? '借 (Debit)' : '贷 (Credit)' }}
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="subjectCode" label="会计科目" min-width="180">
                  <template #default="sub">
                    <span class="code-tag highlight">{{ sub.row.subjectCode }}</span>
                    <span class="subject-name">{{ sub.row.subjectName || '-' }}</span>
                  </template>
                </el-table-column>

                <el-table-column prop="accountNo" label="分户账户编号" min-width="200">
                  <template #default="sub">
                    <div class="account-cell">
                      <span class="font-mono acct-text">{{ sub.row.accountNo || '-' }}</span>
                      <el-button
                        v-if="sub.row.accountNo"
                        link
                        type="primary"
                        :icon="DocumentCopy"
                        class="copy-btn"
                        @click="copyText(sub.row.accountNo, '账户编号')"
                      />
                      <el-button
                        v-if="sub.row.accountNo"
                        link
                        type="success"
                        size="small"
                        class="jump-btn"
                        @click="jumpToBalance(sub.row.accountNo)"
                      >
                        查明细
                      </el-button>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column prop="amount" label="分录金额" min-width="130" align="right">
                  <template #default="sub">
                    <span
                      class="font-mono font-bold"
                      :class="sub.row.debitCredit === 1 ? 'debit-text' : 'credit-text'"
                    >
                      ¥ {{ formatAmount(sub.row.amount) }}
                    </span>
                  </template>
                </el-table-column>

                <el-table-column prop="currency" label="币种" width="65" align="center">
                  <template #default="sub">
                    <span class="text-secondary">{{ sub.row.currency || 'CNY' }}</span>
                  </template>
                </el-table-column>

                <el-table-column label="入账模式" width="130" align="center">
                  <template #default="sub">
                    <el-tag v-if="sub.row.isBuffered" type="warning" size="small" effect="plain">
                      缓冲入账
                    </el-tag>
                    <el-tag v-else-if="sub.row.isUnilateral" type="primary" size="small" effect="plain">
                      实时入账
                    </el-tag>
                    <el-tag v-else type="info" size="small" effect="plain">
                      常规入账
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="summary" label="分录摘要" min-width="160" show-overflow-tooltip />
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column type="index" label="序号" width="50" align="center" />

        <el-table-column prop="voucherNo" label="凭证编号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="code-tag highlight clickable" @click="openDetailDrawer(row.voucherNo)">
              {{ row.voucherNo }}
            </span>
            <el-button
              link
              type="primary"
              :icon="DocumentCopy"
              class="copy-btn"
              @click="copyText(row.voucherNo, '凭证编号')"
            />
            <el-tag v-if="row.origVoucherNo" type="danger" size="small" class="ml-1">
              红冲凭证
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="关联单号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.traceNo" class="ref-no-line">
              <span class="ref-label">流水:</span>
              <span class="font-mono ref-val">{{ row.traceNo }}</span>
              <el-button link type="primary" :icon="DocumentCopy" class="copy-btn" @click="copyText(row.traceNo, '跟踪号')" />
            </div>
            <div v-if="row.txnNo" class="ref-no-line">
              <span class="ref-label">事务:</span>
              <span class="font-mono ref-val">{{ row.txnNo }}</span>
              <el-button link type="primary" :icon="DocumentCopy" class="copy-btn" @click="copyText(row.txnNo, '事务号')" />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="voucherType" label="凭证类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">
              {{ row.voucherTypeName || row.voucherType || '通用凭证' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="tradeType" label="交易类别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getTradeTypeTag(row.tradeType)" size="small">
              {{ row.tradeTypeDesc || getTradeTypeLabel(row.tradeType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="amount" label="凭证金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="font-mono font-bold amount-text">
              ¥ {{ formatAmount(row.amount) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="accountingDate" label="会计日期" width="105" align="center">
          <template #default="{ row }">
            <span class="font-mono">{{ row.accountingDate || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="借贷校验" width="110" align="center">
          <template #default="{ row }">
            <el-tooltip :content="`借: ¥${formatAmount(row.debitAmount)} / 贷: ¥${formatAmount(row.creditAmount)}`" placement="top">
              <el-tag :type="row.isBalanced ? 'success' : 'danger'" size="small" effect="plain">
                {{ row.isBalanced ? '平 (Balanced)' : '不平 (Error)' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="凭证状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small" effect="light">
              {{ row.statusDesc || getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="制单时间" width="155" align="center">
          <template #default="{ row }">
            <span class="font-mono text-secondary">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <!-- 操作列：统一使用 MoreFilled "..." 紧凑下拉菜单 (70px) -->
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click">
              <el-button link type="primary" :icon="MoreFilled" class="more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="Tickets" @click="openPrintModal(row.voucherNo)">
                    凭证印签详情
                  </el-dropdown-item>
                  <el-dropdown-item :icon="View" @click="openDetailDrawer(row.voucherNo)">
                    详情档案
                  </el-dropdown-item>
                  
                  <!-- 手动过账：未过账或失败时 -->
                  <el-dropdown-item
                    v-if="row.status === 1 || row.status === 4"
                    :icon="Money"
                    @click="handleManualPosting(row)"
                  >
                    立即过账
                  </el-dropdown-item>

                  <!-- 凭证红冲：已过账且非红冲凭证且未被冲销 -->
                  <el-dropdown-item
                    v-if="row.status === 3 && row.tradeType !== 3 && !row.origVoucherNo"
                    :icon="WarningFilled"
                    divided
                    @click="openReversalDialog(row)"
                  >
                    凭证红冲
                  </el-dropdown-item>

                  <!-- 原凭证跳转 -->
                  <el-dropdown-item
                    v-if="row.origVoucherNo"
                    :icon="ArrowRight"
                    @click="openDetailDrawer(row.origVoucherNo)"
                  >
                    查看原凭证
                  </el-dropdown-item>

                  <el-dropdown-item :icon="DocumentCopy" divided @click="copyText(row.voucherNo, '凭证编号')">
                    复制凭证号
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页栏 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="searchForm.pageNo"
          v-model:page-size="searchForm.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- ==================== 凭证全景档案抽屉 ==================== -->
    <el-drawer
      v-model="drawerVisible"
      :title="`记账凭证全景档案 · ${currentVoucher?.voucherNo || ''}`"
      size="750px"
      destroy-on-close
      class="voucher-detail-drawer"
    >
      <div v-loading="drawerLoading" class="drawer-body">
        <template v-if="currentVoucher">
          <!-- 抽屉顶部卡片 -->
          <div class="drawer-header-card">
            <div class="drawer-header-left">
              <div class="header-vou-no">
                <span class="label">凭证号:</span>
                <span class="font-mono no-text">{{ currentVoucher.voucherNo }}</span>
                <el-button
                  link
                  type="primary"
                  :icon="DocumentCopy"
                  class="copy-btn"
                  @click="copyText(currentVoucher.voucherNo, '凭证编号')"
                />
              </div>
              <div class="header-tags">
                <el-tag :type="getStatusTag(currentVoucher.status)" size="small">
                  {{ currentVoucher.statusDesc || getStatusLabel(currentVoucher.status) }}
                </el-tag>
                <el-tag :type="getTradeTypeTag(currentVoucher.tradeType)" size="small" class="ml-2">
                  {{ currentVoucher.tradeTypeDesc || getTradeTypeLabel(currentVoucher.tradeType) }}
                </el-tag>
                <el-tag size="small" type="info" class="ml-2">
                  {{ currentVoucher.voucherTypeName || currentVoucher.voucherType || '通用' }}
                </el-tag>
                <el-tag
                  :type="currentVoucher.isBalanced ? 'success' : 'danger'"
                  size="small"
                  effect="dark"
                  class="ml-2"
                >
                  {{ currentVoucher.isBalanced ? '⚖️ 借贷严格平衡' : '⚠️ 借贷不平' }}
                </el-tag>
              </div>
            </div>

            <div class="drawer-header-right">
              <div class="amount-label">凭证总金额</div>
              <div class="amount-val font-mono">¥ {{ formatAmount(currentVoucher.amount) }}</div>
            </div>
          </div>

          <!-- 抽屉 Tab 栏 -->
          <el-tabs v-model="activeDrawerTab" class="voucher-drawer-tabs">
            <!-- Tab 1: 凭证分录与凭证头 -->
            <el-tab-pane label="凭证分录与基本信息" name="entries">
              <!-- 借贷平衡核验看板 -->
              <div class="balance-check-bar">
                <div class="balance-box debit-box">
                  <span class="box-label">借方合计 (Debit)</span>
                  <span class="box-value font-mono">¥ {{ formatAmount(currentVoucher.debitAmount) }}</span>
                  <span class="box-sub">共 {{ currentVoucher.debitCount || 0 }} 笔分录</span>
                </div>
                <div class="balance-equal-sign">
                  {{ currentVoucher.isBalanced ? '=' : '≠' }}
                </div>
                <div class="balance-box credit-box">
                  <span class="box-label">贷方合计 (Credit)</span>
                  <span class="box-value font-mono">¥ {{ formatAmount(currentVoucher.creditAmount) }}</span>
                  <span class="box-sub">共 {{ currentVoucher.creditCount || 0 }} 笔分录</span>
                </div>
                <div class="balance-status-box" :class="currentVoucher.isBalanced ? 'balanced' : 'unbalanced'">
                  <el-icon v-if="currentVoucher.isBalanced"><CircleCheckFilled /></el-icon>
                  <el-icon v-else><WarningFilled /></el-icon>
                  <span>{{ currentVoucher.isBalanced ? '平衡校验通过' : '试算不平' }}</span>
                </div>
              </div>

              <!-- 凭证属性 Descriptions -->
              <el-descriptions :column="2" border size="small" class="detail-descriptions">
                <el-descriptions-item label="系统跟踪号">
                  <span class="font-mono">{{ currentVoucher.traceNo || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="全局事务号">
                  <span class="font-mono">{{ currentVoucher.txnNo || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="业务线编码">
                  <span>{{ getBusinessName(currentVoucher.businessCode) }} ({{ currentVoucher.businessCode || '-' }})</span>
                </el-descriptions-item>
                <el-descriptions-item label="交易编码">
                  <span>{{ currentVoucher.tradingCode || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="入账类型">
                  <span>{{ currentVoucher.postingTypeDesc || (currentVoucher.postingType === 1 ? '手工凭证' : '机制凭证') }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="会计日期">
                  <span class="font-mono">{{ currentVoucher.accountingDate || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="制单人">
                  <span>{{ currentVoucher.bookkeeperName || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="复核人">
                  <span>{{ currentVoucher.reviewerName || '-' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="制单时间">
                  <span class="font-mono">{{ formatDateTime(currentVoucher.createTime) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="过账时间">
                  <span class="font-mono">{{ formatDateTime(currentVoucher.postTime) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="凭证摘要" :span="2">
                  <span>{{ currentVoucher.summary || '-' }}</span>
                </el-descriptions-item>
              </el-descriptions>

              <!-- 分录明细列表 -->
              <div class="sub-section-title">借贷分录明细</div>
              <el-table :data="currentVoucher.entries || []" border size="small" class="drawer-entries-table">
                <el-table-column prop="rowNum" label="行号" width="55" align="center" />
                <el-table-column prop="debitCredit" label="借贷" width="80" align="center">
                  <template #default="sub">
                    <el-tag :type="sub.row.debitCredit === 1 ? 'primary' : 'warning'" size="small" effect="dark">
                      {{ sub.row.debitCredit === 1 ? '借' : '贷' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="subjectCode" label="科目" min-width="160">
                  <template #default="sub">
                    <span class="code-tag highlight">{{ sub.row.subjectCode }}</span>
                    <span class="subject-name">{{ sub.row.subjectName || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="accountNo" label="分户账号" min-width="190">
                  <template #default="sub">
                    <span class="font-mono">{{ sub.row.accountNo }}</span>
                    <el-button
                      v-if="sub.row.accountNo"
                      link
                      type="success"
                      size="small"
                      class="ml-1"
                      @click="jumpToBalance(sub.row.accountNo)"
                    >
                      查余额
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="金额" min-width="120" align="right">
                  <template #default="sub">
                    <span
                      class="font-mono font-bold"
                      :class="sub.row.debitCredit === 1 ? 'debit-text' : 'credit-text'"
                    >
                      ¥ {{ formatAmount(sub.row.amount) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="summary" label="摘要" min-width="140" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>

            <!-- Tab 2: 辅助核算分摊项 -->
            <el-tab-pane label="辅助核算分摊项" name="auxiliaries">
              <div v-if="!currentVoucher.auxiliaries || currentVoucher.auxiliaries.length === 0" class="empty-aux-box">
                <el-empty description="当前凭证无辅助核算分摊项" :image-size="80" />
              </div>
              <div v-else>
                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  class="mb-3"
                  title="辅助核算按部门、项目、客户或供应商维度对会计分录进行精细化成本与效益分摊归集"
                />
                <el-table :data="currentVoucher.auxiliaries" border size="small" class="aux-table">
                  <el-table-column type="index" label="序号" width="50" align="center" />
                  <el-table-column prop="entryId" label="分录流水号" min-width="160" show-overflow-tooltip>
                    <template #default="{ row }">
                      <span class="font-mono text-secondary">{{ row.entryId }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="subjectCode" label="科目编码" width="100" align="center">
                    <template #default="{ row }">
                      <span class="code-tag">{{ row.subjectCode }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="auxType" label="核算类型" width="110" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="primary">{{ row.auxType }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="auxCode" label="项目编码" width="110">
                    <template #default="{ row }">
                      <span class="font-mono">{{ row.auxCode }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="auxName" label="项目名称" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="changeDirection" label="方向" width="70" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.changeDirection === 1 ? 'success' : 'danger'" size="small">
                        {{ row.changeDirection === 1 ? '增' : '减' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="amount" label="分摊金额" min-width="110" align="right">
                    <template #default="{ row }">
                      <span class="font-mono font-bold">¥ {{ formatAmount(row.amount) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- Tab 3: 原始审计报文与溯源 -->
            <el-tab-pane label="审计链路与原始报文" name="audit">
              <div class="audit-card-container">
                <el-descriptions :column="1" border size="small" class="mb-3">
                  <el-descriptions-item label="原凭证关联">
                    <span v-if="currentVoucher.origVoucherNo">
                      <el-button link type="primary" @click="openDetailDrawer(currentVoucher.origVoucherNo)">
                        {{ currentVoucher.origVoucherNo }}
                      </el-button>
                      <span class="text-secondary ml-2">(当前凭证由此原凭证红冲生成)</span>
                    </span>
                    <span v-else class="text-secondary">无关联原凭证 (常规记账凭证)</span>
                  </el-descriptions-item>

                  <el-descriptions-item label="红冲冲正记录">
                    <span v-if="currentVoucher.reversalVoucherNo">
                      <el-button link type="danger" @click="openDetailDrawer(currentVoucher.reversalVoucherNo)">
                        {{ currentVoucher.reversalVoucherNo }}
                      </el-button>
                      <span class="text-secondary ml-2">(该凭证已被红冲冲销)</span>
                    </span>
                    <span v-else class="text-secondary">未红冲</span>
                  </el-descriptions-item>

                  <el-descriptions-item v-if="currentVoucher.failReason" label="过账失败原因">
                    <span class="danger-text">{{ currentVoucher.failReason }}</span>
                  </el-descriptions-item>
                </el-descriptions>

                <div class="json-header-bar">
                  <span class="json-title">全景凭证完整 JSON 数据模型</span>
                  <el-button
                    size="small"
                    :icon="DocumentCopy"
                    @click="copyText(JSON.stringify(currentVoucher, null, 2), 'JSON报文')"
                  >
                    复制完整 JSON
                  </el-button>
                </div>
                <pre class="json-box">{{ JSON.stringify(currentVoucher, null, 2) }}</pre>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button
            :icon="Printer"
            type="primary"
            plain
            @click="openPrintModal(currentVoucher?.voucherNo)"
          >
            打印凭证印签
          </el-button>
          <el-button
            v-if="currentVoucher && (currentVoucher.status === 1 || currentVoucher.status === 4)"
            type="primary"
            :icon="Money"
            @click="handleManualPosting(currentVoucher)"
          >
            立即执行过账
          </el-button>
          <el-button
            v-if="currentVoucher && currentVoucher.status === 3 && currentVoucher.canReversal"
            type="danger"
            :icon="WarningFilled"
            @click="openReversalDialog(currentVoucher)"
          >
            红冲此凭证
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- ==================== 凭证红冲操作弹窗 ==================== -->
    <el-dialog
      v-model="reversalDialogVisible"
      title="记账凭证红冲 (冲账冲正)"
      width="540px"
      destroy-on-close
      append-to-body
      class="reversal-dialog"
    >
      <el-alert
        type="error"
        :closable="false"
        show-icon
        class="reversal-warning-alert mb-3"
        title="⚠️ 财务不可逆警示"
        description="凭证红冲属于法定财务冲销操作！确认后将自动生成方向相反、金额相同的负向红冲凭证并实时更新账户余额，请务必核实冲正原因。"
      />

      <el-form
        ref="reversalFormRef"
        :model="reversalForm"
        :rules="reversalRules"
        label-width="110px"
        class="reversal-form"
      >
        <el-form-item label="原凭证编号">
          <span class="font-mono text-bold highlight">{{ reversalForm.origVoucherNo }}</span>
        </el-form-item>

        <el-form-item label="凭证总金额">
          <span class="font-mono text-bold amount-text">¥ {{ formatAmount(reversalTarget?.amount) }}</span>
        </el-form-item>

        <el-form-item label="会计日期">
          <span class="font-mono">{{ reversalTarget?.accountingDate || '-' }}</span>
        </el-form-item>

        <el-form-item label="红冲记账人" prop="bookkeeperName">
          <el-input
            v-model="reversalForm.bookkeeperName"
            placeholder="请输入执行红冲的财务人员姓名"
            maxlength="32"
          />
        </el-form-item>

        <el-form-item label="红冲原因摘要" prop="summary">
          <el-input
            v-model="reversalForm.summary"
            type="textarea"
            :rows="3"
            placeholder="请详细说明红冲冲销原因（如：交易取消、手工录入差错等）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reversalDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="reversalSubmitting" @click="submitReversal">
          确认执行红冲
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗：经典纸质凭证仿真印签与打印 (无审批流) ==================== -->
    <el-dialog
      v-model="printModalVisible"
      title="记账凭证印签详情与打印"
      width="960px"
      destroy-on-close
      class="classic-voucher-dialog"
    >
      <div v-loading="printModalLoading" v-if="printVoucherData" class="voucher-preview-container print-area">
        <!-- 经典纸质凭证仿真卡片 (无审批流时间轴) -->
        <div class="classic-voucher-sheet">
          <!-- 凭证主标题（自适应类型，大字间距舒展） -->
          <div class="voucher-header-title">
            <h2>{{ getVoucherPrintTitle(printVoucherData.voucherTitle || printVoucherData.voucherTypeName || printVoucherData.voucherType) }}</h2>
            <div class="voucher-title-underline"></div>
          </div>

          <!-- 凭证元数据栏 -->
          <div class="voucher-meta-bar">
            <div class="meta-date">
              {{ formatVoucherDate(printVoucherData.accountingDate) }}
            </div>
            <div class="meta-no">
              <strong>凭证字号：</strong>
              <span class="mono-font">{{ printVoucherData.voucherWord || printVoucherData.voucherNo }}</span>
            </div>
            <div class="meta-attachment">
              附单据 <span class="mono-font underline">{{ printVoucherData.attachmentCount || printVoucherData.attachments?.length || 0 }}</span> 张
            </div>
          </div>

          <!-- 借贷分录对照表格与右外侧属性竖标 -->
          <div class="voucher-table-wrapper">
            <table class="voucher-entries-table">
              <thead>
                <tr>
                  <th style="width: 50px">行号</th>
                  <th style="width: 220px">摘 &nbsp; 要</th>
                  <th style="min-width: 260px">会计科目 / 账户编号</th>
                  <th style="width: 140px" class="text-right">借方金额 (DEBIT)</th>
                  <th style="width: 140px" class="text-right">贷方金额 (CREDIT)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in printVoucherData.entries" :key="item.entryId || item.rowNum">
                  <td class="text-center">{{ item.rowNum }}</td>
                  <td>{{ item.summary }}</td>
                  <td>
                    <div class="subject-title">({{ item.subjectCode }}) {{ item.subjectName }}</div>
                    <div v-if="item.accountNo" class="account-sub mono-font">账号: {{ item.accountNo }}</div>
                  </td>
                  <td class="text-right mono-font">
                    {{ item.debitCredit === 1 ? formatAmount(item.amount) : '' }}
                  </td>
                  <td class="text-right mono-font">
                    {{ item.debitCredit === 2 ? formatAmount(item.amount) : '' }}
                  </td>
                </tr>
                <!-- 空行补齐 -->
                <tr v-if="!printVoucherData.entries || printVoucherData.entries.length < 3">
                  <td class="text-center">-</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
              </tbody>
              <!-- 合计行 -->
              <tfoot>
                <tr class="total-row">
                  <td colspan="2" class="text-center font-bold">合计金额</td>
                  <td class="chinese-total">
                    <span>人民币 (大写): </span>
                    <strong>{{ printVoucherData.totalAmountInWords || formatAmountToChinese(printVoucherData.amount) }}</strong>
                  </td>
                  <td class="text-right mono-font font-bold">
                    ¥ {{ formatAmount(printVoucherData.debitAmount || printVoucherData.amount) }}
                  </td>
                  <td class="text-right mono-font font-bold">
                    ¥ {{ formatAmount(printVoucherData.creditAmount || printVoucherData.amount) }}
                  </td>
                </tr>
              </tfoot>
            </table>
            <!-- 表格最右侧外边竖排标志（黑色字体，无边框，每行一字） -->
            <div class="voucher-side-mark">
              {{ printVoucherData.postingType === 1 ? '手工凭证' : '机制凭证' }}
            </div>
          </div>

          <!-- 四方印签栏 -->
          <div class="voucher-signatures-bar">
            <div class="signature-col">
              <span class="sig-label">财务主管：</span>
              <span class="sig-val">—</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">复 &nbsp; 核：</span>
              <span class="sig-val">{{ printVoucherData.reviewerName || '—' }}</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">记 &nbsp; 账：</span>
              <span class="sig-val font-bold text-primary">{{ printVoucherData.bookkeeperName || '—' }}</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">制 &nbsp; 单：</span>
              <span class="sig-val">{{ printVoucherData.postingType === 1 ? (printVoucherData.bookkeeperName || '手工制单') : '系统自动' }}</span>
            </div>
          </div>
        </div>

        <!-- 辅助核算项表格 (若存在) -->
        <div v-if="printVoucherData.auxiliaries && printVoucherData.auxiliaries.length > 0" class="auxiliary-section mt-4">
          <div class="section-title mb-2">
            <span class="font-bold">辅助核算项 (Auxiliary Items)</span>
          </div>
          <table class="voucher-entries-table classic-aux-table">
            <thead>
              <tr>
                <th style="width: 55px" class="text-center">序号</th>
                <th style="width: 110px" class="text-center">关联分录</th>
                <th style="min-width: 180px">关联会计科目</th>
                <th style="width: 130px" class="text-center">辅助核算类别</th>
                <th style="min-width: 180px">辅助核算项目</th>
                <th style="width: 80px" class="text-center">方向</th>
                <th style="width: 130px" class="text-right">核算金额 (¥)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(aux, idx) in printVoucherData.auxiliaries" :key="aux.entryId || idx">
                <td class="text-center mono-font">{{ idx + 1 }}</td>
                <td class="text-center">{{ aux.entryId ? aux.entryId.substring(aux.entryId.length - 4) : '-' }}</td>
                <td>{{ aux.subjectCode }}</td>
                <td class="text-center">
                  <el-tag size="small" effect="plain">{{ aux.auxType }}</el-tag>
                </td>
                <td class="font-bold">
                  {{ aux.auxName }} <span class="text-muted mono-font">({{ aux.auxCode }})</span>
                </td>
                <td class="text-center">
                  <el-tag size="small" :type="aux.changeDirection === 2 ? 'warning' : 'success'">
                    {{ aux.changeDirection === 2 ? '减少' : '增加' }}
                  </el-tag>
                </td>
                <td class="text-right mono-font font-bold">¥ {{ formatAmount(aux.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <template #footer>
        <el-button @click="printModalVisible = false">关闭</el-button>
        <el-button type="primary" :icon="Printer" @click="handlePrintVoucher">打印凭证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Search, RefreshRight, Refresh, DocumentCopy,
  View, Money, MoreFilled, WarningFilled,
  CircleCheckFilled, Document, Clock, ArrowRight,
  Tickets, Printer
} from '@element-plus/icons-vue'
import { formatAmount } from '@/utils/amount'
import {
  getVoucherPage,
  getVoucherDetail,
  executePosting,
  executeReversal,
  VOUCHER_STATUS_OPTIONS,
  type VoucherPageItem,
  type VoucherFullDetail,
  type VoucherPageQueryRequest
} from '@/api/voucher'
import { getDictByType, type DictResponse } from '@/api/dict'

const route = useRoute()
const router = useRouter()

// 检索表单
const searchForm = reactive<VoucherPageQueryRequest>({
  pageNo: 1,
  pageSize: 10,
  voucherNo: '',
  traceNo: '',
  txnNo: '',
  status: undefined,
  voucherType: '',
  businessCode: '',
  startDate: '',
  endDate: ''
})

const accountingDateRange = ref<[string, string] | null>(null)
const loading = ref(false)
const tableData = ref<VoucherPageItem[]>([])
const total = ref(0)

// 字典列表
const voucherTypeOptions = ref<DictResponse[]>([])
const businessOptions = ref<DictResponse[]>([])

// 统计 KPI
const totalCount = computed(() => total.value)
const pendingCount = computed(() => {
  return tableData.value.filter(v => v.status === 1 || v.status === 2).length
})
const postedCount = computed(() => {
  return tableData.value.filter(v => v.status === 3).length
})
const abnormalCount = computed(() => {
  return tableData.value.filter(v => v.status === 4 || v.status === 5 || v.tradeType === 3).length
})

// 抽屉详情
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const currentVoucher = ref<VoucherFullDetail | null>(null)
const activeDrawerTab = ref('entries')

// 凭证仿真印签与打印弹窗
const printModalVisible = ref(false)
const printModalLoading = ref(false)
const printVoucherData = ref<VoucherFullDetail | null>(null)

async function openPrintModal(voucherNo?: string) {
  if (!voucherNo) return
  printModalVisible.value = true
  printModalLoading.value = true
  try {
    const res = await getVoucherDetail(voucherNo)
    printVoucherData.value = res
  } catch (error) {
    ElMessage.error('获取凭证印签详情失败')
  } finally {
    printModalLoading.value = false
  }
}

function handlePrintVoucher() {
  window.print()
}

function formatVoucherDate(dateStr?: string) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    return `${parts[0]} 年 ${parts[1]} 月 ${parts[2]} 日`
  }
  return dateStr
}

function getVoucherPrintTitle(val?: string): string {
  let raw = val || '记账凭证'
  if (raw === 'RECEIPT' || raw.includes('收款')) raw = '收款凭证'
  else if (raw === 'PAYMENT' || raw.includes('付款')) raw = '付款凭证'
  else if (raw === 'TRANSFER' || raw.includes('转账')) raw = '转账凭证'
  else if (raw === 'ADJUST' || raw.includes('调账')) raw = '调账凭证'
  else if (raw === 'REVERSAL' || raw.includes('冲')) raw = '冲账凭证'
  else if (raw === 'PERIOD_END' || raw.includes('结')) raw = '期末结转凭证'
  else if (raw === 'GENERAL') raw = '记账凭证'
  return raw.split('').join('  ')
}

function formatAmountToChinese(num?: number): string {
  if (num === null || num === undefined) return '零元整'
  return `${formatAmount(num)} 元整`
}

// 红冲弹窗
const reversalDialogVisible = ref(false)
const reversalSubmitting = ref(false)
const reversalTarget = ref<VoucherPageItem | null>(null)
const reversalFormRef = ref<FormInstance>()
const reversalForm = reactive({
  origVoucherNo: '',
  bookkeeperName: '系统操作员',
  summary: ''
})

const reversalRules: FormRules = {
  bookkeeperName: [{ required: true, message: '请输入红冲人姓名', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入红冲原因摘要', trigger: 'blur' }]
}

// 加载字典
async function loadDicts() {
  try {
    const [voucherTypes, businesses] = await Promise.all([
      getDictByType('voucher_type').catch(() => []),
      getDictByType('business_code').catch(() => [])
    ])
    voucherTypeOptions.value = voucherTypes || []
    businessOptions.value = businesses || []
  } catch {
    // 忽略字典错误
  }
}

function getBusinessName(code?: string): string {
  if (!code) return '-'
  const found = businessOptions.value.find(b => b.dictCode === code)
  return found ? found.dictName : code
}

// 查询列表
async function fetchData() {
  loading.value = true
  try {
    const res = await getVoucherPage({
      pageNo: searchForm.pageNo,
      pageSize: searchForm.pageSize,
      voucherNo: searchForm.voucherNo ? searchForm.voucherNo.trim() : undefined,
      traceNo: searchForm.traceNo ? searchForm.traceNo.trim() : undefined,
      txnNo: searchForm.txnNo ? searchForm.txnNo.trim() : undefined,
      status: searchForm.status || undefined,
      voucherType: searchForm.voucherType || undefined,
      businessCode: searchForm.businessCode || undefined,
      startDate: searchForm.startDate || undefined,
      endDate: searchForm.endDate || undefined
    })
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch (err: any) {
    ElMessage.error(err?.message || '获取凭证列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchForm.pageNo = 1
  fetchData()
}

function handleReset() {
  searchForm.voucherNo = ''
  searchForm.traceNo = ''
  searchForm.txnNo = ''
  searchForm.status = undefined
  searchForm.voucherType = ''
  searchForm.businessCode = ''
  searchForm.startDate = ''
  searchForm.endDate = ''
  accountingDateRange.value = null
  searchForm.pageNo = 1
  fetchData()
}

function handleDateRangeChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    searchForm.startDate = val[0]
    searchForm.endDate = val[1]
  } else {
    searchForm.startDate = ''
    searchForm.endDate = ''
  }
  handleSearch()
}

function handleSizeChange(size: number) {
  searchForm.pageSize = size
  searchForm.pageNo = 1
  fetchData()
}

function handleCurrentChange(page: number) {
  searchForm.pageNo = page
  fetchData()
}

// 打开全景档案抽屉
async function openDetailDrawer(voucherNo: string) {
  if (!voucherNo) return
  drawerVisible.value = true
  drawerLoading.value = true
  activeDrawerTab.value = 'entries'
  try {
    const detail = await getVoucherDetail(voucherNo)
    currentVoucher.value = detail
  } catch (err: any) {
    ElMessage.error(err?.message || '获取凭证全景详情失败')
  } finally {
    drawerLoading.value = false
  }
}

// 手动过账
function handleManualPosting(row: VoucherPageItem | VoucherFullDetail) {
  ElMessageBox.confirm(
    `确认对凭证【${row.voucherNo}】执行过账处理？过账将更新对应账户余额。`,
    '过账确认',
    {
      confirmButtonText: '确定过账',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await executePosting({ voucherNo: row.voucherNo })
      ElMessage.success(`凭证【${row.voucherNo}】过账成功`)
      fetchData()
      if (drawerVisible.value && currentVoucher.value?.voucherNo === row.voucherNo) {
        openDetailDrawer(row.voucherNo)
      }
    } catch (err: any) {
      ElMessage.error(err?.message || '过账失败')
    }
  }).catch(() => {})
}

// 打开红冲弹窗
function openReversalDialog(row: VoucherPageItem | VoucherFullDetail) {
  reversalTarget.value = row as VoucherPageItem
  reversalForm.origVoucherNo = row.voucherNo
  reversalForm.bookkeeperName = '系统操作员'
  reversalForm.summary = ''
  reversalDialogVisible.value = true
}

// 提交红冲
async function submitReversal() {
  if (!reversalFormRef.value) return
  await reversalFormRef.value.validate(async (valid) => {
    if (!valid) return
    reversalSubmitting.value = true
    try {
      const res = await executeReversal({
        origVoucherNo: reversalForm.origVoucherNo,
        bookkeeperName: reversalForm.bookkeeperName.trim(),
        summary: reversalForm.summary.trim()
      })
      const newVoucherNo = res?.reversalVoucherNo || ''
      ElMessage.success({
        message: `凭证红冲成功！已生成冲销凭证【${newVoucherNo}】`,
        duration: 5000
      })
      reversalDialogVisible.value = false
      if (drawerVisible.value) {
        drawerVisible.value = false
      }
      fetchData()
    } catch (err: any) {
      ElMessage.error(err?.message || '凭证红冲执行失败')
    } finally {
      reversalSubmitting.value = false
    }
  })
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

// 状态标签
function getStatusTag(status?: number): '' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 1: return 'info'
    case 2: return 'warning'
    case 3: return 'success'
    case 4: return 'danger'
    case 5: return 'info'
    default: return 'info'
  }
}

function getStatusLabel(status?: number): string {
  switch (status) {
    case 1: return '未过账'
    case 2: return '过账中'
    case 3: return '已过账'
    case 4: return '过账失败'
    case 5: return '已冲销'
    default: return '未知'
  }
}

// 交易类别标签
function getTradeTypeTag(type?: number): '' | 'primary' | 'warning' | 'info' | 'danger' {
  switch (type) {
    case 1: return 'primary'
    case 2: return 'warning'
    case 3: return 'danger'
    case 4: return 'info'
    default: return 'info'
  }
}

function getTradeTypeLabel(type?: number): string {
  switch (type) {
    case 1: return '正常'
    case 2: return '调账'
    case 3: return '红冲'
    case 4: return '蓝补'
    default: return '正常'
  }
}

function formatDateTime(val?: string): string {
  if (!val || val.startsWith('1970-01-01')) return '-'
  return val.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  loadDicts()

  // 检查是否从外部路由跳转带来参数
  if (route.query.voucherNo) {
    searchForm.voucherNo = String(route.query.voucherNo).trim()
  }
  if (route.query.traceNo) {
    searchForm.traceNo = String(route.query.traceNo).trim()
  }

  fetchData()

  // 如果路由指定了明确凭证号，自动展开抽屉
  if (route.query.voucherNo) {
    openDetailDrawer(String(route.query.voucherNo).trim())
  }
})
</script>

<style scoped>
.voucher-management-page {
  padding: 16px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 84px);
}

/* 4 维 KPI 看板 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #ebeef5;
  transition: all 0.25s ease;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.kpi-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.total-card .kpi-icon-box {
  background: #ecf5ff;
  color: #409eff;
}

.pending-card .kpi-icon-box {
  background: #fdf6ec;
  color: #e6a23c;
}

.posted-card .kpi-icon-box {
  background: #f0f9eb;
  color: #67c23a;
}

.reversed-card .kpi-icon-box {
  background: #fef0f0;
  color: #f56c6c;
}

.kpi-content {
  flex: 1;
}

.kpi-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  font-family: 'Roboto Mono', 'Courier New', monospace;
  color: #303133;
}

.kpi-unit {
  font-size: 12px;
  font-weight: normal;
  color: #909399;
  margin-left: 2px;
}

.kpi-desc {
  font-size: 11px;
  color: #a8abb2;
  margin-top: 4px;
}

.warning-text { color: #e6a23c; }
.success-text { color: #67c23a; }
.danger-text { color: #f56c6c; }
.debit-text { color: #409eff; }
.credit-text { color: #e6a23c; }

/* 卡片容器 */
.fin-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px 20px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
}

.search-card {
  padding-bottom: 2px;
}

.voucher-search-form :deep(.el-form-item) {
  margin-bottom: 14px;
  margin-right: 16px;
}

/* 表格与展开行样式 */
.fin-table {
  width: 100%;
}

.font-mono {
  font-family: 'Roboto Mono', 'Courier New', monospace;
}

.font-bold {
  font-weight: 600;
}

.code-tag {
  background: #f4f4f5;
  color: #606266;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Roboto Mono', monospace;
  font-size: 12px;
}

.code-tag.highlight {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.code-tag.clickable {
  cursor: pointer;
}

.code-tag.clickable:hover {
  text-decoration: underline;
}

.subject-name {
  margin-left: 6px;
  font-size: 12px;
  color: #606266;
}

.copy-btn {
  padding: 0 4px;
  margin-left: 2px;
  vertical-align: middle;
}

.ref-no-line {
  display: flex;
  align-items: center;
  line-height: 1.6;
  font-size: 12px;
}

.ref-label {
  color: #909399;
  margin-right: 4px;
  font-size: 11px;
}

.ref-val {
  color: #606266;
}

.amount-text {
  font-size: 13px;
  color: #303133;
}

.more-btn {
  font-size: 16px;
  padding: 4px;
}

/* 展开行嵌套分录 */
.expand-entries-box {
  background: #f8fafc;
  padding: 12px 16px;
  border-radius: 6px;
  margin: 6px 12px;
  border: 1px dashed #dcdfe6;
}

.expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.expand-title {
  display: flex;
  align-items: center;
}

.vou-badge {
  background: #409eff;
  color: #ffffff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  margin-right: 6px;
}

.vou-title-text {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.expand-balance-stat {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #606266;
}

.stat-item {
  margin: 0 4px;
}

.stat-separator {
  margin: 0 8px;
  color: #dcdfe6;
}

.nested-entries-table {
  background: #ffffff;
}

.account-cell {
  display: flex;
  align-items: center;
}

.acct-text {
  font-size: 12px;
}

.jump-btn {
  margin-left: 6px;
  font-size: 11px;
  padding: 0 4px;
}

.dc-tag {
  font-size: 11px;
  padding: 0 4px;
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 全景档案抽屉样式 */
.drawer-body {
  padding: 0 8px;
}

.drawer-header-card {
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-vou-no {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.header-vou-no .label {
  font-size: 13px;
  color: #909399;
  margin-right: 8px;
}

.header-vou-no .no-text {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.drawer-header-right {
  text-align: right;
}

.drawer-header-right .amount-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.drawer-header-right .amount-val {
  font-size: 22px;
  font-weight: 700;
  color: #f56c6c;
}

.balance-check-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 16px;
}

.balance-box {
  display: flex;
  flex-direction: column;
}

.box-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.box-value {
  font-size: 16px;
  font-weight: 700;
}

.box-sub {
  font-size: 11px;
  color: #a8abb2;
  margin-top: 2px;
}

.debit-box .box-value {
  color: #409eff;
}

.credit-box .box-value {
  color: #e6a23c;
}

.balance-equal-sign {
  font-size: 20px;
  font-weight: 700;
  color: #909399;
}

.balance-status-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 6px;
}

.balance-status-box.balanced {
  background: #f0f9eb;
  color: #67c23a;
}

.balance-status-box.unbalanced {
  background: #fef0f0;
  color: #f56c6c;
}

.detail-descriptions {
  margin-bottom: 16px;
}

.sub-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 14px 0 8px 0;
}

.drawer-entries-table {
  margin-bottom: 16px;
}

.empty-aux-box {
  padding: 40px 0;
}

.aux-table {
  margin-top: 10px;
}

.json-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.json-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.json-box {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 14px;
  border-radius: 6px;
  font-family: 'Roboto Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 320px;
  overflow-y: auto;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 红冲弹窗 */
.reversal-dialog :deep(.el-alert__description) {
  font-size: 12px;
  line-height: 1.5;
}

/* 经典纸质凭证仿真印签与打印卡片 */
.classic-voucher-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
  background-color: #f7f8fa;
}

.voucher-preview-container {
  display: flex;
  flex-direction: column;
}

.classic-voucher-sheet {
  background: #ffffff;
  padding: 24px 28px;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  border: 1px solid #dcdfe6;
}

.voucher-header-title {
  text-align: center;
  margin-bottom: 12px;
}

.voucher-header-title h2 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  margin: 0;
  color: #1a1a1a;
}

.voucher-title-underline {
  width: 180px;
  height: 2px;
  background: #1a1a1a;
  margin: 4px auto 0;
}

.voucher-meta-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  margin-bottom: 10px;
  padding: 0 4px;
}

.meta-date {
  font-weight: 600;
}

.meta-no {
  font-weight: 500;
}

.meta-attachment .underline {
  text-decoration: underline;
  padding: 0 4px;
}

.voucher-table-wrapper {
  position: relative;
  width: 100%;
}

.voucher-entries-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #333333;
  font-size: 13px;
}

.voucher-side-mark {
  position: absolute;
  left: calc(100% + 6px);
  top: 50%;
  transform: translateY(-50%);
  writing-mode: vertical-rl;
  text-orientation: upright;
  letter-spacing: 6px;
  font-size: 12px;
  font-weight: normal;
  color: #333333;
  line-height: 1;
  white-space: nowrap;
  user-select: none;
}

.voucher-entries-table th,
.voucher-entries-table td {
  border: 1px solid #333333;
  padding: 8px 10px;
}

.voucher-entries-table th {
  background-color: #f2f4f7;
  font-weight: 600;
  text-align: center;
}

.subject-title {
  font-weight: 600;
}

.account-sub {
  font-size: 12px;
  color: #666;
}

.total-row {
  background-color: #fafbfc;
}

.chinese-total {
  font-size: 13px;
}

.voucher-signatures-bar {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 0 8px;
}

.signature-col {
  display: flex;
  align-items: center;
}

.sig-label {
  color: #444;
}

.sig-val {
  min-width: 70px;
  border-bottom: 1px solid #888;
  text-align: center;
  padding-bottom: 2px;
}

.classic-aux-table {
  margin-top: 6px;
  font-size: 12px;
}

.classic-aux-table th {
  background-color: #f5f7fa;
}

@media print {
  body * {
    visibility: hidden;
  }
  .classic-voucher-dialog,
  .print-area,
  .print-area * {
    visibility: visible;
  }
  .print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    margin: 0;
    padding: 0;
  }
  .el-dialog__header,
  .el-dialog__footer {
    display: none !important;
  }
  .voucher-side-mark {
    position: absolute !important;
    left: calc(100% + 5px) !important;
    top: 50% !important;
    transform: translateY(-50%) !important;
    color: #000 !important;
    font-weight: normal !important;
    font-size: 11px !important;
    letter-spacing: 6px !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
