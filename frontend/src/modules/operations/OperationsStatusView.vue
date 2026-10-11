<script setup lang="ts">
import { ElButton, ElCard, ElDescriptions, ElDescriptionsItem, vLoading } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/loading/style/css'
import { onMounted, ref } from 'vue'
import { getOperationsStatus } from './api'
import type { OperationsStatus } from './api'
import { useLatestRequest } from '@/shared/composables/useLatestRequest'
const status = ref<OperationsStatus | null>(null),
  loading = ref(false)
const latest = useLatestRequest()
async function load() {
  const sequence = latest.next()
  loading.value = true
  status.value = null
  try {
    const value = await getOperationsStatus()
    if (latest.isCurrent(sequence)) status.value = value
  } catch {
    /* Failed reads are never shown as zero backlog. */
  } finally {
    if (latest.isCurrent(sequence)) loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">运行保障</p>
        <h1>运行状态</h1>
        <p>当前进程与业务积压的观测值，点击刷新获取最新状态</p>
      </div>
      <el-button
        :loading="loading"
        @click="load"
        >刷新</el-button
      >
    </div>
    <el-card
      v-loading="loading"
      shadow="never"
      ><template v-if="status"
        ><p class="eyebrow">观测时间：{{ status.observedAt }}</p>
        <el-descriptions
          :column="2"
          border
          ><el-descriptions-item label="消息功能">{{
            status.messagingEnabled ? '已启用' : '未启用'
          }}</el-descriptions-item
          ><el-descriptions-item label="基础资料缓存">{{
            status.cacheEnabled ? '已启用' : '未启用'
          }}</el-descriptions-item
          ><el-descriptions-item label="待发布消息">{{ status.backlog.pendingMessages }}</el-descriptions-item
          ><el-descriptions-item label="发布失败消息">{{ status.backlog.failedMessages }}</el-descriptions-item
          ><el-descriptions-item label="未处理失败记录">{{ status.backlog.pendingFailures }}</el-descriptions-item
          ><el-descriptions-item label="最老待发布消息年龄">{{
            status.backlog.oldestPendingAgeSeconds === null
              ? '无待发布消息'
              : `${status.backlog.oldestPendingAgeSeconds} 秒`
          }}</el-descriptions-item
          ><el-descriptions-item label="使用中数据库连接">{{
            status.activeConnections < 0 ? '不可获取' : status.activeConnections
          }}</el-descriptions-item
          ><el-descriptions-item label="空闲数据库连接">{{
            status.idleConnections < 0 ? '不可获取' : status.idleConnections
          }}</el-descriptions-item
          ><el-descriptions-item label="等待连接的线程">{{
            status.waitingConnections < 0 ? '不可获取' : status.waitingConnections
          }}</el-descriptions-item
          ><el-descriptions-item label="进程启动后已观测请求">{{ status.observedRequests }}</el-descriptions-item
          ><el-descriptions-item label="已观测请求累计耗时"
            >{{ status.totalRequestSeconds.toFixed(3) }} 秒</el-descriptions-item
          ><el-descriptions-item label="当前堆内存使用"
            >{{ (status.heapUsedBytes / 1024 / 1024).toFixed(1) }} MB</el-descriptions-item
          ></el-descriptions
        >
        <p>消息功能关闭时，已有待发布记录仍保留。请求累计值随应用重启清零。</p></template
      >
      <p v-else-if="!loading">状态未能获取，请检查服务后重新刷新。</p></el-card
    >
  </div>
</template>
