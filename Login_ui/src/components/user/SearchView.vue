<template>
  <div class="search-page">
    <section class="results-panel">
      <div v-if="loading" class="state-card">
        <span class="loader" aria-hidden="true"></span>
        <p>搜索中...</p>
        <small>正在为你定位匹配内容</small>
      </div>

      <div v-else-if="searchResults.length > 0" class="results-list">
        <div class="results-header">
          <p class="results-count">找到 <strong>{{ searchResults.length }}</strong> 条相关结果</p>
          <p class="search-keyword" v-if="searchQuery">搜索关键词：<strong>{{ searchQuery }}</strong></p>
        </div>
        <article
          v-for="result in searchResults"
          :key="result.noteId"
          class="result-card"
          @click="handleResultClick(result)"
        >
          <div class="result-content">
            <h3 class="result-title" v-html="highlightKeyword(result.title)"></h3>
            <p class="result-summary" v-html="highlightKeyword(result.contentSummary || result.title)"></p>
            <div class="result-meta">
              <div class="meta-left">
                <span class="meta-author">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm2-3a2 2 0 11-4 0 2 2 0 014 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/>
                  </svg>
                  {{ result.authorName || '未知作者' }}
                </span>
                <span class="meta-time">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 3.5a.5.5 0 00-1 0V9a.5.5 0 00.252.434l3.5 2a.5.5 0 00.496-.868L8 8.71V3.5z"/>
                    <path d="M8 16A8 8 0 108 0a8 8 0 000 16zm7-8A7 7 0 111 8a7 7 0 0114 0z"/>
                  </svg>
                  {{ getDisplayTime(result) }}
                </span>
              </div>
              <div class="meta-right">
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 4a.5.5 0 01.5.5v3h3a.5.5 0 010 1h-3v3a.5.5 0 01-1 0v-3h-3a.5.5 0 010-1h3v-3A.5.5 0 018 4z"/>
                  </svg>
                  {{ result.viewCount || 0 }} 阅读
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 15A7 7 0 118 1a7 7 0 010 14zm0 1A8 8 0 108 0a8 8 0 000 16z"/>
                    <path d="M8 4a.5.5 0 00-.5.5v3h-3a.5.5 0 000 1h3v3a.5.5 0 001 0v-3h3a.5.5 0 000-1h-3v-3A.5.5 0 008 4z"/>
                  </svg>
                  {{ result.likeCount || 0 }} 点赞
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M2 2v13.5a.5.5 0 00.74.439L8 13.069l5.26 2.87A.5.5 0 0014 15.5V2a2 2 0 00-2-2H4a2 2 0 00-2 2z"/>
                  </svg>
                  {{ result.favoriteCount || 0 }} 收藏
                </span>
                <span class="meta-stat">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M2.5 1A1.5 1.5 0 001 2.5v11A1.5 1.5 0 002.5 15h6.086a1.5 1.5 0 001.06-.44l4.915-4.914A1.5 1.5 0 0015 7.586V2.5A1.5 1.5 0 0013.5 1h-11zM2 2.5a.5.5 0 01.5-.5h11a.5.5 0 01.5.5v7.086a.5.5 0 01-.146.353l-4.915 4.915a.5.5 0 01-.353.146H2.5a.5.5 0 01-.5-.5v-11z"/>
                    <path d="M5.5 6a.5.5 0 000 1h5a.5.5 0 000-1h-5zM5 8.5a.5.5 0 01.5-.5h5a.5.5 0 010 1h-5a.5.5 0 01-.5-.5zm0 2a.5.5 0 01.5-.5h2a.5.5 0 010 1h-2a.5.5 0 01-.5-.5z"/>
                  </svg>
                  {{ result.commentCount || 0 }} 评论
                </span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <div v-else-if="hasSearched" class="state-card">
        <p>暂无搜索结果</p>
        <small>尝试更换关键词或缩短描述</small>
      </div>

      <div v-else class="state-card placeholder">
        <p>在上方输入关键词开始搜索</p>
        <small>支持搜索：笔记标题、笔记本名称、空间名称、标签、用户名</small>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { searchNotes, changeNoteStat, getFileUrlByNoteId } from '@/api/note'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/time'

