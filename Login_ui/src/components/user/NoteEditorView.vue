<template>
  <div class="editor-layout" @click="closeAllDropdowns">
    <aside class="sidebar">
      <div class="notebook-header new-style">
        <div class="header-left">
          <button class="back-btn" @click="emit('close')" title="返回">
            <svg viewBox="0 0 24 24"><path fill="currentColor" d="M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6l6 6l1.41-1.41z"/></svg>
          </button>
          <h3 class="notebook-title" :title="notebookName">
            {{ notebookName || '数据结构' }}
          </h3>
        </div>

        <button class="btn-icon add-note-btn" @click.stop="showNewNoteModal = true" title="新建笔记">
          +
        </button>
      </div>

      <div class="note-list-container">
        <div v-if="isLoading" class="list-loading-state">
          正在加载笔记列表...
        </div>
        <div v-else-if="noteList.length === 0" class="list-empty-state">
          当前笔记本没有笔记。
        </div>
        <ul v-else class="note-list new-style">
          <li
              v-for="note in noteList"
              :key="note.id"
              :class="{ active: currentNote && currentNote.id === note.id }"
              @click="selectNote(note)"
          >
            <div class="note-item">
              <img 
                v-if="note.type === 'pdf'"
                src="/assets/icons/icon-file-pdf.svg" 
                alt="PDF文件" 
                class="file-icon" 
                :title="'PDF文件'"
              />
              <img 
                v-else
                src="/assets/icons/icon-file-text.svg" 
                alt="富文本" 
                class="file-icon" 
                :title="'富文本'"
              />
              <div class="note-info">
                <p class="note-title">{{ note.title || '无标题笔记' }}</p>
                <div class="note-meta-new-style">
                  <p class="meta-line">修改：{{ note.updatedAt }}</p>
                  <p class="meta-line">创建：{{ note.createdAt }}</p>
                  <p class="meta-line">类型：{{ note.fileType.toLowerCase() }}</p>
                </div>
              </div>

              <div class="relative menu-wrapper" @click.stop>
                <button class="btn-icon actions-menu-btn" title="更多操作" @click="toggleNoteMenu(note.id)">
                  ⋮
                </button>

                <div v-if="showNoteMenuId === note.id" class="dropdown-menu note-actions-menu">
                  <div class="menu-item" @click="handleAction('重命名', note.id)">重命名</div>
                  <div class="menu-item" @click="handleAction('移动到', note.id)">移动到</div>

                  <hr class="menu-divider">

                  <div class="menu-item" @click="handleAction('下载', note.id)">下载</div>

                  <hr class="menu-divider">

                  <div class="menu-item delete-item" @click="handleAction('删除', note.id)">删除</div>
                </div>
              </div>

            </div>
          </li>
        </ul>
      </div>

    </aside>

    <main class="editor-main">
      <div v-if="!currentNote" class="empty-state">
        <div class="empty-message">未选择文件</div>
        <p class="empty-tip">请在左侧列表选择一个笔记进行查看或编辑。</p>
      </div>

      <div v-else-if="currentNoteType === 'pdf'" class="file-preview-container">
        <header class="file-preview-header">
          <h4 class="file-title">PDF 预览: {{ currentNote.title }}</h4>
          <button class="download-btn" @click="handleAction('下载', currentNote.id)">下载文件</button>
        </header>
        <div class="file-content">
          <div v-if="pdfPreviewUrl" class="pdf-wrapper">
            <VuePdfEmbed
                :source="pdfPreviewUrl"
                class="pdf-embed-viewer"
                :width="700"
            />
          </div>
          <p v-else>正在加载 PDF 文件...</p>
        </div>
      </div>

      <div v-else-if="currentNoteType === 'md'" class="editor-container">
        <header class="editor-header">
            <input
              v-model="currentTitle"
              class="title-input"
              placeholder="无标题笔记"
              :disabled="isNoteUnderModerationRef"
              @blur="updateCurrentNoteTitle"
            />
          <div class="header-actions">
            <span class="save-status">☁️ 已保存</span>
            <button class="save-btn" @click="saveNoteContent" :disabled="isNoteUnderModerationRef">保存</button>
            <button class="publish-btn" @click="handlePublishNote" :disabled="!currentNote || isNoteUnderModerationRef">发布</button>
            <span v-if="isNoteUnderModerationRef" class="moderation-status">⏳ 审核中</span>
          </div>
        </header>

        <div v-if="aiRewriteStatus.visible" class="ai-rewrite-banner">
          <div class="ai-rewrite-banner-text">
            <strong>{{ aiRewriteStatus.title }}</strong>
            <span>{{ aiRewriteStatus.detail }}</span>
          </div>
          <div class="ai-rewrite-banner-actions">
            <button
              type="button"
              class="ai-rewrite-banner-btn"
              :disabled="aiRewriteBusy || !canUndoLastAiRewrite"
              @click="undoLastAiRewrite"
            >
              撤销本次改写
            </button>
            <button
              type="button"
              class="ai-rewrite-banner-btn secondary"
              :disabled="aiRewriteBusy"
              @click="emit('open-ai-assistant')"
            >
              继续优化
            </button>
          </div>
        </div>

        <div v-if="!editor" class="loading-state">编辑器加载中...</div>

        <div v-else class="tiptap-wrapper" style="position: relative;">
          <div v-if="isNoteUnderModerationRef" class="moderation-overlay">
            <div class="moderation-message">
              <span>⏳ 笔记正在审核中，无法编辑</span>
            </div>
          </div>
          <div class="tiptap-toolbar" :class="{ 'disabled-toolbar': isNoteUnderModerationRef }">
            <div class="toolbar-group">
              <button @click="editor && editor.view ? editor.chain().focus().undo().run() : null" :disabled="!editor || !editor.can().undo()" title="撤销">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88c3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/></svg>
              </button>
              <button @click="editor && editor.view ? editor.chain().focus().redo().run() : null" :disabled="!editor || !editor.can().redo()" title="重做">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M18.4 10.6C16.55 9 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16a8.002 8.002 0 0 1 7.6-5.5c1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"/></svg>
              </button>

              <button @click="editor && editor.view ? editor.chain().focus().unsetAllMarks().run() : null" :disabled="!editor || isNoteUnderModerationRef" title="清除格式">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M19.89 18.48l-7.45-7.45l.95-2.26L15.1 5.3a1 1 0 0 1 1.59.67l2.09 9.17l1.11 3.34M6 19v-2.4l2.39-2.39l2.4 2.4H6m1.39-8.71l4.62-4.62a.993.993 0 0 1 1.41 0l2.83 2.83l-1.79.4L9.09 3.53L2.53 10.09C1.94 10.68 1.94 11.63 2.53 12.22l2.83 2.83L11 9.41L7.39 10.29z"/></svg>
              </button>
            </div>

            <div class="divider"></div>

            <div class="toolbar-group relative" @click.stop>
              <button class="insert-pill-btn" @click="toggleInsertMenu">
                <span class="plus-icon">＋</span> 插入 <span class="arrow-icon">▼</span>
              </button>

              <div v-if="showInsertMenu" class="dropdown-menu insert-menu" @click.stop="closeAllDropdowns">
                <div class="menu-item" @click="triggerImageUpload"><span class="emoji">🖼️</span> 图片</div>
                <div class="menu-item" @click="editor && editor.view ? editor.chain().focus().toggleCodeBlock().run() : null"><span class="emoji">💻</span> 代码块</div>
                <div class="menu-item" @click="editor && editor.view ? editor.chain().focus().setHorizontalRule().run() : null"><span class="emoji">―</span> 水平线</div>
              </div>
            </div>

            <div class="divider"></div>

            <div class="toolbar-group">
              <select @change="changeHeading($event)" class="toolbar-select" :disabled="!editor || isNoteUnderModerationRef" title="段落格式">
                <option value="0" :selected="editor && editor.isActive('paragraph')">正文</option>
                <option value="1" :selected="editor && editor.isActive('heading', { level: 1 })">标题 1</option>
                <option value="2" :selected="editor && editor.isActive('heading', { level: 2 })">标题 2</option>
                <option value="3" :selected="editor && editor.isActive('heading', { level: 3 })">标题 3</option>
              </select>
            </div>

            <div class="toolbar-group">
              <button @click="editor && editor.view ? editor.chain().focus().toggleBold().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('bold') }" title="加粗">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M15.6 10.79c.97-.67 1.65-1.77 1.65-2.79c0-2.26-1.75-4-4-4H7v14h7.04c2.09 0 3.71-1.7 3.71-3.79c0-1.52-.86-2.82-2.15-3.42zM10 6.5h3c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5h-3v-3zm3.5 9H10v-3h3.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z"/></svg>
              </button>
              <button @click="editor && editor.view ? editor.chain().focus().toggleUnderline().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('underline') }" title="下划线">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 17c3.31 0 6-2.69 6-6V3h-2.5v8c0 1.93-1.57 3.5-3.5 3.5S8.5 12.93 8.5 11V3H6v8c0 3.31 2.69 6 6 6zm-7 2v2h14v-2H5z"/></svg>
              </button>
              <button @click="editor && editor.view ? editor.chain().focus().toggleStrike().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('strike') }" title="删除线">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M10 19h4v-3h-4v3zM5 4v3h5v3h4V7h5V4H5zM3 14h18v-2H3v2z"/></svg>
              </button>
            </div>

            <div class="toolbar-group">
              <div class="color-picker-wrapper">
                <input type="color" class="color-input" :disabled="!editor || isNoteUnderModerationRef" @input="editor && editor.view ? editor.chain().focus().toggleHighlight({ color: $event.target.value }).run() : null" title="背景颜色">
                <svg viewBox="0 0 24 24" width="18" height="18" style="margin-top:2px"><path fill="currentColor" d="M18.5 1.15c-1.79-.63-3.74-.12-5.02 1.33l-1.53 1.74l5.5 5.5l1.74-1.53c1.45-1.27 1.96-3.23 1.33-5.02l-2.02 2.02l-2.02-2.02l2.02-2.02zM4.13 14.06L12.95 5.24l5.5 5.5L9.63 19.56c-1.26 1.26-3.16 1.55-4.72.72l3.33-3.33l-2.12-2.12l-3.33 3.33c-.83-1.56-.54-3.46.72-4.72l.62.62zM3 21.76L4.24 23l3.54-3.54l-2.12-2.12L3 21.76z"/></svg>
              </div>
            </div>

            <div class="divider"></div>

            <div class="toolbar-group">
              <button @click="editor && editor.view ? editor.chain().focus().toggleTaskList().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('taskList') }" title="待办事项">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M19 3H5c-1.11 0-2 .89-2 2v14c0 1.11.89 2 2 2h14c1.1 0 2-.89 2-2V5a2 2 0 0 0-2-2m-9 14l-5-5l1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>
              </button>
              <button @click="editor && editor.view ? editor.chain().focus().toggleBulletList().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('bulletList') }" title="无序列表">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M7 5h14v2H7V5m0 8v-2h14v2H7M7 21v-2h14v2H7M3 6c0-.55.45-1 1-1s1 .45 1 1s-.45 1-1 1s-1-.45-1-1m0 8c0-.55.45-1 1-1s1 .45 1 1s-.45 1-1 1s-1-.45-1-1m0 8c0-.55.45-1 1-1s1 .45 1 1s-.45 1-1 1s-1-.45-1-1z"/></svg>
              </button>
              <button @click="editor && editor.view ? editor.chain().focus().toggleOrderedList().run() : null" :disabled="!editor || isNoteUnderModerationRef" :class="{ 'is-active': editor && editor.isActive('orderedList') }" title="有序列表">
                <svg viewBox="0 0 24 24"><path fill="currentColor" d="M7 13v-2h14v2H7m0 6v-2h14v2H7M7 7V5h14v2H7M3 8V5H2V4h2v4H3m-1 9v-1h3v4H2v-1h2v-.5H3v-1h2v-.5H2M2 14v-4h3v1H4v.5h1v1H4v.5h2v1H2z"/></svg>
              </button>
            </div>
          </div>
          <editor-content :editor="editor" class="tiptap-content" />
        </div>
      </div>
    </main>

    <div v-if="showNewNoteModal" class="modal-overlay" @click.self="showNewNoteModal = false">
      <div class="new-note-modal">
        <h4 class="modal-title">新建笔记</h4>
        <div class="modal-input-group">
          <label for="noteTitle">笔记名</label>
          <input id="noteTitle" v-model="newNoteTitle" placeholder="请输入笔记名称" class="modal-input" />
        </div>

        <div class="modal-input-group">
          <label>创建方式</label>
          <div class="creation-options">
            <button :class="['creation-btn', { active: newNoteType === 'online' }]" @click="newNoteType = 'online'">
              在线编辑 (富文本)
            </button>
            <button :class="['creation-btn', { active: newNoteType === 'upload' }]" @click="newNoteType = 'upload'">
              上传文件
            </button>
          </div>
        </div>

        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="showNewNoteModal = false">取消</button>
          <button class="modal-confirm-btn" @click="handleNewNoteFromModal">确定</button>
        </div>
      </div>
    </div>

    <div v-if="renameDialog.visible" class="modal-overlay" @click.self="cancelRename">
      <div class="rename-dialog">
        <h4 class="modal-title">重命名笔记</h4>
        <div class="modal-input-group">
          <label for="renameTitle">新的笔记名称</label>
          <input
              id="renameTitle"
              type="text"
              v-model="renameDialog.newTitle"
              :placeholder="renameDialog.originalTitle"
              class="modal-input"
              @keyup.enter="confirmRename"
              ref="renameInputRef"
          />
        </div>

        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="cancelRename">取消</button>
          <button
              class="modal-confirm-btn"
              @click="confirmRename"
              :disabled="!renameDialog.newTitle.trim() || renameDialog.newTitle.trim() === renameDialog.originalTitle"
          >
            确定
          </button>
        </div>
      </div>
    </div>

    <div v-if="deleteDialog.visible" class="modal-overlay" @click.self="cancelDelete">
      <div class="delete-dialog rename-dialog">
        <h4 class="modal-title">删除笔记</h4>
        <p class="delete-message">
          确定要删除笔记 《{{ deleteDialog.noteTitle }}》 吗? 此操作无法撤销。
        </p>

        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="cancelDelete">取消</button>
          <button class="modal-confirm-btn delete-confirm-btn" @click="confirmDelete">
            确定删除
          </button>
        </div>
      </div>
    </div>

    <!-- 审核确认对话框 -->
    <div v-if="showModerationDialog" class="modal-overlay" @click.self="cancelModeration">
      <div class="rename-dialog">
        <h4 class="modal-title">需要审核</h4>
        <p class="delete-message">
          您的笔记内容需要管理员审核。审核期间，笔记将无法修改，也无法被其他用户搜索到。
        </p>
        <p class="delete-message" style="margin-top: 10px;">
          是否确认提交审核？
        </p>
        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="cancelModeration">取消上传</button>
          <button class="modal-confirm-btn" @click="confirmModeration">确认审核</button>
        </div>
      </div>
    </div>

    <div v-if="moveToDialog.visible" class="modal-overlay" @click.self="cancelMoveTo">
      <div class="rename-dialog"> <h4 class="modal-title">移动笔记</h4>
        <p class="delete-message">
          请选择要将笔记 **"{{ moveToDialog.noteTitle }}"** 移动到的目标笔记本：
        </p>

        <div class="modal-input-group">
          <label for="targetNotebook">目标笔记本</label>
          <select id="targetNotebook" v-model="moveToDialog.targetNotebookId" class="modal-input">
            <option disabled :value="null">请选择笔记本</option>
            <option
                v-for="notebook in moveToDialog.notebookList"
                :key="notebook.id"
                :value="notebook.id"
                :disabled="notebook.id === notebookId"
            >
              {{ notebook.name }} <span v-if="notebook.id === notebookId">(当前)</span>
            </option>
          </select>
        </div>

        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="cancelMoveTo">取消</button>
          <button
              class="modal-confirm-btn"
              @click="confirmMoveTo"
              :disabled="!moveToDialog.targetNotebookId || moveToDialog.targetNotebookId === notebookId"
          >
            确定移动
          </button>
        </div>
      </div>
    </div>

    <div v-if="downloadDialog.visible" class="modal-overlay" @click.self="cancelDownload">
      <div class="rename-dialog">
        <h4 class="modal-title">下载笔记文件</h4>
        <p class="delete-message">
          确定要下载笔记 **"{{ downloadDialog.noteTitle }}"** 吗? 文件将准备下载。
        </p>

        <div class="modal-actions">
          <button class="modal-cancel-btn" @click="cancelDownload">取消</button>
          <button class="modal-confirm-btn" @click="confirmDownload">
            确定下载
          </button>
        </div>
      </div>
    </div>

    <input
        type="file"
        ref="fileInput"
        accept="image/*"
        style="display:none"
        @change="handleImageUpload"
    />

    <input
        type="file"
        ref="uploadFileInput"
        accept="image/*, .doc, .docx, .pdf, .txt"
        style="display:none"
        @change="handleFileUpload"
    />

    <!-- 敏感词检测对话框 -->
    <div v-if="checkDialogVisible" class="modal-overlay check-dialog-overlay">
      <div class="check-dialog">
        <div class="check-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 6v6l4 2" stroke-linecap="round"/>
          </svg>
        </div>
        <h4 class="check-title">您的笔记正在检测</h4>
        <div class="progress-container">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: checkProgress + '%' }"></div>
          </div>
          <span class="progress-text">{{ checkProgress }}%</span>
        </div>
        <p class="check-tip">正在检测内容安全性，请稍候...</p>
      </div>
    </div>

    <!-- 风险等级结果对话框 -->
    <div v-if="riskResultDialog.visible" class="modal-overlay" @click.self="closeRiskResultDialog">
      <div class="risk-result-dialog">
        <div class="risk-icon" :class="`risk-${riskResultDialog.level.toLowerCase()}`">
          <svg v-if="riskResultDialog.level === 'LOW'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 6L9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <svg v-else-if="riskResultDialog.level === 'MEDIUM'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 16v-4M12 8h.01" stroke-linecap="round" stroke-linejoin="round"/>
            <circle cx="12" cy="12" r="10"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6l12 12" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="risk-content">
          <h4 class="risk-title">{{ riskResultDialog.title }}</h4>
          <p class="risk-message">{{ riskResultDialog.message }}</p>
          <div class="risk-details">
            <div class="risk-item">
              <span class="risk-label">风险等级：</span>
              <span class="risk-value" :class="`risk-value-${riskResultDialog.level.toLowerCase()}`">
                {{ riskResultDialog.level }}
              </span>
            </div>
            <div class="risk-item">
              <span class="risk-label">风险评分：</span>
              <span class="risk-value">{{ riskResultDialog.score }}分</span>
            </div>
          </div>
        </div>
        <div class="risk-actions">
          <button class="risk-confirm-btn" @click="closeRiskResultDialog">确定</button>
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
import { ref, computed, onBeforeUnmount, nextTick, onMounted } from 'vue';
import { useEditor, EditorContent } from '@tiptap/vue-3';

