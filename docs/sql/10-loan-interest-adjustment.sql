USE `accounting`;

-- ============================================================
-- 贷款利息收入调整科目方案
-- 版本：v1.0  日期：2026-10-14
-- 说明：解决减免/提前结清跨周期导致利息收入出现借方余额、
--       进而污染期末结转的问题。
--
-- 执行方式：
--   mysql -h <host> -u <user> -p <db> < 10-loan-interest-adjustment.sql
--
-- 幂等保护：全部使用 INSERT IGNORE，重复执行安全。
-- ============================================================

-- ============================================================
-- SECTION 0：前置检查（人工确认后删除注释再执行）
-- ============================================================
-- 检查 6601/6602 是否已存在，若有数据需人工判断是否冲突：
--
-- SELECT subject_code, subject_name, debit_credit, status
--   FROM t_account_subject
--   WHERE subject_code IN ('6601','6602') AND is_delete = 0;
--
-- 检查现有期末结转规则通配符（如命中 6601/6602 则需评估影响）：
--
-- SELECT rule_code, rule_name, source_subject_code, target_subject_code, period_cycle
--   FROM t_period_end_transfer_rule
--   WHERE status = 1 AND is_delete = 0
--     AND (source_subject_code LIKE '6%' OR source_subject_code = '6*');
-- ============================================================


-- ============================================================
-- SECTION 1：新增会计科目
-- ============================================================

-- 科目1：利息减免损失（6601）
-- 损益类 / 借方余额 / 末级科目 / 允许记账
INSERT IGNORE INTO t_account_subject (
    subject_code,
    subject_name,
    subject_level,
    parent_subject_id,
    subject_category,
    nature,
    debit_credit,
    is_leaf,
    allow_post,
    allow_open_account,
    status,
    create_time,
    update_time,
    create_id,
    create_name,
    update_id,
    update_name,
    is_delete,
    tenant_id
) VALUES (
    '6601',
    '利息减免损失',
    1,          -- 科目级别：一级科目
    0,          -- 父科目ID：无
    6,          -- 账类：6-损益类
    1,          -- 科目性质：1-非特殊性科目
    1,          -- 借贷方向：1-借（费用/损失类）
    1,          -- 末级科目：1-是
    1,          -- 允许记账：1-是
    1,          -- 允许建明细账户：1-是
    1,          -- 状态：1-启用
    NOW(),
    NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0,          -- 未删除
    -1          -- 全局租户
);

-- 科目2：提前结清利息损失（6602）
-- 损益类 / 借方余额 / 末级科目 / 允许记账
INSERT IGNORE INTO t_account_subject (
    subject_code,
    subject_name,
    subject_level,
    parent_subject_id,
    subject_category,
    nature,
    debit_credit,
    is_leaf,
    allow_post,
    allow_open_account,
    status,
    create_time,
    update_time,
    create_id,
    create_name,
    update_id,
    update_name,
    is_delete,
    tenant_id
) VALUES (
    '6602',
    '提前结清利息损失',
    1,
    0,
    6,          -- 损益类
    1,
    1,          -- 借方余额
    1, 1, 1, 1,
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);


-- ============================================================
-- SECTION 2：新增入账规则
-- ============================================================

-- ----------------------------------------------------------
-- 规则A：贷款利息减免（business_code=LOAN, trading_code=INTEREST_WAIVE）
-- ----------------------------------------------------------
INSERT IGNORE INTO t_accounting_rule (
    rule_name,
    voucher_type,
    business_code,
    trading_code,
    pay_channel,
    is_open_account,
    freeze_duration,
    pre_rule_id,
    status,
    create_time,
    update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete,
    tenant_id
) VALUES (
    '贷款利息减免',
    'LOAN_WAIVE',           -- 凭证类型（需提前录入字典 VOUCHER_TYPE）
    'LOAN',                 -- 业务线
    'INTEREST_WAIVE',       -- 交易码（需提前录入字典 TRADING_CODE）
    'INNER',                -- 支付渠道：内部
    0,                      -- 不自动开户
    0,                      -- 无冻结
    0,                      -- 无前置规则
    2,                      -- 状态：2-启用
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);

