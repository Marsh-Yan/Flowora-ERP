<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const form = reactive({
  username: 'operator@demo.flowora',
  password: 'Demo123!',
})

const rules = reactive<FormRules<typeof form>>({
  username: [{ required: true, message: t('auth.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('auth.passwordRequired'), trigger: 'blur' }],
})

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  try {
    await authStore.login(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error: unknown) {
    const messageKey = axios.isAxiosError(error) ? error.response?.data?.messageKey : undefined
    ElMessage.error(messageKey ? t(messageKey) : t('auth.loginFailed'))
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <div class="brand-mark"><span /><span /><span /></div>
        <div>
          <strong>Flowora</strong>
          <small>ERP WORKSPACE</small>
        </div>
      </div>

      <div class="login-heading">
        <span class="eyebrow">{{ t('auth.eyebrow') }}</span>
        <h1>{{ t('auth.title') }}</h1>
        <p>{{ t('auth.subtitle') }}</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('auth.username')" prop="username">
          <el-input v-model="form.username" size="large" :placeholder="t('auth.usernamePlaceholder')">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('auth.password')" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            :placeholder="t('auth.passwordPlaceholder')"
            @keyup.enter="submit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button class="login-submit" type="primary" size="large" :loading="authStore.loading" @click="submit">
          {{ t('auth.signIn') }}
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-form>

      <div class="login-demo-hint">
        <span>{{ t('auth.demoHint') }}</span>
        <code>operator@demo.flowora</code>
        <code>Demo123!</code>
      </div>
    </section>
  </main>
</template>
