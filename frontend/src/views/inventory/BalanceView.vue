<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageBalances } from '../../api/inventory'
import EntityRef from '../../components/EntityRef.vue'
import RemoteLocationSelect from '../../components/RemoteLocationSelect.vue'
import RemoteMasterDataSelect from '../../components/RemoteMasterDataSelect.vue'
import ServerPagination from '../../components/ServerPagination.vue'
import type { InventoryBalance } from '../../types/inventory'
const loading=ref(false), records=ref<InventoryBalance[]>([]), total=ref(0)
const query=reactive<{page:number;size:number;warehouseId?:number;locationId?:number;skuId?:number}>({page:1,size:20})
async function load(){loading.value=true;try{const result=await pageBalances(query);records.value=result.records;total.value=result.total}finally{loading.value=false}}
function reset(){Object.assign(query,{page:1,warehouseId:undefined,locationId:undefined,skuId:undefined});void load()}
onMounted(load)
</script>
<template><div class="page-stack"><div class="page-heading"><div><p class="eyebrow">库存中心</p><h1>库存余额</h1><p>MySQL 权威库存，只读展示实际、可用与冻结数量</p></div></div><el-card shadow="never" class="filter-card"><el-form inline @submit.prevent="query.page=1;load()"><el-form-item label="仓库"><RemoteMasterDataSelect v-model="query.warehouseId" resource="warehouses" /></el-form-item><el-form-item label="库位"><RemoteLocationSelect v-model="query.locationId" :warehouse-id="query.warehouseId" /></el-form-item><el-form-item label="SKU"><RemoteMasterDataSelect v-model="query.skuId" resource="skus" /></el-form-item><el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item></el-form></el-card><el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="records"><el-table-column label="仓库" min-width="170"><template #default="scope"><EntityRef resource="warehouses" :id="scope.row.warehouseId" /></template></el-table-column><el-table-column label="库位" min-width="170"><template #default="scope"><EntityRef resource="locations" :id="scope.row.locationId" /></template></el-table-column><el-table-column label="SKU" min-width="180"><template #default="scope"><EntityRef resource="skus" :id="scope.row.skuId" /></template></el-table-column><el-table-column prop="actualQuantity" label="实际库存" align="right" /><el-table-column prop="availableQuantity" label="可用库存" align="right" /><el-table-column prop="frozenQuantity" label="冻结库存" align="right" /><el-table-column prop="updatedAt" label="更新时间" min-width="165" /></el-table><ServerPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" /></el-card></div></template>
