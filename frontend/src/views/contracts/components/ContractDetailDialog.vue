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
          <el-descriptions-item label="签约年份">{{
            contractDetail?.signingYear || "-"
          }}</el-descriptions-item>
          <el-descriptions-item label="合同名称">{{
            contractDetail?.contractName
          }}</el-descriptions-item>
          <el-descriptions-item label="合同类型">{{
            contractDetail?.contractType
          }}</el-descriptions-item>
          <el-descriptions-item label="合同金额(含税)"
            >¥{{
              formatAmount(contractDetail?.amount || 0)
            }}</el-descriptions-item
          >
          <el-descriptions-item label="税率(%)">{{
            contractDetail?.taxRate ?? 0
          }}</el-descriptions-item>
          <el-descriptions-item label="税额"
            >¥{{
              formatAmount(contractDetail?.taxAmount || 0)
            }}</el-descriptions-item
          >
          <el-descriptions-item label="未税金额"
            >¥{{
              formatAmount(contractDetail?.amountWithoutTax || 0)
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
            <span class="file-meta"
              >{{ formatFileSize(file.size) }} | {{ file.uploadTime }}</span
            >
            <el-button link type="primary" size="small" @click="handleDownload(file)"
              >下载</el-button
            >
          </div>
        </div>
      </el-card>

      <!-- 审批记录 -->
      <el-card class="detail-section" header="审批记录">
        <div v-if="approvalRecords.length === 0" class="no-approval-records">
          暂无审批记录
        </div>
        <el-timeline v-else>
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
      <el-button
        v-if="contractDetail?.status === 'draft'"
        type="primary"
        :loading="submittingApproval"
        @click="handleSubmitApproval"
      >
        提交审批
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  downloadContractAttachment,
  getContractAttachments,
  getContractApprovalRecords,
  getContractById,
  submitForApproval,
  type ContractApprovalRecord,
  type ContractAttachment,
} from "@/api/contract";
import { extractErrorMessage } from "@/utils/error";

interface ContractDetail {
  id: string;
  contractNumber: string;
  signingYear?: number;
  contractName: string;
  contractType: string;
  amount: number;
  taxRate?: number;
  taxAmount?: number;
  amountWithoutTax?: number;
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

interface Attachment extends ContractAttachment {
  id: number;
  name: string;
  size: number;
  uploadTime: string;
}

interface ApprovalRecord {
  id: string | number;
  approver: string;
  status: string;
  comment?: string;
  createdAt: string;
}

interface ContractDetailResponse {
  id?: string | number;
  contractNumber?: string;
  contractNo?: string;
  signingYear?: number | string;
  contractName?: string;
  contractType?: string;
  amount?: number;
  taxRate?: number;
  taxAmount?: number;
  amountWithoutTax?: number;
  status?: string;
  startDate?: string;
  endDate?: string;
  createdBy?: string | number;
  createdAt?: string;
  content?: string;
  description?: string;
}

interface AttachmentRecord {
  id?: string | number;
  name?: string;
  size?: number;
  fileType?: string;
  uploadTime?: string;
}

const props = defineProps<{
  modelValue: boolean;
  contractId: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  close: [];
  submitted: [];
}>();

const visible = ref(false);
const loading = ref(false);
const submittingApproval = ref(false);
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
    const data = (await getContractById(props.contractId)) as ContractDetailResponse;
    contractDetail.value = {
      id: String(data.id ?? props.contractId),
      contractNumber: data.contractNumber || data.contractNo || "",
      signingYear: data.signingYear ? Number(data.signingYear) : undefined,
      contractName: data.contractName || "",
      contractType: data.contractType || "",
      amount: Number(data.amount || 0),
      taxRate: Number(data.taxRate || 0),
      taxAmount: Number(data.taxAmount || 0),
      amountWithoutTax: Number(data.amountWithoutTax || 0),
      status: data.status || "draft",
      startDate: data.startDate || "",
      endDate: data.endDate || "",
      createdBy: String(data.createdBy ?? ""),
      createdAt: data.createdAt || "",
      content: data.content || data.description || "",
    };

    participants.value = [];
    await loadAttachments(contractDetail.value.id);
    await loadApprovalRecords(contractDetail.value.id);
  } catch (error) {
    console.error("加载合同详情失败", error);
    ElMessage.error(extractErrorMessage(error, "加载合同详情失败"));
    contractDetail.value = null;
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
  return Number(amount || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const formatFileSize = (size: number) => {
  const bytes = Number(size || 0);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
};

const loadAttachments = async (contractId: string) => {
  try {
    const response = await getContractAttachments(contractId);
    const records = response.records || response.data?.records || [];
    attachments.value = Array.isArray(records)
      ? records.map((item: AttachmentRecord) => ({
          id: Number(item.id || 0),
          name: String(item.name || ""),
          size: Number(item.size || 0),
          fileType: item.fileType || "",
          uploadTime: String(item.uploadTime || ""),
        }))
      : [];
  } catch (error) {
    console.error("加载附件失败", error);
    attachments.value = [];
  }
};

const loadApprovalRecords = async (contractId: string) => {
  try {
    const response = await getContractApprovalRecords(contractId);
    const records = response.records || response.data?.records || [];
    approvalRecords.value = Array.isArray(records)
      ? records.map((item: ContractApprovalRecord) => ({
          id: item.id || "",
          approver: String(item.approver || "系统"),
          status: String(item.status || "pending").toLowerCase(),
          comment: String(item.comment || ""),
          createdAt: String(item.createdAt || ""),
        }))
      : [];
  } catch (error) {
    console.error("加载审批记录失败", error);
    approvalRecords.value = [];
  }
};

const handleDownload = async (file: Attachment) => {
  if (!contractDetail.value?.id) {
    return;
  }
  try {
    const response = await downloadContractAttachment(contractDetail.value.id, file.id);
    const blob = new Blob([response.data], {
      type: response.headers["content-type"] || "application/octet-stream",
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = file.name || `attachment-${file.id}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error("下载附件失败", error);
    ElMessage.error(extractErrorMessage(error, "下载附件失败"));
  }
};

const handleSubmitApproval = async () => {
  if (!contractDetail.value?.id || submittingApproval.value) {
    return;
  }
  submittingApproval.value = true;
  try {
    await submitForApproval(String(contractDetail.value.id));
    ElMessage.success("提交审批成功");
    await loadContractDetail();
    emit("submitted");
  } catch (error) {
    console.error("提交审批失败", error);
    ElMessage.error(extractErrorMessage(error, "提交审批失败"));
  } finally {
    submittingApproval.value = false;
  }
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

  .no-approval-records {
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
