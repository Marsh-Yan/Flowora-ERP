import { defineStore } from 'pinia'
import { ref } from 'vue'
import { i18n, type AppLocale } from '@/i18n'

export const useAppStore = defineStore('app', () => {
  const locale = ref<AppLocale>(i18n.global.locale.value as AppLocale)
  const sidebarCollapsed = ref(false)

  function setLocale(nextLocale: AppLocale) {
    locale.value = nextLocale
    i18n.global.locale.value = nextLocale
    window.localStorage.setItem('flowora.locale', nextLocale)
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { locale, sidebarCollapsed, setLocale, toggleSidebar }
})
