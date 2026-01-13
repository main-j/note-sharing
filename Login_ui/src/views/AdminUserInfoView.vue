<template>
  <div class="admin-user-info-view">
    <div class="view-header">
      <button class="back-btn" @click="goBack">← 返回</button>
      <h2 class="page-title">用户账户信息</h2>
    </div>

    <div v-if="loading" class="loading-container">
      <div class="loading-text">加载中...</div>
    </div>

    <div v-else-if="error" class="error-container">
      <div class="error-text">{{ error }}</div>
      <button class="retry-btn" @click="loadUserInfo">重试</button>
    </div>

    <div v-else-if="userInfo" class="user-info-container">
      <!-- 用户基本信息 -->
      <div class="info-section">
        <h3 class="section-title">基本信息</h3>
        <div class="info-grid">
          <div class="info-item">
            <label>用户ID</label>
            <div class="info-value">{{ userInfo.userId }}</div>
          </div>
          <div class="info-item">
            <label>用户名</label>
            <div class="info-value">{{ userInfo.username }}</div>
          </div>
          <div class="info-item">
            <label>邮箱</label>
            <div class="info-value">{{ userInfo.email }}</div>
          </div>
          <div class="info-item">
            <label>学号</label>
            <div class="info-value">{{ userInfo.studentNumber }}</div>
          </div>
          <div class="info-item">
            <label>头像</label>
            <div class="info-value">
              <img 
                v-if="userInfo.avatarUrl" 
                :src="userInfo.avatarUrl" 
                alt="头像" 
                class="avatar-img"
                @error="handleImageError"
              />
              <span v-else>未设置</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 发布文章列表 -->
      <div class="info-section">
        <h3 class="section-title">发布文章（{{ userInfo.publishedNotes?.length || 0 }}）</h3>
        <div v-if="!userInfo.publishedNotes || userInfo.publishedNotes.length === 0" class="empty-state">
          该用户暂无发布文章
        </div>
        <div v-else class="notes-list">
          <div 
            v-for="note in userInfo.publishedNotes" 
            :key="note.id" 
            class="note-item"
          >
            <div class="note-title">{{ note.title }}</div>
            <div class="note-meta">
              <span>ID: {{ note.id }}</span>
              <span>类型: {{ note.fileType }}</span>
              <span>创建时间: {{ formatTime(note.createdAt) }}</span>
              <span>更新时间: {{ formatTime(note.updatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 关注用户列表 -->
      <div class="info-section">
        <h3 class="section-title">关注用户（{{ userInfo.followings?.length || 0 }}）</h3>
        <div v-if="!userInfo.followings || userInfo.followings.length === 0" class="empty-state">
          该用户暂无关注用户
        </div>
        <div v-else class="followings-list">
          <div 
            v-for="following in userInfo.followings" 
            :key="following.userId" 
            class="following-item"
          >
            <div class="following-info">
              <img 
                v-if="following.avatarUrl" 
                :src="following.avatarUrl" 
                alt="头像" 
                class="following-avatar"
                @error="handleImageError"
              />
              <div class="following-details">
                <div class="following-name">{{ following.username }}</div>
                <div class="following-email">{{ following.email }}</div>
              </div>
            </div>
            <div class="following-time">关注时间: {{ formatTime(following.followTime) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUserInfoByEmail } from '../api/admin'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const error = ref('')
const userInfo = ref(null)

const loadUserInfo = async () => {
  const studentNumber = route.params.studentNumber
  if (!studentNumber) {
    error.value = '学号参数缺失'
    return
  }

  loading.value = true
  error.value = ''
  
  try {
    // 首先需要通过学号找到邮箱，但后端接口需要邮箱
    // 我们需要先通过学号获取用户信息，或者修改后端接口支持学号
    // 暂时假设前端可以通过学号获取邮箱，或者修改后端接口
    // 这里先尝试从路由参数获取邮箱，如果没有则使用学号
    const email = route.query.email
    
    if (!email) {
      error.value = '缺少邮箱参数'
      loading.value = false
      return
    }

    const response = await getUserInfoByEmail(email)
    
    if (response && response.data) {
      userInfo.value = response.data
    } else {
      error.value = '获取用户信息失败'
    }
  } catch (err) {
    console.error('加载用户信息失败:', err)
    error.value = err.response?.data?.message || err.message || '加载用户信息失败'
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  // 根据来源标签页返回到对应的用户管理模块
  const fromTab = route.query.fromTab
  
  if (fromTab === 'all-users') {
    // 返回到所有用户列表
    router.push({
      path: '/admin/main',
      query: { tab: 'all-users' }
    })
  } else if (fromTab === 'online-users') {
    // 返回到在线用户列表
    router.push({
      path: '/admin/main',
      query: { tab: 'online-users' }
    })
  } else {
    // 默认返回到所有用户列表
    router.push({
      path: '/admin/main',
      query: { tab: 'all-users' }
    })
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  try {
    return new Date(timeStr).toLocaleString('zh-CN')
  } catch {
    return timeStr
  }
}

const handleImageError = (event) => {
  event.target.style.display = 'none'
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.admin-user-info-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.view-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #f5f5f5;
  border-color: #ccc;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.loading-container,
.error-container {
  text-align: center;
  padding: 60px 20px;
}

.loading-text,
.error-text {
  font-size: 16px;
  color: #666;
  margin-bottom: 16px;
}

.error-text {
  color: #d32f2f;
}

.retry-btn {
  padding: 8px 16px;
  border: 1px solid #007FFF;
  background: #007FFF;
  color: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.retry-btn:hover {
  background: #0056b3;
}

.user-info-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.info-section {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.info-value {
  font-size: 16px;
  color: #333;
}

.avatar-img {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 14px;
}

.notes-list,
.followings-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-item {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  transition: all 0.2s;
}

.note-item:hover {
  border-color: #007FFF;
  background: #f8f9ff;
}

.note-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.note-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 14px;
  color: #666;
}

.following-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  transition: all 0.2s;
}

.following-item:hover {
  border-color: #007FFF;
  background: #f8f9ff;
}

.following-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.following-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.following-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.following-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.following-email {
  font-size: 14px;
  color: #666;
}

.following-time {
  font-size: 14px;
  color: #999;
}
</style>
