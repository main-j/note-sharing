<template>
  <div class="admin-main-shell">
    <header class="admin-header">
      <div class="brand-logo-block">
        <span class="brand-logo-text">Folio 管理后台</span>
      </div>

      <nav class="admin-nav-links">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="['nav-link-item', { active: currentTab === tab.value }]"
          @click="currentTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </nav>

      <div class="header-actions">
        <div class="user-info">
          <span class="username">{{ adminUsername || '管理员' }}</span>
        </div>
        <button class="logout-btn" type="button" @click="handleLogout">
          退出登录
        </button>
      </div>
    </header>

    <main class="admin-content">
      <section v-if="currentTab === 'online-users'">
        <OnlineUsersView />
      </section>
      <section v-else-if="currentTab === 'notes'">
        <NoteManagementView />
      </section>
      <section v-else-if="currentTab === 'remarks'">
        <RemarkManagementView />
      </section>
      <section v-else-if="currentTab === 'sensitive'">
        <SensitiveCheckView />
      </section>
      <section v-else-if="currentTab === 'moderation'">
        <ModerationView />
      </section>
      <section v-else>
        <DashboardView />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import OnlineUsersView from '../components/admin/OnlineUsersView.vue'
import NoteManagementView from '../components/admin/NoteManagementView.vue'
import RemarkManagementView from '../components/admin/RemarkManagementView.vue'
import SensitiveCheckView from '../components/admin/SensitiveCheckView.vue'
import ModerationView from '../components/admin/ModerationView.vue'
import DashboardView from '../components/admin/DashboardView.vue'

const router = useRouter()
const userStore = useUserStore()

const tabs = [
  { value: 'dashboard', label: '仪表盘' },
  { value: 'online-users', label: '在线用户' },
  { value: 'notes', label: '笔记管理' },
  { value: 'remarks', label: '评论管理' },
  { value: 'sensitive', label: '敏感词检查' },
  { value: 'moderation', label: '内容审查' }
]

const currentTab = ref('dashboard')

const adminUsername = computed(() => {
  return userStore.userInfo?.username || '管理员'
})

const handleLogout = () => {
  userStore.clearUserData()
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-main-shell {
  min-height: 100vh;
  padding: 0;
  background: #f6f6f6;
  display: flex;
  flex-direction: column;
}

.admin-header {
  display: flex;
  align-items: center;
  background: white;
  padding: 0 20px;
  height: 60px;
  border-bottom: 1px solid #ededed;
  gap: 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.brand-logo-block {
  margin-right: 16px;
}

.brand-logo-text {
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
  font-size: 16px;
  font-weight: 500;
  color: #0a0a0a;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  user-select: none;
  opacity: 0.9;
}

.admin-nav-links {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.nav-link-item {
  background: none;
  border: none;
  padding: 8px 16px;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 6px;
}

.nav-link-item:hover {
  background: #f0f0f0;
  color: #333;
}

.nav-link-item.active {
  color: #007FFF;
  font-weight: 600;
  background: #f0f7ff;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
}

.username {
  font-size: 14px;
  color: #666;
}

.logout-btn {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: #f5f5f5;
  border-color: #ccc;
}

.admin-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .admin-header {
    flex-wrap: wrap;
    height: auto;
    padding: 10px 20px;
  }

  .admin-nav-links {
    width: 100%;
    order: 3;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid #ededed;
  }

  .header-actions {
    order: 2;
  }
}
</style>
