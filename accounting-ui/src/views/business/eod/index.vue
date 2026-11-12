<!-- 文件路径：src/views/business/eod/index.vue -->
<template>
  <div class="eod-management-page">
    <!-- ==================== 顶部全局控制条 ==================== -->
    <div class="fin-card control-card">
      <el-form :inline="true" class="eod-control-form">
        <el-form-item label="会计日期">
          <el-date-picker
            v-model="currentAccountingDate"
            type="date"
            placeholder="选择会计日期"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 160px;"
            @change="handleDateChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loadingStatus" @click="fetchAllData">
            查询状态
          </el-button>
          <el-button :icon="Refresh" :loading="loadingStatus || loadingPreCheck || loadingTrialBalance" @click="fetchAllData">
            刷新
          </el-button>
        </el-form-item>

        <div class="header-action-group">
          <el-button
            type="warning"
            plain
            :icon="Switch"
            @click="openSwitchDateDialog"
          >
            手动瞬间切日
          </el-button>
          <el-button
            type="danger"
            :icon="VideoPlay"
            :loading="executingEod"
            @click="openExecuteDialog"
          >
            手动执行日切
          </el-button>
        </div>
      </el-form>
    </div>

    <!-- ==================== 卡片 1: 全局会计日期与日切执行状态 ==================== -->
    <div class="fin-card banner-card">
      <div class="banner-header">
        <div class="header-left">
          <div class="date-badge-wrap">
            <span class="label">当前会计日 (T日)：</span>
            <span class="date-value mono">{{ eodStatus?.accountingDate || currentAccountingDate || '--' }}</span>
            <el-tag :type="getStatusTagType(eodStatus?.status)" size="default" effect="dark" class="status-tag">
              {{ eodStatus?.statusDesc || '未获取' }}
            </el-tag>
          </div>
          <div class="sub-text">
            系统按自然日或运维指令切换全局会计日期，日终按五阶段实施存量清理、余额快照、试算平衡、结转及归档。
          </div>
        </div>

        <div class="header-right-kpi">
          <div class="kpi-block">
            <span class="kpi-label">切日完成时间</span>
            <span class="kpi-value mono">{{ formatDateTime(eodStatus?.switchDateTime) }}</span>
          </div>
          <el-divider direction="vertical" class="kpi-divider" />
          <div class="kpi-block">
            <span class="kpi-label">归档完成时间</span>
            <span class="kpi-value mono">{{ formatDateTime(eodStatus?.archiveDateTime) }}</span>
          </div>
          <el-divider direction="vertical" class="kpi-divider" />
          <div class="kpi-block">
            <span class="kpi-label">总执行耗时</span>
            <span class="kpi-value mono highlight">{{ formatDuration(eodStatus?.totalDurationMs) }}</span>
          </div>
        </div>
      </div>

      <!-- 失败告警提示 -->
      <div v-if="eodStatus?.status === 9" class="failed-alert-wrap">
        <el-alert
          type="error"
          show-icon
          :closable="false"
          :title="`日切异常阻断【失败阶段：${eodStatus?.failedStage || '未知'}】`"
          :description="eodStatus?.failReason || '日切执行失败，请检查各阶段检查项及数据库日志。'"
        />
      </div>

      <!-- 日切五阶段执行流程步骤条 -->
      <div class="eod-steps-container">
        <el-steps :active="pipelineStepIndex" :process-status="pipelineProcessStatus" align-center finish-status="success">
          <el-step title="瞬间切日" description="全局会计日 T→T+1" />
          <el-step title="存量清理" description="前置无积压事务/明细" />
          <el-step title="余额快照" description="生成当日末余额快照" />
          <el-step title="试算平衡" description="借贷与总分平衡校验" />
          <el-step title="期末结转" description="损益/自定义规则结转" />
          <el-step title="账务归档" description="T日账务只读封闭归档" />
          <el-step title="日切完成" description="全流程闭环就绪" />
        </el-steps>
      </div>
    </div>

    <!-- ==================== 卡片 2 & 3: 并列布局（前置检查 + 试算平衡概览） ==================== -->
    <el-row :gutter="16">
      <!-- 左侧：日切前置检查模块 -->
      <el-col :xs="24" :lg="10">
        <div class="fin-card section-card precheck-card" v-loading="loadingPreCheck">
          <div class="section-title-row">
            <div class="title-with-icon">
              <el-icon class="title-icon"><CircleCheck /></el-icon>
              <span class="title-text">日切前置检查诊断</span>
            </div>
            <div class="action-wrap">
              <el-tag
                :type="preCheckData?.allPassed ? 'success' : 'danger'"
                size="small"
                effect="light"
              >
                {{ preCheckData?.allPassed ? '全部通过' : '存在阻断项' }}
              </el-tag>
              <el-button
                size="small"
                :icon="RefreshRight"
                :loading="loadingPreCheck"
                @click="fetchPreCheck"
              >
                重新诊断
              </el-button>
            </div>
          </div>

          <div class="precheck-summary-tip" :class="{ 'is-passed': preCheckData?.allPassed }">
            <el-icon v-if="preCheckData?.allPassed" class="summary-icon pass-icon"><SuccessFilled /></el-icon>
            <el-icon v-else class="summary-icon fail-icon"><WarningFilled /></el-icon>
            <span class="tip-content">
              {{ preCheckData?.allPassed
                ? '前置存量指标正常：无在途事务、未过账凭证与处理中明细，允许执行日切。'
                : '检测到存在未决待入账业务或在途事务，请核实并清理后再触发日切！'
              }}
            </span>
          </div>

          <!-- 诊断指标项卡片栅格 -->
          <div class="check-items-grid">
            <div
              v-for="item in preCheckData?.checks || defaultCheckItems"
              :key="item.name"
              class="check-item-cell"
              :class="{ 'is-danger': !item.passed, 'is-success': item.passed }"
            >
              <div class="item-name">{{ formatCheckItemName(item.name) }}</div>
              <div class="item-bottom">
                <span class="item-count mono">积压：{{ item.count }} 笔</span>
                <el-tag :type="item.passed ? 'success' : 'danger'" size="small">
                  {{ item.passed ? '正常通过' : '存在积压' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧：试算平衡核心核对看板 -->
      <el-col :xs="24" :lg="14">
        <div class="fin-card section-card balance-card" v-loading="loadingTrialBalance">
          <div class="section-title-row">
            <div class="title-with-icon">
              <el-icon class="title-icon"><ScaleToOriginal /></el-icon>
              <span class="title-text">试算平衡借贷核算</span>
            </div>
            <div class="action-wrap">
              <el-tag
                :type="trialBalanceData?.passed ? 'success' : 'danger'"
                size="small"
                effect="dark"
              >
                {{ trialBalanceData?.passed ? '借贷平衡 · 试算通过' : '借贷不平衡 · 存在差额' }}
              </el-tag>
              <el-button
                size="small"
                :icon="RefreshRight"
                :loading="loadingTrialBalance"
                @click="fetchTrialBalance"
              >
                重新试算
              </el-button>
            </div>
          </div>

          <!-- 借贷平衡三大核心指标 -->
          <div class="trial-balance-stats">
            <div class="stat-box debit-box">
              <span class="stat-label">借方总发生额 (Debit)</span>
              <div class="stat-amount">
                <AmountDisplay :value="trialBalanceData?.totalDebit || 0" prefix="¥ " align="left" />
              </div>
            </div>

            <div class="stat-operator">=</div>

            <div class="stat-box credit-box">
              <span class="stat-label">贷方总发生额 (Credit)</span>
              <div class="stat-amount">
                <AmountDisplay :value="trialBalanceData?.totalCredit || 0" prefix="¥ " align="left" />
              </div>
            </div>

            <div class="stat-operator">|</div>

            <div class="stat-box diff-box" :class="{ 'is-imbalanced': !trialBalanceData?.passed }">
              <span class="stat-label">借贷差额 (Debit - Credit)</span>
              <div class="stat-amount">
                <AmountDisplay :value="trialBalanceData?.diff || 0" prefix="¥ " align="left" />
              </div>
            </div>
          </div>

          <!-- 不平衡科目警示条（若有） -->
          <div v-if="trialBalanceData && !trialBalanceData.passed" class="imbalanced-alert-box">
            <el-alert
              type="error"
              :closable="false"
              show-icon
              title="试算借贷不平衡阻断！"
              :description="`检测到借贷总发生额存在差额（¥ ${trialBalanceData.diff || 0}），借方总发生额与贷方总发生额不相等，阻断日终归档。`"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ==================== 卡片 3.5: 总账分户核对 (总分核对看板与异常诊断) ==================== -->
    <div class="fin-card section-card gl-reconciliation-card" v-loading="loadingGlReconciliation">
      <div class="section-title-row">
        <div class="title-with-icon">
          <el-icon class="title-icon"><DataAnalysis /></el-icon>
          <span class="title-text">总账分户核对诊断 (总分核对)</span>
          <span class="table-subtitle" style="margin-left: 10px; font-size: 13px; color: #909399;">
            校验各科目日末总账余额与分户账户实时余额是否完全一致
          </span>
        </div>
        <div class="action-wrap">
          <el-tag
            :type="glReconciliationData?.passed ? 'success' : 'danger'"
            size="small"
            effect="dark"
          >
            {{ glReconciliationData?.passed ? '总分一致 · 核对通过' : '总分不平 · 存在差额' }}
          </el-tag>
          <el-button
            size="small"
            :icon="RefreshRight"
            :loading="loadingGlReconciliation"
            @click="fetchGlReconciliation"
          >
            重新核对
          </el-button>
          <el-button
            v-if="glReconciliationData && !glReconciliationData.passed"
            type="danger"
            size="small"
            :icon="Tools"
            :loading="repairingGl"
            @click="handleRepairAll"
          >
            一键智能修复
          </el-button>
        </div>
      </div>

      <!-- 总分核对三大核心指标 -->
      <div class="trial-balance-stats gl-stats">
        <div class="stat-box debit-box">
          <span class="stat-label">科目日末总账余额 (GL Balance)</span>
          <div class="stat-amount">
            <AmountDisplay :value="glReconciliationData?.totalGlBalance || 0" prefix="¥ " align="left" />
          </div>
        </div>

        <div class="stat-operator">=</div>

        <div class="stat-box credit-box">
          <span class="stat-label">分户账户实时余额 (Account Balance)</span>
          <div class="stat-amount">
            <AmountDisplay :value="glReconciliationData?.totalAccountBalance || 0" prefix="¥ " align="left" />
          </div>
        </div>

        <div class="stat-operator">|</div>

        <div class="stat-box diff-box" :class="{ 'is-imbalanced': !glReconciliationData?.passed }">
          <span class="stat-label">总分核对总差额 (|GL - Account|)</span>
          <div class="stat-amount">
            <AmountDisplay :value="glReconciliationData?.totalDiff || 0" prefix="¥ " align="left" />
          </div>
        </div>
      </div>

      <!-- 总分不平告警与修复提示条 -->
      <div v-if="glReconciliationData && !glReconciliationData.passed" class="imbalanced-alert-box">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          title="总分核对失败阻断！"
          :description="`检测到 ${glReconciliationData.diffDetails?.length || 0} 个科目总账与分户存在差额（总差额 ¥ ${glReconciliationData.totalDiff || 0}）。通常由于历史会计日未日切导致日余额断档，或跨日消息异步记账产生期初断层。点击【一键智能修复】可自动追溯并补全断档日余额！`"
        />
      </div>

      <!-- 总分核对科目明细列表 -->
      <div class="gl-table-wrap" style="margin-top: 14px;">
        <el-table
          :data="glReconciliationData?.diffDetails || []"
          border
          stripe
          size="small"
          class="fin-table"
          empty-text="总账与分户实时余额完全核对一致，无异常科目"
        >
          <el-table-column type="index" label="序号" width="55" align="center" />

          <el-table-column prop="subjectCode" label="科目代码" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono code-link" @click="copyText(row.subjectCode, '科目代码')">
                {{ row.subjectCode }}
              </span>
              <el-button
                link
                type="primary"
                size="small"
                :icon="CopyDocument"
                title="复制科目代码"
                @click="copyText(row.subjectCode, '科目代码')"
              />
            </template>
          </el-table-column>

          <el-table-column prop="subjectName" label="科目名称" min-width="150" show-overflow-tooltip />

          <el-table-column prop="accountTypeDesc" label="账户类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.accountTypeDesc || '内部/分户' }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="glEndBalance" label="科目日末总账 (GL)" min-width="150" align="right">
            <template #default="{ row }">
              <AmountDisplay :value="row.glEndBalance" align="right" />
            </template>
          </el-table-column>

          <el-table-column prop="accountBalance" label="分户实时余额 (Account)" min-width="150" align="right">
            <template #default="{ row }">
              <AmountDisplay :value="row.accountBalance" align="right" />
            </template>
          </el-table-column>

          <el-table-column prop="diff" label="核对差额" min-width="140" align="right">
            <template #default="{ row }">
              <span class="text-danger mono font-bold">
                <AmountDisplay :value="row.diff" align="right" />
              </span>
            </template>
          </el-table-column>

          <el-table-column prop="accountCount" label="关联分户数" width="95" align="center">
            <template #default="{ row }">
              <span class="mono">{{ row.accountCount || 0 }} 户</span>
            </template>
          </el-table-column>

          <el-table-column prop="reasonDesc" label="智能诊断与根因分析" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="reason-cell">
                <el-icon class="text-warning"><Warning /></el-icon>
                <span class="reason-text">{{ row.reasonDesc || '总账期末余额与分户账户当前余额不一致' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :icon="View"
                @click="openAccountDrawer(row)"
              >
                查看分户
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :icon="Tools"
                :loading="repairingGl"
                @click="handleRepairSubject(row)"
              >
                修复此科目
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ==================== 卡片 4: 科目明细发生额试算表格 ==================== -->
    <div class="fin-card table-card" v-loading="loadingTrialBalance">
      <div class="table-header-bar">
        <div class="bar-left">
          <span class="table-title">科目发生额试算明细</span>
          <span class="table-subtitle">统计当日已过账分录借贷汇总</span>
        </div>

        <div class="bar-right">
          <el-checkbox v-model="onlyShowImbalanced" :disabled="!hasImbalancedSubjects">
            仅看差异科目 ({{ !trialBalanceData?.passed ? (trialBalanceData?.imbalancedSubjects?.length || 0) : 0 }})
          </el-checkbox>
          <el-input
            v-model="subjectFilterKeyword"
            placeholder="搜索科目代码或科目名称"
            prefix-icon="Search"
            clearable
            style="width: 220px; margin-left: 12px;"
          />
        </div>
      </div>

      <el-table
        :data="filteredSubjectList"
        border
        stripe
        size="small"
        class="fin-table"
        empty-text="暂无科目试算数据"
      >
        <el-table-column type="index" label="序号" width="55" align="center" />

        <el-table-column prop="subjectCode" label="科目代码" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono code-link" @click="copyText(row.subjectCode, '科目代码')">
              {{ row.subjectCode }}
            </span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              title="复制科目代码"
              @click="copyText(row.subjectCode, '科目代码')"
            />
          </template>
        </el-table-column>

        <el-table-column prop="subjectName" label="科目名称" min-width="180" show-overflow-tooltip />

        <el-table-column prop="totalDebit" label="借方发生额 (Debit)" min-width="160" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.totalDebit" align="right" />
          </template>
        </el-table-column>

        <el-table-column prop="totalCredit" label="贷方发生额 (Credit)" min-width="160" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.totalCredit" align="right" />
          </template>
        </el-table-column>

        <el-table-column prop="netDiff" label="净差额 (Debit - Credit)" min-width="160" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.netDiff" align="right" />
          </template>
        </el-table-column>

        <el-table-column label="发生方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getNetDirectionType(row.netDiff)"
              size="small"
            >
              {{ getNetDirectionText(row.netDiff) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="平衡校验" width="110" align="center">
          <template #default>
            <el-tag
              :type="trialBalanceData?.passed ? 'success' : 'danger'"
              size="small"
            >
              {{ trialBalanceData?.passed ? '平衡' : '总账未平' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ==================== 对话框 1: 手动瞬间切日 ==================== -->
    <el-dialog
      v-model="switchDateDialogVisible"
      title="手动全局瞬间切日"
      width="480px"
      destroy-on-close
    >
      <el-form :model="switchDateForm" label-width="110px">
        <el-form-item label="当前会计日">
          <span class="mono bold-text">{{ eodStatus?.accountingDate || currentAccountingDate }}</span>
        </el-form-item>

        <el-form-item label="目标会计日">
          <el-date-picker
            v-model="switchDateForm.targetDate"
            type="date"
            placeholder="留空则自动切换至下一天"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>

        <div class="dialog-notice">
          <el-icon><InfoFilled /></el-icon>
          <span>瞬间切日将立即更新 Redis 缓存与数据库会计日，之后发生的所有流水将进入新会计日。请确认当前日账务基本完成。</span>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="switchDateDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="switchingDate" @click="handleConfirmSwitchDate">
            确认切日
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- ==================== 对话框 2: 手动触发日切全流程 ==================== -->
    <el-dialog
      v-model="executeDialogVisible"
      title="手动触发日切总调度"
      width="520px"
      destroy-on-close
    >
      <el-form :model="executeForm" label-width="130px">
        <el-form-item label="执行会计日期" required>
          <span class="mono bold-text">{{ executeForm.accountingDate }}</span>
        </el-form-item>

        <el-form-item label="执行期末结转">
          <el-switch v-model="executeForm.executeTransfer" />
          <span class="form-item-help">试算平衡通过后自动执行损益与期末结转规则</span>
        </el-form-item>

        <el-form-item label="跳过前置检查">
          <el-switch v-model="executeForm.skipPreCheck" active-color="#f56c6c" />
          <span class="form-item-help text-danger">警告：跳过可能导致未决在途明细遗漏计算！</span>
        </el-form-item>

        <div class="dialog-alert-box">
          <el-alert
            title="日切属于核心日终任务"
            type="warning"
            :closable="false"
            show-icon
            description="日切执行将依次进行前置检查、日余额计算、快照生成、试算平衡、期末结转与账务归档。请勿重复触发。"
          />
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="executeDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="executingEod" @click="handleConfirmExecuteEod">
            开始执行日切
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- ==================== 抽屉: 日切执行结果详细报告 ==================== -->
    <el-drawer
      v-model="resultDrawerVisible"
      title="日切执行报告详情"
      size="620px"
      destroy-on-close
    >
      <div v-if="eodExecuteResult" class="eod-report-container">
        <!-- 概览看板 -->
        <div class="report-overview-box">
          <div class="overview-header">
            <span class="report-date mono">会计日：{{ eodExecuteResult.accountingDate }}</span>
            <el-tag :type="eodExecuteResult.trialBalancePassed ? 'success' : 'danger'" size="default">
              {{ eodExecuteResult.trialBalancePassed ? '执行成功 · 已归档' : '执行受阻 · 存在异常' }}
            </el-tag>
          </div>
          <div class="overview-meta">
            <span>总耗时：<strong class="mono">{{ formatDuration(eodExecuteResult.totalDurationMs) }}</strong></span>
            <span>日余额生成：<strong class="mono">{{ eodExecuteResult.balanceCount }} 笔</strong></span>
            <span>快照生成：<strong class="mono">{{ eodExecuteResult.snapshotCount }} 笔</strong></span>
          </div>
        </div>

        <!-- 阶段检查明细 -->
        <div class="report-section">
          <div class="section-badge-title">各阶段结果核对</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="前置检查">
              <el-tag :type="eodExecuteResult.preCheckPassed ? 'success' : 'danger'" size="small">
                {{ eodExecuteResult.preCheckPassed ? '通过' : '未通过' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="日余额持久化">
              <el-tag :type="eodExecuteResult.balanceCalculated ? 'success' : 'danger'" size="small">
                {{ eodExecuteResult.balanceCalculated ? `成功 (已写入 ${eodExecuteResult.balanceCount} 账户)` : '未完成' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="试算平衡">
              <el-tag :type="eodExecuteResult.trialBalancePassed ? 'success' : 'danger'" size="small">
                {{ eodExecuteResult.trialBalancePassed ? '借贷平衡通过' : '试算不平衡' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总分核对 (GL)">
              <el-tag :type="eodExecuteResult.glReconciliationPassed !== false ? 'success' : 'danger'" size="small">
                {{ eodExecuteResult.glReconciliationPassed !== false ? '总分核对通过' : '总分核对失败' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="余额快照生成">
              <el-tag :type="eodExecuteResult.snapshotGenerated ? 'success' : 'danger'" size="small">
                {{ eodExecuteResult.snapshotGenerated ? `成功 (已快照 ${eodExecuteResult.snapshotCount} 条)` : '未生成' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 期末结转明细表 -->
        <div class="report-section">
          <div class="section-badge-title">期末结转规则执行记录</div>
          <el-table
            :data="eodExecuteResult.transferResults || []"
            border
            size="small"
            empty-text="无期末结转记录或未启用结转"
          >
            <el-table-column prop="ruleCode" label="规则代码" width="100" show-overflow-tooltip />
            <el-table-column prop="ruleName" label="规则名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="voucherNo" label="生成凭证号" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.voucherNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="结转金额" width="120" align="right">
              <template #default="{ row }">
                <AmountDisplay :value="row.totalAmount" align="right" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <!-- ==================== 抽屉 2: 账户级总分核对差异穿透详情 ==================== -->
    <el-drawer
      v-model="accountDrawerVisible"
      :title="`分户账户核对穿透明细【科目：${selectedSubject?.subjectCode || ''} - ${selectedSubject?.subjectName || ''}】`"
      size="880px"
      destroy-on-close
    >
      <div class="account-drawer-content" v-loading="loadingAccounts">
        <div class="drawer-header-summary">
          <div class="summary-item">
            <span class="label">科目总账余额：</span>
            <span class="val mono"><AmountDisplay :value="selectedSubject?.glEndBalance || 0" /></span>
          </div>
          <div class="summary-item">
            <span class="label">分户合计余额：</span>
            <span class="val mono"><AmountDisplay :value="selectedSubject?.accountBalance || 0" /></span>
          </div>
          <div class="summary-item">
            <span class="label">当前总分差额：</span>
            <span class="val mono text-danger font-bold"><AmountDisplay :value="selectedSubject?.diff || 0" /></span>
          </div>
          <div class="action-btn" v-if="selectedSubject">
            <el-button
              type="danger"
              size="small"
              :icon="Tools"
              :loading="repairingGl"
              @click="handleRepairSubject(selectedSubject)"
            >
              修复本科目
            </el-button>
          </div>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 14px;"
          title="账户级穿透说明"
          description="展示本科目下所有激活分户账户在日末余额表（t_account_balance）与实时账户表（t_account）的数值对比及最近分录日期。"
        />

        <el-table
          :data="accountDiffList"
          border
          stripe
          size="small"
          class="fin-table"
          empty-text="暂无分户账户数据"
        >
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="accountNo" label="分户账号" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono code-link" @click="copyText(row.accountNo, '分户账号')">
                {{ row.accountNo }}
              </span>
              <el-button
                link
                type="primary"
                size="small"
                :icon="CopyDocument"
                @click="copyText(row.accountNo, '分户账号')"
              />
            </template>
          </el-table-column>
          <el-table-column prop="accountName" label="账户名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="glEndBalance" label="日末余额 (t_account_balance)" min-width="150" align="right">
            <template #default="{ row }">
              <AmountDisplay :value="row.glEndBalance" align="right" />
            </template>
          </el-table-column>
          <el-table-column prop="accountBalance" label="实时余额 (t_account)" min-width="150" align="right">
            <template #default="{ row }">
              <AmountDisplay :value="row.accountBalance" align="right" />
            </template>
          </el-table-column>
          <el-table-column prop="diff" label="差额" min-width="130" align="right">
            <template #default="{ row }">
              <span :class="Number(row.diff) !== 0 ? 'text-danger font-bold mono' : 'mono'">
                <AmountDisplay :value="row.diff" align="right" />
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="latestVoucherDate" label="最近分录日期" width="120" align="center">
            <template #default="{ row }">
              <span class="mono">{{ row.latestVoucherDate || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="statusDesc" label="状态诊断" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag
                :type="Number(row.diff) === 0 ? 'success' : 'danger'"
                size="small"
              >
                {{ row.statusDesc || (Number(row.diff) === 0 ? '平衡' : '存在断档差额') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <!-- ==================== 对话框 3: 一键智能修复结果报告 ==================== -->
    <el-dialog
      v-model="repairResultDialogVisible"
      title="总分核对智能修复报告"
      width="640px"
      destroy-on-close
    >
      <div v-if="repairResult" class="repair-report-box">
        <el-result
          :icon="repairResult.success ? 'success' : 'warning'"
          :title="repairResult.success ? '修复完成' : '修复处理已执行'"
          :sub-title="repairResult.message"
        >
          <template #extra>
            <div class="repair-kpis">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="补全历史断档日">
                  <span class="mono font-bold">{{ repairResult.repairedDateCount }} 天</span>
                </el-descriptions-item>
                <el-descriptions-item label="校准分户账户">
                  <span class="mono font-bold">{{ repairResult.repairedAccountCount }} 户</span>
                </el-descriptions-item>
                <el-descriptions-item label="累计重算结转金额">
                  <span class="mono font-bold">¥ {{ repairResult.repairedAmount }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="修复后剩余差额">
                  <span class="mono font-bold" :class="Number(repairResult.remainingDiff) === 0 ? 'text-success' : 'text-danger'">
                    ¥ {{ repairResult.remainingDiff }}
                  </span>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-result>

        <div class="repair-logs-card">
          <div class="log-title">执行流水日志：</div>
          <div class="log-console">
            <div v-for="(log, idx) in repairResult.repairLogs" :key="idx" class="log-line mono">
              {{ log }}
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="handleCloseRepairResult">确定并刷新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Search,
  Refresh,
  RefreshRight,
  Switch,
  VideoPlay,
  CopyDocument,
  CircleCheck,
  ScaleToOriginal,
  SuccessFilled,
  WarningFilled,
  InfoFilled,
  Tools,
  View,
  Warning,
  DataAnalysis
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { toast } from '@/utils/toast'
import {
  getEodStatus,
  switchDate,
  getPreCheckResult,
  getTrialBalance,
  executeEod,
  getGlReconciliation,
  getGlAccountDiffs,
  repairGlReconciliation,
  type EodStatusResponse,
  type EodPreCheckResponse,
  type TrialBalanceResponse,
  type EodExecuteResponse,
  type CheckItem,
  type SubjectDetail,
  type GlReconciliationResponse,
  type GlDiffDetail,
  type GlAccountDiffResponse,
  type GlRepairResponse
} from '@/api/eod'

// ==================== 响应式状态定义 ====================

const currentAccountingDate = ref<string>('')
const loadingStatus = ref<boolean>(false)
const loadingPreCheck = ref<boolean>(false)
const loadingTrialBalance = ref<boolean>(false)
const loadingGlReconciliation = ref<boolean>(false)
const repairingGl = ref<boolean>(false)
const loadingAccounts = ref<boolean>(false)
const switchingDate = ref<boolean>(false)
const executingEod = ref<boolean>(false)

// 数据源
const eodStatus = ref<EodStatusResponse | null>(null)
const preCheckData = ref<EodPreCheckResponse | null>(null)
const trialBalanceData = ref<TrialBalanceResponse | null>(null)
const glReconciliationData = ref<GlReconciliationResponse | null>(null)
const eodExecuteResult = ref<EodExecuteResponse | null>(null)

// 分户穿透与修复
const selectedSubject = ref<GlDiffDetail | null>(null)
const accountDiffList = ref<GlAccountDiffResponse[]>([])
const accountDrawerVisible = ref<boolean>(false)
const repairResultDialogVisible = ref<boolean>(false)
const repairResult = ref<GlRepairResponse | null>(null)

// 页面表格过滤与检索
const subjectFilterKeyword = ref<string>('')
const onlyShowImbalanced = ref<boolean>(false)

// 弹窗与抽屉状态
const switchDateDialogVisible = ref<boolean>(false)
const switchDateForm = ref<{ targetDate?: string }>({ targetDate: '' })

const executeDialogVisible = ref<boolean>(false)
const executeForm = ref<{
  accountingDate: string
  skipPreCheck: boolean
  executeTransfer: boolean
}>({
  accountingDate: '',
  skipPreCheck: false,
  executeTransfer: true
})

const resultDrawerVisible = ref<boolean>(false)

// 默认未诊断时的检查项占位
const defaultCheckItems: CheckItem[] = [
  { name: 'BUFFER_DETAIL', count: 0, passed: true },
  { name: 'PENDING_TRANSACTION', count: 0, passed: true },
  { name: 'UNPOSTED_VOUCHER', count: 0, passed: true },
  { name: 'PENDING_JOURNAL', count: 0, passed: true },
  { name: 'EXPIRED_FREEZE', count: 0, passed: true }
]

// ==================== 计算属性 ====================

/**
 * 是否存在不平衡科目
 */
const hasImbalancedSubjects = computed(() => {
  return trialBalanceData.value != null && !trialBalanceData.value.passed
})

/**
 * 经过检索词与不平衡开关过滤后的科目明细
 */
const filteredSubjectList = computed<SubjectDetail[]>(() => {
  if (!trialBalanceData.value?.subjectDetails) return []
  let list = trialBalanceData.value.subjectDetails

  if (onlyShowImbalanced.value) {
    if (!trialBalanceData.value.passed) {
      list = list.filter((item) => {
        const net = Number(item.netDiff) || 0
        return Math.abs(net) > 0.000001
      })
    } else {
      list = []
    }
  }

  if (subjectFilterKeyword.value.trim()) {
    const kw = subjectFilterKeyword.value.trim().toLowerCase()
    list = list.filter((item) =>
      (item.subjectCode && item.subjectCode.toLowerCase().includes(kw)) ||
      (item.subjectName && item.subjectName.toLowerCase().includes(kw))
    )
  }

  return list
})

/**
 * 流程步骤条高亮阶段映射
 */
const pipelineStepIndex = computed<number>(() => {
  if (!eodStatus.value) return 0
  const status = eodStatus.value.status
  switch (status) {
    case 1: return 0 // 未开始
    case 2: return 0 // 切日中
    case 3: return 1 // 清理中
    case 4: return 2 // 快照中
    case 5: return 3 // 试算中
    case 6: return 4 // 结转中
    case 7: return 5 // 归档中
    case 8: return 7 // 已完成
    case 9: { // 失败
      const failedStage = eodStatus.value.failedStage || ''
      if (failedStage.includes('CLEAN')) return 1
      if (failedStage.includes('SNAPSHOT')) return 2
      if (failedStage.includes('TRIAL') || failedStage.includes('RECONCIL')) return 3
      if (failedStage.includes('TRANSFER')) return 4
      if (failedStage.includes('ARCHIVE')) return 5
      return 3
    }
    default: return 0
  }
})

/**
 * 流程步骤条状态
 */
const pipelineProcessStatus = computed<'wait' | 'process' | 'finish' | 'error' | 'success'>(() => {
  if (!eodStatus.value) return 'wait'
  const status = eodStatus.value.status
  if (status === 9) return 'error'
  if (status === 8) return 'success'
  if (status >= 2 && status <= 7) return 'process'
  return 'wait'
})

// ==================== 状态展示与辅助函数 ====================

function getStatusTagType(status?: number): '' | 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 1: return 'info'
    case 2: return 'primary'
    case 3:
    case 4:
    case 5:
    case 6:
    case 7: return 'warning'
    case 8: return 'success'
    case 9: return 'danger'
    default: return 'info'
  }
}

function formatCheckItemName(name: string): string {
  const map: Record<string, string> = {
    BUFFER_DETAIL: '缓冲记账待入账明细',
    PENDING_TRANSACTION: '在途处理中业务事务',
    UNPOSTED_VOUCHER: '待过账凭证记录',
    PENDING_JOURNAL: '在途业务流水单据',
    EXPIRED_FREEZE: '已到期未解冻控制明细',
    bufferPending: '缓冲记账待入账明细',
    processingTxn: '在途处理中业务事务',
    unpostedVoucher: '待过账凭证记录',
    processingJournal: '在途业务流水单据',
    expiredFreeze: '已到期未解冻控制明细'
  }
  return map[name] || name
}

function getNetDirectionText(netDiff: number | string): string {
  const num = Number(netDiff) || 0
  if (Math.abs(num) < 0.000001) return '平'
  return num > 0 ? '借' : '贷'
}

function getNetDirectionType(netDiff: number | string): 'primary' | 'warning' | 'info' {
  const num = Number(netDiff) || 0
  if (Math.abs(num) < 0.000001) return 'info'
  return num > 0 ? 'primary' : 'warning'
}

function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '0 ms'
  if (ms < 1000) return `${ms} ms`
  const sec = (ms / 1000).toFixed(2)
  return `${sec} s (${ms} ms)`
}

function formatDateTime(val?: string | null): string {
  if (!val) return '--'
  return val.replace('T', ' ')
}

function copyText(text: string, label: string) {
  if (!text) return
  if (navigator && navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      toast.success(`${label}已复制`)
    }).catch(() => {
      fallbackCopy(text, label)
    })
  } else {
    fallbackCopy(text, label)
  }
}

function fallbackCopy(text: string, label: string) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    toast.success(`${label}已复制`)
  } catch {
    toast.error('复制失败，请手动复制')
  }
  document.body.removeChild(textarea)
}

// ==================== 接口拉取逻辑 ====================

/**
 * 加载日切状态
 */
async function fetchStatus(date?: string) {
  loadingStatus.value = true
  try {
    const res = await getEodStatus(date)
    eodStatus.value = res
    if (res?.accountingDate && !currentAccountingDate.value) {
      currentAccountingDate.value = res.accountingDate
    }
  } catch (error) {
    // 拦截器已有 toast 报错
  } finally {
    loadingStatus.value = false
  }
}

/**
 * 加载前置检查结果
 */
async function fetchPreCheck() {
  const queryDate = currentAccountingDate.value || eodStatus.value?.accountingDate
  if (!queryDate) return
  loadingPreCheck.value = true
  try {
    const res = await getPreCheckResult(queryDate)
    preCheckData.value = res
  } catch (error) {
    // 拦截器报错
  } finally {
    loadingPreCheck.value = false
  }
}

/**
 * 加载试算平衡结果
 */
async function fetchTrialBalance() {
  const queryDate = currentAccountingDate.value || eodStatus.value?.accountingDate
  if (!queryDate) return
  loadingTrialBalance.value = true
  try {
    const res = await getTrialBalance(queryDate)
    trialBalanceData.value = res
  } catch (error) {
    // 拦截器报错
  } finally {
    loadingTrialBalance.value = false
  }
}

/**
 * 加载总账分户核对结果
 */
async function fetchGlReconciliation() {
  const queryDate = currentAccountingDate.value || eodStatus.value?.accountingDate
  if (!queryDate) return
  loadingGlReconciliation.value = true
  try {
    const res = await getGlReconciliation(queryDate)
    glReconciliationData.value = res
  } catch (error) {
    // 拦截器报错
  } finally {
    loadingGlReconciliation.value = false
  }
}

/**
 * 刷新全部模块数据
 */
async function fetchAllData() {
  await fetchStatus(currentAccountingDate.value)
  await Promise.all([fetchPreCheck(), fetchTrialBalance(), fetchGlReconciliation()])
}

function handleDateChange(newDate: string) {
  if (newDate) {
    fetchAllData()
  }
}

/**
 * 打开分户账户穿透抽屉
 */
async function openAccountDrawer(subject: GlDiffDetail) {
  selectedSubject.value = subject
  accountDrawerVisible.value = true
  accountDiffList.value = []
  loadingAccounts.value = true
  try {
    const queryDate = currentAccountingDate.value || eodStatus.value?.accountingDate || ''
    const res = await getGlAccountDiffs(queryDate, subject.subjectCode)
    accountDiffList.value = res || []
  } catch (error) {
    // 拦截器已处理
  } finally {
    loadingAccounts.value = false
  }
}

/**
 * 修复指定科目
 */
async function handleRepairSubject(subject: GlDiffDetail) {
  try {
    await ElMessageBox.confirm(
      `确定对科目【${subject.subjectCode} - ${subject.subjectName}】执行智能一键修复？\n系统将自动检测历史断档日期并补全日余额，重新进行总分核对。`,
      '科目智能修复确认',
      {
        confirmButtonText: '立即修复',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  await doRepair(subject.subjectCode)
}

/**
 * 全局一键智能修复
 */
async function handleRepairAll() {
  try {
    await ElMessageBox.confirm(
      '智能修复将自动扫描历史断档日期（如RocketMQ积压或宕机导致的漏日切），按会计日顺序自动补全日余额快照并校准当前总账。是否立即开始？',
      '一键智能修复确认',
      {
        confirmButtonText: '开始修复',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  await doRepair()
}

/**
 * 执行修复接口
 */
async function doRepair(subjectCode?: string) {
  const queryDate = currentAccountingDate.value || eodStatus.value?.accountingDate || ''
  if (!queryDate) {
    toast.warning('会计日期不能为空')
    return
  }

  repairingGl.value = true
  try {
    const res = await repairGlReconciliation({
      accountingDate: queryDate,
      subjectCode
    })
    repairResult.value = res
    repairResultDialogVisible.value = true
    if (res.success) {
      toast.success(res.message || '总分核对修复成功')
    } else {
      toast.warning(res.message || '修复已完成，但仍有部分差额')
    }
  } catch (error) {
    // 拦截器已处理
  } finally {
    repairingGl.value = false
  }
}

function handleCloseRepairResult() {
  repairResultDialogVisible.value = false
  if (accountDrawerVisible.value && selectedSubject.value) {
    openAccountDrawer(selectedSubject.value)
  }
  fetchAllData()
}

// ==================== 操作逻辑 ====================

function openSwitchDateDialog() {
  switchDateForm.value.targetDate = ''
  switchDateDialogVisible.value = true
}

async function handleConfirmSwitchDate() {
  switchingDate.value = true
  try {
    const res = await switchDate({
      targetDate: switchDateForm.value.targetDate || undefined
    })
    toast.success(`切日成功！新会计日已切换为：${res.newDate}`)
    switchDateDialogVisible.value = false
    currentAccountingDate.value = res.newDate
    await fetchAllData()
  } catch (error) {
    // 错误处理由 toast 拦截
  } finally {
    switchingDate.value = false
  }
}

function openExecuteDialog() {
  executeForm.value.accountingDate = currentAccountingDate.value || eodStatus.value?.accountingDate || ''
  executeForm.value.skipPreCheck = false
  executeForm.value.executeTransfer = true
  executeDialogVisible.value = true
}

async function handleConfirmExecuteEod() {
  if (!executeForm.value.accountingDate) {
    toast.warning('会计日期不能为空')
    return
  }

  executingEod.value = true
  try {
    const res = await executeEod({
      accountingDate: executeForm.value.accountingDate,
      skipPreCheck: executeForm.value.skipPreCheck,
      executeTransfer: executeForm.value.executeTransfer
    })
    eodExecuteResult.value = res
    executeDialogVisible.value = false
    resultDrawerVisible.value = true

    if (res.trialBalancePassed) {
      toast.success('日切流程执行成功！')
    } else {
      toast.warning('日切已执行，但试算平衡或前置检查未通过，请查看执行报告')
    }

    await fetchAllData()
  } catch (error) {
    // 拦截器报错
  } finally {
    executingEod.value = false
  }
}

onMounted(() => {
  fetchAllData()
})
</script>

<style scoped lang="scss">
.eod-management-page {
  padding-bottom: 24px;

  .control-card {
    padding: 14px 20px;
    margin-bottom: 16px;

    .eod-control-form {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      margin-bottom: 0;

      .el-form-item {
        margin-bottom: 0;
        margin-right: 16px;
      }

      .header-action-group {
        margin-left: auto;
        display: flex;
        gap: 12px;
      }
    }
  }

  .banner-card {
    padding: 20px 24px;
    margin-bottom: 16px;

    .banner-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding-bottom: 16px;
      border-bottom: 1px dashed #ebeef5;

      .header-left {
        .date-badge-wrap {
          display: flex;
          align-items: center;
          gap: 10px;

          .label {
            font-size: 15px;
            color: #606266;
            font-weight: 500;
          }

          .date-value {
            font-size: 22px;
            font-weight: 700;
            color: #303133;
            letter-spacing: 0.5px;
          }

          .status-tag {
            font-weight: 600;
          }
        }

        .sub-text {
          margin-top: 6px;
          font-size: 13px;
          color: #909399;
        }
      }

      .header-right-kpi {
        display: flex;
        align-items: center;

        .kpi-block {
          display: flex;
          flex-direction: column;
          align-items: flex-end;
          padding: 0 16px;

          .kpi-label {
            font-size: 12px;
            color: #909399;
            margin-bottom: 4px;
          }

          .kpi-value {
            font-size: 14px;
            color: #303133;
            font-weight: 600;

            &.highlight {
              color: var(--el-color-primary, #409eff);
            }
          }
        }

        .kpi-divider {
          height: 32px;
          margin: 0;
        }
      }
    }

    .failed-alert-wrap {
      margin-bottom: 20px;
    }

    .eod-steps-container {
      padding: 10px 0 6px;
    }
  }

  .section-card {
    min-height: 280px;
    margin-bottom: 16px;

    .section-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 10px;
      border-bottom: 1px solid #f2f3f5;

      .title-with-icon {
        display: flex;
        align-items: center;
        gap: 8px;

        .title-icon {
          font-size: 18px;
          color: var(--el-color-primary, #409eff);
        }

        .title-text {
          font-size: 16px;
          font-weight: 600;
          color: #303133;
        }
      }

      .action-wrap {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }
  }

  /* 前置检查样式 */
  .precheck-card {
    .precheck-summary-tip {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      background: #fef0f0;
      border-radius: 6px;
      margin-bottom: 14px;
      border: 1px solid #fde2e2;

      &.is-passed {
        background: #f0f9eb;
        border-color: #e1f3d8;
      }

      .summary-icon {
        font-size: 18px;
        flex-shrink: 0;

        &.pass-icon {
          color: #67c23a;
        }
        &.fail-icon {
          color: #f56c6c;
        }
      }

      .tip-content {
        font-size: 13px;
        color: #606266;
        line-height: 1.4;
      }
    }

    .check-items-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;

      .check-item-cell {
        background: #f8f9fb;
        border: 1px solid #e4e7ed;
        border-radius: 6px;
        padding: 12px 14px;
        transition: all 0.2s ease;

        &.is-danger {
          border-color: #fbc4c4;
          background: #fef5f5;
        }

        &.is-success {
          border-color: #e1f3d8;
        }

        .item-name {
          font-size: 13px;
          color: #606266;
          font-weight: 500;
          margin-bottom: 8px;
        }

        .item-bottom {
          display: flex;
          justify-content: space-between;
          align-items: center;

          .item-count {
            font-size: 13px;
            color: #303133;
            font-weight: 600;
          }
        }
      }
    }
  }

  /* 试算平衡概览样式 */
  .balance-card {
    .trial-balance-stats {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: #f8f9fb;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 16px 20px;
      margin-bottom: 16px;

      .stat-box {
        flex: 1;

        .stat-label {
          display: block;
          font-size: 12px;
          color: #909399;
          margin-bottom: 6px;
        }

        .stat-amount {
          font-size: 18px;
          font-weight: 700;
        }

        &.diff-box.is-imbalanced {
          .stat-amount {
            color: var(--el-color-danger, #f56c6c);
          }
        }
      }

      .stat-operator {
        font-size: 20px;
        font-weight: bold;
        color: #909399;
        padding: 0 12px;
      }
    }

    .imbalanced-alert-box {
      margin-top: 10px;
    }
  }

  /* 总账分户核对卡片样式 */
  .gl-reconciliation-card {
    padding: 16px 20px;
    margin-bottom: 16px;

    .trial-balance-stats {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: #f8f9fb;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 16px 20px;
      margin-bottom: 16px;

      .stat-box {
        flex: 1;

        .stat-label {
          display: block;
          font-size: 12px;
          color: #909399;
          margin-bottom: 6px;
        }

        .stat-amount {
          font-size: 18px;
          font-weight: 700;
        }

        &.diff-box.is-imbalanced {
          .stat-amount {
            color: var(--el-color-danger, #f56c6c);
          }
        }
      }

      .stat-operator {
        font-size: 20px;
        font-weight: bold;
        color: #909399;
        padding: 0 12px;
      }
    }

    .reason-cell {
      display: flex;
      align-items: center;
      gap: 6px;

      .reason-text {
        color: #e6a23c;
        font-size: 12px;
      }
    }
  }

  /* 账户穿透抽屉头部汇总 */
  .drawer-header-summary {
    display: flex;
    align-items: center;
    background: #f8f9fb;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    padding: 12px 16px;
    margin-bottom: 16px;
    gap: 24px;
    flex-wrap: wrap;

    .summary-item {
      font-size: 13px;

      .label {
        color: #909399;
      }

      .val {
        font-size: 15px;
        font-weight: 600;
      }
    }

    .action-btn {
      margin-left: auto;
    }
  }

  /* 修复报告弹窗样式 */
  .repair-report-box {
    .repair-kpis {
      margin: 16px 0;
    }

    .repair-logs-card {
      margin-top: 16px;
      background: #1e1e1e;
      border-radius: 6px;
      padding: 12px 16px;

      .log-title {
        color: #9cdcfe;
        font-size: 13px;
        font-weight: 600;
        margin-bottom: 8px;
      }

      .log-console {
        max-height: 220px;
        overflow-y: auto;

        .log-line {
          color: #d4d4d4;
          font-size: 12px;
          line-height: 1.6;
          font-family: var(--fin-font-mono, monospace);
        }
      }
    }
  }

  .text-danger {
    color: var(--el-color-danger, #f56c6c);
  }

  .text-warning {
    color: var(--el-color-warning, #e6a23c);
  }

  .text-success {
    color: var(--el-color-success, #67c23a);
  }

  .font-bold {
    font-weight: bold;
  }

  /* 科目表格卡片 */
  .table-card {
    padding: 16px 20px;

    .table-header-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 14px;

      .bar-left {
        .table-title {
          font-size: 16px;
          font-weight: 600;
          color: #303133;
          margin-right: 12px;
        }

        .table-subtitle {
          font-size: 13px;
          color: #909399;
        }
      }

      .bar-right {
        display: flex;
        align-items: center;
      }
    }

    .code-link {
      cursor: pointer;
      color: var(--el-color-primary, #409eff);
      font-weight: 500;
      margin-right: 4px;

      &:hover {
        text-decoration: underline;
      }
    }
  }

  /* 弹窗及抽屉公用样式 */
  .dialog-notice {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-top: 14px;
    padding: 10px 12px;
    background: #f4f4f5;
    border-radius: 4px;
    font-size: 12px;
    color: #606266;
    line-height: 1.5;

    .el-icon {
      color: #909399;
      font-size: 14px;
      margin-top: 2px;
    }
  }

  .dialog-alert-box {
    margin-top: 14px;
  }

  .bold-text {
    font-weight: 600;
    font-size: 15px;
    color: #303133;
  }

  .form-item-help {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;

    &.text-danger {
      color: var(--el-color-danger, #f56c6c);
    }
  }

  /* 执行结果报告抽屉 */
  .eod-report-container {
    .report-overview-box {
      background: #f8f9fb;
      border: 1px solid #e4e7ed;
      border-radius: 6px;
      padding: 14px 16px;
      margin-bottom: 20px;

      .overview-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 10px;

        .report-date {
          font-size: 16px;
          font-weight: 700;
          color: #303133;
        }
      }

      .overview-meta {
        display: flex;
        gap: 20px;
        font-size: 13px;
        color: #606266;
      }
    }

    .report-section {
      margin-bottom: 24px;

      .section-badge-title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 10px;
        display: flex;
        align-items: center;

        &::before {
          content: '';
          display: inline-block;
          width: 4px;
          height: 14px;
          background: var(--el-color-primary, #409eff);
          margin-right: 8px;
          border-radius: 2px;
        }
      }
    }
  }

  .mono {
    font-family: var(--fin-font-mono, monospace);
  }
}
</style>
