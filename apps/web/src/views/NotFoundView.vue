<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-icon">404</div>
    <span class="eyebrow">Flowora ERP</span>
    <h1>Page not found</h1>
    <p>The page you are looking for does not exist.</p>
    <el-button type="primary" round @click="router.push('/dashboard')">Back to workspace</el-button>
  </div>
</template>
