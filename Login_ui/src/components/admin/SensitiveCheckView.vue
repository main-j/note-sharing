<template>
  <div class="sensitive-check-view">
    <h2 class="page-title">敏感词检查</h2>
    
    <div class="tabs-container">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="['tab-btn', { active: currentTab === tab.value }]"
        @click="currentTab = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 文本检查 -->
    <div v-if="currentTab === 'text'" class="check-section">
      <div class="input-section">
        <label>输入待检查文本：</label>
        <textarea
          v-model="checkText"
          class="text-input"
          placeholder="请输入要检查的文本..."
          rows="6"
        ></textarea>
        <div class="button-group">
          <button 
            class="primary-btn" 
            :disabled="loading"
            @click="handleFastFilter"
          >
            {{ loading && loadingType === 'fast' ? '检查中...' : '快速过滤' }}
          </button>
          <button 
            class="primary-btn" 
            :disabled="loading"
            @click="handleDeepCheck"
          >
            {{ loading && loadingType === 'deep' ? '检查中...' : '深度检查' }}
          </button>
        </div>
      </div>
      
      <!-- 高亮预览区域 -->
      <div v-if="checkResult && checkText && highlightedText && (checkResult.hasSensitiveWords || checkResult.hasSensitive)" class="highlight-section">
        <label>高亮预览：</label>
        <div 
          class="highlight-preview" 
          v-html="highlightedText"
        ></div>
      </div>
      
      <div v-if="checkResult" class="result-section">
        <h3>检查结果：</h3>
        <div class="result-content">
          <div v-if="checkResult.hasSensitiveWords || checkResult.hasSensitive" class="result-warning">
            ⚠️ 检测到敏感词
          </div>
          <div v-else class="result-safe">
            ✅ 未检测到敏感词
          </div>
          <div v-if="sensitiveWordsList && sensitiveWordsList.length > 0" class="sensitive-words">
            <strong>敏感词：</strong>
            <span v-for="word in sensitiveWordsList" :key="word" class="sensitive-word">
              {{ word }}
            </span>
          </div>
          <div v-if="checkResult.hitCount !== undefined" class="hit-count">
            共检测到 {{ checkResult.hitCount }} 个敏感词
          </div>
        </div>
      </div>
    </div>

    <!-- 笔记检查 -->
    <div v-if="currentTab === 'note'" class="check-section">
      <div class="input-section">
        <label>笔记ID：</label>
        <input
          v-model.number="noteId"
          type="number"
          class="number-input"
          placeholder="请输入笔记ID"
        />
        <div class="button-group">
          <button 
            class="primary-btn" 
            :disabled="noteLoading"
            @click="handleCheckNote"
          >
            {{ noteLoading && noteLoadingType === 'quick' ? '检查中...' : '快速检查' }}
          </button>
          <button 
            class="primary-btn" 
            :disabled="noteLoading"
            @click="handleCheckNoteFull"
          >
            {{ noteLoading && noteLoadingType === 'full' ? '检查中...' : '全文检查' }}
          </button>
          <button 
            class="primary-btn deep-check-btn" 
            :disabled="noteLoading"
            @click="handleCheckNoteDeep"
          >
            {{ noteLoading && noteLoadingType === 'deep' ? '检查中...' : '深度检查' }}
          </button>
        </div>
      </div>
      
      <!-- 高亮预览区域 -->
      <div v-if="noteCheckResult && noteContent && noteHighlightedText && (noteCheckResult.hasSensitiveWords || noteCheckResult.status === 'FLAGGED' || noteCheckResult.findings?.length > 0)" class="highlight-section">
        <label>高亮预览：</label>
        <div 
          class="highlight-preview" 
          v-html="noteHighlightedText"
        ></div>
      </div>
      
      <div v-if="noteCheckResult" class="result-section">
        <h3>检查结果：</h3>
        <div class="result-content">
          <div v-if="noteCheckResult.hasSensitiveWords || noteCheckResult.status === 'FLAGGED' || noteCheckResult.findings?.length > 0" class="result-warning">
            ⚠️ 检测到敏感词
          </div>
          <div v-else-if="noteCheckResult.status === 'SAFE' || (!noteCheckResult.hasSensitiveWords && !noteCheckResult.findings?.length)" class="result-safe">
            ✅ 未检测到敏感词
          </div>
          <div v-if="uniqueNoteSensitiveWords && uniqueNoteSensitiveWords.length > 0" class="sensitive-words">
            <strong>敏感词：</strong>
            <span v-for="(word, index) in uniqueNoteSensitiveWords" :key="index" class="sensitive-word">
              {{ word }}
            </span>
          </div>
          <div v-if="noteCheckResult.hitCount !== undefined" class="hit-count">
            共检测到 {{ noteCheckResult.hitCount }} 个敏感词
          </div>
          <div v-if="noteCheckResult.riskLevel" class="risk-level">
            风险等级：<span :class="['risk-badge', `risk-${noteCheckResult.riskLevel.toLowerCase()}`]">{{ noteCheckResult.riskLevel }}</span>
          </div>
          <div v-if="noteCheckResult.score !== undefined" class="score-info">
            风险评分：{{ noteCheckResult.score.toFixed(2) }} / 100
          </div>
        </div>
      </div>
    </div>

    <!-- 词库管理 -->
    <div v-if="currentTab === 'wordlib'" class="check-section">
      <div class="wordlib-section">
        <h3>敏感词库管理</h3>
        <div class="button-group">
          <button class="primary-btn" @click="handleReloadWords">重新加载快速过滤词库</button>
          <button class="primary-btn" @click="handleReloadDeepWords">重新加载深度检查词库</button>
        </div>
        <div v-if="reloadMessage" class="reload-message">
          {{ reloadMessage }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  fastFilterText,
  deepCheckText,
  checkNoteSensitive,
  checkNoteSensitiveFull,
  reloadSensitiveWords,
  reloadDeepCheckWords
} from '../../api/admin'

