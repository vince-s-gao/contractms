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
            <div class="value income">
              {{ formatCurrency(metrics.salesRevenue) }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="metric">
            <div class="label">采购费用</div>
            <div class="value expense">
              {{ formatCurrency(metrics.purchaseCost) }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span>合同类型分布</span>
      </template>
      <div v-if="contractTypeStats.length" class="type-pie-wrap">
        <div class="type-pie-chart">
          <svg
            :viewBox="`0 0 ${pieViewWidth} ${pieViewHeight}`"
            class="pie-svg"
          >
            <circle
              :cx="pieCenterX"
              :cy="pieCenterY"
              :r="pieRadius"
              fill="none"
              stroke="#f2f3f5"
              :stroke-width="pieStrokeWidth"
            />
            <circle
              v-for="segment in pieSegments"
              :key="segment.code"
              :cx="pieCenterX"
              :cy="pieCenterY"
              :r="pieRadius"
              fill="none"
              :stroke="segment.color"
              :stroke-width="pieStrokeWidth"
              stroke-linecap="butt"
              :transform="`rotate(-90 ${pieCenterX} ${pieCenterY})`"
              :stroke-dasharray="`${segment.length} ${pieCircumference - segment.length}`"
              :stroke-dashoffset="`-${segment.offset}`"
              class="pie-segment"
              @click="handleContractTypeSegmentClick(segment)"
            />
            <g
              v-for="segment in pieSegments"
              :key="`${segment.code}-label`"
              class="pie-label-group"
              tabindex="0"
              @click="handleContractTypeSegmentClick(segment)"
              @keydown.enter.prevent="handleContractTypeSegmentClick(segment)"
              @keydown.space.prevent="handleContractTypeSegmentClick(segment)"
            >
              <line
                :x1="segment.lineStartX"
                :y1="segment.lineStartY"
                :x2="segment.lineMidX"
                :y2="segment.lineMidY"
                :stroke="segment.color"
                stroke-width="1.2"
              />
              <line
                :x1="segment.lineMidX"
                :y1="segment.lineMidY"
                :x2="segment.lineEndX"
                :y2="segment.lineEndY"
                :stroke="segment.color"
                stroke-width="1.2"
              />
              <circle
                :cx="segment.lineStartX"
                :cy="segment.lineStartY"
                r="2"
                :fill="segment.color"
              />
              <text
                :x="segment.textX"
                :y="segment.textY"
                :text-anchor="segment.textAnchor"
                class="pie-label-text"
              >
                {{ segment.name }} {{ segment.count }}份（{{
                  segment.percentText
                }}）
              </text>
            </g>
          </svg>
        </div>
      </div>
      <el-empty v-else description="暂无合同类型数据" :image-size="72" />
    </el-card>

    <el-card shadow="never" class="mt-16">
      <template #header>
        <div class="customer-header">
          <span>客户收入贡献（销售合同）</span>
          <span class="customer-top5"
            >Top5客户收入占比：{{
              formatPercent(top5CustomerRevenueShare)
            }}</span
          >
        </div>
      </template>
      <el-table :data="topCustomerRevenue" stripe size="small" max-height="420">
        <el-table-column prop="rank" label="排名" width="70" />
        <el-table-column prop="customerName" label="客户名称" min-width="220" />
        <el-table-column prop="contractCount" label="合同数" width="90" />
        <el-table-column label="收入贡献" width="180">
          <template #default="{ row }">
            {{ formatCurrency(row.revenue) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="mt-16">
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
import {
  getContractOverview,
  type ContractOverviewItem,
  type TopCustomerRevenueItem,
} from "@/api/contract";
import { extractErrorMessage } from "@/utils/error";

const router = useRouter();
const metrics = reactive({
  totalContracts: 0,
  approvingContracts: 0,
  activeContracts: 0,
  newThisMonth: 0,
  salesRevenue: 0,
  purchaseCost: 0,
});
const contractTypeStats = ref<ContractOverviewItem[]>([]);
const topCustomerRevenue = ref<TopCustomerRevenueItem[]>([]);
const top5CustomerRevenueShare = ref(0);
const pieColors = [
  "#409EFF",
  "#67C23A",
  "#E6A23C",
  "#F56C6C",
  "#8E44AD",
  "#16A085",
  "#34495E",
  "#D35400",
];
const pieViewWidth = 560;
const pieViewHeight = 300;
const pieCenterX = 280;
const pieCenterY = 140;
const pieRadius = 52;
const pieStrokeWidth = 24;
const pieCircumference = 2 * Math.PI * pieRadius;
const pieTotal = computed(() =>
  contractTypeStats.value.reduce(
    (sum, item) => sum + Number(item.count || 0),
    0,
  ),
);
const pieSegments = computed(() => {
  let offset = 0;
  const segments = contractTypeStats.value.map((item, index) => {
    const count = Number(item.count || 0);
    const ratio = pieTotal.value > 0 ? count / pieTotal.value : 0;
    const length = ratio * pieCircumference;
    const segment = {
      code: item.code,
      name: item.name,
      count,
      color: pieColors[index % pieColors.length],
      offset,
      length,
      percentText: `${(ratio * 100).toFixed(1)}%`,
      lineStartX: 0,
      lineStartY: 0,
      lineMidX: 0,
      lineMidY: 0,
      lineEndX: 0,
      lineEndY: 0,
      textX: 0,
      textY: 0,
      textAnchor: "start" as "start" | "end",
      preferredY: 0,
      isRight: true,
    };
    const midRatio =
      pieCircumference === 0
        ? 0
        : (segment.offset + segment.length / 2) / pieCircumference;
    const angle = -Math.PI / 2 + Math.PI * 2 * midRatio;
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    segment.isRight = cos >= 0;
    segment.lineStartX = pieCenterX + cos * (pieRadius + pieStrokeWidth / 2);
    segment.lineStartY = pieCenterY + sin * (pieRadius + pieStrokeWidth / 2);
    segment.lineMidX = pieCenterX + cos * (pieRadius + pieStrokeWidth / 2 + 18);
    segment.lineMidY = pieCenterY + sin * (pieRadius + pieStrokeWidth / 2 + 18);
    segment.preferredY = segment.lineMidY;
    const horizontal = cos >= 0 ? 28 : -28;
    segment.lineEndX = segment.lineMidX + horizontal;
    segment.lineEndY = segment.preferredY;
    segment.textX = segment.lineEndX + (cos >= 0 ? 4 : -4);
    segment.textY = segment.preferredY + 4;
    segment.textAnchor = cos >= 0 ? "start" : "end";
    offset += length;
    return segment;
  });

  const minGap = 18;
  const topLimit = 22;
  const bottomLimit = pieViewHeight - 20;
  const layoutSide = (isRight: boolean) => {
    const side = segments
      .filter((item) => item.isRight === isRight)
      .sort((a, b) => a.preferredY - b.preferredY);
    if (!side.length) {
      return;
    }
    let cursor = topLimit;
    for (const item of side) {
      item.lineEndY = Math.max(item.preferredY, cursor);
      cursor = item.lineEndY + minGap;
    }
    const overflow = cursor - minGap - bottomLimit;
    if (overflow > 0) {
      let backCursor = bottomLimit;
      for (let i = side.length - 1; i >= 0; i--) {
        const item = side[i];
        item.lineEndY = Math.min(item.lineEndY - overflow, backCursor);
        backCursor = item.lineEndY - minGap;
      }
    }
    for (const item of side) {
      item.lineMidY = item.lineEndY;
      item.textY = item.lineEndY + 4;
    }
  };
  layoutSide(true);
  layoutSide(false);
  return segments;
});

const currentYear = new Date().getFullYear();
const selectedYear = ref<number | undefined>(currentYear);
const yearOptions = computed(() => {
  const years = new Set<number>([currentYear]);
  for (let i = 1; i <= 5; i++) {
    years.add(currentYear - i);
  }
  return Array.from(years).sort((a, b) => b - a);
});

const handleContractTypeSegmentClick = async (segment: {
  code: string;
  name: string;
}) => {
  const contractType = String(segment.code || "").trim();
  if (!contractType) {
    return;
  }
  const query: Record<string, string> = {
    source: "dashboard-type-distribution",
    contractType,
    contractTypeName: String(segment.name || "").trim(),
  };
  if (selectedYear.value) {
    query.signingYears = String(selectedYear.value);
  }
  await router.push({
    path: "/contracts",
    query,
  });
};

const loadOverview = async () => {
  try {
    const data = await getContractOverview({
      year: selectedYear.value,
    });
    metrics.totalContracts = Number(data.totalContracts || 0);
    metrics.approvingContracts = Number(data.approvingContracts || 0);
    metrics.activeContracts = Number(data.activeContracts || 0);
    metrics.newThisMonth = Number(data.newThisMonth || 0);
    metrics.salesRevenue = Number(data.salesRevenue || 0);
    metrics.purchaseCost = Number(data.purchaseCost || 0);
    contractTypeStats.value = Array.isArray(data.contractTypeStats)
      ? data.contractTypeStats.map((item: ContractOverviewItem) => ({
          code: String(item.code || ""),
          name: String(item.name || item.code || "-"),
          count: Number(item.count || 0),
        }))
      : [];
    topCustomerRevenue.value = Array.isArray(data.topCustomerRevenue)
      ? data.topCustomerRevenue.map((item: TopCustomerRevenueItem) => ({
          rank: Number(item.rank || 0),
          customerName: String(item.customerName || "-"),
          revenue: Number(item.revenue || 0),
          contractCount: Number(item.contractCount || 0),
        }))
      : [];
    top5CustomerRevenueShare.value = Number(data.top5CustomerRevenueShare || 0);
  } catch (error) {
    console.error("加载仪表板数据失败:", error);
    ElMessage.error(extractErrorMessage(error, "加载仪表板数据失败"));
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

const formatPercent = (value: number) => {
  return `${(Number(value) || 0).toFixed(2)}%`;
};
</script>

<style lang="scss" scoped>
.dashboard-home {
  .mb-16 {
    margin-bottom: 16px;
  }
  .mt-16 {
    margin-top: 16px;
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

  .customer-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .customer-top5 {
    color: #409eff;
    font-weight: 600;
  }

  .type-pie-wrap {
    display: flex;
    justify-content: center;
  }

  .type-pie-chart {
    width: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .pie-svg {
    width: min(100%, 980px);
    height: auto;
    overflow: visible;
  }

  .pie-label-text {
    fill: #606266;
    font-size: 12px;
  }

  .pie-segment {
    cursor: pointer;
    transition: opacity 0.2s ease;

    &:hover {
      opacity: 0.82;
    }
  }

  .pie-label-group {
    cursor: pointer;
    outline: none;
  }
}
</style>
