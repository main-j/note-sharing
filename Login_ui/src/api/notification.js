import service from './request'

/**
 * 获取当前用户的通知列表（最多 50 条，按时间倒序）
 * [后端] GET /api/v1/notifications/list?userId={userId}
 */
export const fetchNotifications = (userId) => {
  const id = Number(userId)
  if (!id || Number.isNaN(id)) {
    return Promise.reject(new Error('无效的用户ID'))
  }
  return service
    .get('/notifications/list', {
      params: { userId: id }
    })
    .then(res => res.data.data || [])
}

/**
 * 获取当前用户未读通知总数
 * [后端] GET /api/v1/notifications/unread/total?userId={userId}
 */
export const fetchNotificationUnreadTotal = (userId) => {
  const id = Number(userId)
  if (!id || Number.isNaN(id)) {
    return Promise.reject(new Error('无效的用户ID'))
  }
  return service
    .get('/notifications/unread/total', {
      params: { userId: id }
    })
    .then(res => res.data.data ?? 0)
}

/**
 * 标记单条通知为已读
 * [后端] POST /api/v1/notifications/read?notificationId={id}
 */
export const markNotificationAsRead = (notificationId) => {
  if (!notificationId) {
    return Promise.reject(new Error('通知ID不能为空'))
  }
  return service
    .post('/notifications/read', null, {
      params: { notificationId }
    })
    .then(res => res.data)
}

/**
 * 将当前用户所有通知标记为已读
 * [后端] POST /api/v1/notifications/read/all?userId={userId}
 */
export const markAllNotificationsAsRead = (userId) => {
  const id = Number(userId)
  if (!id || Number.isNaN(id)) {
    return Promise.reject(new Error('无效的用户ID'))
  }
  return service
    .post('/notifications/read/all', null, {
      params: { userId: id }
    })
    .then(res => res.data)
}

