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
          <tr v-else v-for="remark in paginatedRemarks" :key="remark.id || remark.remarkId || remark._id">
            <td>{{ remark.id || remark.remarkId || remark._id }}</td>
            <td class="content-cell">{{ remark.content || remark.text || '-' }}</td>
            <td>{{ remark.authorName || remark.username || '-' }}</td>
            <td>{{ remark.noteId || '-' }}</td>
            <td>{{ formatTime(remark.createdAt || remark.createTime) }}</td>
            <td>
              <button class="action-btn" @click="viewNote(remark.noteId)">查看笔记</button>
              <button class="action-btn view-tree-btn" @click="viewCommentTree(remark)">查看评论树</button>
              <button class="action-btn delete-btn" @click="handleDeleteRemark(remark)">删除</button>
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

    <!-- 评论树查看对话框 -->
    <div v-if="showTreeDialog" class="tree-dialog-overlay" @click="closeTreeDialog">
      <div class="tree-dialog" @click.stop>
        <div class="tree-dialog-header">
          <h3>评论树</h3>
          <button class="close-btn" @click="closeTreeDialog">×</button>
        </div>
        <div class="tree-dialog-content">
          <div v-if="treeLoading" class="tree-loading">
            <div class="loader"></div>
            <p>加载评论树中...</p>
          </div>
          <div v-else-if="commentTree" class="comment-tree-container">
            <CommentItem
              :comment="commentTree"
              :depth="0"
              :is-logged-in="false"
              :current-user-id="null"
              :replying-to-id="null"
              :reply-content="''"
              :comment-submitting="false"
              :comment-action-loading="{}"
            />
          </div>
          <div v-else class="tree-empty">
            <p>无法加载评论树</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 消息提示组件 -->
    <MessageToast
      v-if="showToast"
      :message="toastMessage"
      :type="toastType"
      :duration="toastDuration"
      :auto-close="toastType !== 'confirm'"
      :show-close="toastType !== 'confirm'"
      @close="hideMessage"
      @confirm="handleConfirm"
      @cancel="handleCancel"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { getRemarkCount, getRemarkList, adminDeleteRemark } from '../../api/admin'
import { getRemarkTree } from '../../api/remark'
import CommentItem from '../user/CommentItem.vue'
import { useUserStore } from '@/stores/user'
import MessageToast from '../MessageToast.vue'
import { useMessage } from '../../utils/message'

const userStore = useUserStore()

const remarkCount = ref(0)
const remarks = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 30

// 评论树相关状态
const showTreeDialog = ref(false)
const commentTree = ref(null)
const treeLoading = ref(false)

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

const handleDeleteRemark = async (remark) => {
  const remarkId = remark.id || remark.remarkId || remark._id
  if (!remarkId) {
    showError('评论ID不存在，无法删除')
    return
  }

  // 先获取评论树，了解有多少子评论
  let childCount = 0
  try {
    const adminUserId = userStore.userInfo?.id || 0
    const tree = await getRemarkTree(remarkId, adminUserId)
    if (tree?.data || tree) {
      const commentTree = tree?.data || tree
      // 递归计算子评论数量
      const countChildren = (comment) => {
        if (!comment || !comment.replies || comment.replies.length === 0) {
          return 0
        }
        let count = comment.replies.length
        comment.replies.forEach(child => {
          count += countChildren(child)
        })
        return count
      }
      childCount = countChildren(commentTree)
    }
  } catch (error) {
    console.warn('获取评论树失败，将直接删除:', error)
  }

  const childCountText = childCount > 0 ? `\n⚠️ 注意：此评论下有 ${childCount} 条子评论，删除时将一并删除！` : ''
  
  try {
    const confirmed = await showConfirm(
      `确定要删除这条评论吗？\n\n` +
      `评论ID: ${remarkId}\n` +
      `内容: ${(remark.content || remark.text || '').substring(0, 50)}${(remark.content || remark.text || '').length > 50 ? '...' : ''}\n` +
      `作者: ${remark.authorName || remark.username || '未知'}` +
      childCountText +
      `\n\n删除后无法恢复！`
    )
    if (!confirmed) return
  } catch {
    return
  }

  try {
    loading.value = true
    const result = await adminDeleteRemark(remarkId)
    
    if (result && (result.code === 200 || result === true)) {
      const successMsg = childCount > 0 
        ? `删除成功！已删除评论及其 ${childCount} 条子评论。`
        : '删除成功！'
      showSuccess(successMsg)
      // 重新加载评论列表
      await loadRemarks(true) // 保持当前页码
    } else {
      showError('删除失败：' + (result?.message || '未知错误'))
    }
  } catch (error) {
    console.error('删除评论失败:', error)
    showError('删除失败：' + (error.response?.data?.message || error.message || '请稍后重试'))
  } finally {
    loading.value = false
  }
}

const viewCommentTree = async (remark) => {
  const remarkId = remark.id || remark.remarkId || remark._id
  if (!remarkId) {
    showError('评论ID不存在，无法查看评论树')
    return
  }

  showTreeDialog.value = true
  commentTree.value = null
  treeLoading.value = true

  try {
    // 获取当前登录用户ID（管理员ID）
    const adminUserId = userStore.userInfo?.id || 0
    const tree = await getRemarkTree(remarkId, adminUserId)
    
    // 处理响应格式
    commentTree.value = tree?.data || tree || null
  } catch (error) {
    console.error('加载评论树失败:', error)
    showError('加载评论树失败：' + (error.response?.data?.message || error.message || '请稍后重试'))
    commentTree.value = null
  } finally {
    treeLoading.value = false
  }
}

const closeTreeDialog = () => {
  showTreeDialog.value = false
  commentTree.value = null
  treeLoading.value = false
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

.action-btn.delete-btn {
  margin-left: 8px;
  border-color: #dc3545;
  color: #dc3545;
}

.action-btn.delete-btn:hover {
  background: #dc3545;
  color: white;
}

.action-btn.view-tree-btn {
  margin-left: 8px;
  border-color: #22bfa3;
  color: #22bfa3;
}

.action-btn.view-tree-btn:hover {
  background: #22bfa3;
  color: white;
}

/* 评论树对话框样式 */
.tree-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.tree-dialog {
  background: white;
  border-radius: 12px;
  width: 100%;
  max-width: 800px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.tree-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e9ecef;
}

.tree-dialog-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  color: #666;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.tree-dialog-content {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.tree-loading,
.tree-empty {
  text-align: center;
  padding: 40px;
  color: #999;
}

.loader {
  border: 3px solid #f3f3f3;
  border-top: 3px solid #22bfa3;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.comment-tree-container {
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
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
