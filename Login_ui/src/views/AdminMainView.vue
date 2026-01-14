<template>
  <div class="admin-main-shell">
    <header class="admin-header">
      <div class="brand-logo-block">
        <span class="brand-logo-text">Folio 管理后台</span>
      </div>

      <nav class="admin-nav-links">
        <div
          v-for="tab in tabs"
          :key="tab.value"
          class="nav-item-wrapper"
          :class="{ 
            'has-submenu': tab.submenu,
            'active': activeSubmenu === tab.value
          }"
        >
          <button
            type="button"
            :class="['nav-link-item', { 
              active: currentTab === tab.value || (tab.submenu && tab.submenu.some(s => s.value === currentTab))
            }]"
            @click="handleTabClick(tab)"
          >
            {{ tab.label }}
            <span v-if="tab.submenu" class="submenu-arrow">▼</span>
          </button>
          <div v-if="tab.submenu && activeSubmenu === tab.value" class="submenu">
            <button
              v-for="subTab in tab.submenu"
              :key="subTab.value"
              type="button"
              :class="['submenu-item', { active: currentTab === subTab.value }]"
              @click="handleSubmenuClick(subTab)"
            >
              {{ subTab.label }}
            </button>
          </div>
        </div>
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
      <section v-else-if="currentTab === 'all-users'">
        <AllUsersView />
      </section>
      <section v-else-if="currentTab === 'notes'">
        <NoteManagementView />
      </section>
      <section v-else-if="currentTab === 'remarks'">
        <RemarkManagementView />
      </section>
      <section v-else-if="currentTab === 'qa'">
        <QAManagementView />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import OnlineUsersView from '../components/admin/OnlineUsersView.vue'
import AllUsersView from '../components/admin/AllUsersView.vue'
import NoteManagementView from '../components/admin/NoteManagementView.vue'
import RemarkManagementView from '../components/admin/RemarkManagementView.vue'
import SensitiveCheckView from '../components/admin/SensitiveCheckView.vue'
import ModerationView from '../components/admin/ModerationView.vue'
import DashboardView from '../components/admin/DashboardView.vue'
import QAManagementView from '../components/admin/QAManagementView.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tabs = [
  { value: 'dashboard', label: '仪表盘' },
  { 
    value: 'user-management', 
    label: '用户管理',
    submenu: [
      { value: 'online-users', label: '在线用户管理' },
      { value: 'all-users', label: '所有用户' }
    ]
  },
  { value: 'notes', label: '笔记管理' },
  { value: 'remarks', label: '评论管理' },
  { value: 'qa', label: '问答管理' },
  { value: 'sensitive', label: '敏感词检查' },
  { value: 'moderation', label: '内容审查' }
]

const currentTab = ref('dashboard')
const activeSubmenu = ref(null)

// 从查询参数中读取标签页
onMounted(() => {
  const tabParam = route.query.tab
  if (tabParam && ['online-users', 'all-users', 'notes', 'remarks', 'qa', 'sensitive', 'moderation', 'dashboard'].includes(tabParam)) {
    currentTab.value = tabParam
    // 如果是用户管理的子标签页，打开用户管理子菜单
    if (tabParam === 'online-users' || tabParam === 'all-users') {
      activeSubmenu.value = 'user-management'
    }
  }
})

// 监听路由查询参数变化
watch(() => route.query.tab, (newTab) => {
  if (newTab && ['online-users', 'all-users', 'notes', 'remarks', 'qa', 'sensitive', 'moderation', 'dashboard'].includes(newTab)) {
    currentTab.value = newTab
    // 如果是用户管理的子标签页，打开用户管理子菜单
    if (newTab === 'online-users' || newTab === 'all-users') {
      activeSubmenu.value = 'user-management'
    } else {
      activeSubmenu.value = null
    }
  }
})

const handleTabClick = (tab) => {
  if (tab.submenu) {
    // 如果有子菜单，切换子菜单显示状态
    if (activeSubmenu.value === tab.value) {
      activeSubmenu.value = null
    } else {
      activeSubmenu.value = tab.value
    }
  } else {
    // 如果没有子菜单，直接切换标签页
    currentTab.value = tab.value
    activeSubmenu.value = null
    // 更新URL查询参数
    router.replace({
      path: route.path,
      query: { ...route.query, tab: tab.value }
    })
  }
}

// 处理子菜单项点击
const handleSubmenuClick = (subTab) => {
  currentTab.value = subTab.value
  activeSubmenu.value = null
  // 更新URL查询参数
  router.replace({
    path: route.path,
    query: { ...route.query, tab: subTab.value }
  })
}

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
  background: transparent;
  display: flex;
  flex-direction: column;
}

.admin-header {
  display: flex;
  align-items: center;
  background: var(--surface-base);
  padding: 0 24px;
  height: 64px;
  border-bottom: 1px solid var(--line-soft);
  gap: 24px;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
  z-index: 1000;
  box-shadow: var(--shadow-xs);
  backdrop-filter: blur(10px);
}

.brand-logo-block {
  margin-right: 16px;
}

.brand-logo-text {
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
  font-size: 22px;
  font-weight: 500;
  color: #0a0a0a;
  letter-spacing: 1px;
  text-transform: uppercase;
  user-select: none;
  cursor: pointer;
  transition: color 0.3s ease, opacity 0.3s ease;
  opacity: 0.9;
}

.brand-logo-text:hover {
  color: var(--brand-primary);
  opacity: 1;
}

.admin-nav-links {
  display: flex;
  align-items: center;
  gap: 35px;
  flex: 1;
}

.nav-link-item {
  background: none;
  border: none;
  padding: 8px 12px;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
  flex-shrink: 0;
  border-radius: var(--radius-xs);
}

.nav-link-item:hover {
  color: var(--text-strong);
  background: var(--surface-soft);
}

.nav-link-item.active {
  color: var(--text-strong);
  font-weight: 600;
}

.nav-link-item.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  background-color: var(--brand-primary);
  border-radius: 2px;
}

.nav-item-wrapper {
  position: relative;
}

.nav-item-wrapper.has-submenu .nav-link-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.submenu-arrow {
  font-size: 10px;
  transition: transform 0.2s;
}

.nav-item-wrapper.has-submenu.active .submenu-arrow {
  transform: rotate(180deg);
}

.submenu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  min-width: 160px;
  overflow: hidden;
}

.submenu-item {
  display: block;
  width: 100%;
  padding: 10px 16px;
  text-align: left;
  background: none;
  border: none;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.submenu-item:hover {
  background: #f8f9fa;
  color: #333;
}

.submenu-item.active {
  color: var(--brand-primary);
  font-weight: 600;
  background: var(--surface-soft);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.user-info {
  display: flex;
  align-items: center;
}

.username {
  font-size: 14px;
  color: var(--text-secondary);
}

.logout-btn {
  padding: 8px 16px;
  border: 1px solid var(--line-soft);
  background: var(--surface-base);
  color: var(--text-secondary);
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: var(--surface-soft);
  border-color: var(--brand-primary);
  color: var(--text-strong);
}

.admin-content {
  flex: 1;
  padding: 88px 24px 24px;
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
