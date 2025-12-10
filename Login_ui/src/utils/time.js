/**
 * 格式化时间显示
 * @param {string|Date} dateTime - 时间字符串或Date对象
 * @returns {string} 格式化后的时间字符串
 */
export function formatTime(dateTime) {
  if (!dateTime) return ''
  
  try {
    const date = typeof dateTime === 'string' ? new Date(dateTime) : dateTime
    if (isNaN(date.getTime())) return ''
    
    const now = new Date()
    const diff = now - date
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)
    
    // 相对时间显示
    if (seconds < 60) {
      return '刚刚'
    } else if (minutes < 60) {
      return `${minutes}分钟前`
    } else if (hours < 24) {
      return `${hours}小时前`
    } else if (days < 7) {
      return `${days}天前`
    } else {
      // 超过7天显示具体日期
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
 * @param {string|Date} dateTime - 时间字符串或Date对象
 * @returns {string} 格式化后的完整时间字符串
 */
export function formatFullTime(dateTime) {
  if (!dateTime) return ''
  
  try {
    const date = typeof dateTime === 'string' ? new Date(dateTime) : dateTime
    if (isNaN(date.getTime())) return ''
    
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

