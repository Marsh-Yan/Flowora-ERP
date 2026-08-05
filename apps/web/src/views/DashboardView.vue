<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowUp, Box, Check, Clock, Plus, Right } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()

const stats = computed(() => [
  { label: t('dashboard.revenue'), value: '¥ 2.48M', trend: t('dashboard.revenueTrend'), tone: 'mint', icon: '↗' },
  { label: t('dashboard.orders'), value: '24', trend: t('dashboard.ordersTrend'), tone: 'violet', icon: '◌' },
  { label: t('dashboard.inventory'), value: '7.8x', trend: t('dashboard.inventoryTrend'), tone: 'amber', icon: '◒' },
  { label: t('dashboard.tasks'), value: '12', trend: t('dashboard.tasksTrend'), tone: 'blue', icon: '✦' },
])

const workflows = computed(() => [
  { label: t('dashboard.supplyChain'), value: 86, status: t('dashboard.healthy'), tone: 'success' },
  { label: t('dashboard.salesOperations'), value: 72, status: t('dashboard.healthy'), tone: 'success' },
  { label: t('dashboard.collaboration'), value: 54, status: t('dashboard.attention'), tone: 'warning' },
])
</script>

<template>
  <div class="dashboard-page">
    <section class="welcome-banner">
      <div>
        <span class="eyebrow">{{ t('dashboard.eyebrow') }}</span>
        <h1>{{ t('dashboard.title', { name: authStore.user?.displayName }) }}</h1>
        <p>{{ t('dashboard.subtitle') }}</p>
        <div class="welcome-actions">
          <el-button type="primary" round>
            {{ t('dashboard.viewTasks') }}
            <el-icon class="button-icon"><Right /></el-icon>
          </el-button>
          <el-button round plain>
            <el-icon class="button-icon"><Plus /></el-icon>
            {{ t('dashboard.createOrder') }}
          </el-button>
        </div>
      </div>
      <div class="banner-orbit orbit-one" />
      <div class="banner-orbit orbit-two" />
      <div class="banner-spark">✦</div>
    </section>

    <section class="stat-grid">
      <el-card v-for="stat in stats" :key="stat.label" class="stat-card" shadow="never">
        <div class="stat-card-header">
          <span>{{ stat.label }}</span>
          <span class="stat-icon" :class="'tone-' + stat.tone">{{ stat.icon }}</span>
        </div>
        <strong class="stat-value">{{ stat.value }}</strong>
        <div class="stat-trend">
          <el-icon><ArrowUp /></el-icon>
          <span>{{ stat.trend }}</span>
        </div>
      </el-card>
    </section>

    <section class="content-grid">
      <el-card class="insight-card" shadow="never">
        <div class="section-heading">
          <div>
            <h2>{{ t('dashboard.workflowTitle') }}</h2>
            <p>{{ t('dashboard.workflowSubtitle') }}</p>
          </div>
          <el-button text type="primary">{{ t('common.viewAll') }}</el-button>
        </div>
        <div class="workflow-list">
          <div v-for="workflow in workflows" :key="workflow.label" class="workflow-row">
            <div class="workflow-label">
              <span class="workflow-dot" :class="'dot-' + workflow.tone" />
              <strong>{{ workflow.label }}</strong>
            </div>
            <div class="workflow-progress">
              <el-progress
                :percentage="workflow.value"
                :show-text="false"
                :color="workflow.tone === 'warning' ? '#e4a11b' : '#35b890'"
              />
            </div>
            <span class="workflow-value">{{ workflow.value }}%</span>
            <el-tag size="small" :type="workflow.tone === 'warning' ? 'warning' : 'success'" effect="light">
              {{ workflow.status }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <el-card class="activity-card" shadow="never">
        <div class="section-heading">
          <div>
            <h2>{{ t('dashboard.activityTitle') }}</h2>
            <p>{{ t('dashboard.activitySubtitle') }}</p>
          </div>
          <el-button text type="primary">{{ t('dashboard.viewActivity') }}</el-button>
        </div>
        <div class="activity-list">
          <div class="activity-item">
            <div class="activity-icon activity-icon-mint"><el-icon><Check /></el-icon></div>
            <div class="activity-copy">
              <strong>{{ t('dashboard.purchasingApproved') }}</strong>
              <span>{{ t('dashboard.minutesAgo', { minutes: 12 }) }}</span>
            </div>
          </div>
          <div class="activity-item">
            <div class="activity-icon activity-icon-blue"><el-icon><Box /></el-icon></div>
            <div class="activity-copy">
              <strong>{{ t('dashboard.stockReceived') }}</strong>
              <span>{{ t('dashboard.hoursAgo', { hours: 1 }) }}</span>
            </div>
          </div>
          <div class="activity-item">
            <div class="activity-icon activity-icon-violet"><el-icon><Clock /></el-icon></div>
            <div class="activity-copy">
              <strong>{{ t('dashboard.salesCreated') }}</strong>
              <span>{{ t('dashboard.hoursAgo', { hours: 2 }) }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>
