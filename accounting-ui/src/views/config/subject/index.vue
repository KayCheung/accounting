<template>
  <div class="subject-management-page">
    <!-- 顶部账类分类 Tabs -->
    <div class="fin-card tab-card">
      <el-tabs v-model="activeCategoryTab" @tab-change="handleCategoryTabChange">
        <el-tab-pane label="全部科目" name="all" />
        <el-tab-pane
          v-for="cat in SUBJECT_CATEGORIES"
          :key="cat.value"
          :label="cat.label"
          :name="String(cat.value)"
        />
      </el-tabs>
    </div>

    <!-- 检索与操作卡片 -->
    <div class="fin-card">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="科目状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 140px;"
            @change="handleSearch"
          >
            <el-option label="全部状态" :value="0" />
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字搜索">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索编码 / 名称"
            clearable
            style="width: 220px;"
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
        <el-button type="primary" :icon="Plus" @click="openCreateTopDialog">
          新建顶级科目
        </el-button>
        <el-button :icon="Refresh" @click="fetchRootSubjects">
          刷新根节点
        </el-button>
      </div>
    </div>

    <!-- 树形表格卡片 (懒加载模式) -->
    <div class="fin-card">
      <BaseTable
        ref="baseTableRef"
        :data="filteredTableData"
        :loading="loading"
        :show-pagination="false"
        row-key="id"
        lazy
        :load="loadChildren"
        :tree-props="{ hasChildren: 'hasChildren', children: 'children' }"
        empty-text="暂无科目数据"
      >
        <el-table-column prop="subjectCode" label="科目编码" min-width="160">
          <template #default="{ row }">
            <span class="code-tag">{{ row.subjectCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="科目名称" min-width="180" />
        <el-table-column prop="subjectLevel" label="级次" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.subjectLevel }}级</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subjectCategory" label="账类" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getCategoryMeta(row.subjectCategory)?.tagType || 'info'"
              size="small"
            >
              {{ getCategoryMeta(row.subjectCategory)?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nature" label="科目性质" width="130" align="center">
          <template #default="{ row }">
            <span>{{ getNatureLabel(row.nature) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="debitCredit" label="余额方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.debitCredit === 1 ? 'primary' : 'success'"
              size="small"
              effect="plain"
            >
              {{ row.debitCredit === 1 ? '借' : '贷' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="leaf" label="末级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.leaf ? 'success' : 'info'" size="small">
              {{ row.leaf ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="allowPost" label="允许记账" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.allowPost ? 'success' : 'info'" size="small">
              {{ row.allowPost ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="allowOpenAccount" label="允许开户" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.allowOpenAccount ? 'success' : 'info'" size="small">
              {{ row.allowOpenAccount ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag
              :status="row.status === 1 ? 'NORMAL' : 'CANCELLED'"
              :label="row.status === 1 ? '启用' : '停用'"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd: string) => handleActionCommand(cmd, row)">
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
                  <el-dropdown-item command="edit" :icon="Edit">编辑科目</el-dropdown-item>
                  <el-dropdown-item command="addChild" :icon="Plus">添加子级</el-dropdown-item>
                  <el-dropdown-item command="auxiliary" :icon="Setting">辅助核算</el-dropdown-item>
                  <el-dropdown-item
                    command="disable"
                    :icon="Delete"
                    :disabled="row.status === 2"
                    divided
                    style="color: var(--el-color-danger)"
                  >
                    停用科目
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </BaseTable>
    </div>

    <!-- 科目新增 / 编辑弹窗 -->
    <BaseDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :confirm-loading="submitting"
      @confirm="handleSubmitSubject"
      @cancel="dialogVisible = false"
    >
      <el-form
        ref="formRef"
        :model="subjectForm"
        :rules="formRules"
        label-width="100px"
      >
        <!-- 父级科目展示 -->
        <el-form-item label="上级科目">
          <el-input
            :model-value="parentSubjectDisplay"
            disabled
            placeholder="无（顶级科目）"
          />
        </el-form-item>

        <!-- 科目编码 -->
        <el-form-item label="科目编码" prop="subjectCode">
          <el-input
            v-model="subjectForm.subjectCode"
            placeholder="如: 1001 或 100101"
            :disabled="isEditMode"
            maxlength="32"
          >
            <template v-if="!isEditMode && currentParent" #prepend>
              {{ currentParent.subjectCode }}
            </template>
          </el-input>
          <div v-if="!isEditMode && currentParent" class="form-tip">
            子科目编码必须以父科目编码「{{ currentParent.subjectCode }}」为前缀
          </div>
        </el-form-item>

        <!-- 科目名称 -->
        <el-form-item label="科目名称" prop="subjectName">
          <el-input
            v-model="subjectForm.subjectName"
            placeholder="如: 库存现金 或 人民币现金"
            maxlength="64"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <!-- 账类 -->
            <el-form-item label="账类" prop="subjectCategory">
              <el-select
                v-model="subjectForm.subjectCategory"
                placeholder="请选择账类"
                style="width: 100%;"
                :disabled="Boolean(currentParent)"
              >
                <el-option
                  v-for="cat in SUBJECT_CATEGORIES"
                  :key="cat.value"
                  :label="cat.label"
                  :value="cat.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 科目性质 -->
            <el-form-item label="科目性质" prop="nature">
              <el-select
                v-model="subjectForm.nature"
                placeholder="请选择性质"
                style="width: 100%;"
              >
                <el-option
                  v-for="n in SUBJECT_NATURES"
                  :key="n.value"
                  :label="n.label"
                  :value="n.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <!-- 余额方向 -->
            <el-form-item label="余额方向" prop="debitCredit">
              <el-radio-group
                v-model="subjectForm.debitCredit"
                :disabled="Boolean(currentParent)"
              >
                <el-radio :value="1">借方</el-radio>
                <el-radio :value="2">贷方</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 科目级次 -->
            <el-form-item label="科目级次">
              <el-tag type="info">{{ subjectForm.subjectLevel }} 级</el-tag>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">记账与核算控制</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="末级科目" label-width="80px">
              <el-switch
                v-model="subjectForm.leaf"
                @change="handleLeafChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="允许记账" label-width="80px">
              <el-switch
                v-model="subjectForm.allowPost"
                :disabled="!subjectForm.leaf"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="允许开户" label-width="80px">
              <el-switch
                v-model="subjectForm.allowOpenAccount"
                :disabled="!subjectForm.leaf"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="启用状态">
          <el-radio-group v-model="subjectForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </BaseDialog>

    <!-- 辅助核算项抽屉 (从表维护) -->
    <el-drawer
      v-model="auxDrawerVisible"
      title="科目辅助核算项配置"
      size="620px"
      destroy-on-close
    >
      <div v-if="selectedSubject" class="aux-drawer-body">
        <!-- 科目信息提示条 -->
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="subject-info-alert"
        >
          <template #title>
            当前科目：
            <strong>[{{ selectedSubject.subjectCode }}] {{ selectedSubject.subjectName }}</strong>
            （{{ selectedSubject.leaf ? '末级科目' : '非末级科目' }}）
          </template>
        </el-alert>

        <!-- 添加辅助核算项模块 -->
        <div class="aux-add-card">
          <div class="aux-title">添加核算维度</div>
          <el-form
            ref="auxFormRef"
            :model="auxForm"
            :rules="auxRules"
            inline
            class="aux-form"
          >
            <el-form-item label="维度类别" prop="auxiliaryType">
              <el-select
                v-model="auxForm.auxiliaryType"
                placeholder="选择或输入"
                filterable
                allow-create
                default-first-option
                style="width: 190px;"
              >
                <el-option
                  v-for="opt in auxTypeOptions"
                  :key="opt.code"
                  :label="`${opt.label} (${opt.code})`"
                  :value="opt.code"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="必填">
              <el-switch v-model="auxForm.required" />
            </el-form-item>
            <el-form-item label="默认项目代码">
              <el-input
                v-model="auxForm.defaultAuxCode"
                placeholder="选填"
                style="width: 130px;"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="auxAdding"
                @click="handleAddAuxiliary"
              >
                添加
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 已配置辅助核算项列表 -->
        <div class="aux-table-container">
          <div class="aux-title">已配置维度列表</div>
          <el-table
            :data="auxList"
            v-loading="auxLoading"
            stripe
            border
            style="width: 100%;"
            empty-text="当前科目尚未配置辅助核算维度"
          >
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="auxiliaryType" label="核算类别代码" min-width="150">
              <template #default="{ row }">
                <span class="code-tag">{{ row.auxiliaryType }}</span>
                <span class="aux-label-hint">{{ getAuxTypeLabel(row.auxiliaryType) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="required" label="是否必填" width="100" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.required"
                  @change="(val: any) => handleToggleAuxRequired(row, Boolean(val))"
                />
              </template>
            </el-table-column>
            <el-table-column prop="defaultAuxCode" label="默认项目代码" min-width="120">
              <template #default="{ row }">
                <span>{{ row.defaultAuxCode || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-tooltip
                  :disabled="!row.required"
                  content="必填辅助核算项不允许删除"
                  placement="top"
                >
                  <span>
                    <el-button
                      type="danger"
                      link
                      :disabled="row.required"
                      @click="handleDeleteAuxiliary(row)"
                    >
                      删除
                    </el-button>
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, RefreshRight, Edit, Delete, Setting, Refresh, MoreFilled } from '@element-plus/icons-vue'
import BaseTable from '@/components/common/BaseTable/index.vue'
import BaseDialog from '@/components/common/BaseDialog/index.vue'
import StatusTag from '@/components/common/StatusTag/index.vue'
import { useConfirm } from '@/hooks/useConfirm'
import { toast } from '@/utils/toast'
import { getDictByType } from '@/api/dict'
import {
  querySubjectTree,
  createSubject,
  updateSubject,
  disableSubject,
  listSubjectAuxiliary,
  createSubjectAuxiliary,
  updateSubjectAuxiliary,
  deleteSubjectAuxiliary,
  SUBJECT_CATEGORIES,
  SUBJECT_NATURES,
  type SubjectItem,
  type SubjectCreateRequest,
  type SubjectUpdateRequest,
  type AuxiliaryItem
} from '@/api/subject'

// ==================== 状态定义 ====================

const baseTableRef = ref<any>()
const loading = ref(false)
const tableData = ref<SubjectItem[]>([])
const activeCategoryTab = ref('all')

const searchForm = reactive({
  status: 0,
  keyword: ''
})

// 缓存懒加载的节点以便新增/更新时局部刷新
const lazyTreeNodeMap = new Map<number, { row: SubjectItem; treeNode: unknown; resolve: (data: SubjectItem[]) => void }>()

// ==================== 过滤与查询 ====================

const filteredTableData = computed(() => tableData.value)

function getCategoryMeta(categoryCode: number) {
  return SUBJECT_CATEGORIES.find((c) => c.value === categoryCode)
}

function getNatureLabel(natureCode: number) {
  return SUBJECT_NATURES.find((n) => n.value === natureCode)?.label || '未知性质'
}

/**
 * 加载根科目列表（支持跨级关键字搜索与状态/账类跨级过滤）
 */
async function fetchRootSubjects() {
  loading.value = true
  try {
    const categoryVal = activeCategoryTab.value === 'all' ? undefined : Number(activeCategoryTab.value)
    const statusVal = searchForm.status === 0 ? undefined : (searchForm.status || undefined)
    const kw = searchForm.keyword.trim() || undefined
    const isFiltered = Boolean(kw || statusVal || categoryVal !== undefined)

    const res = await querySubjectTree({
      parentId: isFiltered ? undefined : 0,
      status: statusVal,
      subjectCategory: categoryVal,
      keyword: kw
    })
    tableData.value = (res || []).map((item: SubjectItem) => ({
      ...item,
      // 过滤搜索模式下平铺展示全部命中结果，默认根树模式下非末级节点允许懒加载展开
      hasChildren: isFiltered ? false : !item.leaf
    }))
  } catch (err: any) {
    console.error('获取科目列表失败', err)
  } finally {
    loading.value = false
  }
}

/**
 * 表格懒加载子科目
 */
async function loadChildren(
  row: SubjectItem,
  treeNode: unknown,
  resolve: (data: SubjectItem[]) => void
) {
  lazyTreeNodeMap.set(row.id, { row, treeNode, resolve })
  try {
    const statusVal = searchForm.status === 0 ? undefined : (searchForm.status || undefined)
    const res = await querySubjectTree({
      parentId: row.id,
      status: statusVal
    })
    const children = (res || []).map((item: SubjectItem) => ({
      ...item,
      hasChildren: !item.leaf
    }))
    resolve(children)
  } catch (err) {
    resolve([])
  }
}

function handleCategoryTabChange() {
  fetchRootSubjects()
}

function handleSearch() {
  fetchRootSubjects()
}

function handleReset() {
  searchForm.status = 0
  searchForm.keyword = ''
  activeCategoryTab.value = 'all'
  // 清空 Element Plus 懒加载树节点缓存
  const elTable = baseTableRef.value?.tableRef
  if (elTable?.store?.states?.lazyTreeNodeMap?.value) {
    elTable.store.states.lazyTreeNodeMap.value = {}
  }
  lazyTreeNodeMap.clear()
  fetchRootSubjects()
}

/**
 * 局部刷新指定父节点的子树并刷新根列表
 */
async function refreshSubjectData(parentSubjectId?: number) {
  if (parentSubjectId && parentSubjectId > 0) {
    try {
      const statusVal = searchForm.status === 0 ? 0 : searchForm.status || undefined
      const res = await querySubjectTree({
        parentId: parentSubjectId,
        status: statusVal
      })
      const children = (res || []).map((item: SubjectItem) => ({
        ...item,
        hasChildren: !item.leaf
      }))
      const elTable = baseTableRef.value?.tableRef
      if (elTable?.store?.states?.lazyTreeNodeMap?.value) {
        elTable.store.states.lazyTreeNodeMap.value[parentSubjectId] = children
      }
    } catch (e) {
      console.error('局部刷新子节点失败', e)
    }
  }
  await fetchRootSubjects()
}

/**
 * 操作列下拉菜单事件分发
 */
function handleActionCommand(command: string, row: SubjectItem) {
  switch (command) {
    case 'edit':
      openEditDialog(row)
      break
    case 'addChild':
      openAddChildDialog(row)
      break
    case 'auxiliary':
      openAuxiliaryDrawer(row)
      break
    case 'disable':
      handleDisable(row)
      break
  }
}

// ==================== 科目弹窗表单 ====================

const dialogVisible = ref(false)
const dialogTitle = ref('新建顶级科目')
const submitting = ref(false)
const isEditMode = ref(false)
const currentParent = ref<SubjectItem | null>(null)
const formRef = ref<FormInstance>()

const subjectForm = reactive({
  subjectCode: '',
  subjectName: '',
  subjectLevel: 1,
  parentSubjectId: 0,
  subjectCategory: 1,
  nature: 1,
  debitCredit: 1,
  leaf: true,
  allowPost: true,
  allowOpenAccount: true,
  status: 1
})

const parentSubjectDisplay = computed(() => {
  if (!currentParent.value || !currentParent.value.subjectCode) {
    return '无（顶级科目）'
  }
  return `[${currentParent.value.subjectCode}] ${currentParent.value.subjectName || ''}`
})

const formRules: FormRules = {
  subjectCode: [
    { required: true, message: '请输入科目编码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error('科目编码不能为空'))
        if (!/^[0-9A-Za-z_]+$/.test(value)) {
          return callback(new Error('科目编码仅支持数字、字母和下划线'))
        }
        if (currentParent.value) {
          const parentPrefix = currentParent.value.subjectCode
          if (!value.startsWith(parentPrefix)) {
            return callback(
              new Error(`科目编码必须以父科目编码「${parentPrefix}」为前缀`)
            )
          }
          if (value === parentPrefix) {
            return callback(new Error('子科目编码不能与父科目完全相同'))
          }
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  subjectName: [
    { required: true, message: '请输入科目名称', trigger: 'blur' },
    { max: 64, message: '科目名称最大 64 个字符', trigger: 'blur' }
  ],
  subjectCategory: [{ required: true, message: '请选择账类', trigger: 'change' }],
  nature: [{ required: true, message: '请选择科目性质', trigger: 'change' }],
  debitCredit: [{ required: true, message: '请选择余额方向', trigger: 'change' }]
}

function handleLeafChange(val: boolean | string | number) {
  if (!val) {
    subjectForm.allowPost = false
    subjectForm.allowOpenAccount = false
  }
}

/**
 * 打开新建顶级科目弹窗
 */
function openCreateTopDialog() {
  isEditMode.value = false
  currentParent.value = null
  dialogTitle.value = '新建顶级科目'
  const defaultCategory =
    activeCategoryTab.value === 'all' ? 1 : Number(activeCategoryTab.value)

  Object.assign(subjectForm, {
    subjectCode: '',
    subjectName: '',
    subjectLevel: 1,
    parentSubjectId: 0,
    subjectCategory: defaultCategory,
    nature: 1,
    debitCredit: 1,
    leaf: true,
    allowPost: true,
    allowOpenAccount: true,
    status: 1
  })
  dialogVisible.value = true
}

/**
 * 打开添加子级科目弹窗
 */
function openAddChildDialog(parent: SubjectItem) {
  isEditMode.value = false
  currentParent.value = parent
  dialogTitle.value = `添加子科目 - [${parent.subjectCode}] ${parent.subjectName}`

  Object.assign(subjectForm, {
    subjectCode: parent.subjectCode,
    subjectName: '',
    subjectLevel: parent.subjectLevel + 1,
    parentSubjectId: parent.id,
    subjectCategory: parent.subjectCategory,
    nature: parent.nature,
    debitCredit: parent.debitCredit,
    leaf: true,
    allowPost: true,
    allowOpenAccount: true,
    status: 1
  })
  dialogVisible.value = true
}

/**
 * 打开编辑科目弹窗
 */
function openEditDialog(row: SubjectItem) {
  isEditMode.value = true
  if (row.parentSubjectId && row.parentSubjectId > 0) {
    currentParent.value = {
      id: row.parentSubjectId,
      subjectCode: row.parentSubjectCode || '',
      subjectName: row.parentSubjectName || ''
    } as SubjectItem
  } else {
    currentParent.value = null
  }
  dialogTitle.value = `编辑科目 - [${row.subjectCode}] ${row.subjectName}`

  Object.assign(subjectForm, {
    subjectCode: row.subjectCode,
    subjectName: row.subjectName,
    subjectLevel: row.subjectLevel,
    parentSubjectId: row.parentSubjectId,
    subjectCategory: row.subjectCategory,
    nature: row.nature,
    debitCredit: row.debitCredit,
    leaf: row.leaf,
    allowPost: row.allowPost,
    allowOpenAccount: row.allowOpenAccount,
    status: row.status
  })
  dialogVisible.value = true
}

/**
 * 提交科目保存
 */
async function handleSubmitSubject() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    if (isEditMode.value) {
      const updatePayload: SubjectUpdateRequest = {
        subjectName: subjectForm.subjectName,
        subjectCategory: subjectForm.subjectCategory,
        nature: subjectForm.nature,
        leaf: subjectForm.leaf,
        allowPost: subjectForm.allowPost,
        allowOpenAccount: subjectForm.allowOpenAccount,
        status: subjectForm.status
      }
      await updateSubject(subjectForm.subjectCode, updatePayload)
      toast.success('科目更新成功')
    } else {
      const createPayload: SubjectCreateRequest = {
        subjectCode: subjectForm.subjectCode,
        subjectName: subjectForm.subjectName,
        subjectLevel: subjectForm.subjectLevel,
        parentSubjectId: subjectForm.parentSubjectId,
        subjectCategory: subjectForm.subjectCategory,
        nature: subjectForm.nature,
        debitCredit: subjectForm.debitCredit,
        leaf: subjectForm.leaf,
        allowPost: subjectForm.allowPost,
        allowOpenAccount: subjectForm.allowOpenAccount,
        status: subjectForm.status
      }
      await createSubject(createPayload)
      toast.success('科目创建成功')
    }
    dialogVisible.value = false
    await refreshSubjectData(subjectForm.parentSubjectId)
  } catch (err: any) {
    console.error('保存科目失败', err)
  } finally {
    submitting.value = false
  }
}

/**
 * 停用科目
 */
async function handleDisable(row: SubjectItem) {
  const confirmed = await useConfirm(
    `确定要停用科目【${row.subjectCode} - ${row.subjectName}】吗？若该科目存在下级科目、被开户模板或记账规则引用，停用将被系统阻断。`,
    {
      title: '停用科目确认',
      type: 'warning',
      confirmButtonText: '确认停用'
    }
  )
  if (!confirmed) return

  try {
    await disableSubject(row.subjectCode)
    toast.success('科目已停用')
    await refreshSubjectData(row.parentSubjectId)
  } catch (err: any) {
    console.error('停用科目失败', err)
  }
}

// ==================== 辅助核算从表抽屉 ====================

const auxDrawerVisible = ref(false)
const selectedSubject = ref<SubjectItem | null>(null)
const auxList = ref<AuxiliaryItem[]>([])
const auxLoading = ref(false)
const auxAdding = ref(false)
const auxFormRef = ref<FormInstance>()

const auxTypeOptions = ref<{ code: string; label: string }[]>([])

async function loadAuxTypes() {
  try {
    let list = await getDictByType('auxiliary_type')
    if (!list || list.length === 0) {
      list = await getDictByType('AUXILIARY_TYPE')
    }
    if (list && list.length > 0) {
      auxTypeOptions.value = list.map((item) => ({
        code: item.dictCode,
        label: item.dictName
      }))
    } else {
      auxTypeOptions.value = []
    }
  } catch (err) {
    console.error('加载核算维度字典失败', err)
    auxTypeOptions.value = []
  }
}

const auxForm = reactive({
  auxiliaryType: '',
  required: false,
  defaultAuxCode: ''
})

const auxRules: FormRules = {
  auxiliaryType: [{ required: true, message: '请选择或输入辅助核算类别', trigger: 'blur' }]
}

function getAuxTypeLabel(typeCode: string) {
  const match = auxTypeOptions.value.find((item) => item.code === typeCode)
  return match ? `(${match.label})` : ''
}

async function openAuxiliaryDrawer(row: SubjectItem) {
  selectedSubject.value = row
  auxDrawerVisible.value = true
  auxForm.auxiliaryType = ''
  auxForm.required = false
  auxForm.defaultAuxCode = ''
  await Promise.all([loadAuxiliaryList(), loadAuxTypes()])
}

async function loadAuxiliaryList() {
  if (!selectedSubject.value) return
  auxLoading.value = true
  try {
    const res = await listSubjectAuxiliary(selectedSubject.value.subjectCode)
    auxList.value = res || []
  } catch (err: any) {
    console.error('加载辅助核算项失败', err)
  } finally {
    auxLoading.value = false
  }
}

async function handleAddAuxiliary() {
  if (!auxFormRef.value || !selectedSubject.value) return
  await auxFormRef.value.validate()

  auxAdding.value = true
  try {
    await createSubjectAuxiliary(selectedSubject.value.subjectCode, {
      auxiliaryType: auxForm.auxiliaryType.toUpperCase().trim(),
      required: auxForm.required,
      defaultAuxCode: auxForm.defaultAuxCode?.trim() || undefined
    })
    toast.success('辅助核算项添加成功')
    auxForm.auxiliaryType = ''
    auxForm.required = false
    auxForm.defaultAuxCode = ''
    await loadAuxiliaryList()
  } catch (err: any) {
    console.error('添加辅助核算项失败', err)
  } finally {
    auxAdding.value = false
  }
}

async function handleToggleAuxRequired(row: AuxiliaryItem, val: boolean) {
  if (!selectedSubject.value) return
  try {
    await updateSubjectAuxiliary(selectedSubject.value.subjectCode, row.auxiliaryType, {
      required: val,
      defaultAuxCode: row.defaultAuxCode
    })
    toast.success(`辅助核算项已更新为 ${val ? '必填' : '可选'}`)
  } catch (err: any) {
    // 恢复原状态
    row.required = !val
    console.error('更新辅助核算项失败', err)
  }
}

async function handleDeleteAuxiliary(row: AuxiliaryItem) {
  if (!selectedSubject.value) return
  if (row.required) {
    toast.warning('该辅助核算项为必填项，不允许删除')
    return
  }

  const confirmed = await useConfirm(`确定要删除维度【${row.auxiliaryType}】吗？`, {
    title: '删除辅助核算项',
    type: 'info'
  })
  if (!confirmed) return

  try {
    await deleteSubjectAuxiliary(selectedSubject.value.subjectCode, row.auxiliaryType)
    toast.success('辅助核算项删除成功')
    await loadAuxiliaryList()
  } catch (err: any) {
    console.error('删除辅助核算项失败', err)
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchRootSubjects()
  loadAuxTypes()
})
</script>

<style scoped>
.subject-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tab-card {
  padding: 8px 20px 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--fin-border-light);
}

.code-tag {
  font-family: var(--fin-font-family-mono);
  font-weight: 600;
  color: var(--fin-primary-dark);
  background-color: rgba(24, 144, 255, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
}

.form-tip {
  font-size: 12px;
  color: var(--fin-text-secondary);
  margin-top: 4px;
}

.aux-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.subject-info-alert {
  border-radius: 6px;
}

.aux-add-card {
  padding: 16px;
  background-color: var(--fin-bg-page);
  border-radius: 8px;
  border: 1px dashed var(--fin-border-color);
}

.aux-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fin-text-primary);
  margin-bottom: 12px;
}

.aux-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.aux-label-hint {
  font-size: 12px;
  color: var(--fin-text-secondary);
  margin-left: 4px;
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
