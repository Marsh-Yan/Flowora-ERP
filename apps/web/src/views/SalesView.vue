<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, CreditCard, Plus, Refresh, Van } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  approveSalesQuote,
  createDelivery,
  createPayment,
  createSalesOrder,
  createSalesQuote,
  listReceivables,
  listSalesOrders,
  listSalesQuotes,
  type Receivable,
  type SalesOrder,
  type SalesQuote,
} from '@/api/sales'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'

const { t } = useI18n()
const activeTab = ref<'quotes' | 'orders' | 'receivables'>('quotes')
const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref<'quote' | 'order'>('quote')
const deliveryVisible = ref(false)
const paymentVisible = ref(false)
const selectedOrder = ref<SalesOrder | null>(null)
const selectedReceivable = ref<Receivable | null>(null)
const quotes = ref<SalesQuote[]>([])
const orders = ref<SalesOrder[]>([])
const receivables = ref<Receivable[]>([])
const customers = ref<MasterDataRecord[]>([])
const warehouses = ref<MasterDataRecord[]>([])
const items = ref<MasterDataRecord[]>([])
const quoteForm = reactive({ customerId: '', itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0, currencyCode: 'USD', validUntil: '', note: '' })
const orderForm = reactive({ quoteId: '', customerId: '', warehouseId: '', itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0, currencyCode: 'USD', dueDate: '', note: '' })
const deliveryForm = reactive({ quantity: 1 })
const paymentForm = reactive({ amount: 0, method: 'BANK' as 'BANK' | 'CASH' | 'OTHER', paymentDate: '', reference: '' })

const outstandingTotal = computed(() => receivables.value.reduce((sum, item) => sum + item.outstandingAmount, 0))
const openOrderCount = computed(() => orders.value.filter((item) => item.remainingQuantity > 0).length)

function today() {
  return new Date().toISOString().slice(0, 10)
}

function statusType(status: string) {
  if (['APPROVED', 'CONFIRMED', 'FULFILLED', 'SETTLED', 'CONVERTED'].includes(status)) return 'success'
  if (['SUBMITTED', 'PARTIALLY_FULFILLED', 'PARTIALLY_SETTLED', 'OPEN'].includes(status)) return 'warning'
  if (['REJECTED', 'CANCELLED'].includes(status)) return 'danger'
  return 'info'
}

function statusLabel(status: string) {
  return t(`sales.status.${status}`, status)
}

function masterName(rows: MasterDataRecord[], id: string) {
  return rows.find((row) => row.id === id)?.name ?? id
}

