<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Plus, Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'
import { createStockAdjustment, listStockBalances, listStockLedger, receivePurchaseOrder, transferStock, type StockBalance, type StockLedgerEntry } from '@/api/inventory'
import { listPurchaseOrders, type PurchaseOrder } from '@/api/procurement'

const { t, locale } = useI18n()
const activeTab = ref<'balances' | 'ledger'>('balances')
const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref<'receipt' | 'adjustment' | 'transfer'>('receipt')
const balances = ref<StockBalance[]>([])
const ledger = ref<StockLedgerEntry[]>([])
const orders = ref<PurchaseOrder[]>([])
const warehouses = ref<MasterDataRecord[]>([])
const items = ref<MasterDataRecord[]>([])
const receiptForm = reactive({ purchaseOrderId: '', purchaseOrderLineId: '', warehouseId: '', quantity: 1, unitCost: 0 })
const adjustmentForm = reactive({ warehouseId: '', itemId: '', quantityDelta: 0, unitCost: 0, reason: '' })
const transferForm = reactive({ sourceWarehouseId: '', targetWarehouseId: '', itemId: '', quantity: 1, unitCost: 0 })

function masterName(rows: MasterDataRecord[], id: string) {
  return rows.find((row) => row.id === id)?.name ?? id
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function load() {
  loading.value = true
  try {
    const [balancePage, ledgerPage, orderPage, warehousePage, itemPage] = await Promise.all([
      listStockBalances(),
      listStockLedger(),
      listPurchaseOrders(),
      listMasterData('warehouses', '', 0, 100),
      listMasterData('items', '', 0, 100),
    ])
    balances.value = balancePage.content
    ledger.value = ledgerPage.content
    orders.value = orderPage.content.filter((order) => order.remainingQuantity > 0 && order.status !== 'CANCELLED')
    warehouses.value = warehousePage.content
    items.value = itemPage.content
  } catch {
    ElMessage.error(t('inventory.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openDialog(type: 'receipt' | 'adjustment' | 'transfer') {
  dialogType.value = type
  dialogVisible.value = true
  if (type === 'receipt' && orders.value.length) selectOrder(orders.value[0].id)
}

function selectOrder(id: string) {
  const order = orders.value.find((item) => item.id === id)
  if (!order) return
  receiptForm.purchaseOrderLineId = order.lineId ?? ''
  receiptForm.warehouseId = order.warehouseId
  receiptForm.quantity = order.remainingQuantity
  receiptForm.unitCost = order.unitPrice
}

async function submit() {
  try {
    if (dialogType.value === 'receipt') await receivePurchaseOrder({ ...receiptForm })
    if (dialogType.value === 'adjustment') await createStockAdjustment({ ...adjustmentForm })
    if (dialogType.value === 'transfer') await transferStock({ ...transferForm })
    dialogVisible.value = false
    ElMessage.success(t('inventory.saved'))
    await load()
  } catch {
    ElMessage.error(t('inventory.saveFailed'))
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
        <el-button round plain @click="openDialog('transfer')"><el-icon><ArrowRight /></el-icon>{{ t('inventory.transfer') }}</el-button>
        <el-button round plain @click="openDialog('adjustment')"><el-icon><Plus /></el-icon>{{ t('inventory.adjustment') }}</el-button>
        <el-button type="primary" round @click="openDialog('receipt')"><el-icon><Plus /></el-icon>{{ t('inventory.receive') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-blue"><span>Σ</span></div><div><span>{{ t('inventory.totalValue') }}</span><strong>{{ balances.reduce((sum, row) => sum + row.inventoryValue, 0).toFixed(2) }}</strong></div></el-card>
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-mint"><span>Q</span></div><div><span>{{ t('inventory.skuCount') }}</span><strong>{{ balances.length }}</strong></div></el-card>
      <el-card shadow="never" class="workflow-summary-card"><div class="workflow-summary-icon tone-amber"><span>↗</span></div><div><span>{{ t('inventory.ledgerCount') }}</span><strong>{{ ledger.length }}</strong></div></el-card>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('inventory.balances')" name="balances">
          <el-table v-loading="loading" :data="balances" empty-text="">
            <el-table-column :label="t('inventory.warehouse')" min-width="170"><template #default="{ row }">{{ masterName(warehouses, row.warehouseId) }}</template></el-table-column>
            <el-table-column :label="t('inventory.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantity" :label="t('inventory.quantity')" width="130" />
            <el-table-column prop="averageCost" :label="t('inventory.averageCost')" width="150" />
            <el-table-column prop="inventoryValue" :label="t('inventory.inventoryValue')" width="160" />
          </el-table>
          <el-empty v-if="!balances.length && !loading" :description="t('inventory.emptyBalances')" />
        </el-tab-pane>
        <el-tab-pane :label="t('inventory.ledger')" name="ledger">
          <el-table v-loading="loading" :data="ledger" empty-text="">
            <el-table-column :label="t('inventory.movement')" width="150"><template #default="{ row }"><el-tag>{{ t(`inventory.movementTypes.${row.movementType}`, row.movementType) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('inventory.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="quantityDelta" :label="t('inventory.quantityDelta')" width="140" />
            <el-table-column prop="unitCost" :label="t('inventory.unitCost')" width="130" />
            <el-table-column prop="documentId" :label="t('inventory.document')" width="180" />
            <el-table-column :label="t('inventory.createdAt')" width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!ledger.length && !loading" :description="t('inventory.emptyLedger')" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t(`inventory.dialog.${dialogType}`)" width="560px">
      <el-form v-if="dialogType === 'receipt'" label-position="top">
        <el-form-item :label="t('inventory.purchaseOrder')"><el-select v-model="receiptForm.purchaseOrderId" class="full-width" @change="selectOrder"><el-option v-for="row in orders" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
        <div class="operations-form-grid"><el-form-item :label="t('inventory.quantity')"><el-input-number v-model="receiptForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="receiptForm.unitCost" :min="0" :precision="4" class="full-width" /></el-form-item></div>
      </el-form>
      <el-form v-else-if="dialogType === 'adjustment'" label-position="top"><div class="operations-form-grid"><el-form-item :label="t('inventory.warehouse')"><el-select v-model="adjustmentForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.item')"><el-select v-model="adjustmentForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.quantityDelta')"><el-input-number v-model="adjustmentForm.quantityDelta" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="adjustmentForm.unitCost" :min="0" :precision="4" class="full-width" /></el-form-item></div><el-form-item :label="t('inventory.reason')"><el-input v-model="adjustmentForm.reason" type="textarea" :rows="3" /></el-form-item></el-form>
      <el-form v-else label-position="top"><div class="operations-form-grid"><el-form-item :label="t('inventory.sourceWarehouse')"><el-select v-model="transferForm.sourceWarehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.targetWarehouse')"><el-select v-model="transferForm.targetWarehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.item')"><el-select v-model="transferForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item><el-form-item :label="t('inventory.quantity')"><el-input-number v-model="transferForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item><el-form-item :label="t('inventory.unitCost')"><el-input-number v-model="transferForm.unitCost" :min="0" :precision="4" class="full-width" /></el-form-item></div></el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>
