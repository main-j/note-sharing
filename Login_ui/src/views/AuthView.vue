<template>
  <div class="auth-container">
    <!-- 笔记页传送带背景 - 多列倾斜排列 -->
    <div class="notes-conveyor-wrapper">
      <!-- 第1列 - 向上滚动 -->
      <div class="conveyor-column column-1">
        <div class="conveyor-track">
          <!-- 重复两遍以实现无缝循环 -->
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col1-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <!-- 纸张背景 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="white" opacity="0.95"/>
                <!-- 左侧装订线 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#d1d5db" stroke-width="3"/>
                <!-- 横线 - 更明显，数量减半 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <!-- 阴影 -->
                <rect x="4" y="4" width="472" height="632" rx="20" fill="none" stroke="#e5e7eb" stroke-width="2"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第2列 - 向下滚动 -->
      <div class="conveyor-column column-2">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col2-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <!-- 纸张背景 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="white" opacity="0.95"/>
                <!-- 左侧装订线 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#d1d5db" stroke-width="3"/>
                <!-- 横线 - 更明显，数量减半 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <!-- 阴影 -->
                <rect x="4" y="4" width="472" height="632" rx="20" fill="none" stroke="#e5e7eb" stroke-width="2"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第3列 - 向上滚动 -->
      <div class="conveyor-column column-3">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col3-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <!-- 纸张背景 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="white" opacity="0.95"/>
                <!-- 左侧装订线 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#d1d5db" stroke-width="3"/>
                <!-- 横线 - 更明显，数量减半 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <!-- 阴影 -->
                <rect x="4" y="4" width="472" height="632" rx="20" fill="none" stroke="#e5e7eb" stroke-width="2"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第4列 - 向下滚动 -->
      <div class="conveyor-column column-4">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col4-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <!-- 纸张背景 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="white" opacity="0.95"/>
                <!-- 左侧装订线 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#d1d5db" stroke-width="3"/>
                <!-- 横线 - 更明显，数量减半 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <!-- 阴影 -->
                <rect x="4" y="4" width="472" height="632" rx="20" fill="none" stroke="#e5e7eb" stroke-width="2"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
      <!-- 第5列 - 向上滚动 -->
      <div class="conveyor-column column-5">
        <div class="conveyor-track">
          <template v-for="repeat in 2" :key="`repeat-${repeat}`">
            <div 
              v-for="i in 8" 
              :key="`col5-${repeat}-${i}`" 
              class="note-item"
            >
              <svg class="note-icon" viewBox="0 0 480 640" xmlns="http://www.w3.org/2000/svg">
                <!-- 纸张背景 -->
                <rect x="0" y="0" width="480" height="640" rx="20" fill="white" opacity="0.95"/>
                <!-- 左侧装订线 -->
                <line x1="50" y1="0" x2="50" y2="640" stroke="#d1d5db" stroke-width="3"/>
                <!-- 横线 - 更明显，数量减半 -->
                <line x1="70" y1="70" x2="440" y2="70" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="140" x2="440" y2="140" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="210" x2="440" y2="210" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="280" x2="440" y2="280" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="350" x2="440" y2="350" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="420" x2="440" y2="420" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="490" x2="440" y2="490" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <line x1="70" y1="560" x2="440" y2="560" stroke="#9ca3af" stroke-width="2.4" opacity="0.7"/>
                <!-- 阴影 -->
                <rect x="4" y="4" width="472" height="632" rx="20" fill="none" stroke="#e5e7eb" stroke-width="2"/>
              </svg>
            </div>
          </template>
        </div>
      </div>
      
    </div>

    <div class="auth-box">
      <!-- 根据路由显示具体组件 -->
      <LoginPage v-if="currentRoute === 'Login'" />
      <RegisterPage v-if="currentRoute === 'Register'" />
      <ForgotPassword v-if="currentRoute === 'ForgotPassword'" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import LoginPage from '../components/LoginPage.vue'
import RegisterPage from '../components/RegisterPage.vue'
import ForgotPassword from '../components/ForgotPassword.vue'

// 当前路由
const route = useRoute()
const currentRoute = computed(() => route.name)
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f8f9fa;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

