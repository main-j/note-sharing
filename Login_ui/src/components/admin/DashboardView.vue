<template>
  <div class="dashboard-view">
    <div class="dashboard-layout">
      <!-- 左侧 3/4 区域 -->
      <div class="left-section">
        <!-- 上面：系统状态概览 -->
        <div class="status-overview-card">
          <h3 class="module-title">系统状态概览</h3>
          <div class="status-cards-row">
            <div class="status-card online-users-card">
              <div class="status-icon">
                <img src="/assets/icons/icon-users.svg" alt="在线用户" />
              </div>
              <div class="status-content">
                <div class="status-value">{{ onlineCount }}</div>
                <div class="status-label">当前在线用户</div>
                <div class="status-trend" v-if="newUsersCount > 0">
                  <span class="trend-up">+{{ newUsersCount }} 新用户</span>
                </div>
              </div>
              <button class="status-action-btn" @click="goToOnlineUsers">查看详情</button>
              <div class="mountain-decoration-large">
                <svg width="100%" height="60" viewBox="0 0 200 60" preserveAspectRatio="none">
                  <path
                    d="M0,60 L20,45 L40,35 L60,25 L80,20 L100,18 L120,15 L140,12 L160,10 L180,8 L200,6 L200,60 Z"
                    fill="url(#mountainGradientStatus1)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,60 L30,42 L60,30 L90,22 L120,18 L150,14 L180,10 L200,8 L200,60 Z"
                    fill="url(#mountainGradientStatus2)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradientStatus1" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradientStatus2" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>

            <div class="status-card moderation-card">
              <div class="status-icon warning">
                <img src="/assets/icons/icon-warning.svg" alt="待审核" />
              </div>
              <div class="status-content">
                <div class="status-value">{{ pendingModerationCount }}</div>
                <div class="status-label">待审核内容</div>
                <div class="status-trend" v-if="pendingModerationCount === 0">
                  <span class="trend-success">✓ 全部通过</span>
                </div>
              </div>
              <button class="status-action-btn" @click="goToModeration">前往审核</button>
              <div class="mountain-decoration-large">
                <svg width="100%" height="60" viewBox="0 0 200 60" preserveAspectRatio="none">
                  <path
                    d="M0,60 L20,45 L40,35 L60,25 L80,20 L100,18 L120,15 L140,12 L160,10 L180,8 L200,6 L200,60 Z"
                    fill="url(#mountainGradientStatus3)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,60 L30,42 L60,30 L90,22 L120,18 L150,14 L180,10 L200,8 L200,60 Z"
                    fill="url(#mountainGradientStatus4)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradientStatus3" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#ff9800;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#ff9800;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradientStatus4" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#ffc107;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#ffc107;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>
          </div>
        </div>

        <!-- 下面：内容规模统计 -->
        <div class="content-stats-card">
          <h3 class="module-title">内容规模统计</h3>
          <div class="stats-grid">
            <div class="stat-card-small">
              <div class="stat-icon-small">
                <img src="/assets/icons/icon-note.svg" alt="笔记" />
              </div>
              <div class="stat-info">
                <div class="stat-value-small">{{ noteCount }}</div>
                <div class="stat-label-small">笔记总数</div>
              </div>
              <div class="mountain-decoration">
                <svg width="80" height="40" viewBox="0 0 80 40" preserveAspectRatio="none">
                  <path
                    d="M0,40 L10,30 L20,25 L30,20 L40,15 L50,18 L60,12 L70,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient1)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,40 L15,28 L30,22 L45,16 L60,14 L75,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient2)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradient1" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradient2" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>

            <div class="stat-card-small">
              <div class="stat-icon-small">
                <img src="/assets/icons/icon-comment.svg" alt="评论" />
              </div>
              <div class="stat-info">
                <div class="stat-value-small">{{ remarkCount }}</div>
                <div class="stat-label-small">评论总数</div>
              </div>
              <div class="mountain-decoration">
                <svg width="80" height="40" viewBox="0 0 80 40" preserveAspectRatio="none">
                  <path
                    d="M0,40 L10,30 L20,25 L30,20 L40,15 L50,18 L60,12 L70,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient3)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,40 L15,28 L30,22 L45,16 L60,14 L75,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient4)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradient3" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradient4" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>

            <div class="stat-card-small">
              <div class="stat-icon-small">
                <img src="/assets/icons/icon-question.svg" alt="问题" />
              </div>
              <div class="stat-info">
                <div class="stat-value-small">{{ questionCount }}</div>
                <div class="stat-label-small">问题总数</div>
              </div>
              <div class="mountain-decoration">
                <svg width="80" height="40" viewBox="0 0 80 40" preserveAspectRatio="none">
                  <path
                    d="M0,40 L10,30 L20,25 L30,20 L40,15 L50,18 L60,12 L70,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient5)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,40 L15,28 L30,22 L45,16 L60,14 L75,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient6)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradient5" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradient6" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>

            <div class="stat-card-small">
              <div class="stat-icon-small">
                <img src="/assets/icons/icon-comment.svg" alt="问答评论" />
              </div>
              <div class="stat-info">
                <div class="stat-value-small">{{ qaCommentCount }}</div>
                <div class="stat-label-small">问答评论数</div>
              </div>
              <div class="mountain-decoration">
                <svg width="80" height="40" viewBox="0 0 80 40" preserveAspectRatio="none">
                  <path
                    d="M0,40 L10,30 L20,25 L30,20 L40,15 L50,18 L60,12 L70,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient7)"
                    opacity="0.3"
                  />
                  <path
                    d="M0,40 L15,28 L30,22 L45,16 L60,14 L75,10 L80,8 L80,40 Z"
                    fill="url(#mountainGradient8)"
                    opacity="0.2"
                  />
                  <defs>
                    <linearGradient id="mountainGradient7" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                      <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.1" />
                    </linearGradient>
                    <linearGradient id="mountainGradient8" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                      <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.1" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧 1/4 区域：最近发布的笔记 -->
      <div class="right-section">
        <div class="recent-notes-card">
          <h3 class="module-title">最近发布的笔记</h3>
          <div class="notes-list">
            <div v-if="loading" class="loading-text">加载中...</div>
            <div v-else-if="recentNotes.length === 0" class="empty-text">暂无笔记</div>
            <div
              v-else
              v-for="(note, index) in recentNotes"
              :key="index"
              class="note-item"
              @click="viewNote(note)"
            >
              <div class="note-icon">
                <img src="/assets/icons/icon-note.svg" alt="笔记" />
              </div>
              <div class="note-content">
                <div class="note-title">{{ note.title }}</div>
                <div class="note-meta">
                  <span class="note-author">{{ note.author }}</span>
                  <span class="note-time">{{ note.time }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="mountain-decoration-notes">
            <svg width="100%" height="70" viewBox="0 0 200 70" preserveAspectRatio="none">
              <path
                d="M0,70 L20,55 L40,45 L60,38 L80,32 L100,30 L120,28 L140,26 L160,24 L180,22 L200,20 L200,70 Z"
                fill="url(#mountainGradientNotes1)"
                opacity="0.3"
              />
              <path
                d="M0,70 L30,52 L60,40 L90,34 L120,30 L150,26 L180,22 L200,20 L200,70 Z"
                fill="url(#mountainGradientNotes2)"
                opacity="0.2"
              />
              <defs>
                <linearGradient id="mountainGradientNotes1" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#007FFF;stop-opacity:0.6" />
                  <stop offset="100%" style="stop-color:#007FFF;stop-opacity:0.05" />
                </linearGradient>
                <linearGradient id="mountainGradientNotes2" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.5" />
                  <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0.05" />
                </linearGradient>
              </defs>
            </svg>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatTime } from '../../utils/time'
