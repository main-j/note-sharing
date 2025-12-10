<template>
  <div class="qa-detail-page">
    <div v-if="loading" class="loading-state">
      <div class="loader"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <button class="back-button" @click="goBack">返回</button>
    </div>

    <div v-else-if="question" class="detail-layout">
      <div class="detail-container">
        <!-- 返回按钮 -->
        <div class="back-header">
          <button class="back-link-button" @click="goBack">
            <svg class="back-icon" viewBox="0 0 16 16" fill="currentColor">
              <path fill-rule="evenodd" d="M15 8a.5.5 0 0 0-.5-.5H2.707l3.147-3.146a.5.5 0 1 0-.708-.708l-4 4a.5.5 0 0 0 0 .708l4 4a.5.5 0 0 0 .708-.708L2.707 8.5H14.5A.5.5 0 0 0 15 8z"/>
            </svg>
            <span>返回</span>
          </button>
        </div>

        <!-- 问题详情 -->
        <div class="question-section">
          <h1 class="question-title">{{ question.title }}</h1>
          <div class="question-meta">
            <span class="meta-item">
              <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm2-3a2 2 0 11-4 0 2 2 0 014 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/>
              </svg>
              提问者 #{{ question.authorId }}
            </span>
            <span class="meta-item">
              <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 3.5a.5.5 0 00-1 0V9a.5.5 0 00.252.434l3.5 2a.5.5 0 00.496-.868L8 8.71V3.5z"/>
                <path d="M8 16A8 8 0 108 0a8 8 0 000 16zm7-8A7 7 0 111 8a7 7 0 0114 0z"/>
              </svg>
              {{ formatTime(question.createdAt) }}
            </span>
          </div>
          <div v-if="question.tags && question.tags.length" class="tag-list">
            <span v-for="tag in question.tags" :key="tag" class="tag-chip">#{{ tag }}</span>
          </div>
          <div class="question-content">{{ question.content }}</div>
          <div class="question-actions">
            <button class="action-btn" @click="handleLikeQuestion">
              <svg class="action-icon" viewBox="0 0 16 16" fill="currentColor">
                <path d="M8 4a.5.5 0 01.5.5v3h3a.5.5 0 010 1h-3v3a.5.5 0 01-1 0v-3h-3a.5.5 0 010-1h3v-3A.5.5 0 018 4z"/>
              </svg>
              赞同 {{ question.likeCount || 0 }}
            </button>
            <button class="action-btn" @click="handleFavoriteQuestion">
              <svg class="action-icon" viewBox="0 0 16 16" fill="currentColor">
                <path d="M2 2v13.5a.5.5 0 00.74.439L8 13.069l5.26 2.87A.5.5 0 0014 15.5V2a2 2 0 00-2-2H4a2 2 0 00-2 2z"/>
              </svg>
              收藏 {{ question.favoriteCount || 0 }}
            </button>
          </div>
        </div>

        <!-- 回答编辑器 -->
        <div class="answer-editor-section">
          <h3 class="section-title">写下你的回答</h3>
          <textarea
            v-model="answerInput"
            rows="4"
            placeholder="写下你的回答..."
            class="answer-textarea"
          ></textarea>
          <div class="editor-actions">
            <button class="primary-btn" @click="handleCreateAnswer">发布回答</button>
          </div>
        </div>

        <!-- 回答列表 -->
        <div class="answers-section">
          <h3 class="section-title">
            {{ question.answers?.length || 0 }} 个回答
          </h3>
          <div v-if="question.answers && question.answers.length" class="answers-list">
            <div
              v-for="answer in question.answers"
              :key="answer.answerId"
              class="answer-card"
              :class="{ highlight: answerIdToHighlight === answer.answerId }"
            >
              <div class="answer-meta">
                <span class="meta-item">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm2-3a2 2 0 11-4 0 2 2 0 014 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/>
                  </svg>
                  回答者 #{{ answer.authorId }}
                </span>
                <span class="meta-item">
                  <svg class="meta-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 3.5a.5.5 0 00-1 0V9a.5.5 0 00.252.434l3.5 2a.5.5 0 00.496-.868L8 8.71V3.5z"/>
                    <path d="M8 16A8 8 0 108 0a8 8 0 000 16zm7-8A7 7 0 111 8a7 7 0 0114 0z"/>
                  </svg>
                  {{ formatTime(answer.createdAt) }}
                </span>
              </div>
              <div class="answer-content">{{ answer.content }}</div>
              <div class="answer-actions">
                <button class="action-btn" @click="handleLikeAnswer(answer)">
                  <svg class="action-icon" viewBox="0 0 16 16" fill="currentColor">
                    <path d="M8 4a.5.5 0 01.5.5v3h3a.5.5 0 010 1h-3v3a.5.5 0 01-1 0v-3h-3a.5.5 0 010-1h3v-3A.5.5 0 018 4z"/>
                  </svg>
                  赞同 {{ answer.likeCount || 0 }}
                </button>
              </div>

              <!-- 评论 -->
              <div v-if="answer.comments && answer.comments.length" class="comments-section">
                <div
                  v-for="comment in answer.comments"
                  :key="comment.commentId"
                  class="comment-item"
                >
                  <div class="comment-meta">
                    <span>评论者 #{{ comment.authorId }}</span>
                    <span>{{ formatTime(comment.createdAt) }}</span>
                  </div>
                  <div class="comment-content">{{ comment.content }}</div>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-answers">
            还没有回答，快来写下第一条回答吧。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  getQuestionDetail,
  createAnswer,
  likeQuestion,
  favoriteQuestion,
  likeAnswer
} from '@/api/qa'
import { formatTime } from '@/utils/time'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const props = defineProps({
  questionId: {
    type: String,
    required: true
  },
  answerId: {
    type: [Number, String],
    default: null
  }
})

