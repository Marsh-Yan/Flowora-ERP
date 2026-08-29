<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ permission: string }>()
const authStore = useAuthStore()
const allowed = computed(() => authStore.hasPermission(props.permission))
</script>

<template>
  <slot v-if="allowed" />
  <slot v-else name="denied" />
</template>
