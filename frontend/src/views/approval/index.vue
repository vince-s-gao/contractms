<template>
  <div class="approval-container">
    <!-- 审批任务筛选 -->
    <div class="filter-area">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-select
            v-model="filterParams.type"
            placeholder="审批类型"
            clearable
          >
            <el-option label="待我审批" value="pending" />
            <el-option label="我已审批" value="processed" />
            <el-option label="全部" value="all" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select
            v-model="filterParams.status"
            placeholder="审批状态"
            clearable
          >
            <el-option label="待审批" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
        </el-col>
        <el-col :span="12">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 审批任务列表 -->
    <div class="approval-list">
      <el-card
        v-for="task in approvalTasks"
        :key="task.id"
        class="approval-item"
        :class="{ 'pending-item': task.status === 'pending' }"
      >
        <div class="task-header">
          <div class="task-info">
            <h4 class="contract-name">{{ task.contractName }}</h4>
            <div class="task-meta">
              <span class="contract-number"
                >合同编号：{{ task.contractNumber }}</span
              >
              <span class="contract-type"
                >合同类型：{{ task.contractType }}</span
              >
              <span class="contract-amount"
                >金额：¥{{ formatAmount(task.amount) }}</span
              >
            </div>
          </div>
          <div class="task-status">
            <el-tag :type="getStatusType(task.status)" size="large">
              {{ getStatusText(task.status) }}
            </el-tag>
            <div class="task-time">{{ task.createdAt }}</div>
          </div>
        </div>

        <div class="task-content">
          <div class="applicant-info">
            <span>申请人：{{ task.applicant }}</span>
            <span>申请时间：{{ task.applyTime }}</span>
          </div>
          <div class="contract-desc">
            {{ task.description || "暂无描述" }}
          </div>
        </div>

        <div class="task-actions">
          <el-button type="primary" @click="handleViewContract(task)"
            >查看合同</el-button
          >
          <el-button
            v-if="task.status === 'pending'"
            type="success"
            @click="handleApprove(task)"
          >
            通过
          </el-button>
          <el-button
            v-if="task.status === 'pending'"
            type="danger"
            @click="handleReject(task)"
          >
            拒绝
          </el-button>
          <el-button
            v-if="task.status !== 'pending'"
            type="info"
            @click="handleViewApprovalDetail"
          >
            查看审批详情
          </el-button>
        </div>
      </el-card>

      <!-- 空状态 -->
      <div v-if="approvalTasks.length === 0" class="empty-state">
        <el-empty description="暂无审批任务" />
      </div>
    </div>

    <!-- 审批对话框 -->
    <ApprovalDialog
      v-model="approvalDialogVisible"
      :task="currentTask"
      :action="currentAction"
      @success="handleApprovalSuccess"
    />

    <!-- 合同详情对话框 -->
    <ContractDetailDialog
      v-model="contractDialogVisible"
      :contract-id="currentContractId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import ApprovalDialog from "./components/ApprovalDialog.vue";
import ContractDetailDialog from "../contracts/components/ContractDetailDialog.vue";

interface ApprovalTask {
  id: string;
  contractId: string;
  contractNumber: string;
  contractName: string;
  contractType: string;
  amount: number;
  status: string;
  applicant: string;
  applyTime: string;
  createdAt: string;
  description?: string;
}

interface FilterParams {
  type: string;
  status: string;
}

const loading = ref(false);
const router = useRouter();
const approvalTasks = ref<ApprovalTask[]>([]);
const filterParams = reactive<FilterParams>({
  type: "pending",
  status: "",
});

// 对话框控制
const approvalDialogVisible = ref(false);
const contractDialogVisible = ref(false);
const currentTask = ref<ApprovalTask | null>(null);
const currentAction = ref("");
const currentContractId = ref("");

// 模拟数据
const mockApprovalTasks: ApprovalTask[] = [
  {
    id: "1",
    contractId: "1",
    contractNumber: "HT20230001",
    contractName: "软件开发服务合同",
    contractType: "技术服务",
    amount: 500000,
    status: "pending",
    applicant: "张三",
    applyTime: "2023-01-01 10:00:00",
    createdAt: "2023-01-01 10:00:00",
    description:
      "为XX项目提供软件开发服务，包含需求分析、设计、开发、测试等全流程服务。",
  },
  {
    id: "2",
    contractId: "2",
    contractNumber: "HT20230002",
    contractName: "设备采购合同",
    contractType: "采购",
    amount: 200000,
    status: "approved",
    applicant: "李四",
    applyTime: "2023-02-01 14:30:00",
    createdAt: "2023-02-01 14:30:00",
    description: "采购服务器设备，用于公司业务系统部署。",
  },
];

onMounted(() => {
  loadApprovalTasks();
});

