<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { OperationalCurrencyAmount } from '@/api/projects'
import { operationalMoney } from '@/utils/project-operational-currencies'
defineProps<{amounts:OperationalCurrencyAmount[] | null;metric:'actualCost'|'billableAmount';emptyCurrency?:string}>()
const {t,locale}=useI18n()
</script>
<template>
 <span v-if="amounts===null">—</span>
 <span v-else-if="!amounts.length">{{emptyCurrency?operationalMoney(0,emptyCurrency,locale):t('projects.noRegisteredAmounts')}}</span>
 <span v-for="row in amounts || []" :key="row.currencyCode" class="operational-amount">{{operationalMoney(row[metric],row.currencyCode,locale)}}</span>
</template>
<style scoped>.operational-amount{display:block;overflow-wrap:anywhere}</style>
