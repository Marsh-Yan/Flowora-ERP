<script setup lang="ts">
/* global clearTimeout, setTimeout */
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
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
  permission?: string
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
const organizationChanging = ref(false)
const mobileMenuOpen = ref(false)
let searchVersion = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

const menuItems = computed<MenuItem[]>(() =>
  [
    { index: '/dashboard', label: t('nav.dashboard'), icon: Odometer },
    { index: '/sales', label: t('nav.sales'), icon: Tickets, permission: 'sales:view' },
    { index: '/procurement', label: t('nav.procurement'), icon: ShoppingCart, permission: 'procurement:view' },
    { index: '/inventory', label: t('nav.inventory'), icon: Box, permission: 'inventory:view' },
    { index: '/finance', label: t('nav.finance'), icon: DataAnalysis, permission: 'finance:view' },
    { index: '/projects', label: t('nav.projects'), icon: List, permission: 'project:view' },
    { index: '/workflow', label: t('nav.workflow'), icon: Connection, permission: 'workflow:view' },
    { index: '/analytics', label: t('nav.analytics'), icon: DataAnalysis, permission: 'analytics:view' },
    { index: '/platform', label: t('nav.platform'), icon: Setting, permission: 'organization:view' },
    { index: '/settings', label: t('nav.settings'), icon: Setting, permission: 'master:view' },
  ].filter((item) => authStore.hasPermission(item.permission)),
)

const currentTitle = computed(() => {
  const titleKey = route.meta.titleKey as string | undefined
  return titleKey ? t(titleKey) : t('common.workspace')
})

const searchOpen = computed(() => searchFocused.value && searchQuery.value.trim().length > 0)

watch(searchQuery, (value) => {
  const version = ++searchVersion
  if (searchTimer) clearTimeout(searchTimer)
  if (!value.trim()) {
    searchResults.value = []
    searchLoading.value = false
    return
  }
  searchLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      const results = await searchWorkspace(value)
      if (version === searchVersion) searchResults.value = results
    } catch {
      if (version === searchVersion) searchResults.value = []
    } finally {
      if (version === searchVersion) searchLoading.value = false
    }
  }, 280)
})

watch(() => authStore.user?.organizationId, () => {
  searchVersion++
  if (searchTimer) clearTimeout(searchTimer)
  searchQuery.value = ''
  searchResults.value = []
  searchFocused.value = false
  searchLoading.value = false
})
watch(() => route.path, () => { mobileMenuOpen.value = false })
onUnmounted(() => { searchVersion++; if (searchTimer) clearTimeout(searchTimer) })

function navigate(path: string) {
  mobileMenuOpen.value = false
  router.push(path)
}

async function handleOrganizationChange(organizationId: string) {
  if (!organizationId || organizationId === authStore.user?.organizationId) return
  organizationChanging.value = true
  mobileMenuOpen.value = false
  try {
    await authStore.switchOrganization(organizationId)
    await router.replace({ name: 'dashboard' })
  } finally {
    organizationChanging.value = false
  }
}

async function handleProfileCommand(command: string) {
  if (command === 'security') {
    await router.push({ name: 'account-security' })
  } else if (command === 'logout') {
    await handleLogout()
  }
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

onMounted(() => authStore.loadOrganizations())
</script>


<template>
  <a class="skip-link" href="#main-content">{{ t('common.skipToContent') }}</a>
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
        <el-select
          :model-value="authStore.user?.organizationId"
          :loading="organizationChanging"
          :disabled="organizationChanging"
          :aria-label="t('common.organization')"
          class="workspace-switcher"
          @change="handleOrganizationChange"
        >
          <el-option
            v-for="organization in authStore.organizations"
            :key="organization.id"
            :label="organization.name"
            :value="organization.id"
          />
        </el-select>
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
          <button class="mobile-menu icon-button" type="button" :aria-label="t('common.navigation')" :aria-expanded="mobileMenuOpen" aria-controls="mobile-navigation" @click="mobileMenuOpen = !mobileMenuOpen"><el-icon><Menu /></el-icon></button>
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
          <el-dropdown trigger="click" @command="handleProfileCommand">
            <button class="profile-chip profile-button" type="button" :aria-label="t('nav.accountSecurity')">
              <div class="profile-avatar"><el-icon><UserFilled /></el-icon></div>
              <div class="profile-copy">
                <strong>{{ authStore.user?.displayName }}</strong>
                <span>{{ authStore.user?.roles[0] }}</span>
              </div>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="security">{{ t('nav.accountSecurity') }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main id="main-content" class="app-content" tabindex="-1">
        <router-view :key="authStore.user?.organizationId" />
      </el-main>
    </el-container>
  </el-container>
  <el-drawer v-model="mobileMenuOpen" direction="ltr" :size="'min(300px, 90vw)'" :title="t('common.navigation')" :close-on-press-escape="true">
    <nav id="mobile-navigation" :aria-label="t('common.navigation')" class="mobile-navigation">
      <el-select :model-value="authStore.user?.organizationId" :loading="organizationChanging" :disabled="organizationChanging" :aria-label="t('common.organization')" @change="handleOrganizationChange">
        <el-option v-for="organization in authStore.organizations" :key="organization.id" :label="organization.name" :value="organization.id" />
      </el-select>
      <router-link v-for="item in menuItems" :key="item.index" :to="item.index" @click="mobileMenuOpen = false">{{ item.label }}</router-link>
    </nav>
  </el-drawer>
</template>

<style scoped>
.mobile-navigation { display: grid; gap: 12px; }
.mobile-navigation a { padding: 12px; color: var(--el-text-color-primary); border-radius: 8px; text-decoration: none; }
.mobile-navigation a:hover, .mobile-navigation a:focus-visible, .mobile-navigation .router-link-active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
</style>