// 核心扩展导入
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import TaskList from '@tiptap/extension-task-list';
import { TaskItem } from '@tiptap/extension-task-item';
import Highlight from '@tiptap/extension-highlight';
import Placeholder from '@tiptap/extension-placeholder';
import TurndownService from 'turndown';
import { debounce } from 'lodash-es';
import VuePdfEmbed from 'vue-pdf-embed'
import MarkdownIt from 'markdown-it';
import { ResizableImage } from '@/extensions/ResizableImage';
import { buildAiRequestContext, postAiJson } from '@/api/ai'
import { useUserStore } from '@/stores/user'


// 引入真实的 API 接口
import {
  fetchNotesByNotebook,
  createNote,
  uploadNote,
  updateNote,
  renameNote,
  deleteNote,
  moveNote,
  uploadImage,
  getFileUrl,
  publishNote,
  checkSensitiveText
} from '@/api/note'; // 确保路径正确

import MessageToast from '@/components/MessageToast.vue'
import { useMessage } from '@/utils/message'

// ----------------- Props & Emits -----------------
const props = defineProps({
  spaceId: Number,
  notebookId: Number,
  notebookName: String,
  notebookList: Array,
  initialNoteId: Number  // 初始选中的笔记ID
});
const emit = defineEmits(['close', 'note-selected', 'ai-context-updated', 'open-ai-assistant']);
const userStore = useUserStore()

const getSelectedTextFromEditor = (editorInstance) => {
  if (!editorInstance || !editorInstance.state || !editorInstance.state.selection) {
    return ''
  }

  const { from, to, empty } = editorInstance.state.selection
  if (empty || from === to) {
    return ''
  }

  return editorInstance.state.doc
    .textBetween(from, to, '\n', ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 160)
}

// ----------------- 状态管理 -----------------
const showNoteMenuId = ref(null);
const showInsertMenu = ref(false);
const showNewNoteModal = ref(false);
const newNoteTitle = ref('新建笔记');
const newNoteType = ref('md');
const pdfPreviewUrl = ref(null);

const currentNote = ref(null);
const currentNoteType = ref(null);
const noteList = ref([]);
const currentTitle = ref('');
const fileInput = ref(null);
const uploadFileInput = ref(null);
const isLoading = ref(false);
const renameInputRef = ref(null);
const isSelectingNote = ref(false); // 防止重复点击笔记
let aiContextEmitTimer = null;

// 敏感词检测相关状态
const isCheckingSensitive = ref(false);
const checkProgress = ref(0);
const checkDialogVisible = ref(false);

// 审核确认对话框状态
const showModerationDialog = ref(false);
const moderationMeta = ref(null);
const moderationFile = ref(null);
const moderationCheckResult = ref(null);

// 笔记是否在审核中
const isNoteUnderModerationRef = ref(false);

const aiRewriteBusy = ref(false)
const aiRewriteSnapshots = ref([])
const aiRewriteStatus = ref({
  visible: false,
  title: '',
  detail: '',
  progress: 0,
  total: 0
})

// 风险等级结果对话框状态
const riskResultDialog = ref({
  visible: false,
  level: 'LOW', // LOW, MEDIUM, HIGH
  score: 0,
  title: '',
  message: ''
});

// 消息提示
const { showToast, toastMessage, toastType, toastDuration, showSuccess, showError, showInfo, showConfirm, handleConfirm: handleConfirmCallback, handleCancel: handleCancelCallback, hideMessage } = useMessage()

// 辅助函数：检查是否是重名错误
const isDuplicateTitleError = (error) => {
  const responseData = error.response?.data;
  // 检查是否是后端返回的 StandardResponse 格式的重名错误
  if (responseData && typeof responseData === 'object') {
    // 后端返回格式：{ code: 400, message: "同一笔记本下已存在同名笔记", data: null }
    if (responseData.code === 400 && responseData.message === '同一笔记本下已存在同名笔记') {
      return true;
    }
    // 兼容其他可能的错误格式
    if (responseData.message && responseData.message.includes('已存在同名笔记')) {
      return true;
    }
  }
  return false;
}

// 1. 初始化 Markdown 解析器 (MD -> HTML)
const mdParser = new MarkdownIt({
  html: true, // 允许 HTML 标签
  linkify: true, // 自动识别链接
  breaks: true, // 换行符转为 <br>
});

const extractPlainText = (value) => {
  if (!value) return ''
  if (typeof document === 'undefined') {
    return String(value).replace(/\s+/g, ' ').trim()
  }
  const container = document.createElement('div')
  container.innerHTML = String(value)
  return (container.textContent || container.innerText || '').replace(/\s+/g, ' ').trim()
}