const loading = ref(true)
const error = ref(null)
const question = ref(null)
const answerInput = ref('')
const answerIdToHighlight = ref(null)

// 获取问题详情
const fetchQuestionDetail = async () => {
  if (!props.questionId) return
  
  loading.value = true
  error.value = null
  
  try {
    const data = await getQuestionDetail(props.questionId)
    if (data) {
      question.value = data
      
      // 如果有answerId，高亮对应的回答
      if (props.answerId) {
        answerIdToHighlight.value = Number(props.answerId)
        // 滚动到对应回答
        setTimeout(() => {
          const element = document.querySelector('.answer-card.highlight')
          if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'center' })
          }
        }, 300)
      }
    } else {
      error.value = '未找到对应问题'
    }
  } catch (err) {
    console.error('获取问题详情失败', err)
    error.value = '获取问题详情失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 返回上一页
const goBack = () => {
  // 保存问题到localStorage（如果问题有更新）
  if (question.value) {
    try {
      const stored = localStorage.getItem('qa_question_list')
      let questionList = stored ? JSON.parse(stored) : []
      const idx = questionList.findIndex(q => q.questionId === question.value.questionId)
      if (idx >= 0) {
        questionList[idx] = {
          questionId: question.value.questionId,
          authorId: question.value.authorId,
          title: question.value.title,
          content: question.value.content,
          tags: question.value.tags || [],
          createdAt: question.value.createdAt,
          likeCount: question.value.likeCount || 0,
          favoriteCount: question.value.favoriteCount || 0,
          answerCount: question.value.answers?.length || 0,
          answers: question.value.answers || [],
          authorName: `用户 #${question.value.authorId}`
        }
        localStorage.setItem('qa_question_list', JSON.stringify(questionList))
      }
    } catch (err) {
      console.warn('保存问题更新失败:', err)
    }
  }
  
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab: 'circle',
      questionId: undefined,
      answerId: undefined
    }
  })
}

