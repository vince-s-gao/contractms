<template>
  <div class="error-page">
    <div class="error-content">
      <div class="error-code">404</div>
      <div class="error-message">页面不存在</div>
      <div class="error-description">抱歉，您访问的页面不存在或已被移除</div>
      <el-button type="primary" @click="goHome"> 返回首页 </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

const goHome = () => {
  router.push("/dashboard");
};
</script>

<style lang="scss" scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.error-content {
  text-align: center;

  .error-code {
    font-size: 120px;
    font-weight: bold;
    color: #409eff;
    margin-bottom: 20px;
  }

  .error-message {
    font-size: 24px;
    color: #303133;
    margin-bottom: 10px;
  }

  .error-description {
    font-size: 14px;
    color: #606266;
    margin-bottom: 30px;
  }
}
</style>
