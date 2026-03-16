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

    <div class="history-area">
      <el-card header="审批记录">
        <el-table :data="approvalRecords" stripe>
          <el-table-column prop="approvalTime" label="时间" width="180" />
          <el-table-column prop="contractNo" label="合同编号" width="160" />
          <el-table-column
            prop="contractName"
            label="合同名称"
            min-width="240"
          />
          <el-table-column prop="approver" label="审批人" width="120" />
          <el-table-column label="审批结果" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="comment"
            label="审批意见"
            min-width="220"
            show-overflow-tooltip
          />
        </el-table>
      </el-card>
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
                >合同类型：{{ getContractTypeLabel(task.contractType) }}</span
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
            v-if="task.status === 'pending' && canProcessApproval"
            type="success"
            @click="handleApprove(task)"
          >
            通过
          </el-button>
          <el-button
            v-if="task.status === 'pending' && canProcessApproval"
            type="danger"
            @click="handleReject(task)"
          >
            拒绝
          </el-button>
          <el-button
            v-if="task.status !== 'pending'"
            type="info"
            @click="handleViewApprovalDetail(task)"
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

    <el-dialog
      v-model="approvalDetailDialogVisible"
      title="审批详情"
      width="760px"
    >
      <div v-loading="approvalDetailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="合同编号">
            {{ approvalDetailTask?.contractNumber || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusType(approvalDetailTask?.status || '')">
              {{ getStatusText(approvalDetailTask?.status || "") }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合同名称">
            {{ approvalDetailTask?.contractName || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="合同类型">
            {{ getContractTypeLabel(approvalDetailTask?.contractType) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="approval-detail-timeline">
          <el-timeline v-if="approvalDetailRecords.length > 0">
            <el-timeline-item
              v-for="record in approvalDetailRecords"
              :key="record.id"
              :timestamp="record.createdAt"
              :type="getStatusType(record.status)"
            >
              <p>{{ record.approver }} - {{ getStatusText(record.status) }}</p>
              <p v-if="record.comment">审批意见：{{ record.comment }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批记录" />
        </div>
      </div>
      <template #footer>
        <el-button @click="approvalDetailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import ApprovalDialog from "./components/ApprovalDialog.vue";
import ContractDetailDialog from "../contracts/components/ContractDetailDialog.vue";
import {
  getContractTypes,
  getApprovalRecords,
  getApprovalTasks,
  getContractApprovalRecords,
  type ApprovalRecordItem,
  type ApprovalTaskItem,
  type ContractApprovalRecord,
  type ContractTypeItem,
} from "@/api/contract";
import { DEFAULT_CONTRACT_TYPE_LIST } from "@/constants/contract";
import { useUserStore } from "@/stores/user";
import { extractErrorMessage } from "@/utils/error";

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

interface ApprovalRecordView {
  id: string;
  contractId: string;
  contractNo: string;
  contractName: string;
  approver: string;
  status: string;
  comment: string;
  approvalTime: string;
}

const loading = ref(false);
const approvalTasks = ref<ApprovalTask[]>([]);
const approvalRecords = ref<ApprovalRecordView[]>([]);
const filterParams = reactive<FilterParams>({
  type: "all",
  status: "",
});

// 对话框控制
const approvalDialogVisible = ref(false);
const contractDialogVisible = ref(false);
const currentTask = ref<ApprovalTask | null>(null);
const currentAction = ref("");
const currentContractId = ref("");

const approvalDetailDialogVisible = ref(false);
const approvalDetailLoading = ref(false);
const approvalDetailTask = ref<ApprovalTask | null>(null);
const approvalDetailRecords = ref<ContractApprovalRecord[]>([]);
const userStore = useUserStore();
const contractTypeList = ref<ContractTypeItem[]>([
  ...DEFAULT_CONTRACT_TYPE_LIST,
]);
const canProcessApproval = computed(() =>
  userStore.hasAnyPermission([
    "APPROVAL_PROCESS",
    "CONTRACT_APPROVE",
    "contract:approval",
  ]),
);

onMounted(() => {
  loadContractTypeList();
  loadApprovalTasks();
  loadApprovalRecords();
});

const loadContractTypeList = async () => {
  try {
    const response = await getContractTypes();
    const records = response.records || response.data?.records || [];
    if (Array.isArray(records) && records.length > 0) {
      contractTypeList.value = records;
      return;
    }
  } catch (error) {
    console.error("加载合同类型失败:", error);
  }
  if (!contractTypeList.value.length) {
    contractTypeList.value = [...DEFAULT_CONTRACT_TYPE_LIST];
  }
};

const loadApprovalTasks = async () => {
  loading.value = true;

  try {
    const params: { status?: string; approvalStatus?: string } = {};
    if (filterParams.type === "pending") {
      params.status = "PENDING";
    } else if (filterParams.type === "processed") {
      params.status = "APPROVED,REJECTED";
    }

    if (filterParams.status) {
      params.approvalStatus = filterParams.status.toUpperCase();
    }

    const data = await getApprovalTasks(params);
    const records = (data.records || data.data || []) as ApprovalTaskItem[];

    // 转换API数据格式
    approvalTasks.value = records.map((item) => ({
      id: String(item.id || ""),
      contractId: String(item.contractId || ""),
      contractNumber: item.contractNo || "",
      contractName: item.contractName || "",
      contractType: item.contractType || "",
      amount: Number(item.amount || 0),
      status: String(
        item.status || item.approvalStatus || "pending",
      ).toLowerCase(),
      applicant: item.applicantName || "未知申请人",
      applyTime: item.createdAt || "",
      createdAt: item.createdAt || "",
      description: item.description || "无描述",
    }));
  } catch (error) {
    console.error("加载审批任务失败:", error);
    ElMessage.error(extractErrorMessage(error, "加载审批任务失败"));
    approvalTasks.value = [];
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  loadApprovalTasks();
  loadApprovalRecords();
};

const handleReset = () => {
  filterParams.type = "all";
  filterParams.status = "";
  loadApprovalTasks();
  loadApprovalRecords();
};

const loadApprovalRecords = async () => {
  try {
    const params: { status?: string; page: number; size: number } = {
      page: 1,
      size: 50,
    };
    if (filterParams.status) {
      params.status = filterParams.status.toLowerCase();
    }
    const data = await getApprovalRecords(params);
    const records = (data.records || data.data || []) as ApprovalRecordItem[];
    approvalRecords.value = records.map((item) => ({
      id: String(item.id || ""),
      contractId: String(item.contractId || ""),
      contractNo: item.contractNo || "",
      contractName: item.contractName || "",
      approver: item.approver || "系统",
      status: String(item.status || "pending").toLowerCase(),
      comment: item.comment || "",
      approvalTime: item.approvalTime || "",
    }));
  } catch (error) {
    console.error("加载审批记录失败:", error);
    ElMessage.error(extractErrorMessage(error, "加载审批记录失败"));
    approvalRecords.value = [];
  }
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

const handleViewApprovalDetail = async (task: ApprovalTask) => {
  approvalDetailTask.value = task;
  approvalDetailDialogVisible.value = true;
  approvalDetailLoading.value = true;
  try {
    const data = await getContractApprovalRecords(task.contractId);
    const records = (data.records ||
      data.data?.records ||
      []) as ContractApprovalRecord[];
    approvalDetailRecords.value = records.map((item) => ({
      id: item.id,
      approver: item.approver || "系统",
      status: String(item.status || "pending").toLowerCase(),
      comment: item.comment || "",
      createdAt: item.createdAt || "",
    }));
  } catch (error) {
    console.error("加载审批详情失败:", error);
    ElMessage.error(extractErrorMessage(error, "加载审批详情失败"));
    approvalDetailRecords.value = [];
  } finally {
    approvalDetailLoading.value = false;
  }
};

const handleApprovalSuccess = (payload: {
  contractId: string;
  approved: boolean;
}) => {
  approvalDialogVisible.value = false;
  const nextStatus = payload.approved ? "approved" : "rejected";
  if (filterParams.type === "pending") {
    approvalTasks.value = approvalTasks.value.filter(
      (task) =>
        task.contractId !== payload.contractId &&
        task.id !== payload.contractId,
    );
  } else {
    approvalTasks.value = approvalTasks.value.map((task) => {
      if (
        task.contractId === payload.contractId ||
        task.id === payload.contractId
      ) {
        return { ...task, status: nextStatus };
      }
      return task;
    });
  }
  loadApprovalTasks();
  loadApprovalRecords();
};

const formatAmount = (amount: number) => {
  return Number(amount || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
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

const getContractTypeLabel = (code?: string) => {
  const typeCode = String(code || "").trim();
  if (!typeCode) {
    return "-";
  }
  const matched = contractTypeList.value.find((item) => item.code === typeCode);
  return matched?.name || typeCode;
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

.history-area {
  margin-bottom: 20px;
}

.approval-detail-timeline {
  margin-top: 16px;
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
