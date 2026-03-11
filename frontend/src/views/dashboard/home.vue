<template>
  <div class="dashboard-home">
    <el-card shadow="never" class="mb-16">
      <el-form inline>
        <el-form-item label="统计年度">
          <el-select
            v-model="selectedYear"
            placeholder="请选择年度"
            style="width: 160px"
            @change="loadOverview"
          >
            <el-option label="全部" :value="undefined" />
            <el-option
              v-for="year in yearOptions"
              :key="year"
              :label="String(year)"
              :value="year"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="mb-16">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">合同总数</div>
            <div class="value">{{ metrics.totalContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">审批中</div>
            <div class="value warning">{{ metrics.approvingContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">已生效</div>
            <div class="value success">{{ metrics.activeContracts }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">本月新增</div>
            <div class="value primary">{{ metrics.newThisMonth }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">销售收入</div>
            <div class="value income">{{ formatCurrency(metrics.salesRevenue) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">采购费用</div>
            <div class="value expense">{{ formatCurrency(metrics.purchaseCost) }}</div>
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
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";

const metrics = reactive({
  totalContracts: 0,
  approvingContracts: 0,
  activeContracts: 0,
  newThisMonth: 0,
  salesRevenue: 0,
  purchaseCost: 0,
});

const router = useRouter();
const currentYear = new Date().getFullYear();
const selectedYear = ref<number | undefined>(currentYear);
const yearOptions = computed(() => {
  const years = new Set<number>([currentYear]);
  for (let i = 1; i <= 5; i++) {
    years.add(currentYear - i);
  }
  return Array.from(years).sort((a, b) => b - a);
});

const loadOverview = async () => {
  try {
    const token = localStorage.getItem("token");
    const query = selectedYear.value ? `?year=${selectedYear.value}` : "";
    const response = await fetch(`/api/contracts/statistics/overview${query}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });

    if (response.status === 401) {
      ElMessage.error("登录已过期，请重新登录");
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      router.push("/login");
      return;
    }
    if (!response.ok) {
      throw new Error("获取仪表板数据失败");
    }

    const data = await response.json();
    metrics.totalContracts = Number(data.totalContracts || 0);
    metrics.approvingContracts = Number(data.approvingContracts || 0);
    metrics.activeContracts = Number(data.activeContracts || 0);
    metrics.newThisMonth = Number(data.newThisMonth || 0);
    metrics.salesRevenue = Number(data.salesRevenue || 0);
    metrics.purchaseCost = Number(data.purchaseCost || 0);
  } catch (error) {
    console.error("加载仪表板数据失败:", error);
    ElMessage.error("加载仪表板数据失败");
  }
};

onMounted(() => {
  loadOverview();
});

const formatCurrency = (value: number) => {
  return `¥${(Number(value) || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
};
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
      &.income {
        color: #67c23a;
      }
      &.expense {
        color: #f56c6c;
      }
    }
  }

  .hint {
    color: #606266;
    line-height: 1.8;
  }
}
</style>
