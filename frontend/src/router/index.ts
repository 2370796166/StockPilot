import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getAccessToken } from '../utils/session'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    authority?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('../layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '首页总览' } },
        { path: 'master-data/warehouses', name: 'warehouses', component: () => import('../views/master-data/WarehouseView.vue'), meta: { title: '仓库管理', authority: 'MASTER_DATA_READ' } },
        { path: 'master-data/locations', name: 'locations', component: () => import('../views/master-data/LocationView.vue'), meta: { title: '库位管理', authority: 'MASTER_DATA_READ' } },
        { path: 'master-data/categories', name: 'categories', component: () => import('../views/master-data/CategoryView.vue'), meta: { title: '商品分类', authority: 'MASTER_DATA_READ' } },
        { path: 'master-data/skus', name: 'skus', component: () => import('../views/master-data/SkuView.vue'), meta: { title: 'SKU 管理', authority: 'MASTER_DATA_READ' } },
        { path: 'master-data/suppliers', name: 'suppliers', component: () => import('../views/master-data/SupplierView.vue'), meta: { title: '供应商管理', authority: 'MASTER_DATA_READ' } },
        { path: 'documents/purchase-receipts', name: 'purchase-receipts', component: () => import('../views/documents/PurchaseReceiptView.vue'), meta: { title: '采购入库单', authority: 'PURCHASE_RECEIPT_READ' } },
        { path: 'documents/sales-outbound', name: 'sales-outbound', component: () => import('../views/documents/SalesOutboundView.vue'), meta: { title: '销售出库单', authority: 'SALES_OUTBOUND_READ' } },
        { path: 'documents/transfers', name: 'transfers', component: () => import('../views/documents/TransferView.vue'), meta: { title: '库存调拨', authority: 'TRANSFER_READ' } },
        { path: 'documents/inventory-counts', name: 'inventory-counts', component: () => import('../views/documents/InventoryCountView.vue'), meta: { title: '库存盘点', authority: 'INVENTORY_COUNT_READ' } },
        { path: 'inventory/balances', name: 'inventory-balances', component: () => import('../views/inventory/BalanceView.vue'), meta: { title: '库存余额', authority: 'INVENTORY_READ' } },
        { path: 'inventory/ledgers', name: 'inventory-ledgers', component: () => import('../views/inventory/LedgerView.vue'), meta: { title: '库存流水', authority: 'INVENTORY_READ' } },
        { path: 'security/users', name: 'security-users', component: () => import('../views/security/UserView.vue'), meta: { title: '用户管理', authority: 'SECURITY_USER_READ' } },
        { path: 'security/roles', name: 'security-roles', component: () => import('../views/security/RoleView.vue'), meta: { title: '角色管理', authority: 'SECURITY_ROLE_READ' } },
        { path: 'forbidden', name: 'forbidden', component: () => import('../views/ForbiddenView.vue'), meta: { title: '无权访问' } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
  ],
})

function loginRedirect(to: RouteLocationNormalized) {
  return { name: 'login', query: { redirect: to.fullPath } }
}

router.beforeEach(async (to) => {
  document.title = `${to.meta.title ?? '管理后台'} · StockPilot`
  const auth = useAuthStore()
  const hasToken = Boolean(getAccessToken())

  if (to.name === 'login') {
    if (!hasToken) return true
    if (!auth.initialized) await auth.loadCurrentUser()
    return auth.authenticated ? { name: 'dashboard' } : true
  }

  if (to.matched.some((record) => record.meta.requiresAuth)) {
    if (!hasToken) return loginRedirect(to)
    if (!auth.authenticated && !(await auth.loadCurrentUser())) return loginRedirect(to)
    if (to.meta.authority && !auth.can(to.meta.authority)) return { name: 'forbidden' }
  }
  return true
})

export default router
