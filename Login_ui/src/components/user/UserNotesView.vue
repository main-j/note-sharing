<template>
  <div class="user-notes-page">
    <section class="results-panel">
      <div class="page-header">
        <button class="back-button" @click="goBack">
          <svg class="back-icon" viewBox="0 0 16 16" fill="currentColor">
            <path fill-rule="evenodd" d="M15 8a.5.5 0 0 0-.5-.5H2.707l3.147-3.146a.5.5 0 1 0-.708-.708l-4 4a.5.5 0 0 0 0 .708l4 4a.5.5 0 0 0 .708-.708L2.707 8.5H14.5A.5.5 0 0 0 15 8z"/>
          </svg>
          <span>返回</span>
        </button>
        <h2 class="page-title">{{ pageTitle }}</h2>
      </div>

      <div v-if="loading" class="state-card">
        <span class="loader" aria-hidden="true"></span>
        <p>加载中...</p>
        <small>正在获取用户的公开笔记</small>
      </div>

      <div v-else-if="error" class="state-card error">
        <p>{{ error }}</p>
        <button class="retry-button" @click="loadUserNotes">重试</button>
      </div>

      <div v-else-if="notes.length > 0" class="results-list">
        <div class="results-header">
          <p class="results-count">找到 <strong>{{ notes.length }}</strong> 条公开笔记</p>
        </div>
        <!-- 笔记列表 -->
        <article
          v-for="note in notes"
          :key="note.noteId"
          class="result-card"
          @click="handleNoteClick(note)"
        >
          <div class="result-content">
            <h3 class="result-title">{{ note.title }}</h3>
            <p class="result-summary">{{ note.contentSummary || note.title }}</p>
            <div class="result-meta">
              <div class="meta-left">
                <span class="meta-author">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm2-3a2 2 0 11-4 0 2 2 0 014 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/>
                  </svg>
                  {{ note.authorName || '未知作者' }}
                </span>
                <span class="meta-time">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 3.5a.5.5 0 00-1 0V9a.5.5 0 00.252.434l3.5 2a.5.5 0 00.496-.868L8 8.71V3.5z"/>
                    <path d="M8 16A8 8 0 108 0a8 8 0 000 16zm7-8A7 7 0 111 8a7 7 0 0114 0z"/>
                  </svg>
                  {{ getDisplayTime(note) }}
                </span>
              </div>
              <div class="meta-right">
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 4a.5.5 0 01.5.5v3h3a.5.5 0 010 1h-3v3a.5.5 0 01-1 0v-3h-3a.5.5 0 010-1h3v-3A.5.5 0 018 4z"/>
                  </svg>
                  {{ note.viewCount || 0 }} 阅读
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 15A7 7 0 118 1a7 7 0 010 14zm0 1A8 8 0 108 0a8 8 0 000 16z"/>
                    <path d="M8 4a.5.5 0 00-.5.5v3h-3a.5.5 0 000 1h3v3a.5.5 0 001 0v-3h3a.5.5 0 000-1h-3v-3A.5.5 0 008 4z"/>
                  </svg>
                  {{ note.likeCount || 0 }} 点赞
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M2 2v13.5a.5.5 0 00.74.439L8 13.069l5.26 2.87A.5.5 0 0014 15.5V2a2 2 0 00-2-2H4a2 2 0 00-2 2z"/>
                  </svg>
                  {{ note.favoriteCount || 0 }} 收藏
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M2.5 1A1.5 1.5 0 001 2.5v11A1.5 1.5 0 002.5 15h6.086a1.5 1.5 0 001.06-.44l4.915-4.914A1.5 1.5 0 0015 7.586V2.5A1.5 1.5 0 0013.5 1h-11zM2 2.5a.5.5 0 01.5-.5h11a.5.5 0 01.5.5v7.086a.5.5 0 01-.146.353l-4.915 4.915a.5.5 0 01-.353.146H2.5a.5.5 0 01-.5-.5v-11z"/>
                    <path d="M5.5 6a.5.5 0 000 1h5a.5.5 0 000-1h-5zM5 8.5a.5.5 0 01.5-.5h5a.5.5 0 010 1h-5a.5.5 0 01-.5-.5zm0 2a.5.5 0 01.5-.5h2a.5.5 0 010 1h-2a.5.5 0 01-.5-.5z"/>
                  </svg>
                  {{ note.commentCount || 0 }} 评论
                </span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="state-card">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
        </svg>
        <p>该用户还没有公开笔记</p>
        <small>公开笔记会在发布后显示在这里</small>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import { searchNotesByAuthor, changeNoteStat, getFileUrlByNoteId } from '@/api/note'
import { formatTime } from '@/utils/time'
import service from '@/api/request'

