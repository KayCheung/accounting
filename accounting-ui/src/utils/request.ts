import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { toast } from './toast'
import type { ApiResponse } from '@/api/types'

/**
 * Axios 统一实例封装
 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/accounting',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 注入租户与跟踪标识
    config.headers['X-Tenant-Id'] = localStorage.getItem('tenantId') || '10001'
    config.headers['X-Trace-No'] = 'UI_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7)

    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 若返回的是二进制流（导出文件等），直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data as any
    }

    // 后端规范：ResultCode.SUCCESS 为 "0"（兼顾兼容 "0", 0, 200, "200" 与 success === true）
    const isSuccess =
      res.success === true ||
      res.code === '0' ||
      res.code === 0 ||
      res.code === 200 ||
      res.code === '200'

    if (isSuccess) {
      return res.data
    }

    // 业务错误统一提示
    const errorMsg = res.message || '操作失败，请稍后重试'
    toast.error(errorMsg)
    return Promise.reject(new Error(errorMsg))
  },
  (error) => {
    let message = '网络请求异常，请检查服务或网络状态'
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 400:
          message = error.response.data?.message || '请求参数有误'
          break
        case 401:
          message = '登录状态已过期，请重新登录'
          break
        case 403:
          message = '无权限访问该资源'
          break
        case 404:
          message = '请求接口未找到 (404)'
          break
        case 500:
          message = error.response.data?.message || '服务端内部错误 (500)'
          break
        case 502:
        case 504:
          message = '网关超时或后端服务未启动'
          break
        default:
          message = error.response.data?.message || `服务错误 (${status})`
      }
    } else if (error.message.includes('timeout')) {
      message = '请求超时，请检查后端响应速度'
    }

    toast.error(message)
    return Promise.reject(error)
  }
)

/**
 * 封装通用 HTTP 动词方法
 */
const request = {
  get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params, ...config }) as unknown as Promise<T>
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  delete<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, { params, ...config }) as unknown as Promise<T>
  }
}

export default request