const emitAiContextUpdate = (reason = 'editor') => {
  if (!currentNote.value) return

  const htmlContent = currentNoteType.value === 'md' && editor.value
    ? editor.value.getHTML()
    : currentNote.value.content || currentNote.value.title || ''
  const contentPreview = extractPlainText(htmlContent)
  const selectedText = currentNoteType.value === 'md' ? getSelectedTextFromEditor(editor.value) : ''

  emit('ai-context-updated', {
    kind: 'note-editor',
    id: currentNote.value.id,
    noteId: currentNote.value.id,
    notebookId: currentNote.value.notebookId || props.notebookId || null,
    spaceId: props.spaceId || null,
    title: currentTitle.value || currentNote.value.title || '无标题笔记',
    fileType: currentNoteType.value || currentNote.value.fileType || null,
    status: isNoteUnderModerationRef.value ? 'moderating' : 'editing',
    contentPreview,
    contentLength: contentPreview.length,
    selectedText,
    updatedAt: currentNote.value.updatedAt || null,
    isDirty: true,
    reason
  })
}

const scheduleAiContextUpdate = (reason = 'editor') => {
  if (aiContextEmitTimer) {
    clearTimeout(aiContextEmitTimer)
  }
  aiContextEmitTimer = setTimeout(() => {
    emitAiContextUpdate(reason)
  }, 250)
}

const canUndoLastAiRewrite = computed(() => aiRewriteSnapshots.value.length > 0)

const normalizeAiRewriteOutput = (value) => {
  const raw = String(value || '').trim()
  if (!raw) return ''

  const stripped = raw
    .replace(/^```(?:html|markdown|md)?\s*/i, '')
    .replace(/```$/i, '')
    .trim()

  if (!stripped) return ''

  if (/<[a-z][\s\S]*>/i.test(stripped)) {
    return stripped
  }

  return mdParser.render(stripped)
}

const buildAiRewriteContext = (fragmentPreview, fragmentTitle) => buildAiRequestContext(
  {
    kind: 'note-editor',
    id: currentNote.value?.id || null,
    noteId: currentNote.value?.id || null,
    notebookId: currentNote.value?.notebookId || props.notebookId || null,
    spaceId: props.spaceId || null,
    title: currentTitle.value || currentNote.value?.title || '无标题笔记',
    fileType: currentNoteType.value || currentNote.value?.fileType || null,
    contentPreview: fragmentPreview,
    contentLength: fragmentPreview.length,
    fragmentTitle: fragmentTitle || null,
    updatedAt: currentNote.value?.updatedAt || null
  },
  {
    page: {
      tab: 'workspace',
      mode: 'edit',
      editingNotebookId: props.notebookId || null,
      editingSpaceId: props.spaceId || null
    },
    user: {
      id: userStore.userInfo?.id || null,
      username: userStore.userInfo?.username || null,
      role: userStore.userInfo?.role || null
    },
    permissions: {
      canAccessWriteActions: true
    }
  }
)

const captureAiRewriteSnapshot = () => ({
  html: editor.value?.getHTML() || '',
  markdown: editor.value ? turndownService.turndown(editor.value.getHTML()) : (currentNote.value?.content || ''),
  title: currentTitle.value || '',
  noteId: currentNote.value?.id || null,
  updatedAt: currentNote.value?.updatedAt || null
})

const showAiRewriteStatus = (title, detail, progress = 0, total = 0) => {
  aiRewriteStatus.value = {
    visible: true,
    title,
    detail,
    progress,
    total
  }
}

const clearAiRewriteStatus = (hideBanner = false) => {
  if (hideBanner) {
    aiRewriteStatus.value = {
      visible: false,
      title: '',
      detail: '',
      progress: 0,
      total: 0
    }
    return
  }

  aiRewriteStatus.value = {
    ...aiRewriteStatus.value,
    visible: aiRewriteSnapshots.value.length > 0
  }
}

const resetAiRewriteSession = () => {
  aiRewriteBusy.value = false
  aiRewriteSnapshots.value = []
  clearAiRewriteStatus(true)
}

const applyHtmlToEditor = (html) => {
  if (!editor.value) return false
  editor.value.commands.setContent(html, false)
  currentNote.value.content = turndownService.turndown(html)
  scheduleAiContextUpdate('ai-rewrite')
  nextTick(() => {
    safeEditorFocus(() => {
      editor.value?.commands.focus('end')
    })
  })
  return true
}

const replaceSelectionWithHtml = (html) => {
  if (!editor.value) return false
  const selection = editor.value.state?.selection
  if (!selection || selection.empty) return false

  editor.value.chain().focus().insertContentAt(
    { from: selection.from, to: selection.to },
    html
  ).run()
  currentNote.value.content = turndownService.turndown(editor.value.getHTML())
  scheduleAiContextUpdate('ai-rewrite')
  nextTick(() => {
    safeEditorFocus(() => {
      editor.value?.commands.focus('end')
    })
  })
  return true
}

const getRewriteFragments = (html, limit = 1200) => {
  if (typeof document === 'undefined') {
    return [{ html, textLength: extractPlainText(html).length }]
  }

  const container = document.createElement('div')
  container.innerHTML = html
  const nodes = Array.from(container.childNodes)
  const fragments = []
  let current = []
  let currentLength = 0

  const flush = () => {
    if (!current.length) return
    fragments.push({
      html: current.map(item => item.html).join(''),
      textLength: current.reduce((sum, item) => sum + item.textLength, 0)
    })
    current = []
    currentLength = 0
  }

  nodes.forEach((node) => {
    const htmlText = node.nodeType === Node.TEXT_NODE
      ? (node.textContent || '').trim()
      : node.outerHTML

    if (!htmlText) return

    const textLength = extractPlainText(htmlText).length || htmlText.length
    if (current.length && currentLength + textLength > limit) {
      flush()
    }

    current.push({
      html: htmlText,
      textLength
    })
    currentLength += textLength
  })

  flush()
  return fragments.length ? fragments : [{ html, textLength: extractPlainText(html).length }]
}

const rewritePlainTextScope = async (text, instruction) => {
  const prompt = [
    '你是笔记编辑器内的润色助手。',
    '请仅润色下面这段文本，保持原意，提升表达的清晰度、顺畅度和专业度。',
    '只返回纯文本，不要解释，不要加序号，不要使用 Markdown。',
    instruction ? `额外要求：${instruction}` : '',
    '文本：',
    text
  ].filter(Boolean).join('\n')

  const data = await postAiJson('/api/v1/agent/chat', {
    message: prompt,
    context: buildAiRewriteContext(text.slice(0, 240), 'selection')
  })

  return String(data.answer || '').trim()
}

const rewriteHtmlScope = async (html, instruction) => {
  const fragments = getRewriteFragments(html)
  const rewrittenFragments = []

  for (let index = 0; index < fragments.length; index += 1) {
    const fragment = fragments[index]
    showAiRewriteStatus(
      'AI 改写中',
      `正在润色第 ${index + 1} / ${fragments.length} 段`,
      index + 1,
      fragments.length
    )

    const prompt = [
      '你在一个富文本编辑器里工作。',
      '请对下面这段 HTML 富文本进行润色，保持原有 HTML 标签结构、段落顺序和信息完整性，只输出可直接替换的 HTML 片段，不要解释，不要使用 Markdown。',
      '如果需要，可以微调表达、连接句和标题措辞，但不要丢失内容。',
      instruction ? `额外要求：${instruction}` : '',
      `第 ${index + 1} / ${fragments.length} 段内容：`,
      fragment.html
    ].filter(Boolean).join('\n')

    const data = await postAiJson('/api/v1/agent/chat', {
      message: prompt,
      context: buildAiRewriteContext(fragment.html.slice(0, 240), `fragment-${index + 1}`)
    })

    const normalized = normalizeAiRewriteOutput(data.answer || '')
    if (!normalized) {
      throw new Error(`第 ${index + 1} 段未返回可用内容`)
    }

    rewrittenFragments.push(normalized)
  }

  return rewrittenFragments.join('')
}

const applyAiRewriteRequest = async (request = {}) => {
  if (!currentNote.value || currentNoteType.value !== 'md' || !editor.value) {
    return { ok: false, reason: 'editor_unavailable' }
  }

  if (isNoteUnderModerationRef.value) {
    return { ok: false, reason: 'moderating' }
  }

  if (aiRewriteBusy.value) {
    return { ok: false, reason: 'rewrite_in_progress' }
  }

  const instruction = String(request.prompt || request.instruction || '').trim() || '请润色当前内容'
  const selectedText = getSelectedTextFromEditor(editor.value)
  const useSelection = Boolean(selectedText) && !/(全文|整篇|整段|整篇文章|整页|整篇笔记)/.test(instruction)
  const snapshot = captureAiRewriteSnapshot()

  aiRewriteBusy.value = true
  try {
    aiRewriteSnapshots.value.push(snapshot)
    const nextHtml = useSelection
      ? mdParser.render(await rewritePlainTextScope(selectedText, instruction))
      : await rewriteHtmlScope(snapshot.html, instruction)

    if (!nextHtml) {
      throw new Error('AI 未返回可应用的改写结果')
    }

    const applied = useSelection
      ? replaceSelectionWithHtml(nextHtml)
      : applyHtmlToEditor(nextHtml)

    if (!applied) {
      throw new Error('AI 改写结果无法写入当前编辑器')
    }
    showAiRewriteStatus(
      'AI 改写已应用',
      '内容已经写回当前工作区。你可以撤销这次改写，或者继续用更具体的要求再润色一次。',
      1,
      1
    )
    emitAiContextUpdate('ai-rewrite-applied')
    showSuccess('AI 改写已应用到当前工作区')
    return {
      ok: true,
      applied: true,
      scope: useSelection ? 'selection' : 'document',
      summary: 'AI 改写已应用到当前工作区'
    }
  } catch (error) {
    aiRewriteSnapshots.value.pop()
    aiRewriteStatus.value = {
      visible: true,
      title: 'AI 改写失败',
      detail: error?.message || '请稍后重试',
      progress: 0,
      total: 0
    }
    showError(error?.message || 'AI 改写失败，请稍后重试')
    return { ok: false, reason: error?.message || 'rewrite_failed' }
  } finally {
    aiRewriteBusy.value = false
  }
}

const undoLastAiRewrite = () => {
  const snapshot = aiRewriteSnapshots.value.pop()
  if (!snapshot) {
    showInfo('没有可撤销的 AI 改写')
    clearAiRewriteStatus(true)
    return false
  }

  if (editor.value) {
    editor.value.commands.setContent(snapshot.html, false)
  }
  if (currentNote.value) {
    currentNote.value.content = snapshot.markdown
  }
  currentTitle.value = snapshot.title || currentTitle.value
  scheduleAiContextUpdate('ai-rewrite-undone')
  showAiRewriteStatus(
    '已撤销本次改写',
    aiRewriteSnapshots.value.length ? '你还可以继续撤销更早的一次 AI 改写。' : '你可以重新使用 AI 助手继续润色当前内容。',
    0,
    0
  )
  if (!aiRewriteSnapshots.value.length) {
    clearAiRewriteStatus(true)
  }
  showSuccess('已撤销本次 AI 改写')
  return true
}

// 2. 初始化 Turndown 服务 (HTML -> MD)
const turndownService = new TurndownService({
  headingStyle: 'atx',
  bulletListMarker: '-',
  codeBlockStyle: 'fenced'
});

// 确保 Turndown 保留图片（包括尺寸信息）
turndownService.addRule('keepImages', {
  filter: ['img'],
  replacement: function (content, node) {
    const alt = node.alt || '';
    const src = node.getAttribute('src') || '';
    const width = node.getAttribute('width');
    const height = node.getAttribute('height');
    const title = node.title || '';
    
    // 如果有尺寸信息，使用 HTML 格式保留
    if (width || height) {
      let htmlAttrs = '';
      if (width) htmlAttrs += ` width="${width}"`;
      if (height) htmlAttrs += ` height="${height}"`;
      if (title) htmlAttrs += ` title="${title}"`;
      return `<img src="${src}" alt="${alt}"${htmlAttrs}>`;
    }
    
    // 否则使用标准 Markdown 格式
    const titlePart = title ? ` "${title}"` : '';
    return `![${alt}](${src}${titlePart})`;
  }
});

