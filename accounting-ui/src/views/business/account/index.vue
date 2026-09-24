<template>
  <div class="account-management-page">
    <!-- 顶部检索与操作卡片 -->
    <div class="fin-card">
      <el-tabs v-model="activeTab" class="account-category-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="客户分户账户" name="customer" />
        <el-tab-pane label="内部分户账户" name="internal" />
      </el-tabs>

      <!-- 客户分户查询表单 -->
      <el-form
        v-if="activeTab === 'customer'"
        :model="customerSearchForm"
        inline
        class="search-form"
      >
        <el-form-item label="账户编号">
          <el-input
            v-model="customerSearchForm.accountNo"
            placeholder="账号模糊搜索"
            clearable
            style="width: 170px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="账户名称">
          <el-input
            v-model="customerSearchForm.accountName"
            placeholder="户名模糊搜索"
            clearable
            style="width: 160px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="客户编号">
          <el-input
            v-model="customerSearchForm.ownerId"
            placeholder="客户ID"
            clearable
            style="width: 140px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select
            v-model="customerSearchForm.ownerType"
            placeholder="全部"
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in OWNER_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="会计科目">
          <el-select
            v-model="customerSearchForm.subjectCode"
            placeholder="科目编码/名称"
            clearable
            filterable
            style="width: 190px;"
            @change="handleSearch"
          >
            <el-option
              v-for="sub in subjectOptions"
              :key="sub.subjectCode"
              :label="`${sub.subjectCode} - ${sub.subjectName}`"
              :value="sub.subjectCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账户类型">
          <el-select
            v-model="customerSearchForm.accountType"
            placeholder="全部类型"
            clearable
            filterable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in accountTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账户状态">
          <el-select
            v-model="customerSearchForm.status"
            placeholder="全部"
            clearable
            style="width: 110px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in ACCOUNT_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="风控状态">
          <el-select
            v-model="customerSearchForm.riskStatus"
            placeholder="全部"
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in RISK_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开户日期">
          <el-date-picker
            v-model="customerDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 230px;"
            @change="handleCustomerDateChange"
          />
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

      <!-- 内部分户查询表单 -->
      <el-form
        v-else
        :model="internalSearchForm"
        inline
        class="search-form"
      >
        <el-form-item label="内部账号">
          <el-input
            v-model="internalSearchForm.accountNo"
            placeholder="内部账号搜索"
            clearable
            style="width: 200px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="内部户名">
          <el-input
            v-model="internalSearchForm.accountName"
            placeholder="内部户名搜索"
            clearable
            style="width: 190px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="会计科目">
          <el-select
            v-model="internalSearchForm.subjectCode"
            placeholder="科目编码/名称"
            clearable
            filterable
            style="width: 210px;"
            @change="handleSearch"
          >
            <el-option
              v-for="sub in subjectOptions"
              :key="sub.subjectCode"
              :label="`${sub.subjectCode} - ${sub.subjectName}`"
              :value="sub.subjectCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账户状态">
          <el-select
            v-model="internalSearchForm.status"
            placeholder="全部"
            clearable
            style="width: 130px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in ACCOUNT_STATUS_OPTIONS"
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

      <!-- 操作栏 -->
      <div class="action-bar">
        <template v-if="activeTab === 'customer'">
          <el-button type="primary" :icon="Plus" @click="openCustomerCreateDialog">
            开立客户账户
          </el-button>
        </template>
        <template v-else>
          <el-button type="primary" :icon="Plus" @click="openInternalCreateDialog">
            开立内部账户
          </el-button>
          <ActionButton
            type="warning"
            plain
            :icon="Refresh"
            confirm-title="全科目扫描开户确认"
            confirm-message="系统将扫描全量允许开户的末级会计科目，并为尚未开立内部账户的科目自动初始化内部核心分户。是否继续？"
            :on-click="handleBatchScanInternal"
          >
            全科目扫描补齐内部账户
          </ActionButton>
        </template>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 数据表格卡片 -->
    <div class="fin-card">
      <BaseTable
        :data="dataList"
        :loading="loading"
        :total="total"
        :page-no="pageNo"
        :page-size="pageSize"
        empty-text="暂无匹配的分户账户数据"
        @page-change="onPageChange"
      >
        <el-table-column type="index" label="序号" width="55" align="center" />

        <el-table-column prop="accountNo" label="账户编号" min-width="180">
          <template #default="{ row }">
            <div class="account-no-cell">
              <span class="code-tag highlight">{{ row.accountNo }}</span>
              <el-tooltip content="点击复制账号" placement="top">
                <el-icon class="copy-icon" @click="copyText(row.accountNo)"><DocumentCopy /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="accountName" label="账户名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="account-name-text">{{ row.accountName }}</span>
          </template>
        </el-table-column>

        <!-- 仅客户账户展示所有者信息 -->
        <el-table-column
          v-if="activeTab === 'customer'"
          prop="ownerId"
          label="客户编号 / 类型"
          min-width="145"
        >
          <template #default="{ row }">
            <div class="owner-cell">
              <span class="owner-id">{{ row.ownerId }}</span>
              <el-tag :type="getOwnerTypeTagType(row.ownerType)" size="small">
                {{ row.ownerTypeDesc || '个人' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="subjectCode" label="会计科目" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="code-tag">{{ row.subjectCode }}</span>
            <span class="subject-name-cell">{{ row.subjectName || getSubjectName(row.subjectCode) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="accountTypeName" label="账户类型" min-width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">
              {{ row.accountTypeName || row.accountType }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="currency" label="币种" width="75" align="center">
          <template #default="{ row }">
            <span class="currency-cell">{{ row.currency }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="balanceDirection" label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
              {{ row.balanceDirection === 1 ? '借' : '贷' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 主账户总余额 -->
        <el-table-column prop="balance" label="账面总余额" min-width="135" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.balance" prefix="¥ " />
          </template>
        </el-table-column>

        <!-- 可用余额 -->
        <el-table-column prop="availableBalance" label="可用余额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="available-balance-text">
              <AmountDisplay :value="row.availableBalance" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <!-- 冻结余额 -->
        <el-table-column prop="frozenBalance" label="冻结余额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="frozen-balance-text">
              <AmountDisplay :value="row.frozenBalance" prefix="¥ " />
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="账户状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag
              :status="row.status === 1 ? 'NORMAL' : (row.status === 2 ? 'FROZEN' : 'CANCELLED')"
              :label="row.statusDesc || getStatusLabel(row.status)"
            />
          </template>
        </el-table-column>

        <el-table-column
          v-if="activeTab === 'customer'"
          prop="riskStatus"
          label="风控状态"
          width="95"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="getRiskTagType(row.riskStatus)" size="small">
              {{ row.riskStatusDesc || getRiskLabel(row.riskStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="openDate" label="开户日期" width="110" align="center" />

        <!-- 行操作 -->
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => handleActionCommand(cmd, row)"
            >
              <el-button
                type="primary"
                link
                size="small"
                class="action-more-btn"
                title="操作菜单"
              >
                <el-icon :size="16"><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail" :icon="View">
                    全景档案
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 1"
                    command="freeze"
                    :icon="Lock"
                  >
                    <span style="color: #f56c6c;">冻结账户</span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-else-if="row.status === 2"
                    command="unfreeze"
                    :icon="Unlock"
                  >
                    <span style="color: #67c23a;">解冻账户</span>
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="activeTab === 'customer' && row.status !== 3"
                    command="risk"
                    :icon="Operation"
                  >
                    风控调整
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status !== 3"
                    command="cancel"
                    :icon="CircleClose"
                    divided
                  >
                    <span style="color: #909399;">注销账户</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- ==================== 1. 账户全景档案抽屉 ==================== -->
    <el-drawer
      v-model="drawerVisible"
      title="账户全景档案与资金看板"
      size="840px"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-header-title">
          <span>账户全景档案</span>
          <span v-if="selectedAccount" class="code-tag highlight header-account-no">
            {{ selectedAccount.accountNo }}
          </span>
        </div>
      </template>

      <div v-if="selectedAccount" class="account-drawer-content">
        <!-- 账户基本档案卡片 -->
        <div class="drawer-section-card">
          <div class="section-card-title">基本属性</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="账户编号">
              <span class="code-tag highlight">{{ selectedAccount.accountNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="账户名称">
              <strong>{{ selectedAccount.accountName }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="账户归属">
              <el-tag :type="selectedAccount.ownerId === 'INNER' ? 'warning' : 'primary'" size="small">
                {{ selectedAccount.ownerId === 'INNER' ? '系统内部分户' : `客户 ${selectedAccount.ownerId}` }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="会计科目">
              <span class="code-tag">{{ selectedAccount.subjectCode }}</span>
              {{ selectedAccount.subjectName || getSubjectName(selectedAccount.subjectCode) }}
            </el-descriptions-item>
            <el-descriptions-item label="账户类型">
              {{ selectedAccount.accountTypeName || selectedAccount.accountType }}
            </el-descriptions-item>
            <el-descriptions-item label="币种 / 借贷">
              {{ selectedAccount.currency }} / {{ selectedAccount.balanceDirection === 1 ? '借方(Debit)' : '贷方(Credit)' }}
            </el-descriptions-item>
            <el-descriptions-item label="账户状态">
              <StatusTag
                :status="selectedAccount.status === 1 ? 'NORMAL' : (selectedAccount.status === 2 ? 'FROZEN' : 'CANCELLED')"
                :label="selectedAccount.statusDesc || getStatusLabel(selectedAccount.status)"
              />
            </el-descriptions-item>
            <el-descriptions-item label="风控状态">
              <el-tag :type="getRiskTagType(selectedAccount.riskStatus)" size="small">
                {{ selectedAccount.riskStatusDesc || getRiskLabel(selectedAccount.riskStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开户日期">
              {{ selectedAccount.openDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="开户请求号" :span="3">
              <span class="rule-mono">{{ selectedAccount.requestNo || '-' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 双子账户与资金看板卡片 -->
        <div class="drawer-section-card">
          <div class="section-card-title">双子账户余额资产看板</div>
          <div class="balance-kpi-grid">
            <div class="kpi-card total">
              <div class="kpi-label">主账户总余额</div>
              <div class="kpi-value">
                <AmountDisplay :value="aggregateBalanceData?.totalBalance ?? selectedAccount.balance" prefix="¥ " />
              </div>
              <div class="kpi-tip">借贷净记账汇总</div>
            </div>

            <div class="kpi-card available">
              <div class="kpi-label">可用子账户余额</div>
              <div class="kpi-value">
                <AmountDisplay :value="aggregateBalanceData?.availableBalance ?? selectedAccount.availableBalance" prefix="¥ " />
              </div>
              <div class="kpi-tip">子账户 1 (可用账户)</div>
            </div>

            <div class="kpi-card frozen">
              <div class="kpi-label">冻结子账户余额</div>
              <div class="kpi-value">
                <AmountDisplay :value="aggregateBalanceData?.frozenBalance ?? selectedAccount.frozenBalance" prefix="¥ " />
              </div>
              <div class="kpi-tip">子账户 2 (冻结账户)</div>
            </div>

            <div v-if="aggregateBalanceData?.bufferEstimateBalance !== undefined" class="kpi-card buffer">
              <div class="kpi-label">缓冲预估余额</div>
              <div class="kpi-value">
                <AmountDisplay :value="aggregateBalanceData.bufferEstimateBalance" prefix="¥ " />
              </div>
              <div class="kpi-tip">Redis 实时缓冲聚合</div>
            </div>
          </div>
        </div>

        <!-- 详细流水与记录选项卡 -->
        <div class="drawer-section-card detail-tabs-card">
          <el-tabs v-model="drawerTab" class="detail-sub-tabs">
            <!-- 交易变动流水 -->
            <el-tab-pane label="交易变动明细" name="details">
              <div class="sub-filter-bar">
                <el-form :model="detailQueryForm" inline size="small">
                  <el-form-item label="交易类型">
                    <el-input
                      v-model="detailQueryForm.tradeType"
                      placeholder="交易类型"
                      clearable
                      style="width: 140px;"
                    />
                  </el-form-item>
                  <el-form-item label="借贷">
                    <el-select
                      v-model="detailQueryForm.debitCredit"
                      placeholder="全部"
                      clearable
                      style="width: 90px;"
                    >
                      <el-option label="借方" :value="1" />
                      <el-option label="贷方" :value="2" />
                    </el-select>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :icon="Search" @click="loadAccountDetails">
                      查询明细
                    </el-button>
                  </el-form-item>
                </el-form>
              </div>

              <el-table
                v-loading="detailLoading"
                :data="detailList"
                border
                size="small"
                stripe
                style="width: 100%"
                empty-text="暂无账户变动流水"
              >
                <el-table-column type="index" label="序号" width="50" align="center" />
                <el-table-column prop="tradeNo" label="业务交易号" min-width="140" show-overflow-tooltip />
                <el-table-column prop="voucherNo" label="凭证编号" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="code-tag">{{ row.voucherNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="tradeType" label="交易类型" width="100" align="center" />
                <el-table-column prop="debitCredit" label="方向" width="65" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.debitCredit === 1 ? 'primary' : 'warning'" size="small">
                      {{ row.debitCredit === 1 ? '借' : '贷' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="变动金额" min-width="110" align="right">
                  <template #default="{ row }">
                    <AmountDisplay :value="row.amount" prefix="¥ " />
                  </template>
                </el-table-column>
                <el-table-column prop="postBalance" label="变动后余额" min-width="120" align="right">
                  <template #default="{ row }">
                    <AmountDisplay :value="row.postBalance" prefix="¥ " />
                  </template>
                </el-table-column>
                <el-table-column prop="tradeTime" label="记账时间" width="145" align="center" />
                <el-table-column prop="digest" label="摘要" min-width="130" show-overflow-tooltip />
              </el-table>

              <div class="sub-pagination-box">
                <el-pagination
                  v-model:current-page="detailPageNo"
                  v-model:page-size="detailPageSize"
                  :total="detailTotal"
                  size="small"
                  background
                  layout="total, prev, pager, next"
                  @current-change="loadAccountDetails"
                />
              </div>
            </el-tab-pane>

            <!-- 资金冻结记录 -->
            <el-tab-pane label="资金冻结记录" name="freeze">
              <el-table
                v-loading="freezeLoading"
                :data="freezeList"
                border
                size="small"
                stripe
                style="width: 100%"
                empty-text="暂无资金冻结记录"
              >
                <el-table-column type="index" label="序号" width="50" align="center" />
                <el-table-column prop="freezeId" label="冻结单号" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="code-tag highlight">{{ row.freezeId }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="freezeAmount" label="冻结金额" min-width="120" align="right">
                  <template #default="{ row }">
                    <AmountDisplay :value="row.freezeAmount" prefix="¥ " />
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
                      {{ row.status === 1 ? '冻结中' : '已解冻' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="冻结时间" width="150" align="center" />
                <el-table-column prop="expireTime" label="过期时间" width="150" align="center" />
                <el-table-column prop="summary" label="摘要/原因" min-width="140" show-overflow-tooltip />
              </el-table>

              <div class="sub-pagination-box">
                <el-pagination
                  v-model:current-page="freezePageNo"
                  v-model:page-size="freezePageSize"
                  :total="freezeTotal"
                  size="small"
                  background
                  layout="total, prev, pager, next"
                  @current-change="loadFreezeRecords"
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-drawer>

    <!-- ==================== 2. 客户开户弹窗 ==================== -->
    <BaseDialog
      v-model="customerCreateDialogVisible"
      title="开立客户分户账户"
      width="800px"
      :confirm-loading="submitLoading"
      @confirm="submitCustomerOpen"
    >
      <el-form
        ref="customerOpenFormRef"
        :model="customerOpenForm"
        :rules="customerOpenRules"
        label-width="110px"
        label-position="right"
      >
        <el-alert
          title="开户机制说明"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
        >
          <template #default>
            <div>1. <strong>按模板批量开立（推荐）</strong>：基于所选开户模板，一键为客户批量开立模板配置的全部会计科目账户。</div>
            <div>2. <strong>指定单一科目开立</strong>：从所选开户模板中指定单一科目，自动联动回显预置的账户类型、币种、借贷方向和账号/户名规则。</div>
          </template>
        </el-alert>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业务线" prop="businessCode">
              <el-select
                v-model="customerOpenForm.businessCode"
                :placeholder="businessCodeOptions.length > 0 ? '请选择业务线' : '暂无业务线字典'"
                no-data-text="暂无业务线字典，请先在系统字典中维护"
                filterable
                style="width: 100%"
                @change="handleBusinessOrCustomerTypeChange"
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
            <el-form-item label="客户类型" prop="customerType">
              <el-select
                v-model="customerOpenForm.customerType"
                placeholder="请选择客户类型"
                style="width: 100%"
                @change="handleBusinessOrCustomerTypeChange"
              >
                <el-option
                  v-for="item in OWNER_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户编号" prop="customerId">
              <el-input
                v-model="customerOpenForm.customerId"
                placeholder="如: CUST202603001"
                maxlength="32"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input
                v-model="customerOpenForm.customerName"
                placeholder="如: 杭州科技有限公司 / 张三"
                maxlength="64"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 开户模板选择 -->
        <el-form-item label="开户模板" prop="templateName">
          <div v-loading="templateLoading" style="width: 100%">
            <el-select
              v-model="selectedTemplateName"
              placeholder="请选择适用的开户模板"
              style="width: 100%"
              no-data-text="当前业务线与客户类型下无启用的开户模板"
              @change="onTemplateChange"
            >
              <el-option
                v-for="item in availableTemplates"
                :key="item.templateName"
                :label="`${item.templateName}（共包含 ${item.items.length} 个科目账户）`"
                :value="item.templateName"
              />
            </el-select>
            <div v-if="availableTemplates.length === 0 && !templateLoading" class="template-empty-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>未检索到匹配且启用的开户模板，请先在【开户模板管理】中配置启用。</span>
            </div>
          </div>
        </el-form-item>

        <!-- 开户范围单选 -->
        <el-form-item label="开户范围" prop="openMode">
          <el-radio-group v-model="customerOpenForm.openMode" @change="onOpenModeChange">
            <el-radio value="batch">开立模板下全量科目账户（批量开立，推荐）</el-radio>
            <el-radio value="single">指定模板下单科目开立（单开）</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 模式 1：批量开立科目明细预览表格 -->
        <div v-if="customerOpenForm.openMode === 'batch' && selectedTemplateGroup" class="batch-preview-section">
          <div class="preview-section-header">
            <el-icon><Tickets /></el-icon>
            <span class="preview-title">
              模板包含科目账户明细（共 {{ selectedTemplateGroup.items.length }} 个账户）
            </span>
          </div>
          <el-table
            :data="selectedTemplateGroup.items"
            border
            size="small"
            stripe
            class="batch-preview-table"
          >
            <el-table-column type="index" label="序号" width="50" align="center" />
            <el-table-column prop="subjectCode" label="会计科目" min-width="160">
              <template #default="{ row }">
                <span class="code-tag highlight">{{ row.subjectCode }}</span>
                <span class="sub-name-text">{{ getSubjectName(row.subjectCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="accountType" label="账户类型" min-width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.accountType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currency" label="币种" width="65" align="center" />
            <el-table-column prop="balanceDirection" label="方向" width="65" align="center">
              <template #default="{ row }">
                <el-tag :type="row.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
                  {{ row.balanceDirection === 1 ? '借' : '贷' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="acctNoRule" label="账号规则" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="rule-mono">{{ row.acctNoRule }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="acctNameRule" label="户名规则" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="rule-mono">{{ row.acctNameRule }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 模式 2：指定单一科目开立及联动回显 -->
        <div v-if="customerOpenForm.openMode === 'single'" class="single-preview-section">
          <el-form-item label="指定会计科目" prop="subjectCode">
            <el-select
              v-model="customerOpenForm.subjectCode"
              placeholder="请选择模板已配置的会计科目"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="item in (selectedTemplateGroup?.items || [])"
                :key="item.subjectCode"
                :label="`${item.subjectCode} - ${getSubjectName(item.subjectCode)} (${item.accountType})`"
                :value="item.subjectCode"
              />
            </el-select>
          </el-form-item>

          <!-- 联动回显：所选科目的规则与预置属性卡片 -->
          <div v-if="currentSelectedTemplateItem" class="template-item-preview-card">
            <div class="preview-section-header">
              <el-icon><InfoFilled /></el-icon>
              <span class="preview-title">模板科目属性与规则联动回显</span>
            </div>
            <el-descriptions :column="2" border size="small" class="preview-descriptions">
              <el-descriptions-item label="会计科目">
                <span class="code-tag highlight">{{ currentSelectedTemplateItem.subjectCode }}</span>
                <span class="desc-val">{{ getSubjectName(currentSelectedTemplateItem.subjectCode) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="账户类型">
                <el-tag size="small" type="info">{{ currentSelectedTemplateItem.accountType }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="币种">
                <span class="desc-val">{{ currentSelectedTemplateItem.currency || 'CNY' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="借贷方向">
                <el-tag :type="currentSelectedTemplateItem.balanceDirection === 1 ? 'primary' : 'warning'" size="small">
                  {{ currentSelectedTemplateItem.balanceDirection === 1 ? '借方 (Debit)' : '贷方 (Credit)' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="账号生成规则" :span="2">
                <span class="rule-mono highlight-rule">{{ currentSelectedTemplateItem.acctNoRule }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="户名生成规则" :span="2">
                <span class="rule-mono highlight-rule">{{ currentSelectedTemplateItem.acctNameRule }}</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="previewAccountName" label="预计生成户名" :span="2">
                <span class="preview-acct-name">{{ previewAccountName }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>

        <el-form-item label="开户请求号" prop="requestNo" style="margin-top: 14px;">
          <div class="request-no-row">
            <el-input
              v-model="customerOpenForm.requestNo"
              placeholder="幂等请求唯一编号"
              maxlength="40"
            />
            <el-button :icon="Refresh" @click="generateCustomerRequestNo">重新生成</el-button>
          </div>
        </el-form-item>
      </el-form>
    </BaseDialog>

    <!-- ==================== 3. 内部账户开户弹窗 ==================== -->
    <BaseDialog
      v-model="internalCreateDialogVisible"
      title="开立内部核心分户账户"
      width="540px"
      :confirm-loading="submitLoading"
      @confirm="submitInternalOpen"
    >
      <el-form
        ref="internalOpenFormRef"
        :model="internalOpenForm"
        :rules="internalOpenRules"
        label-width="110px"
        label-position="right"
      >
        <el-alert
          title="内部账户开立规范"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
        >
          系统将为所选会计科目初始化系统内部分户账户（所有者编号固定为 'INNER'），支持日常利息核算、内部往来、费用归集等账务操作。
        </el-alert>

        <el-form-item label="会计科目" prop="subjectCode">
          <el-select
            v-model="internalOpenForm.subjectCode"
            placeholder="请选择允许开户的科目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="sub in subjectOptions"
              :key="sub.subjectCode"
              :label="`${sub.subjectCode} - ${sub.subjectName}`"
              :value="sub.subjectCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="internalOpenForm.subjectCode" label="预计账户名称">
          <span style="font-weight: 600; color: #409eff;">
            {{ getInternalAccountNamePreview(internalOpenForm.subjectCode) }}
          </span>
        </el-form-item>
      </el-form>
    </BaseDialog>

    <!-- ==================== 4. 状态操作弹窗 (冻结/解冻/注销/风控) ==================== -->
    <BaseDialog
      v-model="statusDialogVisible"
      :title="statusDialogConfig.title"
      width="500px"
      :confirm-loading="statusSubmitLoading"
      @confirm="submitStatusChange"
    >
      <div v-if="targetAccount" class="status-dialog-body">
        <el-alert
          :title="statusDialogConfig.alertTitle"
          :type="statusDialogConfig.alertType"
          :closable="false"
          show-icon
          style="margin-bottom: 14px;"
        >
          {{ statusDialogConfig.alertMessage }}
        </el-alert>

        <el-descriptions :column="1" border size="small" style="margin-bottom: 14px;">
          <el-descriptions-item label="账户编号">
            <span class="code-tag highlight">{{ targetAccount.accountNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="账户名称">
            {{ targetAccount.accountName }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <StatusTag
              :status="targetAccount.status === 1 ? 'NORMAL' : (targetAccount.status === 2 ? 'FROZEN' : 'CANCELLED')"
              :label="targetAccount.statusDesc || getStatusLabel(targetAccount.status)"
            />
          </el-descriptions-item>
          <el-descriptions-item v-if="currentStatusAction === 'risk'" label="当前风控">
            <el-tag :type="getRiskTagType(targetAccount.riskStatus)" size="small">
              {{ targetAccount.riskStatusDesc || getRiskLabel(targetAccount.riskStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="90px">
          <!-- 仅风控调整场景选择风控状态 -->
          <el-form-item v-if="currentStatusAction === 'risk'" label="目标风控" required>
            <el-select v-model="newRiskStatus" placeholder="请选择风控状态" style="width: 100%">
              <el-option
                v-for="item in RISK_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="操作原因" required>
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="3"
              placeholder="请输入操作说明或审批单据号"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
    </BaseDialog>
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
  View,
  Lock,
  Unlock,
  Operation,
  CircleClose,
  DocumentCopy,
  InfoFilled,
  Tickets,
  MoreFilled
} from '@element-plus/icons-vue'
import {
  getTemplatePage,
  type TemplateResponse,
  type TemplateGroupItem
} from '@/api/template'
import {
  getAccountPage,
  getAggregateBalance,
  getAccountDetails,
  getFreezeRecords,
  openExternalAccount,
  openExternalBatch,
  openInternalAccount,
  batchScanInternalAccounts,
  freezeAccount,
  unfreezeAccount,
  cancelAccount,
  changeRiskStatus,
  ACCOUNT_STATUS_OPTIONS,
  RISK_STATUS_OPTIONS,
  OWNER_TYPE_OPTIONS,
  type AccountPageItem,
  type AggregateBalanceResponse,
  type AccountDetailItem,
  type FreezeRecordItem
} from '@/api/account'
import { getDictByType } from '@/api/dict'
import { querySubjectPage, type SubjectItem } from '@/api/subject'
import type { SelectOption } from '@/api/types'
import { toast } from '@/utils/toast'

// ==================== 状态定义 ====================

const activeTab = ref<'customer' | 'internal'>('customer')
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const dataList = ref<AccountPageItem[]>([])

// 业务线字典
const businessCodeOptions = ref<SelectOption[]>([])
// 账户类型字典
const accountTypeOptions = ref<SelectOption[]>([])
// 会计科目列表
const subjectOptions = ref<SubjectItem[]>([])

// 检索表单 - 客户分户
const customerSearchForm = reactive({
  accountNo: '',
  accountName: '',
  ownerId: '',
  ownerType: undefined as number | undefined,
  subjectCode: '',
  accountType: '',
  status: undefined as number | undefined,
  riskStatus: undefined as number | undefined,
  startDate: '',
  endDate: ''
})
const customerDateRange = ref<[string, string] | null>(null)

// 检索表单 - 内部分户
const internalSearchForm = reactive({
  accountNo: '',
  accountName: '',
  subjectCode: '',
  status: undefined as number | undefined
})

// ==================== 数据字典与科目加载 ====================

async function loadDictionaries() {
  try {
    let bizRes = await getDictByType('business_code')
    if (!bizRes || bizRes.length === 0) {
      bizRes = await getDictByType('BUSINESS_CODE')
    }
    businessCodeOptions.value = (bizRes || []).map((item) => ({
      label: `${item.dictName} (${item.dictCode})`,
      value: item.dictCode
    }))
  } catch {
    businessCodeOptions.value = []
  }

  try {
    let acctTypeRes = await getDictByType('account_type')
    if (!acctTypeRes || acctTypeRes.length === 0) {
      acctTypeRes = await getDictByType('ACCOUNT_TYPE')
    }
    accountTypeOptions.value = (acctTypeRes || []).map((item) => ({
      label: item.dictName,
      value: item.dictCode
    }))
  } catch {
    accountTypeOptions.value = []
  }

  try {
    const subjectPage = await querySubjectPage({ pageNo: 1, pageSize: 500, status: 1 })
    subjectOptions.value = subjectPage.list || []
  } catch {
    subjectOptions.value = []
  }
}

function getSubjectName(subjectCode: string): string {
  const sub = subjectOptions.value.find((s) => s.subjectCode === subjectCode)
  return sub ? sub.subjectName : ''
}

function getStatusLabel(status: number): string {
  const found = ACCOUNT_STATUS_OPTIONS.find((s) => s.value === status)
  return found ? found.label : '未知'
}

function getRiskLabel(riskStatus: number): string {
  const found = RISK_STATUS_OPTIONS.find((s) => s.value === riskStatus)
  return found ? found.label : '正常'
}

function getRiskTagType(riskStatus?: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' | '' {
  if (riskStatus === 2 || riskStatus === 3) return 'warning'
  if (riskStatus === 4) return 'danger'
  return 'success'
}

function getOwnerTypeTagType(ownerType?: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' | '' {
  if (ownerType === 2) return 'primary'
  if (ownerType === 99) return 'info'
  return 'success'
}

// ==================== 列表查询 ====================

async function loadData() {
  loading.value = true
  try {
    const isCustomer = activeTab.value === 'customer'
    const res = await getAccountPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      accountCategory: isCustomer ? 'CUSTOMER' : 'INTERNAL',
      accountNo: isCustomer ? customerSearchForm.accountNo : internalSearchForm.accountNo,
      accountName: isCustomer ? customerSearchForm.accountName : internalSearchForm.accountName,
      ownerId: isCustomer ? customerSearchForm.ownerId : undefined,
      ownerType: isCustomer ? customerSearchForm.ownerType : undefined,
      subjectCode: isCustomer ? customerSearchForm.subjectCode : internalSearchForm.subjectCode,
      accountType: isCustomer ? customerSearchForm.accountType : undefined,
      status: isCustomer ? customerSearchForm.status : internalSearchForm.status,
      riskStatus: isCustomer ? customerSearchForm.riskStatus : undefined,
      startDate: isCustomer ? customerSearchForm.startDate : undefined,
      endDate: isCustomer ? customerSearchForm.endDate : undefined
    })
    dataList.value = res.list || []
    total.value = res.total || 0
  } catch {
    dataList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  pageNo.value = 1
  loadData()
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handleReset() {
  if (activeTab.value === 'customer') {
    customerSearchForm.accountNo = ''
    customerSearchForm.accountName = ''
    customerSearchForm.ownerId = ''
    customerSearchForm.ownerType = undefined
    customerSearchForm.subjectCode = ''
    customerSearchForm.accountType = ''
    customerSearchForm.status = undefined
    customerSearchForm.riskStatus = undefined
    customerSearchForm.startDate = ''
    customerSearchForm.endDate = ''
    customerDateRange.value = null
  } else {
    internalSearchForm.accountNo = ''
    internalSearchForm.accountName = ''
    internalSearchForm.subjectCode = ''
    internalSearchForm.status = undefined
  }
  handleSearch()
}

function handleCustomerDateChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    customerSearchForm.startDate = val[0]
    customerSearchForm.endDate = val[1]
  } else {
    customerSearchForm.startDate = ''
    customerSearchForm.endDate = ''
  }
  handleSearch()
}

function onPageChange(page: number, size: number) {
  pageNo.value = page
  pageSize.value = size
  loadData()
}

function copyText(text: string) {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    toast.success('账户编号已复制到剪贴板')
  })
}

// ==================== 抽屉：账户全景档案 ====================

const drawerVisible = ref(false)
const drawerTab = ref('details')
const selectedAccount = ref<AccountPageItem | null>(null)
const aggregateBalanceData = ref<AggregateBalanceResponse | null>(null)

// 明细列表
const detailLoading = ref(false)
const detailList = ref<AccountDetailItem[]>([])
const detailTotal = ref(0)
const detailPageNo = ref(1)
const detailPageSize = ref(10)
const detailQueryForm = reactive({
  tradeType: '',
  debitCredit: undefined as number | undefined
})

// 冻结记录
const freezeLoading = ref(false)
const freezeList = ref<FreezeRecordItem[]>([])
const freezeTotal = ref(0)
const freezePageNo = ref(1)
const freezePageSize = ref(10)

async function openAccountDetail(row: AccountPageItem) {
  selectedAccount.value = row
  drawerVisible.value = true
  drawerTab.value = 'details'

  // 加载聚合余额
  try {
    aggregateBalanceData.value = await getAggregateBalance(row.accountNo)
  } catch {
    aggregateBalanceData.value = null
  }

  // 加载交易明细
  detailPageNo.value = 1
  loadAccountDetails()

  // 加载冻结记录
  freezePageNo.value = 1
  loadFreezeRecords()
}

async function loadAccountDetails() {
  if (!selectedAccount.value) return
  detailLoading.value = true
  try {
    const res = await getAccountDetails({
      pageNo: detailPageNo.value,
      pageSize: detailPageSize.value,
      accountNo: selectedAccount.value.accountNo,
      tradeType: detailQueryForm.tradeType || undefined,
      debitCredit: detailQueryForm.debitCredit
    })
    detailList.value = res.list || []
    detailTotal.value = res.total || 0
  } catch {
    detailList.value = []
    detailTotal.value = 0
  } finally {
    detailLoading.value = false
  }
}

async function loadFreezeRecords() {
  if (!selectedAccount.value) return
  freezeLoading.value = true
  try {
    const res = await getFreezeRecords({
      pageNo: freezePageNo.value,
      pageSize: freezePageSize.value,
      accountNo: selectedAccount.value.accountNo
    })
    freezeList.value = res.list || []
    freezeTotal.value = res.total || 0
  } catch {
    freezeList.value = []
    freezeTotal.value = 0
  } finally {
    freezeLoading.value = false
  }
}

// ==================== 客户开户弹窗 (方案 A: 基于开户模板驱动) ====================

const customerCreateDialogVisible = ref(false)
const submitLoading = ref(false)
const templateLoading = ref(false)
const customerOpenFormRef = ref<FormInstance>()

// 启用的模板组列表与当前选中的模板名称
const availableTemplates = ref<TemplateGroupItem[]>([])
const selectedTemplateName = ref('')

const customerOpenForm = reactive({
  openMode: 'batch' as 'batch' | 'single',
  businessCode: '',
  customerType: 1,
  customerId: '',
  customerName: '',
  templateName: '',
  subjectCode: '',
  requestNo: ''
})

const customerOpenRules: FormRules = {
  openMode: [{ required: true, message: '请选择开户范围', trigger: 'change' }],
  businessCode: [{ required: true, message: '请选择业务线', trigger: 'change' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  customerId: [{ required: true, message: '请输入客户编号', trigger: 'blur' }],
  templateName: [{ required: true, message: '请选择适用的开户模板', trigger: 'change' }],
  subjectCode: [{ required: true, message: '指定单科目开立必须选择会计科目', trigger: 'change' }],
  requestNo: [{ required: true, message: '请输入或生成开户请求号', trigger: 'blur' }]
}

// 当前选中的模板组
const selectedTemplateGroup = computed<TemplateGroupItem | null>(() => {
  return availableTemplates.value.find((t) => t.templateName === selectedTemplateName.value) || null
})

// 单科目模式下当前选中的模板科目明细
const currentSelectedTemplateItem = computed<TemplateResponse | null>(() => {
  if (!selectedTemplateGroup.value || !customerOpenForm.subjectCode) return null
  return selectedTemplateGroup.value.items.find((item) => item.subjectCode === customerOpenForm.subjectCode) || null
})

// 预计生成户名实时预览
const previewAccountName = computed<string>(() => {
  if (!currentSelectedTemplateItem.value) return ''
  const rule = currentSelectedTemplateItem.value.acctNameRule || '{customerName}'
  const custName = customerOpenForm.customerName || '【客户名称】'
  const custId = customerOpenForm.customerId || '【客户编号】'
  const acctType = currentSelectedTemplateItem.value.accountType || ''
  const subName = getSubjectName(currentSelectedTemplateItem.value.subjectCode)
  const currency = currentSelectedTemplateItem.value.currency || 'CNY'
  const dirName = currentSelectedTemplateItem.value.balanceDirection === 1 ? '借' : '贷'
  const ownerType = customerOpenForm.customerType === 1 ? '个人' : (customerOpenForm.customerType === 2 ? '企业' : '其他')

  return rule
    .replace(/\{customerName\d*\}/g, custName)
    .replace(/\{ownerName\d*\}/g, custName)
    .replace(/\{customerId\d*\}/g, custId)
    .replace(/\{ownerId\d*\}/g, custId)
    .replace(/\{accountType\d*\}/g, acctType)
    .replace(/\{accountTypeName\d*\}/g, acctType)
    .replace(/\{subjectName\d*\}/g, subName)
    .replace(/\{currencyName\d*\}/g, currency === 'CNY' ? '人民币' : currency)
    .replace(/\{directionName\d*\}/g, dirName)
    .replace(/\{ownerTypeName\d*\}/g, ownerType)
})

function generateCustomerRequestNo() {
  const timestamp = Date.now().toString().slice(-8)
  const random = Math.floor(1000 + Math.random() * 9000)
  customerOpenForm.requestNo = `REQ${timestamp}${random}`
}

async function fetchTemplatesForCustomerOpen() {
  if (!customerOpenForm.businessCode) {
    availableTemplates.value = []
    selectedTemplateName.value = ''
    customerOpenForm.templateName = ''
    customerOpenForm.subjectCode = ''
    return
  }
  templateLoading.value = true
  try {
    const res = await getTemplatePage({
      pageNo: 1,
      pageSize: 500,
      businessCode: customerOpenForm.businessCode,
      customerType: customerOpenForm.customerType,
      status: 2 // 仅加载启用状态的模板
    })
    const map = new Map<string, TemplateGroupItem>()
    for (const item of (res.list || [])) {
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
    availableTemplates.value = Array.from(map.values())
    if (availableTemplates.value.length > 0) {
      selectedTemplateName.value = availableTemplates.value[0].templateName
      customerOpenForm.templateName = selectedTemplateName.value
      onTemplateChange()
    } else {
      selectedTemplateName.value = ''
      customerOpenForm.templateName = ''
      customerOpenForm.subjectCode = ''
    }
  } catch {
    availableTemplates.value = []
    selectedTemplateName.value = ''
    customerOpenForm.templateName = ''
    customerOpenForm.subjectCode = ''
  } finally {
    templateLoading.value = false
  }
}

function handleBusinessOrCustomerTypeChange() {
  fetchTemplatesForCustomerOpen()
}

function onTemplateChange() {
  customerOpenForm.templateName = selectedTemplateName.value
  if (selectedTemplateGroup.value && selectedTemplateGroup.value.items.length > 0) {
    customerOpenForm.subjectCode = selectedTemplateGroup.value.items[0].subjectCode
  } else {
    customerOpenForm.subjectCode = ''
  }
}

function onOpenModeChange() {
  if (customerOpenForm.openMode === 'single') {
    if (selectedTemplateGroup.value && selectedTemplateGroup.value.items.length > 0) {
      if (!customerOpenForm.subjectCode || !selectedTemplateGroup.value.items.some((i) => i.subjectCode === customerOpenForm.subjectCode)) {
        customerOpenForm.subjectCode = selectedTemplateGroup.value.items[0].subjectCode
      }
    }
  }
}

async function openCustomerCreateDialog() {
  if (businessCodeOptions.value.length === 0) {
    await loadDictionaries()
  }
  customerOpenForm.openMode = 'batch'
  customerOpenForm.businessCode = (businessCodeOptions.value[0]?.value as string) || ''
  customerOpenForm.customerType = 1
  customerOpenForm.customerId = ''
  customerOpenForm.customerName = ''
  customerOpenForm.templateName = ''
  customerOpenForm.subjectCode = ''
  generateCustomerRequestNo()
  customerCreateDialogVisible.value = true
  if (customerOpenForm.businessCode) {
    await fetchTemplatesForCustomerOpen()
  } else {
    availableTemplates.value = []
    selectedTemplateName.value = ''
  }
}

async function submitCustomerOpen() {
  if (!customerOpenFormRef.value) return
  await customerOpenFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!selectedTemplateGroup.value) {
      toast.warning('请选择适用的开户模板')
      return
    }
    submitLoading.value = true
    try {
      if (customerOpenForm.openMode === 'batch') {
        const res = await openExternalBatch({
          businessCode: customerOpenForm.businessCode,
          customerType: customerOpenForm.customerType,
          customerId: customerOpenForm.customerId,
          customerName: customerOpenForm.customerName,
          requestNo: customerOpenForm.requestNo
        })
        toast.success(`批量开户成功！已开立 ${res?.length || 1} 个科目账户`)
      } else {
        await openExternalAccount({
          businessCode: customerOpenForm.businessCode,
          customerType: customerOpenForm.customerType,
          customerId: customerOpenForm.customerId,
          customerName: customerOpenForm.customerName,
          subjectCode: customerOpenForm.subjectCode,
          requestNo: customerOpenForm.requestNo
        })
        toast.success('客户账户开立成功！')
      }
      customerCreateDialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

// ==================== 内部账户开户弹窗 ====================

const internalCreateDialogVisible = ref(false)
const internalOpenFormRef = ref<FormInstance>()
const internalOpenForm = reactive({
  subjectCode: ''
})

const internalOpenRules: FormRules = {
  subjectCode: [{ required: true, message: '请选择会计科目', trigger: 'change' }]
}

function getInternalAccountNamePreview(subjectCode: string): string {
  const sub = subjectOptions.value.find((s) => s.subjectCode === subjectCode)
  const name = sub?.subjectName || subjectCode
  return `${name}-内部账户`
}

function openInternalCreateDialog() {
  internalOpenForm.subjectCode = ''
  internalCreateDialogVisible.value = true
}

async function submitInternalOpen() {
  if (!internalOpenFormRef.value) return
  await internalOpenFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await openInternalAccount({
        subjectCode: internalOpenForm.subjectCode
      })
      toast.success('内部核心分户账户开立成功！')
      internalCreateDialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

async function handleBatchScanInternal() {
  const res = await batchScanInternalAccounts()
  toast.success(`全科目扫描补齐完成！总扫描科目: ${res?.totalSubjects || 0}，成功开立新内部账户: ${res?.successCount || 0}`)
  loadData()
}

// ==================== 账户状态管理弹窗 ====================

type StatusAction = 'freeze' | 'unfreeze' | 'cancel' | 'risk'

const statusDialogVisible = ref(false)
const statusSubmitLoading = ref(false)
const currentStatusAction = ref<StatusAction>('freeze')
const targetAccount = ref<AccountPageItem | null>(null)
const statusReason = ref('')
const newRiskStatus = ref<number>(1)

const statusDialogConfig = computed(() => {
  switch (currentStatusAction.value) {
    case 'freeze':
      return {
        title: '冻结账户',
        alertTitle: '账户冻结警示',
        alertType: 'warning' as const,
        alertMessage: '冻结后账户状态变更为【冻结】，该账户将禁止任何转账出金及动支操作。'
      }
    case 'unfreeze':
      return {
        title: '解冻账户',
        alertTitle: '账户解冻确认',
        alertType: 'success' as const,
        alertMessage: '解冻后账户恢复为【正常】状态，账户收付动支权限恢复可用。'
      }
    case 'cancel':
      return {
        title: '注销账户确认',
        alertTitle: '不可逆注销警示',
        alertType: 'error' as const,
        alertMessage: '注销操作不可逆！账户总余额及可用/冻结子账户余额必须为 0，且无任何在途未完结交易。'
      }
    case 'risk':
      return {
        title: '风控状态调整',
        alertTitle: '风控状态规则',
        alertType: 'info' as const,
        alertMessage: '止入：禁止资金入账与充值；止出：禁止资金流出与扣款；止入止出：双向阻断。'
      }
  }
})

function openFreezeDialog(row: AccountPageItem) {
  targetAccount.value = row
  currentStatusAction.value = 'freeze'
  statusReason.value = '后台合规风控冻结'
  statusDialogVisible.value = true
}

function openUnfreezeDialog(row: AccountPageItem) {
  targetAccount.value = row
  currentStatusAction.value = 'unfreeze'
  statusReason.value = '风控审核通过，解除冻结'
  statusDialogVisible.value = true
}

function openCancelDialog(row: AccountPageItem) {
  if (row.balance !== 0 || row.availableBalance !== 0 || row.frozenBalance !== 0) {
    toast.warning('账户余额不为零（或仍有冻结资金），严禁注销账户！')
    return
  }
  targetAccount.value = row
  currentStatusAction.value = 'cancel'
  statusReason.value = '客户申请销户'
  statusDialogVisible.value = true
}

function openRiskDialog(row: AccountPageItem) {
  targetAccount.value = row
  currentStatusAction.value = 'risk'
  newRiskStatus.value = row.riskStatus || 1
  statusReason.value = '例行风控等级调整'
  statusDialogVisible.value = true
}

function handleActionCommand(command: string, row: AccountPageItem) {
  switch (command) {
    case 'detail':
      openAccountDetail(row)
      break
    case 'freeze':
      openFreezeDialog(row)
      break
    case 'unfreeze':
      openUnfreezeDialog(row)
      break
    case 'risk':
      openRiskDialog(row)
      break
    case 'cancel':
      openCancelDialog(row)
      break
  }
}

async function submitStatusChange() {
  if (!targetAccount.value) return
  if (!statusReason.value.trim()) {
    toast.warning('请输入操作原因说明')
    return
  }

  statusSubmitLoading.value = true
  const accountNo = targetAccount.value.accountNo
  try {
    switch (currentStatusAction.value) {
      case 'freeze':
        await freezeAccount(accountNo, statusReason.value)
        toast.success(`账户【${accountNo}】已成功冻结`)
        break
      case 'unfreeze':
        await unfreezeAccount(accountNo, statusReason.value)
        toast.success(`账户【${accountNo}】已成功解冻恢复正常`)
        break
      case 'cancel':
        await cancelAccount(accountNo, statusReason.value)
        toast.success(`账户【${accountNo}】注销成功`)
        break
      case 'risk':
        await changeRiskStatus(accountNo, newRiskStatus.value, statusReason.value)
        toast.success(`账户【${accountNo}】风控状态更新成功`)
        break
    }
    statusDialogVisible.value = false
    loadData()
  } finally {
    statusSubmitLoading.value = false
  }
}

// ==================== 初始化 ====================

onMounted(async () => {
  await loadDictionaries()
  await loadData()
})
</script>

<style scoped>
.account-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.account-category-tabs {
  margin-bottom: 12px;
}

.search-form {
  margin-bottom: 8px;
}

.action-bar {
  display: flex;
  gap: 12px;
  padding-top: 4px;
}

.account-no-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .copy-icon {
    font-size: 14px;
    color: #909399;
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: #409eff;
    }
  }
}

.account-name-text {
  font-weight: 500;
  color: #303133;
}

.owner-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  .owner-id {
    font-family: var(--fin-font-mono, monospace);
    font-size: 12px;
    color: #606266;
  }
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

.rule-mono {
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  color: #606266;
}

.currency-cell {
  font-weight: 600;
  color: #606266;
  font-size: 12px;
}

.subject-name-cell {
  margin-left: 6px;
  color: #606266;
  font-size: 13px;
}

.available-balance-text {
  font-weight: 500;
  color: #67c23a;
}

.frozen-balance-text {
  font-weight: 500;
  color: #e6a23c;
}

/* 抽屉样式 */
.drawer-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
}

.header-account-no {
  font-size: 14px;
}

.account-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-section-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
}

.section-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.balance-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.kpi-card {
  border-radius: 6px;
  padding: 12px 14px;
  background: #f8f9fb;
  border: 1px solid #eef0f5;

  .kpi-label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
  }

  .kpi-value {
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 4px;
  }

  .kpi-tip {
    font-size: 11px;
    color: #a8abb2;
  }

  &.total {
    border-color: #d9ecff;
    background: #ecf5ff;
    .kpi-value {
      color: #409eff;
    }
  }

  &.available {
    border-color: #e1f3d8;
    background: #f0f9eb;
    .kpi-value {
      color: #67c23a;
    }
  }

  &.frozen {
    border-color: #faecd8;
    background: #fdf6ec;
    .kpi-value {
      color: #e6a23c;
    }
  }

  &.buffer {
    border-color: #e9e9eb;
    background: #f4f4f5;
    .kpi-value {
      color: #909399;
    }
  }
}

.detail-tabs-card {
  padding: 10px 14px;
}

.sub-filter-bar {
  margin-bottom: 10px;
}

.sub-pagination-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.request-no-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.status-dialog-body {
  padding: 4px 0;
}

.batch-preview-section,
.single-preview-section {
  margin-bottom: 14px;
}

.preview-section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.preview-title {
  color: #303133;
}

.batch-preview-table {
  background: #ffffff;
}

.template-item-preview-card {
  background: #fdfdfd;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  margin-top: 8px;
}

.preview-descriptions {
  margin-top: 6px;
}

.template-empty-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.sub-name-text,
.desc-val {
  margin-left: 6px;
  color: #606266;
  font-size: 12px;
}

.highlight-rule {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #303133;
}

.preview-acct-name {
  font-weight: 600;
  color: #67c23a;
}

.action-more-btn {
  padding: 4px 6px;
  height: 28px;
  border-radius: 4px;
}

.action-more-btn:hover {
  background-color: var(--el-color-primary-light-9);
}
</style>

