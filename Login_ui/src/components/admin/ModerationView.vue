<template>
  <div class="moderation-view">
    <div class="view-header">
      <h2 class="page-title">内容审查管理</h2>
      <button class="refresh-btn" @click="loadModerations">刷新</button>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>审查ID</th>
            <th>内容类型</th>
            <th>内容ID</th>
            <th>触发原因</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="loading-cell">加载中...</td>
          </tr>
          <tr v-else-if="moderations.length === 0">
            <td colspan="7" class="empty-cell">暂无待审查内容</td>
          </tr>
          <tr v-else v-for="mod in moderations" :key="mod.id">
            <td>{{ mod.id }}</td>
            <td>{{ mod.contentType || mod.type || '-' }}</td>
            <td>{{ mod.contentId || mod.targetId || '-' }}</td>
            <td class="reason-cell">{{ mod.reason || '-' }}</td>
            <td>
              <span :class="['status-badge', getStatusClass(mod.status)]">
                {{ getStatusText(mod.status) }}
              </span>
            </td>
            <td>{{ formatTime(mod.createdAt || mod.createTime) }}</td>
            <td>
              <button class="action-btn" @click="viewDetail(mod)">查看详情</button>
              <button
                v-if="mod.status === 'PENDING'"
                class="action-btn handle-btn"
                @click="handleModeration(mod)"
              >
                处理
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 详情/处理对话框 -->
    <div v-if="showDialog" class="dialog-overlay" @click="closeDialog">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>审查详情</h3>
          <button class="close-btn" @click="closeDialog">×</button>
        </div>
        <div class="dialog-body">
          <div class="detail-item">
            <label>审查ID：</label>
            <span>{{ currentModeration?.id }}</span>
          </div>
          <div class="detail-item">
            <label>内容类型：</label>
            <span>{{ currentModeration?.contentType || currentModeration?.type }}</span>
          </div>
          <div class="detail-item">
            <label>内容ID：</label>
            <span>{{ currentModeration?.contentId || currentModeration?.targetId }}</span>
          </div>
          <div class="detail-item">
            <label>触发原因：</label>
            <span>{{ currentModeration?.reason || '-' }}</span>
          </div>
          <div class="detail-item">
            <label>状态：</label>
            <span :class="['status-badge', getStatusClass(currentModeration?.status)]">
              {{ getStatusText(currentModeration?.status) }}
            </span>
          </div>
          <div v-if="currentModeration?.status === 'PENDING'" class="handle-section">
            <label>管理员备注：</label>
            <textarea
              v-model="adminNote"
              class="note-input"
              placeholder="请输入处理备注..."
              rows="4"
            ></textarea>
            <button class="submit-btn" @click="submitHandle">提交处理</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getPendingModerations, handleModeration as handleModerationApi } from '../../api/admin'

const moderations = ref([])
const loading = ref(false)
const showDialog = ref(false)
const currentModeration = ref(null)
const adminNote = ref('')

const loadModerations = async () => {
  loading.value = true
  try {
    const res = await getPendingModerations()
    moderations.value = res?.data || res || []
  } catch (error) {
    console.error('加载审查记录失败:', error)
    moderations.value = []
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

const getStatusText = (status) => {
  const statusMap = {
    PENDING: '待处理',
    HANDLED: '已处理',
    REJECTED: '已拒绝'
  }
  return statusMap[status] || status || '-'
}

const getStatusClass = (status) => {
  const classMap = {
    PENDING: 'status-pending',
    HANDLED: 'status-handled',
    REJECTED: 'status-rejected'
  }
  return classMap[status] || ''
}

const viewDetail = (mod) => {
  currentModeration.value = mod
  adminNote.value = ''
  showDialog.value = true
}

const handleModeration = (mod) => {
  viewDetail(mod)
}

const submitHandle = async () => {
  if (!currentModeration.value) return
  
  try {
    await handleModerationApi(currentModeration.value.id, adminNote.value)
    alert('处理成功')
    closeDialog()
    loadModerations()
  } catch (error) {
    console.error('处理审查记录失败:', error)
    alert('处理失败：' + (error.response?.data?.message || error.message))
  }
}

const closeDialog = () => {
  showDialog.value = false
  currentModeration.value = null
  adminNote.value = ''
}

let refreshTimer = null

onMounted(() => {
  loadModerations()
  // 每20秒自动刷新待审查记录
  refreshTimer = setInterval(() => {
    loadModerations()
  }, 20000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.moderation-view {
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

.reason-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-pending {
  background: #fff7e6;
  color: #fa8c16;
}

.status-handled {
  background: #f6ffed;
  color: #52c41a;
}

.status-rejected {
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

.dialog-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
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

.detail-item {
  display: flex;
  margin-bottom: 16px;
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

.handle-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #e9ecef;
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

.submit-btn {
  padding: 10px 20px;
  border: none;
  background: #007FFF;
  color: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.submit-btn:hover {
  background: #006EDC;
}
</style>
