<template>
  <div class="demo-page">
    <div class="fin-card">
      <h2>Step 21 · 前端公共基础组件展台</h2>
      <p class="desc">
        为满足账务核心系统规范与用户要求，以下抽离的所有公共组件均已完成封装，可开箱即用。
      </p>
    </div>

    <!-- 1. 弹窗与反馈组件（Toast、二次确认、BaseDialog） -->
    <div class="fin-card">
      <h3>1. 交互反馈组件（Toast、二次确认、通用模态框）</h3>
      <el-divider />
      <div class="demo-section">
        <h4>1.1 统一 Toast 提示</h4>
        <div class="btn-group">
          <el-button type="success" @click="toast.success('操作成功！')">Success Toast</el-button>
          <el-button type="danger" @click="toast.error('记账借贷不平：差额 100.00 元')">Error Toast</el-button>
          <el-button type="warning" @click="toast.warning('提示：当前账户可用余额不足')">Warning Toast</el-button>
          <el-button type="info" @click="toast.info('这是一条业务提醒信息')">Info Toast</el-button>
        </div>
      </div>

      <div class="demo-section">
        <h4>1.2 函数式二次确认弹窗 (useConfirm)</h4>
        <div class="btn-group">
          <el-button type="warning" @click="handleStandardConfirm">普通二次确认</el-button>
          <el-button type="danger" @click="handleDangerConfirm">高危红冲确认 (Danger)</el-button>
        </div>
      </div>

      <div class="demo-section">
        <h4>1.3 二次确认组件 (ConfirmDialog) 与 通用模态框 (BaseDialog)</h4>
        <div class="btn-group">
          <el-button type="primary" @click="showConfirmModal = true">打开 ConfirmDialog 组件</el-button>
          <el-button @click="showBaseDialog = true">打开 BaseDialog 基础模态框</el-button>
        </div>
      </div>
    </div>

    <!-- 2. 表单录入与金融数值（AmountInput, DictSelect, Checkbox, ActionButton） -->
    <div class="fin-card">
      <h3>2. 表单与金融录入组件（AmountInput、DictSelect、CheckboxGroup、ActionButton）</h3>
      <el-divider />
      <el-row :gutter="24">
        <el-col :span="8">
          <h4>2.1 金融金额输入框 (AmountInput)</h4>
          <p class="sub-desc">防负数、自动清洗非数字、失焦自动补齐千分位与 2 位小数：</p>
          <AmountInput
            v-model="inputAmount"
            placeholder="请输入记账金额"
            @change="onAmountChange"
          />
          <div class="result-box">绑定的原始值：{{ inputAmount }}</div>
        </el-col>

        <el-col :span="8">
          <h4>2.2 字典下拉选择器 (DictSelect)</h4>
          <p class="sub-desc">支持传入本地 options 或直接绑定 dictType：</p>
          <DictSelect
            v-model="selectedType"
            :options="accountTypeOptions"
            placeholder="请选择账户类型"
          />
          <div class="result-box">选中的值：{{ selectedType }}</div>
        </el-col>

        <el-col :span="8">
          <h4>2.3 通用复选框组 (BaseCheckboxGroup)</h4>
          <p class="sub-desc">支持全选/半选联动、数据驱动：</p>
          <BaseCheckboxGroup
            v-model="checkedChannels"
            :options="channelOptions"
            show-check-all
          />
          <div class="result-box">选中的渠道：{{ checkedChannels }}</div>
        </el-col>
      </el-row>

      <el-row :gutter="24" style="margin-top: 16px;">
        <el-col :span="12">
          <h4>2.4 通用操作按钮 (ActionButton)</h4>
          <p class="sub-desc">内置二次确认弹窗、异步防重复点击 loading：</p>
          <div class="btn-group">
            <ActionButton
              type="primary"
              confirm-message="确定要提交这笔凭证过账吗？"
              :on-click="simulateAsyncAction"
            >
              异步提交（带确认+Loading）
            </ActionButton>
            <ActionButton
              danger
              confirm-title="红冲凭证危险确认"
              confirm-type="danger"
              confirm-message="即将红冲该凭证，借贷方向将对调并生成红字凭证，是否继续？"
              :on-click="simulateAsyncAction"
            >
              危险操作按钮
            </ActionButton>
          </div>
        </el-col>

        <el-col :span="12">
          <h4>2.5 状态标签 (StatusTag) 与金额展示 (AmountDisplay)</h4>
          <p class="sub-desc">负数标红、等宽千分位、状态颜色自动映射：</p>
          <div class="tag-group">
            <StatusTag status="NORMAL" label="正常" />
            <StatusTag status="FROZEN" label="已冻结" />
            <StatusTag status="CANCELLED" label="已注销" />
            <StatusTag :status="1" label="待处理" />
            <StatusTag :status="2" label="过账中" />
            <StatusTag :status="3" label="已过账" />
          </div>
          <div class="amount-demo-group">
            <span>正数：<AmountDisplay value="1285320.68" prefix="￥" /></span>
            <span>负数标红：<AmountDisplay value="-8950.5" prefix="￥" /></span>
            <span>零值：<AmountDisplay value="0" prefix="￥" /></span>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 3. 通用表格与分页组件（BaseTable、BasePagination） -->
    <div class="fin-card">
      <h3>3. 通用表格与分页器（BaseTable + BasePagination）</h3>
      <el-divider />
      <div class="table-toolbar">
        <el-button type="primary" plain @click="loadMockData">刷新数据</el-button>
        <el-button @click="toggleLoading">切换 Loading (当前: {{ tableLoading }})</el-button>
        <el-button @click="toggleEmpty">清空数据验证 Empty 状态</el-button>
      </div>

      <BaseTable
        :data="tableList"
        :loading="tableLoading"
        :total="total"
        :page-no="pageNo"
        :page-size="pageSize"
        @page-change="onPageChange"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="accountNo" label="账户编号" min-width="150" />
        <el-table-column prop="accountName" label="账户名称" min-width="140" />
        <el-table-column prop="status" label="账户状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="row.statusDesc" />
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="当前可用余额" min-width="160" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.balance" prefix="￥" />
          </template>
        </el-table-column>
        <el-table-column prop="frozenBalance" label="冻结余额" min-width="140" align="right">
          <template #default="{ row }">
            <AmountDisplay :value="row.frozenBalance" prefix="￥" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <ActionButton
              type="primary"
              link
              size="small"
              confirm-message="确定查看明细？"
              @click="toast.info(`查看账户 ${row.accountNo}`)"
            >
              明细
            </ActionButton>
            <ActionButton
              type="danger"
              link
              size="small"
              danger
              confirm-type="danger"
              :confirm-message="`警告：即将注销账户 ${row.accountNo}，是否继续？`"
              @click="toast.success('注销成功')"
            >
              注销
            </ActionButton>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- 弹窗组件实例 -->
    <ConfirmDialog
      v-model="showConfirmModal"
      title="高危资金扣除确认"
      type="danger"
      message="确定要从客户可用余额中强制扣减 50,000.00 元吗？"
      description="注意：该操作不可撤销，扣款后将自动生成记账凭证并写入审计流水。"
      @confirm="onConfirmModalOk"
    />

    <BaseDialog
      v-model="showBaseDialog"
      title="记账规则配置详情"
      width="640px"
      @confirm="onBaseDialogConfirm"
    >
      <el-form label-width="100px">
        <el-form-item label="规则编码">
          <el-input value="RULE_TRANSFER_001" disabled />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input value="行内跨行转账记账规则" />
        </el-form-item>
        <el-form-item label="单笔限额">
          <AmountInput value="1000000.00" />
        </el-form-item>
      </el-form>
    </BaseDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { toast } from '@/utils/toast'
