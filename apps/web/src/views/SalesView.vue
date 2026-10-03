<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { Check, CreditCard, Plus, Refresh, Van } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  approveSalesQuote,
  createDelivery,
  createPayment,
  createSalesQuote,
  listReceivables,
  listSalesOrders,
  listSalesQuotes,
  type Receivable,
  type SalesOrder,
  type SalesQuote,
} from '@/api/sales'
import { listMasterData, type MasterDataRecord } from '@/api/master-data'

import { useAuthStore } from '@/stores/auth'
import { useTradeOrderActions } from '@/composables/useTradeOrderActions'

const { t } = useI18n()
const auth = useAuthStore()
const { busyOrderId, canManageOrders, changeOrderState } = useTradeOrderActions('sales', load)
const canReadShared = computed(() => auth.user?.dataScope === 'ALL' && auth.hasPermission('sales:view'))
const canReadMaster = computed(() => auth.hasPermission('master:view'))
const canCreateOrder = computed(() => auth.hasPermission('sales:create') && canReadMaster.value)
const canCreateShared = computed(() => canReadShared.value && canReadMaster.value &&
  ['sales:create', 'sales:submit', 'workflow:view', 'workflow:submit'].every(auth.hasPermission))
const canApprove = computed(() => canReadShared.value && auth.hasPermission('workflow:approve'))
const canDeliver = computed(() => auth.hasPermission('inventory:post'))
const canPay = computed(() => auth.hasPermission('finance:post'))
const receivablesFailed = ref(false)
const sharedFailed = ref(false)
const ordersFailed = ref(false)
const activeTab = ref<'quotes' | 'orders' | 'receivables'>(canReadShared.value ? 'quotes' : 'orders')
import { createSalesOrderV2, type TradeLineInput } from '@/api/trade'
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
const orderForm = reactive({ quoteId: '', customerId: '', warehouseId: '', currencyCode: 'USD', dueDate: '', note: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 } as TradeLineInput] })
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
      read(quotes, canReadShared.value, listSalesQuotes, sharedFailed),
      read(orders, auth.hasPermission('sales:view'), listSalesOrders, ordersFailed),
      read(receivables, canReadShared.value, listReceivables, receivablesFailed),
      read(customers, canReadMaster.value, () => listMasterData('customers', '', 0, 100)),
      read(warehouses, canReadMaster.value, () => listMasterData('warehouses', '', 0, 100)),
      read(items, canReadMaster.value, () => listMasterData('items', '', 0, 100)),
    ])
    if (results.includes(false)) ElMessage.error(t('sales.loadFailed'))
  } finally { loading.value = false }
}

function openCreate(type: 'quote' | 'order') {
  if (!(type === 'quote' ? canCreateShared.value : canCreateOrder.value)) return
  dialogType.value = type
  if (type === 'quote') quoteForm.validUntil = today()
  else {
    orderForm.quoteId = ''
    orderForm.lines.splice(0, orderForm.lines.length, { itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 })
  }
  dialogVisible.value = true
}

function applyQuoteToOrder(id: string) {
  const quote = quotes.value.find((item) => item.id === id)
  if (!id) {
    for (const line of orderForm.lines) { delete line.sourceDocumentType; delete line.sourceDocumentId; delete line.sourceLineId }
    return
  }
  if (!canReadShared.value || !quote?.lineId || quote.status !== 'APPROVED' || quote.sourceEligible !== true) {
    orderForm.quoteId = ''
    for (const line of orderForm.lines) { delete line.sourceDocumentType; delete line.sourceDocumentId; delete line.sourceLineId }
    ElMessage.error(t('sales.saveFailed'))
    return
  }
  orderForm.customerId = quote.customerId
  orderForm.lines.splice(0, orderForm.lines.length, { itemId: quote.itemId, quantity: quote.quantity, unitPrice: quote.unitPrice, discountRate: quote.discountRate, taxRate: quote.taxRate, sourceDocumentType: 'SALES_QUOTE', sourceDocumentId: quote.id, sourceLineId: quote.lineId })
  orderForm.currencyCode = quote.currencyCode
}

function addOrderLine() {
  orderForm.lines.push({ itemId: '', quantity: 1, unitPrice: 0, discountRate: 0, taxRate: 0 })
}

function removeOrderLine(index: number) {
  if (orderForm.lines.length > 1) {
    orderForm.lines.splice(index, 1)
    if (!orderForm.lines.some(line => line.sourceDocumentId === orderForm.quoteId)) orderForm.quoteId = ''
  }
}

