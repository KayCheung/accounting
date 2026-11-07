<template>
  <div class="fin-card placeholder-card">
    <el-result
      icon="info"
      :title="title"
      :sub-title="`该功能模块计划在 ${step} 中集中开发交付。当前 Step 21 前端脚手架与公共基础组件库已就绪。`"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/component-demo')">
          查看公共组件规范展台
        </el-button>
        <el-button @click="$router.push('/dashboard')">
          返回仪表盘
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const title = computed(() => {
  return (route.meta?.title as string) || '页面功能'
})

const step = computed(() => {
  if (route.path.startsWith('/config')) {
    return 'Step 22 · Config Pages（配置管理模块）'
  }
  return 'Step 23 · Business Pages（业务功能模块）'
})
</script>

<style scoped>
.placeholder-card {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
