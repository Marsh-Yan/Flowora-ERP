<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { createPurchaseRequest, listPurchaseOrders, listPurchaseRequests, type PurchaseOrder, type PurchaseRequest } from '@/api/procurement'
import { createPurchaseOrderV2, type TradeLineInput } from '@/api/trade'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'

import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const canReadShared = computed(() => auth.user?.dataScope === 'ALL' && auth.hasPermission('procurement:view'))
const canReadMaster = computed(() => auth.hasPermission('master:view'))
const canCreateOrder = computed(() => auth.hasPermission('procurement:create') && canReadMaster.value)
const canCreateShared = computed(() => canReadShared.value && canReadMaster.value &&
  ['procurement:create', 'procurement:submit', 'workflow:view', 'workflow:submit'].every(auth.hasPermission))
const sharedFailed = ref(false)
const ordersFailed = ref(false)
const activeTab = ref<'requests' | 'orders'>(canReadShared.value ? 'requests' : 'orders')
const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref<'request' | 'order'>('request')
const requests = ref<PurchaseRequest[]>([])
const orders = ref<PurchaseOrder[]>([])
const suppliers = ref<MasterDataRecord[]>([])
const warehouses = ref<MasterDataRecord[]>([])
const items = ref<MasterDataRecord[]>([])
const requestForm = reactive({ supplierId: '', warehouseId: '', itemId: '', quantity: 1, estimatedUnitCost: 0, note: '' })
const orderForm = reactive({ purchaseRequestId: '', supplierId: '', warehouseId: '', currencyCode: 'CNY', expectedDate: '', note: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 } as TradeLineInput] })

function statusType(status: PurchaseRequest['status']) {
  if (status === 'APPROVED') return 'success'
  if (status === 'PARTIALLY_RECEIVED') return 'warning'
  if (status === 'RECEIVED') return 'success'
  if (status === 'CANCELLED' || status === 'REJECTED') return 'danger'
  return 'info'
}

function statusLabel(status: PurchaseRequest['status']) {
  return t(`procurement.status.${status}`, status)
}

function masterName(rows: MasterDataRecord[], id: string) {
  return rows.find((row) => row.id === id)?.name ?? id
}

async function read<T>(rows: Ref<T[]>, enabled: boolean, fetch: () => Promise<{ content: T[] }>, failed?: Ref<boolean>) {
  if (failed) failed.value = false
  if (!enabled) { rows.value = []; return true }
  try { rows.value = (await fetch()).content; return true }
  catch { rows.value = []; if (failed) failed.value = true; return false }
}

async function load() {
  loading.value = true
  if (!canReadShared.value) activeTab.value = 'orders'
  try {
    const results = await Promise.all([
      read(requests, canReadShared.value, listPurchaseRequests, sharedFailed),
      read(orders, auth.hasPermission('procurement:view'), listPurchaseOrders, ordersFailed),

      read(suppliers, canReadMaster.value, () => listMasterData('suppliers', '', 0, 100)),
      read(warehouses, canReadMaster.value, () => listMasterData('warehouses', '', 0, 100)),
      read(items, canReadMaster.value, () => listMasterData('items', '', 0, 100)),
    ])
    if (results.includes(false)) ElMessage.error(t('procurement.loadFailed'))
  } finally { loading.value = false }
}

function openCreate(type: 'request' | 'order') {
  if (!(type === 'request' ? canCreateShared.value : canCreateOrder.value)) return
  dialogType.value = type
  dialogVisible.value = true
  if (type === 'order') {
    orderForm.purchaseRequestId = ''
    orderForm.lines.splice(0, orderForm.lines.length, { itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 })
    const request = canReadShared.value ? requests.value.find((item) => item.status === 'APPROVED' && item.lineId) : undefined
    if (request) { orderForm.purchaseRequestId = request.id; applyRequestToOrder(request.id) }
  }
}

function applyRequestToOrder(id: string) {
  const request = requests.value.find((item) => item.id === id)
  if (!id) {
    for (const line of orderForm.lines) { delete line.sourceDocumentType; delete line.sourceDocumentId; delete line.sourceLineId }
    return
  }
  if (!canReadShared.value || !request?.lineId || request.status !== 'APPROVED') {
    orderForm.purchaseRequestId = ''
    for (const line of orderForm.lines) { delete line.sourceDocumentType; delete line.sourceDocumentId; delete line.sourceLineId }
    ElMessage.error(t('procurement.saveFailed'))
    return
  }
  orderForm.supplierId = request.supplierId
  orderForm.warehouseId = request.warehouseId
  orderForm.lines.splice(0, orderForm.lines.length, { itemId: request.itemId, quantity: request.quantity, unitPrice: request.estimatedUnitCost, discountRate: 0, taxRate: 0, sourceDocumentType: 'PURCHASE_REQUEST', sourceDocumentId: request.id, sourceLineId: request.lineId })
}


function addOrderLine() {
  orderForm.lines.push({ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 })
}

