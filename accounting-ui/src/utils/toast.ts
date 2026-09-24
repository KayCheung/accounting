import { ElMessage, ElNotification } from 'element-plus'

/**
 * 统一消息提示 / Toast 工具
 */
let lastErrorMessage = ''
let lastErrorTime = 0

export const toast = {
  /**
   * 成功提示
   */
  success(message: string, duration = 3000) {
    ElMessage.success({ message, duration, showClose: true })
  },

  /**
   * 错误提示（内置 1 秒防重复机制）
   */
  error(message: string, duration = 4000) {
    const now = Date.now()
    if (lastErrorMessage === message && now - lastErrorTime < 1000) {
      return
    }
    lastErrorMessage = message
    lastErrorTime = now
    ElMessage.error({ message, duration, showClose: true })
  },

  /**
   * 警告提示
   */
  warning(message: string, duration = 3000) {
    ElMessage.warning({ message, duration, showClose: true })
  },

  /**
   * 消息提示
   */
  info(message: string, duration = 3000) {
    ElMessage.info({ message, duration, showClose: true })
  },

  /**
   * 右侧通知
   */
  notify(options: { title?: string; message: string; type?: 'success' | 'warning' | 'info' | 'error' }) {
    ElNotification({
      title: options.title || '系统通知',
      message: options.message,
      type: options.type || 'info',
      duration: 4000
    })
  }
}

export default toast
