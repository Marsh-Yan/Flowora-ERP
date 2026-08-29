<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  platformApi,
  type Department,
  type Organization,
  type Permission,
  type PlatformUser,
  type Role,
} from '@/api/platform'
import PermissionGate from '@/components/PermissionGate.vue'

const { t } = useI18n()
const loading = ref(false)
const organizations = ref<Organization[]>([])
const departments = ref<Department[]>([])
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const users = ref<PlatformUser[]>([])
const activeTab = ref('users')

const activeUsers = computed(() => users.value.filter((user) => user.status === 'ACTIVE').length)
const sensitivePermissions = computed(() => permissions.value.filter((permission) => permission.sensitive).length)

async function load() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      platformApi.organizations(),
      platformApi.departments(),
      platformApi.roles(),
      platformApi.permissions(),
      platformApi.users(),
    ])
    if (results[0].status === 'fulfilled') organizations.value = results[0].value
    if (results[1].status === 'fulfilled') departments.value = results[1].value
    if (results[2].status === 'fulfilled') roles.value = results[2].value
    if (results[3].status === 'fulfilled') permissions.value = results[3].value
    if (results[4].status === 'fulfilled') users.value = results[4].value
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="module-page platform-settings">
    <header class="module-header">
      <div>
        <span class="eyebrow">{{ t('platform.eyebrow') }}</span>
        <h1>{{ t('platform.title') }}</h1>
        <p>{{ t('platform.subtitle') }}</p>
      </div>
      <PermissionGate permission="organization:configure">
        <el-button type="primary">{{ t('platform.createOrganization') }}</el-button>
      </PermissionGate>
    </header>

    <div class="kpi-grid">
      <article class="kpi-card"><span>{{ t('platform.organizations') }}</span><strong>{{ organizations.length }}</strong></article>
      <article class="kpi-card"><span>{{ t('platform.activeUsers') }}</span><strong>{{ activeUsers }}</strong></article>
      <article class="kpi-card"><span>{{ t('platform.roles') }}</span><strong>{{ roles.length }}</strong></article>
      <article class="kpi-card"><span>{{ t('platform.sensitivePermissions') }}</span><strong>{{ sensitivePermissions }}</strong></article>
    </div>

    <el-card shadow="never" class="content-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('platform.users')" name="users">
          <el-table :data="users" row-key="id">
            <el-table-column prop="displayName" :label="t('platform.displayName')" min-width="160" />
            <el-table-column prop="username" :label="t('platform.username')" min-width="220" />
            <el-table-column prop="status" :label="t('common.status')" width="120" />
            <el-table-column :label="t('platform.roleCount')" width="120">
              <template #default="scope">{{ scope.row.roleIds.length }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="t('platform.roles')" name="roles">
          <el-table :data="roles" row-key="id">
            <el-table-column prop="code" :label="t('common.code')" width="160" />
            <el-table-column prop="name" :label="t('common.name')" min-width="180" />
            <el-table-column prop="dataScope" :label="t('platform.dataScope')" width="140" />
            <el-table-column :label="t('platform.permissionCount')" width="140">
              <template #default="scope">{{ scope.row.permissions.length }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="t('platform.departments')" name="departments">
          <el-table :data="departments" row-key="id">
            <el-table-column prop="code" :label="t('common.code')" width="160" />
            <el-table-column prop="name" :label="t('common.name')" min-width="200" />
            <el-table-column prop="active" :label="t('common.active')" width="120" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="t('platform.permissions')" name="permissions">
          <el-table :data="permissions" row-key="code">
            <el-table-column prop="code" :label="t('platform.permissionCode')" min-width="200" />
            <el-table-column prop="description" :label="t('common.description')" min-width="280" />
            <el-table-column prop="sensitive" :label="t('platform.sensitive')" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>
