<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, ChatDotRound, CircleCheck, Clock, Refresh, Right, Timer } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  actOnWorkflowTask,
  addResourceComment,
  listResourceActivities,
  listResourceComments,
  listWorkflowNotifications,
  listWorkflowTasks,
  markWorkflowNotificationRead,
  type WorkflowActivity,
  type WorkflowComment,
  type WorkflowNotification,
  type WorkflowTask,
  type WorkflowTaskStatus,
} from '@/api/workflow'

const { t, locale } = useI18n()
const tasks = ref<WorkflowTask[]>([])
const notifications = ref<WorkflowNotification[]>([])
const selectedStatus = ref<'ALL' | WorkflowTaskStatus>('ALL')
const loading = ref(false)
const notificationsLoading = ref(false)
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const selectedTask = ref<WorkflowTask>()
const comments = ref<WorkflowComment[]>([])
const activities = ref<WorkflowActivity[]>([])
const commentDraft = ref('')

const statusOptions = computed(() => [
  { value: 'ALL' as const, label: t('workflow.all') },
  { value: 'OPEN' as const, label: t('workflow.statusOpen') },
  { value: 'APPROVED' as const, label: t('workflow.statusApproved') },
  { value: 'REJECTED' as const, label: t('workflow.statusRejected') },
  { value: 'COMPLETED' as const, label: t('workflow.statusCompleted') },
  { value: 'CANCELLED' as const, label: t('workflow.statusCancelled') },
])

const filteredTasks = computed(() => selectedStatus.value === 'ALL'
  ? tasks.value
  : tasks.value.filter((task) => task.status === selectedStatus.value))

const unreadNotifications = computed(() => notifications.value.filter((item) => !item.read).length)

function statusLabel(status: WorkflowTaskStatus) {
  return statusOptions.value.find((item) => item.value === status)?.label ?? status
}

function statusType(status: WorkflowTaskStatus) {
  if (status === 'OPEN') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  return 'info'
}

function resourceLabel(resourceType: string) {
  return t(`workflow.resources.${resourceType}`, resourceType)
}

