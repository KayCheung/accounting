<template>
  <div class="dict-management-page">
    <!-- 顶部检索与操作卡片 -->
    <div class="fin-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="字典类型">
          <el-input
            v-model="searchForm.dictType"
            placeholder="如: ACCOUNT_STATUS"
            clearable
            style="width: 200px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 140px;"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组键">
          <el-input
            v-model="searchForm.groupKey"
            placeholder="分组过滤"
            clearable
            style="width: 160px;"
            @keyup.enter="handleSearch"
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

      <div class="action-bar">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新增字典项
        </el-button>
        <ActionButton
          type="warning"
          plain
          :icon="Refresh"
          confirm-title="刷新缓存确认"
          confirm-message="确定要刷新全量字典的 Redis 缓存吗？刷新后将立即同步各服务节点。"
          :on-click="handleRefreshCache"
        >
          刷新 Redis 缓存
        </ActionButton>
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
        empty-text="暂无匹配的字典数据"
        @page-change="onPageChange"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="dictType" label="字典类型编码" min-width="170">
          <template #default="{ row }">
            <span class="code-tag">{{ row.dictType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dictCode" label="字典项编码" min-width="130">
          <template #default="{ row }">
            <span class="code-tag highlight">{{ row.dictCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dictName" label="字典项名称" min-width="150" />
        <el-table-column prop="dictNameEn" label="英文名称" min-width="140" />
        <el-table-column prop="groupKey" label="分组键" min-width="110" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag
              :status="row.status === 1 ? 'NORMAL' : 'CANCELLED'"
              :label="row.status === 1 ? '启用' : '停用'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="system" label="属性" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.system ? 'warning' : 'info'" size="small">
              {{ row.system ? '系统内置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :icon="Edit"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-tooltip
              v-if="row.system"
              content="系统内置字典项禁止删除"
              placement="top"
            >
              <span>
                <el-button type="danger" link size="small" :icon="Delete" disabled>
                  删除
                </el-button>
              </span>
            </el-tooltip>
            <ActionButton
              v-else
              type="danger"
              link
              size="small"
              danger
              :icon="Delete"
              confirm-type="danger"
              confirm-title="删除字典确认"
              :confirm-message="`确定要删除字典项【${row.dictType} / ${row.dictCode}】吗？`"
              :on-click="() => handleDelete(row)"
            >
              删除
            </ActionButton>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- 新增 / 编辑字典弹窗 -->
    <BaseDialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑字典项' : '新增字典项'"
      width="580px"
      :confirm-loading="dialogSubmitLoading"
      @confirm="submitForm"
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-width="110px"
        label-position="right"
      >
        <el-form-item label="字典类型" prop="dictType">
          <el-input
            v-model="formModel.dictType"
            placeholder="如: ACCOUNT_STATUS"
            :disabled="isEdit"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input
            v-model="formModel.dictCode"
            placeholder="如: 1 或 NORMAL"
            :disabled="isEdit"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input
            v-model="formModel.dictName"
            placeholder="如: 正常"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="英文名称" prop="dictNameEn">
          <el-input
            v-model="formModel.dictNameEn"
            placeholder="如: Normal"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="分组键" prop="groupKey">
          <el-input
            v-model="formModel.groupKey"
            placeholder="可选，用于细分场景"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="排序序号" prop="sortOrder">
          <el-input-number
            v-model="formModel.sortOrder"
            :min="0"
            :max="9999"
            style="width: 140px;"
          />
        </el-form-item>
        <el-form-item label="字典状态" prop="status">
          <el-radio-group v-model="formModel.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="扩展属性 JSON" prop="extJson">
          <el-input
            v-model="formModel.extJson"
            type="textarea"
            :rows="3"
            placeholder='可选自定义扩展属性，必须为合法 JSON，例如：{"color":"#67c23a"}'
          />
        </el-form-item>
      </el-form>
    </BaseDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Search,
  RefreshRight,
  Plus,
  Refresh,
  Edit,
  Delete
} from '@element-plus/icons-vue'
import {
  getDictPage,
  createDict,
  updateDict,
  deleteDict,
  refreshDictCache,
  type DictResponse,
  type DictQueryRequest
} from '@/api/dict'
import { useDictStore } from '@/stores/dict'
import { toast } from '@/utils/toast'
import BaseTable from '@/components/common/BaseTable/index.vue'
import BaseDialog from '@/components/common/BaseDialog/index.vue'
import ActionButton from '@/components/common/ActionButton/index.vue'
import StatusTag from '@/components/common/StatusTag/index.vue'

const dictStore = useDictStore()

// 搜索条件
const searchForm = reactive<DictQueryRequest>({
  dictType: '',
  status: undefined,
  groupKey: ''
})

// 表格数据与分页
const loading = ref(false)
const dataList = ref<DictResponse[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// 加载列表数据
async function loadData() {
  loading.value = true
  try {
    const res = await getDictPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      dictType: searchForm.dictType || undefined,
      status: searchForm.status || undefined,
      groupKey: searchForm.groupKey || undefined
    })
    dataList.value = res.list || res.records || []
    total.value = res.total || 0
  } catch {
    dataList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  loadData()
}

function handleReset() {
  searchForm.dictType = ''
  searchForm.status = undefined
  searchForm.groupKey = ''
  pageNo.value = 1
  loadData()
}

function onPageChange(p: number, s: number) {
  pageNo.value = p
  pageSize.value = s
  loadData()
}

// 刷新 Redis 缓存
async function handleRefreshCache() {
  await refreshDictCache(searchForm.dictType || undefined)
  dictStore.clearCache()
  toast.success('字典 Redis 缓存已刷新，本地缓存已同步清理！')
}

// 删除字典项
async function handleDelete(row: DictResponse) {
  await deleteDict(row.dictType, row.dictCode)
  dictStore.clearCache(row.dictType)
  toast.success(`字典项【${row.dictType}/${row.dictCode}】删除成功！`)
  loadData()
}

// 弹窗表单状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogSubmitLoading = ref(false)
const formRef = ref<FormInstance>()

const formModel = reactive({
  dictType: '',
  dictCode: '',
  dictName: '',
  dictNameEn: '',
  groupKey: '',
  sortOrder: 0,
  status: 1,
  extJson: ''
})

const formRules: FormRules = {
  dictType: [{ required: true, message: '请输入字典类型编码', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典项编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典项名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择字典状态', trigger: 'change' }]
}

function openCreateDialog() {
  isEdit.value = false
  formModel.dictType = searchForm.dictType || ''
  formModel.dictCode = ''
  formModel.dictName = ''
  formModel.dictNameEn = ''
  formModel.groupKey = ''
  formModel.sortOrder = 0
  formModel.status = 1
  formModel.extJson = ''
  dialogVisible.value = true
}

function openEditDialog(row: DictResponse) {
  isEdit.value = true
  formModel.dictType = row.dictType
  formModel.dictCode = row.dictCode
  formModel.dictName = row.dictName
  formModel.dictNameEn = row.dictNameEn || ''
  formModel.groupKey = row.groupKey || ''
  formModel.sortOrder = row.sortOrder ?? 0
  formModel.status = row.status
  formModel.extJson = row.extJson || ''
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 校验 extJson 合法性
    if (formModel.extJson) {
      try {
        JSON.parse(formModel.extJson)
      } catch {
        toast.error('扩展属性必须为合法的 JSON 格式')
        return
      }
    }

    dialogSubmitLoading.value = true
    try {
      if (isEdit.value) {
        await updateDict(formModel.dictType, formModel.dictCode, {
          dictName: formModel.dictName,
          dictNameEn: formModel.dictNameEn || undefined,
          groupKey: formModel.groupKey || undefined,
          sortOrder: formModel.sortOrder,
          status: formModel.status,
          extJson: formModel.extJson || undefined
        })
        toast.success('字典项修改成功！')
      } else {
        await createDict({
          dictType: formModel.dictType,
          dictCode: formModel.dictCode,
          dictName: formModel.dictName,
          dictNameEn: formModel.dictNameEn || undefined,
          groupKey: formModel.groupKey || undefined,
          sortOrder: formModel.sortOrder,
          status: formModel.status,
          extJson: formModel.extJson || undefined
        })
        toast.success('字典项新增成功！')
      }
      dictStore.clearCache(formModel.dictType)
      dialogVisible.value = false
      loadData()
    } finally {
      dialogSubmitLoading.value = false
    }
  })
}

// 页面初始化加载数据
loadData()
</script>

<style scoped>
.dict-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-form {
  margin-bottom: 8px;
}

.action-bar {
  display: flex;
  gap: 12px;
  padding-top: 4px;
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
</style>
