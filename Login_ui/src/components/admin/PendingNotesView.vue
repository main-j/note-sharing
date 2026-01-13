<template>
  <div class="pending-notes-view">
    <div class="view-header">
      <h2 class="page-title">待审核笔记</h2>
      <button class="refresh-btn" @click="loadPendingNotes">刷新</button>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>笔记ID</th>
            <th>笔记标题</th>
            <th>文件类型</th>
            <th>风险等级</th>
            <th>风险评分</th>
            <th>违规类别</th>
            <th>提交时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="paginatedNotes.length === 0">
            <td colspan="8" class="empty-cell">暂无待审核笔记</td>
          </tr>
          <tr v-else v-for="note in paginatedNotes" :key="note.noteId">
            <td>{{ note.noteId }}</td>
            <td class="title-cell" :title="note.noteTitle">
              {{ note.noteTitle || '无标题' }}
            </td>
            <td>{{ note.fileType || '-' }}</td>
            <td>
              <span :class="['risk-badge', getRiskClass(note.riskLevel)]">
                {{ getRiskText(note.riskLevel) }}
              </span>
            </td>
            <td>{{ note.score || 0 }}分</td>
            <td class="categories-cell">
              <span v-if="note.categories && note.categories.length > 0">
                {{ note.categories.join(', ') }}
              </span>
              <span v-else>-</span>
            </td>
            <td>{{ formatTime(note.moderationCreatedAt) }}</td>
            <td>
              <button class="action-btn" @click="viewNoteDetail(note)">查看详情</button>
              <button class="action-btn review-btn" @click="reviewNote(note)">审查</button>
              <button class="action-btn handle-btn" @click="handleNote(note)">处理</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination-container" v-if="!loading && pendingNotes.length > 0">
      <div class="pagination-info">
        显示第 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, pendingNotes.length) }} 条，共 {{ pendingNotes.length }} 条
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

    <!-- 审查对话框 -->
    <div v-if="showReviewDialog" class="dialog-overlay" @click="closeReviewDialog">
      <div class="review-dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>笔记审查</h3>
          <button class="close-btn" @click="closeReviewDialog">×</button>
        </div>
        <div class="review-dialog-body">
          <div v-if="reviewLoading" class="loading-section">
            <p>正在审查中...</p>
          </div>
          <div v-else-if="reviewResult" class="review-content">
            <!-- 笔记信息 -->
            <div class="review-info-section">
              <h4>笔记信息</h4>
              <div class="info-item">
                <label>笔记ID：</label>
                <span>{{ reviewResult.noteId }}</span>
              </div>
              <div class="info-item">
                <label>笔记标题：</label>
                <span>{{ reviewResult.noteTitle }}</span>
              </div>
              <div class="info-item">
                <label>文件类型：</label>
                <span>{{ reviewResult.fileType }}</span>
              </div>
            </div>

            <!-- 检测结果 -->
            <div class="review-result-section">
              <h4>检测结果</h4>
              <div class="result-info">
                <div class="result-item">
                  <label>是否检测到敏感词：</label>
                  <span :class="reviewResult.hasSensitiveWords ? 'result-warning' : 'result-safe'">
                    {{ reviewResult.hasSensitiveWords ? '是' : '否' }}
                  </span>
                </div>
                <div class="result-item" v-if="reviewResult.hitCount !== undefined">
                  <label>检测到敏感词数量：</label>
                  <span>{{ reviewResult.hitCount }} 个</span>
                </div>
                <div class="result-item" v-if="reviewResult.uniqueHits && reviewResult.uniqueHits.length > 0">
                  <label>敏感词列表：</label>
                  <div class="sensitive-words-list">
                    <span v-for="word in reviewResult.uniqueHits" :key="word" class="sensitive-word-tag">
                      {{ word }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 高亮预览 -->
            <div v-if="reviewHighlightedText" class="highlight-section">
              <h4>高亮预览</h4>
              <div 
                class="highlight-preview" 
                v-html="reviewHighlightedText"
              ></div>
            </div>

            <!-- 原文预览（如果没有高亮） -->
            <div v-else-if="reviewNoteContent" class="content-section">
              <h4>笔记内容</h4>
              <div class="content-preview">{{ reviewNoteContent }}</div>
            </div>

            <!-- 审查操作 -->
            <div class="review-actions-section">
              <h4>审查操作</h4>
              <label>管理员备注：</label>
              <textarea
                v-model="adminNote"
                class="note-input"
                placeholder="请输入审查备注（未通过时必填）..."
                rows="4"
              ></textarea>
              <div class="review-buttons">
                <button class="submit-btn approve-btn" @click="approveReview">通过</button>
                <button class="submit-btn reject-btn" @click="rejectReview">未通过</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 笔记详情对话框 -->
    <div v-if="showDialog" class="dialog-overlay" @click="closeDialog">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>笔记详情</h3>
          <button class="close-btn" @click="closeDialog">×</button>
        </div>
        <div class="dialog-body">
          <div class="detail-section">
            <h4>笔记信息</h4>
            <div class="detail-item">
              <label>笔记ID：</label>
              <span>{{ currentNote?.noteId }}</span>
            </div>
            <div class="detail-item">
              <label>笔记标题：</label>
              <span>{{ currentNote?.noteTitle }}</span>
            </div>
            <div class="detail-item">
              <label>文件类型：</label>
              <span>{{ currentNote?.fileType || '-' }}</span>
            </div>
            <div class="detail-item">
              <label>创建时间：</label>
              <span>{{ formatTime(currentNote?.noteCreatedAt) }}</span>
            </div>
            <div class="detail-item">
              <label>更新时间：</label>
              <span>{{ formatTime(currentNote?.noteUpdatedAt) }}</span>
            </div>
            <div v-if="currentNote?.fileUrl" class="detail-item">
              <label>文件链接：</label>
              <a :href="currentNote.fileUrl" target="_blank" class="file-link">查看文件</a>
            </div>
          </div>

          <div class="detail-section">
            <h4>审查信息</h4>
            <div class="detail-item">
              <label>审查ID：</label>
              <span>{{ currentNote?.moderationId }}</span>
            </div>
            <div class="detail-item">
              <label>状态：</label>
              <span :class="['status-badge', getStatusClass(currentNote?.status)]">
                {{ getStatusText(currentNote?.status) }}
              </span>
            </div>
            <div class="detail-item">
              <label>风险等级：</label>
              <span :class="['risk-badge', getRiskClass(currentNote?.riskLevel)]">
                {{ getRiskText(currentNote?.riskLevel) }}
              </span>
            </div>
            <div class="detail-item">
              <label>风险评分：</label>
              <span>{{ currentNote?.score || 0 }}分</span>
            </div>
            <div class="detail-item">
              <label>违规类别：</label>
              <span v-if="currentNote?.categories && currentNote.categories.length > 0">
                {{ currentNote.categories.join(', ') }}
              </span>
              <span v-else>-</span>
            </div>
            <div v-if="currentNote?.findings && currentNote.findings.length > 0" class="detail-item">
              <label>发现项：</label>
              <div class="findings-list">
                <div v-for="(finding, index) in currentNote.findings" :key="index" class="finding-item">
                  <div v-if="typeof finding === 'object'">
                    <strong>{{ finding.term || finding.category || '未知' }}</strong>
                    <span v-if="finding.snippet"> - {{ finding.snippet }}</span>
                  </div>
                  <div v-else>{{ finding }}</div>
                </div>
              </div>
            </div>
            <div class="detail-item">
              <label>提交时间：</label>
              <span>{{ formatTime(currentNote?.moderationCreatedAt) }}</span>
            </div>
          </div>

          <div v-if="currentNote && !currentNote.isHandled" class="handle-section">
            <h4>处理操作</h4>
            <label>管理员备注：</label>
            <textarea
              v-model="adminNote"
              class="note-input"
              placeholder="请输入处理备注..."
              rows="4"
            ></textarea>
            <div class="handle-buttons">
              <button class="submit-btn approve-btn" @click="approveNote">通过</button>
              <button class="submit-btn reject-btn" @click="rejectNote">拒绝</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { getPendingNotes, handleModeration as handleModerationApi, reviewNote as reviewNoteApi, handleReviewResult } from '../../api/admin'

const pendingNotes = ref([])
const loading = ref(false)
const showDialog = ref(false)
const currentNote = ref(null)
const adminNote = ref('')
const currentPage = ref(1)
const pageSize = 30

// 审查相关状态
const showReviewDialog = ref(false)
const reviewLoading = ref(false)
const reviewResult = ref(null)
const reviewNoteContent = ref('')
const reviewHighlightedText = ref('')

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(pendingNotes.value.length / pageSize)
})

