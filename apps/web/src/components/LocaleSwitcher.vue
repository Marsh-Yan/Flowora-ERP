<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import type { AppLocale } from '@/i18n'

const { t } = useI18n()
const appStore = useAppStore()

function selectLocale(locale: AppLocale) {
  appStore.setLocale(locale)
}
</script>

<template>
  <el-dropdown trigger="click" @command="selectLocale">
    <button class="locale-switcher" type="button" :aria-label="t('common.language')">
      <span class="locale-dot" />
      <span>{{ appStore.locale === 'zh-CN' ? '中文' : 'EN' }}</span>
      <el-icon><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh-CN" :class="{ 'is-selected': appStore.locale === 'zh-CN' }">
          中文
        </el-dropdown-item>
        <el-dropdown-item command="en-US" :class="{ 'is-selected': appStore.locale === 'en-US' }">
          English
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
