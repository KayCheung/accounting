import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type RoleCode = 'ADMIN' | 'MAKER' | 'AUDITOR' | 'REVIEWER' | 'BOOKKEEPER' | 'OPERATOR'

export interface RoleInfo {
  code: RoleCode
  name: string
  operatorName: string
  tagType: 'danger' | 'primary' | 'success' | 'warning' | 'info'
  dutyDesc: string
  permissions: string[]
}

export const ROLE_CONFIGS: Record<RoleCode, RoleInfo> = {
  ADMIN: {
    code: 'ADMIN',
    name: '账务主管',
    operatorName: '张主管',
    tagType: 'danger',
    dutyDesc: '拥有全盘会计核算监管、日切状态把关、试算平衡核验及审批权限',
    permissions: [
      'dashboard:system:view',
      'dashboard:todo:maker',
      'dashboard:todo:audit',
      'dashboard:todo:review',
      'dashboard:todo:bookkeeper',
      'dashboard:exception:view',
      'dashboard:metrics:view',
      'dashboard:quick:view',
      'business:eod',
      'business:transfer',
      'business:manual-voucher',
      'business:journal',
      'business:voucher',
      'business:balance',
      'business:freeze',
      'business:buffer',
      'report:all'
    ]
  },
  MAKER: {
    code: 'MAKER',
    name: '会计经办',
    operatorName: '王经办',
    tagType: 'primary',
    dutyDesc: '负责日常业务流水录入、手工凭证制单填制及草稿/驳回单据维护',
    permissions: [
      'dashboard:todo:maker',
      'dashboard:quick:view',
      'business:manual-voucher',
      'business:journal',
      'business:balance'
    ]
  },
  AUDITOR: {
    code: 'AUDITOR',
    name: '审核员',
    operatorName: '李初审',
    tagType: 'warning',
    dutyDesc: '负责凭证初审把关，核对借贷要素、附件真实性及分录科目合规性',
    permissions: [
      'dashboard:todo:audit',
      'dashboard:quick:view',
      'business:manual-voucher',
      'business:voucher',
      'report:auxiliary'
    ]
  },
  REVIEWER: {
    code: 'REVIEWER',
    name: '复核员',
    operatorName: '赵复核',
    tagType: 'warning',
    dutyDesc: '负责凭证终审复核，执行内控双签确认，通过后流转至记账岗',
    permissions: [
      'dashboard:todo:review',
      'dashboard:quick:view',
      'business:manual-voucher',
      'business:voucher',
      'report:auxiliary'
    ]
  },
  BOOKKEEPER: {
    code: 'BOOKKEEPER',
    name: '记账员',
    operatorName: '钱记账',
    tagType: 'success',
    dutyDesc: '负责对已终审凭证执行确认记账，触发过账引擎完成法定分户入账',
    permissions: [
      'dashboard:todo:bookkeeper',
      'dashboard:quick:view',
      'business:manual-voucher',
      'business:voucher',
      'business:journal'
    ]
  },
  OPERATOR: {
    code: 'OPERATOR',
    name: '对账运维',
    operatorName: '孙运维',
    tagType: 'info',
    dutyDesc: '负责日间掉单核对自愈、过账异常治理、缓冲队列调度及风控冻结排查',
    permissions: [
      'dashboard:system:view',
      'dashboard:exception:view',
      'dashboard:metrics:view',
      'dashboard:quick:view',
      'business:journal',
      'business:voucher',
      'business:buffer',
      'business:freeze',
      'business:eod'
    ]
  }
}

export const useUserStore = defineStore('user', () => {
  // 当前角色，默认 ADMIN
  const initialRole = (localStorage.getItem('fin_user_role') as RoleCode) || 'ADMIN'
  const currentRole = ref<RoleCode>(ROLE_CONFIGS[initialRole] ? initialRole : 'ADMIN')

  // 当前用户与角色信息
  const roleInfo = computed(() => ROLE_CONFIGS[currentRole.value] || ROLE_CONFIGS.ADMIN)
  const currentUsername = computed(() => roleInfo.value.operatorName)
  const currentRoleName = computed(() => roleInfo.value.name)

  /**
   * 切换当前操作角色
   */
  function switchRole(role: RoleCode) {
    if (ROLE_CONFIGS[role]) {
      currentRole.value = role
      localStorage.setItem('fin_user_role', role)
      localStorage.setItem('operatorName', ROLE_CONFIGS[role].operatorName)
    }
  }

  /**
   * 校验是否拥有某个权限点
   */
  function hasPermission(permission: string): boolean {
    if (currentRole.value === 'ADMIN') return true
    return roleInfo.value.permissions.includes(permission)
  }

  /**
   * 校验是否是指定角色之一
   */
  function hasRole(...roles: RoleCode[]): boolean {
    return roles.includes(currentRole.value)
  }

  return {
    currentRole,
    roleInfo,
    currentUsername,
    currentRoleName,
    switchRole,
    hasPermission,
    hasRole
  }
})