// 计算当前页显示的数据
const paginatedNotes = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return pendingNotes.value.slice(start, end)
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

const loadPendingNotes = async (preservePage = false) => {
  loading.value = true
  try {
    const res = await getPendingNotes()
    const notes = res?.data || res || []
    pendingNotes.value = notes
    
    // 如果数据变化导致当前页超出范围，调整到最后一页
    if (!preservePage && currentPage.value > totalPages.value && totalPages.value > 0) {
      currentPage.value = totalPages.value
    }
  } catch (error) {
    console.error('加载待审核笔记失败:', error)
    pendingNotes.value = []
  } finally {
    loading.value = false
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

// 监听数据变化，自动调整页码
watch(() => pendingNotes.value.length, (newLength, oldLength) => {
  if (newLength === 0 && currentPage.value > 1) {
    currentPage.value = 1
  } else if (currentPage.value > totalPages.value && totalPages.value > 0) {
    currentPage.value = totalPages.value
  }
})

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  try {
    return new Date(timeStr).toLocaleString('zh-CN')
  } catch {
    return timeStr
  }
}

const getRiskText = (riskLevel) => {
  const riskMap = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return riskMap[riskLevel] || riskLevel || '-'
}

const getRiskClass = (riskLevel) => {
  const classMap = {
    LOW: 'risk-low',
    MEDIUM: 'risk-medium',
    HIGH: 'risk-high'
  }
  return classMap[riskLevel] || ''
}

const getStatusText = (status) => {
  const statusMap = {
    SAFE: '安全',
    FLAGGED: '已标记',
    ERROR: '错误'
  }
  return statusMap[status] || status || '-'
}

const getStatusClass = (status) => {
  const classMap = {
    SAFE: 'status-safe',
    FLAGGED: 'status-flagged',
    ERROR: 'status-error'
  }
  return classMap[status] || ''
}

const viewNoteDetail = (note) => {
  currentNote.value = note
  adminNote.value = ''
  showDialog.value = true
}

const handleNote = (note) => {
  viewNoteDetail(note)
}

// 审查笔记
const reviewNote = async (note) => {
  if (!note || !note.noteId) return
  
  reviewLoading.value = true
  showReviewDialog.value = true
  reviewResult.value = null
  reviewNoteContent.value = ''
  reviewHighlightedText.value = ''
  currentNote.value = note
  adminNote.value = ''
  
  try {
    const res = await reviewNoteApi(note.noteId)
    const data = res?.data || res
    reviewResult.value = data
    reviewNoteContent.value = data.noteContent || ''
    
    // 生成高亮文本
    if (data.findings && data.findings.length > 0) {
      generateHighlightedText(data.noteContent, data.findings)
    }
  } catch (error) {
    console.error('审查笔记失败:', error)
    alert('审查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    showReviewDialog.value = false
  } finally {
    reviewLoading.value = false
  }
}

// 生成高亮文本
const generateHighlightedText = (text, findings) => {
  if (!text || !findings || findings.length === 0) {
    reviewHighlightedText.value = ''
    return
  }
  
  const ranges = []
  findings.forEach((finding) => {
    if (finding.startOffset !== undefined && finding.endOffset !== undefined) {
      const start = Math.max(0, Math.min(finding.startOffset, text.length))
      const end = Math.max(start, Math.min(finding.endOffset, text.length))
      if (start < end) {
        ranges.push({ start, end, term: finding.term || '' })
      }
    }
  })
  
  if (ranges.length === 0) {
    reviewHighlightedText.value = ''
    return
  }
  
  // 按位置排序并去重（处理重叠的情况）
  ranges.sort((a, b) => a.start - b.start)
  const mergedRanges = []
  for (let i = 0; i < ranges.length; i++) {
    if (mergedRanges.length === 0 || ranges[i].start >= mergedRanges[mergedRanges.length - 1].end) {
      mergedRanges.push({ ...ranges[i] })
    } else {
      const last = mergedRanges[mergedRanges.length - 1]
      last.end = Math.max(last.end, ranges[i].end)
    }
  }
  
  // 从后往前插入高亮标签，避免位置偏移
  let highlighted = text
  for (let i = mergedRanges.length - 1; i >= 0; i--) {
    const range = mergedRanges[i]
    if (range.start >= 0 && range.end <= highlighted.length && range.start < range.end) {
      const before = highlighted.substring(0, range.start)
      const match = highlighted.substring(range.start, range.end)
      const after = highlighted.substring(range.end)
      highlighted = before + `<mark class="sensitive-highlight">${escapeHtml(match)}</mark>` + after
    }
  }
  
  // 将换行符转换为 <br>
  highlighted = highlighted.replace(/\n/g, '<br>')
  reviewHighlightedText.value = highlighted
}

// HTML转义函数
const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 审查通过
const approveReview = async () => {
  if (!currentNote.value) return
  
  try {
    await handleReviewResult(
      currentNote.value.moderationId,
      true,
      adminNote.value || '已通过审核'
    )
    alert('审查通过，笔记已发布')
    closeReviewDialog()
    // 处理成功后重新加载数据，保持当前页
    const wasOnLastPage = currentPage.value === totalPages.value
    await loadPendingNotes(true)
    if (wasOnLastPage && currentPage.value > totalPages.value && totalPages.value > 0) {
      currentPage.value = totalPages.value
    }
  } catch (error) {
    console.error('审查通过失败:', error)
    alert('处理失败：' + (error.response?.data?.message || error.message))
  }
}

// 审查未通过
const rejectReview = async () => {
  if (!currentNote.value) return
  
  if (!adminNote.value.trim()) {
    alert('请填写拒绝原因')
    return
  }
  
  try {
    await handleReviewResult(
      currentNote.value.moderationId,
      false,
      adminNote.value
    )
    alert('审查未通过，笔记已退回')
    closeReviewDialog()
    // 处理成功后重新加载数据，保持当前页
    const wasOnLastPage = currentPage.value === totalPages.value
    await loadPendingNotes(true)
    if (wasOnLastPage && currentPage.value > totalPages.value && totalPages.value > 0) {
      currentPage.value = totalPages.value
    }
  } catch (error) {
    console.error('审查未通过失败:', error)
    alert('处理失败：' + (error.response?.data?.message || error.message))
  }
}

// 关闭审查对话框
const closeReviewDialog = () => {
  showReviewDialog.value = false
  reviewResult.value = null
  reviewNoteContent.value = ''
  reviewHighlightedText.value = ''
  currentNote.value = null
  adminNote.value = ''
}

const approveNote = async () => {
  if (!currentNote.value) return
  
  try {
    await handleModerationApi(
      currentNote.value.moderationId, 
      adminNote.value || '已通过审核',
      true
    )
    alert('处理成功')
    closeDialog()
    // 处理成功后重新加载数据，保持当前页
    const wasOnLastPage = currentPage.value === totalPages.value
    await loadPendingNotes(true)
    // 如果之前在最后一页且当前页超出范围，调整到新的最后一页
    if (wasOnLastPage && currentPage.value > totalPages.value && totalPages.value > 0) {
      currentPage.value = totalPages.value
    }
  } catch (error) {
    console.error('处理笔记失败:', error)
    alert('处理失败：' + (error.response?.data?.message || error.message))
  }
}

const rejectNote = async () => {
  if (!currentNote.value) return
  
  if (!adminNote.value.trim()) {
    alert('请填写拒绝原因')
    return
  }
  
  try {
    await handleModerationApi(
      currentNote.value.moderationId, 
      adminNote.value,
      false
    )
    alert('已拒绝该笔记')
    closeDialog()
    // 处理成功后重新加载数据，保持当前页
    const wasOnLastPage = currentPage.value === totalPages.value
    await loadPendingNotes(true)
    // 如果之前在最后一页且当前页超出范围，调整到新的最后一页
    if (wasOnLastPage && currentPage.value > totalPages.value && totalPages.value > 0) {
      currentPage.value = totalPages.value
    }
  } catch (error) {
    console.error('处理笔记失败:', error)
    alert('处理失败：' + (error.response?.data?.message || error.message))
  }
}

const closeDialog = () => {
  showDialog.value = false
  currentNote.value = null
  adminNote.value = ''
}

let refreshTimer = null

onMounted(() => {
  loadPendingNotes()
  // 每30秒自动刷新待审核笔记列表，保持当前页码
  refreshTimer = setInterval(() => {
    loadPendingNotes(true)
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
.pending-notes-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
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

.title-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.categories-cell {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.risk-low {
  background: #f6ffed;
  color: #52c41a;
}

.risk-medium {
  background: #fff7e6;
  color: #fa8c16;
}

.risk-high {
  background: #fff1f0;
  color: #ff4d4f;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-safe {
  background: #f6ffed;
  color: #52c41a;
}

.status-flagged {
  background: #fff7e6;
  color: #fa8c16;
}

.status-error {
  background: #fff1f0;
  color: #ff4d4f;
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
  margin-right: 8px;
}

.action-btn:hover {
  background: #007FFF;
  color: white;
}

.handle-btn {
  border-color: #52c41a;
  color: #52c41a;
}

.handle-btn:hover {
  background: #52c41a;
  color: white;
}

.review-btn {
  border-color: #722ed1;
  color: #722ed1;
}

.review-btn:hover {
  background: #722ed1;
  color: white;
}

.loading-cell,
.empty-cell {
  text-align: center;
  color: #999;
  padding: 40px;
}

.dialog-overlay {
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
}

.review-dialog-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 1000px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.review-dialog-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.review-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.review-info-section,
.review-result-section,
.highlight-section,
.content-section,
.review-actions-section {
  padding: 16px;
  background: #f8f9fa;
  border-radius: 6px;
}

.review-info-section h4,
.review-result-section h4,
.highlight-section h4,
.content-section h4,
.review-actions-section h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.info-item,
.result-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 8px;
}

.info-item label,
.result-item label {
  font-weight: 500;
  color: #666;
  min-width: 120px;
}

.result-warning {
  color: #ff4d4f;
  font-weight: 600;
}

.result-safe {
  color: #52c41a;
  font-weight: 600;
}

.sensitive-words-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sensitive-word-tag {
  padding: 4px 12px;
  background: #fff1f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  font-size: 13px;
}

.highlight-preview {
  width: 100%;
  min-height: 200px;
  max-height: 400px;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fafafa;
  font-size: 14px;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-y: auto;
  overflow-x: auto;
}

.highlight-preview :deep(.sensitive-highlight) {
  background: #ffebee;
  color: #c62828;
  padding: 2px 4px;
  border-radius: 3px;
  font-weight: 600;
  border-bottom: 2px solid #ff4d4f;
  box-shadow: 0 1px 2px rgba(255, 77, 79, 0.2);
}

.content-preview {
  width: 100%;
  min-height: 200px;
  max-height: 400px;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fafafa;
  font-size: 14px;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-y: auto;
}

.review-buttons {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.loading-section {
  text-align: center;
  padding: 40px;
  color: #666;
}

.dialog-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 800px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e9ecef;
}

.dialog-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.close-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.dialog-body {
  padding: 20px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e9ecef;
}

.detail-item {
  display: flex;
  margin-bottom: 12px;
  align-items: flex-start;
}

.detail-item label {
  font-weight: 500;
  color: #666;
  min-width: 100px;
}

.detail-item span {
  color: #333;
}

.file-link {
  color: #007FFF;
  text-decoration: none;
}

.file-link:hover {
  text-decoration: underline;
}

.findings-list {
  margin-top: 8px;
}

.finding-item {
  padding: 8px;
  background: #f8f9fa;
  border-radius: 4px;
  margin-bottom: 8px;
  font-size: 13px;
}

.handle-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e9ecef;
}

.handle-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.handle-section label {
  display: block;
  font-weight: 500;
  color: #666;
  margin-bottom: 8px;
}

.note-input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  margin-bottom: 12px;
}

.handle-buttons {
  display: flex;
  gap: 12px;
}

.submit-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.approve-btn {
  background: #52c41a;
  color: white;
}

.approve-btn:hover {
  background: #73d13d;
}

.reject-btn {
  background: #ff4d4f;
  color: white;
}

.reject-btn:hover {
  background: #ff7875;
}

/* 分页样式 */
.pagination-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
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
  padding: 6px 12px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #ccc;
  color: #333;
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
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-number:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #ccc;
  color: #333;
}

.page-number.active {
  background: #007FFF;
  border-color: #007FFF;
  color: white;
  font-weight: 500;
}

.page-number:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: transparent;
  border-color: transparent;
}
</style>
