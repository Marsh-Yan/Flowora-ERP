<script setup lang="ts">
/* global clearTimeout, setTimeout */
import { computed, ref, watch, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  Box,
  Connection,
  DataAnalysis,
  Expand,
  Fold,
  Menu,
  Odometer,
  List,
  Search,
  Setting,
  ShoppingCart,
  Tickets,
  UserFilled,
} from '@element-plus/icons-vue'
import LocaleSwitcher from '@/components/LocaleSwitcher.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { searchWorkspace, type SearchResult } from '@/api/search'

interface MenuItem {
  index: string
  label: string
  icon: Component
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const searchQuery = ref('')
const searchFocused = ref(false)
const searchLoading = ref(false)
const searchResults = ref<SearchResult[]>([])
let searchTimer: ReturnType<typeof setTimeout> | undefined

const menuItems = computed<MenuItem[]>(() => [
  { index: '/dashboard', label: t('nav.dashboard'), icon: Odometer },
  { index: '/sales', label: t('nav.sales'), icon: Tickets },
  { index: '/procurement', label: t('nav.procurement'), icon: ShoppingCart },
  { index: '/inventory', label: t('nav.inventory'), icon: Box },
  { index: '/finance', label: t('nav.finance'), icon: DataAnalysis },
  { index: '/projects', label: t('nav.projects'), icon: List },
  { index: '/workflow', label: t('nav.workflow'), icon: Connection },
  { index: '/analytics', label: t('nav.analytics'), icon: DataAnalysis },
  { index: '/settings', label: t('nav.settings'), icon: Setting },
])

const currentTitle = computed(() => {
  const titleKey = route.meta.titleKey as string | undefined
  return titleKey ? t(titleKey) : t('common.workspace')
})

const searchOpen = computed(() => searchFocused.value && searchQuery.value.trim().length > 0)

watch(searchQuery, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!value.trim()) {
    searchResults.value = []
    searchLoading.value = false
    return
  }
  searchLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      searchResults.value = await searchWorkspace(value)
    } catch {
      searchResults.value = []
    } finally {
      searchLoading.value = false
    }
  }, 280)
})

function navigate(path: string) {
  router.push(path)
}

async function handleLogout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}

function openSearchResult(result: SearchResult) {
  searchQuery.value = ''
  searchFocused.value = false
  router.push(result.route)
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="appStore.sidebarCollapsed ? '84px' : '248px'" class="app-sidebar">
      <div class="brand-lockup">
        <div class="brand-mark"><span /><span /><span /></div>
        <div v-if="!appStore.sidebarCollapsed" class="brand-copy">
          <strong>Flowora</strong>
          <small>ERP WORKSPACE</small>
        </div>
      </div>

      <div v-if="!appStore.sidebarCollapsed" class="workspace-selector">
        <div class="workspace-avatar">{{ authStore.user?.organizationName.slice(0, 2).toUpperCase() }}</div>
        <div class="workspace-info">
          <strong>{{ authStore.user?.organizationName }}</strong>
          <span>{{ t('common.active') }}</span>
        </div>
        <el-icon><Expand /></el-icon>
      </div>

      <el-menu
        class="sidebar-menu"
        :default-active="route.path"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        @select="navigate"
      >
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="system-status">
          <span class="status-pulse" />
          <span v-if="!appStore.sidebarCollapsed">All systems operational</span>
        </div>
        <button
          class="collapse-button"
          type="button"
          :aria-label="appStore.sidebarCollapsed ? t('common.expand') : t('common.collapse')"
          @click="appStore.toggleSidebar"
        >
          <el-icon>
            <Expand v-if="appStore.sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
          <span v-if="!appStore.sidebarCollapsed">{{ t('common.collapse') }}</span>
        </button>
      </div>
    </el-aside>

    <el-container class="main-container">
      <el-header class="app-header">
        <div class="header-context">
          <el-icon class="mobile-menu"><Menu /></el-icon>
          <span class="header-eyebrow">{{ t('common.workspace') }}</span>
          <span class="header-separator">/</span>
          <strong>{{ currentTitle }}</strong>
        </div>
        <div class="header-actions">
          <el-popover placement="bottom-start" :visible="searchOpen" :width="360" popper-class="global-search-popper">
            <template #reference>
              <label class="search-box">
                <el-icon><Search /></el-icon>
                <input v-model="searchQuery" :placeholder="t('common.search')" @focus="searchFocused = true" />
                <kbd>Ctrl K</kbd>
              </label>
            </template>
            <div class="global-search-results">
              <span v-if="searchLoading" class="global-search-hint">{{ t('common.searchLoading') }}</span>
              <span v-else-if="!searchResults.length" class="global-search-hint">{{ t('common.searchNoResults') }}</span>
              <button v-for="result in searchResults" v-else :key="`${result.type}-${result.id}`" type="button" class="global-search-result" @click="openSearchResult(result)">
                <span class="global-search-result-type">{{ t(`common.searchTypes.${result.type}`, result.type) }}</span>
                <strong>{{ result.title }}</strong>
                <small>{{ result.subtitle }}</small>
              </button>
            </div>
          </el-popover>
          <LocaleSwitcher />
          <button class="icon-button notification-button" type="button" :aria-label="t('common.notifications')">
            <el-icon><Bell /></el-icon>
            <span class="notification-dot" />
          </button>
          <button class="profile-chip profile-button" type="button" :aria-label="t('common.logout')" @click="handleLogout">
            <div class="profile-avatar"><el-icon><UserFilled /></el-icon></div>
            <div class="profile-copy">
              <strong>{{ authStore.user?.displayName }}</strong>
              <span>{{ authStore.user?.roles[0] }}</span>
            </div>
          </button>
        </div>
      </el-header>

      <el-main class="app-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