function formatAmount(amount: number) {
  return new Intl.NumberFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

function formatDate(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

async function load() {
  loading.value = true
  try {
    const [taskPage, notificationPage] = await Promise.all([
      listWorkflowTasks(),
      listWorkflowNotifications(),
    ])
    tasks.value = taskPage.content
    notifications.value = notificationPage.content
  } catch {
    ElMessage.error(t('workflow.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function refreshNotifications() {
  notificationsLoading.value = true
  try {
    notifications.value = (await listWorkflowNotifications()).content
  } catch {
    ElMessage.error(t('workflow.notificationLoadFailed'))
  } finally {
    notificationsLoading.value = false
  }
}

async function handleAction(task: WorkflowTask, action: 'APPROVE' | 'REJECT' | 'COMPLETE' | 'CANCEL' | 'TRANSFER') {
  let transferToUserId: string | undefined
  if (action === 'TRANSFER') {
    try {
      const result = await ElMessageBox.prompt(t('workflow.transferPrompt'), t('workflow.transferTitle'), {
        confirmButtonText: t('workflow.transfer'),
        cancelButtonText: t('masterData.cancel'),
        inputPattern: /\S+/,
        inputErrorMessage: t('workflow.transferRequired'),
      })
      transferToUserId = result.value
    } catch {
      return
    }
  }

  if (action === 'REJECT' || action === 'CANCEL') {
    try {
      await ElMessageBox.confirm(t(`workflow.confirm${action}`), t('workflow.confirmTitle'), {
        confirmButtonText: t('workflow.confirm'),
        cancelButtonText: t('masterData.cancel'),
        type: 'warning',
      })
    } catch {
      return
    }
  }

  try {
    const result = await actOnWorkflowTask(task.id, action, undefined, transferToUserId)
    const index = tasks.value.findIndex((item) => item.id === task.id)
    if (index >= 0) tasks.value[index] = result.task
    ElMessage.success(t('workflow.actionSucceeded'))
    await refreshNotifications()
  } catch {
    ElMessage.error(t('workflow.actionFailed'))
  }
}

async function openDetails(task: WorkflowTask) {
  selectedTask.value = task
  drawerVisible.value = true
  drawerLoading.value = true
  commentDraft.value = ''
  try {
    const [commentPage, activityPage] = await Promise.all([
      listResourceComments(task.resourceType, task.resourceId),
      listResourceActivities(task.resourceType, task.resourceId),
    ])
    comments.value = commentPage.content
    activities.value = activityPage.content
  } catch {
    ElMessage.error(t('workflow.detailsLoadFailed'))
  } finally {
    drawerLoading.value = false
  }
}

async function markRead(notification: WorkflowNotification) {
  if (notification.read) return
  try {
    const updated = await markWorkflowNotificationRead(notification.id)
    const index = notifications.value.findIndex((item) => item.id === notification.id)
    if (index >= 0) notifications.value[index] = updated
  } catch {
    ElMessage.error(t('workflow.notificationReadFailed'))
  }
}

async function submitComment() {
  if (!selectedTask.value || !commentDraft.value.trim()) return
  try {
    const comment = await addResourceComment(selectedTask.value.resourceType, selectedTask.value.resourceId, commentDraft.value.trim())
    comments.value = [...comments.value, comment]
    commentDraft.value = ''
    ElMessage.success(t('workflow.commentAdded'))
  } catch {
    ElMessage.error(t('workflow.commentFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="workflow-page">
    <div class="workflow-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('workflow.eyebrow') }}</span>
        <h1>{{ t('workflow.title') }}</h1>
        <p>{{ t('workflow.subtitle') }}</p>
      </div>
      <el-button :loading="loading" round plain @click="load">
        <el-icon><Refresh /></el-icon>
        {{ t('workflow.refresh') }}
      </el-button>
    </div>

    <div class="workflow-summary-grid">
      <el-card shadow="never" class="workflow-summary-card">
        <div class="workflow-summary-icon tone-violet"><el-icon><Clock /></el-icon></div>
        <div><span>{{ t('workflow.openTasks') }}</span><strong>{{ tasks.filter((task) => task.status === 'OPEN').length }}</strong></div>
      </el-card>
      <el-card shadow="never" class="workflow-summary-card">
        <div class="workflow-summary-icon tone-mint"><el-icon><CircleCheck /></el-icon></div>
        <div><span>{{ t('workflow.approvedTasks') }}</span><strong>{{ tasks.filter((task) => task.status === 'APPROVED').length }}</strong></div>
      </el-card>
      <el-card shadow="never" class="workflow-summary-card">
        <div class="workflow-summary-icon tone-amber"><el-icon><Bell /></el-icon></div>
        <div><span>{{ t('workflow.unreadNotifications') }}</span><strong>{{ unreadNotifications }}</strong></div>
      </el-card>
    </div>

    <div class="workflow-layout">
      <el-card shadow="never" class="workflow-task-card">
        <div class="workflow-toolbar">
          <div>
            <h2>{{ t('workflow.inbox') }}</h2>
            <p>{{ t('workflow.inboxSubtitle') }}</p>
          </div>
          <el-segmented v-model="selectedStatus" :options="statusOptions" :props="{ label: 'label', value: 'value' }" />
        </div>

        <el-table v-loading="loading" :data="filteredTasks" class="workflow-table" empty-text="">
          <el-table-column :label="t('workflow.task')" min-width="230">
            <template #default="{ row }">
              <div class="workflow-task-title">
                <strong>{{ row.title }}</strong>
                <span>{{ resourceLabel(row.resourceType) }} · {{ row.resourceId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('workflow.amount')" width="130" align="right">
            <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column :label="t('workflow.status')" width="130">
            <template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="t('workflow.dueAt')" width="170">
            <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('workflow.actions')" width="240" fixed="right">
            <template #default="{ row }">
              <div class="workflow-actions">
                <el-button link type="primary" @click="openDetails(row)">{{ t('workflow.details') }}</el-button>
                <el-button v-if="row.status === 'OPEN'" link type="success" @click="handleAction(row, 'APPROVE')">{{ t('workflow.approve') }}</el-button>
                <el-button v-if="row.status === 'OPEN'" link type="danger" @click="handleAction(row, 'REJECT')">{{ t('workflow.reject') }}</el-button>
                <el-dropdown v-if="row.status === 'OPEN' || row.status === 'APPROVED'" @command="(command: 'TRANSFER' | 'COMPLETE' | 'CANCEL') => handleAction(row, command)">
                  <el-button link><el-icon><Right /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.status === 'OPEN'" command="TRANSFER">{{ t('workflow.transfer') }}</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 'APPROVED'" command="COMPLETE">{{ t('workflow.complete') }}</el-dropdown-item>
                      <el-dropdown-item command="CANCEL">{{ t('workflow.cancelTask') }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="t('workflow.empty')" />
          </template>
        </el-table>
      </el-card>

      <el-card shadow="never" class="workflow-notification-card">
        <div class="workflow-toolbar">
          <div>
            <h2>{{ t('workflow.notifications') }}</h2>
            <p>{{ t('workflow.notificationsSubtitle') }}</p>
          </div>
          <el-button link :loading="notificationsLoading" @click="refreshNotifications"><el-icon><Refresh /></el-icon></el-button>
        </div>
        <div v-loading="notificationsLoading" class="notification-list">
          <button v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.read }" type="button" @click="markRead(item)">
            <span class="notification-item-icon"><el-icon><Bell /></el-icon></span>
            <span class="notification-item-copy">
              <strong>{{ item.title }}</strong>
              <span>{{ item.message }}</span>
              <small>{{ formatDate(item.createdAt) }}</small>
            </span>
          </button>
          <el-empty v-if="!notifications.length" :description="t('workflow.noNotifications')" :image-size="70" />
        </div>
      </el-card>
    </div>

    <el-drawer v-model="drawerVisible" :title="selectedTask?.title" size="min(92vw, 560px)">
      <div v-loading="drawerLoading" class="workflow-drawer">
        <div v-if="selectedTask" class="workflow-drawer-meta">
          <el-tag :type="statusType(selectedTask.status)">{{ statusLabel(selectedTask.status) }}</el-tag>
          <span>{{ resourceLabel(selectedTask.resourceType) }} · {{ selectedTask.resourceId }}</span>
        </div>
        <h3><el-icon><Timer /></el-icon>{{ t('workflow.activity') }}</h3>
        <div v-if="activities.length" class="workflow-timeline">
          <div v-for="item in activities" :key="item.id" class="workflow-timeline-item">
            <span class="workflow-timeline-dot" />
            <div><strong>{{ item.summary }}</strong><span>{{ item.actorUserId }} · {{ formatDate(item.createdAt) }}</span></div>
          </div>
        </div>
        <el-empty v-else :description="t('workflow.noActivity')" :image-size="55" />

        <h3><el-icon><ChatDotRound /></el-icon>{{ t('workflow.comments') }}</h3>
        <div v-if="comments.length" class="workflow-comments">
          <div v-for="item in comments" :key="item.id" class="workflow-comment">
            <strong>{{ item.authorUserId }}</strong>
            <p>{{ item.body }}</p>
            <small>{{ formatDate(item.createdAt) }}</small>
          </div>
        </div>
        <el-empty v-else :description="t('workflow.noComments')" :image-size="55" />
        <el-input v-model="commentDraft" type="textarea" :rows="3" :placeholder="t('workflow.commentPlaceholder')" />
        <el-button class="workflow-comment-button" type="primary" :disabled="!commentDraft.trim()" @click="submitComment">
          {{ t('workflow.addComment') }}
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>
