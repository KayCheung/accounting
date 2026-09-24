import { ElMessageBox } from 'element-plus'

export interface ConfirmOptions {
  title?: string
  confirmButtonText?: string
  cancelButtonText?: string
  type?: 'warning' | 'info' | 'success' | 'error'
  dangerouslyUseHTMLString?: boolean
  distinguishCancelAndClose?: boolean
}

/**
 * 二次确认弹窗 Hook
 *
 * @param message 提示内容
 * @param options 配置项
 * @returns Promise<boolean> 点击确定 resolve(true)，取消 resolve(false)
 */
export function useConfirm(message: string, options?: ConfirmOptions): Promise<boolean> {
  const {
    title = '操作提示',
    confirmButtonText = '确定',
    cancelButtonText = '取消',
    type = 'warning',
    dangerouslyUseHTMLString = false
  } = options || {}

  return ElMessageBox.confirm(message, title, {
    confirmButtonText,
    cancelButtonText,
    type,
    dangerouslyUseHTMLString,
    autofocus: false,
    closeOnClickModal: false
  })
    .then(() => true)
    .catch(() => false)
}

export default useConfirm
