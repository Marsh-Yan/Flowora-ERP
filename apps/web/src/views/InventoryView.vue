<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Plus, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'
import { createStockAdjustment, getStockSummary, listStockBalances, listStockLedger, receivePurchaseOrder, transferStock, type PageResponse, type StockSummary, type StockBalance, type StockLedgerEntry } from '@/api/inventory'
import { listAvailability, traceInventory, type Availability, type TraceResult } from '@/api/trade'
import { listPurchaseOrders, type PurchaseOrder } from '@/api/procurement'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canPost = computed(() => auth.hasPermission('inventory:post') && auth.hasPermission('master:view'))
const canReceive = computed(() => canPost.value && auth.hasPermission('procurement:view'))
const loaded = ref(false)
const { t, locale } = useI18n()
const activeTab = ref<'balances' | 'advanced' | 'ledger' | 'trace'>('balances')
const loading = ref(false)
const dialogVisible = ref(false)
const posting = ref(false)
const dialogType = ref<'receipt' | 'adjustment' | 'transfer'>('receipt')
const pageSize = 50
const balancePage = usePagedRows<StockBalance>((page) => listStockBalances('', page, pageSize))
const ledgerPage = usePagedRows<StockLedgerEntry>((page) => listStockLedger('', '', page, pageSize))
const balances = balancePage.rows
const ledger = ledgerPage.rows
const summary = ref<StockSummary | null>(null)
let summaryRequest = 0
let loadRequest = 0
const availability = ref<Availability[]>([])
const traceResult = ref<TraceResult | null>(null)
const traceItemId = ref('')
const traceLoading = ref(false)
const orders = ref<PurchaseOrder[]>([])
const warehouses = ref<MasterDataRecord[]>([])
const items = ref<MasterDataRecord[]>([])
const receiptForm = reactive({ purchaseOrderId: '', purchaseOrderLineId: '', warehouseId: '', quantity: 1, unitCost: 0 })
const selectedReceiptOrder = computed(() => orders.value.find(order => order.id === receiptForm.purchaseOrderId))
const receiptReady = computed(() => canReceive.value && !!selectedReceiptOrder.value?.lineId && !!receiptForm.warehouseId && Number.isFinite(receiptForm.quantity) && receiptForm.quantity > 0 && receiptForm.quantity <= selectedReceiptOrder.value.remainingQuantity && Number.isFinite(receiptForm.unitCost) && receiptForm.unitCost >= 0)
const adjustmentForm = reactive({ warehouseId: '', itemId: '', quantityDelta: 0, unitCost: 0, reason: '' })
const transferForm = reactive({ sourceWarehouseId: '', targetWarehouseId: '', itemId: '', quantity: 1, unitCost: 0 })

