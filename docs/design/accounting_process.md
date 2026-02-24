# 入账流程

```
flowchart TB
    classDef startend fill:#f9f,stroke:#333,stroke-width:1px
    classDef process fill:#87CEFA,stroke:#333,stroke-width:1px
    classDef decision fill:#FFB6C1,stroke:#333,stroke-width:1px
    classDef config fill:#E6E6FA,stroke:#333,stroke-width:1px
    START[记账/调账]:::process
    LOG_JOURNAL[登记记账流水]:::process
    BOOK_RULE[记账规则]:::process
    %%DECISION_FREEZE{冻结？}:::decision
    UNILATERAL_BOOK{单边记账？}:::decision
    GEN_VOUCHER[生成记账凭证]:::process
    TX_MANAGE[事务管理]:::process
    SINGLE_ENTRY[客户子账户单边记账]:::process
    UPDATE_SUB_BAL[客户冻结/可用<br/>子账户余额更新]:::process
    SUB_BAL_DETAIL[客户子账户余额明细]:::process
    CIF_LEDGER[客户分户明细账簿]:::startend
    CIF_ACCT[客户分户账户]:::startend
    BUFFER_RULE[缓冲入账规则]:::process
    DECISION_REAL_TIME{实时入账？}:::decision
    BUFFER_ENTRY[缓冲入账明细]:::config
    JOURNAL_ENTRY[分录流水]:::process
    INTERNAL_LEDGER[内部分户明细账簿]:::startend
    INTERNAL_ACCT[内部账户]:::startend
    VOUCHER_CONFIG[记账凭证规则配置]:::config
    BUFFER_CONFIG[缓冲入账规则配置]:::config

    START --> LOG_JOURNAL
    LOG_JOURNAL --> BOOK_RULE
    BOOK_RULE --> GEN_VOUCHER
    %%BOOK_RULE --> DECISION_FREEZE
    %%DECISION_FREEZE -->|否| GEN_VOUCHER
    %%DECISION_FREEZE -->|是| SINGLE_ENTRY
    GEN_VOUCHER --> TX_MANAGE
    TX_MANAGE --> UNILATERAL_BOOK
    UNILATERAL_BOOK --是-->SINGLE_ENTRY
    UNILATERAL_BOOK --否-->BUFFER_RULE
    SINGLE_ENTRY --> UPDATE_SUB_BAL
    UPDATE_SUB_BAL --> SUB_BAL_DETAIL
    CIF_LEDGER --> CIF_ACCT
    BUFFER_RULE --> DECISION_REAL_TIME
    DECISION_REAL_TIME -->|否| BUFFER_ENTRY
    DECISION_REAL_TIME -->|是| JOURNAL_ENTRY
    JOURNAL_ENTRY --客户分户分录明细--> CIF_LEDGER
    JOURNAL_ENTRY --内部分户分录明细--> INTERNAL_LEDGER
    INTERNAL_LEDGER --> INTERNAL_ACCT
    CIF_ACCT <-->|交易对手余额更新| INTERNAL_ACCT
    BOOK_RULE -....->|后台配置| VOUCHER_CONFIG
    BUFFER_RULE -....->|后台配置| BUFFER_CONFIG
```