const props = defineProps({
  userId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['open-note-detail'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)

const notes = ref([])
const loading = ref(false)
const error = ref('')
const authorName = ref('')
const VIEW_CACHE_PREFIX = 'note_view_ts'

const pageTitle = computed(() => {
  if (authorName.value) {
    return `${authorName.value} 的公开笔记`
  }
  return `用户 ${props.userId} 的公开笔记`
})

// 获取显示时间
const getDisplayTime = (note) => {
  if (!note) return '时间未知'
  const time = note.updatedAt || note.updated_at || note.createdAt || note.created_at
  if (time) {
    return formatTime(time) || '时间未知'
  }
  return note._timeLoading ? '加载中...' : '时间未知'
}

// 获取用户信息（包括用户名）
const getUserInfo = async (userId) => {
  try {
    // 通过用户ID获取用户信息
    const response = await service.get('/auth/user/by-id', {
      params: { userId }
    })
    // 后端返回的是直接的 Map 对象，不是 StandardResponse 格式
    // 检查响应格式
    if (response.data && typeof response.data === 'object') {
      // 如果返回的是 StandardResponse 格式，提取 data 字段
      if (response.data.code === 200 && response.data.data) {
        return response.data.data
      }
      // 如果直接返回用户信息对象
      if (response.data.id || response.data.username) {
        return response.data
      }
    }
    return null
  } catch (err) {
    console.error('获取用户信息失败:', err)
    // 如果API不存在或用户不存在，返回null
    return null
  }
}

// 加载用户的公开笔记
const loadUserNotes = async () => {
  if (!props.userId) {
    error.value = '用户ID无效'
    return
  }

  loading.value = true
  error.value = ''
  notes.value = []

  try {
    const currentUserId = userInfo.value?.id
    if (!currentUserId) {
      error.value = '请先登录'
      loading.value = false
      return
    }

    // 首先尝试获取用户信息（包括用户名）
    let userInfoData = null
    try {
      userInfoData = await getUserInfo(props.userId)
      if (userInfoData && userInfoData.username) {
        authorName.value = userInfoData.username
      }
    } catch (err) {
      console.warn('获取用户信息失败:', err)
    }

    // 如果无法获取用户名，显示错误
    if (!authorName.value) {
      error.value = '无法获取用户信息，请稍后重试'
      loading.value = false
      return
    }

    // 由于搜索API是基于关键词匹配标题和内容的，不支持按作者名精确搜索
    // 我们需要采用以下策略：
    // 1. 使用一个非常通用的关键词（如"的"）搜索尽可能多的笔记
    // 2. 然后通过authorName精确过滤出该作者的笔记
    
    let searchResults = []
    
    // 使用通用关键词"的"搜索，这是一个非常常见的字，应该能匹配到大部分笔记
    try {
      const allResults = await searchNotesByAuthor('的', currentUserId)
      // 通过authorName精确过滤出该作者的笔记
      searchResults = allResults.filter(note => {
        return note.authorName === authorName.value
      })
    } catch (err) {
      console.warn('搜索笔记失败:', err)
      // 如果搜索失败，可能是网络问题或服务问题
      error.value = '搜索失败，请稍后重试'
      loading.value = false
      return
    }

    notes.value = searchResults

    // 批量获取没有时间的笔记的时间信息
    if (notes.value.length > 0) {
      await fetchNoteTimes(notes.value)
    }
  } catch (err) {
    console.error('加载用户公开笔记失败:', err)
    error.value = err.message || err.response?.data?.message || '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 批量获取笔记时间信息
const fetchNoteTimes = async (noteList) => {
  if (!noteList || noteList.length === 0) return
  
  const notesWithoutTime = noteList.filter(note => 
    note.noteId && 
    !note.updatedAt && 
    !note.updated_at && 
    !note.createdAt && 
    !note.created_at &&
    !note._timeLoading
  )
  
  if (notesWithoutTime.length === 0) return
  
  notesWithoutTime.forEach(note => { note._timeLoading = true })
  
  const promises = notesWithoutTime.map(async (note) => {
    try {
      const noteInfo = await getFileUrlByNoteId(note.noteId)
      if (noteInfo) {
        note.updatedAt = noteInfo.updatedAt || noteInfo.createdAt
        note.createdAt = noteInfo.createdAt
      }
    } catch (err) {
      console.warn(`获取笔记 ${note.noteId} 时间失败:`, err)
    } finally {
      note._timeLoading = false
    }
  })
  
  await Promise.all(promises)
}

// 处理笔记点击
const handleNoteClick = async (note) => {
  if (!note || !note.noteId) {
    console.error('笔记数据无效:', note)
    return
  }

  try {
    const userId = userInfo.value?.id
    let latestStats = null

    if (userId) {
      try {
        if (canIncreaseView(note.noteId, userId)) {
          latestStats = await changeNoteStat(note.noteId, userId, 'views', 1)
          markViewIncreased(note.noteId, userId)
          if (latestStats?.views !== undefined) {
            note.viewCount = latestStats.views
            note.likeCount = latestStats.likes ?? note.likeCount
            note.favoriteCount = latestStats.favorites ?? note.favoriteCount
            note.commentCount = latestStats.comments ?? note.commentCount
            note.authorName = latestStats.authorName || note.authorName
          }
        }
      } catch (err) {
        console.error('增加阅读量失败:', err)
        note.viewCount = (note.viewCount || 0) + 1
      }
    }

    const statsPayload = {
      authorName: latestStats?.authorName ?? note.authorName,
      viewCount: latestStats?.views ?? note.viewCount ?? 0,
      likeCount: latestStats?.likes ?? note.likeCount ?? 0,
      favoriteCount: latestStats?.favorites ?? note.favoriteCount ?? 0,
      commentCount: latestStats?.comments ?? note.commentCount ?? 0
    }

    emit('open-note-detail', {
      noteId: note.noteId,
      title: note.title || '无标题',
      fileType: note.fileType,
      fromTab: 'user-notes',
      authorName: statsPayload.authorName,
      viewCount: statsPayload.viewCount,
      likeCount: statsPayload.likeCount,
      favoriteCount: statsPayload.favoriteCount,
      commentCount: statsPayload.commentCount
    })
    
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        tab: 'note-detail',
        noteId: note.noteId,
        title: note.title || undefined,
        fileType: note.fileType || undefined,
        fromTab: 'user-notes',
        userId: props.userId
      }
    })
  } catch (error) {
    console.error('打开笔记详情页失败:', error)
  }
}

// 阅读量节流相关函数
const getViewCacheKey = (noteId, userId) => {
  if (!noteId || !userId) return null
  return `${VIEW_CACHE_PREFIX}:${noteId}:${userId}`
}

const canIncreaseView = (noteId, userId) => {
  const key = getViewCacheKey(noteId, userId)
  if (!key) return false
  try {
    const ts = Number(localStorage.getItem(key) || 0)
    if (!ts) return true
    return Date.now() - ts >= 5 * 60 * 1000 // 5分钟节流
  } catch (err) {
    console.warn('读取本地阅读缓存失败:', err)
    return true
  }
}

const markViewIncreased = (noteId, userId) => {
  const key = getViewCacheKey(noteId, userId)
  if (!key) return
  try {
    localStorage.setItem(key, String(Date.now()))
  } catch (err) {
    console.warn('写入本地阅读缓存失败:', err)
  }
}

// 返回
const goBack = () => {
  router.back()
}

// 监听userId变化
watch(() => props.userId, () => {
  if (props.userId) {
    loadUserNotes()
  }
}, { immediate: true })

// 生命周期
onMounted(() => {
  if (props.userId) {
    loadUserNotes()
  }
})
</script>

<style scoped>
:global(:root) {
  --brand-primary: #007FFF;
  --surface-base: #ffffff;
  --surface-muted: #f6f6f6;
  --line-soft: #e2e2e2;
  --text-strong: #111c17;
  --text-secondary: #666;
  --text-muted: #999;
}

.user-notes-page {
  min-height: 100vh;
  padding: 20px 24px 100px;
  background: var(--surface-muted);
}

.results-panel {
  width: min(1200px, 100%);
  margin: 0 auto;
  background: var(--surface-base);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  min-height: 400px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line-soft);
}

