# step-05-java-c · 科目域 + 字典域自定义 Mapper

## 任务目标

在 Step 3 生成的基础 Mapper 之上，补充科目域和字典域业务所需的自定义查询方法。

## 科目域自定义方法清单

| # | Mapper | 方法 | XML 位置 | 说明 | 索引 |
|---|--------|------|----------|------|------|
| 1 | AccountSubjectMapper | `selectTreeByParent(parentSubjectId)` | AccountSubjectMapper.xml | 按父科目ID查询子科目列表 | idx_parent_subject_id (ref) |
| 2 | AccountSubjectMapper | `selectLeafForPosting()` | AccountSubjectMapper.xml | 查询末级且允许记账的科目 | 多条件过滤 |
| 3 | AccountTemplateMapper | `selectByBusinessKey(businessCode, customerType, subjectCode)` | AccountTemplateMapper.xml | 按业务键查询开户模板 | uk_business_code (const) |

## 字典域自定义方法清单

| # | Mapper | 方法 | XML 位置 | 说明 | 索引 |
|---|--------|------|----------|------|------|
| 1 | DictionaryMapper | `selectByType(dictType)` | DictionaryMapper.xml | 按字典类型查询字典项列表 | idx_dict_type (ref) |
| 2 | DictionaryMapper | `selectByGroupKey(groupKey)` | DictionaryMapper.xml | 按分组键查询字典项 | idx_group_key (ref) |

## Repository 封装

- `SubjectRepository`: 封装科目查询 + 递归树构建 + 模板查询
  - `selectSubjectTree(rootSubjectId)`: 一次性加载所有科目，在内存中递归构建树（MySQL 5.7 不支持 CTE 递归）
  - 返回 `List<SubjectTreeNodeDTO>` 结构
- `DictionaryRepository`: 封装字典查询 + 批量按类型分组

## DTO

- `SubjectTreeNodeDTO`: 含 `subject`（AccountSubjectPO）和 `children`（递归列表）

## 单测覆盖

- `SubjectRepositoryTest`: 科目树查询层级正确（三级嵌套）；科目树空表返回空列表
- `DictionaryRepositoryTest`: 字典按类型分组查询结果正确
