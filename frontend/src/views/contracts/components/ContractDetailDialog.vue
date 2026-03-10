<template>
  <el-dialog
    v-model="visible"
    :title="contractDetail ? contractDetail.contractName : '合同详情'"
    width="80%"
    top="5vh"
    @close="handleClose"
  >
    <div v-loading="loading" class="contract-detail">
      <!-- 基本信息 -->
      <el-card class="detail-section" header="基本信息">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="合同编号">{{
            contractDetail?.contractNumber
          }}</el-descriptions-item>
          <el-descriptions-item label="合同名称">{{
            contractDetail?.contractName
          }}</el-descriptions-item>
          <el-descriptions-item label="合同类型">{{
            contractDetail?.contractType
          }}</el-descriptions-item>
          <el-descriptions-item label="合同金额"
            >¥{{
              formatAmount(contractDetail?.amount || 0)
            }}</el-descriptions-item
          >
          <el-descriptions-item label="开始日期">{{
            contractDetail?.startDate
          }}</el-descriptions-item>
          <el-descriptions-item label="结束日期">{{
            contractDetail?.endDate
          }}</el-descriptions-item>
          <el-descriptions-item label="合同状态">
            <el-tag :type="getStatusType(contractDetail?.status || '')">
              {{ getStatusText(contractDetail?.status || "") }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{
            contractDetail?.createdBy
          }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            contractDetail?.createdAt
          }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 合同内容 -->
      <el-card class="detail-section" header="合同内容">
        <div class="contract-content">
          {{ contractDetail?.content || "暂无合同内容" }}
        </div>
      </el-card>

      <!-- 参与人员 -->
      <el-card class="detail-section" header="参与人员">
        <el-table :data="participants" stripe>
          <el-table-column prop="name" label="姓名" />
          <el-table-column prop="role" label="角色" />
          <el-table-column prop="department" label="部门" />
          <el-table-column prop="phone" label="联系电话" />
        </el-table>
      </el-card>

      <!-- 附件 -->
      <el-card class="detail-section" header="附件">
        <div v-if="attachments.length === 0" class="no-attachments">
          暂无附件
        </div>
        <div v-else class="attachments-list">
          <div
            v-for="file in attachments"
            :key="file.id"
            class="attachment-item"
          >
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <el-button link type="primary" size="small">下载</el-button>
          </div>
        </div>
      </el-card>

      <!-- 审批记录 -->
      <el-card class="detail-section" header="审批记录">
        <el-timeline>
          <el-timeline-item
            v-for="record in approvalRecords"
            :key="record.id"
            :timestamp="record.createdAt"
            :type="getApprovalType(record.status)"
          >
            <p>{{ record.approver }} - {{ getApprovalText(record.status) }}</p>
            <p v-if="record.comment">审批意见：{{ record.comment }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="contractDetail?.status === 'draft'" type="primary"
        >提交审批</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

interface ContractDetail {
  id: string;
  contractNumber: string;
  contractName: string;
  contractType: string;
  amount: number;
  status: string;
  startDate: string;
  endDate: string;
  createdBy: string;
  createdAt: string;
  content?: string;
}

interface Participant {
  id: string;
  name: string;
  role: string;
  department: string;
  phone: string;
}

interface Attachment {
  id: string;
  name: string;
  size: string;
  uploadTime: string;
}

interface ApprovalRecord {
  id: string;
  approver: string;
  status: string;
  comment?: string;
  createdAt: string;
}

const props = defineProps<{
  modelValue: boolean;
  contractId: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  close: [];
}>();

const visible = ref(false);
const loading = ref(false);
const contractDetail = ref<ContractDetail | null>(null);
const participants = ref<Participant[]>([]);
const attachments = ref<Attachment[]>([]);
const approvalRecords = ref<ApprovalRecord[]>([]);

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val && props.contractId) {
      loadContractDetail();
    }
  },
);

watch(visible, (val) => {
  emit("update:modelValue", val);
  if (!val) {
    emit("close");
  }
});

const loadContractDetail = async () => {
  loading.value = true;

  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500));

    // 模拟数据
    contractDetail.value = {
      id: props.contractId,
      contractNumber: "HT20230001",
      contractName: "软件开发服务合同",
      contractType: "技术服务",
      amount: 500000,
      status: "active",
      startDate: "2023-01-01",
      endDate: "2023-12-31",
      createdBy: "张三",
      createdAt: "2023-01-01 10:00:00",
      content:
        "本合同由甲乙双方根据《中华人民共和国合同法》等相关法律法规，经友好协商一致达成。",
    };

    participants.value = [
      {
        id: "1",
        name: "张三",
        role: "甲方代表",
        department: "技术部",
        phone: "13800138000",
      },
      {
        id: "2",
        name: "李四",
        role: "乙方代表",
        department: "销售部",
        phone: "13900139000",
      },
    ];

    attachments.value = [
      {
        id: "1",
        name: "合同附件1.pdf",
        size: "2.5MB",
        uploadTime: "2023-01-01 10:00:00",
      },
    ];

    approvalRecords.value = [
      {
        id: "1",
        approver: "王五",
        status: "approved",
        comment: "合同内容完整，同意",
        createdAt: "2023-01-02 09:00:00",
      },
      {
        id: "2",
        approver: "赵六",
        status: "approved",
        comment: "金额合理，同意",
        createdAt: "2023-01-02 14:00:00",
      },
    ];
  } catch (error) {
    console.error("加载合同详情失败", error);
  } finally {
    loading.value = false;
  }
};

const handleClose = () => {
  visible.value = false;
  contractDetail.value = null;
  participants.value = [];
  attachments.value = [];
  approvalRecords.value = [];
};

const formatAmount = (amount: number) => {
  return amount.toLocaleString();
};

const getStatusType = (status: string) => {
  const typeMap: Record<string, string> = {
    draft: "info",
    approving: "warning",
    active: "success",
    terminated: "danger",
  };
  return typeMap[status] || "info";
};

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    draft: "草稿",
    approving: "审批中",
    active: "已生效",
    terminated: "已终止",
  };
  return textMap[status] || status;
};

const getApprovalType = (status: string) => {
  const typeMap: Record<string, string> = {
    approved: "success",
    rejected: "danger",
    pending: "primary",
  };
  return typeMap[status] || "info";
};

const getApprovalText = (status: string) => {
  const textMap: Record<string, string> = {
    approved: "已通过",
    rejected: "已拒绝",
    pending: "待审批",
  };
  return textMap[status] || status;
};
</script>

<style lang="scss" scoped>
.contract-detail {
  .detail-section {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .contract-content {
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .no-attachments {
    color: #999;
    text-align: center;
    padding: 20px;
  }

  .attachments-list {
    .attachment-item {
      display: flex;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid #f0f0f0;

      .el-icon {
        margin-right: 8px;
        color: #409eff;
      }

      .file-name {
        flex: 1;
        margin-right: 10px;
      }
    }
  }
}
</style>
