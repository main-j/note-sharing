/**
 * 解析后端返回的各种时间格式为 Date 对象
 * @param {string|number|Date|Array} dateTime
 * @returns {Date|null}
 */
export function parseDateTime(dateTime) {
  if (!dateTime) return null
  if (dateTime instanceof Date) {
    return Number.isNaN(dateTime.getTime()) ? null : dateTime
  }
  if (typeof dateTime === 'number') {
    const date = new Date(dateTime)
    return Number.isNaN(date.getTime()) ? null : date
  }
  if (Array.isArray(dateTime)) {
    const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = dateTime
    if (!year || !month || !day) return null
    const millis = typeof nano === 'number' && nano > 999 ? Math.floor(nano / 1000000) : 0
    const date = new Date(year, month - 1, day, hour, minute, second, millis)
    return Number.isNaN(date.getTime()) ? null : date
  }
  if (typeof dateTime === 'string') {
    const trimmed = dateTime.trim()
    if (!trimmed) return null
    const date = new Date(trimmed)
    return Number.isNaN(date.getTime()) ? null : date
  }
  return null
}

/**
 * 格式化时间显示
 * @param {string|Date|Array|number} dateTime - 时间字符串或Date对象
 * @returns {string} 格式化后的时间字符串
 */
export function formatTime(dateTime) {
  try {
    const date = parseDateTime(dateTime)
    if (!date) return ''

    const now = new Date()
    const diff = now - date
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (seconds < 60) {
      return '刚刚'
    } else if (minutes < 60) {
      return `${minutes}分钟前`
    } else if (hours < 24) {
      return `${hours}小时前`
    } else if (days < 7) {
      return `${days}天前`
    } else {
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const currentYear = now.getFullYear()

      if (year === currentYear) {
        return `${month}-${day}`
      } else {
        return `${year}-${month}-${day}`
      }
    }
  } catch (error) {
    console.error('时间格式化失败:', error)
    return ''
  }
}

/**
 * 格式化完整时间显示（用于详情页）
 * @param {string|Date|Array|number} dateTime - 时间字符串或Date对象
 * @returns {string} 格式化后的完整时间字符串
 */
export function formatFullTime(dateTime) {
  try {
    const date = parseDateTime(dateTime)
    if (!date) return ''

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')

    return `${year}-${month}-${day} ${hours}:${minutes}`
  } catch (error) {
    console.error('时间格式化失败:', error)
    return ''
  }
}
