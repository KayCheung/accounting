import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '系统概览', icon: 'Odometer' }
      },
      {
        path: 'component-demo',
        name: 'ComponentDemo',
        component: () => import('@/views/component-demo/index.vue'),
        meta: { title: '公共组件规范展台', icon: 'Grid' }
      },
      // 配置管理模块路由 (Step 22)
      {
        path: 'config/dict',
        name: 'DictConfig',
        component: () => import('@/views/config/dict/index.vue'),
        meta: { title: '数据字典管理' }
      },
      {
        path: 'config/subject',
        name: 'SubjectConfig',
        component: () => import('@/views/config/subject/index.vue'),
        meta: { title: '科目树管理' }
      },
      {
        path: 'config/template',
        name: 'TemplateConfig',
        component: () => import('@/views/config/template/index.vue'),
        meta: { title: '开户模板管理' }
      },
      {
        path: 'config/rule',
        name: 'RuleConfig',
        component: () => import('@/views/config/rule/index.vue'),
        meta: { title: '记账规则配置' }
      },
      {
        path: 'config/buffer-rule',
        name: 'BufferRuleConfig',
        component: () => import('@/views/config/buffer-rule/index.vue'),
        meta: { title: '缓冲规则配置' }
      },
      {
        path: 'config/transfer-rule',
        name: 'TransferRuleConfig',
        component: () => import('@/views/config/transfer-rule/index.vue'),
        meta: { title: '期末结转规则' }
      },
      // 业务功能模块路由 (Step 23)
      {
        path: 'business/account',
        name: 'AccountBusiness',
        component: () => import('@/views/business/account/index.vue'),
        meta: { title: '账户开户与状态' }
      },
      {
        path: 'business/balance',
        name: 'BalanceBusiness',
        component: () => import('@/views/business/balance/index.vue'),
        meta: { title: '余额与明细查询' }
      },
      {
        path: 'business/freeze',
        name: 'FreezeBusiness',
        component: () => import('@/views/business/freeze/index.vue'),
        meta: { title: '资金冻结与扣款' }
      },
      {
        path: 'business/journal',
        name: 'JournalBusiness',
        component: () => import('@/views/business/journal/index.vue'),
        meta: { title: '记账流水与事务监控' }
      },
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
        meta: { title: '手工凭证录入与审核' }
      },
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
        meta: { title: '期末结转管理' }
      },
      {
        path: 'business/buffer-monitor',
        name: 'BufferMonitorBusiness',
        component: () => import('@/views/business/buffer-monitor/index.vue'),
        meta: { title: '缓冲记账监控' }
      },
      // 报表中心模块路由
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
      {
        path: 'report/general-ledger',
        name: 'GeneralLedgerReport',
        component: () => import('@/views/report/general-ledger/index.vue'),
        meta: { title: '科目总账' }
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
