<template>
  <div class="report-mapping-page">
    <!-- 顶部检索与操作栏卡片 -->
    <div class="fin-card filter-card">
      <!-- 快捷报表类型 Tab 切换 -->
      <div class="report-type-tabs">
        <el-radio-group v-model="activeReportType" size="default" @change="handleTabChange">
          <el-radio-button label="">全部报表</el-radio-button>
          <el-radio-button label="BALANCE_SHEET">资产负债表</el-radio-button>
          <el-radio-button label="INCOME_STATEMENT">利润表</el-radio-button>
        </el-radio-group>
      </div>

      <el-form :model="queryForm" inline class="search-form">
        <el-form-item label="关键字">
          <el-input
            v-model="queryForm.keyword"
            placeholder="搜索编码、名称、科目..."
            clearable
            style="width: 220px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="展示层级">
          <el-select
            v-model="queryForm.displayLevel"
            placeholder="全部层级"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in DISPLAY_LEVEL_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="财务分类">
          <el-select
            v-model="queryForm.itemCategory"
            placeholder="全部分类"
            clearable
            filterable
            style="width: 180px;"
            @change="handleSearch"
          >
            <el-option
              v-for="item in CATEGORY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
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

      <div class="action-btn-group">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新增行项配置
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">
          刷新
        </el-button>
        <el-dropdown @command="handleNavigateReport">
          <el-button type="default">
            前往查看报表 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/report/balance-sheet">资产负债表预览</el-dropdown-item>
              <el-dropdown-item command="/report/income-statement">利润表预览</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 配置数据表格卡片 -->
    <div class="fin-card table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        row-key="id"
      >
        <el-table-column prop="lineNo" label="行次" width="75" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain" class="line-badge">
              {{ row.lineNo }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="reportType" label="报表类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getReportTypeTag(row.reportType)">
              {{ getReportTypeLabel(row.reportType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="itemName" label="行项目显示名称" min-width="180">
          <template #default="{ row }">
            <span :class="['item-name', `level-${row.displayLevel}`]">
              {{ row.itemName }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="itemCode" label="行项唯一编码" min-width="170">
          <template #default="{ row }">
            <span class="mono-code">{{ row.itemCode }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="itemCategory" label="所属分类" min-width="150">
          <template #default="{ row }">
            <span class="category-text">{{ getCategoryLabel(row.itemCategory) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="displayLevel" label="层级" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getLevelTag(row.displayLevel)">
              {{ getLevelLabel(row.displayLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="subjectCodes" label="绑定会计科目" min-width="200">
          <template #default="{ row }">
            <div v-if="row.subjectCodes" class="subject-tags-wrapper">
              <el-tag
                v-for="code in splitCodes(row.subjectCodes)"
                :key="code"
                size="small"
                class="sub-tag"
                effect="light"
              >
                {{ code }}
              </el-tag>
            </div>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="calcOperator" label="计算规则" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getOperatorTag(row.calcOperator)">
              {{ row.calcOperator }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-color="#13ce66"
              inactive-color="#ff4949"
              :loading="row._statusLoading"
              @change="(val: any) => handleStatusChange(row, val ? 1 : 2)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-popconfirm
              title="确定要删除此报表配置项吗？删除后将影响报表聚合生成！"
              confirm-button-text="确认删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button link type="danger" size="small">
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryForm.pageNo"
          v-model:page-size="queryForm.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑财务报表配置项' : '新增财务报表配置项'"
      width="680px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        class="dialog-form"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报表类型" prop="reportType">
              <el-select
                v-model="formData.reportType"
                placeholder="选择报表类型"
                :disabled="isEdit"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in REPORT_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="行项目编码" prop="itemCode">
              <el-input
                v-model="formData.itemCode"
                placeholder="例如: ASSET_CASH"
                :disabled="isEdit"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目显示名称" prop="itemName">
              <el-input
                v-model="formData.itemName"
                placeholder="例如: 货币资金"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="财务大类" prop="itemCategory">
              <el-select
                v-model="formData.itemCategory"
                placeholder="选择大类分类"
                filterable
                style="width: 100%;"
              >
                <el-option
                  v-for="item in CATEGORY_OPTIONS"
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
            <el-form-item label="行次排序号" prop="lineNo">
              <el-input-number
                v-model="formData.lineNo"
                :min="1"
                :max="999"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="展示层级" prop="displayLevel">
              <el-radio-group v-model="formData.displayLevel">
                <el-radio :label="1">1-大类标题</el-radio>
                <el-radio :label="2">2-数据项</el-radio>
                <el-radio :label="3">3-合计行</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item
          v-if="formData.displayLevel === 2"
          label="绑定会计科目"
          prop="subjectCodes"
        >
          <el-input
            v-model="formData.subjectCodes"
            placeholder="输入绑定科目代码，多个科目以英文逗号分隔，如: 1001,1002,1012"
          />
          <div class="form-tip">支持前缀匹配或精确科目代码，多个科目按逗号分隔。</div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计算规则符号" prop="calcOperator">
              <el-select
                v-model="formData.calcOperator"
                placeholder="选择计算规则"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in CALC_OPERATOR_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="取数方向" prop="balanceDirection">
              <el-select
                v-model="formData.balanceDirection"
                placeholder="选择取数方向"
                style="width: 100%;"
              >
                <el-option label="AUTO - 跟随科目性质" value="AUTO" />
                <el-option label="DEBIT - 取借方余额" value="DEBIT" />
                <el-option label="CREDIT - 取贷方余额" value="CREDIT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item
          v-if="formData.calcOperator === 'FORMULA'"
          label="公式表达式"
          prop="formulaExpr"
        >
          <el-input
            v-model="formData.formulaExpr"
            placeholder="例如: 1-2-3-4-5-6-7+8"
          />
        </el-form-item>

        <el-form-item label="启用状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注说明" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="输入此报表行项的说明或编制口径..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
            保存提交
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Search,
  RefreshRight,
  Plus,
  Refresh,
  ArrowDown
} from '@element-plus/icons-vue'
import {
  pageReportItemMappings,
  createReportItemMapping,
  updateReportItemMapping,
  updateReportItemMappingStatus,
  deleteReportItemMapping,
  REPORT_TYPE_OPTIONS,
  DISPLAY_LEVEL_OPTIONS,
  CALC_OPERATOR_OPTIONS,
  CATEGORY_OPTIONS,
  type ReportItemMappingResponse,
  type ReportItemMappingQueryRequest
} from '@/api/reportMapping'

const router = useRouter()

// 状态定义
const loading = ref(false)
const total = ref(0)
const tableData = ref<(ReportItemMappingResponse & { _statusLoading?: boolean })[]>([])
const activeReportType = ref<string>('')

// 检索表单
const queryForm = reactive<ReportItemMappingQueryRequest>({
  pageNo: 1,
  pageSize: 20,
  reportType: undefined,
  keyword: undefined,
  itemCategory: undefined,
  displayLevel: undefined,
  status: undefined
})

// 弹窗表单
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive({
  reportType: 'BALANCE_SHEET',
  itemCode: '',
  itemName: '',
  itemCategory: 'CURRENT_ASSET',
  lineNo: 1,
  displayLevel: 2,
  subjectCodes: '',
  calcOperator: '+',
  balanceDirection: 'AUTO',
  formulaExpr: '',
  status: 1,
  remark: ''
})

const formRules: FormRules = {
  reportType: [{ required: true, message: '请选择报表类型', trigger: 'change' }],
  itemCode: [
    { required: true, message: '请输入行项目唯一编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]+$/, message: '仅支持大写英文字母、数字和下划线', trigger: 'blur' }
  ],
  itemName: [{ required: true, message: '请输入项目显示名称', trigger: 'blur' }],
  lineNo: [{ required: true, message: '请输入行次', trigger: 'blur' }],
  displayLevel: [{ required: true, message: '请选择展示层级', trigger: 'change' }],
  calcOperator: [{ required: true, message: '请选择计算规则', trigger: 'change' }]
}

// 标签映射辅助
function getReportTypeTag(type: string) {
  const match = REPORT_TYPE_OPTIONS.find(item => item.value === type)
  return (match?.tagType as any) || 'info'
}

function getReportTypeLabel(type: string) {
  const match = REPORT_TYPE_OPTIONS.find(item => item.value === type)
  return match?.label || type
}

function getLevelTag(level: number) {
  if (level === 1) return 'info'
  if (level === 2) return 'primary'
  return 'success'
}

function getLevelLabel(level: number) {
  if (level === 1) return '大类标题'
  if (level === 2) return '数据明细'
  if (level === 3) return '合计小计'
  return String(level)
}

function getOperatorTag(op: string) {
  if (op === '+') return 'success'
  if (op === '-') return 'danger'
  if (op.includes('SUM') || op === 'FORMULA') return 'warning'
  return 'info'
}

function getCategoryLabel(category: string) {
  const match = CATEGORY_OPTIONS.find(item => item.value === category)
  return match?.label || category
}

function splitCodes(codes: string): string[] {
  if (!codes) return []
  return codes.split(',').map(c => c.trim()).filter(Boolean)
}

// 查询数据
async function fetchData() {
  loading.value = true
  try {
    const res = await pageReportItemMappings(queryForm)
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch (error: any) {
    ElMessage.error(error?.message || '加载报表配置列表失败')
  } finally {
    loading.value = false
  }
}

function handleTabChange(val: any) {
  queryForm.reportType = val || undefined
  queryForm.pageNo = 1
  fetchData()
}

function handleSearch() {
  queryForm.pageNo = 1
  fetchData()
}

function handleReset() {
  activeReportType.value = ''
  queryForm.pageNo = 1
  queryForm.pageSize = 20
  queryForm.reportType = undefined
  queryForm.keyword = undefined
  queryForm.itemCategory = undefined
  queryForm.displayLevel = undefined
  queryForm.status = undefined
  fetchData()
}

function handleSizeChange(size: number) {
  queryForm.pageSize = size
  queryForm.pageNo = 1
  fetchData()
}

function handleCurrentChange(page: number) {
  queryForm.pageNo = page
  fetchData()
}

function handleNavigateReport(path: string) {
  router.push(path)
}

// 状态切换
async function handleStatusChange(row: any, newStatus: number) {
  row._statusLoading = true
  try {
    await updateReportItemMappingStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(`配置项[${row.itemName}]已成功${newStatus === 1 ? '启用' : '停用'}`)
  } catch (error: any) {
    ElMessage.error(error?.message || '切换状态失败')
  } finally {
    row._statusLoading = false
  }
}

// 删除
async function handleDelete(id: number) {
  try {
    await deleteReportItemMapping(id)
    ElMessage.success('报表配置项已删除')
    fetchData()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
  }
}

// 弹窗控制
function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  formData.reportType = activeReportType.value || 'BALANCE_SHEET'
  formData.itemCode = ''
  formData.itemName = ''
  formData.itemCategory = 'CURRENT_ASSET'
  formData.lineNo = (tableData.value.length ? Math.max(...tableData.value.map(i => i.lineNo)) + 1 : 1)
  formData.displayLevel = 2
  formData.subjectCodes = ''
  formData.calcOperator = '+'
  formData.balanceDirection = 'AUTO'
  formData.formulaExpr = ''
  formData.status = 1
  formData.remark = ''
  dialogVisible.value = true
}

function openEditDialog(row: ReportItemMappingResponse) {
  isEdit.value = true
  editId.value = row.id
  formData.reportType = row.reportType
  formData.itemCode = row.itemCode
  formData.itemName = row.itemName
  formData.itemCategory = row.itemCategory
  formData.lineNo = row.lineNo
  formData.displayLevel = row.displayLevel
  formData.subjectCodes = row.subjectCodes || ''
  formData.calcOperator = row.calcOperator
  formData.balanceDirection = row.balanceDirection || 'AUTO'
  formData.formulaExpr = row.formulaExpr || ''
  formData.status = row.status
  formData.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value && editId.value) {
        await updateReportItemMapping(editId.value, {
          itemName: formData.itemName,
          itemCategory: formData.itemCategory,
          lineNo: formData.lineNo,
          displayLevel: formData.displayLevel,
          subjectCodes: formData.subjectCodes,
          calcOperator: formData.calcOperator,
          balanceDirection: formData.balanceDirection,
          formulaExpr: formData.formulaExpr,
          status: formData.status,
          remark: formData.remark
        })
        ElMessage.success('更新报表配置项成功')
      } else {
        await createReportItemMapping(formData)
        ElMessage.success('新增报表配置项成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error: any) {
      ElMessage.error(error?.message || '保存失败')
    } finally {
      submitLoading.value = false
    }
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.report-mapping-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fin-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.report-type-tabs {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e8e8e8;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.action-btn-group {
  margin-top: 8px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.line-badge {
  font-weight: 600;
  min-width: 28px;
}

.item-name {
  font-size: 14px;
}

.item-name.level-1 {
  font-weight: 700;
  color: #303133;
}

.item-name.level-2 {
  padding-left: 12px;
  color: #606266;
}

.item-name.level-3 {
  font-weight: 600;
  color: #1f2d3d;
}

.mono-code {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  color: #409eff;
}

.category-text {
  font-size: 13px;
  color: #909399;
}

.subject-tags-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.sub-tag {
  font-family: 'Consolas', monospace;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
