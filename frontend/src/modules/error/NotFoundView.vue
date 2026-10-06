<script setup lang="ts">
import { ElButton, ElResult } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/result/style/css'
</script>

<template>
  <div class="result-page">
    <el-result
      icon="info"
      title="页面不存在"
      sub-title="请检查访问地址"
      ><template #extra
        ><el-button
          type="primary"
          @click="$router.push('/')"
          >返回首页</el-button
        ></template
      ></el-result
    >
  </div>
</template>
