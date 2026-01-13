<template>
  <div class="dashboard-view">
    <h2 class="page-title">仪表盘</h2>
    
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon">👥</div>
        <div class="stat-content">
          <div class="stat-value">{{ onlineCount }}</div>
          <div class="stat-label">当前在线用户</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">📝</div>
        <div class="stat-content">
          <div class="stat-value">{{ noteCount }}</div>
          <div class="stat-label">笔记总数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">💬</div>
        <div class="stat-content">
          <div class="stat-value">{{ remarkCount }}</div>
          <div class="stat-label">评论总数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">⚠️</div>
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
import { getOnlineCount, getNoteCount, getRemarkCount, getPendingModerations } from '../../api/admin'

const onlineCount = ref(0)
const noteCount = ref(0)
const remarkCount = ref(0)
const pendingModerationCount = ref(0)

const loadStats = async () => {
  try {
    const [onlineRes, noteRes, remarkRes, moderationRes] = await Promise.all([
      getOnlineCount(),
      getNoteCount(),
      getRemarkCount(),
      getPendingModerations()
    ])
    
    onlineCount.value = onlineRes?.data?.onlineCount || onlineRes?.onlineCount || 0
    noteCount.value = noteRes?.data?.noteCount || noteRes?.noteCount || 0
    remarkCount.value = remarkRes?.data?.remarkCount || remarkRes?.remarkCount || 0
    
    const moderationList = moderationRes?.data || moderationRes || []
    pendingModerationCount.value = Array.isArray(moderationList) ? moderationList.length : 0
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
  font-size: 40px;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f7ff;
  border-radius: 12px;
  flex-shrink: 0;
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