// 重命名弹窗状态
const renameDialog = ref({
  visible: false,
  noteId: null,
  originalTitle: '',
  newTitle: '',
  resolve: null, // 用于解决 Promise
});

// 删除弹窗状态
const deleteDialog = ref({
  visible: false,
  noteId: null,
  noteTitle: '',
  resolve: null, // 用于解决 Promise
});

// 移动到弹窗状态
const moveToDialog = ref({
  visible: false,
  noteId: null,
  noteTitle: '',
  notebookList: [], // 目标笔记本列表
  targetNotebookId: null, // 选中的目标笔记本ID
  resolve: null,
});

// 下载弹窗状态
const downloadDialog = ref({
  visible: false,
  noteId: null,
  noteTitle: '',
  resolve: null,
});

// ----------------- TipTap Editor -----------------
// ... (TipTap Editor 配置和 debouncedUpdateNote 保持不变)

const debouncedUpdateNote = debounce(async (meta, file) => {
  // 检查 ID 是否存在，确保在有效笔记上操作
  if (!meta.id) return;

  // 检查笔记是否在审核中（防抖延迟期间可能状态变化）
  try {
    const isUnderModeration = await isNoteUnderModeration(meta.id);
    if (isUnderModeration) {
      console.warn('笔记正在审核中，取消自动保存');
      // 恢复编辑器内容
      if (editor.value && currentNote.value) {
        const htmlContent = mdParser.render(currentNote.value.content || '');
        editor.value.commands.setContent(htmlContent, false);
      }
      showError('笔记正在审核中，无法修改');
      return;
    }
  } catch (error) {
    console.error('检查审核状态失败:', error);
    // 检查失败时，为了安全起见，取消保存
    return;
  }

  try {
    isLoading.value = true;

    const updatedVo = await updateNote(meta, file);

    // 更新本地的 updatedAt 状态，给用户反馈
    if (currentNote.value && currentNote.value.id === updatedVo.id) {
      currentNote.value.updatedAt = updatedVo.updatedAt;
      // 同步更新 noteList 中对应笔记的信息
      const noteInList = noteList.value.find(n => n.id === updatedVo.id);
      if (noteInList) {
        Object.assign(noteInList, updatedVo);
      }
      emitAiContextUpdate('autosaved')
    }

    isLoading.value = false;
    console.log(`笔记 ${updatedVo.id} 自动保存成功.`);

  } catch (error) {
    isLoading.value = false;
    console.error('自动保存笔记失败:', error);
  }
}, 5000); // 5000ms = 1秒的延迟，可以根据需要调整

// 安全的编辑器 focus 包装函数
const safeEditorFocus = (callback) => {
  if (!editor.value) return;
  try {
    // 检查编辑器视图是否可用
    if (editor.value.view && editor.value.view.hasFocus) {
      callback();
    } else {
      // 如果视图不可用，延迟执行
      nextTick(() => {
        if (editor.value && editor.value.view && editor.value.view.hasFocus) {
          callback();
        }
      });
    }
  } catch (error) {
    console.warn('编辑器 focus 失败:', error);
  }
};

const editor = useEditor({
  content: '',
  extensions: [
    StarterKit, Underline, TaskList,
    TaskItem.configure({ nested: true }), Highlight.configure({ multicolor: true }),
    ResizableImage.configure({ 
      inline: true, 
      allowBase64: true,
      HTMLAttributes: {
        class: 'resizable-image',
      },
    }),
    Placeholder.configure({ placeholder: '输入内容，输入 / 唤起菜单...' }),
  ],
  editorProps: {
    attributes: {
      // 移除原有的 prose 类，使用自定义样式
      class: 'prose-container focus:outline-none',
    },
    handlePaste: (view, event, slice) => {
      // 处理粘贴事件
      const items = Array.from(event.clipboardData?.items || [])
      const imageItem = items.find(item => {
        const type = item.type || ''
        return type.indexOf('image') !== -1
      })
      
      if (imageItem) {
        event.preventDefault()
        event.stopPropagation()
        
        const file = imageItem.getAsFile()
        if (file && file.size > 0) {
          // 确保编辑器存在且当前笔记类型正确
          if (currentNoteType.value !== 'md') {
            showError('请先选择一个富文本笔记进行编辑。')
            return true
          }
          
          // 异步处理图片上传和插入
          handlePastedImage(file).catch(error => {
            console.error('粘贴图片失败:', error)
            showError('图片粘贴失败：' + (error.message || '请稍后重试'))
          })
          
          return true // 阻止默认粘贴行为
        } else {
          console.warn('粘贴的图片文件无效或为空')
        }
      }
      
      return false // 允许其他内容正常粘贴
    },
  },
  onUpdate: ({ editor }) => {
    // 【API调用点 A】: 内容变化时自动保存
    if (currentNote.value && currentNoteType.value === 'md') {
      // 如果笔记在审核中，阻止编辑
      if (isNoteUnderModerationRef.value) {
        // 恢复内容
        const htmlContent = mdParser.render(currentNote.value.content || '');
        editor.commands.setContent(htmlContent, false);
        showError('笔记正在审核中，无法修改');
        return;
      }

      const htmlContent = editor.getHTML();
      const markdownContent = turndownService.turndown(htmlContent);

      // 1. 本地状态同步
      currentNote.value.content = markdownContent;
      scheduleAiContextUpdate('typing')

      // 2. 构造 File 对象
      const blob = new Blob([markdownContent], { type: 'text/markdown' });
      const filename = `${currentTitle.value}.md`;
      const mdFile = new File([blob], filename, { type: 'text/markdown' });

      // 3. 构造 meta 对象
      const meta = {
        id: currentNote.value.id,
        title: currentTitle.value,
        notebookId: currentNote.value.notebookId,
        fileType: currentNoteType.value
      };

      // 4. 调用防抖函数，而不是直接调用 updateNote
      debouncedUpdateNote(meta, mdFile);
    }
  },
  onSelectionUpdate: ({ editor }) => {
    if (currentNote.value && currentNoteType.value === 'md') {
      scheduleAiContextUpdate(getSelectedTextFromEditor(editor) ? 'selection' : 'selection-clear')
    }
  }
});

const saveNoteContent = async () => {
  // 保持检查不变，但确保逻辑严谨性
  if (!currentNote.value || currentNoteType.value !== 'md' || !editor.value) return;

  // 检查笔记是否在审核中
  if (await isNoteUnderModeration(currentNote.value.id)) {
    showError('笔记正在审核中，无法修改');
    return;
  }

  try {
    // 1. 获取 HTML 内容
    const htmlContent = editor.value.getHTML();

    // 2. 转换为 Markdown 字符串
    const markdownContent = turndownService.turndown(htmlContent);

    // 3. 构造 Blob/File 对象 (将 Markdown 字符串包装成文件)
    const blob = new Blob([markdownContent], { type: 'text/markdown' });
    const filename = `${currentTitle.value}.md`;
    const mdFile = new File([blob], filename, { type: 'text/markdown' });

    // 4. 构造 meta 对象（仅包含元数据，不包含 content）
    const meta = {
      id: currentNote.value.id,
      title: currentTitle.value, // 使用 .value
      notebookId: currentNote.value.notebookId, // 假设存在此字段
      fileType: currentNoteType.value // 使用 .value
    };

    // 【API调用点 B】: 手动保存笔记内容 (PUT /noting/notes/update)
    const updatedVo = await updateNote(meta, mdFile);

    if (updatedVo) {
      // 更新 currentNote
      if (updatedVo.updatedAt) {
        currentNote.value.updatedAt = updatedVo.updatedAt;
      } else {
        currentNote.value.updatedAt = new Date().toISOString();
      }
      // 同步更新 noteList 中对应笔记的信息
      const noteInList = noteList.value.find(n => n.id === updatedVo.id);
      if (noteInList) {
        Object.assign(noteInList, updatedVo);
      }
    }

    showSuccess('笔记内容保存成功！');
    emitAiContextUpdate('saved');

  } catch (error) {
    showError('保存笔记失败，请稍后重试。');
    console.error('Error saving note content:', error);
  }
};

/**
 * 模拟检测进度更新（与实际检测同步）
 */
const simulateCheckProgress = (checkPromise) => {
  return new Promise((resolve) => {
    checkProgress.value = 0;
    let currentProgress = 0;
    
    // 模拟进度更新
    const interval = setInterval(() => {
      currentProgress += Math.random() * 15 + 5; // 每次增加5-20%
      if (currentProgress >= 90) {
        currentProgress = 90;
        clearInterval(interval);
      } else {
        checkProgress.value = Math.floor(currentProgress);
      }
    }, 150);
    
    // 等待实际检测完成
    checkPromise.then(() => {
      clearInterval(interval);
      checkProgress.value = 100;
      setTimeout(resolve, 300);
    }).catch(() => {
      clearInterval(interval);
      checkProgress.value = 100;
      setTimeout(resolve, 300);
    });
  });
};

/**
 * 发布笔记（带敏感词检测）
 */
const handleConfirm = () => {
  handleConfirmCallback()
}

const handleCancel = () => {
  handleCancelCallback()
}

const handlePublishNote = async () => {
  if (!currentNote.value) {
    showError('请先选择一个笔记！');
    return;
  }

  // 确认发布
  try {
    const confirmed = await showConfirm('确定要发布这篇笔记吗？发布后笔记将对其他用户可见。')
    if (!confirmed) {
      return
    }
  } catch {
    return
  }

  try {
    isLoading.value = true;
    isCheckingSensitive.value = true;
    checkDialogVisible.value = true;
    checkProgress.value = 0;

    // 根据笔记类型处理内容
    let file = null;
    let meta = {};
    let textContent = '';

    if (currentNoteType.value === 'md') {
      // 富文本笔记：获取HTML内容并转换为Markdown
      if (!editor.value) {
        showError('编辑器未初始化，无法发布。');
        isCheckingSensitive.value = false;
        checkDialogVisible.value = false;
        isLoading.value = false;
        return;
      }

      const htmlContent = editor.value.getHTML();
      const markdownContent = turndownService.turndown(htmlContent);
      textContent = markdownContent;

      // 构造 File 对象
      const blob = new Blob([markdownContent], { type: 'text/markdown' });
      const filename = `${currentTitle.value || currentNote.value.title}.md`;
      file = new File([blob], filename, { type: 'text/markdown' });

      // 构造 meta 对象（使用 NoteUpdateMeta 格式）
      meta = {
        id: currentNote.value.id,
        title: currentTitle.value || currentNote.value.title,
        notebookId: currentNote.value.notebookId,
        fileType: 'md'
      };
    } else {
      // PDF或其他文件类型：需要获取文件
      showError('文件类型笔记的发布功能需要先上传文件，请使用更新功能。');
      isCheckingSensitive.value = false;
      checkDialogVisible.value = false;
      isLoading.value = false;
      return;
    }

    // 调用敏感词检测API
    let checkResult = null;
    let checkPromise;
    try {
      // 组合标题和内容进行检测
      const checkText = `${meta.title}\n${textContent}`;
      checkPromise = checkSensitiveText(checkText);
      checkResult = await checkPromise;
    } catch (error) {
      console.error('敏感词检测失败:', error);
      // 检测失败时，视为高风险，阻止发布
      checkResult = { riskLevel: 'HIGH', status: 'FLAGGED', score: 100 };
      checkPromise = Promise.resolve(checkResult);
    }

    // 同步进度条与实际检测
    await simulateCheckProgress(checkPromise);

    // 关闭检测对话框
    isCheckingSensitive.value = false;
    checkDialogVisible.value = false;

    // 根据风险等级处理
    const riskLevel = checkResult?.riskLevel?.toUpperCase() || 'LOW';
    const status = checkResult?.status?.toUpperCase() || 'SAFE';
    const score = checkResult?.score || 0;
    
    // 根据风险等级处理
    if (riskLevel === 'LOW') {
      // LOW 风险：正常发布
      try {
        const publishedVo = await publishNote(meta, file);
        
        if (publishedVo) {
          // 更新本地笔记信息
          if (publishedVo.updatedAt) {
            currentNote.value.updatedAt = publishedVo.updatedAt;
          }
          // 同步更新 noteList 中对应笔记的信息
          const noteInList = noteList.value.find(n => n.id === publishedVo.id);
          if (noteInList) {
            Object.assign(noteInList, publishedVo);
          }
          
          // 显示成功提示
          isLoading.value = false;
          showSuccess('笔记成功发布');
        }
      } catch (error) {
        isLoading.value = false;
        showError('发布笔记失败：' + (error.response?.data?.message || error.message || '请稍后重试。'));
        console.error('Error publishing note:', error);
      }
    } else if (riskLevel === 'MEDIUM') {
      // MEDIUM 风险：显示审核确认对话框
      isLoading.value = false;
      
      // 保存meta和file到临时变量，供确认审核时使用
      moderationMeta.value = meta;
      moderationFile.value = file;
      moderationCheckResult.value = checkResult;
      
      // 显示审核确认对话框
      showModerationDialog.value = true;
    } else {
      // HIGH 风险：不发布，显示退回提示
      isLoading.value = false;
      showError('笔记发布违规被退回');
    }

  } catch (error) {
    isCheckingSensitive.value = false;
    checkDialogVisible.value = false;
    showError('发布笔记失败：' + (error.response?.data?.message || error.message || '请稍后重试。'));
    console.error('Error publishing note:', error);
  } finally {
    isLoading.value = false;
  }
};