import { useConfirm } from '@/hooks/useConfirm'
import type { SelectOption } from '@/api/types'

// 1. 弹窗与提示
const showConfirmModal = ref(false)
const showBaseDialog = ref(false)

async function handleStandardConfirm() {
  const ok = await useConfirm('确定要导出当期日切对账报表吗？', {
    title: '导出提示'
  })
  if (ok) {
    toast.success('已开始生成对账报表')
  } else {
    toast.info('已取消操作')
  }
}

async function handleDangerConfirm() {
  const ok = await useConfirm('确定要对凭证 V202606250001 执行红冲吗？冲销后将生成反向红字分录！', {
    title: '⚠️ 危险冲账提示',
    type: 'error',
    confirmButtonText: '立即红冲'
  })
  if (ok) {
    toast.success('红冲执行成功，原凭证状态已更新为 REVERSED')
  }
}

function onConfirmModalOk() {
  toast.success('已完成扣款操作')
  showConfirmModal.value = false
}

function onBaseDialogConfirm() {
  toast.success('规则保存成功')
  showBaseDialog.value = false
}

// 2. 表单与录入数据
const inputAmount = ref('25890.50')
const selectedType = ref('1')
const checkedChannels = ref(['WX', 'ALI'])

const accountTypeOptions: SelectOption[] = [
  { label: '内部核算账户', value: '1' },
  { label: '外部对公账户', value: '2' },
  { label: '外部个人账户', value: '3' }
]