function removeOrderLine(index: number) {
  if (orderForm.lines.length > 1) {
    orderForm.lines.splice(index, 1)
    if (!orderForm.lines.some(line => line.sourceDocumentId === orderForm.purchaseRequestId)) orderForm.purchaseRequestId = ''
  }
}
async function submit() {
  if (!(dialogType.value === 'request' ? canCreateShared.value : canCreateOrder.value)) return
  try {
    if (dialogType.value === 'request') {
      await createPurchaseRequest({ ...requestForm })
    } else {
      await createPurchaseOrderV2({ supplierId: orderForm.supplierId, warehouseId: orderForm.warehouseId, currencyCode: orderForm.currencyCode, expectedDate: orderForm.expectedDate || undefined, note: orderForm.note || undefined, lines: orderForm.lines.map(line => ({ ...line })) })
    }
    dialogVisible.value = false
    ElMessage.success(t('procurement.created'))
    await load()
  } catch {
    ElMessage.error(t('procurement.saveFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="operations-page">
    <div class="operations-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('procurement.eyebrow') }}</span>
        <h1>{{ t('procurement.title') }}</h1>
        <p>{{ t('procurement.subtitle') }}</p>
      </div>
      <div class="operations-actions">
        <el-button round plain :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('procurement.refresh') }}</el-button>
        <el-button v-if="activeTab === 'requests' ? canCreateShared : canCreateOrder" type="primary" round @click="openCreate(activeTab === 'requests' ? 'request' : 'order')"><el-icon><Plus /></el-icon>{{ t('procurement.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="canReadShared" :label="t('procurement.requests')" name="requests">
          <el-alert v-if="sharedFailed && !loading" :title="t('procurement.loadFailed')" type="error" :closable="false"><el-button @click="load">{{ t('procurement.refresh') }}</el-button></el-alert>
          <el-table v-loading="loading" :data="requests" empty-text="">
            <el-table-column prop="number" :label="t('procurement.number')" width="150" />
            <el-table-column :label="t('procurement.supplier')" min-width="170"><template #default="{ row }">{{ masterName(suppliers, row.supplierId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantity" :label="t('procurement.quantity')" width="110" />
            <el-table-column :label="t('procurement.statusLabel')" width="140"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('procurement.warehouse')" min-width="150"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!requests.length && !loading && !sharedFailed" :description="t('procurement.emptyRequests')" />
        </el-tab-pane>
        <el-tab-pane :label="t('procurement.orders')" name="orders">
          <el-alert v-if="ordersFailed && !loading" :title="t('procurement.loadFailed')" type="error" :closable="false"><el-button @click="load">{{ t('procurement.refresh') }}</el-button></el-alert>
          <el-table v-loading="loading" :data="orders" empty-text="">
            <el-table-column prop="number" :label="t('procurement.number')" width="150" />
            <el-table-column :label="t('procurement.supplier')" min-width="170"><template #default="{ row }">{{ masterName(suppliers, row.supplierId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.progress')" width="150"><template #default="{ row }">{{ row.receivedQuantity }} / {{ row.orderedQuantity }}</template></el-table-column>
            <el-table-column :label="t('procurement.statusLabel')" width="160"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('procurement.warehouse')" min-width="150"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!orders.length && !loading && !ordersFailed" :description="t('procurement.emptyOrders')" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogType === 'request' ? t('procurement.createRequest') : t('procurement.createOrder')" width="560px">
      <el-form v-if="dialogType === 'request'" label-position="top" @submit.prevent="submit">
        <div class="operations-form-grid">
          <el-form-item :label="t('procurement.supplier')"><el-select v-model="requestForm.supplierId" class="full-width"><el-option v-for="row in suppliers" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.warehouse')"><el-select v-model="requestForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.item')"><el-select v-model="requestForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.quantity')"><el-input-number v-model="requestForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('procurement.unitCost')"><el-input-number v-model="requestForm.estimatedUnitCost" :min="0" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('procurement.note')"><el-input v-model="requestForm.note" /></el-form-item>
        </div>
      </el-form>
      <el-form v-else label-position="top" @submit.prevent="submit">
        <div class="operations-form-grid">
          <el-form-item v-if="canReadShared" :label="t('procurement.sourceRequest')"><el-select v-model="orderForm.purchaseRequestId" clearable class="full-width" @change="applyRequestToOrder"><el-option v-for="row in requests.filter((item) => item.status === 'APPROVED' && item.lineId)" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.supplier')"><el-select v-model="orderForm.supplierId" class="full-width"><el-option v-for="row in suppliers" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.warehouse')"><el-select v-model="orderForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.expectedDate')"><el-date-picker v-model="orderForm.expectedDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        </div>
        <div v-for="(line, index) in orderForm.lines" :key="index" class="operations-form-grid trade-line-editor">
          <el-form-item :label="`${t('procurement.item')} #${index + 1}`"><el-select v-model="line.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('procurement.quantity')"><el-input-number v-model="line.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('procurement.unitPrice')"><el-input-number v-model="line.unitPrice" :min="0" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.discountRate')"><el-input-number v-model="line.discountRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('procurement.taxRate')"><el-input-number v-model="line.taxRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item><el-button :disabled="orderForm.lines.length === 1" @click="removeOrderLine(index)">−</el-button></el-form-item>
        </div>
        <el-button plain @click="addOrderLine"><el-icon><Plus /></el-icon>{{ t('procurement.addLine', 'Add line') }}</el-button>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button v-if="dialogType === 'request' ? canCreateShared : canCreateOrder" type="primary" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>
