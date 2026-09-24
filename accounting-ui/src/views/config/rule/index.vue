<template>
  <div class="rule-management-page">
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

        <el-form-item label="交易编码">
          <el-select
            v-model="searchForm.tradingCode"
            placeholder="全部交易编码"
            clearable
            filterable
            style="width: 200px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in tradingCodeOptions"
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
          新建记账规则
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 记账规则列表卡片 -->
    <div class="fin-card">
      <BaseTable
        :data="ruleList"
        :loading="loading"
        :show-pagination="true"
        :total="total"
        :current-page="searchForm.pageNo"
        :page-size="searchForm.pageSize"
        empty-text="暂无记账规则数据"
        row-key="id"
        @page-change="handlePageChange"
      >
        <!-- 展开行：展示当前规则下所有的借贷分录明细 -->
        <el-table-column type="expand" width="45">
          <template #default="{ row }">
            <div class="expand-entries-box">
              <div class="expand-header">
                <span class="expand-title">
                  【{{ row.ruleName }}】分录明细配置
                  <el-tag size="small" type="info" class="ml-2">
                    业务键: {{ row.businessCode }} / {{ row.tradingCode }} / {{ row.payChannel }}
                  </el-tag>
                </span>
                <span class="expand-summary">
                  借方分录: <span class="debit-text">{{ getEntryCount(row.entries, 1) }}</span> 行 ｜
                  贷方分录: <span class="credit-text">{{ getEntryCount(row.entries, 2) }}</span> 行
                </span>
              </div>

              <el-table :data="row.entries || []" border size="small" class="nested-entries-table">
                <el-table-column prop="rowNum" label="行号" width="60" align="center" />
                <el-table-column prop="debitCredit" label="借贷方向" width="90" align="center">
                  <template #default="subScope">
                    <el-tag
                      :type="subScope.row.debitCredit === 1 ? 'primary' : 'warning'"
                      size="small"
                      effect="dark"
                    >
                      {{ subScope.row.debitCredit === 1 ? '借 (Debit)' : '贷 (Credit)' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="fundsType" label="款项类型" width="130">
                  <template #default="subScope">
                    <span class="code-tag">{{ getFundsTypeLabel(subScope.row.fundsType) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="subjectCode" label="会计科目" min-width="190">
                  <template #default="subScope">
                    <span class="code-tag highlight">{{ subScope.row.subjectCode }}</span>
                    <span class="subject-name-text">{{ getSubjectName(subScope.row.subjectCode) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="accountScope" label="作用域" width="95" align="center">
                  <template #default="subScope">
                    <el-tag size="small" :type="subScope.row.accountScope === 1 ? 'info' : 'primary'">
                      {{ subScope.row.accountScope === 1 ? '内部分户' : '外部分户' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="currency" label="币种" width="75" align="center">
                  <template #default="subScope">
                    {{ subScope.row.currency || 'CNY' }}
                  </template>
                </el-table-column>
                <el-table-column prop="isUnilateral" label="单边更新" width="85" align="center">
                  <template #default="subScope">
                    <el-tag size="small" :type="subScope.row.isUnilateral ? 'success' : 'info'">
                      {{ subScope.row.isUnilateral ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="extendScript" label="SpEL计算表达式" min-width="150" show-overflow-tooltip>
                  <template #default="subScope">
                    <span v-if="subScope.row.extendScript" class="rule-mono">{{ subScope.row.extendScript }}</span>
                    <span v-else class="text-placeholder">默认全额 (businessDetail.amount)</span>
                  </template>
                </el-table-column>
                <el-table-column prop="summary" label="分录摘要" min-width="130" show-overflow-tooltip />
                <el-table-column label="辅助核算" width="95" align="center">
                  <template #default="subScope">
                    <el-badge
                      v-if="subScope.row.auxiliaries && subScope.row.auxiliaries.length > 0"
                      :value="subScope.row.auxiliaries.length"
                      type="primary"
                    >
                      <el-button link type="primary" size="small" @click="viewRowAuxiliaries(subScope.row)">
                        查看
                      </el-button>
                    </el-badge>
                    <span v-else class="text-placeholder">无</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="ruleName" label="规则名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="rule-name-cell">{{ row.ruleName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="businessCode" label="业务线" min-width="140">
          <template #default="{ row }">
            <span class="code-tag">{{ getBusinessLabel(row.businessCode) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tradingCode" label="交易编码" min-width="140">
          <template #default="{ row }">
            <span class="code-tag">{{ getTradingLabel(row.tradingCode) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="voucherType" label="凭证类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ getVoucherTypeLabel(row.voucherType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payChannel" label="支付渠道" width="105" align="center">
          <template #default="{ row }">
            <span class="code-tag">{{ getPayChannelLabel(row.payChannel) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isOpenAccount" label="自动开户" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isOpenAccount ? 'success' : 'info'">
              {{ row.isOpenAccount ? '允许' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
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

    <!-- 创建 / 编辑规则全功能弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1080px"
      top="4vh"
      destroy-on-close
      :close-on-click-modal="false"
      class="rule-edit-dialog"
    >
      <el-form
        ref="ruleFormRef"
        :model="formModel"
        :rules="formRules"
        label-width="110px"
        size="default"
      >
        <!-- 基础信息卡片 -->
        <div class="form-section-title">
          <span>基础交易信息</span>
        </div>
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input
                v-model="formModel.ruleName"
                placeholder="请输入规则名称，如：放款现金入账规则"
                maxlength="32"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务线" prop="businessCode">
              <el-select
                v-model="formModel.businessCode"
                placeholder="请选择所属业务线"
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
                placeholder="选择或输入交易编码，如 CASH_PAY"
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
                placeholder="选择或输入渠道，如 CASH / BANK"
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
            <el-form-item label="凭证类型" prop="voucherType">
              <el-select
                v-model="formModel.voucherType"
                placeholder="选择凭证类型，如 PAYMENT / RECEIPT"
                filterable
                allow-create
                default-first-option
                style="width: 100%;"
              >
                <el-option
                  v-for="item in voucherTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="前置规则" prop="preRuleId">
              <el-select
                v-model="formModel.preRuleId"
                placeholder="选择前置记账规则（选填）"
                clearable
                filterable
                style="width: 100%;"
              >
                <el-option
                  v-for="item in preRuleOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="允许自动开户">
              <el-switch
                v-model="formModel.isOpenAccount"
                active-text="允许"
                inactive-text="禁止"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则状态" prop="status">
              <el-radio-group v-model="formModel.status">
                <el-radio :value="1">待启用</el-radio>
                <el-radio :value="2">立即启用</el-radio>
                <el-radio v-if="isEditMode" :value="3">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="冻结时长(秒)">
              <div style="display: flex; align-items: center; gap: 12px;">
                <el-input-number
                  v-model="formModel.freezeDuration"
                  :min="0"
                  :max="8640000"
                  controls-position="right"
                  style="width: 220px;"
                />
                <span class="text-placeholder">（针对资金冻结类交易，0 表示不冻结）</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 会计借贷分录配置表格区域 -->
        <div class="form-section-title mt-4">
          <div class="title-left">
            <span>会计借贷分录明细</span>
            <!-- 借贷平衡指示灯 -->
            <el-tag
              :type="isBalanceValid ? 'success' : 'danger'"
              size="small"
              class="balance-tag"
              effect="light"
            >
              {{ balanceNoticeText }}
            </el-tag>
          </div>
          <div class="title-actions">
            <el-button size="small" type="primary" plain :icon="Plus" @click="handleAddEntry">
              添加分录行
            </el-button>
            <el-button size="small" :icon="MagicStick" @click="handleApplyStandardTemplate">
              预设一借一贷
            </el-button>
          </div>
        </div>

        <div class="entries-table-wrapper">
          <el-table :data="formModel.entries" border size="small" class="edit-entries-table">
            <el-table-column label="行号" width="55" align="center">
              <template #default="{ $index }">
                <span class="row-num-text">{{ $index + 1 }}</span>
              </template>
            </el-table-column>

            <el-table-column label="借贷方向 *" width="125" align="center">
              <template #default="{ row }">
                <el-select v-model="row.debitCredit" style="width: 100%;" size="small">
                  <el-option
                    v-for="item in DEBIT_CREDIT_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="交易款项类型 *" width="135">
              <template #default="{ row }">
                <el-select
                  v-model="row.fundsType"
                  placeholder="款项类型"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="item in fundsTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="会计科目 (末级) *" min-width="210">
              <template #default="{ row }">
                <el-select
                  v-model="row.subjectCode"
                  placeholder="检索并选择末级科目"
                  filterable
                  size="small"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="item in subjectOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="作用域" width="110">
              <template #default="{ row }">
                <el-select v-model="row.accountScope" size="small" style="width: 100%;">
                  <el-option
                    v-for="item in ACCOUNT_SCOPE_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="币种" width="85">
              <template #default="{ row }">
                <el-select v-model="row.currency" size="small" style="width: 100%;">
                  <el-option
                    v-for="item in currencyOptions"
                    :key="item.value"
                    :label="item.value"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>

            <el-table-column label="单边更新" width="75" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isUnilateral" size="small" />
              </template>
            </el-table-column>

            <el-table-column label="SpEL计算脚本" min-width="165">
              <template #default="{ row, $index }">
                <el-input
                  v-model="row.extendScript"
                  placeholder="如 #root.amount*0.1"
                  size="small"
                >
                  <template #suffix>
                    <el-tooltip content="大视窗编辑 SpEL 脚本" placement="top">
                      <el-icon
                        class="input-suffix-icon"
                        @click.stop="openTextEditor(row, 'extendScript', $index)"
                      >
                        <FullScreen />
                      </el-icon>
                    </el-tooltip>
                  </template>
                </el-input>
              </template>
            </el-table-column>

            <el-table-column label="摘要说明" min-width="160">
              <template #default="{ row, $index }">
                <el-input
                  v-model="row.summary"
                  placeholder="分录摘要"
                  size="small"
                >
                  <template #suffix>
                    <el-tooltip content="大视窗编辑分录摘要" placement="top">
                      <el-icon
                        class="input-suffix-icon"
                        @click.stop="openTextEditor(row, 'summary', $index)"
                      >
                        <FullScreen />
                      </el-icon>
                    </el-tooltip>
                  </template>
                </el-input>
              </template>
            </el-table-column>

            <el-table-column label="辅助核算" width="95" align="center">
              <template #default="{ row, $index }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="openAuxiliaryEditor(row, $index)"
                >
                  {{ row.auxiliaries && row.auxiliaries.length > 0 ? `已配(${row.auxiliaries.length})` : '+ 配置' }}
                </el-button>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="55" align="center">
              <template #default="{ $index }">
                <el-button
                  link
                  type="danger"
                  size="small"
                  :icon="Delete"
                  @click="handleRemoveEntry($index)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存记账规则
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 辅助核算项配置子弹窗 -->
    <el-dialog
      v-model="auxDialogVisible"
      title="配置分录辅助核算项"
      width="780px"
      append-to-body
      destroy-on-close
    >
      <div class="aux-notice mb-3">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="辅助核算项支持按固定金额或按比例分摊。若选择【按比例】，同一明细下所有比例之和必须精确等于 1.000000。"
        />
      </div>

      <div class="action-bar-small mb-2">
        <el-button type="primary" size="small" plain :icon="Plus" @click="handleAddAuxItem">
          添加辅助核算项
        </el-button>
        <span class="aux-ratio-sum ml-3" :class="{ 'text-danger': currentAuxRatioMismatch }">
          比例分摊当前总和: <strong>{{ currentAuxRatioSum }}</strong>
        </span>
      </div>

      <el-table :data="currentAuxList" border size="small">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column label="辅助核算类型 *" width="160">
          <template #default="{ row }">
            <el-select
              v-model="row.auxType"
              placeholder="选择类型"
              size="small"
              filterable
              allow-create
              default-first-option
            >
              <el-option
                v-for="item in auxTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="辅助核算编码 *" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.auxCode" placeholder="如 DEPT_001" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="分摊方式 *" width="120">
          <template #default="{ row }">
            <el-select v-model="row.allocationMethod" size="small">
              <el-option
                v-for="item in ALLOCATION_METHOD_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="分摊值" width="130">
          <template #default="{ row }">
            <el-input
              v-if="row.allocationMethod === 3"
              v-model="row.allocationValue"
              placeholder="如 0.600000"
              size="small"
            />
            <el-input
              v-else-if="row.allocationMethod === 2"
              v-model="row.allocationValue"
              placeholder="固定金额"
              size="small"
            />
            <span v-else class="text-placeholder">无需分摊值</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" :icon="Delete" @click="handleRemoveAuxItem($index)" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="auxDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveAuxiliaries">确认保存辅助核算</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- SpEL计算脚本 / 摘要说明 大视窗编辑器弹窗 -->
    <el-dialog
      v-model="textEditorVisible"
      :title="textEditorTitle"
      width="680px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="large-text-editor-dialog"
    >
      <div v-if="textEditorField === 'extendScript'" class="editor-help-box">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="Spring EL 表达式计算说明：留空表示直接采用业务明细的原始金额 (#root.amount)。"
        />
        <div class="quick-tags mt-2">
          <span class="quick-label">常用表达式变量参考（点击快捷插入）：</span>
          <div class="tags-container">
            <el-tag
              class="clickable-tag"
              size="small"
              effect="plain"
              @click="insertIntoEditor('#root.amount')"
            >
              #root.amount (交易金额)
            </el-tag>
            <el-tag
              class="clickable-tag"
              size="small"
              effect="plain"
              @click="insertIntoEditor('#root.amount * 0.05')"
            >
              * 0.05 (5%计提)
            </el-tag>
            <el-tag
              class="clickable-tag"
              size="small"
              effect="plain"
              @click="insertIntoEditor('#root.feeAmount')"
            >
              #root.feeAmount (手续费)
            </el-tag>
            <el-tag
              class="clickable-tag"
              size="small"
              effect="plain"
              @click="insertIntoEditor('#root.taxAmount')"
            >
              #root.taxAmount (税金)
            </el-tag>
            <el-tag
              class="clickable-tag"
              size="small"
              effect="plain"
              @click="insertIntoEditor('#root.extMap[\'customRate\']')"
            >
              #root.extMap['customRate'] (扩展参数)
            </el-tag>
          </div>
        </div>
      </div>

      <div v-else class="editor-help-box">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="分录摘要说明：用于凭证生成时展示的业务摘要，支持包含业务场景说明。"
        />
        <div class="quick-tags mt-2">
          <span class="quick-label">常用预设摘要（点击快捷插入）：</span>
          <div class="tags-container">
            <el-tag class="clickable-tag" size="small" effect="plain" @click="insertIntoEditor('扣减客户可用余额')">
              扣减客户可用余额
            </el-tag>
            <el-tag class="clickable-tag" size="small" effect="plain" @click="insertIntoEditor('银行支付放款资金')">
              银行支付放款资金
            </el-tag>
            <el-tag class="clickable-tag" size="small" effect="plain" @click="insertIntoEditor('冲抵客户贷款本金')">
              冲抵客户贷款本金
            </el-tag>
            <el-tag class="clickable-tag" size="small" effect="plain" @click="insertIntoEditor('商户待结算款入账')">
              商户待结算款入账
            </el-tag>
            <el-tag class="clickable-tag" size="small" effect="plain" @click="insertIntoEditor('代扣交易手续费')">
              代扣交易手续费
            </el-tag>
          </div>
        </div>
      </div>

      <div class="editor-input-box">
        <el-input
          v-model="textEditorContent"
          type="textarea"
          :rows="7"
          :placeholder="textEditorField === 'extendScript' ? '请输入或编辑 SpEL 表达式，如：#root.amount * 0.05' : '请输入分录摘要说明'"
          class="large-editor-textarea"
        />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="textEditorContent = ''">清空内容</el-button>
          <el-button @click="textEditorVisible = false">取消</el-button>
          <el-button type="primary" @click="saveLargeTextEditor">确认写入分录行</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 规则详情抽屉（全景档案） -->
    <el-drawer
      v-model="drawerVisible"
      title="记账规则全景档案"
      size="720px"
      destroy-on-close
    >
      <div v-if="detailRecord" class="rule-detail-container">
        <!-- 规则基础信息看板 -->
        <el-descriptions title="规则核心属性" :column="2" border size="small">
          <el-descriptions-item label="规则ID">{{ detailRecord.id }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ detailRecord.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="业务线">
            <span class="code-tag">{{ getBusinessLabel(detailRecord.businessCode) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="交易编码">
            <span class="code-tag">{{ getTradingLabel(detailRecord.tradingCode) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="凭证类型">
            {{ getVoucherTypeLabel(detailRecord.voucherType) }}
          </el-descriptions-item>
          <el-descriptions-item label="支付渠道">
            {{ getPayChannelLabel(detailRecord.payChannel) }}
          </el-descriptions-item>
          <el-descriptions-item label="自动开户">
            <el-tag size="small" :type="detailRecord.isOpenAccount ? 'success' : 'info'">
              {{ detailRecord.isOpenAccount ? '允许自动开户' : '禁止' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusMeta(detailRecord.status).tagType" size="small">
              {{ getStatusMeta(detailRecord.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="冻结时长">{{ detailRecord.freezeDuration || 0 }} 秒</el-descriptions-item>
          <el-descriptions-item label="前置规则">{{ getRuleNameById(detailRecord.preRuleId) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 分录明细列表 -->
        <div class="mt-4">
          <div class="detail-section-title">借贷分录明细（共 {{ (detailRecord.entries || []).length }} 行）</div>
          <el-table :data="detailRecord.entries || []" border size="small" class="mt-2">
            <el-table-column prop="rowNum" label="行号" width="55" align="center" />
            <el-table-column prop="debitCredit" label="借贷" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.debitCredit === 1 ? 'primary' : 'warning'" size="small" effect="dark">
                  {{ row.debitCredit === 1 ? '借方' : '贷方' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fundsType" label="款项类型" width="110">
              <template #default="{ row }">
                <span class="code-tag">{{ getFundsTypeLabel(row.fundsType) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="subjectCode" label="会计科目" min-width="160">
              <template #default="{ row }">
                <span class="code-tag highlight">{{ row.subjectCode }}</span>
                <span class="subject-name-text">{{ getSubjectName(row.subjectCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="accountScope" label="作用域" width="85" align="center">
              <template #default="{ row }">
                {{ row.accountScope === 1 ? '内部分户' : '外部分户' }}
              </template>
            </el-table-column>
            <el-table-column prop="isUnilateral" label="单边" width="60" align="center">
              <template #default="{ row }">
                {{ row.isUnilateral ? '是' : '否' }}
              </template>
            </el-table-column>
            <el-table-column prop="extendScript" label="SpEL计算脚本" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.extendScript" class="rule-mono">{{ row.extendScript }}</span>
                <span v-else class="text-placeholder">默认全额</span>
              </template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="110" show-overflow-tooltip />
          </el-table>
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
  Delete,
  MagicStick,
  VideoPlay,
  VideoPause,
  DocumentCopy,
  FullScreen
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getRulePage,
  getRuleById,
  createRule,
  updateRule,
  enableRule,
  disableRule,
  RULE_STATUS_OPTIONS,
  DEBIT_CREDIT_OPTIONS,
  ACCOUNT_SCOPE_OPTIONS,
  ALLOCATION_METHOD_OPTIONS,
  type RuleResponse,
  type RuleEntryRequest,
  type RuleAuxiliaryRequest,
  type RuleQueryRequest
} from '@/api/rule'
import { getDictByType } from '@/api/dict'
import { querySubjectPage } from '@/api/subject'

// ===== 字典与下拉选项 =====
interface OptionItem {
  label: string
  value: string
  name?: string
}

const businessCodeOptions = ref<OptionItem[]>([])
const tradingCodeOptions = ref<OptionItem[]>([])
const voucherTypeOptions = ref<OptionItem[]>([])
const payChannelOptions = ref<OptionItem[]>([])
const fundsTypeOptions = ref<OptionItem[]>([])
const currencyOptions = ref<OptionItem[]>([])
const auxTypeOptions = ref<OptionItem[]>([])
const subjectOptions = ref<OptionItem[]>([])

const subjectMap = ref<Record<string, string>>({})
const businessMap = ref<Record<string, string>>({})
const tradingMap = ref<Record<string, string>>({})
const voucherTypeMap = ref<Record<string, string>>({})
const payChannelMap = ref<Record<string, string>>({})
const fundsTypeMap = ref<Record<string, string>>({})

// ===== 列表检索状态 =====
const loading = ref(false)
const total = ref(0)
const ruleList = ref<RuleResponse[]>([])
const searchForm = ref<RuleQueryRequest>({
  pageNo: 1,
  pageSize: 10,
  businessCode: '',
  tradingCode: '',
  status: undefined
})

// ===== 所有规则（供前置规则下拉选择） =====
const allRules = ref<RuleResponse[]>([])

// ===== 新建/编辑弹窗状态 =====
const dialogVisible = ref(false)
const isEditMode = ref(false)
const currentEditId = ref<number | null>(null)
const submitting = ref(false)
const ruleFormRef = ref<FormInstance>()

interface RuleFormState {
  ruleName: string
  voucherType: string
  businessCode: string
  tradingCode: string
  payChannel: string
  isOpenAccount: boolean
  freezeDuration: number
  preRuleId?: number
  status: number
  entries: RuleEntryRequest[]
}

const formModel = ref<RuleFormState>({
  ruleName: '',
  voucherType: 'PAYMENT',
  businessCode: '',
  tradingCode: '',
  payChannel: 'CASH',
  isOpenAccount: false,
  freezeDuration: 0,
  preRuleId: undefined,
  status: 1,
  entries: []
})

const formRules: FormRules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' },
    { max: 32, message: '规则名称最多32个字符', trigger: 'blur' }
  ],
  businessCode: [{ required: true, message: '请选择业务线', trigger: 'change' }],
  tradingCode: [{ required: true, message: '请选择或输入交易编码', trigger: 'change' }],
  payChannel: [{ required: true, message: '请选择或输入支付渠道', trigger: 'change' }],
  voucherType: [{ required: true, message: '请选择凭证类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择规则状态', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  if (isEditMode.value) {
    return '编辑记账规则'
  }
  return '新建记账规则'
})

// ===== 借贷平衡实时校验 =====
const debitCount = computed(() => {
  return formModel.value.entries.filter(e => e.debitCredit === 1).length
})

const creditCount = computed(() => {
  return formModel.value.entries.filter(e => e.debitCredit === 2).length
})

const isBalanceValid = computed(() => {
  return debitCount.value >= 1 && creditCount.value >= 1
})

const balanceNoticeText = computed(() => {
  if (debitCount.value === 0 && creditCount.value === 0) {
    return '未配置分录行（必须包含借贷双方）'
  }
  if (debitCount.value === 0) {
    return `缺少借方分录 (当前贷方 ${creditCount.value} 行)`
  }
  if (creditCount.value === 0) {
    return `缺少贷方分录 (当前借方 ${debitCount.value} 行)`
  }
  return `借贷已配置：借方 ${debitCount.value} 行 / 贷方 ${creditCount.value} 行 (平衡)`
})

// ===== 辅助核算弹窗状态 =====
const auxDialogVisible = ref(false)
const currentEntryIndex = ref<number>(-1)
const currentAuxList = ref<RuleAuxiliaryRequest[]>([])

const currentAuxRatioSum = computed(() => {
  const sum = currentAuxList.value
    .filter(a => a.allocationMethod === 3)
    .reduce((acc, a) => {
      const val = parseFloat(String(a.allocationValue || 0))
      return acc + (isNaN(val) ? 0 : val)
    }, 0)
  return sum.toFixed(6)
})

const currentAuxRatioMismatch = computed(() => {
  const hasRatio = currentAuxList.value.some(a => a.allocationMethod === 3)
  if (!hasRatio) return false
  return currentAuxRatioSum.value !== '1.000000'
})

// ===== SpEL计算脚本 / 摘要 大视窗编辑器状态 =====
const textEditorVisible = ref(false)
const textEditorField = ref<'extendScript' | 'summary'>('extendScript')
const textEditorRowIndex = ref<number>(-1)
const textEditorRowNum = ref<number>(1)
const textEditorDebitCredit = ref<number>(1)
const textEditorContent = ref('')

const textEditorTitle = computed(() => {
  const dcLabel = textEditorDebitCredit.value === 1 ? '借方' : '贷方'
  const fieldName = textEditorField.value === 'extendScript' ? 'SpEL 计算脚本' : '分录摘要说明'
  return `分录编辑（第 ${textEditorRowNum.value} 行 / ${dcLabel}）- ${fieldName}`
})

// ===== 详情抽屉状态 =====
const drawerVisible = ref(false)
const detailRecord = ref<RuleResponse | null>(null)

// ===== 方法实现 =====

const getStatusMeta = (status: number) => {
  return RULE_STATUS_OPTIONS.find(s => s.value === status) || { label: '未知', tagType: 'info' }
}

const getBusinessLabel = (code: string) => {
  return businessMap.value[code] || code
}

const getTradingLabel = (code: string) => {
  return tradingMap.value[code] || code
}

const getVoucherTypeLabel = (code: string) => {
  return voucherTypeMap.value[code] || code
}

const getPayChannelLabel = (code: string) => {
  return payChannelMap.value[code] || code
}

const getFundsTypeLabel = (code: string) => {
  return fundsTypeMap.value[code] || code
}

const getSubjectName = (code: string) => {
  return subjectMap.value[code] || ''
}

const getEntryCount = (entries: any[] | undefined, debitCredit: number) => {
  if (!entries) return 0
  return entries.filter(e => e.debitCredit === debitCredit).length
}

// 前置规则下拉选项（排他当前编辑规则自身）
const preRuleOptions = computed(() => {
  return allRules.value
    .filter(r => !isEditMode.value || r.id !== currentEditId.value)
    .map(r => ({
      label: `[ID:${r.id}] ${r.ruleName} (${getBusinessLabel(r.businessCode)} - ${getTradingLabel(r.tradingCode)})`,
      value: r.id
    }))
})

// 根据前置规则ID获取展示名称
const getRuleNameById = (id?: number) => {
  if (!id || id === 0) return '无'
  const found = allRules.value.find(r => r.id === id)
  if (found) {
    return `[ID:${found.id}] ${found.ruleName}`
  }
  return `规则ID: ${id}`
}

// 加载初始字典与科目数据
const loadDictsAndSubjects = async () => {
  try {
    const [bizRes, tradeRes, voucherRes, payRes, fundsRes, currRes, auxRes, subRes] = await Promise.all([
      getDictByType('business_code'),
      getDictByType('trading_code'),
      getDictByType('voucher_type'),
      getDictByType('pay_channel'),
      getDictByType('funds_type'),
      getDictByType('currency'),
      getDictByType('auxiliary_type'),
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

    voucherTypeOptions.value = (voucherRes || []).map(d => {
      voucherTypeMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    payChannelOptions.value = (payRes || []).map(d => {
      payChannelMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    fundsTypeOptions.value = (fundsRes || []).map(d => {
      fundsTypeMap.value[d.dictCode] = d.dictName
      return { label: `${d.dictName} (${d.dictCode})`, value: d.dictCode }
    })

    currencyOptions.value = (currRes || []).map(d => ({
      label: `${d.dictName} (${d.dictCode})`,
      value: d.dictCode
    }))

    // 优先使用 auxiliary_type，若由于大小写或旧数据为空则降级兼容
    let actualAuxList = auxRes || []
    if (actualAuxList.length === 0) {
      try {
        const fallbackAux = await getDictByType('AUXILIARY_TYPE')
        if (fallbackAux && fallbackAux.length > 0) {
          actualAuxList = fallbackAux
        }
      } catch {}
    }

    auxTypeOptions.value = actualAuxList.map(d => ({
      label: `${d.dictName} (${d.dictCode})`,
      value: d.dictCode
    }))

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
    const res = await getRulePage(searchForm.value)
    // 针对每个规则，并行拉取其完整明细
    const detailedList = await Promise.all(
      (res.list || []).map(async rule => {
        try {
          const detail = await getRuleById(rule.id)
          return detail
        } catch {
          return rule
        }
      })
    )
    ruleList.value = detailedList
    total.value = res.total || 0

    // 同步拉取全部记账规则供前置规则下拉选择
    getRulePage({ pageSize: 500 }).then(allRes => {
      allRules.value = allRes.list || []
    }).catch(() => {})
  } catch (err: any) {
    ElMessage.error(err?.message || '加载记账规则列表失败')
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
    tradingCode: '',
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
    voucherType: 'PAYMENT',
    businessCode: businessCodeOptions.value[0]?.value || '',
    tradingCode: 'CASH_PAY',
    payChannel: 'CASH',
    isOpenAccount: false,
    freezeDuration: 0,
    preRuleId: undefined,
    status: 1,
    entries: [
      {
        rowNum: 1,
        fundsType: 'PRINCIPAL',
        subjectCode: subjectOptions.value[0]?.value || '',
        accountScope: 2,
        debitCredit: 1,
        currency: 'CNY',
        isUnilateral: false,
        extendScript: '',
        summary: '',
        auxiliaries: []
      },
      {
        rowNum: 2,
        fundsType: 'PRINCIPAL',
        subjectCode: subjectOptions.value[1]?.value || subjectOptions.value[0]?.value || '',
        accountScope: 1,
        debitCredit: 2,
        currency: 'CNY',
        isUnilateral: false,
        extendScript: '',
        summary: '',
        auxiliaries: []
      }
    ]
  }
  dialogVisible.value = true
}

// 快速应用一借一贷标准预设
const handleApplyStandardTemplate = () => {
  formModel.value.entries = [
    {
      rowNum: 1,
      fundsType: 'PRINCIPAL',
      subjectCode: subjectOptions.value[0]?.value || '',
      accountScope: 2,
      debitCredit: 1,
      currency: 'CNY',
      isUnilateral: false,
      extendScript: '',
      summary: '业务分录借方',
      auxiliaries: []
    },
    {
      rowNum: 2,
      fundsType: 'PRINCIPAL',
      subjectCode: subjectOptions.value[1]?.value || subjectOptions.value[0]?.value || '',
      accountScope: 1,
      debitCredit: 2,
      currency: 'CNY',
      isUnilateral: false,
      extendScript: '',
      summary: '业务分录贷方',
      auxiliaries: []
    }
  ]
  ElMessage.success('已预设标准一借一贷分录行')
}

// 添加分录行
const handleAddEntry = () => {
  const nextRowNum = formModel.value.entries.length + 1
  // 默认智能推断借贷方向：若已有借方无贷方则默认贷方，反之亦然
  const debitCountNow = formModel.value.entries.filter(e => e.debitCredit === 1).length
  const creditCountNow = formModel.value.entries.filter(e => e.debitCredit === 2).length
  const defaultDir = debitCountNow > creditCountNow ? 2 : 1

  formModel.value.entries.push({
    rowNum: nextRowNum,
    fundsType: 'PRINCIPAL',
    subjectCode: subjectOptions.value[0]?.value || '',
    accountScope: defaultDir === 1 ? 2 : 1,
    debitCredit: defaultDir,
    currency: 'CNY',
    isUnilateral: false,
    extendScript: '',
    summary: '',
    auxiliaries: []
  })
}

// 移除分录行
const handleRemoveEntry = (index: number) => {
  formModel.value.entries.splice(index, 1)
  // 重新规范行号
  formModel.value.entries.forEach((e, i) => {
    e.rowNum = i + 1
  })
}

// 打开编辑弹窗
const openEditDialog = async (row: RuleResponse) => {
  if (row.status === 2) {
    ElMessageBox.confirm(
      '已启用的记账规则处于核心交易生产状态，直接修改可能导致正在发生的流水解析错乱。需先停用规则后方可编辑，是否立即停用该规则？',
      '提示',
      {
        confirmButtonText: '停用并编辑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      try {
        await disableRule(row.id)
        ElMessage.success('规则已停用，现在可以进行编辑')
        await loadData()
        const refreshed = await getRuleById(row.id)
        doOpenEdit(refreshed)
      } catch (err: any) {
        ElMessage.error(err?.message || '停用规则失败')
      }
    }).catch(() => {})
    return
  }

  try {
    const detail = await getRuleById(row.id)
    doOpenEdit(detail)
  } catch (err: any) {
    ElMessage.error(err?.message || '获取规则详情失败')
  }
}

const doOpenEdit = (detail: RuleResponse) => {
  isEditMode.value = true
  currentEditId.value = detail.id
  formModel.value = {
    ruleName: detail.ruleName,
    voucherType: detail.voucherType,
    businessCode: detail.businessCode,
    tradingCode: detail.tradingCode,
    payChannel: detail.payChannel,
    isOpenAccount: !!detail.isOpenAccount,
    freezeDuration: detail.freezeDuration || 0,
    preRuleId: detail.preRuleId && detail.preRuleId > 0 ? detail.preRuleId : undefined,
    status: detail.status || 1,
    entries: (detail.entries || []).map(e => ({
      rowNum: e.rowNum,
      fundsType: e.fundsType,
      subjectCode: e.subjectCode,
      accountScope: e.accountScope,
      debitCredit: e.debitCredit,
      currency: e.currency || 'CNY',
      isUnilateral: !!e.isUnilateral,
      extendScript: e.extendScript || '',
      summary: e.summary || '',
      auxiliaries: (e.auxiliaries || []).map(a => ({
        auxType: a.auxType,
        auxCode: a.auxCode,
        allocationMethod: a.allocationMethod,
        allocationValue: a.allocationValue,
        extendScript: a.extendScript || ''
      }))
    }))
  }
  dialogVisible.value = true
}

// 复制规则快速新建
const handleCopyRule = async (row: RuleResponse) => {
  try {
    const detail = await getRuleById(row.id)
    isEditMode.value = false
    currentEditId.value = null
    formModel.value = {
      ruleName: `${detail.ruleName}-副本`,
      voucherType: detail.voucherType,
      businessCode: detail.businessCode,
      tradingCode: `${detail.tradingCode}_COPY`,
      payChannel: detail.payChannel,
      isOpenAccount: !!detail.isOpenAccount,
      freezeDuration: detail.freezeDuration || 0,
      preRuleId: detail.preRuleId && detail.preRuleId > 0 ? detail.preRuleId : undefined,
      status: 1, // 副本默认待启用
      entries: (detail.entries || []).map(e => ({
        rowNum: e.rowNum,
        fundsType: e.fundsType,
        subjectCode: e.subjectCode,
        accountScope: e.accountScope,
        debitCredit: e.debitCredit,
        currency: e.currency || 'CNY',
        isUnilateral: !!e.isUnilateral,
        extendScript: e.extendScript || '',
        summary: e.summary || '',
        auxiliaries: (e.auxiliaries || []).map(a => ({
          auxType: a.auxType,
          auxCode: a.auxCode,
          allocationMethod: a.allocationMethod,
          allocationValue: a.allocationValue,
          extendScript: a.extendScript || ''
        }))
      }))
    }
    dialogVisible.value = true
    ElMessage.info('已复制规则配置，请确认交易编码与分录后保存')
  } catch (err: any) {
    ElMessage.error(err?.message || '复制规则失败')
  }
}

// 辅助核算项编辑器
const openAuxiliaryEditor = (row: RuleEntryRequest, index: number) => {
  currentEntryIndex.value = index
  currentAuxList.value = JSON.parse(JSON.stringify(row.auxiliaries || []))
  auxDialogVisible.value = true
}

const handleAddAuxItem = () => {
  currentAuxList.value.push({
    auxType: auxTypeOptions.value[0]?.value || 'CUSTOMER',
    auxCode: '',
    allocationMethod: 1,
    allocationValue: '',
    extendScript: ''
  })
}

const handleRemoveAuxItem = (index: number) => {
  currentAuxList.value.splice(index, 1)
}

const saveAuxiliaries = () => {
  // 校验按比例分摊合法性
  if (currentAuxRatioMismatch.value) {
    ElMessage.error(`按比例分摊的辅助核算项分摊值之和必须等于 1.000000（当前为 ${currentAuxRatioSum.value}）`)
    return
  }
  for (const item of currentAuxList.value) {
    if (!item.auxType || !item.auxCode) {
      ElMessage.warning('辅助核算类型与项目编码均不可为空')
      return
    }
  }
  if (currentEntryIndex.value >= 0 && formModel.value.entries[currentEntryIndex.value]) {
    formModel.value.entries[currentEntryIndex.value].auxiliaries = JSON.parse(JSON.stringify(currentAuxList.value))
  }
  auxDialogVisible.value = false
  ElMessage.success('辅助核算项已暂存')
}

// 展开行查看辅助核算项
const viewRowAuxiliaries = (row: any) => {
  const lines = (row.auxiliaries || []).map((a: any, i: number) => {
    const methodText = a.allocationMethod === 3 ? `按比例(${a.allocationValue})` : a.allocationMethod === 2 ? `固定金额(${a.allocationValue})` : '不分摊'
    return `${i + 1}. [${a.auxType}] 编码:${a.auxCode} ｜ ${methodText}`
  }).join('\n')
  ElMessageBox.alert(lines || '无辅助核算项', '分录辅助核算明细', {
    confirmButtonText: '确定'
  })
}

// ===== SpEL计算脚本 / 摘要 大视窗编辑器操作 =====
const openTextEditor = (row: RuleEntryRequest, field: 'extendScript' | 'summary', index: number) => {
  textEditorRowIndex.value = index
  textEditorRowNum.value = row.rowNum || (index + 1)
  textEditorDebitCredit.value = row.debitCredit
  textEditorField.value = field
  textEditorContent.value = (row[field] as string) || ''
  textEditorVisible.value = true
}

const insertIntoEditor = (text: string) => {
  if (!textEditorContent.value) {
    textEditorContent.value = text
  } else {
    textEditorContent.value = `${textEditorContent.value} ${text}`
  }
}

const saveLargeTextEditor = () => {
  if (textEditorRowIndex.value >= 0 && formModel.value.entries[textEditorRowIndex.value]) {
    formModel.value.entries[textEditorRowIndex.value][textEditorField.value] = textEditorContent.value.trim()
  }
  textEditorVisible.value = false
  ElMessage.success('内容已更新至分录行')
}

// 提交表单保存
const handleSubmit = async () => {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate()

  if (formModel.value.entries.length === 0) {
    ElMessage.error('必须配置至少一借一贷分录行')
    return
  }

  if (!isBalanceValid.value) {
    ElMessage.error('记账规则必须同时包含借方和贷方分录行！')
    return
  }

  // 校验每行必填项
  for (const entry of formModel.value.entries) {
    if (!entry.subjectCode) {
      ElMessage.error(`分录行 #${entry.rowNum} 的会计科目未选择`)
      return
    }
    if (!entry.fundsType) {
      ElMessage.error(`分录行 #${entry.rowNum} 的款项类型未填写`)
      return
    }
  }

  submitting.value = true
  try {
    if (isEditMode.value && currentEditId.value) {
      await updateRule(currentEditId.value, {
        ruleName: formModel.value.ruleName,
        voucherType: formModel.value.voucherType,
        isOpenAccount: formModel.value.isOpenAccount,
        freezeDuration: formModel.value.freezeDuration,
        preRuleId: formModel.value.preRuleId || 0,
        status: formModel.value.status,
        entries: formModel.value.entries
      })
      ElMessage.success('记账规则更新成功')
    } else {
      await createRule({
        ruleName: formModel.value.ruleName,
        voucherType: formModel.value.voucherType,
        businessCode: formModel.value.businessCode,
        tradingCode: formModel.value.tradingCode,
        payChannel: formModel.value.payChannel,
        isOpenAccount: formModel.value.isOpenAccount,
        freezeDuration: formModel.value.freezeDuration,
        preRuleId: formModel.value.preRuleId || 0,
        status: formModel.value.status,
        entries: formModel.value.entries
      })
      ElMessage.success('记账规则创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存记账规则失败')
  } finally {
    submitting.value = false
  }
}

// 启用规则
const handleEnable = async (row: RuleResponse) => {
  try {
    await enableRule(row.id)
    ElMessage.success(`规则【${row.ruleName}】已成功启用`)
    loadData()
  } catch (err: any) {
    ElMessage.error(err?.message || '启用记账规则失败')
  }
}

// 停用规则
const handleDisable = (row: RuleResponse) => {
  ElMessageBox.confirm(
    `确定要停用记账规则【${row.ruleName}】吗？停用前系统将自动检查是否存在历史未完结凭证引用。`,
    '停用确认',
    {
      confirmButtonText: '确定停用',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await disableRule(row.id)
      ElMessage.success(`规则【${row.ruleName}】已成功停用`)
      loadData()
    } catch (err: any) {
      ElMessage.error(err?.message || '停用记账规则失败')
    }
  }).catch(() => {})
}

// 打开详情抽屉
const openDetailDrawer = async (row: RuleResponse) => {
  try {
    detailRecord.value = await getRuleById(row.id)
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
.rule-management-page {
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

.rule-mono {
  font-family: var(--fin-font-mono, monospace);
  font-size: 12px;
  color: #409eff;
}

.subject-name-text {
  margin-left: 6px;
  color: #303133;
  font-size: 12px;
}

.text-placeholder {
  color: #909399;
  font-size: 12px;
}

.rule-name-cell {
  font-weight: 500;
  color: #303133;
}

.more-btn {
  padding: 4px 8px;
  font-size: 14px;
}

/* 展开行样式 */
.expand-entries-box {
  padding: 12px 16px;
  background-color: #fafbfc;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  margin: 6px 12px;
}

.expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.expand-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.expand-summary {
  font-size: 12px;
  color: #606266;
}

.debit-text {
  color: #409eff;
  font-weight: bold;
}

.credit-text {
  color: #e6a23c;
  font-weight: bold;
}

.nested-entries-table {
  background-color: #ffffff;
}

/* 弹窗样式 */
.form-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #1f2f3d;
  padding-bottom: 8px;
  margin-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.balance-tag {
  font-weight: normal;
}

.title-actions {
  display: flex;
  gap: 8px;
}

.entries-table-wrapper {
  margin-top: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.row-num-text {
  font-weight: bold;
  color: #909399;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.aux-notice {
  font-size: 12px;
  margin-bottom: 16px;
}

.action-bar-small {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.aux-ratio-sum {
  font-size: 12px;
  color: #67c23a;
}

.aux-ratio-sum.text-danger {
  color: #f56c6c;
}

.rule-detail-container {
  padding: 4px 12px;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 输入框内侧右端大视窗编辑图标 */
.input-suffix-icon {
  cursor: pointer;
  color: #909399;
  font-size: 13px;
  transition: color 0.2s, transform 0.2s;
  vertical-align: middle;
}

.input-suffix-icon:hover {
  color: #409eff;
  transform: scale(1.2);
}

/* 大视窗编辑器弹窗样式 */
.editor-help-box {
  margin-bottom: 14px;
}

.quick-tags {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.quick-label {
  font-size: 12px;
  color: #606266;
  font-weight: 500;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.clickable-tag {
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.clickable-tag:hover {
  background-color: #ecf5ff;
  color: #409eff;
  border-color: #b3d8ff;
  transform: translateY(-1px);
}

.editor-input-box {
  margin-top: 12px;
}

.large-editor-textarea :deep(.el-textarea__inner) {
  font-family: var(--fin-font-mono, 'JetBrains Mono', 'Fira Code', Consolas, monospace);
  font-size: 13px;
  line-height: 1.6;
  padding: 10px 12px;
}
</style>