// ----------------- 核心数据操作 -----------------

/**
 * 获取笔记列表
 */
const fetchNotes = async (sortBy = 'updatedAt') => {
  // 确保 notebookId 存在且有效
  if (!props.notebookId) return;

  isLoading.value = true;
  try {
    // 【API调用点 C】: 获取笔记列表 (POST /noting/notes/by-notebook)
    const notes = await fetchNotesByNotebook(props.notebookId);
    noteList.value = notes;

    // 优先选中初始笔记ID，否则选中第一个笔记或保持现有选中状态
    if (props.initialNoteId) {
      const targetNote = noteList.value.find(n => n.id === props.initialNoteId);
      if (targetNote) {
        selectNote(targetNote);
      } else if (noteList.value.length > 0) {
        // 如果初始笔记ID不存在，选中第一个
        selectNote(noteList.value[0]);
      }
    } else if (!currentNote.value && noteList.value.length > 0) {
      selectNote(noteList.value[0]);
    } else if (currentNote.value) {
      const updatedNote = noteList.value.find(n => n.id === currentNote.value.id);
      if (updatedNote) currentNote.value = updatedNote;
      else {
        currentNote.value = null;
        currentNoteType.value = null;
        if (noteList.value.length > 0) selectNote(noteList.value[0]);
      }
    }
  } catch (error) {
    console.error('Failed to fetch notes:', error);
    showError('获取笔记列表失败，请稍后重试。');
  } finally {
    isLoading.value = false;
  }
};

// ----------------- 生命周期 -----------------
const handleAiRewriteRequestEvent = async (event) => {
  const detail = event?.detail
  if (!detail?.requestId) return

  try {
    const payload = {
      prompt: detail.prompt || detail.instruction || '',
      instruction: detail.instruction || detail.prompt || ''
    }

    const result = await applyAiRewriteRequest(payload)
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('folio-ai-rewrite-result', {
        detail: {
          requestId: detail.requestId,
          ok: Boolean(result?.ok),
          result,
          error: result?.ok ? null : (result?.reason || 'rewrite_failed')
        }
      }))
    }
  } catch (error) {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('folio-ai-rewrite-result', {
        detail: {
          requestId: detail.requestId,
          ok: false,
          result: null,
          error: error?.message || 'rewrite_failed'
        }
      }))
    }
  }
}

onMounted(() => {
  fetchNotes();
  if (typeof window !== 'undefined') {
    window.addEventListener('folio-ai-rewrite-request', handleAiRewriteRequestEvent)
  }
});

onBeforeUnmount(() => {
  editor.value?.destroy();
  if (aiContextEmitTimer) {
    clearTimeout(aiContextEmitTimer);
    aiContextEmitTimer = null;
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('folio-ai-rewrite-request', handleAiRewriteRequestEvent)
  }
});

// ----------------- 【自定义弹窗函数】 -----------------

/**
 * 显示重命名弹窗并返回一个 Promise，用于替代原生的 prompt
 */
const showRenameDialog = (noteId, originalTitle) => {
  return new Promise((resolve) => {
    renameDialog.value = {
      visible: true,
      noteId,
      originalTitle,
      newTitle: originalTitle, // 初始值设为当前标题
      resolve,
    };
    // 确保弹窗显示后自动聚焦输入框
    nextTick(() => {
      // 使用可选链或条件判断确保引用存在
      renameInputRef.value?.focus();
    });
  });
};

// 确认重命名
const confirmRename = () => {
  const newTitle = renameDialog.value.newTitle.trim();
  if (!newTitle) return;

  // 解决 Promise，并传递新标题
  if (renameDialog.value.resolve) {
    renameDialog.value.resolve(newTitle);
  }
  // 关闭弹窗并重置状态
  renameDialog.value.visible = false;
  renameDialog.value.newTitle = '';
};

// 取消重命名
const cancelRename = () => {
  // 解决 Promise，并传递 null 表示取消
  if (renameDialog.value.resolve) {
    renameDialog.value.resolve(null);
  }
  // 关闭弹窗并重置状态
  renameDialog.value.visible = false;
  renameDialog.value.newTitle = '';
};

/**
 * 显示删除确认弹窗
 */
const showDeleteDialog = (noteId, noteTitle) => {
  return new Promise((resolve) => {
    deleteDialog.value = {
      visible: true,
      noteId,
      noteTitle,
      resolve,
    };
  });
};

// 确认删除
const confirmDelete = () => {
  // 解决 Promise，并传递 true 表示确定删除
  if (deleteDialog.value.resolve) {
    deleteDialog.value.resolve(true);
  }
  // 关闭弹窗并重置状态
  deleteDialog.value.visible = false;
  // 清除 noteTitle 和 noteId 状态，避免泄露
  deleteDialog.value.noteTitle = '';
  deleteDialog.value.noteId = null;
};

// 取消删除
const cancelDelete = () => {
  // 解决 Promise，并传递 false 表示取消
  if (deleteDialog.value.resolve) {
    deleteDialog.value.resolve(false);
  }
  // 关闭弹窗并重置状态
  deleteDialog.value.visible = false;
  deleteDialog.value.noteTitle = '';
  deleteDialog.value.noteId = null;
};


/**
 * 显示移动到弹窗
 * @param {string | number} noteId
 * @param {string} noteTitle
 * @param {Array} notebookList - 笔记本列表
 * @returns {Promise<number | null>} 返回目标笔记本ID或 null (如果取消)
 */
const showMoveToDialog = (noteId, noteTitle, notebookList) => {
  return new Promise((resolve) => {
    moveToDialog.value = {
      visible: true,
      noteId,
      noteTitle,
      notebookList,
      targetNotebookId: props.notebookId, // 默认选中当前笔记本
      resolve,
    };
  });
};

// 确认移动到
const confirmMoveTo = () => {
  const targetId = moveToDialog.value.targetNotebookId;
  // 仅在 targetId 有效且不是当前笔记本时解决 Promise
  if (targetId && targetId !== props.notebookId && moveToDialog.value.resolve) {
    moveToDialog.value.resolve(targetId);
  } else {
    // 视为取消或无效选择
    moveToDialog.value.resolve(null);
  }
  // 关闭弹窗并重置状态
  moveToDialog.value.visible = false;
  moveToDialog.value.targetNotebookId = null;
};

// 取消移动到
const cancelMoveTo = () => {
  if (moveToDialog.value.resolve) {
    moveToDialog.value.resolve(null);
  }
  moveToDialog.value.visible = false;
  moveToDialog.value.targetNotebookId = null;
};

/**
 * 显示下载弹窗
 */
const showDownloadDialog = (noteId, noteTitle) => {
  return new Promise((resolve) => {
    downloadDialog.value = {
      visible: true,
      noteId,
      noteTitle,
      resolve,
    };
  });
};

// 确认下载
const confirmDownload = () => {
  if (downloadDialog.value.resolve) {
    downloadDialog.value.resolve(true);
  }
  downloadDialog.value.visible = false;
  downloadDialog.value.noteTitle = '';
  downloadDialog.value.noteId = null;
};

// 取消下载
const cancelDownload = () => {
  if (downloadDialog.value.resolve) {
    downloadDialog.value.resolve(false);
  }
  downloadDialog.value.visible = false;
  downloadDialog.value.noteTitle = '';
  downloadDialog.value.noteId = null;
};

// ----------------- 逻辑函数 -----------------
const closeAllDropdowns = () => {
  showInsertMenu.value = false;
  showNoteMenuId.value = null;
};