const tabs = [
  { value: 'text', label: '文本检查' },
  { value: 'note', label: '笔记检查' },
  { value: 'wordlib', label: '词库管理' }
]

const currentTab = ref('text')
const checkText = ref('')
const noteId = ref(null)
const checkResult = ref(null)
const noteCheckResult = ref(null)
const noteContent = ref('') // 存储笔记的文本内容
const reloadMessage = ref('')
const loading = ref(false)
const loadingType = ref(null) // 'fast' 或 'deep'
const noteLoading = ref(false) // 笔记检查加载状态
const noteLoadingType = ref(null) // 'quick', 'full', 或 'deep'

// 计算敏感词列表（兼容不同的返回格式）
const sensitiveWordsList = computed(() => {
  if (!checkResult.value) return []
  
  // 优先使用 sensitiveWords
  if (checkResult.value.sensitiveWords) {
    return Array.isArray(checkResult.value.sensitiveWords) 
      ? checkResult.value.sensitiveWords 
      : []
  }
  
  // 快速过滤返回的是 hits
  if (checkResult.value.hits) {
    return Array.isArray(checkResult.value.hits) 
      ? checkResult.value.hits 
      : []
  }
  
  // 深度检查返回的是 uniqueHits (Set)
  if (checkResult.value.uniqueHits) {
    if (Array.isArray(checkResult.value.uniqueHits)) {
      return checkResult.value.uniqueHits
    }
    // 如果是 Set 对象，转换为数组
    if (checkResult.value.uniqueHits instanceof Set || 
        typeof checkResult.value.uniqueHits === 'object') {
      return Array.from(checkResult.value.uniqueHits)
    }
  }
  
  return []
})

// 计算高亮文本
const highlightedText = computed(() => {
  if (!checkResult.value || !checkText.value) return ''
  
  // 如果没有检测到敏感词，不显示高亮
  if (!checkResult.value.hasSensitiveWords && !checkResult.value.hasSensitive) {
    return ''
  }
  
  let text = checkText.value
  const ranges = []
  
  // 深度检查：使用 findings 中的位置信息（更准确）
  if (checkResult.value.findings && Array.isArray(checkResult.value.findings)) {
    checkResult.value.findings.forEach((finding) => {
      if (finding.startOffset !== undefined && finding.endOffset !== undefined) {
        // 确保位置在文本范围内
        const start = Math.max(0, Math.min(finding.startOffset, text.length))
        const end = Math.max(start, Math.min(finding.endOffset, text.length))
        if (start < end) {
          ranges.push({
            start: start,
            end: end,
            term: finding.term || ''
          })
        }
      }
    })
  } else {
    // 快速过滤：手动查找敏感词位置
    const words = sensitiveWordsList.value
    words.forEach((word) => {
      if (!word || typeof word !== 'string') return
      
      // 转义特殊字符用于正则表达式
      const escapedWord = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      try {
        const regex = new RegExp(escapedWord, 'gi')
        let match
        // 重置 lastIndex 避免无限循环
        regex.lastIndex = 0
        while ((match = regex.exec(text)) !== null) {
          ranges.push({
            start: match.index,
            end: match.index + match[0].length,
            term: match[0]
          })
          // 避免零长度匹配导致无限循环
          if (match[0].length === 0) {
            regex.lastIndex++
          }
        }
      } catch (e) {
        console.warn('正则表达式错误:', e, word)
      }
    })
  }
  
  if (ranges.length === 0) return ''
  
  // 按位置排序并去重（处理重叠的情况）
  ranges.sort((a, b) => a.start - b.start)
  const mergedRanges = []
  for (let i = 0; i < ranges.length; i++) {
    if (mergedRanges.length === 0 || ranges[i].start >= mergedRanges[mergedRanges.length - 1].end) {
      mergedRanges.push({ ...ranges[i] })
    } else {
      // 合并重叠的范围
      const last = mergedRanges[mergedRanges.length - 1]
      last.end = Math.max(last.end, ranges[i].end)
    }
  }
  
  // 从后往前插入高亮标签，避免位置偏移
  let highlighted = text
  for (let i = mergedRanges.length - 1; i >= 0; i--) {
    const range = mergedRanges[i]
    // 再次确保范围有效
    if (range.start >= 0 && range.end <= highlighted.length && range.start < range.end) {
      const before = highlighted.substring(0, range.start)
      const match = highlighted.substring(range.start, range.end)
      const after = highlighted.substring(range.end)
      highlighted = before + `<mark class="sensitive-highlight">${escapeHtml(match)}</mark>` + after
    }
  }
  
  // 将换行符转换为 <br>
  highlighted = highlighted.replace(/\n/g, '<br>')
  
  return highlighted
})

