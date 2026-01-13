import request from './request'

// 管理员登录
export const adminLogin = async (email, password) => {
  const res = await request.post('/auth/admin/login', {
    email,
    password
  })
  return res.data
}

// ========== 在线用户管理 ==========
// 获取当前在线人数
export const getOnlineCount = async () => {
  const res = await request.get('/admin/online-count')
  return res.data
}

// 获取所有在线用户
export const getOnlineUsers = async () => {
  const res = await request.get('/admin/online-users')
  return res.data
}

// ========== 笔记管理 ==========
// 统计笔记总数
export const getNoteCount = async () => {
  const res = await request.get('/admin/notes/count')
  return res.data
}

// 获取所有笔记列表
export const getNoteList = async () => {
  const res = await request.get('/admin/notes/list')
  return res.data
}

// 搜索笔记（管理员端，使用Elasticsearch搜索）
export const searchNotes = async (keyword) => {
  // 注意：后端API要求userId不能为null，但管理员搜索不需要记录用户行为
  // 这里传入0作为占位符，后端会判断如果userId为0或无效则不记录搜索行为
  const res = await request.post('/search/notes', {
    keyword: keyword,
    userId: 0 // 管理员搜索使用0作为占位符
  })
  return res.data
}

// ========== 评论管理 ==========
// 统计评论总数
export const getRemarkCount = async () => {
  const res = await request.get('/admin/remarks/count')
  return res.data
}

// 获取所有评论列表
export const getRemarkList = async () => {
  const res = await request.get('/admin/remarks/list')
  return res.data
}

// ========== 敏感词检查 ==========
// 检查纯文本敏感词
export const checkSensitiveText = async (text) => {
  const res = await request.post('/admin/sensitive/check/text', { text })
  return res.data
}

// 检查笔记敏感词（快速模式）
export const checkNoteSensitive = async (noteId) => {
  const res = await request.get(`/admin/sensitive/check/note/${noteId}`)
  return res.data
}

// 检查笔记敏感词（全文模式）
export const checkNoteSensitiveFull = async (noteId) => {
  const res = await request.get(`/admin/sensitive/check/note/${noteId}/full`)
  return res.data
}

// 批量检查笔记敏感词
export const batchCheckSensitive = async (noteIds) => {
  const res = await request.post('/admin/sensitive/check/batch', { noteIds })
  return res.data
}

// ========== 敏感词过滤 ==========
// 快速过滤检查文本
export const fastFilterText = async (text) => {
  const res = await request.post('/admin/sensitive/fast-filter', { text })
  return res.data
}

// 重新加载敏感词库
export const reloadSensitiveWords = async () => {
  const res = await request.post('/admin/sensitive/reload')
  return res.data
}

// 深度检查文本
export const deepCheckText = async (text) => {
  const res = await request.post('/admin/sensitive/deep-check', { text })
  return res.data
}

// 重新加载深度检查词库
export const reloadDeepCheckWords = async () => {
  const res = await request.post('/admin/sensitive/deep-reload')
  return res.data
}

// ========== 内容审查管理 ==========
// 获取待处理的审查记录列表
export const getPendingModerations = async () => {
  const res = await request.get('/admin/moderation/pending')
  return res.data
}

// 获取审查记录详情
export const getModerationDetail = async (id) => {
  const res = await request.get(`/admin/moderation/${id}`)
  return res.data
}

// 获取笔记的审查历史
export const getNoteModerationHistory = async (noteId) => {
  const res = await request.get(`/admin/moderation/note/${noteId}`)
  return res.data
}

// 处理审查记录
export const handleModeration = async (id, adminNote) => {
  const res = await request.post(`/admin/moderation/${id}/handle`, { adminNote })
  return res.data
}
