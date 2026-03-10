<template>
  <div class="dashboard-home">
    <el-row :gutter="16" class="mb-16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">合同总数</div>
            <div class="value">{{ metrics.totalContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">审批中</div>
            <div class="value warning">{{ metrics.approvingContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">已生效</div>
            <div class="value success">{{ metrics.activeContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">本月新增</div>
            <div class="value primary">{{ metrics.newThisMonth }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>系统概览</span>
      </template>
      <p class="hint">
        欢迎使用合同管理系统。请通过左侧菜单进入“合同管理”或“合同审批”页面进行具体操作。
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";

const metrics = reactive({
  totalContracts: 0,
  approvingContracts: 0,
  activeContracts: 0,
  newThisMonth: 0,
});
</script>

<style lang="scss" scoped>
.dashboard-home {
  .mb-16 {
    margin-bottom: 16px;
  }

  .metric {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .label {
      color: #909399;
      font-size: 13px;
    }

    .value {
      color: #303133;
      font-size: 30px;
      font-weight: 700;
      line-height: 1;

      &.warning {
        color: #e6a23c;
      }
      &.success {
        color: #67c23a;
      }
      &.primary {
        color: #409eff;
      }
    }
  }

  .hint {
    color: #606266;
    line-height: 1.8;
  }
}
</style>