const loadApprovalTasks = async () => {
  loading.value = true;

  try {
    const token = localStorage.getItem("token");
    const userInfo = JSON.parse(localStorage.getItem("userInfo") || "{}");

    // 构建查询参数
    const params = new URLSearchParams();
    if (filterParams.type === "pending") {
      params.append("status", "PENDING");
    } else if (filterParams.type === "processed") {
      params.append("status", "APPROVED,REJECTED");
    }

    if (filterParams.status) {
      params.append("approvalStatus", filterParams.status.toUpperCase());
    }

    // 调用真实API获取审批任务
    const response = await fetch(
      `/api/contracts/approvals?${params.toString()}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
          "X-User-Id": userInfo.id || "1",
        },
      },
    );

    if (response.status === 401) {
      ElMessage.error("登录已过期，请重新登录");
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      router.push("/login");
      return;
    }

    if (!response.ok) {
      throw new Error("获取审批任务失败");
    }

    const data = await response.json();

    // 转换API数据格式
    approvalTasks.value = (data.records || data.data || []).map(
      (item: any) => ({
        id: item.id,
        contractId: item.contractId,
        contractNumber: item.contractNo,
        contractName: item.contractName,
        contractType: item.contractType,
        amount: item.amount,
        status: item.approvalStatus?.toLowerCase() || "pending",
        applicant: item.applicantName || "未知申请人",
        applyTime: item.createdAt,
        createdAt: item.createdAt,
        description: item.description || "无描述",
      }),
    );
  } catch (error) {
    console.error("加载审批任务失败:", error);
    ElMessage.error("加载审批任务失败");
    // 降级使用模拟数据
    let filteredTasks = mockApprovalTasks;

    if (filterParams.type === "pending") {
      filteredTasks = filteredTasks.filter((task) => task.status === "pending");
    } else if (filterParams.type === "processed") {
      filteredTasks = filteredTasks.filter((task) => task.status !== "pending");
    }

    if (filterParams.status) {
      filteredTasks = filteredTasks.filter(
        (task) => task.status === filterParams.status,
      );
    }

    approvalTasks.value = filteredTasks;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  loadApprovalTasks();
};

const handleReset = () => {
  filterParams.type = "pending";
  filterParams.status = "";
  loadApprovalTasks();
};

const handleViewContract = (task: ApprovalTask) => {
  currentContractId.value = task.contractId;
  contractDialogVisible.value = true;
};

const handleApprove = (task: ApprovalTask) => {
  currentTask.value = task;
  currentAction.value = "approve";
  approvalDialogVisible.value = true;
};

const handleReject = (task: ApprovalTask) => {
  currentTask.value = task;
  currentAction.value = "reject";
  approvalDialogVisible.value = true;
};

const handleViewApprovalDetail = () => {
  ElMessage.info("查看审批详情功能开发中");
};

const handleApprovalSuccess = () => {
  approvalDialogVisible.value = false;
  loadApprovalTasks();
};

const formatAmount = (amount: number) => {
  return amount.toLocaleString();
};

const getStatusType = (status: string) => {
  const typeMap: Record<string, string> = {
    pending: "warning",
    approved: "success",
    rejected: "danger",
  };
  return typeMap[status] || "info";
};

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: "待审批",
    approved: "已通过",
    rejected: "已拒绝",
  };
  return textMap[status] || status;
};
</script>

<style lang="scss" scoped>
.approval-container {
  padding: 20px;
  background: white;
  border-radius: 4px;
  min-height: calc(100vh - 140px);
}

.filter-area {
  margin-bottom: 20px;

  .el-col {
    margin-bottom: 10px;
  }
}

.approval-list {
  .approval-item {
    margin-bottom: 16px;
    transition: all 0.3s;

    &.pending-item {
      border-left: 4px solid #e6a23c;

      &:hover {
        box-shadow: 0 2px 12px 0 rgba(230, 162, 60, 0.1);
      }
    }

    .task-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;

      .task-info {
        flex: 1;

        .contract-name {
          margin: 0 0 8px 0;
          font-size: 16px;
          color: #303133;
        }

        .task-meta {
          display: flex;
          gap: 20px;
          font-size: 12px;
          color: #909399;

          span {
            display: inline-block;
          }
        }
      }

      .task-status {
        text-align: right;

        .task-time {
          margin-top: 8px;
          font-size: 12px;
          color: #909399;
        }
      }
    }

    .task-content {
      margin-bottom: 16px;

      .applicant-info {
        display: flex;
        gap: 20px;
        margin-bottom: 8px;
        font-size: 14px;
        color: #606266;
      }

      .contract-desc {
        line-height: 1.6;
        color: #606266;
        background: #f5f7fa;
        padding: 12px;
        border-radius: 4px;
      }
    }

    .task-actions {
      text-align: right;

      .el-button {
        margin-left: 8px;
      }
    }
  }

  .empty-state {
    text-align: center;
    padding: 60px 0;
  }
}
</style>