async function load() {
  loading.value = true
  try {
    const [quotePage, orderPage, receivablePage, customerPage, warehousePage, itemPage] = await Promise.all([
      listSalesQuotes(),
      listSalesOrders(),
      listReceivables(),
      listMasterData('customers', '', 0, 100),
      listMasterData('warehouses', '', 0, 100),
      listMasterData('items', '', 0, 100),
    ])
    quotes.value = quotePage.content
    orders.value = orderPage.content
    receivables.value = receivablePage.content
    customers.value = customerPage.content
    warehouses.value = warehousePage.content
    items.value = itemPage.content
  } catch {
    ElMessage.error(t('sales.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(type: 'quote' | 'order') {
  dialogType.value = type
  if (type === 'quote') quoteForm.validUntil = today()
  dialogVisible.value = true
}

function applyQuoteToOrder(id: string) {
  const quote = quotes.value.find((item) => item.id === id)
  if (!quote) return
  orderForm.customerId = quote.customerId
  orderForm.itemId = quote.itemId
  orderForm.quantity = quote.quantity
  orderForm.unitPrice = quote.unitPrice
  orderForm.discountRate = quote.discountRate
  orderForm.taxRate = quote.taxRate
  orderForm.currencyCode = quote.currencyCode
}

async function submit() {
  try {
    if (dialogType.value === 'quote') {
      await createSalesQuote({ ...quoteForm })
    } else {
      await createSalesOrder({ ...orderForm, quoteId: orderForm.quoteId || undefined, dueDate: orderForm.dueDate || undefined })
    }
    dialogVisible.value = false
    ElMessage.success(t('sales.created'))
    await load()
  } catch {
    ElMessage.error(t('sales.saveFailed'))
  }
}

async function approve(quote: SalesQuote) {
  try {
    await approveSalesQuote(quote.id)
    ElMessage.success(t('sales.approved'))
    await load()
  } catch {
    ElMessage.error(t('sales.actionFailed'))
  }
}

function openDelivery(order: SalesOrder) {
  selectedOrder.value = order
  deliveryForm.quantity = Math.min(1, order.remainingQuantity)
  deliveryVisible.value = true
}

async function submitDelivery() {
  if (!selectedOrder.value) return
  try {
    await createDelivery({ salesOrderId: selectedOrder.value.id, salesOrderLineId: selectedOrder.value.lineId, warehouseId: selectedOrder.value.warehouseId, quantity: deliveryForm.quantity })
    deliveryVisible.value = false
    ElMessage.success(t('sales.deliveryPosted'))
    await load()
  } catch {
    ElMessage.error(t('sales.actionFailed'))
  }
}

function openPayment(receivable: Receivable) {
  selectedReceivable.value = receivable
  paymentForm.amount = receivable.outstandingAmount
  paymentForm.paymentDate = today()
  paymentVisible.value = true
}

async function submitPayment() {
  if (!selectedReceivable.value) return
  try {
    await createPayment({ receivableId: selectedReceivable.value.id, ...paymentForm })
    paymentVisible.value = false
    ElMessage.success(t('sales.paymentPosted'))
    await load()
  } catch {
    ElMessage.error(t('sales.actionFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="operations-page">
    <div class="operations-heading section-heading">
      <div>
        <span class="eyebrow">{{ t('sales.eyebrow') }}</span>
        <h1>{{ t('sales.title') }}</h1>
        <p>{{ t('sales.subtitle') }}</p>
      </div>
      <div class="operations-actions">
        <el-button round plain :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('sales.refresh') }}</el-button>
        <el-button type="primary" round @click="openCreate(activeTab === 'quotes' ? 'quote' : 'order')"><el-icon><Plus /></el-icon>{{ t('sales.create') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never"><span class="eyebrow">{{ t('sales.openOrders') }}</span><strong>{{ openOrderCount }}</strong><small>{{ t('sales.openOrdersHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('sales.receivableOutstanding') }}</span><strong>{{ outstandingTotal.toFixed(2) }}</strong><small>{{ t('sales.receivableHint') }}</small></el-card>
      <el-card shadow="never"><span class="eyebrow">{{ t('sales.quoteCount') }}</span><strong>{{ quotes.length }}</strong><small>{{ t('sales.quoteHint') }}</small></el-card>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="t('sales.quotes')" name="quotes">
          <el-table v-loading="loading" :data="quotes" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column :label="t('sales.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column :label="t('sales.statusLabel')" width="150"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="130"><template #default="{ row }"><el-button v-if="row.status === 'SUBMITTED'" link type="primary" @click="approve(row)"><el-icon><Check /></el-icon>{{ t('sales.approve') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!quotes.length && !loading" :description="t('sales.emptyQuotes')" />
        </el-tab-pane>
        <el-tab-pane :label="t('sales.orders')" name="orders">
          <el-table v-loading="loading" :data="orders" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column :label="t('sales.item')" min-width="160"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column :label="t('sales.progress')" width="150"><template #default="{ row }">{{ row.fulfilledQuantity }} / {{ row.orderedQuantity }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column :label="t('sales.statusLabel')" width="170"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="130"><template #default="{ row }"><el-button v-if="row.remainingQuantity > 0" link type="primary" @click="openDelivery(row)"><el-icon><Van /></el-icon>{{ t('sales.deliver') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!orders.length && !loading" :description="t('sales.emptyOrders')" />
        </el-tab-pane>
        <el-tab-pane :label="t('sales.receivables')" name="receivables">
          <el-table v-loading="loading" :data="receivables" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column prop="outstandingAmount" :label="t('sales.outstanding')" width="130" />
            <el-table-column :label="t('sales.statusLabel')" width="160"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="150"><template #default="{ row }"><el-button v-if="row.outstandingAmount > 0" link type="primary" @click="openPayment(row)"><el-icon><CreditCard /></el-icon>{{ t('sales.receivePayment') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!receivables.length && !loading" :description="t('sales.emptyReceivables')" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogType === 'quote' ? t('sales.createQuote') : t('sales.createOrder')" width="580px">
      <el-form v-if="dialogType === 'quote'" label-position="top" @submit.prevent="submit">
        <div class="operations-form-grid">
          <el-form-item :label="t('sales.customer')"><el-select v-model="quoteForm.customerId" class="full-width"><el-option v-for="row in customers" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.item')"><el-select v-model="quoteForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.quantity')"><el-input-number v-model="quoteForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.unitPrice')"><el-input-number v-model="quoteForm.unitPrice" :min="0" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.discountRate')"><el-input-number v-model="quoteForm.discountRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.taxRate')"><el-input-number v-model="quoteForm.taxRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.validUntil')"><el-date-picker v-model="quoteForm.validUntil" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        </div>
      </el-form>
      <el-form v-else label-position="top" @submit.prevent="submit">
        <div class="operations-form-grid">
          <el-form-item :label="t('sales.sourceQuote')"><el-select v-model="orderForm.quoteId" clearable class="full-width" @change="applyQuoteToOrder"><el-option v-for="row in quotes.filter((item) => item.status === 'APPROVED')" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.customer')"><el-select v-model="orderForm.customerId" class="full-width"><el-option v-for="row in customers" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.warehouse')"><el-select v-model="orderForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.item')"><el-select v-model="orderForm.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.quantity')"><el-input-number v-model="orderForm.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.unitPrice')"><el-input-number v-model="orderForm.unitPrice" :min="0" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.discountRate')"><el-input-number v-model="orderForm.discountRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.taxRate')"><el-input-number v-model="orderForm.taxRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.dueDate')"><el-date-picker v-model="orderForm.dueDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="deliveryVisible" :title="t('sales.deliveryDialog')" width="420px">
      <el-form label-position="top"><el-form-item :label="t('sales.quantity')"><el-input-number v-model="deliveryForm.quantity" :min="0.0001" :max="selectedOrder?.remainingQuantity" :precision="4" class="full-width" /></el-form-item></el-form>
      <template #footer><el-button @click="deliveryVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" @click="submitDelivery">{{ t('sales.deliver') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="paymentVisible" :title="t('sales.paymentDialog')" width="420px">
      <el-form label-position="top">
        <el-form-item :label="t('sales.amount')"><el-input-number v-model="paymentForm.amount" :min="0.0001" :max="selectedReceivable?.outstandingAmount" :precision="4" class="full-width" /></el-form-item>
        <el-form-item :label="t('sales.paymentMethod')"><el-select v-model="paymentForm.method" class="full-width"><el-option :label="t('sales.bank')" value="BANK" /><el-option :label="t('sales.cash')" value="CASH" /><el-option :label="t('sales.other')" value="OTHER" /></el-select></el-form-item>
        <el-form-item :label="t('sales.paymentDate')"><el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        <el-form-item :label="t('sales.reference')"><el-input v-model="paymentForm.reference" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="paymentVisible = false">{{ t('masterData.cancel') }}</el-button><el-button type="primary" @click="submitPayment">{{ t('sales.receivePayment') }}</el-button></template>
    </el-dialog>
  </div>
</template>
