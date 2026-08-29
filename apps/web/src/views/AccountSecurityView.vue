<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { platformApi, type ActiveSession } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const sessions = ref<ActiveSession[]>([])
const loading = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const enrollment = ref<{ secret: string; otpauthUri: string } | null>(null)
const mfaCode = ref('')
const recoveryCodes = ref<string[]>([])

async function loadSessions() {
  loading.value = true
  try {
    sessions.value = await platformApi.sessions()
  } finally {
    loading.value = false
  }
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error(t('security.passwordMismatch'))
    return
  }
  await authStore.ensureCsrf()
  await platformApi.changePassword(passwordForm.currentPassword, passwordForm.newPassword)
  ElMessage.success(t('security.passwordChanged'))
  await authStore.logout()
}

async function startMfa() {
  await authStore.ensureCsrf()
  enrollment.value = await platformApi.startMfaEnrollment()
}

async function confirmMfa() {
  await authStore.ensureCsrf()
  recoveryCodes.value = (await platformApi.confirmMfaEnrollment(mfaCode.value)).codes
  enrollment.value = null
  ElMessage.success(t('security.mfaEnabled'))
}

async function revoke(session: ActiveSession) {
  await authStore.ensureCsrf()
  await platformApi.revokeSession(session.id, 'User requested revocation')
  await loadSessions()
}

onMounted(loadSessions)
</script>

<template>
  <section class="module-page account-security">
    <header class="module-header">
      <div>
        <span class="eyebrow">{{ t('security.eyebrow') }}</span>
        <h1>{{ t('security.title') }}</h1>
        <p>{{ t('security.subtitle') }}</p>
      </div>
    </header>

    <div class="security-grid">
      <el-card shadow="never" class="content-card">
        <template #header><strong>{{ t('security.password') }}</strong></template>
        <el-form label-position="top" @submit.prevent="changePassword">
          <el-form-item :label="t('security.currentPassword')"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
          <el-form-item :label="t('security.newPassword')"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
          <el-form-item :label="t('security.confirmPassword')"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
          <el-button type="primary" native-type="submit">{{ t('security.changePassword') }}</el-button>
        </el-form>
      </el-card>

      <el-card shadow="never" class="content-card">
        <template #header><strong>{{ t('security.mfa') }}</strong></template>
        <p>{{ t('security.mfaDescription') }}</p>
        <el-button v-if="!enrollment" @click="startMfa">{{ t('security.startMfa') }}</el-button>
        <div v-else class="mfa-enrollment">
          <code>{{ enrollment.secret }}</code>
          <el-input v-model="mfaCode" :placeholder="t('security.verificationCode')" maxlength="6" />
          <el-button type="primary" @click="confirmMfa">{{ t('security.confirmMfa') }}</el-button>
        </div>
        <el-alert v-if="recoveryCodes.length" type="warning" :closable="false" :title="t('security.saveRecoveryCodes')">
          <div class="recovery-codes"><code v-for="code in recoveryCodes" :key="code">{{ code }}</code></div>
        </el-alert>
      </el-card>
    </div>

    <el-card v-loading="loading" shadow="never" class="content-card session-card">
      <template #header><strong>{{ t('security.activeSessions') }}</strong></template>
      <el-table :data="sessions" row-key="id">
        <el-table-column prop="lastAccessedAt" :label="t('security.lastActive')" min-width="210" />
        <el-table-column prop="createdAt" :label="t('security.createdAt')" min-width="210" />
        <el-table-column :label="t('common.status')" width="120">
          <template #default="scope">{{ scope.row.current ? t('security.currentSession') : t('common.active') }}</template>
        </el-table-column>
        <el-table-column width="140">
          <template #default="scope"><el-button link type="danger" @click="revoke(scope.row)">{{ t('security.revoke') }}</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
