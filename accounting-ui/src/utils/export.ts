import { service } from '@/utils/request'
import { ElMessage, ElLoading } from 'element-plus'

/**
 * 通用报表 Excel 二进制流下载工具
 * <p>
 * 统一处理：请求参数序列化、认证头透传、加载指示、Content-Disposition 文件名提取、
 * 业务错误 JSON 拦截兜底及虚拟 <a> 标签触发下载与内存释放。
 * 全系统报表导出均复用此方法，杜绝重复代码。
 * </p>
 *
 * @param url              导出后端接口相对地址（如：/report/export/general-ledger）
 * @param params           查询参数对象
 * @param defaultFileName  默认文件名（当后端未返回 Content-Disposition 时兜底使用）
 */
export async function downloadReportExcel(
  url: string,
  params: Record<string, any> = {},
  defaultFileName: string
): Promise<void> {
  const loading = ElLoading.service({
    lock: true,
    text: `正在导出【${defaultFileName.replace('.xlsx', '')}】文件，请稍候...`,
    background: 'rgba(0, 0, 0, 0.4)'
  })

  try {
    const response: any = await service.get(url, {
      params,
      responseType: 'blob',
      // @ts-ignore
      rawResponse: true
    })

    const data = response.data

    // 1. 业务异常兜底处理（如果服务端报错并返回了 JSON 格式的错误信息）
    if (data.type && data.type.includes('application/json')) {
      const text = await data.text()
      try {
        const errObj = JSON.parse(text)
        ElMessage.error(errObj.message || '导出报表发生服务端异常')
      } catch {
        ElMessage.error('导出失败，服务器返回了非预期的数据格式')
      }
      return
    }

    // 2. 解析文件名（优先从 Content-Disposition 头提取）
    let fileName = defaultFileName
    const disposition = response.headers?.['content-disposition'] || response.headers?.['Content-Disposition']
    if (disposition) {
      // 匹配 filename*=UTF-8''... (RFC 5987 / RFC 6266)
      const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
      if (utf8Match && utf8Match[1]) {
        try {
          fileName = decodeURIComponent(utf8Match[1])
        } catch {
          // ignore decode error
        }
      } else {
        // 匹配普通 filename="..." 或 filename=...
        const normalMatch = disposition.match(/filename="?([^";]+)"?/i)
        if (normalMatch && normalMatch[1]) {
          try {
            fileName = decodeURIComponent(normalMatch[1])
          } catch {
            fileName = normalMatch[1]
          }
        }
      }
    }

    if (!fileName.endsWith('.xlsx')) {
      fileName += '.xlsx'
    }

    // 3. 构建 Blob 对象并通过 <a> 标签触发浏览器原生下载
    const blob = new Blob([data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()

    // 4. 清理 DOM 和内存
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success(`【${fileName}】导出成功`)
  } catch (error: any) {
    console.error('导出 Excel 失败:', error)
    ElMessage.error(error.message || '导出文件失败，请检查网络或后端服务')
  } finally {
    loading.close()
  }
}