import {
  getOnlineUsers,
  getNoteCount,
  getRemarkCount,
  getPendingNotes,
  getQuestionCount,
  getQACommentCount,
  getNoteList
} from '../../api/admin'

const router = useRouter()

const onlineCount = ref(0)
const newUsersCount = ref(0)
const noteCount = ref(0)
const remarkCount = ref(0)
const questionCount = ref(0)
const qaCommentCount = ref(0)
const pendingModerationCount = ref(0)
const recentNotes = ref([])
const loading = ref(false)

// 加载统计数据
const loadStats = async () => {
  try {
    // 获取在线用户列表
    const usersRes = await getOnlineUsers()
    let usersList = []
    if (usersRes) {
      if (usersRes.data !== undefined) {
        usersList = usersRes.data
      } else if (Array.isArray(usersRes)) {
        usersList = usersRes
      }
    }
    if (!Array.isArray(usersList)) {
      usersList = []
    }
    onlineCount.value = usersList.length
    // 简单计算新用户（这里可以根据实际需求改进）
    newUsersCount.value = usersList.length > 0 ? 1 : 0

    // 并行获取其他统计数据
    const [noteRes, remarkRes, questionRes, qaCommentRes, pendingNotesRes] = await Promise.all([
      getNoteCount(),
      getRemarkCount(),
      getQuestionCount(),
      getQACommentCount(),
      getPendingNotes()
    ])

    noteCount.value = noteRes?.data?.noteCount || noteRes?.noteCount || 0
    remarkCount.value = remarkRes?.data?.remarkCount || remarkRes?.remarkCount || 0
    questionCount.value = questionRes?.data?.questionCount || questionRes?.questionCount || 0
    qaCommentCount.value = qaCommentRes?.data?.qaCommentCount || qaCommentRes?.qaCommentCount || 0

    const pendingNotesList = pendingNotesRes?.data || pendingNotesRes || []
    pendingModerationCount.value = Array.isArray(pendingNotesList) ? pendingNotesList.length : 0
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 加载最近发布的笔记
const loadRecentNotes = async () => {
  loading.value = true
  try {
    const notesRes = await getNoteList()
    const notes = notesRes?.data || notesRes || []
    
    if (Array.isArray(notes)) {
      const sortedNotes = notes
        .sort((a, b) => {
          const timeA = new Date(a.createdAt || a.created_at || 0)
          const timeB = new Date(b.createdAt || b.created_at || 0)
          return timeB - timeA
        })
        .slice(0, 5)
      
      recentNotes.value = sortedNotes.map(note => ({
        id: note.id,
        title: note.title || '无标题',
        author: note.authorName || note.author_name || '未知用户',
        time: formatTime(note.createdAt || note.created_at)
      }))
    } else {
      recentNotes.value = []
    }
  } catch (error) {
    console.error('获取笔记列表失败:', error)
    recentNotes.value = []
  } finally {
    loading.value = false
  }
}

// 导航到在线用户页面
const goToOnlineUsers = () => {
  router.push({ path: '/admin/main', query: { tab: 'online-users' } })
}

// 导航到审核页面
const goToModeration = () => {
  router.push({ path: '/admin/main', query: { tab: 'moderation' } })
}

// 查看笔记详情
const viewNote = (note) => {
  router.push({ path: '/admin/main', query: { tab: 'notes' } })
}

let refreshTimer = null

onMounted(() => {
  loadStats()
  loadRecentNotes()
  // 每30秒刷新一次
  refreshTimer = setInterval(() => {
    loadStats()
    loadRecentNotes()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.dashboard-view {
  width: 100%;
  padding: 0;
  display: flex;
  justify-content: center;
}

.dashboard-layout {
  display: grid;
  grid-template-columns: 3fr 1fr;
  gap: 24px;
  align-items: start;
  width: 60%;
  max-width: 1400px;
}

/* 左侧区域 */
.left-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 模块标题 */
.module-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 20px 0;
  text-align: left;
}

/* 系统状态概览卡片 */
.status-overview-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.status-cards-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.status-card {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
  overflow: hidden;
}

.status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.status-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f7ff;
  border-radius: 12px;
  flex-shrink: 0;
}

.status-icon.warning {
  background: #fff4e6;
}

.status-icon img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.status-content {
  flex: 1;
}

.status-value {
  font-size: 40px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
  margin-bottom: 8px;
}

.status-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.status-trend {
  font-size: 13px;
}

.trend-up {
  color: #007FFF;
}

.trend-success {
  color: #52c41a;
}

.status-action-btn {
  padding: 8px 16px;
  background: var(--brand-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  align-self: flex-start;
}

.status-action-btn:hover {
  background: var(--brand-primary-hover);
  box-shadow: var(--shadow-xs);
}

.mountain-decoration-large {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  opacity: 0.4;
  z-index: 0;
  pointer-events: none;
}

.status-icon,
.status-content,
.status-action-btn {
  position: relative;
  z-index: 1;
}

/* 内容规模统计卡片 */
.content-stats-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.stat-card-small {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s;
}

.stat-card-small:hover {
  transform: translateY(-2px);
}

.stat-icon-small {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 10px;
  flex-shrink: 0;
  z-index: 1;
}

.stat-icon-small img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.stat-info {
  flex: 1;
  z-index: 1;
}

.stat-value-small {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label-small {
  font-size: 13px;
  color: #666;
}

.mountain-decoration {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 80px;
  height: 40px;
  opacity: 0.4;
  z-index: 0;
}

/* 右侧区域：最近发布的笔记 */
.right-section {
  height: 100%;
}

.recent-notes-card {
  background: #fcfcfd;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 600px;
  position: relative;
  overflow: hidden; 
}

.notes-list {
  flex: 1;
  overflow-y: auto;
}

.loading-text,
.empty-text {
  text-align: center;
  color: #999;
  padding: 40px 0;
}

.note-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}

.note-item:hover {
  background: #f8f9fa;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 8px;
}

.note-item:last-child {
  border-bottom: none;
}

.note-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 8px;
  flex-shrink: 0;
}

.note-icon img {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.note-content {
  flex: 1;
  min-width: 0;
}

.note-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 6px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-weight: 500;
}

.note-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #999;
}

.note-author {
  font-weight: 500;
  color: #007FFF;
}

.note-time {
  color: #999;
}

.mountain-decoration-notes {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 70px;
  opacity: 0.4;
  z-index: 0;
  pointer-events: none;
}

.recent-notes-card .notes-list,
.recent-notes-card .module-title {
  position: relative;
  z-index: 1;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
    width: 80%;
  }

  .right-section {
    height: auto;
  }

  .recent-notes-card {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .dashboard-layout {
    width: 95%;
    gap: 16px;
  }

  .status-cards-row {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .status-value {
    font-size: 32px;
  }
}
</style>