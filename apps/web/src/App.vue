<script setup lang="ts">
import { computed } from 'vue'
import { ElConfigProvider } from 'element-plus'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppLayout from './layouts/AppLayout.vue'
import { useAppStore } from './stores/app'

const appStore = useAppStore()
const elementLocale = computed(() => (appStore.locale === 'en-US' ? en : zhCn))
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <AppLayout />
  </el-config-provider>
</template>
