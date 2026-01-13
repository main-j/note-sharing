<template>
  <div class="all-users-view">
    <div class="view-header">
      <h2 class="page-title">所有用户</h2>
      <button class="refresh-btn" @click="loadAllUsers">刷新</button>
    </div>
    
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-label">用户总数：</span>
        <span class="stat-value">{{ totalCount }}</span>
      </div>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>用户ID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>学号</th>
            <th>角色</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="allUsers.length === 0">
            <td colspan="8" class="empty-cell">暂无用户</td>
          </tr>
          <tr v-else v-for="user in allUsers" :key="user.id">
            <td>{{ user.id }}</td>
            <td>{{ user.username || '-' }}</td>
            <td>
              <router-link 
                v-if="user.email && user.studentNumber"
                :to="`/admin/${user.studentNumber}/info?email=${encodeURIComponent(user.email)}&fromTab=all-users`"
                class="email-link"
              >
                {{ user.email }}
              </router-link>
              <span v-else>{{ user.email || '-' }}</span>
            </td>
            <td>{{ user.studentNumber || '-' }}</td>
            <td>
              <span :class="['role-badge', user.role === 'Admin' ? 'admin' : 'user']">
                {{ user.role === 'Admin' ? '管理员' : '用户' }}
              </span>
            </td>
            <td>
              <span :class="['status-badge', user.enabled ? 'enabled' : 'disabled']">
                {{ user.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ formatTime(user.createdAt) }}</td>
            <td>{{ formatTime(user.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination-container" v-if="!loading && allUsers.length > 0">
      <div class="pagination-info">
        显示第 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, totalCount) }} 条，共 {{ totalCount }} 条
      </div>
      <div class="pagination">
        <button 
          class="page-btn" 
          :disabled="currentPage === 1" 
          @click="goToPage(currentPage - 1)"
        >
          上一页
        </button>
        <div class="page-numbers">
          <button
            v-for="page in visiblePages"
            :key="page"
            class="page-number"
            :class="{ active: page === currentPage }"
            @click="goToPage(page)"
            :disabled="page === '...'"
          >
            {{ page }}
          </button>
        </div>
        <button 
          class="page-btn" 
          :disabled="currentPage === totalPages" 
          @click="goToPage(currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { getAllUsers } from '../../api/admin'

const totalCount = ref(0)
const allUsers = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 30
const totalPages = ref(1)

// 计算可见的页码
const visiblePages = computed(() => {
  const pages = []
  const total = totalPages.value
  const current = currentPage.value
  
  if (total <= 7) {
    // 如果总页数少于等于7页，显示所有页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 如果总页数大于7页，显示部分页码
    if (current <= 4) {
      // 当前页在前4页
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    } else if (current >= total - 3) {
      // 当前页在后4页
      pages.push(1)
      pages.push('...')
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    }
  }
  
  return pages
})

const loadAllUsers = async (page = 1) => {
  loading.value = true
  try {
    const response = await getAllUsers(page, pageSize)
    
    // 处理响应数据
    if (response && response.data) {
      const data = response.data
      // 设置用户列表
      allUsers.value = data.users || []
      totalCount.value = data.total || 0
      totalPages.value = data.totalPages || 1
      currentPage.value = data.page || page
    } else {
      console.warn('用户数据格式异常:', response)
      allUsers.value = []
      totalCount.value = 0
      totalPages.value = 1
    }
  } catch (error) {
    console.error('加载所有用户失败:', error)
    allUsers.value = []
    totalCount.value = 0
    totalPages.value = 1
  } finally {
    loading.value = false
  }
}

const goToPage = (page) => {
  if (page === '...' || page < 1 || page > totalPages.value || page === currentPage.value) {
    return
  }
  currentPage.value = page
  loadAllUsers(page)
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  try {
    return new Date(timeStr).toLocaleString('zh-CN')
  } catch {
    return timeStr
  }
}

onMounted(() => {
  loadAllUsers(1)
})
</script>

<style scoped>
.all-users-view {
  max-width: 1200px;
  margin: 0 auto;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.refresh-btn {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: #f5f5f5;
  border-color: #ccc;
}

.stats-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #007FFF;
}

.table-container {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead {
  background: #f8f9fa;
}

.data-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  border-bottom: 2px solid #e9ecef;
}

.data-table td {
  padding: 12px 16px;
  font-size: 14px;
  color: #666;
  border-bottom: 1px solid #f0f0f0;
}

.data-table tbody tr:hover {
  background: #f8f9fa;
}

.loading-cell,
.empty-cell {
  text-align: center;
  padding: 40px;
  color: #999;
}

.role-badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.role-badge.admin {
  background: #fff3cd;
  color: #856404;
}

.role-badge.user {
  background: #d1ecf1;
  color: #0c5460;
}

.status-badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.enabled {
  background: #d4edda;
  color: #155724;
}

.status-badge.disabled {
  background: #f8d7da;
  color: #721c24;
}

.email-link {
  color: #007FFF;
  text-decoration: none;
  transition: all 0.2s;
}

.email-link:hover {
  color: #0056b3;
  text-decoration: underline;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.pagination-info {
  font-size: 14px;
  color: #666;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #ccc;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.page-number {
  min-width: 36px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.page-number:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #ccc;
}

.page-number.active {
  background: #007FFF;
  color: white;
  border-color: #007FFF;
}

.page-number:disabled {
  cursor: default;
  border: none;
  background: transparent;
}
</style>