const props = defineProps({
  initialKeyword: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['open-note-detail'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const searchQuery = ref('')
const searchResults = ref([])
const loading = ref(false)
const hasSearched = ref(false)
const VIEW_CACHE_PREFIX = 'note_view_ts'

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

// 获取显示时间
const getDisplayTime = (result) => {
  if (!result) return '时间未知'
  // 优先使用已获取的时间字段
  const time = result.updatedAt || result.updated_at || result.createdAt || result.created_at
  if (time) {
    return formatTime(time) || '时间未知'
  }
  // 如果时间字段为空，返回加载中状态（会在fetchNoteTimes中异步更新）
  return result._timeLoading ? '加载中...' : '时间未知'
}

// 批量获取笔记时间信息
const fetchNoteTimes = async (results) => {
  if (!results || results.length === 0) return
  
  // 找出没有时间的笔记
  const notesWithoutTime = results.filter(r => 
    r.noteId && 
    !r.updatedAt && 
    !r.updated_at && 
    !r.createdAt && 
    !r.created_at &&
    !r._timeLoading
  )
  
  if (notesWithoutTime.length === 0) return
  
  // 标记为加载中
  notesWithoutTime.forEach(r => { r._timeLoading = true })
  
  // 批量获取时间信息
  const promises = notesWithoutTime.map(async (result) => {
    try {
      const noteInfo = await getFileUrlByNoteId(result.noteId)
      if (noteInfo) {
        // 使用updatedAt，如果没有则使用createdAt
        result.updatedAt = noteInfo.updatedAt || noteInfo.createdAt
        result.createdAt = noteInfo.createdAt
      }
    } catch (err) {
      console.warn(`获取笔记 ${result.noteId} 时间失败:`, err)
    } finally {
      result._timeLoading = false
    }
  })
  
  await Promise.all(promises)
}

// 高亮关键词
const highlightKeyword = (text) => {
  if (!text || !searchQuery.value.trim()) return text
  const keyword = searchQuery.value.trim()
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark class="highlight">$1</mark>')
}

// 处理搜索结果点击 - 跳转到笔记详情页
const handleResultClick = async (result) => {
  if (!result || !result.noteId) {
    console.error('搜索结果数据无效:', result)
    return
  }
  
  try {
    const userId = userStore.userInfo?.id
    let latestStats = null

    if (userId) {
      try {
        if (canIncreaseView(result.noteId, userId)) {
          latestStats = await changeNoteStat(result.noteId, userId, 'views', 1)
          markViewIncreased(result.noteId, userId)
          if (latestStats?.views !== undefined) {
            result.viewCount = latestStats.views
            result.likeCount = latestStats.likes ?? result.likeCount
            result.favoriteCount = latestStats.favorites ?? result.favoriteCount
            result.commentCount = latestStats.comments ?? result.commentCount
            result.authorName = latestStats.authorName || result.authorName
          }
        } else {
          // 节流命中，不再增加阅读量
          latestStats = null
        }
      } catch (err) {
        console.error('增加阅读量失败:', err)
        result.viewCount = (result.viewCount || 0) + 1
      }
    } else {
      console.warn('用户未登录，跳过阅读量统计')
    }

    const statsPayload = {
      authorName: latestStats?.authorName ?? result.authorName,
      viewCount: latestStats?.views ?? result.viewCount ?? 0,
      likeCount: latestStats?.likes ?? result.likeCount ?? 0,
      favoriteCount: latestStats?.favorites ?? result.favoriteCount ?? 0,
      commentCount: latestStats?.comments ?? result.commentCount ?? 0
    }

    // 发出事件通知父组件（MainView）显示笔记详情页，传递统计信息
    const fileType = result.fileType

    emit('open-note-detail', {
      noteId: result.noteId,
      title: result.title || '无标题',
      fileType, // 传后端返回的类型，未返回时让详情页自行判断
      // 传递统计信息
      authorName: statsPayload.authorName,
      viewCount: statsPayload.viewCount,
      likeCount: statsPayload.likeCount,
      favoriteCount: statsPayload.favoriteCount,
      commentCount: statsPayload.commentCount
    })
    
    // 更新URL参数，记录当前查看的笔记
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        tab: 'note-detail',
        noteId: result.noteId,
        title: result.title || undefined,
        fileType: fileType || undefined
      }
    })
  } catch (error) {
    console.error('打开笔记详情页失败:', error)
  }
}

// 执行搜索
const handleSearch = async () => {
  const keyword = searchQuery.value.trim()
  if (!keyword) return
  
  loading.value = true
  hasSearched.value = true
  
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      console.error('用户未登录')
      loading.value = false
      return
    }
    
    const results = await searchNotes(keyword, userId)
    searchResults.value = results || []
    
    // 批量获取没有时间的笔记的时间信息
    if (searchResults.value.length > 0) {
      await fetchNoteTimes(searchResults.value)
    }
    
    // 更新URL中的关键词
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        keyword: keyword
      }
    })
  } catch (error) {
    console.error('搜索失败:', error)
    searchResults.value = []
  } finally {
    loading.value = false
  }
}

// 监听initialKeyword变化
watch(() => props.initialKeyword, (newKeyword) => {
  if (newKeyword) {
    searchQuery.value = newKeyword
    handleSearch()
  }
}, { immediate: true })

// 监听路由中的关键词
watch(() => route.query.keyword, (newKeyword) => {
  if (newKeyword && newKeyword !== searchQuery.value) {
    searchQuery.value = newKeyword
    if (!hasSearched.value) {
      handleSearch()
    }
  }
})

// 组件挂载时，如果有初始关键词则执行搜索
onMounted(() => {
  if (props.initialKeyword) {
    searchQuery.value = props.initialKeyword
    handleSearch()
  } else if (route.query.keyword) {
    searchQuery.value = route.query.keyword
    handleSearch()
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
  --highlight-bg: #fff3cd;
}

.search-page {
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
}

.results-panel {
  min-height: 400px;
}

.results-header {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-soft);
}

.results-count {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.results-count strong {
  color: var(--brand-primary);
  font-weight: 600;
}

.search-keyword {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.search-keyword strong {
  color: var(--text-strong);
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

.result-title :deep(.highlight) {
  background: var(--highlight-bg);
  padding: 2px 4px;
  border-radius: 3px;
  font-weight: 700;
  color: var(--brand-primary);
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

.result-summary :deep(.highlight) {
  background: var(--highlight-bg);
  padding: 2px 4px;
  border-radius: 3px;
  font-weight: 600;
  color: var(--brand-primary);
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

.state-card p {
  margin: 0;
  font-size: 16px;
  color: var(--text-strong);
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

.placeholder small {
  max-width: 400px;
  line-height: 1.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .search-page {
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