async function submit() {
  if (!(dialogType.value === 'quote' ? canCreateShared.value : canCreateOrder.value)) return
  try {
    if (dialogType.value === 'quote') {
      await createSalesQuote({ ...quoteForm })
    } else {
      await createSalesOrderV2({ customerId: orderForm.customerId, warehouseId: orderForm.warehouseId, currencyCode: orderForm.currencyCode, dueDate: orderForm.dueDate || undefined, note: orderForm.note || undefined, lines: orderForm.lines.map(line => ({ ...line })) })
    }
    dialogVisible.value = false
    ElMessage.success(t('sales.created'))
    await load()
  } catch (error) {
    const code = axios.isAxiosError(error) ? error.response?.data?.code : undefined
    ElMessage.error(t(code === 'SOURCE_QUANTITY_EXCEEDED' ? 'errors.sourceQuantityExceeded' : code === 'SOURCE_QUOTE_EXPIRED' ? 'errors.sourceQuoteExpired' : code === 'IDEMPOTENCY_REQUEST_MISMATCH' ? 'errors.idempotencyRequestMismatch' : 'sales.saveFailed'))
  }
}

async function approve(quote: SalesQuote) {
  if (!canApprove.value) return
  try {
    await approveSalesQuote(quote.id)
    ElMessage.success(t('sales.approved'))
    await load()
  } catch {
    ElMessage.error(t('sales.actionFailed'))
  }
}

function openDelivery(order: SalesOrder) {
  if (!canDeliver.value) return
  selectedOrder.value = order
  deliveryForm.quantity = Math.min(1, order.remainingQuantity)
  deliveryVisible.value = true
}

async function submitDelivery() {
  if (!canDeliver.value || !selectedOrder.value) return
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
  if (!canPay.value) return
  selectedReceivable.value = receivable
  paymentForm.amount = receivable.outstandingAmount
  paymentForm.paymentDate = today()
  paymentVisible.value = true
}