/* 传送带包装器 - 整体向右倾斜 */
.notes-conveyor-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
  transform: rotate(5deg);
  transform-origin: center center;
  opacity: 0.7;
  padding: 0 20px;
  box-sizing: border-box;
}

/* 传送带列基础样式 */
.conveyor-column {
  position: absolute;
  width: 360px;
  height: 100%;
  left: 0;
  overflow: hidden;
}

/* 传送带轨道 - 垂直排列，首尾相连 */
.conveyor-track {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  will-change: transform;
}

/* 笔记项样式 - 高级质感设计 */
.note-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 360px;
  height: 520px;
  padding: 0;
  margin: 0 auto;
  flex-shrink: 0;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15)) drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
  transition: filter 0.3s ease;
}

.note-item:hover {
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.2)) drop-shadow(0 3px 6px rgba(0, 0, 0, 0.15));
}

.note-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 1;
  filter: brightness(1.05) contrast(1.1);
}

/* 第1列 - 向上滚动 */
.column-1 {
  left: 5%;
  animation: columnMoveUp1 40s infinite linear;
}

.column-1 .conveyor-track {
  animation: trackMoveUp 40s infinite linear;
}

/* 第2列 - 向下滚动 */
.column-2 {
  left: 25%;
  animation: columnMoveDown1 45s infinite linear;
}

.column-2 .conveyor-track {
  animation: trackMoveDown 45s infinite linear;
}

/* 第3列 - 向上滚动 */
.column-3 {
  left: 45%;
  animation: columnMoveUp2 50s infinite linear;
}

.column-3 .conveyor-track {
  animation: trackMoveUp 50s infinite linear;
}

/* 第4列 - 向下滚动 */
.column-4 {
  left: 65%;
  animation: columnMoveDown2 42s infinite linear;
}

.column-4 .conveyor-track {
  animation: trackMoveDown 42s infinite linear;
}

/* 第5列 - 向上滚动 */
.column-5 {
  left: 85%;
  animation: columnMoveUp3 48s infinite linear;
}

.column-5 .conveyor-track {
  animation: trackMoveUp 48s infinite linear;
}

/* 向上滚动动画 - 无缝循环（移动一半距离） */
@keyframes trackMoveUp {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(calc(-50% - 10px));
  }
}

/* 向下滚动动画 - 无缝循环（移动一半距离） */
@keyframes trackMoveDown {
  0% {
    transform: translateY(calc(-50% - 10px));
  }
  100% {
    transform: translateY(0);
  }
}

/* 列的轻微位置变化 - 营造交错节奏（非常缓慢） */
@keyframes columnMoveUp1 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

@keyframes columnMoveDown1 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

@keyframes columnMoveUp2 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

@keyframes columnMoveDown2 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(2px);
  }
}

@keyframes columnMoveUp3 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

@keyframes columnMoveDown3 {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(4px);
  }
}


.auth-box {
  width: 100%;
  max-width: 440px;
  padding: 48px 40px;
  background: var(--surface-base);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  position: relative;
  z-index: 10;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
}

@media (max-width: 768px) {
  .notes-conveyor-wrapper {
    transform: rotate(4deg);
    opacity: 0.65;
    padding: 0 10px;
  }

  .conveyor-column {
    width: 280px;
  }

  .note-item {
    width: 280px;
    height: 400px;
  }

  /* 移动端调整列位置 */
  .column-1 { left: 3%; }
  .column-2 { left: 23%; }
  .column-3 { left: 43%; }
  .column-4 { left: 63%; }
  .column-5 { left: 83%; }
}

@media (max-width: 480px) {
  .auth-box {
    padding: 36px 24px;
    border-radius: var(--radius-md);
  }

  .notes-conveyor-wrapper {
    transform: rotate(3deg);
    opacity: 0.6;
    padding: 0 5px;
  }

  .conveyor-column {
    width: 240px;
  }

  .note-item {
    width: 240px;
    height: 360px;
  }

  /* 移动端调整列位置 */
  .column-1 { left: 2%; }
  .column-2 { left: 22%; }
  .column-3 { left: 42%; }
  .column-4 { left: 62%; }
  .column-5 { left: 82%; }
}
</style>
