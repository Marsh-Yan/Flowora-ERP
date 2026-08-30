<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { createPurchaseRequest, listPurchaseOrders, listPurchaseRequests, type PurchaseOrder, type PurchaseRequest } from '@/api/procurement'
import { createPurchaseOrderV2 } from '@/api/trade'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'

const { t } = useI18n()
const activeTab = ref<'requests' | 'orders'>('requests')
const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref<'request' | 'order'>('request')
const requests = ref<PurchaseRequest[]>([])
const orders = ref<PurchaseOrder[]>([])
const suppliers = ref<MasterDataRecord[]>([])
const warehouses = ref<MasterDataRecord[]>([])
const items = ref<MasterDataRecord[]>([])
const requestForm = reactive({ supplierId: '', warehouseId: '', itemId: '', quantity: 1, estimatedUnitCost: 0, note: '' })
const orderForm = reactive({ purchaseRequestId: '', supplierId: '', warehouseId: '', currencyCode: 'CNY', expectedDate: '', note: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 }] })

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

async function load() {
  loading.value = true
  try {
    const [requestPage, orderPage, supplierPage, warehousePage, itemPage] = await Promise.all([
      listPurchaseRequests(),
      listPurchaseOrders(),
      listMasterData('suppliers', '', 0, 100),
      listMasterData('warehouses', '', 0, 100),
      listMasterData('items', '', 0, 100),
    ])
    requests.value = requestPage.content
    orders.value = orderPage.content
    suppliers.value = supplierPage.content
    warehouses.value = warehousePage.content
    items.value = itemPage.content
  } catch {
    ElMessage.error(t('procurement.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(type: 'request' | 'order') {
  dialogType.value = type
  dialogVisible.value = true
  if (type === 'order' && requests.value.length) {
    const request = requests.value.find((item) => item.status === 'APPROVED')
    if (request) {
      orderForm.purchaseRequestId = request.id
      orderForm.supplierId = request.supplierId
      orderForm.warehouseId = request.warehouseId
      orderForm.lines.splice(0, orderForm.lines.length, { itemId: request.itemId, quantity: request.quantity, unitPrice: request.estimatedUnitCost, discountRate: 0, taxRate: 0 })
    }
  }
}

function applyRequestToOrder(id: string) {
  const request = requests.value.find((item) => item.id === id)
  if (!request) return
  orderForm.supplierId = request.supplierId
  orderForm.warehouseId = request.warehouseId
  orderForm.lines.splice(0, orderForm.lines.length, { itemId: request.itemId, quantity: request.quantity, unitPrice: request.estimatedUnitCost, discountRate: 0, taxRate: 0 })
}


function addOrderLine() {
  orderForm.lines.push({ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 })
}

function removeOrderLine(index: number) {
  if (orderForm.lines.length > 1) orderForm.lines.splice(index, 1)
}
async function submit() {
  try {
    if (dialogType.value === 'request') {
      await createPurchaseRequest({ ...requestForm })
    } else {
      await createPurchaseOrderV2({ supplierId: orderForm.supplierId, warehouseId: orderForm.warehouseId, currencyCode: orderForm.currencyCode, expectedDate: orderForm.expectedDate || undefined, note: orderForm.note || undefined, lines: orderForm.lines.map((line, index) => ({ ...line, sourceDocumentType: index === 0 && orderForm.purchaseRequestId ? 'PURCHASE_REQUEST' : undefined, sourceDocumentId: index === 0 ? orderForm.purchaseRequestId || undefined : undefined, sourceLineId: index === 0 ? orderForm.purchaseRequestId || undefined : undefined })) })
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
        <el-button type="primary" round @click="openCreate(activeTab === 'requests' ? 'request' : 'order')"><el-icon><Plus /></el-icon>{{ t('procurement.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('procurement.requests')" name="requests">
          <el-table v-loading="loading" :data="requests" empty-text="">
            <el-table-column prop="number" :label="t('procurement.number')" width="150" />
            <el-table-column :label="t('procurement.supplier')" min-width="170"><template #default="{ row }">{{ masterName(suppliers, row.supplierId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantity" :label="t('procurement.quantity')" width="110" />
            <el-table-column :label="t('procurement.statusLabel')" width="140"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('procurement.warehouse')" min-width="150"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!requests.length && !loading" :description="t('procurement.emptyRequests')" />
        </el-tab-pane>
        <el-tab-pane :label="t('procurement.orders')" name="orders">
          <el-table v-loading="loading" :data="orders" empty-text="">
            <el-table-column prop="number" :label="t('procurement.number')" width="150" />
            <el-table-column :label="t('procurement.supplier')" min-width="170"><template #default="{ row }">{{ masterName(suppliers, row.supplierId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column :label="t('procurement.progress')" width="150"><template #default="{ row }">{{ row.receivedQuantity }} / {{ row.orderedQuantity }}</template></el-table-column>
            <el-table-column :label="t('procurement.statusLabel')" width="160"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('procurement.warehouse')" min-width="150"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!orders.length && !loading" :description="t('procurement.emptyOrders')" />
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
          <el-form-item :label="t('procurement.sourceRequest')"><el-select v-model="orderForm.purchaseRequestId" clearable class="full-width" @change="applyRequestToOrder"><el-option v-for="row in requests.filter((item) => item.status === 'APPROVED')" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
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
      <template #footer><el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>
