import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      // 1. 系统概览与工作台
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '系统概览', icon: 'Odometer' }
      },

      // 2. 账户资产管理
      {
        path: 'business/account',
        name: 'AccountBusiness',
        component: () => import('@/views/business/account/index.vue'),
        meta: { title: '账户档案管理' }
      },
      {
        path: 'business/balance',
        name: 'BalanceBusiness',
        component: () => import('@/views/business/balance/index.vue'),
        meta: { title: '账户余额明细' }
      },
      {
        path: 'business/freeze',
        name: 'FreezeBusiness',
        component: () => import('@/views/business/freeze/index.vue'),
        meta: { title: '资金冻结与扣款' }
      },

      // 3. 凭证账务作业
      {
        path: 'business/voucher',
        name: 'VoucherBusiness',
        component: () => import('@/views/business/voucher/index.vue'),
        meta: { title: '记账凭证管理' }
      },
      {
        path: 'business/manual-voucher',
        name: 'ManualVoucherBusiness',
        component: () => import('@/views/business/manual-voucher/index.vue'),
        meta: { title: '手工凭证作业' }
      },

      // 4. 期末与日终核算
      {
        path: 'business/eod',
        name: 'EodBusiness',
        component: () => import('@/views/business/eod/index.vue'),
        meta: { title: '日切与试算平衡' }
      },
      {
        path: 'business/transfer',
        name: 'TransferBusiness',
        component: () => import('@/views/business/transfer/index.vue'),
        meta: { title: '期末损益结转' }
      },

      // 5. 财务账簿与报表
      {
        path: 'report/general-ledger',
        name: 'GeneralLedgerReport',
        component: () => import('@/views/report/general-ledger/index.vue'),
        meta: { title: '科目总分类账' }
      },
      {
        path: 'report/subsidiary-ledger',
        name: 'SubsidiaryLedgerReport',
        component: () => import('@/views/report/subsidiary-ledger/index.vue'),
        meta: { title: '科目明细账' }
      },
      {
        path: 'report/auxiliary-ledger',
        name: 'AuxiliaryLedgerReport',
        component: () => import('@/views/report/auxiliary-ledger/index.vue'),
        meta: { title: '辅助核算账簿' }
      },
      {
        path: 'report/balance-sheet',
        name: 'BalanceSheetReport',
        component: () => import('@/views/report/balance-sheet/index.vue'),
        meta: { title: '资产负债表' }
      },
      {
        path: 'report/income-statement',
        name: 'IncomeStatementReport',
        component: () => import('@/views/report/income-statement/index.vue'),
        meta: { title: '利润表' }
      },

      // 6. 核心规则配置
      {
        path: 'config/subject',
        name: 'SubjectConfig',
        component: () => import('@/views/config/subject/index.vue'),
        meta: { title: '会计科目体系' }
      },
      {
        path: 'config/template',
        name: 'TemplateConfig',
        component: () => import('@/views/config/template/index.vue'),
        meta: { title: '账户配置模板' }
      },
      {
        path: 'config/rule',
        name: 'RuleConfig',
        component: () => import('@/views/config/rule/index.vue'),
        meta: { title: '业务记账规则' }
      },
      {
        path: 'config/buffer-rule',
        name: 'BufferRuleConfig',
        component: () => import('@/views/config/buffer-rule/index.vue'),
        meta: { title: '缓冲入账规则' }
      },
      {
        path: 'config/transfer-rule',
        name: 'TransferRuleConfig',
        component: () => import('@/views/config/transfer-rule/index.vue'),
        meta: { title: '期末结转规则' }
      },
      {
        path: 'config/dict',
        name: 'DictConfig',
        component: () => import('@/views/config/dict/index.vue'),
        meta: { title: '基础数据字典' }
      },
      {
        path: 'config/report-mapping',
        name: 'ReportMappingConfig',
        component: () => import('@/views/config/report-mapping/index.vue'),
        meta: { title: '财务报表配置' }
      },

      // 7. 事务与运行监控
      {
        path: 'business/journal',
        name: 'JournalBusiness',
        component: () => import('@/views/business/journal/index.vue'),
        meta: { title: '记账流水与事务' }
      },
      {
        path: 'business/buffer-monitor',
        name: 'BufferMonitorBusiness',
        component: () => import('@/views/business/buffer-monitor/index.vue'),
        meta: { title: '缓冲记账监控' }
      },

      // 8. 开发者工具与展台（仅开发调试，不在主导航展示）
      {
        path: 'component-demo',
        name: 'ComponentDemo',
        component: () => import('@/views/component-demo/index.vue'),
        meta: { title: '公共组件规范展台', icon: 'Grid', hidden: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || '智能账务核心系统'
  document.title = `${title} - FIN-Core`
})

export default router
