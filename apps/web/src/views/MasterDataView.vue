<script setup lang="ts">
/* global HTMLInputElement, Event, fetch, URL, document */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { Download, EditPen, Plus, Upload, Delete } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import {
  deactivateMasterData,
  getOrganizationSettings,
  importMasterData,
  listMasterData,
  saveMasterData,
  saveOrganizationSettings,
  type MasterDataRecord,
  type MasterDataResource,
} from '@/api/master-data'

type PanelKey = MasterDataResource | 'settings'
type FieldType = 'text' | 'number' | 'select' | 'date' | 'switch'

interface FieldDefinition {
  key: string
  label: string
  type?: FieldType
  options?: string[]
}

interface ResourceDefinition {
  key: MasterDataResource
  label: string
  description: string
  columns: string[]
  fields: FieldDefinition[]
  defaults: Record<string, unknown>
}

const { t } = useI18n()
const panel = ref<PanelKey>('customers')
const query = ref('')
const page = ref(1)
const size = 10
const rows = ref<MasterDataRecord[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const settingsLoading = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const fileInput = ref<HTMLInputElement>()
const form = reactive<Record<string, unknown>>({})

const resources = computed<ResourceDefinition[]>(() => [
  {
    key: 'customers',
    label: t('masterData.customers'),
    description: t('masterData.customersDescription'),
    columns: ['code', 'name', 'currencyCode', 'paymentTermsDays', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'currencyCode', label: t('masterData.currency') },
      { key: 'paymentTermsDays', label: t('masterData.paymentTerms'), type: 'number' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { currencyCode: 'USD', paymentTermsDays: 30, active: true },
  },
  {
    key: 'suppliers',
    label: t('masterData.suppliers'),
    description: t('masterData.suppliersDescription'),
    columns: ['code', 'name', 'currencyCode', 'paymentTermsDays', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'currencyCode', label: t('masterData.currency') },
      { key: 'paymentTermsDays', label: t('masterData.paymentTerms'), type: 'number' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { currencyCode: 'USD', paymentTermsDays: 30, active: true },
  },
  {
    key: 'items',
    label: t('masterData.items'),
    description: t('masterData.itemsDescription'),
    columns: ['code', 'name', 'type', 'unit', 'salesPrice', 'taxRate', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'type', label: t('masterData.type'), type: 'select', options: ['GOODS', 'SERVICE'] },
      { key: 'unit', label: t('masterData.unit') },
      { key: 'salesPrice', label: t('masterData.salesPrice'), type: 'number' },
      { key: 'purchasePrice', label: t('masterData.purchasePrice'), type: 'number' },
      { key: 'averageCost', label: t('masterData.averageCost'), type: 'number' },
      { key: 'taxRate', label: t('masterData.taxRate'), type: 'number' },
      { key: 'inventoryManaged', label: t('masterData.inventoryManaged'), type: 'switch' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { type: 'GOODS', unit: 'pcs', salesPrice: 0, purchasePrice: 0, averageCost: 0, taxRate: 0, inventoryManaged: true, active: true },
  },
  {
    key: 'warehouses',
    label: t('masterData.warehouses'),
    description: t('masterData.warehousesDescription'),
    columns: ['code', 'name', 'address', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'address', label: t('masterData.address') },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { active: true },
  },
  {
    key: 'currencies',
    label: t('masterData.currencies'),
    description: t('masterData.currenciesDescription'),
    columns: ['code', 'name', 'symbol', 'decimalPlaces', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'symbol', label: t('masterData.symbol') },
      { key: 'decimalPlaces', label: t('masterData.decimalPlaces'), type: 'number' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { decimalPlaces: 2, active: true },
  },
  {
    key: 'exchange-rates',
    label: t('masterData.exchangeRates'),
    description: t('masterData.exchangeRatesDescription'),
    columns: ['baseCurrencyCode', 'quoteCurrencyCode', 'rate', 'effectiveDate', 'active'],
    fields: [
      { key: 'baseCurrencyCode', label: t('masterData.baseCurrency') },
      { key: 'quoteCurrencyCode', label: t('masterData.quoteCurrency') },
      { key: 'rate', label: t('masterData.rate'), type: 'number' },
      { key: 'effectiveDate', label: t('masterData.effectiveDate'), type: 'date' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { rate: 1, effectiveDate: new Date().toISOString().slice(0, 10), active: true },
  },
  {
    key: 'tax-rates',
    label: t('masterData.taxRates'),
    description: t('masterData.taxRatesDescription'),
    columns: ['code', 'name', 'rate', 'effectiveDate', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'rate', label: t('masterData.ratePercent'), type: 'number' },
      { key: 'effectiveDate', label: t('masterData.effectiveDate'), type: 'date' },
      { key: 'exempt', label: t('masterData.exempt'), type: 'switch' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { rate: 0, effectiveDate: new Date().toISOString().slice(0, 10), exempt: false, active: true },
  },
  {
    key: 'accounts',
    label: t('masterData.accounts'),
    description: t('masterData.accountsDescription'),
    columns: ['code', 'name', 'type', 'parentCode', 'postingAllowed', 'active'],
    fields: [
      { key: 'code', label: t('masterData.code') },
      { key: 'name', label: t('masterData.name') },
      { key: 'type', label: t('masterData.type'), type: 'select', options: ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'] },
      { key: 'parentCode', label: t('masterData.parentCode') },
      { key: 'postingAllowed', label: t('masterData.postingAllowed'), type: 'switch' },
      { key: 'active', label: t('masterData.active'), type: 'switch' },
    ],
    defaults: { postingAllowed: true, active: true },
  },
])

const activeDefinition = computed(() => resources.value.find((item) => item.key === panel.value) ?? resources.value[0])
const isSettings = computed(() => panel.value === 'settings')
const supportsImport = computed(() => ['customers', 'suppliers', 'items', 'warehouses'].includes(panel.value))
const supportsExport = computed(() => !isSettings.value)
const panelTabs = computed(() => [...resources.value.map((item) => ({ key: item.key as PanelKey, label: item.label })), { key: 'settings' as PanelKey, label: t('masterData.organizationSettings') }])

async function loadRows() {
  if (isSettings.value) {
    settingsLoading.value = true
    try {
      Object.assign(form, await getOrganizationSettings())
    } finally {
      settingsLoading.value = false
    }
    return
  }
  loading.value = true
  try {
    const result = await listMasterData(panel.value as MasterDataResource, query.value, page.value - 1, size)
    rows.value = result.content
    total.value = result.totalElements
  } catch {
    ElMessage.error(t('masterData.loadFailed'))
  } finally {
    loading.value = false
  }
}

function selectPanel(next: PanelKey) {
  panel.value = next
  query.value = ''
  page.value = 1
  rows.value = []
  resetForm()
  void loadRows()
}

function handleTabChange(value: string | number) {
  selectPanel(String(value) as PanelKey)
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
}

function openCreate() {
  resetForm()
  Object.assign(form, activeDefinition.value.defaults)
  editingId.value = undefined
  dialogVisible.value = true
}

function openEdit(row: MasterDataRecord) {
  resetForm()
  Object.assign(form, row)
  editingId.value = row.id
  dialogVisible.value = true
}

async function submitForm() {
  if (isSettings.value) {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    await saveOrganizationSettings({
      name: String(form.name ?? ''),
      baseCurrencyCode: String(form.baseCurrencyCode ?? ''),
      timezone: String(form.timezone ?? ''),
      approvalThreshold: Number(form.approvalThreshold ?? 0),
      defaultTaxRate: Number(form.defaultTaxRate ?? 0),
    })
    ElMessage.success(t('masterData.saved'))
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await saveMasterData(activeDefinition.value.key, { ...form } as Partial<MasterDataRecord>, editingId.value)
    ElMessage.success(t('masterData.saved'))
    dialogVisible.value = false
    await loadRows()
  } catch (error: unknown) {
    const messageKey = axios.isAxiosError(error) ? error.response?.data?.messageKey : undefined
    ElMessage.error(messageKey ? t(messageKey) : t('masterData.saveFailed'))
  }
}

async function removeRow(row: MasterDataRecord) {
  try {
    await deactivateMasterData(activeDefinition.value.key, row.id)
    ElMessage.success(t('masterData.deactivated'))
    await loadRows()
  } catch {
    ElMessage.error(t('masterData.saveFailed'))
  }
}

function triggerImport() {
  fileInput.value?.click()
}

async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || isSettings.value) return
  try {
    const result = await importMasterData(activeDefinition.value.key, file)
    ElMessage.success(t('masterData.importSummary', { imported: result.imported, rejected: result.rejected }))
    await loadRows()
  } catch {
    ElMessage.error(t('masterData.importFailed'))
  } finally {
    input.value = ''
  }
}

async function downloadExport() {
  if (isSettings.value) return
  try {
    const response = await fetch(`/api/v1/masters/${activeDefinition.value.key}/export.csv`, { credentials: 'include' })
    if (!response.ok) throw new Error('export failed')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${activeDefinition.value.key}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('masterData.exportFailed'))
  }
}

function columnLabel(column: string) {
  return t(`masterData.${column}`)
}

function displayValue(row: MasterDataRecord, column: string) {
  const value = row[column]
  if (typeof value === 'boolean') return value ? t('masterData.yes') : t('masterData.no')
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

onMounted(() => void loadRows())
</script>

<template>
  <section class="master-data-page">
    <div class="master-data-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('masterData.eyebrow') }}</span>
        <h1>{{ t('masterData.title') }}</h1>
        <p>{{ t('masterData.subtitle') }}</p>
      </div>
      <div class="master-data-actions">
        <input ref="fileInput" class="visually-hidden" type="file" accept=".csv,text/csv" @change="handleImport" />
        <el-button v-if="supportsExport" plain @click="downloadExport">
          <el-icon><Download /></el-icon>{{ t('masterData.export') }}
        </el-button>
        <el-button v-if="supportsImport" plain @click="triggerImport">
          <el-icon><Upload /></el-icon>{{ t('masterData.import') }}
        </el-button>
        <el-button v-if="isSettings" type="primary" @click="submitForm">
          {{ t('masterData.saveSettings') }}
        </el-button>
        <el-button v-else type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>{{ t('masterData.create') }}
        </el-button>
      </div>
    </div>

    <el-card class="master-data-card" shadow="never">
      <el-tabs :model-value="panel" @tab-change="handleTabChange">
        <el-tab-pane v-for="tab in panelTabs" :key="tab.key" :name="tab.key" :label="tab.label" />
      </el-tabs>

      <template v-if="isSettings">
        <el-skeleton v-if="settingsLoading" :rows="6" animated />
        <el-form v-else ref="formRef" class="settings-form" :model="form" label-position="top">
          <el-form-item :label="t('masterData.organizationName')" prop="name" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item :label="t('masterData.baseCurrency')" prop="baseCurrencyCode" required>
            <el-input v-model="form.baseCurrencyCode" />
          </el-form-item>
          <el-form-item :label="t('masterData.timezone')" prop="timezone" required>
            <el-input v-model="form.timezone" />
          </el-form-item>
          <el-form-item :label="t('masterData.approvalThreshold')" prop="approvalThreshold" required>
            <el-input-number v-model="form.approvalThreshold" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item :label="t('masterData.defaultTaxRate')" prop="defaultTaxRate" required>
            <el-input-number v-model="form.defaultTaxRate" :min="0" :max="100" :precision="4" />
          </el-form-item>
        </el-form>
      </template>

      <template v-else>
        <div class="master-data-toolbar">
          <div>
            <strong>{{ activeDefinition.label }}</strong>
            <span>{{ activeDefinition.description }}</span>
          </div>
          <el-input v-model="query" clearable :placeholder="t('masterData.searchPlaceholder')" @keyup.enter="page = 1; loadRows()" @clear="page = 1; loadRows()" />
        </div>
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column v-for="column in activeDefinition.columns" :key="column" :prop="column" :label="columnLabel(column)" min-width="130">
            <template #default="scope">{{ displayValue(scope.row, column) }}</template>
          </el-table-column>
          <el-table-column :label="t('masterData.actions')" fixed="right" width="140">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)"><el-icon><EditPen /></el-icon></el-button>
              <el-popconfirm :title="t('masterData.confirmDeactivate')" @confirm="removeRow(scope.row)">
                <template #reference><el-button link type="danger"><el-icon><Delete /></el-icon></el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="master-data-pagination">
          <el-pagination v-model:current-page="page" layout="total, prev, pager, next" :page-size="size" :total="total" @current-change="loadRows" />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('masterData.edit') : t('masterData.create')" width="min(620px, 92vw)">
      <el-form ref="formRef" :model="form" label-position="top">
        <div class="master-data-form-grid">
          <el-form-item v-for="field in activeDefinition.fields" :key="field.key" :label="field.label" :prop="field.key" required>
            <el-switch v-if="field.type === 'switch'" v-model="form[field.key]" />
            <el-select v-else-if="field.type === 'select'" v-model="form[field.key]" class="full-width">
              <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
            </el-select>
            <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" class="full-width" :min="0" />
            <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" class="full-width" type="date" value-format="YYYY-MM-DD" />
            <el-input v-else v-model="form[field.key]" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="submitForm">{{ t('masterData.save') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
