<template>
  <div class="result-page">
    <el-result
      icon="warning"
      title="无权访问"
      sub-title="当前账号没有访问该页面所需的权限"
      ><template #extra
        ><el-button
          type="primary"
          @click="$router.push('/dashboard')"
          >返回首页</el-button
        ></template
      ></el-result
    >
  </div>
</template>
