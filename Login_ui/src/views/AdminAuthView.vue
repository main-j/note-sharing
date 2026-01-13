<template>
  <div class="auth-container">
    <div class="auth-box">
      <!-- 根据路由显示具体组件 -->
      <AdminLoginPage v-if="currentRoute === 'AdminLogin'" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AdminLoginPage from '../components/admin/AdminLoginPage.vue'

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
  background: linear-gradient(135deg, #4e54c8, #8f94fb);
}

.auth-box {
  width: 400px;
  padding: 30px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}
</style>
