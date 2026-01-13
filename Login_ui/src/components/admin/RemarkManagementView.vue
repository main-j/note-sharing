<template>
  <div class="remark-management-view">
    <div class="view-header">
      <h2 class="page-title">评论管理</h2>
      <div class="header-actions">
        <div class="stat-item">
          <span class="stat-label">评论总数：</span>
          <span class="stat-value">{{ remarkCount }}</span>
        </div>
        <button class="refresh-btn" @click="loadRemarks">刷新</button>
      </div>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>评论ID</th>
            <th>内容</th>
            <th>作者</th>
            <th>笔记ID</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="paginatedRemarks.length === 0">
            <td colspan="6" class="empty-cell">暂无评论</td>
          </tr>
          <tr v-else v-for="remark in paginatedRemarks" :key="remark.id || remark.remarkId">
            <td>{{ remark.id || remark.remarkId }}</td>
            <td class="content-cell">{{ remark.content || remark.text || '-' }}</td>
            <td>{{ remark.authorName || remark.username || '-' }}</td>
            <td>{{ remark.noteId || '-' }}</td>
            <td>{{ formatTime(remark.createdAt || remark.createTime) }}</td>
            <td>
              <button class="action-btn" @click="viewNote(remark.noteId)">查看笔记</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination-container" v-if="!loading && remarks.length > 0">
      <div class="pagination-info">
        显示第 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, remarks.length) }} 条，共 {{ remarks.length }} 条
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
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { getRemarkCount, getRemarkList } from '../../api/admin'

const remarkCount = ref(0)
const remarks = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 30

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(remarks.value.length / pageSize)
})

// 计算当前页显示的数据
const paginatedRemarks = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return remarks.value.slice(start, end)
})

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

const loadRemarks = async (preservePage = false) => {
  loading.value = true
  try {
    const [countRes, listRes] = await Promise.all([
      getRemarkCount(),
      getRemarkList()
    ])
    
    remarkCount.value = countRes?.data?.remarkCount || countRes?.remarkCount || 0
    remarks.value = listRes?.data || listRes || []
    
    // 如果数据量发生变化，可能需要调整页码
    if (!preservePage) {
      currentPage.value = 1
    } else {
      // 如果当前页超出范围，调整到最后一页
      const newTotalPages = Math.ceil(remarks.value.length / pageSize)
      if (currentPage.value > newTotalPages && newTotalPages > 0) {
        currentPage.value = newTotalPages
      }
    }
  } catch (error) {
    console.error('加载评论列表失败:', error)
    remarks.value = []
  } finally {
    loading.value = false
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

const viewNote = (noteId) => {
  if (noteId) {
    window.open(`/main?tab=note-detail&noteId=${noteId}`, '_blank')
  }
}

const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value && page !== currentPage.value && page !== '...') {
    currentPage.value = page
    // 滚动到表格顶部
    const tableContainer = document.querySelector('.table-container')
    if (tableContainer) {
      tableContainer.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
}

let refreshTimer = null

onMounted(() => {
  loadRemarks()
  // 每30秒自动刷新数据
  refreshTimer = setInterval(() => {
    loadRemarks(true) // 保持当前页码
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
.remark-management-view {
  max-width: 1200px;
  margin: 0 auto;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
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
  font-size: 16px;
  font-weight: 600;
  color: #007FFF;
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

.content-cell {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-btn {
  padding: 6px 12px;
  border: 1px solid #007FFF;
  background: white;
  color: #007FFF;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #007FFF;
  color: white;
}

.loading-cell,
.empty-cell {
  text-align: center;
  color: #999;
  padding: 40px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
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
  border-color: #007FFF;
  color: #007FFF;
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
  padding: 0 8px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-number:hover:not(.active):not(:disabled) {
  background: #f5f5f5;
  border-color: #007FFF;
  color: #007FFF;
}

.page-number.active {
  background: #007FFF;
  border-color: #007FFF;
  color: white;
  font-weight: 600;
}

.page-number:disabled {
  cursor: default;
  border: none;
  background: transparent;
}

@media (max-width: 640px) {
  .pagination-container {
    flex-direction: column;
    align-items: stretch;
  }
  
  .pagination-info {
    text-align: center;
  }
  
  .pagination {
    justify-content: center;
  }
}
</style>