function masterName(rows: MasterDataRecord[], id: string) {
  return rows.find((row) => row.id === id)?.name ?? id
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function usePagedRows<T>(fetchPage: (page: number) => Promise<PageResponse<T>>) {
  const rows = shallowRef<T[]>([])
  const state = reactive({ page: 1, total: 0, loading: false, loaded: false, error: false })
  let request = 0
  async function loadPage(page: number) {
    const current = ++request
    state.page = page
    state.loading = true; state.loaded = false; state.error = false; rows.value = []
    try {
      const response = await fetchPage(page - 1)
      if (current !== request) return
      const lastPage = Math.max(1, response.totalPages)
      if (page > lastPage) { await loadPage(lastPage); return }
      rows.value = response.content
      state.page = response.page + 1; state.total = response.totalElements; state.loaded = true
    } catch {
      if (current !== request) return
      state.error = true
      ElMessage.error(t('inventory.loadFailed'))
    } finally {
      if (current === request) state.loading = false
    }
  }
  return { rows, state, loadPage, invalidate: () => { request++ } }
}

async function loadSummary() {
  const current = ++summaryRequest
  summary.value = null
  try {
    const result = await getStockSummary()
    if (current === summaryRequest) summary.value = result
  } catch {
    if (current === summaryRequest) ElMessage.error(t('inventory.loadFailed'))
  }
}

async function load() {
  const current = ++loadRequest
  loading.value = true; loaded.value = false
  availability.value = []; orders.value = []; warehouses.value = []; items.value = []
  try {
    await Promise.all([
      loadSummary(), balancePage.loadPage(1), ledgerPage.loadPage(1),
      listAvailability().then(rows => {
        if (current === loadRequest) { availability.value = rows; loaded.value = true }
      }).catch(() => { if (current === loadRequest) ElMessage.error(t('inventory.loadFailed')) }),
    ])
    if (current !== loadRequest) return
    // Optional pickers must not prevent inventory readers from seeing their data.
    const optional = []
    if (auth.hasPermission('master:view')) optional.push((async () => {
      const [warehouseRows, itemRows] = await Promise.all([listMasterData('warehouses', '', 0, 100), listMasterData('items', '', 0, 100)])
      if (current === loadRequest) { warehouses.value = warehouseRows.content; items.value = itemRows.content }
    })())
    if (canReceive.value) optional.push((async () => {
      const orderRows = await listPurchaseOrders()
      if (current === loadRequest) orders.value = orderRows.content.filter(order => !!order.lineId && order.remainingQuantity > 0 && ['CONFIRMED', 'APPROVED', 'PARTIALLY_RECEIVED'].includes(order.status))
    })())
    const results = await Promise.allSettled(optional)
    if (current === loadRequest && results.some(result => result.status === 'rejected')) ElMessage.error(t('inventory.loadFailed'))
  } finally {
    if (current === loadRequest) loading.value = false
  }
}

onBeforeUnmount(() => { loadRequest++; summaryRequest++; balancePage.invalidate(); ledgerPage.invalidate() })

function openDialog(type: 'receipt' | 'adjustment' | 'transfer') {
  if (posting.value || loading.value || !canPost.value) return
  if (type === 'receipt') {
    selectOrder(orders.value[0]?.id ?? '')
    if (!receiptReady.value) return
  }
  dialogType.value = type
  dialogVisible.value = true
}

function selectOrder(id: string) {
  const order = orders.value.find((item) => item.id === id)
  Object.assign(receiptForm, {
    purchaseOrderId: order?.id ?? '', purchaseOrderLineId: order?.lineId ?? '', warehouseId: order?.warehouseId ?? '',
    quantity: order?.remainingQuantity ?? 0, unitCost: order?.unitPrice ?? 0,
  })
}

async function submit() {
  if (posting.value || !canPost.value || (dialogType.value === 'receipt' && !receiptReady.value)) return
  posting.value = true
  try {
    if (dialogType.value === 'receipt') await receivePurchaseOrder({ ...receiptForm })
    if (dialogType.value === 'adjustment') await createStockAdjustment({ ...adjustmentForm })
    if (dialogType.value === 'transfer') await transferStock({ ...transferForm })
    dialogVisible.value = false
    ElMessage.success(t('inventory.saved'))
    await load()
  } catch {
    ElMessage.error(t('inventory.saveFailed'))
  } finally { posting.value = false }
}


async function runTrace() {
  if (!traceItemId.value) return
  traceLoading.value = true
  try {
    traceResult.value = await traceInventory(traceItemId.value)
  } catch {
    ElMessage.error(t('inventory.loadFailed'))
  } finally {
    traceLoading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="operations-page">
    <div class="operations-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('inventory.eyebrow') }}</span>
        <h1>{{ t('inventory.title') }}</h1>
        <p>{{ t('inventory.subtitle') }}</p>
      </div>
      <div class="operations-actions">
        <el-button round plain :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('inventory.refresh') }}</el-button>
        <el-button v-if="canPost" round plain :disabled="loading || posting" @click="openDialog('transfer')"><el-icon><ArrowRight /></el-icon>{{ t('inventory.transfer') }}</el-button>
        <el-button v-if="canPost" round plain :disabled="loading || posting" @click="openDialog('adjustment')"><el-icon><Plus /></el-icon>{{ t('inventory.adjustment') }}</el-button>
        <el-button v-if="canReceive" type="primary" round :disabled="loading || posting || !orders.length" @click="openDialog('receipt')"><el-icon><Plus /></el-icon>{{ t('inventory.receive') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-blue"><span>Σ</span></div><div><span>{{ t('inventory.totalValue') }}</span><strong>{{ summary ? summary.inventoryValue.toFixed(2) : '—' }}</strong></div></el-card>
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-mint"><span>Q</span></div><div><span>{{ t('inventory.skuCount') }}</span><strong>{{ summary ? summary.balanceCount : '—' }}</strong></div></el-card>
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-amber"><span>↗</span></div><div><span>{{ t('inventory.ledgerCount') }}</span><strong>{{ summary ? summary.ledgerCount : '—' }}</strong></div></el-card>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('inventory.balances')" name="balances">
          <el-table v-loading="balancePage.state.loading" :data="balances" empty-text="">
            <el-table-column :label="t('inventory.warehouse')" min-width="170"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
            <el-table-column :label="t('inventory.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantity" :label="t('inventory.quantity')" width="130" />
            <el-table-column prop="averageCost" :label="t('inventory.averageCost')" width="150" />
            <el-table-column prop="inventoryValue" :label="t('inventory.inventoryValue')" width="160" />
          </el-table>
          <el-empty v-if="balancePage.state.loaded && !balances.length && !balancePage.state.loading" :description="t('inventory.emptyBalances')" />
          <div v-if="balancePage.state.error" role="alert"><span>{{ t('inventory.loadFailed') }}</span><el-button @click="balancePage.loadPage(balancePage.state.page)">{{ t('inventory.refresh') }}</el-button></div>
          <el-pagination v-if="balancePage.state.loaded || balancePage.state.loading" data-testid="balances-pagination" :current-page="balancePage.state.page" :page-size="pageSize" :total="balancePage.state.total" :disabled="balancePage.state.loading || loading" :pager-count="5" layout="total, prev, pager, next" @update:current-page="balancePage.loadPage" />
        </el-tab-pane>
        <el-tab-pane :label="t('inventory.advanced', 'Available stock')" name="advanced">
          <el-table v-loading="loading" :data="availability" empty-text="">
            <el-table-column :label="t('inventory.warehouse')" min-width="150"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
            <el-table-column prop="locationId" :label="t('inventory.location', 'Location')" min-width="120" />
            <el-table-column :label="t('inventory.item')" min-width="150"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="lotId" :label="t('inventory.lot', 'Lot')" min-width="120" />
            <el-table-column prop="serialId" :label="t('inventory.serial', 'Serial')" min-width="120" />
            <el-table-column prop="onHand" :label="t('inventory.onHand', 'On hand')" width="110" />
            <el-table-column prop="reserved" :label="t('inventory.reserved', 'Reserved')" width="110" />
            <el-table-column prop="available" :label="t('inventory.available', 'Available')" width="110" />
            <el-table-column :label="t('inventory.frozen', 'Frozen')" width="100"><template #default="{ row }"><el-tag :type="row.frozen ? 'danger' : 'success'">{{ row.frozen ? 'Yes' : 'No' }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('inventory:trace') && auth.hasPermission('master:view')" :label="t('inventory.trace', 'Trace')" name="trace">
          <div class="operations-actions">
            <el-select v-model="traceItemId" filterable :placeholder="t('inventory.item')" style="width: 280px"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select>
            <el-button type="primary" :loading="traceLoading" @click="runTrace">{{ t('inventory.trace', 'Trace') }}</el-button>
          </div>
          <el-timeline v-if="traceResult" style="margin-top: 24px">
            <el-timeline-item v-for="movement in traceResult.movements" :key="movement.id" :timestamp="formatDate(movement.postedAt)" placement="top">
              <el-card shadow="never"><strong>{{ movement.number }} · {{ movement.movementType }}</strong><p>{{ movement.sourceType }} / {{ movement.sourceId }}</p><el-tag v-for="line in movement.lines" :key="line.id" style="margin-right: 8px">{{ line.quantity }} × {{ masterName(items, line.itemId) }}</el-tag></el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else :description="t('inventory.traceHint', 'Choose an item to inspect its movement chain')" />
        </el-tab-pane>
        <el-tab-pane :label="t('inventory.ledger')" name="ledger">
          <el-table v-loading="ledgerPage.state.loading" :data="ledger" empty-text="">
            <el-table-column :label="t('inventory.movement')" width="150"><template #default="{ row }"><el-tag>{{ t(`inventory.movementTypes.${row.movementType}`, row.movementType) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('inventory.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantityDelta" :label="t('inventory.quantityDelta')" width="140" />
            <el-table-column prop="unitCost" :label="t('inventory.unitCost')" width="130" />
            <el-table-column prop="documentId" :label="t('inventory.document')" width="180" />
            <el-table-column :label="t('inventory.createdAt')" width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="ledgerPage.state.loaded && !ledger.length && !ledgerPage.state.loading" :description="t('inventory.emptyLedger')" />
          <div v-if="ledgerPage.state.error" role="alert"><span>{{ t('inventory.loadFailed') }}</span><el-button @click="ledgerPage.loadPage(ledgerPage.state.page)">{{ t('inventory.refresh') }}</el-button></div>
          <el-pagination v-if="ledgerPage.state.loaded || ledgerPage.state.loading" data-testid="ledger-pagination" :current-page="ledgerPage.state.page" :page-size="pageSize" :total="ledgerPage.state.total" :disabled="ledgerPage.state.loading || loading" :pager-count="5" layout="total, prev, pager, next" @update:current-page="ledgerPage.loadPage" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t(`inventory.dialog.${dialogType}`)" width="min(560px, calc(100vw - 32px))" destroy-on-close :close-on-click-modal="!posting" :close-on-press-escape="!posting" :show-close="!posting">
      <el-form v-if="dialogType === 'receipt'" label-position="top">
        <el-form-item :label="t('inventory.purchaseOrder')"><el-select v-model="receiptForm.purchaseOrderId" :disabled="posting" class="full-width" @change="selectOrder"><el-option v-for="row in orders" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
        <div class="operations-form-grid"><el-form-item :label="t('inventory.quantity')"><el-input-number v-model="receiptForm.quantity" :disabled="posting" :max="selectedReceiptOrder?.remainingQuantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="receiptForm.unitCost" :disabled="posting" :min="0" :precision="4" class="full-width" /></el-form-item></div>
      </el-form>
      <el-form v-else-if="dialogType === 'adjustment'" label-position="top"><div class="operations-form-grid"><el-form-item :label="t('inventory.warehouse')"><el-select v-model="adjustmentForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.item')"><el-select v-model="adjustmentForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.quantityDelta')"><el-input-number v-model="adjustmentForm.quantityDelta" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="adjustmentForm.unitCost" :min="0" :precision="4" class="full-width" /></el-form-item></div><el-form-item :label="t('inventory.reason')"><el-input v-model="adjustmentForm.reason" type="textarea" :rows="3" /></el-form-item></el-form>
      <el-form v-else label-position="top"><div class="operations-form-grid"><el-form-item :label="t('inventory.sourceWarehouse')"><el-select v-model="transferForm.sourceWarehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.targetWarehouse')"><el-select v-model="transferForm.targetWarehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.item')"><el-select v-model="transferForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.quantity')"><el-input-number v-model="transferForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="transferForm.unitCost" :min="0" :precision="4" class="full-width" /></el-form-item></div></el-form>
      <template #footer><el-button :disabled="posting" @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" :loading="posting" :disabled="posting || (dialogType === 'receipt' && !receiptReady)" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>
