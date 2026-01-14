<template>
  <div class="moderation-view">
    <div class="view-header">
      <h2 class="page-title">内容审查管理</h2>
      <div class="header-actions-group">
        <div class="tab-switcher">
          <button 
            :class="['tab-btn', { active: activeTab === 'records' }]"
            @click="activeTab = 'records'"
          >
            审查记录
          </button>
          <button 
            :class="['tab-btn', { active: activeTab === 'notes' }]"
            @click="activeTab = 'notes'"
          >
            待审核笔记
          </button>
        </div>
        <button class="refresh-btn" @click="handleRefresh">刷新</button>
      </div>
    </div>

    <!-- 审查记录标签页 -->
    <div v-if="activeTab === 'records'" class="tab-content">
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
              <td colspan="7" class="empty-cell">暂无审查记录</td>
            </tr>
            <tr v-else v-for="mod in moderations" :key="mod.id">
              <td>{{ mod.id }}</td>
              <td>NOTE</td>
              <td>{{ mod.noteId || '-' }}</td>
              <td class="reason-cell">{{ getTriggerReason(mod) }}</td>
              <td>
                <span :class="['status-badge', getStatusClass(mod.status, mod.isHandled)]">
                  {{ getStatusText(mod.status, mod.isHandled) }}
                </span>
              </td>
              <td>{{ formatTime(mod.createdAt) }}</td>
              <td>
                <button class="action-btn" @click="viewDetail(mod)">查看详情</button>
                <button
                  v-if="!mod.isHandled && mod.status === 'FLAGGED'"
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
    </div>

    <!-- 待审核笔记标签页 -->
    <div v-if="activeTab === 'notes'" class="tab-content">
      <PendingNotesView />
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
            <span>NOTE</span>
          </div>
          <div class="detail-item">
            <label>内容ID：</label>
            <span>{{ currentModeration?.noteId || '-' }}</span>
          </div>
          <div class="detail-item">
            <label>笔记标题：</label>
            <span>{{ currentModeration?.noteTitle || '-' }}</span>
          </div>
          <div class="detail-item">
            <label>风险等级：</label>
            <span>{{ getRiskLevelText(currentModeration?.riskLevel) }}</span>
          </div>
          <div class="detail-item">
            <label>风险评分：</label>
            <span>{{ currentModeration?.score || '-' }}</span>
          </div>
          <div class="detail-item">
            <label>违规类别：</label>
            <span>{{ formatCategories(currentModeration?.categories) }}</span>
          </div>
          <div class="detail-item">
            <label>状态：</label>
            <span :class="['status-badge', getStatusClass(currentModeration?.status, currentModeration?.isHandled)]">
              {{ getStatusText(currentModeration?.status, currentModeration?.isHandled) }}
            </span>
          </div>
          <div class="detail-item">
            <label>创建时间：</label>
            <span>{{ formatTime(currentModeration?.createdAt) }}</span>
          </div>
          <div v-if="currentModeration?.adminComment" class="detail-item">
            <label>管理员备注：</label>
            <span>{{ currentModeration.adminComment }}</span>
          </div>
          <div v-if="!currentModeration?.isHandled && currentModeration?.status === 'FLAGGED'" class="handle-section">
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
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { getPendingModerations, handleModeration as handleModerationApi } from '../../api/admin'
import PendingNotesView from './PendingNotesView.vue'

const activeTab = ref('records')
const moderations = ref([])
const loading = ref(false)
const showDialog = ref(false)
const currentModeration = ref(null)
const adminNote = ref('')

const handleRefresh = () => {
  if (activeTab.value === 'records') {
    loadModerations()
  }
  // 待审核笔记的刷新由 PendingNotesView 组件自己处理
}

const loadModerations = async () => {
  loading.value = true
  try {
    const res = await getPendingModerations()
    // StandardResponse结构：{ code, message, data }
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

const getStatusText = (status, isHandled) => {
  if (isHandled) {
    return '已处理'
  }
  const statusMap = {
    FLAGGED: '待处理',
    SAFE: '安全',
    ERROR: '错误'
  }
  return statusMap[status] || status || '-'
}

const getStatusClass = (status, isHandled) => {
  if (isHandled) {
    return 'status-handled'
  }
  const classMap = {
    FLAGGED: 'status-pending',
    SAFE: 'status-safe',
    ERROR: 'status-error'
  }
  return classMap[status] || ''
}

const getRiskLevelText = (riskLevel) => {
  const levelMap = {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高'
  }
  return levelMap[riskLevel] || riskLevel || '-'
}

const formatCategories = (categories) => {
  if (!categories || categories.length === 0) {
    return '-'
  }
  return categories.join('、')
}

const getTriggerReason = (mod) => {
  if (mod.categories && mod.categories.length > 0) {
    return mod.categories.join('、')
  }
  if (mod.riskLevel) {
    return `风险等级：${getRiskLevelText(mod.riskLevel)}`
  }
  return '-'
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
    await handleModerationApi(
      currentModeration.value.id, 
      adminNote.value || '已处理',
      true
    )
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

.header-actions-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.tab-switcher {
  display: flex;
  gap: 8px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 6px;
}

.tab-btn {
  padding: 6px 16px;
  border: none;
  background: transparent;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #333;
  background: rgba(255, 255, 255, 0.5);
}

.tab-btn.active {
  background: var(--surface-soft);
  color: var(--brand-primary);
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.tab-content {
  margin-top: 20px;
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

.status-safe {
  background: #f6ffed;
  color: #52c41a;
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