const fetchFileContentByUrl = async (url) => {
  // 使用 fetch 的 cache 配置来强制浏览器发起网络请求
  const response = await fetch(url, {
    method: 'GET',
    cache: 'no-cache' // 'reload' 表示忽略本地缓存，强制从服务器获取
    // 或者使用 'no-store' / 'no-cache'
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch content from URL: ${url}, Status: ${response.status}`);
  }
  return response.text();
};

const selectNote = async (note) => {
  // 防止重复点击：如果正在加载同一个笔记，直接返回
  if (isSelectingNote.value && currentNote.value && currentNote.value.id === note.id) {
    console.log('正在加载该笔记，忽略重复点击');
    return;
  }
  
  // 如果切换回同一个笔记，且已经加载完成，直接返回（不需要重新加载）
  const isSameNote = currentNote.value && currentNote.value.id === note.id;
  if (isSameNote && !isSelectingNote.value) {
    console.log('该笔记已经加载完成，无需重新加载');
    // 只更新标题等基本信息，不重新加载内容
    currentTitle.value = note.title;
    // 检查笔记是否在审核中（状态可能已变化）
    isNoteUnderModerationRef.value = await isNoteUnderModeration(note.id);
    emitAiContextUpdate('same-note')
    return;
  }
  
  // 设置加载状态
  isSelectingNote.value = true;
  
  currentNote.value = note;
  currentTitle.value = note.title;
  currentNoteType.value = (note.fileType || '').toLowerCase();
  pdfPreviewUrl.value = null;
  resetAiRewriteSession()
  
  // 检查笔记是否在审核中
  isNoteUnderModerationRef.value = await isNoteUnderModeration(note.id);
  
  // 通知父组件当前选中的笔记ID
  emit('note-selected', note.id);
  emitAiContextUpdate('select-note')

  // 1. 获取文件名 (假设 note 对象中包含文件名)
  const fileName = note.filename;
  if (!fileName) {
    console.error(`Note ${note.id} missing filename.`);
    // 强制清空编辑器/预览区
    editor.value?.commands.setContent('', false);
    isSelectingNote.value = false;
    return;
  }

  try {
    // 2. 获取 MinIO 文件 URL
    const fileUrl = await getFileUrl(fileName);
    if (!fileUrl) {
      throw new Error('Failed to get file URL.');
    }

    if (note.fileType?.toLowerCase() === 'pdf') {
      // 3. 处理 PDF 预览
      pdfPreviewUrl.value = fileUrl;
      console.log(`PDF Preview URL: ${fileUrl}`);
      emitAiContextUpdate('pdf-loaded')
    } else if (note.fileType?.toLowerCase() === 'md' && editor.value) {
      // 4. 处理 Markdown 文件
      // 使用原始 URL，不添加时间戳参数（MinIO presigned URL 可能不支持额外参数）
      const markdownContent = await fetchFileContentByUrl(fileUrl);
      const htmlContent = mdParser.render(markdownContent || '');
      editor.value.commands.setContent(htmlContent, false);
      emitAiContextUpdate('md-loaded')
      nextTick(() => {
        safeEditorFocus(() => {
        editor.value.commands.focus('end');
        });
      });
    }
  } catch (error) {
    console.error('Failed to load note content:', error);
    // 只有在当前选中的笔记确实是这个笔记时才显示错误
    if (currentNote.value && currentNote.value.id === note.id) {
    showError('加载笔记内容失败，请检查文件链接。');
    // 如果加载失败，清空编辑器/预览区
    editor.value?.commands.setContent('', false);
    emitAiContextUpdate('load-error')
    }
  } finally {
    // 清除加载状态
    isSelectingNote.value = false;
  }
};

const toggleNoteMenu = (noteId) => {
  showNoteMenuId.value = showNoteMenuId.value === noteId ? null : noteId;
};

const handleAction = async (action, noteId) => {
  closeAllDropdowns();
  const note = noteList.value.find(n => n.id === noteId);
  if (!note) return;

  try {
    if (action === '重命名') {
      // **调用自定义弹窗，并等待 Promise 结果**
      const newTitle = await showRenameDialog(noteId, note.title);

      if (newTitle && newTitle !== note.title) {
        // 检查笔记是否在审核中
        if (await isNoteUnderModeration(noteId)) {
          showError('笔记正在审核中，无法重命名');
          return;
        }
        
        try {
          // 【API调用点 D】: 重命名笔记 (PUT /noting/notes/rename)
          const updateResult = await renameNote(noteId, newTitle);
          note.title = newTitle;
          note.updatedAt = updateResult.updatedAt;

          if (currentNote.value && currentNote.value.id === noteId) {
            currentTitle.value = newTitle;
            currentNote.value.updatedAt = updateResult.updatedAt;
          }

          showSuccess(`笔记已重命名为 "${newTitle}"`);
        } catch (error) {
          if (isDuplicateTitleError(error)) {
            // 重名错误：显示友好的业务提示
            showError('该笔记名称已存在，请使用其他名称', 3000);
          } else {
            // 其他系统错误：显示技术性错误信息
            const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || '重命名失败，请稍后重试。';
            showError('重命名失败：' + errorMessage);
          }
          console.error('Error renaming note:', error);
        }
      }
    } else if (action === '移动到') {
      // 检查笔记是否在审核中
      if (await isNoteUnderModeration(noteId)) {
        showError('笔记正在审核中，无法移动');
        return;
      }
      
      const targetNotebookId = await showMoveToDialog(noteId, note.title, props.notebookList);

      if (targetNotebookId) {
        // 【API调用点 F】: 移动笔记 (PUT /noting/notes/move)
        // 假设 moveNote API 返回更新后的笔记对象或成功指示
        // 在实际应用中，您可能需要重新获取目标笔记本的笔记列表
        await moveNote(noteId, targetNotebookId);
        noteList.value = noteList.value.filter(n => n.id !== noteId);

        if (currentNote.value && currentNote.value.id === noteId) {
          currentNote.value = null;
          currentNoteType.value = null;
          if (noteList.value.length > 0) selectNote(noteList.value[0]);
        }

        showSuccess(`笔记 "${note.title}" 已成功移动到目标笔记本。`);
      }
    } else if (action === '下载') {
      const fileName = note.filename;
      if (!fileName) {
        showError(`笔记 "${note.title}" 缺少文件名信息，无法下载。`);
        return;
      }

      try {
        // 1. 获取 MinIO 下载链接
        const downloadUrl = await getFileUrl(fileName);
        if (!downloadUrl) {
          throw new Error('未能获取到下载链接。');
        }

        // 2. 构造下载的文件名
        const fileExtension = note.fileType ? `.${note.fileType.toLowerCase()}` : '';
        const downloadName = note.title.endsWith(fileExtension)
            ? note.title
            : `${note.title}${fileExtension}`;

        // --- 核心修改开始 ---

        // 3. 使用 fetch 请求文件流 (Blob)
        // 这会把文件内容下载到内存中，而不是让浏览器去导航
        const response = await fetch(downloadUrl);

        if (!response.ok) {
          throw new Error(`下载失败: ${response.statusText}`);
        }

        const blob = await response.blob();

        // 4. 创建一个指向内存中 Blob 的临时 URL
        const blobUrl = window.URL.createObjectURL(blob);

        // 5. 创建临时链接并触发下载
        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = downloadName; // 这里设置文件名在 Blob 模式下一定生效

        document.body.appendChild(link);
        link.click();

        // 6. 清理资源
        document.body.removeChild(link);
        window.URL.revokeObjectURL(blobUrl); // 释放内存

        // --- 核心修改结束 ---

        console.log(`Note ${noteId} downloaded via Blob: ${downloadName}`);
        // alert(`笔记 "${note.title}" 下载已完成。`);

      } catch (error) {
        console.error('下载出错:', error);
        showError('下载失败，可能是跨域限制或网络问题，请检查控制台。');
      }
    } else if (action === '删除') {
      // 检查笔记是否在审核中
      if (await isNoteUnderModeration(noteId)) {
        showError('笔记正在审核中，无法删除');
        return;
      }
      
      // **调用自定义弹窗，并等待 Promise 结果**
      const isConfirmed = await showDeleteDialog(noteId, note.title);

      // 检查 Promise 返回的布尔值
      if (isConfirmed) {
        // isConfirmed === true，表示用户点击了"确定删除"
        // 【API调用点 E】: 删除笔记 (DELETE /noting/notes)
        await deleteNote(noteId);
        const deletedId = noteId;
        noteList.value = noteList.value.filter(n => n.id !== noteId);

        if (currentNote.value && currentNote.value.id === deletedId) {
          currentNote.value = null;
          currentNoteType.value = null;
          // 删除后默认选中第一个
          if (noteList.value.length > 0) selectNote(noteList.value[0]);
        }
      }
    }
  } catch (error) {
    showError(`${action}操作失败，请稍后重试。`);
    console.error(`Error during ${action}:`, error);
  }
};

const handleNewNoteFromModal = async () => {
  const title = newNoteTitle.value.trim();
  if (!title) {
    showError('笔记名不能为空！');
    return;
  }
  
  // 验证 notebookId 是否存在
  if (!props.notebookId) {
    showError('笔记本ID不存在，无法创建笔记！');
    return;
  }
  
  const type = newNoteType.value === 'online' ? 'md' : 'pdf';
  showNewNoteModal.value = false;
  newNoteType.value = 'online';

  try {
    if (type === 'md') {
      // 1. 构造内容为空的 Blob 对象
      const emptyContent = '在此处编辑';
      const mimeType = 'text/markdown'; // 明确指定 MIME Type
      const blob = new Blob([emptyContent], { type: mimeType });
      // 2. 将 Blob 包装成 File 对象，并
      const file = new File([blob], `${title}.md`, { type: mimeType });

      const meta = {
        title: title,
        notebookId: props.notebookId,
        fileType: 'md'
      };

      // 【API调用点 G】: 创建新的 MD 笔记 (POST /noting/notes)
      const newNote = await createNote(meta, file);
      noteList.value.unshift(newNote); // 在列表前插入新笔记
      selectNote(newNote); // 选中新笔记
      newNoteTitle.value = '新建笔记'; // 重置
      showSuccess(`富文本笔记 "${title}" 创建成功。`);

    } else if (type === 'pdf') {
      // 【API调用点 H】: 触发文件上传
      uploadFileInput.value.accept = '.pdf';
      uploadFileInput.value.click();
    }
  } catch (error) {
    if (isDuplicateTitleError(error)) {
      // 重名错误：显示友好的业务提示
      showError('该笔记名称已存在，请使用其他名称', 3000);
    } else {
      // 其他系统错误：显示技术性错误信息
      const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || '创建笔记失败，请稍后重试。';
      showError('创建笔记失败：' + errorMessage);
    }
    console.error('Error creating new note:', error);
  }
};

const handleFileUpload = async (e) => {
  const file = e.target.files[0];
  if (!file) return;

  // 验证 notebookId 是否存在
  if (!props.notebookId) {
    showError('笔记本ID不存在，无法上传文件！');
    return;
  }

  const titleToUse = newNoteTitle.value.trim() || file.name.split('.').slice(0, -1).join('.');

  try {
    // 1. 确定文件类型
    let fileType = file.type;
    if (fileType.includes('/')) {
      fileType = fileType.split('/').pop().toLowerCase();
    } else {
      fileType = fileType.toLowerCase();
    }
    if (!fileType) fileType = 'unknown';

    const meta = {
      title: titleToUse,
      notebookId: props.notebookId,
      fileType: fileType,
    };

    const fileNote = await uploadNote(meta, file);
    noteList.value.unshift(fileNote);
    selectNote(fileNote);
    newNoteTitle.value = fileNote.title;

  } catch (error) {
    if (isDuplicateTitleError(error)) {
      // 重名错误：显示友好的业务提示
      showError('该笔记名称已存在，请使用其他名称', 3000);
    } else {
      // 其他系统错误：显示技术性错误信息
      const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || '文件上传和笔记创建失败，请稍后重试。';
      showError('文件上传和笔记创建失败：' + errorMessage);
    }
    console.error('Error uploading file/creating note:', error);
  }
};

const updateCurrentNoteTitle = async () => {
  if (!currentNote.value) return;

  // 标题不变动或为空则不进行 API 调用
  if (currentNote.value.title === currentTitle.value || currentTitle.value.trim() === '') return;

  // 检查笔记是否在审核中
  if (await isNoteUnderModeration(currentNote.value.id)) {
    // 恢复原标题
    currentTitle.value = currentNote.value.title;
    showError('笔记正在审核中，无法修改标题');
    return;
  }

  try {
    const newTitle = currentTitle.value;
    const updateResult = await renameNote(currentNote.value.id, newTitle);
    currentNote.value.title = newTitle;
    currentNote.value.updatedAt = updateResult.updatedAt;
  } catch (error) {
    // 恢复原标题
    currentTitle.value = currentNote.value.title;
    
    if (isDuplicateTitleError(error)) {
      // 重名错误：显示友好的业务提示
      showError('该笔记名称已存在，请使用其他名称', 3000);
    } else {
      // 其他系统错误：显示技术性错误信息
      const errorMessage = error.response?.data?.message || error.response?.data?.error || error.message || '重命名失败，请稍后重试。';
      showError('重命名失败：' + errorMessage);
    }
    console.error('Error updating title:', error);
  }
};

const triggerImageUpload = () => {
  if (currentNoteType.value === 'md') {
    fileInput.value.click();
  } else {
    showError('请先选择一个富文本笔记进行编辑。');
  }
  closeAllDropdowns();
};

const handleImageUpload = async (e) => {
  const file = e.target.files[0];
  if (!file || !editor.value) return;

  await insertImage(file);
  
  // 清空 input，防止无法连续上传同一张图
  e.target.value = null;
};

// 处理粘贴的图片
const handlePastedImage = async (file) => {
  if (!file) {
    console.warn('粘贴的文件无效')
    return;
  }
  
  if (!editor.value) {
    console.warn('编辑器未初始化')
    showError('编辑器未准备好，请稍后再试')
    return;
  }
  
  console.log('开始处理粘贴的图片，文件大小:', file.size, '文件类型:', file.type)
  await insertImage(file);
};

// 统一的图片插入函数
const insertImage = async (file) => {
  if (!editor.value) {
    console.warn('编辑器未初始化')
    return;
  }

  try {
    console.log('开始上传图片...')
    // 【API调用点 J】: 上传图片并获取 URL (POST /noting/notes/image)
    const imageUrl = await uploadImage(file);
    console.log('图片上传成功，URL:', imageUrl)

    if (!imageUrl) {
      throw new Error('图片上传失败，未返回 URL')
    }

    // 获取图片的原始尺寸（使用 Promise 包装）
    const getImageDimensions = (url) => {
      return new Promise((resolve) => {
        const img = new Image();
        img.crossOrigin = 'anonymous'; // 允许跨域加载
        let resolved = false;
        
        img.onload = () => {
          if (!resolved) {
            resolved = true;
            console.log('图片尺寸获取成功:', img.naturalWidth, 'x', img.naturalHeight)
            resolve({
              width: img.naturalWidth,
              height: img.naturalHeight
            });
          }
        };
        img.onerror = () => {
          if (!resolved) {
            resolved = true;
            // 如果无法加载图片尺寸，返回 null（不阻塞插入）
            console.warn('无法获取图片尺寸，将使用默认尺寸');
            resolve(null);
          }
        };
        // 设置超时，避免长时间等待
        setTimeout(() => {
          if (!resolved) {
            resolved = true;
            console.warn('获取图片尺寸超时');
            resolve(null);
          }
        }, 3000);
        img.src = url;
      });
    };

    // 等待图片尺寸加载完成（最多等待3秒）
    const dimensions = await getImageDimensions(imageUrl);
    
    // 插入图片，设置合理的默认尺寸
    const imageAttrs = { src: imageUrl };
    
    // 设置最大显示宽度（可以根据需要调整）
    const MAX_DISPLAY_WIDTH = 800; // 最大显示宽度 800px
    const MAX_DISPLAY_HEIGHT = 600; // 最大显示高度 600px
    
    if (dimensions && dimensions.width && dimensions.height) {
      // 计算缩放后的尺寸，保持宽高比
      let displayWidth = dimensions.width;
      let displayHeight = dimensions.height;
      
      // 如果宽度超过最大宽度，按比例缩放
      if (displayWidth > MAX_DISPLAY_WIDTH) {
        const ratio = MAX_DISPLAY_WIDTH / displayWidth;
        displayWidth = MAX_DISPLAY_WIDTH;
        displayHeight = Math.round(displayHeight * ratio);
      }
      
      // 如果高度仍然超过最大高度，再次按比例缩放
      if (displayHeight > MAX_DISPLAY_HEIGHT) {
        const ratio = MAX_DISPLAY_HEIGHT / displayHeight;
        displayHeight = MAX_DISPLAY_HEIGHT;
        displayWidth = Math.round(displayWidth * ratio);
      }
      
      imageAttrs.width = displayWidth;
      imageAttrs.height = displayHeight;
      
      console.log(`图片尺寸: 原始 ${dimensions.width}x${dimensions.height}, 显示 ${displayWidth}x${displayHeight}`)
    } else {
      // 如果无法获取尺寸，使用默认尺寸
      imageAttrs.width = MAX_DISPLAY_WIDTH;
      imageAttrs.height = MAX_DISPLAY_HEIGHT;
      console.log('使用默认图片尺寸:', MAX_DISPLAY_WIDTH, 'x', MAX_DISPLAY_HEIGHT)
    }
    
    console.log('准备插入图片，属性:', imageAttrs)
    // 插入图片到编辑器
    editor.value.chain().focus().setImage(imageAttrs).run();
    
    // 验证图片是否插入成功
    const htmlContent = editor.value.getHTML();
    const hasImage = htmlContent.includes(imageUrl);
    console.log('图片插入成功，HTML 中包含图片:', hasImage)
    console.log('当前编辑器 HTML 内容:', htmlContent.substring(0, 500))
      
  } catch (error) {
    console.error('Error uploading image:', error);
    showError('图片上传失败：' + (error.message || '请稍后重试'));
    throw error; // 重新抛出错误以便调用者处理
  }
};

// ... 其他编辑器/UI相关函数 ...
const changeHeading = (event) => {
  const level = parseInt(event.target.value);
  if (level === 0) {
    editor.value.chain().focus().setParagraph().run();
  } else {
    editor.value.chain().focus().toggleHeading({ level }).run();
  }
};

const toggleInsertMenu = () => showInsertMenu.value = !showInsertMenu.value;

/**
 * 显示风险等级结果对话框
 */
const showRiskResultDialog = (options) => {
  riskResultDialog.value = {
    visible: true,
    level: options.level || 'LOW',
    score: options.score || 0,
    title: options.title || '检测结果',
    message: options.message || ''
  };
};

/**
 * 关闭风险等级结果对话框
 */
const closeRiskResultDialog = () => {
  riskResultDialog.value.visible = false;
  // 根据风险等级显示对应的提示消息
  const level = riskResultDialog.value.level;
  if (level === 'LOW') {
    showSuccess('笔记发布成功！');
  } else if (level === 'MEDIUM') {
    showInfo('笔记已发布，已提交管理员审查。');
  }
};

/**
 * 确认提交审核
 */
const confirmModeration = async () => {
  if (!moderationMeta.value || !moderationFile.value || !moderationCheckResult.value) {
    showError('审核信息不完整，无法提交');
    showModerationDialog.value = false;
    return;
  }

  try {
    isLoading.value = true;
    
    // 先保存笔记（不发布）
    const savedVo = await updateNote(moderationMeta.value, moderationFile.value);
    if (savedVo) {
      // 提交审查记录到管理员端
      const { submitModeration } = await import('@/api/admin');
      const moderationResponse = await submitModeration(savedVo.id, moderationCheckResult.value);
      
      // 更新本地笔记信息
      if (savedVo.updatedAt) {
        currentNote.value.updatedAt = savedVo.updatedAt;
      }
      // 同步更新 noteList 中对应笔记的信息
      const noteInList = noteList.value.find(n => n.id === savedVo.id);
      if (noteInList) {
        Object.assign(noteInList, savedVo);
      }
      
      // 立即更新审核状态，锁定笔记编辑
      isNoteUnderModerationRef.value = true;
      
      showModerationDialog.value = false;
      isLoading.value = false;
      
      // 显示提交成功信息，包含审查ID
      const moderationData = moderationResponse?.data || moderationResponse;
      if (moderationData?.moderationId) {
        showSuccess(`笔记已提交审核（审查ID：${moderationData.moderationId}），审核期间无法修改`);
      } else {
      showSuccess('笔记已提交审核，审核期间无法修改');
      }
    }
  } catch (error) {
    console.error('提交审核失败:', error);
    isLoading.value = false;
    showError('提交审核失败：' + (error.response?.data?.message || error.message || '请稍后重试。'));
  } finally {
    // 清理临时变量
    moderationMeta.value = null;
    moderationFile.value = null;
    moderationCheckResult.value = null;
  }
};

/**
 * 取消审核，取消上传
 */
const cancelModeration = () => {
  showModerationDialog.value = false;
  // 清理临时变量
  moderationMeta.value = null;
  moderationFile.value = null;
  moderationCheckResult.value = null;
  showInfo('已取消上传');
};

/**
 * 检查笔记是否在审核中
 */
const isNoteUnderModeration = async (noteId) => {
  if (!noteId) return false;
  try {
    const { getNoteModerationHistory } = await import('@/api/admin');
    const response = await getNoteModerationHistory(noteId);
    
    // 处理可能的响应格式：可能是 StandardResponse { code, message, data } 或直接是数组
    let moderationList = null;
    if (response && typeof response === 'object') {
      // 如果是 StandardResponse 格式，提取 data 字段
      if (response.code !== undefined && response.data !== undefined) {
        moderationList = response.data;
      } else if (Array.isArray(response)) {
        // 如果直接是数组
        moderationList = response;
      } else if (response.data && Array.isArray(response.data)) {
        // 如果 response.data 是数组
        moderationList = response.data;
      }
    }
    
    // 如果存在未处理的FLAGGED审核记录，说明笔记在审核中
    // 需要同时检查 status === 'FLAGGED' 和 isHandled === false
    if (!moderationList || !Array.isArray(moderationList) || moderationList.length === 0) {
      return false;
    }
    
    const isUnderModeration = moderationList.some(m => 
      m && m.status === 'FLAGGED' && (m.isHandled === false || m.isHandled === null)
    );
    
    console.log(`笔记 ${noteId} 审核状态检查:`, {
      moderationList,
      isUnderModeration,
      flaggedCount: moderationList.filter(m => m && m.status === 'FLAGGED').length,
      unhandledCount: moderationList.filter(m => m && m.status === 'FLAGGED' && (m.isHandled === false || m.isHandled === null)).length
    });
    
    return isUnderModeration;
  } catch (error) {
    console.error('检查审核状态失败:', error);
    // 出错时为了安全起见，返回true（阻止编辑）
    return true;
  }
};

</script>

<style scoped>
/* ================================================= */
/* ============= 布局和滚动容器样式 ============= */
/* ================================================= */
.editor-layout {
  display: flex;
  /* 跟随外层居中内容区宽度，而不是占满整个视口宽度 */
  width: 100%;
  /* 占满可视区域高度（减去顶部固定导航的大致高度），避免上下留白 */
  min-height: calc(100vh - 64px);
  background-color: #fff; /* 整体白色背景 */
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  color: #333;
}

.sidebar {
  width: 300px; /* 侧边栏宽度略窄 */
  background-color: #f7f7f7; /* 侧边栏浅灰色背景 */
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.editor-main {
  flex: 1; /* 占据剩余空间 */
  display: flex;
  flex-direction: column;
  background-color: #fff;
}

/* --- 侧边栏顶部 (笔记本标题) --- */

/* 删除了 .search-bar-wrapper 和 .search-input-box 相关的样式 */

.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 6px;
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 200;
  min-width: 140px;
  padding: 4px 0;
  overflow: hidden;
}

.dropdown-menu .menu-item {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.dropdown-menu .menu-item:hover {
  background: #f0f0f0;
}

.notebook-header.new-style {
  padding: 10px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e0e0e0;
  background: #fff; /* 笔记本标题区白色背景 */
}

.notebook-header.new-style .header-left {
  display: flex;
  align-items: center;
  min-width: 0;
}

.back-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  color: #666;
  margin-right: 10px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-btn:hover {
  color: #333;
}

.notebook-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #333;
}

.add-note-btn {
  background: #4c7cff;
  border: none;
  border-radius: 50%;
  color: white;
  width: 30px;
  height: 30px;
  font-size: 20px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
}

.add-note-btn:hover {
  background: #3a68e0;
}

/* --- 笔记列表 --- */
.note-list-container {
  flex: 1; /* 占据剩余高度 */
  overflow-y: auto; /* 允许笔记列表独立滚动 */
  padding: 4px 0;
  min-height: 0; /* 【关键强化】防止 flex item 因内容过多而溢出 */
}

.list-loading-state, .list-empty-state {
  padding: 20px;
  text-align: center;
  color: #999;
  font-style: italic;
  font-size: 14px;
}

.note-list.new-style {
  list-style: none;
  padding: 0;
  margin: 0;
}

.note-list.new-style li {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.1s;
  min-height: 50px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  position: relative;
}

.note-list.new-style li:hover {
  background: #f0f0f0;
}

.note-list.new-style li.active {
  background: #eef2ff;
}

.note-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 优化后的元数据样式（每行独占一行） */
.note-info {
  flex: 1;
  min-width: 0;
  padding-left: 12px;
}

.note-title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-meta-new-style {
  font-size: 11px;
  color: #999;
}

.note-meta-new-style .meta-line {
  margin: 0;
  padding: 0;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-meta {
  font-size: 12px;
  color: #999;
}

.file-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  object-fit: contain;
  color: #4c7cff;
}

.menu-wrapper {
  position: relative;
}

.actions-menu-btn {
  opacity: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.note-list.new-style li:hover .actions-menu-btn {
  opacity: 1;
}

.actions-menu-btn:hover {
  background: #d9e2ff;
  color: #4c7cff;
}

.note-actions-menu {
  right: 0;
  left: auto;
}

.note-actions-menu .delete-item {
  color: #e53e3e;
}

.note-actions-menu .delete-item:hover {
  background: #fbecec;
}

.menu-divider {
  border: none;
  border-top: 1px solid #eee;
  margin: 4px 0;
}

/* --- 右侧内容区 (预览和编辑) --- */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.empty-message {
  font-size: 24px;
  font-weight: 300;
  margin-bottom: 10px;
}

.empty-tip {
  font-size: 14px;
}

/* --- 编辑器头部 (标题和保存) --- */
.editor-container {
  display: flex;
  flex-direction: column;
  height: 100%; /* 确保占据 editor-main 的全部高度 */
}

.editor-header {
  padding: 15px 30px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
}

.ai-rewrite-banner {
  margin: 12px 30px 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(76, 124, 255, 0.16);
  background: linear-gradient(180deg, rgba(76, 124, 255, 0.08), rgba(255, 255, 255, 0.98));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.ai-rewrite-banner-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.ai-rewrite-banner-text strong {
  font-size: 14px;
  color: #1f2937;
}

.ai-rewrite-banner-text span {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

.ai-rewrite-banner-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ai-rewrite-banner-btn {
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid rgba(76, 124, 255, 0.2);
  background: #4c7cff;
  color: #fff;
  cursor: pointer;
  font-size: 12px;
}

.ai-rewrite-banner-btn.secondary {
  background: #fff;
  color: #4c7cff;
}

.ai-rewrite-banner-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.title-input {
  flex: 1;
  border: none;
  font-size: 20px;
  font-weight: 700;
  outline: none;
  padding: 0;
  margin-right: 20px;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.save-status {
  font-size: 13px;
  color: #888;
}

.save-btn {
  padding: 8px 15px;
  background: #4c7cff;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
  margin-left: 8px;
}

.save-btn:hover {
  background: #3a68e0;
}

.publish-btn {
  padding: 8px 15px;
  background: #10b981;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
  margin-left: 8px;
}

.publish-btn:hover:not(:disabled) {
  background: #059669;
}

.publish-btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
  opacity: 0.6;
}

.moderation-status {
  font-size: 13px;
  color: #f59e0b;
  margin-left: 10px;
  font-weight: 500;
}

.title-input:disabled {
  background-color: #f3f4f6;
  cursor: not-allowed;
  color: #6b7280;
}

.moderation-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.9);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.moderation-message {
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 8px;
  padding: 20px 30px;
  font-size: 16px;
  color: #856404;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.disabled-toolbar {
  opacity: 0.5;
  pointer-events: none;
}

/* --- TipTap 工具栏 --- */
.tiptap-wrapper {
  flex: 1; /* 占据 header 以外的剩余高度 */
  display: flex;
  flex-direction: column;
  min-height: 0; /* 【关键强化】防止 flex item 因内容过多而溢出 */
}

/* 【样式优化点 2】: 工具栏样式 */
.tiptap-toolbar {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-bottom: 1px solid #e0e0e0; /* 略深一点的边框 */
  background: #f5f5f5; /* 略微灰色背景 */
  flex-wrap: wrap;
  gap: 8px; /* 增加组间距 */
  position: sticky; /* 粘性定位，如果顶部有导航栏，可避免滚动时工具栏消失 */
  top: 0;
  z-index: 10;
}

.tiptap-toolbar button {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.tiptap-toolbar button:hover {
  background: #e0e0e0;
  color: #333;
}

.tiptap-toolbar button.is-active {
  background: #d9e2ff; /* 浅蓝色背景 */
  border-color: #4c7cff;
  color: #4c7cff;
}

.toolbar-group {
  display: flex;
  gap: 4px;
  align-items: center;
}

.divider {
  width: 1px;
  height: 20px;
  background: #ccc;
  margin: 0 4px;
}

.toolbar-select {
  padding: 5px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

/* 【样式优化点 3】: 插入按钮样式 */
.insert-pill-btn {
  padding: 4px 10px;
  height: 30px;
  border: 1px solid #4c7cff;
  border-radius: 15px;
  background: #4c7cff;
  color: white;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
  writing-mode: horizontal-tb;
  text-orientation: mixed;
  direction: ltr;
  white-space: nowrap;
  min-width: fit-content;
}

.insert-pill-btn:hover {
  background: #3a68e0;
}

.insert-pill-btn .plus-icon {
  font-size: 14px;
  margin-right: 4px;
  line-height: 1;
}

.insert-pill-btn .arrow-icon {
  font-size: 8px;
  margin-left: 4px;
  transform: translateY(1px);
}

.insert-menu {
  left: 50%;
  transform: translateX(-50%);
  min-width: 120px;
}

.insert-menu .menu-item {
  writing-mode: horizontal-tb;
  text-orientation: mixed;
  direction: ltr;
}

.insert-menu .emoji {
  margin-right: 8px;
  font-size: 16px;
}

.color-picker-wrapper {
  position: relative;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.color-picker-wrapper:hover {
  background: #e0e0e0;
}

.color-picker-wrapper .color-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

/* --- TipTap 内容区 --- */
.tiptap-content {
  flex: 1; /* 占据 toolbar 以外的剩余高度 */
  overflow-y: auto; /* 允许内容独立滚动 */
  padding: 30px 50px;
  outline: none;
  cursor: text;
  min-height: 0; /* 【关键强化】防止 flex item 因内容过多而溢出 */
  /* 【新增】确保内容居中，并限制最大宽度以提高可读性 */
  display: flex;
  justify-content: center;
}

:deep(.ProseMirror) {
  outline: none;
  min-height: 100%;
  cursor: text;
  /* 【新增】限制编辑内容的最大宽度，使其居中显示，提高美观度 */
  max-width: 100%; /* 优化可读性的标准宽度 */
  width: 100%; /* 允许在 max-width 内自适应 */
  padding-bottom: 50px; /* 底部留白 */
  line-height: 1.6; /* 提高行高 */
  font-size: 16px;
  color: #333;
}

/* TipTap 元素默认样式覆盖 */
:deep(h1) { font-size: 2em; margin-top: 1em; margin-bottom: 0.5em; }
:deep(h2) { font-size: 1.5em; margin-top: 1em; margin-bottom: 0.5em; }
:deep(h3) { font-size: 1.17em; margin-top: 1em; margin-bottom: 0.5em; }

:deep(ul), :deep(ol) { padding-left: 1.5em; margin-top: 0.5em; margin-bottom: 0.5em; }

:deep(pre) {
  background: #2d2d2d;
  color: #ccc;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 14px;
  margin: 1em 0;
  white-space: pre-wrap;
}

:deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  padding: 2px 4px;
  background-color: #f0f0f0;
  border-radius: 4px;
  font-size: 0.9em;
}

:deep(pre code) {
  padding: 0;
  background: none;
  border-radius: 0;
}

:deep(li[data-type="taskItem"]) {
  display: flex;
  align-items: flex-start;
  margin-bottom: 6px;
}

:deep(li[data-type="taskItem"] label) {
  margin-right: 8px;
  user-select: none;
}

:deep(li[data-type="taskItem"] input[type="checkbox"]) {
  margin-top: 0.5em; /* 调整位置 */
  margin-right: 8px;
}

:deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  display: block;
  margin: 10px auto; /* 图片居中 */
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

/* 图片包装器样式 */
:deep(.image-wrapper) {
  display: inline-block;
  position: relative;
  max-width: 100%;
  margin: 10px auto;
  text-align: center;
}

:deep(.image-wrapper:hover .image-resize-handle) {
  opacity: 1;
}

/* 调整大小控制点样式 */
:deep(.image-resize-handle) {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 16px;
  height: 16px;
  background: #4c7cff;
  border: 2px solid white;
  border-radius: 50%;
  cursor: nwse-resize;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 10;
  box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

:deep(.image-resize-handle:hover) {
  background: #3a68e0;
  transform: scale(1.2);
}

/* --- PDF 预览样式 --- */
.file-preview-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%; /* 确保占据 editor-main 的全部高度 */
}

.file-preview-header {
  padding: 15px 30px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
}

.file-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #333;
}

.download-btn {
  padding: 8px 15px;
  background: #4c7cff;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
}

.download-btn:hover {
  background: #3a68e0;
}

.file-content {
  flex: 1; /* 占据剩余高度 */
  overflow-y: auto; /* 允许文件内容独立滚动 */
  padding: 20px;
  background: #f0f0f0;
  text-align: center;
  color: #666;
  font-style: italic;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start; /* 从顶部开始对齐 */
  min-height: 0; /* 【关键强化】防止 flex item 因内容过多而溢出 */
  /* 确保PDF内容不被头部栏遮挡 */
  scroll-padding-top: 0;
}

.pdf-wrapper {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  /* 确保PDF内容有足够的顶部间距，避免被头部栏遮挡 */
  padding-top: 0;
}

.pdf-embed-viewer {
  max-width: 100%;
  height: auto;
  margin: 0 auto;
  display: block;
  /* 确保PDF内容不被遮挡 */
  position: relative;
  z-index: 1;
}

/* ================================================= */
/* ============= 模态框/弹窗样式 ================= */
/* ================================================= */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.new-note-modal, .rename-dialog, .delete-dialog {
  background: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  width: 90%;
  max-width: 450px;
}

.modal-title {
  margin-top: 0;
  margin-bottom: 25px;
  font-size: 22px;
  font-weight: 600;
}

.modal-input-group {
  margin-bottom: 20px;
}

.modal-input-group label {
  display: block;
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
  font-weight: 500;
}

.creation-options {
  display: flex;
  gap: 10px;
}

.creation-btn {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.creation-btn:hover {
  border-color: #a0a0a0;
}

.creation-btn.active {
  background: #4c7cff;
  color: white;
  border-color: #4c7cff;
}

.modal-input {
  width: 100%;
  padding: 10px 12px;
  box-sizing: border-box;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 15px;
  transition: border-color 0.2s;
}

.modal-input:focus {
  border-color: #4c7cff;
  outline: none;
  box-shadow: 0 0 0 3px rgba(76, 124, 255, 0.1);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.modal-cancel-btn, .modal-confirm-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 15px;
  transition: background-color 0.2s;
}

.modal-cancel-btn {
  background: #f0f0f0;
  color: #666;
}

.modal-cancel-btn:hover {
  background: #e0e0e0;
}

.modal-confirm-btn {
  background: #4c7cff;
  color: white;
}

.modal-confirm-btn:hover:not(:disabled) {
  background: #3a68e0;
}

.modal-confirm-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.delete-dialog .modal-confirm-btn {
  background: #e53e3e;
}

.delete-dialog .modal-confirm-btn:hover:not(:disabled) {
  background: #c53030;
}

.delete-message {
  font-size: 15px;
  line-height: 1.5;
  margin-bottom: 25px;
  color: #333;
}

.check-dialog-overlay {
  background: rgba(0, 0, 0, 0.5);
}

.check-dialog {
  background: white;
  padding: 40px 50px;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  min-width: 400px;
  text-align: center;
}

.check-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 20px;
  color: #4c7cff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-icon svg {
  width: 100%;
  height: 100%;
}

.check-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #333;
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4c7cff, #3a68e0);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  color: #666;
  min-width: 45px;
  text-align: right;
}

.check-tip {
  margin-top: 16px;
  font-size: 13px;
  color: #999;
  text-align: center;
}

/* ================================================= */
/* ============= 风险等级结果对话框样式 ============= */
/* ================================================= */
.risk-result-dialog {
  background: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  width: 90%;
  max-width: 500px;
}

.risk-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.risk-icon svg {
  width: 32px;
  height: 32px;
}

.risk-low {
  background: #e6f7e6;
  color: #52c41a;
}

.risk-medium {
  background: #fff7e6;
  color: #faad14;
}

.risk-high {
  background: #fff1f0;
  color: #ff4d4f;
}

.risk-content {
  text-align: center;
  margin-bottom: 25px;
}

.risk-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.risk-message {
  font-size: 15px;
  line-height: 1.6;
  color: #666;
  margin-bottom: 20px;
}

.risk-details {
  background: #f7f7f7;
  border-radius: 8px;
  padding: 16px;
  margin-top: 20px;
}

.risk-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.risk-item:not(:last-child) {
  border-bottom: 1px solid #e0e0e0;
}

.risk-label {
  font-size: 14px;
  color: #666;
}

.risk-value {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.risk-value-low {
  color: #52c41a;
}

.risk-value-medium {
  color: #faad14;
}

.risk-value-high {
  color: #ff4d4f;
}

.risk-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.risk-confirm-btn {
  padding: 10px 30px;
  background: #4c7cff;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 15px;
  transition: background-color 0.2s;
}

.risk-confirm-btn:hover {
  background: #3a68e0;
}
</style>