async function submitPayment() {
  if (!canPay.value || !selectedReceivable.value) return
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
        <el-button v-if="activeTab === 'quotes' ? canCreateShared : activeTab === 'orders' && canCreateOrder" type="primary" round @click="openCreate(activeTab === 'quotes' ? 'quote' : 'order')"><el-icon><Plus /></el-icon>{{ t('sales.create') }}</el-button>
      </div>
    </div>

    <div class="inventory-summary-grid">
      <el-card shadow="never"><span class="eyebrow">{{ t('sales.openOrders') }}</span><strong>{{ ordersFailed ? '—' : openOrderCount }}</strong><small>{{ t('sales.openOrdersHint') }}</small></el-card>
      <el-card v-if="canReadShared" shadow="never"><span class="eyebrow">{{ t('sales.receivableOutstanding') }}</span><strong>{{ receivablesFailed ? '—' : outstandingTotal.toFixed(2) }}</strong><small>{{ t('sales.receivableHint') }}</small></el-card>
      <el-card v-if="canReadShared" shadow="never"><span class="eyebrow">{{ t('sales.quoteCount') }}</span><strong>{{ sharedFailed ? '—' : quotes.length }}</strong><small>{{ t('sales.quoteHint') }}</small></el-card>
    </div>

    <el-card shadow="never" class="operations-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="canReadShared" :label="t('sales.quotes')" name="quotes">
          <el-alert v-if="sharedFailed && !loading" :title="t('sales.loadFailed')" type="error" :closable="false"><el-button @click="load">{{ t('sales.refresh') }}</el-button></el-alert>
          <el-table v-loading="loading" :data="quotes" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column :label="t('sales.item')" min-width="170"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column :label="t('sales.statusLabel')" width="150"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="130"><template #default="{ row }"><el-button v-if="canApprove && row.status === 'SUBMITTED'" link type="primary" @click="approve(row)"><el-icon><Check /></el-icon>{{ t('sales.approve') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!quotes.length && !loading && !sharedFailed" :description="t('sales.emptyQuotes')" />
        </el-tab-pane>
        <el-tab-pane :label="t('sales.orders')" name="orders">
          <el-alert v-if="ordersFailed && !loading" :title="t('sales.loadFailed')" type="error" :closable="false"><el-button @click="load">{{ t('sales.refresh') }}</el-button></el-alert>
          <el-table v-loading="loading" :data="orders" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column :label="t('sales.item')" min-width="160"><template #default="{ row }">{{ masterName(items, row.itemId) }}</template></el-table-column>
            <el-table-column :label="t('sales.progress')" width="150"><template #default="{ row }">{{ row.fulfilledQuantity }} / {{ row.orderedQuantity }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column :label="t('sales.statusLabel')" width="170"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="210" fixed="right"><template #default="{ row }"><el-button v-if="canManageOrders && row.status === 'DRAFT'" link type="primary" :disabled="!!busyOrderId" @click="changeOrderState('confirm', row.id, row.number)">{{ t('tradeActions.confirmOrder') }}</el-button><el-button v-if="canManageOrders && ['DRAFT', 'CONFIRMED', 'RESERVED'].includes(row.status) && (row.fulfilledQuantity ?? 0) === 0" link type="danger" :disabled="!!busyOrderId" @click="changeOrderState('cancel', row.id, row.number)">{{ t('tradeActions.cancelOrder') }}</el-button><el-button v-if="canDeliver && ['CONFIRMED', 'PARTIALLY_FULFILLED'].includes(row.status) && row.remainingQuantity > 0" link type="primary" @click="openDelivery(row)"><el-icon><Van /></el-icon>{{ t('sales.deliver') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!orders.length && !loading && !ordersFailed" :description="t('sales.emptyOrders')" />
        </el-tab-pane>
        <el-tab-pane v-if="canReadShared" :label="t('sales.receivables')" name="receivables">
          <el-alert v-if="receivablesFailed && !loading" :title="t('sales.loadFailed')" type="error" :closable="false"><el-button @click="load">{{ t('sales.refresh') }}</el-button></el-alert>
          <el-table v-loading="loading" :data="receivables" empty-text="">
            <el-table-column prop="number" :label="t('sales.number')" width="150" />
            <el-table-column :label="t('sales.customer')" min-width="170"><template #default="{ row }">{{ masterName(customers, row.customerId) }}</template></el-table-column>
            <el-table-column prop="totalAmount" :label="t('sales.amount')" width="120" />
            <el-table-column prop="outstandingAmount" :label="t('sales.outstanding')" width="130" />
            <el-table-column :label="t('sales.statusLabel')" width="160"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column :label="t('sales.actions')" width="150"><template #default="{ row }"><el-button v-if="canPay && row.outstandingAmount > 0" link type="primary" @click="openPayment(row)"><el-icon><CreditCard /></el-icon>{{ t('sales.receivePayment') }}</el-button></template></el-table-column>
          </el-table>
          <el-empty v-if="!receivables.length && !loading && !receivablesFailed" :description="t('sales.emptyReceivables')" />
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
          <el-form-item v-if="canReadShared" :label="t('sales.sourceQuote')"><el-select v-model="orderForm.quoteId" clearable class="full-width" @change="applyQuoteToOrder"><el-option v-for="row in quotes.filter((item) => item.status === 'APPROVED' && item.lineId && item.sourceEligible === true)" :key="row.id" :label="row.number" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.customer')"><el-select v-model="orderForm.customerId" class="full-width"><el-option v-for="row in customers" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.warehouse')"><el-select v-model="orderForm.warehouseId" class="full-width"><el-option v-for="row in warehouses" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.dueDate')"><el-date-picker v-model="orderForm.dueDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        </div>
        <div v-for="(line, index) in orderForm.lines" :key="index" class="operations-form-grid trade-line-editor">
          <el-form-item :label="`${t('sales.item')} #${index + 1}`"><el-select v-model="line.itemId" class="full-width"><el-option v-for="row in items" :key="row.id" :label="row.name" :value="row.id" /></el-select></el-form-item>
          <el-form-item :label="t('sales.quantity')"><el-input-number v-model="line.quantity" :min="0.0001" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.unitPrice')"><el-input-number v-model="line.unitPrice" :min="0" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.discountRate')"><el-input-number v-model="line.discountRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item :label="t('sales.taxRate')"><el-input-number v-model="line.taxRate" :min="0" :max="100" :precision="4" class="full-width" /></el-form-item>
          <el-form-item><el-button :disabled="orderForm.lines.length === 1" @click="removeOrderLine(index)">−</el-button></el-form-item>
        </div>
        <el-button plain @click="addOrderLine"><el-icon><Plus /></el-icon>{{ t('sales.addLine', 'Add line') }}</el-button>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">{{ t('masterData.cancel') }}</el-button><el-button v-if="dialogType === 'quote' ? canCreateShared : canCreateOrder" type="primary" @click="submit">{{ t('masterData.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="deliveryVisible" :title="t('sales.deliveryDialog')" width="420px">
      <el-form label-position="top"><el-form-item :label="t('sales.quantity')"><el-input-number v-model="deliveryForm.quantity" :min="0.0001" :max="selectedOrder?.remainingQuantity" :precision="4" class="full-width" /></el-form-item></el-form>
      <template #footer><el-button @click="deliveryVisible = false">{{ t('masterData.cancel') }}</el-button><el-button v-if="canDeliver" type="primary" @click="submitDelivery">{{ t('sales.deliver') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="paymentVisible" :title="t('sales.paymentDialog')" width="420px">
      <el-form label-position="top">
        <el-form-item :label="t('sales.amount')"><el-input-number v-model="paymentForm.amount" :min="0.0001" :max="selectedReceivable?.outstandingAmount" :precision="4" class="full-width" /></el-form-item>
        <el-form-item :label="t('sales.paymentMethod')"><el-select v-model="paymentForm.method" class="full-width"><el-option :label="t('sales.bank')" value="BANK" /><el-option :label="t('sales.cash')" value="CASH" /><el-option :label="t('sales.other')" value="OTHER" /></el-select></el-form-item>
        <el-form-item :label="t('sales.paymentDate')"><el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
        <el-form-item :label="t('sales.reference')"><el-input v-model="paymentForm.reference" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="paymentVisible = false">{{ t('masterData.cancel') }}</el-button><el-button v-if="canPay" type="primary" @click="submitPayment">{{ t('sales.receivePayment') }}</el-button></template>
    </el-dialog>
  </div>
</template>
