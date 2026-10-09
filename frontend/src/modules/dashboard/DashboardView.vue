<script setup lang="ts">
import { ElEmpty, ElIcon, ElTag } from 'element-plus'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/tag/style/css'
import { computed } from 'vue'
import {
  Box,
  Checked,
  OfficeBuilding,
  ShoppingBag,
  Switch,
  Tickets,
  TrendCharts,
  User,
  ArrowRight,
  ChatDotRound,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/modules/auth/store'
const auth = useAuthStore()
const modules = computed(
  () =>
    [
      auth.can('MASTER_DATA_READ') && {
        title: '仓库管理',
        text: '维护仓库、库位、商品和供应商',
        path: '/master-data/warehouses',
        icon: OfficeBuilding,
      },
      auth.can('PURCHASE_RECEIPT_READ') && {
        title: '采购入库',
        text: '制单、提交、审核并确认入库',
        path: '/documents/purchase-receipts',
        icon: Tickets,
      },
      auth.can('SALES_OUTBOUND_READ') && {
        title: '销售出库',
        text: '冻结、审核、出库或取消释放',
        path: '/documents/sales-outbound',
        icon: ShoppingBag,
      },
      auth.can('INVENTORY_READ') && {
        title: '库存中心',
        text: '查看各仓库的实际、可用和冻结数量',
        path: '/inventory/balances',
        icon: Box,
      },
      auth.can('TRANSFER_READ') && {
        title: '库存调拨',
        text: '管理源仓调出、在途和目标收货',
        path: '/documents/transfers',
        icon: Switch,
      },
      auth.can('INVENTORY_COUNT_READ') && {
        title: '库存盘点',
        text: '录入实盘并完成审核调整',
        path: '/documents/inventory-counts',
        icon: Checked,
      },
      auth.can('SECURITY_USER_READ') && {
        title: '系统管理',
        text: '维护用户、角色和授权关系',
        path: '/security/users',
        icon: User,
      },
      auth.can('INVENTORY_READ') && {
        title: '库存流水',
        text: '追踪每一笔库存数量变化',
        path: '/inventory/ledgers',
        icon: TrendCharts,
      },
    ].filter(Boolean) as Array<{ title: string; text: string; path: string; icon: typeof Box }>,
)
</script>
<template>
  <div class="page-stack dashboard-page">
    <section class="welcome-card">
      <div>
        <p class="eyebrow">仓储工作台</p>
        <h1>{{ auth.user?.displayName || auth.user?.username }}，欢迎回来</h1>
        <p>从一张业务单据开始，让库存流转清晰有序。</p>
        <div class="role-list">
          <span>当前角色</span
          ><el-tag
            v-for="role in auth.user?.roles"
            :key="role"
            effect="plain"
            >{{ role }}</el-tag
          >
        </div>
      </div>
      <div class="welcome-aside">
        <el-icon><Box /></el-icon>
        <div><strong>每一笔变化，都有据可循</strong><span>入库 · 出库 · 调拨 · 盘点</span></div>
      </div>
    </section>
    <div class="workspace-grid">
      <section>
        <div class="section-heading">
          <div>
            <h2>业务入口</h2>
            <p>按当前账号权限展示，选择业务开始处理</p>
          </div>
        </div>
        <el-empty
          v-if="modules.length === 0"
          description="当前账号暂无可访问的业务模块"
        />
        <div
          v-else
          class="module-grid"
        >
          <RouterLink
            v-for="module in modules"
            :key="module.path"
            :to="module.path"
            class="module-card"
          >
            <span class="module-icon"
              ><el-icon><component :is="module.icon" /></el-icon
            ></span>
            <div>
              <h3>{{ module.title }}</h3>
              <p>{{ module.text }}</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </RouterLink>
        </div>
      </section>
      <aside class="workspace-aside">
        <section class="assistant-entry">
          <span class="assistant-icon"
            ><el-icon><ChatDotRound /></el-icon
          ></span>
          <h2>有问题，问仓储助手</h2>
          <p>用自然语言查询库存、查找冻结来源，或追溯业务单据。</p>
          <div class="assistant-example">“这件商品的库存冻结在哪些单据里？”</div>
          <RouterLink
            to="/ai-assistant"
            class="assistant-link"
            >开始查询<el-icon><ArrowRight /></el-icon
          ></RouterLink>
          <small>只读查询，支持连续追问</small>
        </section>
        <section class="inventory-guide">
          <div class="section-heading"><h2>读懂库存数量</h2></div>
          <dl>
            <div>
              <dt><span class="quantity-key actual"></span>实际库存</dt>
              <dd>仓库内实际持有的数量</dd>
            </div>
            <div>
              <dt><span class="quantity-key available"></span>可用库存</dt>
              <dd>可继续分配给业务的数量</dd>
            </div>
            <div>
              <dt><span class="quantity-key frozen"></span>冻结库存</dt>
              <dd>已被销售或调拨单据占用</dd>
            </div>
          </dl>
          <p class="inventory-equation">实际库存 = 可用库存 + 冻结库存</p>
        </section>
      </aside>
    </div>
    <section
      class="workflow-guide"
      v-if="auth.can('PURCHASE_RECEIPT_READ') || auth.can('SALES_OUTBOUND_READ')"
    >
      <div class="section-heading">
        <div>
          <h2>单据如何流转</h2>
          <p>按流程完成操作，库存随业务动作更新</p>
        </div>
      </div>
      <div class="workflow-lanes">
        <div
          v-if="auth.can('PURCHASE_RECEIPT_READ')"
          class="workflow-lane"
        >
          <strong>采购入库</strong>
          <div class="workflow-steps">
            <span>创建草稿</span><el-icon><ArrowRight /></el-icon><span>提交</span><el-icon><ArrowRight /></el-icon
            ><span>审核</span><el-icon><ArrowRight /></el-icon><span class="workflow-final">确认入库</span>
          </div>
        </div>
        <div
          v-if="auth.can('SALES_OUTBOUND_READ')"
          class="workflow-lane"
        >
          <strong>销售出库</strong>
          <div class="workflow-steps">
            <span>创建草稿</span><el-icon><ArrowRight /></el-icon><span>冻结库存</span><el-icon><ArrowRight /></el-icon
            ><span>审核</span><el-icon><ArrowRight /></el-icon><span class="workflow-final">确认出库</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
