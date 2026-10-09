<!-- 文件路径：src/views/business/transfer/index.vue -->
<template>
  <div class="transfer-business-page">
    <!-- ==================== 顶部全局控制与状态栏 ==================== -->
    <div class="fin-card header-bar-card">
      <div class="header-bar-content">
        <div class="left-system-date">
          <div class="date-label-group">
            <span class="label">当前系统会计日：</span>
            <span class="date-value mono">{{ systemAccountingDate || '--' }}</span>
            <el-tag :type="systemAccountingDate ? 'success' : 'info'" size="small" effect="plain">
              {{ systemAccountingDate ? '已联机' : '加载中' }}
            </el-tag>
          </div>
          <div class="sub-tip">
            期末结转是日终核算与会计期间结账的核心步骤，系统基于损益与成本结转规则自动生成结转凭证并清零损益余额。
          </div>
        </div>

        <div class="right-quick-actions">
          <el-button :icon="Setting" plain @click="goToRuleConfig">
            规则配置管理
          </el-button>
          <el-button :icon="Refresh" plain :loading="loadingSystemInfo" @click="fetchSystemInfo">
            刷新
          </el-button>
        </div>
      </div>
    </div>

    <!-- ==================== 主体工作区 Tabs ==================== -->
    <div class="fin-card main-tabs-card">
      <el-tabs v-model="activeTabName" class="transfer-tabs" @tab-change="handleTabChange">
        <!-- ==================== Tab 1: 期末结转向导工作台 ==================== -->
        <el-tab-pane label="期末结转工作台" name="workbench">
          <!-- 步骤导航条 -->
          <div class="wizard-steps-container">
            <el-steps :active="wizardStep" finish-status="success" align-center class="custom-steps">
              <el-step title="选择期间与规则" description="设定会计日并勾选结转规则" />
              <el-step title="在线试算预览" description="只读试算发生额与借贷平衡" />
              <el-step title="执行结转与出具凭证" description="正式生成凭证并更新余额" />
            </el-steps>
          </div>

          <!-- Step 1: 选择期间与规则 -->
          <div v-if="wizardStep === 0" class="step-content-section step-1-pane">
            <div class="section-title">
              <span class="title-text">第一步：配置结转执行参数</span>
              <span class="title-hint">请确认结转会计日期并勾选需要执行的规则</span>
            </div>

            <el-form :model="execForm" label-width="110px" class="exec-config-form">
              <el-row :gutter="24">
                <el-col :xs="24" :sm="12" :md="8">
                  <el-form-item label="结转会计日" required>
                    <el-date-picker
                      v-model="execForm.accountingDate"
                      type="date"
                      placeholder="请选择会计日期"
                      value-format="YYYY-MM-DD"
                      :clearable="false"
                      style="width: 100%;"
                    />
                  </el-form-item>
                </el-col>

                <el-col :xs="24" :sm="12" :md="6">
                  <el-form-item label="结转类型过滤">
                    <el-select
                      v-model="ruleFilterType"
                      placeholder="全部类型"
                      clearable
                      style="width: 100%;"
                      @change="filterRules"
                    >
                      <el-option label="全部类型" :value="0" />
                      <el-option label="损益结转" :value="TransferTypeEnum.PROFIT_LOSS" />
                      <el-option label="成本结转" :value="TransferTypeEnum.COST" />
                      <el-option label="自定义结转" :value="TransferTypeEnum.CUSTOM" />
                    </el-select>
                  </el-form-item>
                </el-col>

                <el-col :xs="24" :sm="12" :md="6">
                  <el-form-item label="结转周期过滤">
                    <el-select
                      v-model="ruleFilterCycle"
                      placeholder="全部周期"
                      clearable
                      style="width: 100%;"
                      @change="filterRules"
                    >
                      <el-option label="全部周期" :value="0" />
                      <el-option label="每日/日结" :value="1" />
                      <el-option label="月末/月结" :value="2" />
                      <el-option label="季末/季结" :value="3" />
                      <el-option label="年末/年结" :value="4" />
                      <el-option label="仅手动/自定义" :value="5" />
                    </el-select>
                  </el-form-item>
                </el-col>

                <el-col :xs="24" :sm="12" :md="6">
                  <el-form-item label="强制重试模式">
                    <div class="force-retry-box">
                      <el-switch
                        v-model="execForm.forceRetry"
                        active-text="开启"
                        inactive-text="关闭"
                      />
                      <el-tooltip
                        content="开启后，若当天该规则已有历史结转凭证，将自动清理历史重跑结转"
                        placement="top"
                      >
                        <el-icon class="help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>

            <!-- 规则选择表格 -->
            <div class="rules-table-wrapper">
              <div class="table-toolbar">
                <div class="toolbar-left">
                  <span class="selection-count">
                    已勾选 <strong>{{ selectedRules.length }}</strong> / {{ displayRules.length }} 条有效规则
                  </span>
                  <el-button link type="primary" size="small" @click="selectAllActiveRules">
                    全选启用规则
                  </el-button>
                  <el-button link type="info" size="small" @click="clearRuleSelection">
                    清空选择
                  </el-button>
                </div>
                <div class="toolbar-right">
                  <el-tag type="info" size="small">注：停用状态的规则不会参与结转执行</el-tag>
                </div>
              </div>

              <el-table
                ref="ruleTableRef"
                v-loading="loadingRules"
                :data="displayRules"
                row-key="ruleCode"
                border
                stripe
                size="default"
                @selection-change="handleRuleSelectionChange"
              >
                <el-table-column type="selection" width="50" align="center" :selectable="checkRuleSelectable" />
                <el-table-column prop="executeOrder" label="顺序" width="70" align="center" sortable />
                <el-table-column prop="ruleCode" label="规则编码" width="160" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="mono">{{ row.ruleCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="transferType" label="规则类型" width="105" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getTransferTypeTag(row.transferType)" size="small">
                      {{ row.transferTypeDesc || formatTransferType(row.transferType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="periodCycle" label="结转周期" width="115" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getPeriodCycleTag(row.periodCycle)" effect="plain" size="small">
                      {{ row.periodCycleDesc || formatPeriodCycle(row.periodCycle) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="autoTransfer" label="自动结转" width="95" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.autoTransfer ? 'success' : 'info'" size="small">
                      {{ row.autoTransfer ? '支持' : '仅手动' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceSubjectCode" label="源科目通配范围" width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="mono font-semibold text-blue">{{ row.sourceSubjectCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="targetSubjectCode" label="目标结转科目" min-width="170" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="mono">{{ row.targetSubjectCode }}</span>
                    <span v-if="row.targetSubjectName" class="sub-text"> ({{ row.targetSubjectName }})</span>
                  </template>
                </el-table-column>
                <el-table-column prop="transferDirection" label="结转方向" width="150" align="center">

                  <template #default="{ row }">
                    <el-tag
                      :type="row.transferDirection === TransferDirectionEnum.DEBIT_TO_CREDIT ? 'warning' : 'primary'"
                      size="small"
                      effect="plain"
                    >
                      {{ row.transferDirectionDesc || (row.transferDirection === 1 ? '借方余额转贷方' : '贷方余额转借方') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                      {{ row.status === 1 ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- 第一步操作栏 -->
            <div class="wizard-actions-bar">
              <el-button
                type="primary"
                size="large"
                :icon="Search"
                :loading="loadingPreview"
                :disabled="selectedRules.length === 0"
                @click="startPreviewCalculation"
              >
                开始在线试算预览 ({{ selectedRules.length }})
              </el-button>
            </div>
          </div>

          <!-- Step 2: 在线试算分录预览 -->
          <div v-else-if="wizardStep === 1" class="step-content-section step-2-pane">
            <div class="section-title">
              <div class="title-left">
                <span class="title-text">第二步：期末结转试算分录预览（Dry Run）</span>
                <span class="title-hint">
                  只读计算模拟生成的凭证分录与借贷平衡性，未在数据库落库
                </span>
              </div>
              <div class="title-right">
                <el-button :icon="Back" @click="wizardStep = 0">
                  返回修改参数
                </el-button>
                <el-button :icon="Refresh" :loading="loadingPreview" @click="startPreviewCalculation">
                  重新试算
                </el-button>
              </div>
            </div>

            <!-- 试算结果宏观看板 -->
            <div v-if="previewResult" class="preview-metrics-banner">
              <div class="metric-card">
                <div class="metric-label">结转会计日期</div>
                <div class="metric-value mono">{{ previewResult.accountingDate }}</div>
              </div>

              <div class="metric-card">
                <div class="metric-label">参与试算规则</div>
                <div class="metric-value mono">{{ previewResult.rulePreviews?.length || 0 }} <span class="unit">条</span></div>
              </div>

              <div class="metric-card highlight">
                <div class="metric-label">预计结转总金额</div>
                <div class="metric-value mono">¥ {{ formatAmount(previewResult.grandTotalAmount) }}</div>
              </div>

              <div class="metric-card balance-card" :class="{ 'is-balanced': previewResult.allBalanced, 'is-imbalanced': !previewResult.allBalanced }">
                <div class="metric-label">全局试算平衡检验</div>
                <div class="balance-status-tag">
                  <el-icon v-if="previewResult.allBalanced" :size="18" color="#67c23a"><CircleCheckFilled /></el-icon>
                  <el-icon v-else :size="18" color="#f56c6c"><CircleCloseFilled /></el-icon>
                  <span class="status-text">{{ previewResult.allBalanced ? '借贷试算平衡' : '借贷轧差不平衡' }}</span>
                </div>
              </div>
            </div>

            <!-- 分规则试算明细折叠面板 -->
            <div v-if="previewResult && previewResult.rulePreviews" class="preview-rules-list">
              <el-collapse v-model="activeCollapseNames" class="custom-collapse">
                <el-collapse-item
                  v-for="(rulePreview, idx) in previewResult.rulePreviews"
                  :key="rulePreview.ruleCode"
                  :name="rulePreview.ruleCode"
                  class="rule-collapse-item"
                >
                  <template #title>
                    <div class="collapse-header-row">
                      <div class="rule-title-group">
                        <span class="rule-badge">{{ idx + 1 }}</span>
                        <span class="rule-name">{{ rulePreview.ruleName }}</span>
                        <span class="rule-code mono">[{{ rulePreview.ruleCode }}]</span>
                        <el-tag :type="getTransferTypeTag(rulePreview.transferType)" size="small">
                          {{ rulePreview.transferTypeDesc }}
                        </el-tag>
                        <el-tag :type="getPeriodCycleTag(rulePreview.periodCycle)" size="small" effect="plain">
                          {{ rulePreview.periodCycleDesc || formatPeriodCycle(rulePreview.periodCycle) }}
                        </el-tag>
                        <el-tag :type="rulePreview.autoTransfer ? 'success' : 'info'" size="small">
                          {{ rulePreview.autoTransfer ? '自动结转' : '手动结转' }}
                        </el-tag>
                      </div>


                      <div class="rule-stats-group">
                        <span class="stat-item">
                          源范围：<span class="mono text-blue">{{ rulePreview.sourceSubjectCode }}</span>
                        </span>
                        <span class="divider">→</span>
                        <span class="stat-item">
                          目标：<span class="mono">{{ rulePreview.targetSubjectCode }}</span>
                        </span>
                        <span class="divider">|</span>
                        <span class="stat-item">
                          发生额：<strong class="mono text-orange">¥ {{ formatAmount(rulePreview.totalAmount) }}</strong>
                        </span>
                        <el-tag
                          :type="rulePreview.balanced ? 'success' : 'danger'"
                          size="small"
                          effect="dark"
                          class="balance-tag"
                        >
                          {{ rulePreview.balanced ? '平' : '不平' }}
                        </el-tag>
                      </div>
                    </div>
                  </template>

                  <!-- 规则内分录表格 -->
                  <div class="collapse-body-content">
                    <div v-if="!rulePreview.entries || rulePreview.entries.length === 0" class="empty-entry-tip">
                      <el-empty description="当前会计日该规则所匹配的源科目期末余额均为 0.00，无需结转（执行时自动跳过）" :image-size="70" />
                    </div>

                    <div v-else class="entries-table-wrapper">
                      <el-table
                        :data="rulePreview.entries"
                        border
                        size="small"
                        stripe
                        class="entries-table"
                      >
                        <el-table-column prop="rowNum" label="行号" width="60" align="center" />
                        <el-table-column prop="subjectCode" label="科目编码" width="140">
                          <template #default="{ row }">
                            <span class="mono font-semibold">{{ row.subjectCode }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column prop="subjectName" label="科目名称" min-width="150" show-overflow-tooltip />
                        <el-table-column prop="accountNo" label="记账账号" min-width="170" show-overflow-tooltip>
                          <template #default="{ row }">
                            <span class="mono">{{ row.accountNo || '--' }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column prop="debitCredit" label="借贷方向" width="90" align="center">
                          <template #default="{ row }">
                            <el-tag
                              :type="row.debitCredit === 1 ? 'primary' : 'success'"
                              size="small"
                              effect="dark"
                            >
                              {{ row.debitCredit === 1 ? '借' : '贷' }}
                            </el-tag>
                          </template>
                        </el-table-column>
                        <el-table-column prop="debitAmount" label="借方金额" width="140" align="right">
                          <template #default="{ row }">
                            <span v-if="row.debitAmount !== null && row.debitAmount !== undefined" class="mono font-semibold text-primary">
                              {{ formatAmount(row.debitAmount) }}
                            </span>
                            <span v-else class="text-muted">-</span>
                          </template>
                        </el-table-column>
                        <el-table-column prop="creditAmount" label="贷方金额" width="140" align="right">
                          <template #default="{ row }">
                            <span v-if="row.creditAmount !== null && row.creditAmount !== undefined" class="mono font-semibold text-success">
                              {{ formatAmount(row.creditAmount) }}
                            </span>
                            <span v-else class="text-muted">-</span>
                          </template>
                        </el-table-column>
                        <el-table-column prop="currency" label="币种" width="70" align="center" />
                        <el-table-column prop="summary" label="摘要说明" min-width="180" show-overflow-tooltip />
                      </el-table>

                      <!-- 借贷平衡统计栏 -->
                      <div class="entry-subtotal-bar">
                        <div class="subtotal-left">
                          <span>分录合计行数：{{ rulePreview.entries.length }} 行</span>
                        </div>
                        <div class="subtotal-right">
                          <span class="amount-tag">
                            借方发生额合计：<strong class="mono text-primary">¥ {{ formatAmount(rulePreview.totalDebitAmount) }}</strong>
                          </span>
                          <span class="divider">|</span>
                          <span class="amount-tag">
                            贷方发生额合计：<strong class="mono text-success">¥ {{ formatAmount(rulePreview.totalCreditAmount) }}</strong>
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>

            <!-- 第二步操作栏 -->
            <div class="wizard-actions-bar">
              <el-button size="large" :icon="Back" @click="wizardStep = 0">
                上一步：调整选择
              </el-button>
              <el-button
                type="danger"
                size="large"
                :icon="Check"
                :loading="executingTransfer"
                :disabled="!previewResult || !previewResult.allBalanced"
                @click="confirmAndExecuteTransfer"
              >
                确认无误，正式执行期末结转
              </el-button>
            </div>
          </div>

          <!-- Step 3: 执行结果与凭证出具报告 -->
          <div v-else-if="wizardStep === 2" class="step-content-section step-3-pane">
            <div class="section-title">
              <span class="title-text">第三步：期末结转执行报告</span>
              <span class="title-hint">结转凭证已生成落库，账务核心已完成损益清算</span>
            </div>

            <div v-if="executeResult" class="execute-summary-banner">
              <el-result
                :icon="executeResult.failedCount === 0 ? 'success' : 'warning'"
                :title="executeResult.failedCount === 0 ? '期末结转执行全部成功！' : '期末结转执行完成（部分规则异常）'"
                :sub-title="`会计日期：${executeResult.accountingDate}，总耗时：${executeResult.totalDurationMs} ms`"
              >
                <template #extra>
                  <div class="summary-kpi-grid">
                    <div class="kpi-box">
                      <span class="kpi-label">执行规则总数</span>
                      <span class="kpi-num mono">{{ executeResult.totalRules }}</span>
                    </div>
                    <div class="kpi-box success">
                      <span class="kpi-label">成功条数</span>
                      <span class="kpi-num mono">{{ executeResult.successCount }}</span>
                    </div>
                    <div class="kpi-box danger">
                      <span class="kpi-label">失败条数</span>
                      <span class="kpi-num mono">{{ executeResult.failedCount }}</span>
                    </div>
                    <div class="kpi-box highlight">
                      <span class="kpi-label">累计结转金额</span>
                      <span class="kpi-num mono">¥ {{ formatAmount(executeResult.totalAmount) }}</span>
                    </div>
                  </div>
                </template>
              </el-result>
            </div>

            <!-- 执行结果明细表格 -->
            <div v-if="executeResult && executeResult.items" class="execute-items-wrapper">
              <div class="table-title">执行明细清单</div>
              <el-table :data="executeResult.items" border stripe size="default">
                <el-table-column type="index" label="序号" width="60" align="center" />
                <el-table-column prop="ruleCode" label="规则编码" width="160">
                  <template #default="{ row }">
                    <span class="mono">{{ row.ruleCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
                <el-table-column prop="transferNo" label="结转流水号" width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="mono text-blue clickable" @click="copyText(row.transferNo)">
                      {{ row.transferNo }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="voucherNo" label="生成凭证号" width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-link
                      v-if="row.voucherNo"
                      type="primary"
                      class="mono"
                      :underline="true"
                      @click="openVoucherDetail(row.voucherNo)"
                    >
                      {{ row.voucherNo }}
                    </el-link>
                    <span v-else class="text-muted">（未生成凭证/余额为0）</span>
                  </template>
                </el-table-column>
                <el-table-column prop="totalAmount" label="结转金额" width="140" align="right">
                  <template #default="{ row }">
                    <span class="mono font-semibold">¥ {{ formatAmount(row.totalAmount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 2 ? 'success' : (row.status === 3 ? 'danger' : 'info')" size="small">
                      {{ row.statusDesc || (row.status === 2 ? '成功' : '失败') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="failReason" label="备注/失败原因" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span :class="{ 'text-danger': row.status === 3 }">{{ row.failReason || '正常' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.voucherNo"
                      link
                      type="primary"
                      size="small"
                      @click="openVoucherDetail(row.voucherNo)"
                    >
                      查看凭证
                    </el-button>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- 第三步操作栏 -->
            <div class="wizard-actions-bar">
              <el-button size="large" @click="resetWizard">
                完成，重新开始
              </el-button>
              <el-button type="primary" size="large" @click="activeTabName = 'records'">
                前往结转历史审计台账
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== Tab 2: 结转历史审计台账 ==================== -->
        <el-tab-pane label="结转历史审计台账" name="records">
          <!-- 检索条件表单 -->
          <div class="records-search-bar">
            <el-form :inline="true" :model="recordQueryForm" class="search-form">
              <el-form-item label="日期范围">
                <el-date-picker
                  v-model="recordDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  style="width: 240px;"
                  @change="handleDateRangeChange"
                />
              </el-form-item>

              <el-form-item label="结转流水号">
                <el-input
                  v-model="recordQueryForm.transferNo"
                  placeholder="TRN流水号"
                  clearable
                  style="width: 170px;"
                  @keyup.enter="handleRecordSearch"
                />
              </el-form-item>

              <el-form-item label="规则编码">
                <el-input
                  v-model="recordQueryForm.ruleCode"
                  placeholder="规则编码"
                  clearable
                  style="width: 150px;"
                  @keyup.enter="handleRecordSearch"
                />
              </el-form-item>

              <el-form-item label="凭证号">
                <el-input
                  v-model="recordQueryForm.voucherNo"
                  placeholder="VCH凭证号"
                  clearable
                  style="width: 160px;"
                  @keyup.enter="handleRecordSearch"
                />
              </el-form-item>

              <el-form-item label="执行状态">
                <el-select
                  v-model="recordQueryForm.status"
                  placeholder="全部状态"
                  clearable
                  style="width: 120px;"
                  @change="handleRecordSearch"
                >
                  <el-option label="全部状态" :value="undefined" />
                  <el-option label="处理中" :value="TransferRecordStatusEnum.PROCESSING" />
                  <el-option label="成功" :value="TransferRecordStatusEnum.SUCCESS" />
                  <el-option label="失败" :value="TransferRecordStatusEnum.FAILED" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" :icon="Search" :loading="loadingRecords" @click="handleRecordSearch">
                  查询
                </el-button>
                <el-button :icon="Refresh" @click="resetRecordSearch">
                  重置
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 审计记录表格 -->
          <div class="records-table-container">
            <el-table
              v-loading="loadingRecords"
              :data="recordList"
              border
              stripe
              size="default"
            >
              <el-table-column prop="transferNo" label="结转流水号" width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="mono clickable text-blue" @click="copyText(row.transferNo)">
                    {{ row.transferNo }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="accountingDate" label="会计日期" width="110" align="center">
                <template #default="{ row }">
                  <span class="mono">{{ row.accountingDate }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ruleCode" label="规则编码" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="mono">{{ row.ruleCode }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.ruleName || '--' }}
                </template>
              </el-table-column>
              <el-table-column prop="transferType" label="类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getTransferTypeTag(row.transferType)" size="small">
                    {{ row.transferTypeDesc || formatTransferType(row.transferType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="voucherNo" label="关联凭证号" width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-link
                    v-if="row.voucherNo"
                    type="primary"
                    class="mono"
                    :underline="true"
                    @click="openVoucherDetail(row.voucherNo)"
                  >
                    {{ row.voucherNo }}
                  </el-link>
                  <span v-else class="text-muted">（未生成凭证）</span>
                </template>
              </el-table-column>
              <el-table-column prop="totalAmount" label="结转金额" width="140" align="right">
                <template #default="{ row }">
                  <span class="mono font-semibold">¥ {{ formatAmount(row.totalAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 2 ? 'success' : (row.status === 3 ? 'danger' : 'info')" size="small">
                    {{ row.statusDesc || (row.status === 2 ? '成功' : '失败') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="executeTime" label="执行时间" width="170" align="center">
                <template #default="{ row }">
                  <span class="mono text-muted">{{ row.executeTime || '--' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="failReason" label="备注/失败信息" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tooltip v-if="row.failReason" :content="row.failReason" placement="top">
                    <span class="text-danger">{{ row.failReason }}</span>
                  </el-tooltip>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="row.voucherNo"
                    link
                    type="primary"
                    size="small"
                    @click="openVoucherDetail(row.voucherNo)"
                  >
                    查看凭证
                  </el-button>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页组件 -->
            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="recordQueryForm.pageNo"
                v-model:page-size="recordQueryForm.pageSize"
                :total="recordTotal"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="fetchRecordList"
                @current-change="fetchRecordList"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 凭证档案详情弹窗 ==================== -->
    <el-dialog
      v-model="voucherDialogVisible"
      title="记账凭证全景档案详情"
      width="900px"
      destroy-on-close
      append-to-body
    >
      <div v-loading="loadingVoucherDetail" class="voucher-detail-modal-body">
        <template v-if="voucherDetail">
          <!-- 凭证主头信息 -->
          <div class="voucher-sheet-header">
            <div class="voucher-title-large">记账凭证</div>
            <div class="voucher-meta-row">
              <span class="meta-item">凭证编号：<strong class="mono">{{ voucherDetail.voucherNo }}</strong></span>
              <span class="meta-item">会计日期：<strong class="mono">{{ voucherDetail.accountingDate }}</strong></span>
              <span class="meta-item">
                状态：
                <el-tag :type="voucherDetail.status === 3 ? 'success' : 'primary'" size="small">
                  {{ voucherDetail.statusDesc || '已过账' }}
                </el-tag>
              </span>
            </div>
          </div>

          <!-- 凭证分录表格 -->
          <div class="voucher-entries-box">
            <el-table :data="voucherDetail.entries || []" border stripe size="small">
              <el-table-column prop="rowNum" label="行号" width="60" align="center" />
              <el-table-column prop="summary" label="摘要" min-width="160" show-overflow-tooltip />
              <el-table-column prop="subjectCode" label="科目编码" width="130">
                <template #default="{ row }">
                  <span class="mono">{{ row.subjectCode }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="subjectName" label="科目名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="accountNo" label="账号" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="mono">{{ row.accountNo || '--' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="借方金额" width="130" align="right">
                <template #default="{ row }">
                  <span v-if="row.debitCredit === 1" class="mono font-semibold text-primary">
                    {{ formatAmount(row.amount) }}
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="贷方金额" width="130" align="right">
                <template #default="{ row }">
                  <span v-if="row.debitCredit === 2" class="mono font-semibold text-success">
                    {{ formatAmount(row.amount) }}
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>

            <!-- 凭证总计栏 -->
            <div class="voucher-total-footer">
              <div class="footer-left">
                <span>合计大写：{{ voucherDetail.totalAmountInWords || '人民币' }}</span>
              </div>
              <div class="footer-right">
                <span>借方合计：<strong class="mono text-primary">¥ {{ formatAmount(voucherDetail.debitAmount ?? voucherDetail.amount) }}</strong></span>
                <span class="divider">|</span>
                <span>贷方合计：<strong class="mono text-success">¥ {{ formatAmount(voucherDetail.creditAmount ?? voucherDetail.amount) }}</strong></span>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="!loadingVoucherDetail">
          <el-empty description="未查询到凭证档案详情" />
        </template>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="voucherDialogVisible = false">关闭</el-button>
          <el-button type="primary" plain @click="goToVoucherManage">前往凭证管理模块</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  Refresh,
  Setting,
  Search,
  Back,
  Check,
  QuestionFilled,
  CircleCheckFilled,
  CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  TransferTypeEnum,
  TransferDirectionEnum,
  TransferRecordStatusEnum,
  type TransferRuleItem,
  type TransferPreviewResponse,
  type TransferExecuteBatchResponse,
  type TransferRecordItem,
  getTransferRulePage,
  previewTransfer,
  executeTransfer,
  getTransferRecordPage
} from '@/api/transfer'
import { getEodStatus } from '@/api/eod'
import { getVoucherDetail, type VoucherFullDetail } from '@/api/voucher'
import { formatAmount } from '@/utils/amount'
import { toast } from '@/utils/toast'

const router = useRouter()

// ==================== 全局状态与系统信息 ====================
const systemAccountingDate = ref<string>('')
const loadingSystemInfo = ref(false)
const activeTabName = ref<string>('workbench')

// ==================== Step 导航与表单状态 ====================
const wizardStep = ref<number>(0) // 0-选择, 1-预览, 2-完成

const execForm = reactive({
  accountingDate: '',
  forceRetry: false
})

const ruleFilterType = ref<number>(0)
const ruleFilterCycle = ref<number>(0)
const allActiveRules = ref<TransferRuleItem[]>([])

const loadingRules = ref(false)
const selectedRules = ref<TransferRuleItem[]>([])
const ruleTableRef = ref()

// 计算过滤后显示的规则（同时支持类型与结转周期过滤）
const displayRules = computed(() => {
  return allActiveRules.value.filter(r => {
    const matchType = !ruleFilterType.value || ruleFilterType.value === 0 || r.transferType === ruleFilterType.value
    const matchCycle = !ruleFilterCycle.value || ruleFilterCycle.value === 0 || r.periodCycle === ruleFilterCycle.value
    return matchType && matchCycle
  })
})


// ==================== 试算预览状态 ====================
const loadingPreview = ref(false)
const previewResult = ref<TransferPreviewResponse | null>(null)
const activeCollapseNames = ref<string[]>([])

// ==================== 结转执行状态 ====================
const executingTransfer = ref(false)
const executeResult = ref<TransferExecuteBatchResponse | null>(null)

// ==================== 历史审计台账状态 ====================
const loadingRecords = ref(false)
const recordList = ref<TransferRecordItem[]>([])
const recordTotal = ref(0)
const recordDateRange = ref<[string, string] | null>(null)

const recordQueryForm = reactive({
  pageNo: 1,
  pageSize: 10,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  transferNo: '',
  ruleCode: '',
  voucherNo: '',
  status: undefined as number | undefined
})

// ==================== 凭证档案弹窗状态 ====================
const voucherDialogVisible = ref(false)
const loadingVoucherDetail = ref(false)
const voucherDetail = ref<VoucherFullDetail | null>(null)

// ==================== 生命周期钩子 ====================
onMounted(async () => {
  await fetchSystemInfo()
  await fetchRuleList()
})

// ==================== 系统信息与规则列表加载 ====================
async function fetchSystemInfo() {
  loadingSystemInfo.value = true
  try {
    const res = await getEodStatus()
    if (res && res.accountingDate) {
      systemAccountingDate.value = res.accountingDate
      if (!execForm.accountingDate) {
        execForm.accountingDate = res.accountingDate
      }
    }
  } catch {
    // 降级使用当天日期
    if (!systemAccountingDate.value) {
      const today = new Date().toISOString().split('T')[0]
      systemAccountingDate.value = today
      if (!execForm.accountingDate) {
        execForm.accountingDate = today
      }
    }
  } finally {
    loadingSystemInfo.value = false
  }
}

async function fetchRuleList() {
  loadingRules.value = true
  try {
    const res = await getTransferRulePage({
      pageNo: 1,
      pageSize: 100
    })
    allActiveRules.value = res.records || []
    // 默认全选所有启用的规则
    selectAllActiveRules()
  } catch (err: any) {
    toast.error(err.message || '加载结转规则列表失败')
  } finally {
    loadingRules.value = false
  }
}

function selectAllActiveRules() {
  const activeOnly = displayRules.value.filter(r => r.status === 1)
  selectedRules.value = activeOnly
  ruleTableRef.value?.clearSelection()
  activeOnly.forEach(row => {
    ruleTableRef.value?.toggleRowSelection(row, true)
  })
}

function clearRuleSelection() {
  selectedRules.value = []
  ruleTableRef.value?.clearSelection()
}

function checkRuleSelectable(row: TransferRuleItem) {
  return row.status === 1
}

function handleRuleSelectionChange(val: TransferRuleItem[]) {
  selectedRules.value = val
}

function filterRules() {
  // 过滤后重新联动选择
  selectAllActiveRules()
}


// ==================== 试算预览 (Dry Run) ====================
async function startPreviewCalculation() {
  if (!execForm.accountingDate) {
    toast.warning('请选择结转会计日期')
    return
  }
  if (selectedRules.value.length === 0) {
    toast.warning('请至少勾选一条启用的结转规则')
    return
  }

  loadingPreview.value = true
  try {
    // 如果勾选了全部或部分，单条或批量。后端 previewTransfer 接收 ruleCode 或全量。
    // 若只选了一条规则，传 ruleCode；若选了多条，传空以全量试算，并在前端匹配已选规则。
    const singleRuleCode = selectedRules.value.length === 1 ? selectedRules.value[0].ruleCode : undefined

    const res = await previewTransfer({
      accountingDate: execForm.accountingDate,
      ruleCode: singleRuleCode,
      forceRetry: execForm.forceRetry
    })

    // 若选了部分规则，过滤只保留选中的规则预览
    if (singleRuleCode) {
      previewResult.value = res
    } else {
      const selectedCodes = new Set(selectedRules.value.map(r => r.ruleCode))
      const filteredRulePreviews = (res.rulePreviews || []).filter(r => selectedCodes.has(r.ruleCode))
      const subTotalAmount = filteredRulePreviews.reduce((sum, item) => sum + (item.totalAmount || 0), 0)
      const isSubAllBalanced = filteredRulePreviews.every(item => item.balanced)

      previewResult.value = {
        ...res,
        rulePreviews: filteredRulePreviews,
        grandTotalAmount: subTotalAmount,
        allBalanced: isSubAllBalanced
      }
    }

    // 默认展开所有有发生额的规则折叠面板
    activeCollapseNames.value = (previewResult.value?.rulePreviews || [])
      .filter(r => r.entries && r.entries.length > 0)
      .map(r => r.ruleCode)

    wizardStep.value = 1
    toast.success('期末结转试算分录生成成功')
  } catch (err: any) {
    toast.error(err.message || '结转试算预览失败')
  } finally {
    loadingPreview.value = false
  }
}

// ==================== 正式执行结转 ====================
async function confirmAndExecuteTransfer() {
  if (!previewResult.value) {
    toast.warning('未获取试算预览结果，无法执行')
    return
  }
  if (!previewResult.value.allBalanced) {
    toast.error('当前试算借贷不平衡，账务安全核心已阻断结转执行！')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定对会计日【${execForm.accountingDate}】执行期末结转吗？将正式生成记账凭证并轧差清零损益余额。`,
      '结转执行确认',
      {
        confirmButtonText: '立即执行',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  executingTransfer.value = true
  try {
    const singleRuleCode = selectedRules.value.length === 1 ? selectedRules.value[0].ruleCode : undefined
    const res = await executeTransfer({
      accountingDate: execForm.accountingDate,
      ruleCode: singleRuleCode,
      forceRetry: execForm.forceRetry
    })

    executeResult.value = res
    wizardStep.value = 2
    toast.success('期末结转执行完成！')
  } catch (err: any) {
    toast.error(err.message || '期末结转执行失败')
  } finally {
    executingTransfer.value = false
  }
}

function resetWizard() {
  wizardStep.value = 0
  previewResult.value = null
  executeResult.value = null
  fetchRuleList()
}

// ==================== 历史审计台账查询 ====================
function handleTabChange(tabName: string) {
  if (tabName === 'records') {
    fetchRecordList()
  }
}

function handleDateRangeChange(val: [string, string] | null) {
  if (val && val.length === 2) {
    recordQueryForm.startDate = val[0]
    recordQueryForm.endDate = val[1]
  } else {
    recordQueryForm.startDate = undefined
    recordQueryForm.endDate = undefined
  }
}

function handleRecordSearch() {
  recordQueryForm.pageNo = 1
  fetchRecordList()
}

function resetRecordSearch() {
  recordDateRange.value = null
  recordQueryForm.startDate = undefined
  recordQueryForm.endDate = undefined
  recordQueryForm.transferNo = ''
  recordQueryForm.ruleCode = ''
  recordQueryForm.voucherNo = ''
  recordQueryForm.status = undefined
  recordQueryForm.pageNo = 1
  fetchRecordList()
}

async function fetchRecordList() {
  loadingRecords.value = true
  try {
    const res = await getTransferRecordPage(recordQueryForm)
    recordList.value = res.records || []
    recordTotal.value = res.total || 0
  } catch (err: any) {
    toast.error(err.message || '加载结转审计记录失败')
  } finally {
    loadingRecords.value = false
  }
}

// ==================== 凭证档案弹窗查看 ====================
async function openVoucherDetail(voucherNo: string) {
  if (!voucherNo) return
  voucherDialogVisible.value = true
  loadingVoucherDetail.value = true
  voucherDetail.value = null
  try {
    const res = await getVoucherDetail(voucherNo)
    voucherDetail.value = res
  } catch (err: any) {
    toast.error(err.message || '获取凭证档案详情失败')
  } finally {
    loadingVoucherDetail.value = false
  }
}

function goToVoucherManage() {
  voucherDialogVisible.value = false
  router.push('/business/voucher')
}

function goToRuleConfig() {
  router.push('/config/transfer-rule')
}

// ==================== 工具辅助方法 ====================
function copyText(text?: string) {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    toast.success('已复制到剪贴板')
  }).catch(() => {
    toast.info(`流水号：${text}`)
  })
}

function getTransferTypeTag(type: number): '' | 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (type) {
    case TransferTypeEnum.PROFIT_LOSS:
      return 'primary'
    case TransferTypeEnum.COST:
      return 'warning'
    case TransferTypeEnum.CUSTOM:
      return 'info'
    default:
      return 'info'
  }
}


function formatTransferType(type: number): string {
  switch (type) {
    case TransferTypeEnum.PROFIT_LOSS:
      return '损益结转'
    case TransferTypeEnum.COST:
      return '成本结转'
    case TransferTypeEnum.CUSTOM:
      return '自定义结转'
    default:
      return '未知类型'
  }
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
</script>


<style scoped>
.transfer-business-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fin-card {
  background: #ffffff;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

/* ==================== 顶部状态条 ==================== */
.header-bar-card {
  padding: 16px 20px;
}

.header-bar-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.date-label-group {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
}

.date-label-group .label {
  color: #303133;
  font-weight: 500;
}

.date-label-group .date-value {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  letter-spacing: 0.5px;
}

.sub-tip {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.right-quick-actions {
  display: flex;
  gap: 10px;
}

/* ==================== 主 Tabs 卡片 ==================== */
.main-tabs-card {
  padding: 16px 20px;
}

.transfer-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
}

/* ==================== 向导步骤条 ==================== */
.wizard-steps-container {
  padding: 24px 40px 16px 40px;
  background: #f8fafc;
  border-radius: 6px;
  margin-bottom: 24px;
}

.step-content-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.section-title .title-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-right: 12px;
}

.section-title .title-hint {
  font-size: 13px;
  color: #909399;
}

.exec-config-form {
  margin-top: 8px;
}

.force-retry-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-icon {
  color: #909399;
  cursor: pointer;
}

/* 规则表格包装 */
.rules-table-wrapper {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}

.selection-count {
  font-size: 13px;
  color: #606266;
  margin-right: 12px;
}

.wizard-actions-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px dashed #dcdfe6;
}

/* ==================== Step 2 试算预览看板 ==================== */
.preview-metrics-banner {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 16px 20px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-card .metric-label {
  font-size: 13px;
  color: #6c757d;
}

.metric-card .metric-value {
  font-size: 22px;
  font-weight: 700;
  color: #212529;
}

.metric-card .metric-value .unit {
  font-size: 13px;
  font-weight: normal;
  color: #6c757d;
}

.metric-card.highlight .metric-value {
  color: #e6a23c;
}

.metric-card.balance-card {
  justify-content: space-between;
}

.balance-status-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.balance-card.is-balanced {
  background: #f0f9eb;
  border-color: #e1f3d8;
  color: #67c23a;
}

.balance-card.is-imbalanced {
  background: #fef0f0;
  border-color: #fde2e2;
  color: #f56c6c;
}

/* 折叠面板美化 */
.custom-collapse {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.collapse-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 16px;
}

.rule-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rule-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: #409eff;
  color: #ffffff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
}

.rule-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.rule-code {
  color: #909399;
  font-size: 13px;
}

.rule-stats-group {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.divider {
  color: #dcdfe6;
}

.collapse-body-content {
  padding: 12px 16px 16px 16px;
  background: #fafbfc;
}

.entry-subtotal-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #f4f4f5;
  border: 1px solid #e4e7ed;
  border-top: none;
  font-size: 13px;
}

.subtotal-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ==================== Step 3 执行结果 ==================== */
.execute-summary-banner {
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  padding: 10px;
}

.summary-kpi-grid {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-top: 16px;
}

.kpi-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.kpi-box .kpi-label {
  font-size: 13px;
  color: #909399;
}

.kpi-box .kpi-num {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.kpi-box.success .kpi-num {
  color: #67c23a;
}

.kpi-box.danger .kpi-num {
  color: #f56c6c;
}

.kpi-box.highlight .kpi-num {
  color: #e6a23c;
}

.execute-items-wrapper {
  margin-top: 16px;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

/* ==================== Tab 2 审计台账 ==================== */
.records-search-bar {
  margin-bottom: 16px;
}

.records-table-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

/* ==================== 凭证档案弹窗 ==================== */
.voucher-sheet-header {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 2px solid #303133;
  margin-bottom: 16px;
}

.voucher-title-large {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #303133;
  margin-bottom: 10px;
}

.voucher-meta-row {
  display: flex;
  justify-content: space-around;
  font-size: 13px;
  color: #606266;
}

.voucher-total-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #f9fafc;
  border: 1px solid #ebeef5;
  border-top: none;
  font-size: 13px;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ==================== 通用辅助样式 ==================== */
.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
}

.font-semibold {
  font-weight: 600;
}

.clickable {
  cursor: pointer;
  text-decoration: underline;
}

.text-blue {
  color: #409eff;
}

.text-primary {
  color: #409eff;
}

.text-success {
  color: #67c23a;
}

.text-orange {
  color: #e6a23c;
}

.text-danger {
  color: #f56c6c;
}

.text-muted {
  color: #909399;
}
</style>
