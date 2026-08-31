<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getWorkspace, type WorkspaceSnapshot } from '@/api/analytics'

const router = useRouter()
const loading = ref(true)
const error = ref(false)
const workspace = ref<WorkspaceSnapshot>()

async function refresh() {
  loading.value = true
  error.value = false
  try { workspace.value = await getWorkspace() } catch { error.value = true } finally { loading.value = false }
}
function formatValue(value: number, currency?: string) {
  if (currency) return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value)
  return new Intl.NumberFormat().format(value)
}
onMounted(refresh)
</script>

<template>
  <section class="workspace-view" aria-labelledby="workspace-title">
    <header class="page-heading">
      <div><span class="eyebrow">ROLE WORKSPACE</span><h1 id="workspace-title">{{ $t('workspace.title') }}</h1><p>{{ $t('workspace.subtitle') }}</p></div>
      <el-button :loading="loading" @click="refresh">{{ $t('common.refresh') }}</el-button>
    </header>
    <el-alert v-if="error" type="error" :title="$t('workspace.loadFailed')" show-icon />
    <el-skeleton v-else-if="loading" :rows="5" animated />
    <template v-else-if="workspace">
      <div class="role-strip"><strong>{{ $t('workspace.roles') }}</strong><el-tag v-for="role in workspace.roles" :key="role">{{ role }}</el-tag></div>
      <div class="metric-grid">
        <button v-for="card in workspace.cards" :key="card.code" class="metric-card" type="button" @click="router.push(card.route)">
          <span>{{ $t(`workspace.cards.${card.code}`) }}</span><strong>{{ formatValue(card.value, card.currencyCode) }}</strong>
          <small :class="`severity-${card.severity.toLowerCase()}`">{{ $t(`workspace.severity.${card.severity}`) }}</small>
        </button>
      </div>
      <el-card class="risk-card" shadow="never">
        <template #header><strong>{{ $t('workspace.risks') }}</strong></template>
        <el-empty v-if="!workspace.risks.length" :description="$t('workspace.noRisks')" />
        <ul v-else><li v-for="risk in workspace.risks" :key="risk">{{ $t(`workspace.risk.${risk}`) }}</li></ul>
      </el-card>
    </template>
  </section>
</template>

<style scoped>
.workspace-view{display:grid;gap:22px}.page-heading{display:flex;justify-content:space-between;align-items:start}.page-heading h1{margin:5px 0}.page-heading p{margin:0;color:var(--el-text-color-secondary)}.eyebrow{font-size:12px;letter-spacing:.16em;color:var(--el-color-primary)}.role-strip{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:16px}.metric-card{display:grid;gap:10px;text-align:left;border:1px solid var(--el-border-color-light);border-radius:14px;background:var(--el-bg-color);padding:20px;cursor:pointer}.metric-card:hover,.metric-card:focus-visible{border-color:var(--el-color-primary);box-shadow:0 8px 24px rgba(28,45,70,.08)}.metric-card strong{font-size:26px}.metric-card small{font-weight:700}.severity-high{color:var(--el-color-danger)}.severity-medium{color:var(--el-color-warning)}.severity-normal{color:var(--el-color-success)}.risk-card ul{margin:0;padding-left:22px;display:grid;gap:8px}@media(max-width:640px){.page-heading{gap:12px}.metric-grid{grid-template-columns:1fr}}
</style>
<style scoped>.severity-info{color:var(--el-color-success)}.severity-warning{color:var(--el-color-warning)}.severity-danger{color:var(--el-color-danger)}</style>
