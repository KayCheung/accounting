<template>
  <div class="manual-voucher-page">
    <!-- 顶部工单状态漏斗看板 (6维指标卡) -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="4" v-for="card in statCards" :key="card.key">
        <el-card
          shadow="hover"
          class="stat-card"
          :class="{ active: currentStatFilter === card.statusVal }"
          @click="handleCardClick(card.statusVal)"
        >
          <div class="stat-content">
            <div class="stat-label">
              <el-icon :class="card.colorClass" class="stat-icon"><component :is="card.icon" /></el-icon>
              <span>{{ card.label }}</span>
            </div>
            <div class="stat-value" :class="card.colorClass">{{ card.count }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 多维检索工具栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" ref="queryFormRef" inline class="search-form">
        <el-form-item label="申请单号">
          <el-input
            v-model="queryForm.applyNo"
            placeholder="支持模糊匹配"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="正式凭证号">
          <el-input
            v-model="queryForm.voucherNo"
            placeholder="已入账凭证号"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="制单人">
          <el-input
            v-model="queryForm.makerName"
            placeholder="制单人姓名"
            clearable
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select
            v-model="queryForm.applyStatus"
            placeholder="全部状态"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="会计日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日"
            end-placeholder="结束日"
            value-format="YYYY-MM-DD"
            style="width: 230px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="openCreateModal">填制手工凭证</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 申请工单主表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        row-key="applyNo"
        @expand-change="handleExpandChange"
        class="custom-table"
      >
        <!-- 行展开预览借贷分录明细 -->
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="expand-entry-box">
              <div class="expand-header">
                <span class="expand-title">
                  <el-icon><Document /></el-icon> 借贷分录明细预览
                </span>
                <span class="balance-indicator">
                  <el-tag :type="row.isBalanced ? 'success' : 'danger'" size="small">
                    {{ row.isBalanced ? '🟢 借贷严格平衡' : '🔴 借贷不平衡' }}
                  </el-tag>
                  <span class="sum-text">
                    借方合计: <strong>¥ {{ formatAmount(row.totalDebitAmount) }}</strong> |
                    贷方合计: <strong>¥ {{ formatAmount(row.totalCreditAmount) }}</strong>
                  </span>
                </span>
              </div>
              <el-table :data="row.entries" border size="small" class="entry-sub-table">
                <el-table-column label="行号" prop="rowNum" width="60" align="center" />
                <el-table-column label="借贷方向" width="90" align="center">
                  <template #default="{ row: subRow }">
                    <el-tag
                      :type="subRow.debitCredit === 1 ? 'primary' : 'warning'"
                      size="small"
                      effect="dark"
                    >
                      {{ subRow.debitCredit === 1 ? '借方 (D)' : '贷方 (C)' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="科目代码" prop="subjectCode" width="120" />
                <el-table-column label="科目名称" prop="subjectName" min-width="160" show-overflow-tooltip />
                <el-table-column label="分户账号" prop="accountNo" min-width="150" show-overflow-tooltip>
                  <template #default="{ row: subRow }">
                    <span v-if="subRow.accountNo" class="mono-font">{{ subRow.accountNo }}</span>
                    <span v-else class="text-muted">（未指定）</span>
                  </template>
                </el-table-column>
                <el-table-column label="分录金额" width="130" align="right">
                  <template #default="{ row: subRow }">
                    <span class="mono-font font-bold">¥ {{ formatAmount(subRow.amount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="分录摘要" prop="summary" min-width="180" show-overflow-tooltip />
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="申请单号" prop="applyNo" width="170" align="center">
          <template #default="{ row }">
            <span class="mono-font font-bold cursor-pointer text-primary" @click="openDetail(row)">
              {{ row.applyNo }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="会计日期" prop="accountingDate" width="110" align="center" />
        <el-table-column label="凭证类型" prop="voucherType" width="100" align="center" />
        <el-table-column label="凭证摘要" prop="summary" min-width="180" show-overflow-tooltip />
        <el-table-column label="借贷合计金额" width="140" align="right">
          <template #default="{ row }">
            <span class="mono-font font-bold">¥ {{ formatAmount(row.totalDebitAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审批流状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.applyStatus)" size="small">
              {{ row.applyStatusDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="制单人" prop="makerName" width="90" align="center" />
        <el-table-column label="初审人" prop="auditorName" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.auditorName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="复核人" prop="reviewerName" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.reviewerName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="记账人" prop="bookkeeperName" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.bookkeeperName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="正式凭证号" width="160" align="center">
          <template #default="{ row }">
            <el-link
              v-if="row.voucherNo"
              type="primary"
              :underline="false"
              @click="goToVoucherLedger(row.voucherNo)"
            >
              {{ row.voucherNo }}
            </el-link>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createTime" width="150" align="center" />

        <!-- 规范操作列：紧凑下拉菜单 (MoreFilled, 列宽 70px) -->
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
              <el-button type="primary" link :icon="MoreFilled" class="more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="preview">
                    <el-icon><Tickets /></el-icon> 凭证印签详情
                  </el-dropdown-item>
                  <!-- 草稿或驳回状态：可重新编辑 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 1 || row.applyStatus === 3 || row.applyStatus === 5"
                    command="edit"
                  >
                    <el-icon><EditPen /></el-icon> 修改编辑
                  </el-dropdown-item>
                  <!-- 草稿状态：可直接提交初审 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 1"
                    command="submit"
                  >
                    <el-icon><Promotion /></el-icon> 提交初审
                  </el-dropdown-item>
                  <!-- 待初审状态：初审操作 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 2"
                    command="audit"
                  >
                    <el-icon><Check /></el-icon> 初审把关
                  </el-dropdown-item>
                  <!-- 待复核状态：终审复核操作 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 4"
                    command="review"
                  >
                    <el-icon><Stamp /></el-icon> 终审复核
                  </el-dropdown-item>
                  <!-- 待记账状态：确认记账 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 6"
                    command="bookkeep"
                    divided
                  >
                    <el-icon class="text-warning"><Coin /></el-icon>
                    <span class="font-bold text-warning">确认记账入账</span>
                  </el-dropdown-item>
                  <!-- 草稿或驳回状态：作废 -->
                  <el-dropdown-item
                    v-if="row.applyStatus === 1 || row.applyStatus === 3 || row.applyStatus === 5"
                    command="cancel"
                    divided
                  >
                    <el-icon class="text-danger"><Delete /></el-icon>
                    <span class="text-danger">作废申请</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ==================== 弹窗1：手工凭证填制/编辑弹窗 ==================== -->
    <el-dialog
      v-model="createDialogVisible"
      :title="createForm.applyNo ? '编辑修改凭证申请' : '手工编制记账凭证'"
      width="1020px"
      destroy-on-close
      :close-on-click-modal="false"
      class="manual-create-dialog"
    >
      <el-form :model="createForm" ref="createFormRef" label-width="85px" class="create-form">
        <!-- 凭证头两列栅格 -->
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="会计日期" required>
              <el-date-picker
                v-model="createForm.accountingDate"
                type="date"
                placeholder="选择会计日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="凭证类型">
              <el-select v-model="createForm.voucherType" style="width: 100%">
                <el-option label="记账凭证" value="记账凭证" />
                <el-option label="调整凭证" value="调整凭证" />
                <el-option label="结账凭证" value="结账凭证" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制单人" required>
              <el-input v-model="createForm.makerName" placeholder="制单人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="凭证摘要" required>
          <el-input
            v-model="createForm.summary"
            placeholder="请输入凭证整体业务摘要（如：9月末借贷调账、利息计提调整）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 借贷分录动态明细表格 -->
        <div class="entries-section">
          <div class="entries-toolbar">
            <div class="toolbar-left">
              <span class="section-title">借贷分录编制（至少一借一贷）</span>
              <el-tag size="small" type="info">已录入 {{ createForm.entries.length }} 行</el-tag>
            </div>
            <div class="toolbar-right">
              <el-button size="small" type="primary" :icon="Plus" @click="handleAddEntry">添加分录行</el-button>
              <el-button size="small" type="warning" :icon="ScaleToOriginal" @click="handleAutoBalance">一键配平差额</el-button>
            </div>
          </div>

          <el-table :data="createForm.entries" border size="small" class="entry-edit-table">
            <el-table-column label="行号" width="55" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="借/贷" width="110" align="center">
              <template #default="{ row }">
                <el-select v-model="row.debitCredit" size="small" style="width: 90px">
                  <el-option :value="1" label="借 (D)" />
                  <el-option :value="2" label="贷 (C)" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="会计科目" min-width="220">
              <template #default="{ row }">
                <el-select
                  v-model="row.subjectCode"
                  filterable
                  placeholder="搜索科目代码或名称"
                  size="small"
                  style="width: 100%"
                  @change="(val: string) => handleSubjectChange(row, val)"
                >
                  <el-option
                    v-for="sub in leafSubjects"
                    :key="sub.subjectCode"
                    :label="`${sub.subjectCode} - ${sub.subjectName}`"
                    :value="sub.subjectCode"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="分户账号" width="160">
              <template #default="{ row }">
                <el-input v-model="row.accountNo" placeholder="分户账号(可选)" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="金额 (¥)" width="150" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.amount"
                  :min="0.01"
                  :precision="2"
                  :step="100"
                  size="small"
                  controls-position="right"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="分录摘要" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.summary" placeholder="默认同凭证摘要" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  :icon="Delete"
                  :disabled="createForm.entries.length <= 2"
                  @click="handleRemoveEntry($index)"
                />
              </template>
            </el-table-column>
          </el-table>

          <!-- 底部借贷平衡核验看板 -->
          <div class="balance-check-bar" :class="{ 'is-balanced': isModalBalanced }">
            <div class="bar-left">
              <span>借方合计 (Debit): <strong>¥ {{ formatAmount(modalDebitTotal) }}</strong></span>
              <span class="bar-sep">|</span>
              <span>贷方合计 (Credit): <strong>¥ {{ formatAmount(modalCreditTotal) }}</strong></span>
              <span class="bar-sep">|</span>
              <span>借贷差额: <strong>¥ {{ formatAmount(modalDiffAmount) }}</strong></span>
            </div>
            <div class="bar-right">
              <el-tag :type="isModalBalanced ? 'success' : 'danger'" effect="dark">
                {{ isModalBalanced ? '🟢 借贷严格平衡 (允许提交)' : '🔴 借贷不平衡 (禁止提交)' }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 辅助核算分摊项编制 (可选维度：部门/项目/客户/供应商) -->
        <div class="entries-section mt-4">
          <div class="entries-toolbar">
            <div class="toolbar-left">
              <span class="section-title">辅助核算分摊明细 (可选维度：部门 / 项目 / 客户 / 供应商)</span>
              <el-tag size="small" type="info">已录入 {{ createForm.auxiliaries.length }} 项</el-tag>
            </div>
            <div class="toolbar-right">
              <el-button size="small" type="primary" plain :icon="Plus" @click="handleAddAuxiliary">添加辅助核算</el-button>
            </div>
          </div>

          <el-table
            v-if="createForm.auxiliaries.length > 0"
            :data="createForm.auxiliaries"
            border
            size="small"
            class="entry-edit-table"
          >
            <el-table-column label="序号" width="55" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="关联分录行" width="130">
              <template #default="{ row }">
                <el-select
                  v-model="row.entryRowNum"
                  size="small"
                  style="width: 100%"
                  @change="handleAuxEntryChange(row)"
                >
                  <el-option
                    v-for="entry in createForm.entries"
                    :key="entry.rowNum"
                    :label="`第 ${entry.rowNum} 行 (${entry.debitCredit === 1 ? '借' : '贷'}: ${entry.subjectCode || '未选科目'})`"
                    :value="entry.rowNum"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="核算类别" width="130">
              <template #default="{ row }">
                <el-select v-model="row.auxType" size="small" style="width: 100%" @change="handleAuxTypeChange(row)">
                  <el-option label="部门 (DEPT)" value="DEPT" />
                  <el-option label="项目 (PROJECT)" value="PROJECT" />
                  <el-option label="客户 (CUSTOMER)" value="CUSTOMER" />
                  <el-option label="供应商 (SUPPLIER)" value="SUPPLIER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="核算项目编码" width="130">
              <template #default="{ row }">
                <el-input v-model="row.auxCode" placeholder="如 DEPT001" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="核算项目名称" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.auxName" placeholder="如 产品运营部" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="增减方向" width="95" align="center">
              <template #default="{ row }">
                <el-select v-model="row.changeDirection" size="small" style="width: 75px">
                  <el-option :value="1" label="增加" />
                  <el-option :value="2" label="减少" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="核算金额 (¥)" width="140" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.amount"
                  :min="0.01"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleRemoveAuxiliary($index)"
                />
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-aux-tip">
            暂无辅助核算项。若本科目需按部门、项目、往来客户或供应商做二级多维核算，请点击上方按钮添加。
          </div>
        </div>

        <!-- 凭证原始单据附件编制 (对应《凭证附件.html》) -->
        <div class="entries-section mt-4">
          <div class="entries-toolbar">
            <div class="toolbar-left">
              <span class="section-title">凭证原始单据附件 (自动统计附件张数)</span>
              <el-tag size="small" type="success">已关联 {{ createForm.attachments.length }} 张原始凭证</el-tag>
            </div>
            <div class="toolbar-right">
              <el-button size="small" type="primary" plain :icon="Paperclip" @click="handleAddAttachment">添加附件单据</el-button>
              <el-button size="small" type="info" plain @click="handleQuickPresetAttachments">快速填入示范附件</el-button>
            </div>
          </div>

          <el-table
            v-if="createForm.attachments.length > 0"
            :data="createForm.attachments"
            border
            size="small"
            class="entry-edit-table"
          >
            <el-table-column label="序号" width="55" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="文件名称" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.fileName" placeholder="如：京东代扣证明.pdf" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="存储路径 / 凭证URL" min-width="240">
              <template #default="{ row }">
                <el-input v-model="row.filePath" placeholder="/attachments/202609/xxx.pdf" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="文件大小 (Bytes)" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.fileSize"
                  :min="1"
                  :step="1024"
                  size="small"
                  controls-position="right"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleRemoveAttachment($index)"
                />
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-aux-tip">
            暂无关联附件。点击上方按钮可添加记账原始单据凭证（如代扣证明、发票、银行还款截图），系统将自动统计附件张数。
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="info" :loading="submitLoading" @click="submitCreateForm('DRAFT')">
          保存为草稿
        </el-button>
        <el-button
          type="primary"
          :disabled="!isModalBalanced"
          :loading="submitLoading"
          @click="submitCreateForm('SUBMIT')"
        >
          提交初审
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗2：初审 / 复核审核弹窗 ==================== -->
    <el-dialog
      v-model="auditDialogVisible"
      :title="auditForm.operatorRole === 'AUDITOR' ? '初审把关审核' : '终审复核审批'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="audit-summary-box">
        <p><strong>申请单号：</strong><span class="mono-font">{{ currentAuditRow?.applyNo }}</span></p>
        <p><strong>制单人：</strong>{{ currentAuditRow?.makerName }}</p>
        <p><strong>借贷金额：</strong>¥ {{ formatAmount(currentAuditRow?.totalDebitAmount) }}</p>
        <p><strong>凭证摘要：</strong>{{ currentAuditRow?.summary }}</p>
      </div>

      <el-form :model="auditForm" label-width="85px" class="mt-4">
        <el-form-item :label="auditForm.operatorRole === 'AUDITOR' ? '初审人' : '复核人'" required>
          <el-input v-model="auditForm.operatorName" placeholder="请输入您的实名" />
        </el-form-item>
        <el-form-item label="审批意见" required>
          <el-input
            v-model="auditForm.opinion"
            type="textarea"
            :rows="3"
            placeholder="请输入审批处理意见（驳回时必须详细说明原因）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="auditLoading" @click="handleAuditAction('REJECT')">
          驳回申请
        </el-button>
        <el-button type="success" :loading="auditLoading" @click="handleAuditAction('PASS')">
          审核通过
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗3：确认记账入账弹窗 ==================== -->
    <el-dialog
      v-model="bookkeepDialogVisible"
      title="确认记账并过账入账"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        title="重要合规提醒"
        type="warning"
        description="该手工凭证已通过初审与复核终审。点击【确认记账】将正式落库法定凭证业务表（t_accounting_voucher），并调用过账引擎实时更新分户账户余额！"
        show-icon
        :closable="false"
        class="mb-3"
      />
      <div class="audit-summary-box">
        <p><strong>申请单号：</strong><span class="mono-font">{{ currentBookkeepRow?.applyNo }}</span></p>
        <p><strong>借贷金额：</strong>¥ {{ formatAmount(currentBookkeepRow?.totalDebitAmount) }}</p>
        <p><strong>初审人 / 复核人：</strong>{{ currentBookkeepRow?.auditorName }} / {{ currentBookkeepRow?.reviewerName }}</p>
      </div>
      <el-form :model="bookkeepForm" label-width="85px" class="mt-3">
        <el-form-item label="记账人姓名" required>
          <el-input v-model="bookkeepForm.bookkeeperName" placeholder="请输入经办记账人姓名" />
        </el-form-item>
        <el-form-item label="记账说明">
          <el-input v-model="bookkeepForm.remark" placeholder="调账正式记账过账" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="bookkeepDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bookkeepLoading" @click="handleConfirmBookkeeping">
          确认记账过账
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗4：经典财务凭证印签详情与流转追溯 (参考《凭证详情.html》) ==================== -->
    <el-dialog
      v-model="detailDialogVisible"
      title="记账凭证全景档案与印签预览"
      width="960px"
      destroy-on-close
      class="classic-voucher-dialog"
    >
      <div v-loading="detailLoading" v-if="detailData" class="voucher-preview-container">
        <!-- 经典纸质凭证仿真卡片 (参考原型《凭证详情.html》) -->
        <div class="classic-voucher-sheet">
          <!-- 凭证主标题 -->
          <div class="voucher-header-title">
            <h2>记 &nbsp; 账 &nbsp; 凭 &nbsp; 证</h2>
            <div class="voucher-title-underline"></div>
          </div>

          <!-- 凭证元数据栏 -->
          <div class="voucher-meta-bar">
            <div class="meta-date">
              {{ formatVoucherDate(detailData.accountingDate) }}
            </div>
            <div class="meta-no">
              <strong>凭证字号：</strong>
              <span class="mono-font">{{ detailData.voucherNo || detailData.applyNo }}</span>
              <el-tag size="small" type="info" class="ml-2">{{ detailData.voucherType }}</el-tag>
            </div>
            <div class="meta-attachment">
              附单据 <span class="mono-font underline">{{ detailData.attachmentCount || detailData.attachments?.length || 0 }}</span> 张
              <el-button
                v-if="detailData.attachments && detailData.attachments.length > 0"
                size="small"
                type="primary"
                link
                :icon="Paperclip"
                class="ml-2"
                @click="attachmentModalVisible = true"
              >
                查看附件 ({{ detailData.attachments.length }})
              </el-button>
            </div>
          </div>

          <!-- 借贷分录对照表格 -->
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
              <tr v-for="item in detailData.entries" :key="item.rowNum">
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
              <!-- 空行补齐以呈现经典凭证版面 -->
              <tr v-if="detailData.entries.length < 3">
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
                  <strong>{{ detailData.totalAmountInWords }}</strong>
                </td>
                <td class="text-right mono-font font-bold">
                  ¥ {{ formatAmount(detailData.totalDebitAmount) }}
                </td>
                <td class="text-right mono-font font-bold">
                  ¥ {{ formatAmount(detailData.totalCreditAmount) }}
                </td>
              </tr>
            </tfoot>
          </table>

          <!-- 经典四方签章栏 (根据原型《凭证详情.html》行 391-422 精确还原) -->
          <div class="voucher-signatures-bar">
            <div class="signature-col">
              <span class="sig-label">会计主管：</span>
              <span class="sig-val">{{ detailData.reviewerName || '—' }}</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">审 &nbsp; 核：</span>
              <span class="sig-val">{{ detailData.auditorName || '—' }}</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">记 &nbsp; 账：</span>
              <span class="sig-val font-bold text-primary">{{ detailData.bookkeeperName || '—' }}</span>
            </div>
            <div class="signature-col">
              <span class="sig-label">制 &nbsp; 单：</span>
              <span class="sig-val">{{ detailData.makerName }}</span>
            </div>
          </div>
        </div>

        <!-- 辅助核算项表格 (严格遵循原型《凭证详情.html》行 448-585) -->
        <div v-if="detailData.auxiliaries && detailData.auxiliaries.length > 0" class="auxiliary-section mt-4">
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
              <tr v-for="(aux, idx) in detailData.auxiliaries" :key="aux.id || idx">
                <td class="text-center mono-font">{{ idx + 1 }}</td>
                <td class="text-center">第 {{ aux.entryRowNum }} 行</td>
                <td>{{ aux.subjectCode }} {{ aux.subjectName || '' }}</td>
                <td class="text-center">
                  <el-tag size="small" effect="plain">{{ aux.auxTypeName || aux.auxType }}</el-tag>
                </td>
                <td class="font-bold">
                  {{ aux.auxName }} <span class="text-muted mono-font">({{ aux.auxCode }})</span>
                </td>
                <td class="text-center">
                  <el-tag size="small" :type="aux.changeDirection === 2 ? 'warning' : 'success'">
                    {{ aux.changeDirectionDesc || (aux.changeDirection === 2 ? '减少' : '增加') }}
                  </el-tag>
                </td>
                <td class="text-right mono-font font-bold">¥ {{ formatAmount(aux.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 全生命周期流转可追溯时间轴 -->
        <div class="timeline-section mt-4">
          <div class="section-title mb-3">
            <el-icon><Timer /></el-icon> 审批流转可追溯时间轴 (Audit Trail)
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="log in detailData.auditLogs"
              :key="log.id"
              :timestamp="log.operateTime"
              placement="top"
              :type="getTimelineType(log.action)"
            >
              <div class="timeline-card">
                <div class="timeline-head">
                  <span class="timeline-action">{{ log.actionDesc }}</span>
                  <el-tag size="small" class="ml-2">{{ log.operatorRole }}</el-tag>
                  <span class="timeline-operator font-bold ml-2">经办人: {{ log.operatorName }}</span>
                </div>
                <div v-if="log.opinion" class="timeline-opinion text-muted mt-1">
                  审批意见 / 说明: {{ log.opinion }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="detailData?.attachments && detailData.attachments.length > 0"
          type="info"
          plain
          :icon="Paperclip"
          @click="attachmentModalVisible = true"
        >
          查看附件 ({{ detailData.attachments.length }})
        </el-button>
        <el-button :icon="Printer" @click="handlePrint">打印凭证</el-button>
        <el-button
          v-if="detailData?.voucherNo"
          type="primary"
          @click="goToVoucherLedger(detailData.voucherNo)"
        >
          穿透至凭证中心全景档案
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 弹窗5：凭证附件预览弹窗 (参考《凭证附件.html》) ==================== -->
    <el-dialog
      v-model="attachmentModalVisible"
      title="凭证原始单据附件"
      width="780px"
      append-to-body
      destroy-on-close
    >
      <div v-if="detailData?.attachments && detailData.attachments.length > 0">
        <el-table :data="detailData.attachments" border stripe size="small">
          <el-table-column label="序号" width="60" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="文件名称" min-width="220">
            <template #default="{ row }">
              <span class="attachment-name">
                <el-icon class="mr-1 text-primary"><Document /></el-icon>
                <span class="underline font-bold">{{ row.fileName }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="文件类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="dark" :type="getFileTypeTag(row.fileType)">
                {{ row.fileType || 'FILE' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文件大小" width="120" align="center">
            <template #default="{ row }">
              <span class="mono-font">{{ row.fileSizeFormatted || formatBytes(row.fileSize) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handlePreviewAttachment(row)">查看</el-button>
              <el-button type="success" link size="small" @click="handleDownloadAttachment(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="本张凭证暂无关联原始单据附件" />
      <template #footer>
        <el-button @click="attachmentModalVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  Plus,
  MoreFilled,
  Tickets,
  Document,
  EditPen,
  Delete,
  Check,
  Stamp,
  Coin,
  Promotion,
  ScaleToOriginal,
  Timer,
  Clock,
  CircleCheck,
  CircleClose,
  Paperclip,
  Printer
} from '@element-plus/icons-vue'
import {
  getManualVoucherPage,
  getManualVoucherDetail,
  saveOrSubmitManualVoucher,
  auditManualVoucher,
  reviewManualVoucher,
  postManualVoucher,
  cancelManualVoucher,
  getManualVoucherStatistics,
  getLeafSubjects,
  type ManualVoucherApplyPageItem,
  type ManualVoucherApplyDetail,
  type LeafSubjectOption,
  type ApplyEntryItem,
  type ManualVoucherAuxiliaryItem,
  type ManualVoucherAttachmentItem
} from '@/api/manualVoucher'

const router = useRouter()

// ==================== 状态统计看板 ====================
const currentStatFilter = ref<number | null>(null)
const statCounts = reactive({
  total: 0,
  pendingAudit: 0,
  pendingReview: 0,
  pendingBookkeeping: 0,
  booked: 0,
  rejected: 0
})

const statCards = computed(() => [
  { key: 'total', label: '全部申请', count: statCounts.total, icon: Tickets, colorClass: 'text-info', statusVal: null },
  { key: 'pendingAudit', label: '待初审', count: statCounts.pendingAudit, icon: Clock, colorClass: 'text-warning', statusVal: 2 },
  { key: 'pendingReview', label: '待复核', count: statCounts.pendingReview, icon: Stamp, colorClass: 'text-primary', statusVal: 4 },
  { key: 'pendingBookkeeping', label: '待记账', count: statCounts.pendingBookkeeping, icon: Coin, colorClass: 'text-purple', statusVal: 6 },
  { key: 'booked', label: '已记账入账', count: statCounts.booked, icon: CircleCheck, colorClass: 'text-success', statusVal: 7 },
  { key: 'rejected', label: '被驳回单据', count: statCounts.rejected, icon: CircleClose, colorClass: 'text-danger', statusVal: 3 }
])

function handleCardClick(statusVal: number | null) {
  currentStatFilter.value = statusVal
  queryForm.applyStatus = statusVal !== null ? statusVal : undefined
  handleSearch()
}

// ==================== 检索表单 ====================
const queryForm = reactive({
  pageNo: 1,
  pageSize: 20,
  applyNo: '',
  voucherNo: '',
  applyStatus: undefined as number | undefined,
  makerName: '',
  startDate: '',
  endDate: ''
})
const dateRange = ref<[string, string] | null>(null)

const statusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待初审' },
  { value: 3, label: '初审驳回' },
  { value: 4, label: '待复核' },
  { value: 5, label: '复核驳回' },
  { value: 6, label: '待记账' },
  { value: 7, label: '已记账' },
  { value: 8, label: '已作废' }
]

function handleDateChange(val: [string, string] | null) {
  if (val) {
    queryForm.startDate = val[0]
    queryForm.endDate = val[1]
  } else {
    queryForm.startDate = ''
    queryForm.endDate = ''
  }
}

function handleSearch() {
  queryForm.pageNo = 1
  loadData()
}

function handleReset() {
  queryForm.applyNo = ''
  queryForm.voucherNo = ''
  queryForm.applyStatus = undefined
  queryForm.makerName = ''
  queryForm.startDate = ''
  queryForm.endDate = ''
  dateRange.value = null
  currentStatFilter.value = null
  handleSearch()
}

// ==================== 表格数据加载 ====================
const loading = ref(false)
const tableData = ref<ManualVoucherApplyPageItem[]>([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getManualVoucherPage(queryForm)
    if (res) {
      tableData.value = res.list || []
      total.value = res.total || 0
    }
  } catch (error) {
    console.error('加载手工凭证申请列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getManualVoucherStatistics()
    if (res) {
      statCounts.total = res.total
      statCounts.pendingAudit = res.pendingAudit
      statCounts.pendingReview = res.pendingReview
      statCounts.pendingBookkeeping = res.pendingBookkeeping
      statCounts.booked = res.booked
      statCounts.rejected = res.rejected
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

function handleExpandChange(_row: ManualVoucherApplyPageItem, _expandedRows: ManualVoucherApplyPageItem[]) {
  // 单击展开时行分录数据已预装配，无需额外请求
}

// ==================== 末级科目字典加载 ====================
const leafSubjects = ref<LeafSubjectOption[]>([])

async function loadLeafSubjects() {
  try {
    const res = await getLeafSubjects()
    if (res) {
      leafSubjects.value = res
    }
  } catch (error) {
    console.error('加载末级科目字典失败:', error)
  }
}

function handleSubjectChange(row: ApplyEntryItem, subjectCode: string) {
  const target = leafSubjects.value.find(s => s.subjectCode === subjectCode)
  if (target) {
    row.subjectName = target.subjectName
    // 若原先未设置借贷，可依据科目余额方向给默认值
    if (!row.debitCredit) {
      row.debitCredit = target.balanceDirection || 1
    }
  }
}

// ==================== 弹窗1：手工凭证填制与编辑 ====================
const createDialogVisible = ref(false)
const submitLoading = ref(false)

const createForm = reactive({
  applyNo: '',
  voucherType: '记账凭证',
  tradeType: 2,
  accountingDate: new Date().toISOString().split('T')[0],
  summary: '',
  makerName: '当前操作员',
  entries: [] as ApplyEntryItem[],
  auxiliaries: [] as ManualVoucherAuxiliaryItem[],
  attachments: [] as ManualVoucherAttachmentItem[]
})

function openCreateModal() {
  createForm.applyNo = ''
  createForm.voucherType = '记账凭证'
  createForm.tradeType = 2
  createForm.accountingDate = new Date().toISOString().split('T')[0]
  createForm.summary = ''
  createForm.makerName = '张会计'
  // 默认预设一借一贷2行
  createForm.entries = [
    { rowNum: 1, debitCredit: 1, subjectCode: '', accountNo: '', amount: 1000, summary: '', unilateral: 1 },
    { rowNum: 2, debitCredit: 2, subjectCode: '', accountNo: '', amount: 1000, summary: '', unilateral: 1 }
  ]
  createForm.auxiliaries = []
  createForm.attachments = []
  createDialogVisible.value = true
}

function handleAddEntry() {
  const nextRow = createForm.entries.length + 1
  createForm.entries.push({
    rowNum: nextRow,
    debitCredit: 2, // 默认贷方
    subjectCode: '',
    accountNo: '',
    amount: 100,
    summary: createForm.summary,
    unilateral: 1
  })
}

function handleRemoveEntry(index: number) {
  createForm.entries.splice(index, 1)
  createForm.entries.forEach((e, idx) => {
    e.rowNum = idx + 1
  })
}

// ==================== 辅助核算分摊项交互 ====================
function handleAddAuxiliary() {
  const defaultEntry = createForm.entries[0]
  createForm.auxiliaries.push({
    entryRowNum: defaultEntry ? defaultEntry.rowNum : 1,
    subjectCode: defaultEntry ? defaultEntry.subjectCode : '',
    subjectName: defaultEntry ? defaultEntry.subjectName : '',
    auxType: 'DEPT',
    auxTypeName: '部门',
    auxCode: 'DEPT001',
    auxName: '产品运营部',
    changeDirection: 1,
    amount: defaultEntry ? defaultEntry.amount : 100
  })
}

function handleRemoveAuxiliary(index: number) {
  createForm.auxiliaries.splice(index, 1)
}

function handleAuxEntryChange(aux: ManualVoucherAuxiliaryItem) {
  const entry = createForm.entries.find(e => e.rowNum === aux.entryRowNum)
  if (entry) {
    aux.subjectCode = entry.subjectCode
    aux.subjectName = entry.subjectName
    if (!aux.amount || aux.amount <= 0) {
      aux.amount = entry.amount
    }
  }
}

function handleAuxTypeChange(aux: ManualVoucherAuxiliaryItem) {
  const typeMap: Record<string, { name: string; code: string; label: string }> = {
    DEPT: { name: '部门', code: 'DEPT001', label: '产品运营部' },
    PROJECT: { name: '项目', code: 'PRJ001', label: '核心账务系统改造' },
    CUSTOMER: { name: '客户', code: 'CUST8888', label: '京东零售自营账户' },
    SUPPLIER: { name: '供应商', code: 'SUPP9999', label: '阿里云计算技术服务' }
  }
  const config = typeMap[aux.auxType]
  if (config) {
    aux.auxTypeName = config.name
    aux.auxCode = config.code
    aux.auxName = config.label
  }
}

// ==================== 凭证原始附件交互 ====================
function handleAddAttachment() {
  createForm.attachments.push({
    fileName: `原始单据_${createForm.attachments.length + 1}.pdf`,
    filePath: `/attachments/${new Date().getFullYear()}/voucher_proof_${Date.now()}.pdf`,
    fileSize: 1024 * 512,
    fileType: 'PDF'
  })
}

function handleRemoveAttachment(index: number) {
  createForm.attachments.splice(index, 1)
}

function handleQuickPresetAttachments() {
  createForm.attachments = [
    {
      fileName: '京东代扣证明.pdf',
      filePath: '/attachments/2026/09/jd_debit_proof_001.pdf',
      fileSize: 1048576,
      fileType: 'PDF'
    },
    {
      fileName: '用户银行还款账单截图.jpg',
      filePath: '/attachments/2026/09/bank_repay_screenshot.jpg',
      fileSize: 436224,
      fileType: 'JPG'
    }
  ]
  ElMessage.success('已自动填充典型单据凭据附件')
}

const modalDebitTotal = computed(() => {
  return createForm.entries
    .filter(e => e.debitCredit === 1)
    .reduce((sum, e) => sum + (Number(e.amount) || 0), 0)
})

const modalCreditTotal = computed(() => {
  return createForm.entries
    .filter(e => e.debitCredit === 2)
    .reduce((sum, e) => sum + (Number(e.amount) || 0), 0)
})

const modalDiffAmount = computed(() => {
  return Math.abs(modalDebitTotal.value - modalCreditTotal.value)
})

const isModalBalanced = computed(() => {
  return (
    createForm.entries.length >= 2 &&
    modalDebitTotal.value > 0 &&
    modalCreditTotal.value > 0 &&
    modalDiffAmount.value < 0.0001
  )
})

function handleAutoBalance() {
  const diff = modalDebitTotal.value - modalCreditTotal.value
  if (Math.abs(diff) < 0.0001) {
    ElMessage.info('借贷双方当前已经平衡，无需配平')
    return
  }

  const nextRow = createForm.entries.length + 1
  if (diff > 0) {
    // 借方大于贷方，需追加贷方
    createForm.entries.push({
      rowNum: nextRow,
      debitCredit: 2,
      subjectCode: '',
      accountNo: '',
      amount: Number(diff.toFixed(2)),
      summary: createForm.summary,
      unilateral: 1
    })
  } else {
    // 贷方大于借方，需追加借方
    createForm.entries.push({
      rowNum: nextRow,
      debitCredit: 1,
      subjectCode: '',
      accountNo: '',
      amount: Number(Math.abs(diff).toFixed(2)),
      summary: createForm.summary,
      unilateral: 1
    })
  }
  ElMessage.success('已自动根据借贷差额追加平衡分录！')
}

async function submitCreateForm(action: 'DRAFT' | 'SUBMIT') {
  if (!createForm.accountingDate) {
    ElMessage.error('请选择会计日期')
    return
  }
  if (!createForm.summary.trim()) {
    ElMessage.error('请输入凭证摘要')
    return
  }
  if (!createForm.makerName.trim()) {
    ElMessage.error('请输入制单人姓名')
    return
  }
  if (action === 'SUBMIT' && !isModalBalanced.value) {
    ElMessage.error('借贷金额不平衡，严禁提交初审！')
    return
  }

  submitLoading.value = true
  try {
    const res = await saveOrSubmitManualVoucher({
      applyNo: createForm.applyNo || undefined,
      voucherType: createForm.voucherType,
      tradeType: createForm.tradeType,
      accountingDate: createForm.accountingDate,
      summary: createForm.summary,
      makerName: createForm.makerName,
      action,
      entries: createForm.entries,
      auxiliaries: createForm.auxiliaries,
      attachments: createForm.attachments
    })
    ElMessage.success(res?.message || '操作成功')
    createDialogVisible.value = false
    loadData()
    loadStats()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

async function openEditModal(row: ManualVoucherApplyPageItem) {
  try {
    const detail = await getManualVoucherDetail(row.applyNo)
    if (detail) {
      createForm.applyNo = detail.applyNo
      createForm.voucherType = detail.voucherType
      createForm.tradeType = detail.tradeType
      createForm.accountingDate = detail.accountingDate
      createForm.summary = detail.summary
      createForm.makerName = detail.makerName
      createForm.entries = (detail.entries || []).map(e => ({ ...e }))
      createForm.auxiliaries = (detail.auxiliaries || []).map(a => ({ ...a }))
      createForm.attachments = (detail.attachments || []).map(att => ({ ...att }))
      createDialogVisible.value = true
    }
  } catch (error: any) {
    ElMessage.error('获取申请全景详情失败: ' + (error.message || '未知错误'))
  }
}

// ==================== 弹窗2：初审与复核操作 ====================
const auditDialogVisible = ref(false)
const auditLoading = ref(false)
const currentAuditRow = ref<ManualVoucherApplyPageItem | null>(null)
const auditForm = reactive({
  applyNo: '',
  action: 'PASS' as 'PASS' | 'REJECT',
  operatorName: '',
  operatorRole: 'AUDITOR',
  opinion: ''
})

function openAuditModal(row: ManualVoucherApplyPageItem, role: 'AUDITOR' | 'REVIEWER') {
  currentAuditRow.value = row
  auditForm.applyNo = row.applyNo
  auditForm.operatorRole = role
  auditForm.operatorName = role === 'AUDITOR' ? '李主管' : '赵经理'
  auditForm.opinion = role === 'AUDITOR' ? '科目与凭据核实无误，同意提交复核' : '终审合规，同意记账入账'
  auditDialogVisible.value = true
}

async function handleAuditAction(action: 'PASS' | 'REJECT') {
  if (!auditForm.operatorName.trim()) {
    ElMessage.error('请输入操作人姓名')
    return
  }
  if (action === 'REJECT' && !auditForm.opinion.trim()) {
    ElMessage.error('驳回时必须填写审批意见/驳回原因')
    return
  }

  auditLoading.value = true
  auditForm.action = action
  try {
    if (auditForm.operatorRole === 'AUDITOR') {
      await auditManualVoucher(auditForm)
    } else {
      await reviewManualVoucher(auditForm)
    }
    ElMessage.success(action === 'PASS' ? '审核通过！' : '已成功驳回该申请！')
    auditDialogVisible.value = false
    loadData()
    loadStats()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    auditLoading.value = false
  }
}

// ==================== 弹窗3：确认记账过账 ====================
const bookkeepDialogVisible = ref(false)
const bookkeepLoading = ref(false)
const currentBookkeepRow = ref<ManualVoucherApplyPageItem | null>(null)
const bookkeepForm = reactive({
  applyNo: '',
  bookkeeperName: '王出纳',
  remark: '确认入账，更新账户余额'
})

function openBookkeepModal(row: ManualVoucherApplyPageItem) {
  currentBookkeepRow.value = row
  bookkeepForm.applyNo = row.applyNo
  bookkeepForm.bookkeeperName = '王出纳'
  bookkeepForm.remark = '正式记账并过账'
  bookkeepDialogVisible.value = true
}

async function handleConfirmBookkeeping() {
  if (!bookkeepForm.bookkeeperName.trim()) {
    ElMessage.error('请输入记账人姓名')
    return
  }

  bookkeepLoading.value = true
  try {
    const res = await postManualVoucher(bookkeepForm)
    ElMessage.success(res?.message || '记账过账成功！')
    bookkeepDialogVisible.value = false
    loadData()
    loadStats()

    // 弹出成功提示并展示凭证号
    ElMessageBox.confirm(
      `手工凭证已成功入账并过账！\n正式凭证号：${res?.voucherNo}\n是否立即前往凭证管理查看全景档案？`,
      '记账成功提示',
      {
        confirmButtonText: '查看凭证档案',
        cancelButtonText: '留在此页',
        type: 'success'
      }
    ).then(() => {
      goToVoucherLedger(res?.voucherNo)
    }).catch(() => {})
  } catch (error: any) {
    ElMessage.error(error.message || '记账失败')
  } finally {
    bookkeepLoading.value = false
  }
}

// ==================== 弹窗4：全景档案与凭证印签详情 (参考《凭证详情.html》) ====================
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<ManualVoucherApplyDetail | null>(null)

async function openDetail(row: ManualVoucherApplyPageItem) {
  detailLoading.value = true
  detailDialogVisible.value = true
  try {
    const res = await getManualVoucherDetail(row.applyNo)
    if (res) {
      detailData.value = res
    }
  } catch (error) {
    console.error('获取详情失败:', error)
  } finally {
    detailLoading.value = false
  }
}

// ==================== 操作列分发处理 ====================
function handleCommand(cmd: string, row: ManualVoucherApplyPageItem) {
  switch (cmd) {
    case 'preview':
      openDetail(row)
      break
    case 'edit':
      openEditModal(row)
      break
    case 'submit':
      ElMessageBox.confirm(`确认将申请单 ${row.applyNo} 提交至初审岗审核吗？`, '提示', {
        type: 'info'
      }).then(() => {
        saveOrSubmitManualVoucher({
          applyNo: row.applyNo,
          voucherType: row.voucherType,
          tradeType: row.tradeType,
          accountingDate: row.accountingDate,
          summary: row.summary,
          makerName: row.makerName,
          action: 'SUBMIT',
          entries: row.entries
        }).then(() => {
          ElMessage.success('已成功提交初审！')
          loadData()
          loadStats()
        })
      })
      break
    case 'audit':
      openAuditModal(row, 'AUDITOR')
      break
    case 'review':
      openAuditModal(row, 'REVIEWER')
      break
    case 'bookkeep':
      openBookkeepModal(row)
      break
    case 'cancel':
      ElMessageBox.prompt('请输入作废原因：', '作废申请确认', {
        confirmButtonText: '确定作废',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '作废原因不能为空'
      }).then(({ value }) => {
        cancelManualVoucher(row.applyNo, '张会计', value).then(() => {
          ElMessage.success('单据已作废')
          loadData()
          loadStats()
        })
      })
      break
  }
}

function goToVoucherLedger(voucherNo?: string) {
  if (voucherNo) {
    router.push({
      path: '/business/voucher',
      query: { voucherNo }
    })
  }
}

// ==================== 格式化辅助方法 ====================
function formatAmount(val?: number) {
  if (val === null || val === undefined) return '0.00'
  return Number(val).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function formatVoucherDate(dateStr?: string) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    return `${parts[0]} 年 ${parts[1]} 月 ${parts[2]} 日`
  }
  return dateStr
}

function getStatusTag(status: number): '' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 1: return 'info'    // 草稿
    case 2: return 'warning' // 待初审
    case 3: return 'danger'  // 初审驳回
    case 4: return ''        // 待复核
    case 5: return 'danger'  // 复核驳回
    case 6: return 'warning' // 待记账
    case 7: return 'success' // 已记账
    case 8: return 'info'    // 已作废
    default: return 'info'
  }
}

function getTimelineType(action: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (action.includes('PASS') || action === 'BOOKKEEPING') return 'success'
  if (action.includes('REJECT') || action === 'CANCEL') return 'danger'
  if (action.includes('SUBMIT')) return 'primary'
  return 'info'
}

// ==================== 凭证附件与打印辅助 ====================
const attachmentModalVisible = ref(false)

function handlePreviewAttachment(row: ManualVoucherAttachmentItem) {
  ElMessage.info(`预览凭证附件：${row.fileName}`)
}

function handleDownloadAttachment(row: ManualVoucherAttachmentItem) {
  ElMessage.success(`开始下载附件：${row.fileName}`)
}

function handlePrint() {
  window.print()
}

function getFileTypeTag(ext?: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (!ext) return 'info'
  const upper = ext.toUpperCase()
  if (upper === 'PDF') return 'danger'
  if (['JPG', 'JPEG', 'PNG'].includes(upper)) return 'success'
  if (['XLS', 'XLSX'].includes(upper)) return 'warning'
  return 'info'
}

function formatBytes(bytes?: number) {
  if (!bytes) return '0 KB'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

onMounted(() => {
  loadStats()
  loadLeafSubjects()
  loadData()
})
</script>

<style scoped>
.manual-voucher-page {
  padding: 16px;
  background-color: var(--el-bg-color-page);
  min-height: calc(100vh - 84px);
}

.empty-aux-tip {
  padding: 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  background-color: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.classic-aux-table {
  margin-top: 8px;
}

.classic-aux-table th {
  background-color: #f7f9fa !important;
}

.attachment-name {
  display: inline-flex;
  align-items: center;
}

.attachment-name .underline {
  text-decoration: underline;
  cursor: pointer;
}

.mono-font {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.font-bold {
  font-weight: 600;
}

.cursor-pointer {
  cursor: pointer;
}

.text-primary {
  color: var(--el-color-primary);
}

.text-warning {
  color: var(--el-color-warning);
}

.text-danger {
  color: var(--el-color-danger);
}

.text-muted {
  color: var(--el-text-color-secondary);
}

.text-right {
  text-align: right;
}

.text-center {
  text-align: center;
}

/* 顶部状态卡片 */
.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.25s ease;
  border-radius: 8px;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-card.active {
  border: 2px solid var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.stat-icon {
  font-size: 16px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.text-purple {
  color: #722ed1;
}

/* 搜索卡片 */
.search-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.search-form {
  margin-bottom: -18px;
}

/* 表格卡片 */
.table-card {
  border-radius: 8px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 展开行样式 */
.expand-entry-box {
  padding: 12px 18px;
  background-color: var(--el-fill-color-light);
  border-radius: 6px;
}

.expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.expand-title {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}

.balance-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sum-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

/* 弹窗中的分录编制区域 */
.entries-section {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  background-color: var(--el-fill-color-blank);
}

.entries-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-right: 8px;
}

.balance-check-bar {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  background-color: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-5);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  transition: all 0.3s ease;
}

.balance-check-bar.is-balanced {
  background-color: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-5);
}

.bar-sep {
  margin: 0 10px;
  color: var(--el-border-color);
}

.audit-summary-box {
  padding: 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.8;
}

/* ==================== 经典财务凭证仿真样式 (参考《凭证详情.html》) ==================== */
.classic-voucher-dialog :deep(.el-dialog__body) {
  padding: 16px 24px;
  background-color: #f7f9fa;
}

.classic-voucher-sheet {
  background: #ffffff;
  border: 2px solid #555555;
  padding: 24px 30px;
  border-radius: 4px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
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

.meta-attachment .underline {
  text-decoration: underline;
  padding: 0 4px;
}

.voucher-entries-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #333333;
  font-size: 13px;
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

/* 时间轴 */
.timeline-section {
  background: #ffffff;
  padding: 16px 20px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}

.timeline-card {
  padding: 6px 10px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
}

.timeline-head {
  display: flex;
  align-items: center;
}

.timeline-action {
  font-weight: 600;
}
</style>