// 创建回答
const handleCreateAnswer = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    window.alert('请先登录后再进行此操作')
    return
  }
  
  const content = answerInput.value.trim()
  if (!content) {
    window.alert('回答内容不能为空')
    return
  }
  
  try {
    await createAnswer({
      questionId: props.questionId,
      authorId: userId,
      content
    })
    
    answerInput.value = ''
    // 重新加载问题详情
    await fetchQuestionDetail()
  } catch (err) {
    console.error('创建回答失败', err)
    window.alert('创建回答失败')
  }
}

// 点赞问题
const handleLikeQuestion = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    window.alert('请先登录后再进行此操作')
    return
  }
  
  try {
    await likeQuestion(userId, props.questionId)
    await fetchQuestionDetail()
  } catch (err) {
    console.error('点赞问题失败', err)
    window.alert('点赞失败')
  }
}

// 收藏问题
const handleFavoriteQuestion = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    window.alert('请先登录后再进行此操作')
    return
  }
  
  try {
    await favoriteQuestion(userId, props.questionId)
    await fetchQuestionDetail()
  } catch (err) {
    console.error('收藏问题失败', err)
    window.alert('收藏失败')
  }
}

// 点赞回答
const handleLikeAnswer = async (answer) => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    window.alert('请先登录后再进行此操作')
    return
  }
  
  try {
    await likeAnswer(userId, props.questionId, answer.answerId)
    await fetchQuestionDetail()
  } catch (err) {
    console.error('点赞回答失败', err)
    window.alert('点赞失败')
  }
}

// 监听questionId变化
watch(() => props.questionId, () => {
  if (props.questionId) {
    fetchQuestionDetail()
  }
})

onMounted(() => {
  if (props.questionId) {
    fetchQuestionDetail()
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

.qa-detail-page {
  min-height: 100vh;
  background: var(--surface-muted);
  padding: 20px 24px 100px;
}

.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
}

.loader {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 3px solid var(--line-soft);
  border-top-color: var(--brand-primary);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.back-button {
  padding: 8px 16px;
  background: var(--brand-primary);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.detail-layout {
  width: min(1200px, 100%);
  margin: 0 auto;
}

.detail-container {
  background: var(--surface-base);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.back-header {
  margin-bottom: 24px;
}

.back-link-button {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 14px;
  padding: 4px 0;
}

.back-link-button:hover {
  color: var(--brand-primary);
}

.back-icon {
  width: 16px;
  height: 16px;
}

.question-section {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line-soft);
}

.question-title {
  margin: 0 0 16px 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-strong);
  line-height: 1.4;
}

.question-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.meta-icon {
  width: 14px;
  height: 14px;
}

.tag-list {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.tag-chip {
  background: #eef2ff;
  color: #4338ca;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
}

.question-content {
  margin-bottom: 16px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--text-strong);
  white-space: pre-wrap;
}

.question-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--surface-muted);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-strong);
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--line-soft);
  border-color: var(--brand-primary);
}

.action-icon {
  width: 16px;
  height: 16px;
}

.answer-editor-section {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line-soft);
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-strong);
}

.answer-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  margin-bottom: 12px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
}

.primary-btn {
  padding: 10px 20px;
  background: var(--brand-primary);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.primary-btn:hover {
  opacity: 0.9;
}

.answers-section {
  margin-bottom: 24px;
}

.answers-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.answer-card {
  padding: 20px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: var(--surface-base);
  transition: all 0.2s;
}

.answer-card.highlight {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(0, 127, 255, 0.1);
  background: #f0f7ff;
}

.answer-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.answer-content {
  margin-bottom: 12px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--text-strong);
  white-space: pre-wrap;
}

.answer-actions {
  margin-top: 12px;
}

.comments-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.comment-item {
  margin-bottom: 12px;
}

.comment-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.comment-content {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.empty-answers {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 768px) {
  .qa-detail-page {
    padding: 16px;
  }

  .detail-container {
    padding: 20px;
  }

  .question-title {
    font-size: 20px;
  }
}
</style>

