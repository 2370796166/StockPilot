<script setup lang="ts">
import { computed } from 'vue'
import { Box, Checked, OfficeBuilding, ShoppingBag, Switch, Tickets, TrendCharts, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
const modules = computed(() => [
  auth.can('MASTER_DATA_READ') && { title: '仓库管理', text: '维护仓库、库位、分类、SKU 和供应商', path: '/master-data/warehouses', icon: OfficeBuilding },
  auth.can('PURCHASE_RECEIPT_READ') && { title: '采购入库', text: '制单、提交、审核并确认入库', path: '/documents/purchase-receipts', icon: Tickets },
  auth.can('SALES_OUTBOUND_READ') && { title: '销售出库', text: '冻结、审核、出库或取消释放', path: '/documents/sales-outbound', icon: ShoppingBag },
  auth.can('INVENTORY_READ') && { title: '库存中心', text: '查询库存余额与不可变库存流水', path: '/inventory/balances', icon: Box },
  auth.can('TRANSFER_READ') && { title: '库存调拨', text: '管理源仓调出、在途和目标收货', path: '/documents/transfers', icon: Switch },
  auth.can('INVENTORY_COUNT_READ') && { title: '库存盘点', text: '录入实盘并完成审核调整', path: '/documents/inventory-counts', icon: Checked },
  auth.can('SECURITY_USER_READ') && { title: '系统管理', text: '维护用户、角色和授权关系', path: '/security/users', icon: User },
  auth.can('INVENTORY_READ') && { title: '库存流水', text: '追踪每一笔库存数量变化', path: '/inventory/ledgers', icon: TrendCharts },
].filter(Boolean) as Array<{ title: string; text: string; path: string; icon: typeof Box }>)
</script>
<template>
  <div class="page-stack">
    <section class="welcome-card">
      <div><p class="eyebrow">工作台</p><h1>{{ auth.user?.displayName || auth.user?.username }}，欢迎回来</h1><p>当前页面仅呈现已接通的真实业务模块。</p></div>
      <div class="role-list"><span>当前角色</span><el-tag v-for="role in auth.user?.roles" :key="role" effect="plain">{{ role }}</el-tag></div>
    </section>
    <section>
      <div class="section-heading"><div><h2>可访问模块</h2><p>权限来自后端当前登录用户接口</p></div></div>
      <el-empty v-if="modules.length === 0" description="当前账号暂无可访问的业务模块" />
      <div v-else class="module-grid">
        <RouterLink v-for="module in modules" :key="module.path" :to="module.path" class="module-card">
          <span class="module-icon"><el-icon><component :is="module.icon" /></el-icon></span><div><h3>{{ module.title }}</h3><p>{{ module.text }}</p></div><span class="arrow">→</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