.back-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.back-button:hover {
  background: var(--surface-muted);
  color: var(--brand-primary);
}

.back-icon {
  width: 16px;
  height: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-strong);
}

.results-header {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-soft);
}

.results-count {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.results-count strong {
  color: var(--brand-primary);
  font-weight: 600;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-card {
  padding: 20px;
  border-radius: 8px;
  border: 1px solid var(--line-soft);
  background: var(--surface-base);
  transition: border-color 0.2s, box-shadow 0.2s;
  cursor: pointer;
}

.result-card:hover {
  border-color: var(--brand-primary);
  box-shadow: 0 2px 8px rgba(0, 127, 255, 0.1);
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-strong);
  line-height: 1.5;
}

.result-summary {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.meta-left,
.meta-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-author,
.meta-time,
.meta-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.meta-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.state-card {
  border-radius: 8px;
  border: 1px dashed var(--line-soft);
  padding: 60px 24px;
  text-align: center;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.state-card.error {
  border-color: #c6534c;
  color: #c6534c;
}

.state-card p {
  margin: 0;
  font-size: 16px;
  color: var(--text-strong);
}

.state-card.error p {
  color: #c6534c;
}

.state-card small {
  color: var(--text-muted);
  font-size: 13px;
}

.loader {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 3px solid var(--line-soft);
  border-top-color: var(--brand-primary);
  animation: spin 1s linear infinite;
}

.empty-icon {
  width: 64px;
  height: 64px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.retry-button {
  padding: 10px 20px;
  border: 1px solid var(--line-soft);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 16px;
}

.retry-button:hover {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .user-notes-page {
    padding: 16px;
  }

  .results-panel {
    padding: 20px;
  }

  .result-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .meta-left,
  .meta-right {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