// HTML转义函数
const escapeHtml = (text) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 计算笔记去重后的敏感词列表（用于显示）
const uniqueNoteSensitiveWords = computed(() => {
  if (!noteCheckResult.value) return []
  
  const uniqueWords = new Set()
  
  // 优先使用 uniqueHits（深度检查返回的去重集合）
  if (noteCheckResult.value.uniqueHits) {
    if (Array.isArray(noteCheckResult.value.uniqueHits)) {
      noteCheckResult.value.uniqueHits.forEach(word => {
        if (word) uniqueWords.add(word)
      })
    } else if (noteCheckResult.value.uniqueHits instanceof Set || 
               typeof noteCheckResult.value.uniqueHits === 'object') {
      Array.from(noteCheckResult.value.uniqueHits).forEach(word => {
        if (word) uniqueWords.add(word)
      })
    }
  }
  
  // 从 findings 中提取敏感词并去重
  if (noteCheckResult.value.findings && Array.isArray(noteCheckResult.value.findings)) {
    noteCheckResult.value.findings.forEach((finding) => {
      const term = finding.term || finding
      if (term && typeof term === 'string') {
        uniqueWords.add(term)
      }
    })
  }
  
  // 如果都没有，返回空数组
  return Array.from(uniqueWords).sort()
})

// 计算笔记高亮文本（兼容LLM检查和深度检查两种结果格式）
const noteHighlightedText = computed(() => {
  if (!noteCheckResult.value || !noteContent.value) return ''
  
  let text = noteContent.value
  const ranges = []
  
  // 深度检查结果格式：{ hasSensitiveWords, findings, uniqueHits, hitCount }
  if (noteCheckResult.value.findings && Array.isArray(noteCheckResult.value.findings)) {
    noteCheckResult.value.findings.forEach((finding) => {
      if (finding.startOffset !== undefined && finding.endOffset !== undefined) {
        // 确保位置在文本范围内
        const start = Math.max(0, Math.min(finding.startOffset, text.length))
        const end = Math.max(start, Math.min(finding.endOffset, text.length))
        if (start < end) {
          ranges.push({
            start: start,
            end: end,
            term: finding.term || ''
          })
        }
      }
    })
  }
  
  // 如果没有检测到敏感词，不显示高亮
  const hasSensitive = noteCheckResult.value.hasSensitiveWords || 
                       noteCheckResult.value.status === 'FLAGGED' ||
                       ranges.length > 0
  
  if (!hasSensitive) {
    return ''
  }
  
  if (ranges.length === 0) return ''
  
  // 按位置排序并去重（处理重叠的情况）
  ranges.sort((a, b) => a.start - b.start)
  const mergedRanges = []
  for (let i = 0; i < ranges.length; i++) {
    if (mergedRanges.length === 0 || ranges[i].start >= mergedRanges[mergedRanges.length - 1].end) {
      mergedRanges.push({ ...ranges[i] })
    } else {
      // 合并重叠的范围
      const last = mergedRanges[mergedRanges.length - 1]
      last.end = Math.max(last.end, ranges[i].end)
    }
  }
  
  // 从后往前插入高亮标签，避免位置偏移
  let highlighted = text
  for (let i = mergedRanges.length - 1; i >= 0; i--) {
    const range = mergedRanges[i]
    // 再次确保范围有效
    if (range.start >= 0 && range.end <= highlighted.length && range.start < range.end) {
      const before = highlighted.substring(0, range.start)
      const match = highlighted.substring(range.start, range.end)
      const after = highlighted.substring(range.end)
      highlighted = before + `<mark class="sensitive-highlight">${escapeHtml(match)}</mark>` + after
    }
  }
  
  // 将换行符转换为 <br>
  highlighted = highlighted.replace(/\n/g, '<br>')
  
  return highlighted
})

