<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Box, Checked, CollectionTag, Expand, Fold, HomeFilled, Location, OfficeBuilding, ShoppingBag, Switch, Tickets, TrendCharts, User, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const activePath = computed(() => route.path)

const masterDataItems = computed(() => auth.can('MASTER_DATA_READ') ? [
  { path: '/master-data/warehouses', label: '仓库管理', icon: OfficeBuilding },
  { path: '/master-data/locations', label: '库位管理', icon: Location },
  { path: '/master-data/categories', label: '商品分类', icon: CollectionTag },
  { path: '/master-data/skus', label: 'SKU 管理', icon: Box },
  { path: '/master-data/suppliers', label: '供应商管理', icon: ShoppingBag },
] : [])
const businessItems = computed(() => [
  auth.can('PURCHASE_RECEIPT_READ') && { path: '/documents/purchase-receipts', label: '采购入库单', icon: Tickets },
  auth.can('SALES_OUTBOUND_READ') && { path: '/documents/sales-outbound', label: '销售出库单', icon: Tickets },
  auth.can('TRANSFER_READ') && { path: '/documents/transfers', label: '库存调拨', icon: Switch },
  auth.can('INVENTORY_COUNT_READ') && { path: '/documents/inventory-counts', label: '库存盘点', icon: Checked },
  auth.can('INVENTORY_READ') && { path: '/inventory/balances', label: '库存余额', icon: Box },
  auth.can('INVENTORY_READ') && { path: '/inventory/ledgers', label: '库存流水', icon: TrendCharts },
].filter(Boolean) as Array<{ path: string; label: string; icon: typeof Box }>)
const securityItems = computed(() => [
  auth.can('SECURITY_USER_READ') && { path: '/security/users', label: '用户管理', icon: User },
  auth.can('SECURITY_ROLE_READ') && { path: '/security/roles', label: '角色管理', icon: UserFilled },
].filter(Boolean) as Array<{ path: string; label: string; icon: typeof User }>)

async function logout() {
  auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '72px' : '232px'" class="sidebar">
      <div class="brand" :class="{ compact: collapsed }">
        <div class="brand-mark">S</div>
        <div v-if="!collapsed" class="brand-copy">
          <strong>StockPilot</strong>
          <span>仓储管理后台</span>
        </div>
      </div>
      <el-menu :default-active="activePath" router :collapse="collapsed" :collapse-transition="false" class="side-menu">
        <el-menu-item index="/dashboard"><el-icon><HomeFilled /></el-icon><template #title>首页总览</template></el-menu-item>
        <el-menu-item v-for="item in masterDataItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
        </el-menu-item>
        <el-menu-item v-for="item in businessItems" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template></el-menu-item>
        <el-menu-item v-for="item in securityItems" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <el-button text class="collapse-button" :aria-label="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
          <el-icon size="20"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
        </el-button>
        <div class="topbar-title">{{ route.meta.title }}</div>
        <el-dropdown trigger="click">
          <button class="user-menu">
            <span class="user-avatar"><el-icon><UserFilled /></el-icon></span>
            <span class="user-copy"><strong>{{ auth.user?.displayName || auth.user?.username }}</strong><small>{{ auth.user?.username }}</small></span>
          </button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </el-header>
      <el-main class="main-content"><RouterView /></el-main>
    </el-container>
  </el-container>
</template>