-- 规则A 明细行1：借 利息减免损失(6601)
INSERT IGNORE INTO t_accounting_rule_detail (
    rule_id,
    row_num,
    funds_type,
    subject_code,
    account_scope,
    debit_credit,
    currency,
    is_unilateral,
    extend_script,
    summary,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) SELECT
    r.id,           -- 引用刚插入的规则 ID
    1,              -- 行号
    'INTEREST',     -- 款项类型：利息
    '6601',         -- 利息减免损失科目
    1,              -- 账户作用域：1-内部分户
    1,              -- 借方
    'CNY',
    1,              -- 实时更新账户余额
    '',             -- 无 SpEL 扩展
    '利息减免损失',
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
FROM t_accounting_rule r
WHERE r.business_code = 'LOAN'
  AND r.trading_code  = 'INTEREST_WAIVE'
  AND r.pay_channel   = 'INNER'
  AND r.is_delete     = 0
LIMIT 1;

-- 规则A 明细行2：贷 应收利息(1123)
INSERT IGNORE INTO t_accounting_rule_detail (
    rule_id,
    row_num,
    funds_type,
    subject_code,
    account_scope,
    debit_credit,
    currency,
    is_unilateral,
    extend_script,
    summary,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) SELECT
    r.id,
    2,
    'INTEREST',
    '1123',         -- 应收利息科目（已存在）
    2,              -- 账户作用域：2-外部分户（客户账户）
    2,              -- 贷方
    'CNY',
    1,
    '',
    '核销减免应收利息',
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
FROM t_accounting_rule r
WHERE r.business_code = 'LOAN'
  AND r.trading_code  = 'INTEREST_WAIVE'
  AND r.pay_channel   = 'INNER'
  AND r.is_delete     = 0
LIMIT 1;


-- ----------------------------------------------------------
-- 规则B：贷款提前结清利息退回（business_code=LOAN, trading_code=INTEREST_PREPAY）
-- ----------------------------------------------------------
INSERT IGNORE INTO t_accounting_rule (
    rule_name,
    voucher_type,
    business_code,
    trading_code,
    pay_channel,
    is_open_account,
    freeze_duration,
    pre_rule_id,
    status,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) VALUES (
    '贷款提前结清利息退回',
    'LOAN_PREPAY',
    'LOAN',
    'INTEREST_PREPAY',
    'INNER',
    0, 0, 0, 2,
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);

-- 规则B 明细行1：借 提前结清利息损失(6602)
INSERT IGNORE INTO t_accounting_rule_detail (
    rule_id,
    row_num,
    funds_type,
    subject_code,
    account_scope,
    debit_credit,
    currency,
    is_unilateral,
    extend_script,
    summary,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) SELECT
    r.id,
    1,
    'INTEREST',
    '6602',
    1,
    1,              -- 借方
    'CNY',
    1, '',
    '提前结清利息损失',
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
FROM t_accounting_rule r
WHERE r.business_code = 'LOAN'
  AND r.trading_code  = 'INTEREST_PREPAY'
  AND r.pay_channel   = 'INNER'
  AND r.is_delete     = 0
LIMIT 1;

-- 规则B 明细行2：贷 应收利息(1123)
INSERT IGNORE INTO t_accounting_rule_detail (
    rule_id,
    row_num,
    funds_type,
    subject_code,
    account_scope,
    debit_credit,
    currency,
    is_unilateral,
    extend_script,
    summary,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) SELECT
    r.id,
    2,
    'INTEREST',
    '1123',
    2,
    2,              -- 贷方
    'CNY',
    1, '',
    '核销提前结清应收利息',
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
FROM t_accounting_rule r
WHERE r.business_code = 'LOAN'
  AND r.trading_code  = 'INTEREST_PREPAY'
  AND r.pay_channel   = 'INNER'
  AND r.is_delete     = 0
LIMIT 1;


-- ============================================================
-- SECTION 3：新增期末结转规则
-- ============================================================