const handleFastFilter = async () => {
  if (!checkText.value.trim()) {
    alert('请输入要检查的文本')
    return
  }
  
  loading.value = true
  loadingType.value = 'fast'
  checkResult.value = null // 清空之前的结果，确保状态刷新
  
  try {
    const res = await fastFilterText(checkText.value)
    // 后端返回的数据结构：{ hasSensitiveWords, hits, hitCount }
    checkResult.value = res?.data || res
    console.log('快速过滤结果:', checkResult.value)
  } catch (error) {
    console.error('快速过滤失败:', error)
    alert('检查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    checkResult.value = null
  } finally {
    loading.value = false
    loadingType.value = null
  }
}

const handleDeepCheck = async () => {
  if (!checkText.value.trim()) {
    alert('请输入要检查的文本')
    return
  }
  
  loading.value = true
  loadingType.value = 'deep'
  checkResult.value = null // 清空之前的结果，确保状态刷新
  
  try {
    const res = await deepCheckText(checkText.value)
    // 后端返回的数据结构：{ hasSensitiveWords, uniqueHits, hitCount, findings }
    checkResult.value = res?.data || res
    console.log('深度检查结果:', checkResult.value)
  } catch (error) {
    console.error('深度检查失败:', error)
    alert('检查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    checkResult.value = null
  } finally {
    loading.value = false
    loadingType.value = null
  }
}

// 获取笔记文本内容
const fetchNoteContent = async (noteId) => {
  try {
    // 导入笔记API
    const { getFileUrlByNoteId } = await import('../../api/note')
    
    // 获取笔记信息
    const noteInfo = await getFileUrlByNoteId(noteId)
    if (!noteInfo || !noteInfo.url) {
      throw new Error('无法获取笔记信息')
    }
    
    // 只支持Markdown文件的高亮（PDF需要特殊处理）
    if (noteInfo.fileType === 'md') {
      const response = await fetch(noteInfo.url, {
        method: 'GET',
        cache: 'no-cache'
      })
      if (response.ok) {
        const text = await response.text()
        return text
      } else {
        throw new Error('无法加载笔记内容')
      }
    } else if (noteInfo.fileType === 'pdf') {
      // PDF文件暂时不支持高亮，返回提示信息
      return null
    } else {
      throw new Error('不支持的文件类型')
    }
  } catch (error) {
    console.error('获取笔记内容失败:', error)
    throw error
  }
}

const handleCheckNote = async () => {
  if (!noteId.value) {
    alert('请输入笔记ID')
    return
  }
  
  noteLoading.value = true
  noteLoadingType.value = 'quick'
  noteCheckResult.value = null
  noteContent.value = ''
  
  try {
    // 并行获取检查结果和笔记内容
    const [res, content] = await Promise.allSettled([
      checkNoteSensitive(noteId.value),
      fetchNoteContent(noteId.value)
    ])
    
    // 处理检查结果
    if (res.status === 'fulfilled') {
      noteCheckResult.value = res.value?.data || res.value
      console.log('笔记检查结果:', noteCheckResult.value)
    } else {
      throw res.reason
    }
    
    // 处理笔记内容
    if (content.status === 'fulfilled' && content.value) {
      noteContent.value = content.value
    } else if (content.status === 'rejected') {
      console.warn('获取笔记内容失败，将无法显示高亮:', content.reason)
      // 不阻止检查结果的显示，只是没有高亮
    }
  } catch (error) {
    console.error('笔记检查失败:', error)
    alert('检查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    noteCheckResult.value = null
  } finally {
    noteLoading.value = false
    noteLoadingType.value = null
  }
}

const handleCheckNoteFull = async () => {
  if (!noteId.value) {
    alert('请输入笔记ID')
    return
  }
  
  noteLoading.value = true
  noteLoadingType.value = 'full'
  noteCheckResult.value = null
  noteContent.value = ''
  
  try {
    // 并行获取检查结果和笔记内容
    const [res, content] = await Promise.allSettled([
      checkNoteSensitiveFull(noteId.value),
      fetchNoteContent(noteId.value)
    ])
    
    // 处理检查结果
    if (res.status === 'fulfilled') {
      noteCheckResult.value = res.value?.data || res.value
      console.log('笔记全文检查结果:', noteCheckResult.value)
    } else {
      throw res.reason
    }
    
    // 处理笔记内容
    if (content.status === 'fulfilled' && content.value) {
      noteContent.value = content.value
    } else if (content.status === 'rejected') {
      console.warn('获取笔记内容失败，将无法显示高亮:', content.reason)
      // 不阻止检查结果的显示，只是没有高亮
    }
  } catch (error) {
    console.error('笔记全文检查失败:', error)
    alert('检查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    noteCheckResult.value = null
  } finally {
    noteLoading.value = false
    noteLoadingType.value = null
  }
}

// 笔记深度检查（使用深度检查词库）
const handleCheckNoteDeep = async () => {
  if (!noteId.value) {
    alert('请输入笔记ID')
    return
  }
  
  noteLoading.value = true
  noteLoadingType.value = 'deep'
  noteCheckResult.value = null
  noteContent.value = ''
  
  try {
    // 先获取笔记内容
    const content = await fetchNoteContent(noteId.value)
    if (!content) {
      throw new Error('无法获取笔记内容，请确保笔记是Markdown格式')
    }
    
    noteContent.value = content
    
    // 使用深度检查API检查笔记内容
    const res = await deepCheckText(content)
    // 深度检查返回的数据结构：{ hasSensitiveWords, uniqueHits, hitCount, findings }
    noteCheckResult.value = res?.data || res
    console.log('笔记深度检查结果:', noteCheckResult.value)
  } catch (error) {
    console.error('笔记深度检查失败:', error)
    alert('检查失败：' + (error.response?.data?.message || error.message || '未知错误'))
    noteCheckResult.value = null
    noteContent.value = ''
  } finally {
    noteLoading.value = false
    noteLoadingType.value = null
  }
}

const handleReloadWords = async () => {
  try {
    const res = await reloadSensitiveWords()
    reloadMessage.value = res?.data?.message || res?.message || '词库已重新加载'
    setTimeout(() => {
      reloadMessage.value = ''
    }, 3000)
  } catch (error) {
    console.error('重新加载词库失败:', error)
    alert('重新加载失败：' + (error.response?.data?.message || error.message))
  }
}

const handleReloadDeepWords = async () => {
  try {
    const res = await reloadDeepCheckWords()
    reloadMessage.value = res?.data?.message || res?.message || '深度检查词库已重新加载'
    setTimeout(() => {
      reloadMessage.value = ''
    }, 3000)
  } catch (error) {
    console.error('重新加载深度检查词库失败:', error)
    alert('重新加载失败：' + (error.response?.data?.message || error.message))
  }
}
</script>

<style scoped>
.sensitive-check-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
}

.tabs-container {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  border-bottom: 2px solid #e9ecef;
}

.tab-btn {
  padding: 12px 24px;
  border: none;
  background: transparent;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #007FFF;
}

.tab-btn.active {
  color: #007FFF;
  border-bottom-color: #007FFF;
  font-weight: 600;
}

.check-section {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.input-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-section label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.text-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
}

.highlight-section {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.highlight-section label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.highlight-preview {
  width: 100%;
  min-height: 120px;
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

.number-input {
  width: 200px;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
}

.button-group {
  display: flex;
  gap: 12px;
}

.primary-btn {
  padding: 10px 20px;
  border: none;
  background: #007FFF;
  color: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.primary-btn:hover:not(:disabled) {
  background: #006EDC;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.deep-check-btn {
  background: #722ed1;
}

.deep-check-btn:hover:not(:disabled) {
  background: #5b1fa8;
}

.result-section {
  margin-top: 24px;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 6px;
}

.result-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-warning {
  color: #ff4d4f;
  font-weight: 600;
}

.result-safe {
  color: #52c41a;
  font-weight: 600;
}

.sensitive-words {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.sensitive-word {
  padding: 4px 12px;
  background: #fff1f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  font-size: 13px;
}

.hit-count {
  font-size: 14px;
  color: #666;
  margin-top: 8px;
}

.risk-level {
  font-size: 14px;
  color: #666;
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-badge {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
}

.risk-low {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

.risk-medium {
  background: #fff7e6;
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.risk-high {
  background: #fff1f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}

.score-info {
  font-size: 14px;
  color: #666;
  margin-top: 8px;
}

.wordlib-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wordlib-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.reload-message {
  padding: 12px;
  background: #f0f7ff;
  color: #007FFF;
  border-radius: 6px;
  font-size: 14px;
}
</style>
