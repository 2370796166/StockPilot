import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/modules/auth/store'
import { getAccessToken } from '@/shared/utils/session'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    authority?: string
  }
}

// 路由元数据声明页面是否需要登录及所需权限；这里只控制前端可见性，后端仍执行最终授权。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/modules/auth/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'ai-assistant',
          name: 'ai-assistant',
          component: () => import('@/modules/ai/AiAgentView.vue'),
          meta: { title: 'AI 仓储助手' },
        },
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/modules/dashboard/DashboardView.vue'),
          meta: { title: '首页总览' },
        },
        {
          path: 'master-data/warehouses',
          name: 'warehouses',
          component: () => import('@/modules/master-data/views/WarehouseView.vue'),
          meta: { title: '仓库管理', authority: 'MASTER_DATA_READ' },
        },
        {
          path: 'master-data/locations',
          name: 'locations',
          component: () => import('@/modules/master-data/views/LocationView.vue'),
          meta: { title: '库位管理', authority: 'MASTER_DATA_READ' },
        },
        {
          path: 'master-data/categories',
          name: 'categories',
          component: () => import('@/modules/master-data/views/CategoryView.vue'),
          meta: { title: '商品分类', authority: 'MASTER_DATA_READ' },
        },
        {
          path: 'master-data/skus',
          name: 'skus',
          component: () => import('@/modules/master-data/views/SkuView.vue'),
          meta: { title: '商品管理', authority: 'MASTER_DATA_READ' },
        },
        {
          path: 'master-data/suppliers',
          name: 'suppliers',
          component: () => import('@/modules/master-data/views/SupplierView.vue'),
          meta: { title: '供应商管理', authority: 'MASTER_DATA_READ' },
        },
        {
          path: 'documents/purchase-receipts',
          name: 'purchase-receipts',
          component: () => import('@/modules/documents/views/PurchaseReceiptView.vue'),
          meta: { title: '采购入库单', authority: 'PURCHASE_RECEIPT_READ' },
        },
        {
          path: 'documents/sales-outbound',
          name: 'sales-outbound',
          component: () => import('@/modules/documents/views/SalesOutboundView.vue'),
          meta: { title: '销售出库单', authority: 'SALES_OUTBOUND_READ' },
        },
        {
          path: 'documents/transfers',
          name: 'transfers',
          component: () => import('@/modules/transfer/TransferView.vue'),
          meta: { title: '库存调拨', authority: 'TRANSFER_READ' },
        },
        {
          path: 'documents/inventory-counts',
          name: 'inventory-counts',
          component: () => import('@/modules/inventory-count/InventoryCountView.vue'),
          meta: { title: '库存盘点', authority: 'INVENTORY_COUNT_READ' },
        },
        {
          path: 'inventory/balances',
          name: 'inventory-balances',
          component: () => import('@/modules/inventory/views/BalanceView.vue'),
          meta: { title: '库存余额', authority: 'INVENTORY_READ' },
        },
        {
          path: 'inventory/ledgers',
          name: 'inventory-ledgers',
          component: () => import('@/modules/inventory/views/LedgerView.vue'),
          meta: { title: '库存流水', authority: 'INVENTORY_READ' },
        },
        {
          path: 'security/users',
          name: 'security-users',
          component: () => import('@/modules/security/views/UserView.vue'),
          meta: { title: '用户管理', authority: 'SECURITY_USER_READ' },
        },
        {
          path: 'security/roles',
          name: 'security-roles',
          component: () => import('@/modules/security/views/RoleView.vue'),
          meta: { title: '角色管理', authority: 'SECURITY_ROLE_READ' },
        },
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('@/modules/error/ForbiddenView.vue'),
          meta: { title: '无权访问' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/modules/error/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

function loginRedirect(to: RouteLocationNormalized) {
  return { name: 'login', query: { redirect: to.fullPath } }
}

// 全局守卫先恢复当前用户和实时权限，再决定进入页面、跳转登录页或展示无权访问页。
// 登录成功后的 redirect 参数保留原目标地址，避免认证完成后丢失用户正在访问的业务页面。
router.beforeEach(async (to) => {
  document.title = `${to.meta.title ?? '管理后台'} · StockPilot`
  const auth = useAuthStore()
  const hasToken = Boolean(getAccessToken())

  if (to.name === 'login') {
    if (!hasToken) return true
    return (await auth.loadCurrentUser()) ? { name: 'dashboard' } : true
  }

  if (to.matched.some((record) => record.meta.requiresAuth)) {
    if (!hasToken) return loginRedirect(to)
    if (!(await auth.loadCurrentUser())) return loginRedirect(to)
    if (to.meta.authority && !auth.can(to.meta.authority)) return { name: 'forbidden' }
  }
  return true
})

export default router
