/**
 * Problem 格式响应解析工具
 * 
 * 用于解析后端返回的 RFC 7807 Problem 格式错误响应
 * 
 * @see https://tools.ietf.org/html/rfc7807
 */

/**
 * Problem 格式响应结构
 */
export interface ProblemResponse {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: number
  message?: string
  [key: string]: any
}

/**
 * 从错误响应中提取错误消息
 * 
 * 优先级：
 * 1. detail（Problem 格式的详细错误信息）
 * 2. message（兼容字段）
 * 3. title（Problem 格式的标题）
 * 4. 默认消息
 * 
 * @param errorResponse 错误响应数据
 * @param defaultMessage 默认消息
 * @returns 错误消息
 */
export function extractErrorMessage(
  errorResponse: any,
  defaultMessage: string = '操作失败'
): string {
  if (!errorResponse) {
    return defaultMessage
  }

  // 优先使用 detail（Problem 格式的标准字段）
  if (errorResponse.detail) {
    return errorResponse.detail
  }

  // 兼容 message 字段
  if (errorResponse.message) {
    return errorResponse.message
  }

  // 使用 title（Problem 格式的标题）
  if (errorResponse.title) {
    return errorResponse.title
  }

  return defaultMessage
}

/**
 * 从 axios 错误对象中提取错误消息
 * 
 * @param error axios 错误对象
 * @param defaultMessage 默认消息
 * @returns 错误消息
 */
export function extractErrorFromAxios(
  error: any,
  defaultMessage: string = '操作失败'
): string {
  // 尝试从响应数据中提取
  if (error?.response?.data) {
    const message = extractErrorMessage(error.response.data, defaultMessage)
    if (message !== defaultMessage) {
      return message
    }
  }

  // 使用 error.message（通常是 axios 的默认消息）
  if (error?.message) {
    // 如果是 axios 的默认消息（如 "Request failed with status code 409"），返回默认消息
    if (error.message.includes('Request failed with status code')) {
      return defaultMessage
    }
    return error.message
  }

  return defaultMessage
}

/**
 * 从 axios 错误对象中提取完整的错误信息（包含 code、detail 等）
 * 
 * @param error axios 错误对象
 * @returns 错误信息对象
 */
export function extractErrorInfo(error: any): {
  message: string
  code?: number
  status?: number
  detail?: string
  title?: string
  type?: string
} {
  const responseData = error?.response?.data || {}
  
  return {
    message: extractErrorMessage(responseData, '操作失败'),
    code: responseData.code,
    status: responseData.status || error?.response?.status,
    detail: responseData.detail,
    title: responseData.title,
    type: responseData.type,
  }
}

