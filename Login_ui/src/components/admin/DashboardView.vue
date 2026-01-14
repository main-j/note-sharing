<template>
  <div class="dashboard-view">
    <h2 class="page-title">仪表盘</h2>
    
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon">
          <img src="/assets/icons/icon-users.svg" alt="在线用户" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ onlineCount }}</div>
          <div class="stat-label">当前在线用户</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <img src="/assets/icons/icon-note.svg" alt="笔记" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ noteCount }}</div>
          <div class="stat-label">笔记总数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <img src="/assets/icons/icon-comment.svg" alt="评论" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ remarkCount }}</div>
          <div class="stat-label">评论总数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <img src="/assets/icons/icon-question.svg" alt="问题" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ questionCount }}</div>
          <div class="stat-label">问题总数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <img src="/assets/icons/icon-warning.svg" alt="待审查" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ pendingModerationCount }}</div>
          <div class="stat-label">待审查内容</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getOnlineUsers, getNoteCount, getRemarkCount, getPendingNotes, getQuestionCount } from '../../api/admin'

const onlineCount = ref(0)
const noteCount = ref(0)
const remarkCount = ref(0)
const questionCount = ref(0)
const pendingModerationCount = ref(0)

const loadStats = async () => {
  try {
    // 获取在线用户列表，使用列表长度作为在线人数统计
    const usersRes = await getOnlineUsers()
    
    // 处理在线用户列表 - 确保是数组类型
    let usersList = []
    if (usersRes) {
      // 如果 usersRes 有 data 属性，使用 data
      if (usersRes.data !== undefined) {
        usersList = usersRes.data
      } else if (Array.isArray(usersRes)) {
        // 如果 usersRes 本身就是数组，直接使用
        usersList = usersRes
      }
    }
    
    // 确保 usersList 是数组类型
    if (!Array.isArray(usersList)) {
      console.warn('在线用户数据格式异常，不是数组:', usersRes)
      usersList = []
    }
    
    // 使用用户列表长度作为在线人数统计
    onlineCount.value = usersList.length
    
    // 并行获取其他统计数据
    const [noteRes, remarkRes, questionRes, pendingNotesRes] = await Promise.all([
      getNoteCount(),
      getRemarkCount(),
      getQuestionCount(),
      getPendingNotes()
    ])
    
    noteCount.value = noteRes?.data?.noteCount || noteRes?.noteCount || 0
    remarkCount.value = remarkRes?.data?.remarkCount || remarkRes?.remarkCount || 0
    questionCount.value = questionRes?.data?.questionCount || questionRes?.questionCount || 0
    
    // 待审查内容：只统计待审核的笔记（未处理的）
    const pendingNotesList = pendingNotesRes?.data || pendingNotesRes || []
    pendingModerationCount.value = Array.isArray(pendingNotesList) ? pendingNotesList.length : 0
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

let refreshTimer = null

onMounted(() => {
  loadStats()
  // 每30秒刷新一次
  refreshTimer = setInterval(loadStats, 30000)
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
  max-width: 1200px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.stat-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f7ff;
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-icon img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
