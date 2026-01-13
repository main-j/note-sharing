<template>
  <div
    :class="[
      'comment-item',
      depth === 0 ? '' : depth === 1 ? 'reply-item' : 'nested-reply-item-wrapper'
    ]"
  >
    <div class="comment-main">
      <div class="comment-header">
        <div class="comment-author-info">
          <img
            :src="'/assets/avatars/avatar.png'"
            :alt="comment.username"
            class="comment-avatar"
          />
          <span class="comment-author">{{ comment.username || '匿名用户' }}</span>
        </div>
        <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
      </div>

      <div class="comment-content-wrapper">
        <p class="comment-content">
          <span v-if="comment.replyToUsername" class="reply-to">
            回复 @{{ comment.replyToUsername }}：
          </span>
          <span>{{ comment.content }}</span>
        </p>
      </div>

      <div class="comment-actions">
        <button
          class="comment-action-btn"
          :class="{ active: comment.LikedOrNot }"
          :disabled="commentActionLoading[comment._id]"
          @click="$emit('toggle-like', comment)"
        >
          <svg class="action-icon-small" viewBox="0 0 16 16" fill="currentColor">
            <path
              d="m8 2.748-.717-.737C5.6.281 2.514.878 1.4 3.053c-.523 1.023-.641 2.5.314 4.385C2.972 9.59 5.614 12.368 8 14.25c2.386-1.882 5.028-4.659 6.286-6.813.955-1.886.838-3.362.314-4.385C13.486.878 10.4.28 8.717 2.01z"
            />
          </svg>
          <span>{{ comment.likeCount || 0 }}</span>
        </button>
        <button
          v-if="isLoggedIn"
          class="comment-action-btn"
          @click="$emit('start-reply', comment)"
        >
          回复
        </button>
        <button
          v-if="isLoggedIn"
          class="comment-action-btn danger"
          :disabled="commentActionLoading[comment._id]"
          @click="$emit('delete-comment', comment)"
        >
          删除
        </button>
      </div>
    </div>

    <!-- 回复表单，针对当前节点 -->
    <div v-if="replyingToId === comment._id" class="reply-form">
      <textarea
        :value="replyContent"
        class="comment-input reply-input"
        :placeholder="`回复 @${comment.username}:`"
        rows="3"
        :disabled="commentSubmitting"
        @input="$emit('update:replyContent', $event.target.value)"
      ></textarea>
      <div class="reply-form-actions">
        <button
          class="comment-submit-btn small"
          :disabled="!replyContent.trim() || commentSubmitting"
          @click="$emit('submit-reply', comment)"
        >
          {{ commentSubmitting ? '提交中...' : '回复' }}
        </button>
        <button class="comment-cancel-btn" @click="$emit('cancel-reply')">
          取消
        </button>
      </div>
    </div>

    <!-- 子回复（递归） -->
    <div
      v-if="comment.replies && comment.replies.length > 0"
      :class="depth === 0 ? 'comment-replies' : 'nested-replies'"
    >
      <CommentItem
        v-for="child in comment.replies"
        :key="child._id"
        :comment="child"
        :depth="depth + 1"
        :is-logged-in="isLoggedIn"
        :replying-to-id="replyingToId"
        :reply-content="replyContent"
        :comment-submitting="commentSubmitting"
        :comment-action-loading="commentActionLoading"
        @toggle-like="$emit('toggle-like', $event)"
        @start-reply="$emit('start-reply', $event)"
        @delete-comment="$emit('delete-comment', $event)"
        @update:replyContent="$emit('update:replyContent', $event)"
        @submit-reply="$emit('submit-reply', $event)"
        @cancel-reply="$emit('cancel-reply')"
      />
    </div>
  </div>
</template>

<script setup>
import { defineProps } from 'vue'
import { formatTime } from '@/utils/time'

const props = defineProps({
  comment: {
    type: Object,
    required: true
  },
  depth: {
    type: Number,
    default: 0
  },
  isLoggedIn: {
    type: Boolean,
    default: false
  },
  replyingToId: {
    type: String,
    default: null
  },
  replyContent: {
    type: String,
    default: ''
  },
  commentSubmitting: {
    type: Boolean,
    default: false
  },
  commentActionLoading: {
    type: Object,
    default: () => ({})
  }
})
</script>

<style scoped>
:global(:root) {
  --brand-primary: #22bfa3;
  --surface-base: #ffffff;
  --surface-muted: #f6f6f6;
  --line-soft: #e2e2e2;
  --text-strong: #111c17;
  --text-secondary: #666;
  --text-muted: #999;
  --feedback-danger: #dc3545;
}

.comment-item {
  padding: 16px;
  border-radius: 8px;
  background: var(--surface-base);
  border: 2px solid var(--line-soft);
  transition: all 0.2s;
  position: relative;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.comment-item:not(.reply-item):hover {
  border-color: var(--brand-primary);
  box-shadow: 0 4px 12px rgba(0, 127, 255, 0.15);
  transform: translateY(-2px);
}

.comment-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.comment-author-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--line-soft);
  flex-shrink: 0;
  background: var(--surface-muted);
  transition: border-color 0.2s, transform 0.2s;
}

