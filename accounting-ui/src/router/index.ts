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
        component: () => import('@/views/placeholder.vue'),
        meta: { title: '余额与明细查询' }
      },
      {
        path: 'business/freeze',
        name: 'FreezeBusiness',
        component: () => import('@/views/placeholder.vue'),
        meta: { title: '资金冻结与扣款' }
      },
      {
        path: 'business/voucher',
        name: 'VoucherBusiness',
        component: () => import('@/views/placeholder.vue'),
        meta: { title: '记账凭证管理' }
      },
      {
        path: 'business/eod',
        name: 'EodBusiness',
        component: () => import('@/views/placeholder.vue'),
        meta: { title: '日切与试算平衡' }
      },
      {
        path: 'business/buffer-monitor',
        name: 'BufferMonitorBusiness',
        component: () => import('@/views/placeholder.vue'),
        meta: { title: '缓冲记账监控' }
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