-- 结转规则1：利息收入(6021) → 本年利润(3131)
-- 注意：若此规则已存在，INSERT IGNORE 会跳过，无需手动删除。
-- 若已存在的规则使用通配符 6021* 或 6*，
-- 需人工确认是否改为精确匹配 '6021' 并单独处理 6601/6602。
INSERT IGNORE INTO t_period_end_transfer_rule (
    rule_code,
    rule_name,
    transfer_type,
    source_subject_code,
    target_subject_code,
    transfer_direction,
    summary_template,
    execute_order,
    status,
    auto_transfer,
    period_cycle,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) VALUES (
    'INTEREST_INCOME_TRANSFER',
    '利息收入期末结转',
    2,              -- 结转类型：2-成本结转
    '6021',         -- 源科目：精确匹配，非通配符
    '3131',         -- 目标：本年利润
    2,              -- transfer_direction：2-贷方余额→借方
    '{year}年{month}月利息收入结转',
    10,             -- execute_order：最先执行
    1,              -- 状态：1-启用
    1,              -- 自动结转：1-是
    2,              -- period_cycle：2-月末
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);

-- 结转规则2：利息减免损失(6601) → 本年利润(3131)
INSERT IGNORE INTO t_period_end_transfer_rule (
    rule_code,
    rule_name,
    transfer_type,
    source_subject_code,
    target_subject_code,
    transfer_direction,
    summary_template,
    execute_order,
    status,
    auto_transfer,
    period_cycle,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) VALUES (
    'INTEREST_WAIVE_TRANSFER',
    '利息减免损失期末结转',
    3,              -- 结转类型：3-自定义结转
    '6601',
    '3131',
    1,              -- transfer_direction：1-借方余额→贷方
    '{year}年{month}月利息减免损失结转',
    20,             -- execute_order：利息收入结转后
    1, 1,
    2,              -- 月末
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);

-- 结转规则3：提前结清利息损失(6602) → 本年利润(3131)
INSERT IGNORE INTO t_period_end_transfer_rule (
    rule_code,
    rule_name,
    transfer_type,
    source_subject_code,
    target_subject_code,
    transfer_direction,
    summary_template,
    execute_order,
    status,
    auto_transfer,
    period_cycle,
    create_time, update_time,
    create_id, create_name,
    update_id, update_name,
    is_delete, tenant_id
) VALUES (
    'INTEREST_PREPAY_TRANSFER',
    '提前结清利息损失期末结转',
    3,
    '6602',
    '3131',
    1,              -- 借方余额→贷方
    '{year}年{month}月提前结清利息损失结转',
    30,
    1, 1,
    2,
    NOW(), NOW(),
    'SYSTEM', '系统初始化',
    'SYSTEM', '系统初始化',
    0, -1
);


-- ============================================================
-- SECTION 4：执行结果验证查询（执行完后运行确认）
-- ============================================================

-- 验证1：科目是否创建成功
SELECT subject_code, subject_name, subject_category, debit_credit, status
  FROM t_account_subject
  WHERE subject_code IN ('6601', '6602') AND is_delete = 0;

-- 验证2：入账规则是否创建成功
SELECT r.id, r.rule_name, r.business_code, r.trading_code, r.status,
       d.row_num, d.subject_code, d.debit_credit, d.summary
  FROM t_accounting_rule r
  JOIN t_accounting_rule_detail d ON r.id = d.rule_id AND d.is_delete = 0
  WHERE r.trading_code IN ('INTEREST_WAIVE', 'INTEREST_PREPAY')
    AND r.is_delete = 0
  ORDER BY r.trading_code, d.row_num;

-- 验证3：期末结转规则是否创建成功
SELECT rule_code, rule_name, source_subject_code, target_subject_code,
       transfer_direction, execute_order, period_cycle, status, auto_transfer
  FROM t_period_end_transfer_rule
  WHERE rule_code IN ('INTEREST_INCOME_TRANSFER','INTEREST_WAIVE_TRANSFER','INTEREST_PREPAY_TRANSFER')
    AND is_delete = 0
  ORDER BY execute_order;