.comment-item:hover .comment-avatar {
  border-color: var(--brand-primary);
  transform: scale(1.05);
}

.comment-author {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-strong);
}

.comment-time {
  font-size: 13px;
  color: var(--text-muted);
}

.comment-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reply-to {
  font-size: 15px;
  color: var(--brand-primary);
  font-weight: 600;
  margin-right: 4px;
  padding: 2px 6px;
  background: rgba(0, 127, 255, 0.1);
  border-radius: 4px;
  display: inline-block;
}

.comment-content {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  word-wrap: break-word;
  display: inline;
  padding: 6px 0;
}

.comment-content span {
  display: inline;
}

.comment-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.comment-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s, color 0.2s;
}

.comment-action-btn:hover:not(:disabled) {
  background: var(--surface-muted);
  color: var(--text-strong);
}

.comment-action-btn.active {
  color: var(--brand-primary);
}

.comment-action-btn.danger {
  color: var(--feedback-danger);
}

.comment-action-btn.danger:hover:not(:disabled) {
  background: rgba(220, 53, 69, 0.1);
}

.comment-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-icon-small {
  width: 14px;
  height: 14px;
}

.reply-form {
  margin-top: 16px;
  margin-left: 0;
  padding: 16px;
  background: var(--surface-muted);
  border-radius: 6px;
  border: 1px solid rgba(0, 127, 255, 0.2);
  border-left: 3px solid var(--brand-primary);
  position: relative;
  z-index: 10;
  animation: slideDown 0.2s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.comment-input {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  background: var(--surface-base);
  color: var(--text-strong);
  transition: border-color 0.2s;
}

.comment-input:focus {
  outline: none;
  border-color: var(--brand-primary);
}

.comment-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.reply-input {
  margin-bottom: 8px;
}

.reply-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.comment-submit-btn {
  padding: 8px 20px;
  background: var(--brand-primary);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.comment-submit-btn:hover:not(:disabled) {
  background: #006EDC;
}

.comment-submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.comment-submit-btn.small {
  padding: 6px 16px;
  font-size: 13px;
}

.comment-cancel-btn {
  padding: 6px 16px;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.comment-cancel-btn:hover {
  border-color: var(--text-secondary);
  color: var(--text-strong);
}

.comment-replies {
  margin-top: 16px;
  margin-left: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.nested-replies {
  margin-top: 10px;
  margin-left: 16px;
  padding-left: 12px;
  border-left: 2px solid rgba(0, 127, 255, 0.3);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reply-item {
  padding: 0;
  margin-bottom: 0;
  border-radius: 0;
  background: transparent;
  border: none;
  border-left: 3px solid var(--brand-primary);
  padding-left: 12px;
  position: relative;
  transition: all 0.2s;
  overflow: visible;
}

.reply-item:hover {
  background: transparent;
  border-color: var(--brand-primary);
  transform: none;
  box-shadow: none;
}

.reply-item .comment-avatar {
  width: 28px;
  height: 28px;
  border-width: 1.5px;
}

.reply-item .comment-author {
  font-size: 13px;
  font-weight: 500;
}

.reply-item .comment-content {
  font-size: 13px;
  line-height: 1.6;
}

.reply-item .comment-time {
  font-size: 11px;
}

.reply-item .comment-action-btn {
  font-size: 12px;
  padding: 3px 6px;
}

.reply-item .action-icon-small {
  width: 12px;
  height: 12px;
}

.reply-item .reply-to {
  font-size: 13px;
  padding: 1px 4px;
}

.nested-reply-item-wrapper {
  padding: 8px 10px;
  background: rgba(0, 127, 255, 0.03);
  border-radius: 6px;
  border-left: 2px solid rgba(0, 127, 255, 0.4);
  position: relative;
  margin-top: 8px;
}

.nested-reply-item-wrapper::before {
  content: '↪';
  position: absolute;
  left: -14px;
  top: 10px;
  color: rgba(0, 127, 255, 0.5);
  font-size: 14px;
}

.nested-reply-item-wrapper .comment-avatar {
  width: 24px;
  height: 24px;
  border-width: 1px;
}

.nested-reply-item-wrapper .comment-author {
  font-size: 12px;
  font-weight: 500;
}

.nested-reply-item-wrapper .comment-content {
  font-size: 12px;
  line-height: 1.5;
}

.nested-reply-item-wrapper .comment-time {
  font-size: 10px;
}

.nested-reply-item-wrapper .comment-action-btn {
  font-size: 11px;
  padding: 2px 5px;
}

.nested-reply-item-wrapper .action-icon-small {
  width: 11px;
  height: 11px;
}
</style>

