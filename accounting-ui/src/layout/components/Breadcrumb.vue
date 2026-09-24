<template>
  <el-breadcrumb separator="/" class="fin-breadcrumb">
    <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
    <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
      {{ item.meta?.title || item.name }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const breadcrumbs = computed(() => {
  return route.matched.filter((item) => item.meta && item.meta.title && item.path !== '/')
})
</script>

<style scoped>
.fin-breadcrumb {
  font-size: 14px;
}
</style>
