<script setup lang="ts">
import { ArrowLeft, Compass } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const titleKey = route.meta.titleKey as string
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-icon"><el-icon><Compass /></el-icon></div>
    <span class="eyebrow">{{ t('common.appName') }}</span>
    <h1>{{ t(titleKey) }}</h1>
    <p>{{ t('placeholder.subtitle') }}</p>
    <div class="placeholder-actions">
      <el-button type="primary" round @click="router.push('/dashboard')">
        <el-icon class="button-icon"><ArrowLeft /></el-icon>
        {{ t('placeholder.backDashboard') }}
      </el-button>
      <el-button round plain>{{ t('placeholder.roadmap') }}</el-button>
    </div>
  </div>
</template>