const channelOptions: SelectOption[] = [
  { label: '微信支付 (WX)', value: 'WX' },
  { label: '支付宝 (ALI)', value: 'ALI' },
  { label: '银联渠道 (UNION)', value: 'UNION' },
  { label: '网联渠道 (NUCC)', value: 'NUCC' }
]

function onAmountChange(val: string) {
  toast.info(`金额变更为: ${val}`)
}

async function simulateAsyncAction() {
  await new Promise((resolve) => setTimeout(resolve, 1000))
  toast.success('异步任务执行成功！')
}

// 3. 表格与分页数据
const tableLoading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(25)
const tableList = ref([
  { accountNo: '1001000000000001', accountName: '平台清算待结算户', status: 'NORMAL', statusDesc: '正常', balance: '1859200.00', frozenBalance: '0.00' },
  { accountNo: '1001000000000002', accountName: '商户资金可用户-A', status: 'NORMAL', statusDesc: '正常', balance: '45600.50', frozenBalance: '12000.00' },
  { accountNo: '1001000000000003', accountName: '商户资金可用户-B', status: 'FROZEN', statusDesc: '冻结', balance: '8900.00', frozenBalance: '8900.00' },
  { accountNo: '1001000000000004', accountName: '风控争议暂扣户', status: 'NORMAL', statusDesc: '正常', balance: '-500.00', frozenBalance: '0.00' }
])

function loadMockData() {
  tableLoading.value = true
  setTimeout(() => {
    tableList.value = [
      { accountNo: '1001000000000001', accountName: '平台清算待结算户', status: 'NORMAL', statusDesc: '正常', balance: '1859200.00', frozenBalance: '0.00' },
      { accountNo: '1001000000000002', accountName: '商户资金可用户-A', status: 'NORMAL', statusDesc: '正常', balance: '45600.50', frozenBalance: '12000.00' },
      { accountNo: '1001000000000003', accountName: '商户资金可用户-B', status: 'FROZEN', statusDesc: '冻结', balance: '8900.00', frozenBalance: '8900.00' },
      { accountNo: '1001000000000004', accountName: '风控争议暂扣户', status: 'NORMAL', statusDesc: '正常', balance: '-500.00', frozenBalance: '0.00' }
    ]
    total.value = 4
    tableLoading.value = false
    toast.success('数据已刷新')
  }, 400)
}

function toggleLoading() {
  tableLoading.value = !tableLoading.value
}

function toggleEmpty() {
  tableList.value = []
  total.value = 0
}

function onPageChange(page: number, size: number) {
  pageNo.value = page
  pageSize.value = size
  loadMockData()
}
</script>

<style scoped>
.demo-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
}

h3 {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

h4 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #606266;
}

.desc {
  font-size: 13px;
  color: #909399;
}

.sub-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.demo-section {
  margin-bottom: 16px;
}

.btn-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tag-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.amount-demo-group {
  display: flex;
  gap: 20px;
  font-size: 14px;
}

.result-box {
  margin-top: 6px;
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 4px 8px;
  border-radius: 4px;
}

.table-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
</style>
