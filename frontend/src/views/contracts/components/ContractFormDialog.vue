<template>
  <el-dialog
    v-model="visible"
    :title="formTitle"
    width="60%"
    top="5vh"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      class="contract-form"
    >
      <!-- 基本信息 -->
      <el-card header="基本信息">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="合同编号" prop="contractNumber">
              <el-input
                v-model="formData.contractNumber"
                placeholder="请输入合同编号"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约年份">
              <el-input
                :model-value="signingYearPreview"
                disabled
                placeholder="将从合同编号自动提取"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="合同名称" prop="contractName">
              <el-input
                v-model="formData.contractName"
                placeholder="请输入合同名称"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="合同类型" prop="contractType">
              <el-select
                v-model="formData.contractType"
                placeholder="请选择合同类型"
                style="width: 100%"
              >
                <el-option
                  v-for="item in contractTypeOptions"
                  :key="item.code"
                  :label="item.name"
                  :value="item.code"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同金额(含税)" prop="amount">
              <el-input-number
                v-model="formData.amount"
                :min="0"
                :precision="2"
                style="width: 100%"
                placeholder="请输入合同金额（含税，选填）"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="税率(%)" prop="taxRate">
              <el-input-number
                v-model="formData.taxRate"
                :min="0"
                :max="100"
                :precision="2"
                :step="0.1"
                style="width: 100%"
                placeholder="请输入税率"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税额">
              <el-input :model-value="formattedTaxAmount" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="未税金额">
              <el-input :model-value="formattedAmountWithoutTax" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input
                v-model="formData.customerName"
                placeholder="请输入客户名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司签约主体" prop="companySignatory">
              <el-input
                v-model="formData.companySignatory"
                placeholder="请输入公司签约主体"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="签署日期" prop="startDate">
              <el-date-picker
                v-model="formData.startDate"
                type="date"
                placeholder="选择签署日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="formData.endDate"
                type="date"
                placeholder="选择结束日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <!-- 合同内容 -->
      <el-card header="合同内容" class="mt-20">
        <el-form-item label="合同内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="6"
            placeholder="请输入合同主要内容"
          />
        </el-form-item>
      </el-card>

      <!-- 参与人员 -->
      <el-card header="参与人员" class="mt-20">
        <div class="participants-section">
          <div class="participants-header">
            <span>参与人员列表</span>
            <el-button type="primary" size="small" @click="addParticipant">
              <el-icon><Plus /></el-icon>
              添加人员
            </el-button>
          </div>

          <div
            v-for="(participant, index) in formData.participants"
            :key="index"
            class="participant-item"
          >
            <el-row :gutter="10">
              <el-col :span="5">
                <el-input v-model="participant.name" placeholder="姓名" />
              </el-col>
              <el-col :span="5">
                <el-input v-model="participant.role" placeholder="角色" />
              </el-col>
              <el-col :span="5">
                <el-input v-model="participant.department" placeholder="部门" />
              </el-col>
              <el-col :span="5">
                <el-input v-model="participant.phone" placeholder="联系电话" />
              </el-col>
              <el-col :span="4">
                <el-button
                  type="danger"
                  size="small"
                  @click="removeParticipant(index)"
                >
                  删除
                </el-button>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-card>

      <!-- 附件上传 -->
      <el-card header="附件" class="mt-20">
        <div
          v-if="isEdit && existingAttachments.length > 0"
          class="existing-attachments"
        >
          <div class="attachments-title">已上传附件</div>
          <div
            v-for="file in existingAttachments"
            :key="file.id"
            class="attachment-row"
          >
            <span class="attachment-name">{{ file.name }}</span>
            <span class="attachment-meta">{{ formatFileSize(file.size) }}</span>
            <el-button
              link
              type="danger"
              :loading="deletingAttachmentId === file.id"
              @click="handleDeleteExistingAttachment(file.id)"
            >
              删除
            </el-button>
          </div>
        </div>
        <el-upload
          class="upload-demo"
          action="#"
          :file-list="fileList"
          multiple
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            选择文件
          </el-button>
          <template #tip>
            <div class="el-upload__tip">
              支持上传PDF、Word、Excel文件，单个文件不超过100MB
            </div>
          </template>
        </el-upload>
      </el-card>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isEdit ? "更新" : "创建" }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from "vue";
import {
  ElMessageBox,
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadFile,
  type UploadFiles,
  type UploadRawFile,
} from "element-plus";
import {
  createContract,
  deleteContractAttachment,
  getContractById,
  getContractAttachments,
  updateContract,
  getContractTypes,
  uploadContractAttachments,
  type ContractAttachment,
  type ContractUpsertPayload,
  type ContractTypeItem,
} from "@/api/contract";
import { DEFAULT_CONTRACT_TYPE_LIST } from "@/constants/contract";
import { useUserStore } from "@/stores/user";
import { extractErrorMessage } from "@/utils/error";

interface ContractFormData {
  contractNumber: string;
  contractName: string;
  contractType: string;
  amount: number | undefined;
  taxRate: number | undefined;
  customerName: string;
  companySignatory: string;
  startDate: string;
  endDate: string;
  content: string;
  participants: Participant[];
}

interface Participant {
  name: string;
  role: string;
  department: string;
  phone: string;
}

interface ContractFormSourceData {
  id?: string | number;
  contractNumber?: string;
  contractNo?: string;
  contractName?: string;
  contractType?: string;
  amount?: number;
  taxRate?: number;
  customerName?: string;
  companySignatory?: string;
  partyA?: string;
  partyB?: string;
  startDate?: string;
  endDate?: string;
  content?: string;
  description?: string;
  participants?: Participant[];
}

const props = defineProps<{
  modelValue: boolean;
  contractData?: ContractFormSourceData;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  success: [];
}>();

const userStore = useUserStore();
const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();
const fileList = ref<UploadFile[]>([]);
const existingAttachments = ref<ContractAttachment[]>([]);
const deletingAttachmentId = ref<number | null>(null);
const contractTypeOptions = ref<ContractTypeItem[]>([
  ...DEFAULT_CONTRACT_TYPE_LIST,
]);

const isEdit = computed(() => !!props.contractData);
const formTitle = computed(() => (isEdit.value ? "编辑合同" : "新建合同"));
const signingYearPreview = computed(() => {
  const matched = (formData.contractNumber || "").match(/20\d{2}/);
  return matched ? matched[0] : "";
});
const taxAmount = computed(() => {
  const amountWithTax = Number(formData.amount || 0);
  const rate = Number(formData.taxRate || 0);
  if (!rate) {
    return 0;
  }
  return (amountWithTax * rate) / (100 + rate);
});
const amountWithoutTax = computed(() => {
  const amountWithTax = Number(formData.amount || 0);
  return amountWithTax - taxAmount.value;
});
const formatCurrency = (value: number) =>
  `¥${Number(value || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
const formattedTaxAmount = computed(() => formatCurrency(taxAmount.value));
const formattedAmountWithoutTax = computed(() =>
  formatCurrency(amountWithoutTax.value),
);

const formData = reactive<ContractFormData>({
  contractNumber: "",
  contractName: "",
  contractType: "",
  amount: undefined,
  taxRate: 0,
  customerName: "",
  companySignatory: "",
  startDate: "",
  endDate: "",
  content: "",
  participants: [],
});

const formRules: FormRules = {
  contractNumber: [
    { required: true, message: "请输入合同编号", trigger: "blur" },
  ],
  contractName: [
    { required: true, message: "请输入合同名称", trigger: "blur" },
  ],
  contractType: [
    { required: true, message: "请选择合同类型", trigger: "change" },
  ],
  startDate: [{ required: true, message: "请选择签署日期", trigger: "change" }],
  endDate: [{ required: true, message: "请选择结束日期", trigger: "change" }],
};

onMounted(() => {
  loadContractTypeOptions();
});

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      loadContractTypeOptions();
      resetForm();
      if (props.contractData) {
        void loadContractData();
      }
    }
  },
);

watch(visible, (val) => {
  emit("update:modelValue", val);
});

const loadContractTypeOptions = async () => {
  try {
    const response = await getContractTypes();
    const list = response.records || response.data?.records || [];
    if (Array.isArray(list) && list.length > 0) {
      contractTypeOptions.value = list;
    }
  } catch (error) {
    console.error("加载合同类型失败:", error);
  }
};

const applyContractData = (source: ContractFormSourceData) => {
  const participants = Array.isArray(source.participants)
    ? source.participants.map((item) => ({
        name: item.name || "",
        role: item.role || "",
        department: item.department || "",
        phone: item.phone || "",
      }))
    : [];

  Object.assign(formData, {
    ...source,
    contractNumber: source.contractNumber || source.contractNo || "",
    taxRate:
      source.taxRate === undefined || source.taxRate === null
        ? 0
        : Number(source.taxRate),
    customerName: source.customerName || source.partyA || "",
    companySignatory: source.companySignatory || source.partyB || "",
    participants,
  });
  if (
    formData.contractType &&
    !contractTypeOptions.value.some(
      (item) => item.code === formData.contractType,
    )
  ) {
    contractTypeOptions.value.push({
      code: formData.contractType,
      name: formData.contractType,
    });
  }
};

const loadContractData = async () => {
  if (!props.contractData) return;

  applyContractData(props.contractData);

  const contractId = props.contractData.id;
  if (contractId !== undefined && contractId !== null) {
    try {
      const detail = (await getContractById(
        String(contractId),
      )) as ContractFormSourceData;
      applyContractData({
        ...props.contractData,
        ...detail,
      });
    } catch (error) {
      console.error("加载合同详情失败:", error);
    }
  }
  loadExistingAttachments();
};

const loadExistingAttachments = async () => {
  const contractId = props.contractData?.id;
  if (!contractId) {
    existingAttachments.value = [];
    return;
  }
  try {
    const response = await getContractAttachments(contractId);
    const records = response.records || response.data?.records || [];
    existingAttachments.value = Array.isArray(records) ? records : [];
  } catch (error) {
    console.error("加载附件失败:", error);
    existingAttachments.value = [];
  }
};

const resetForm = () => {
  formRef.value?.resetFields();
  Object.assign(formData, {
    contractNumber: "",
    contractName: "",
    contractType: "",
    amount: undefined,
    taxRate: 0,
    customerName: "",
    companySignatory: "",
    startDate: "",
    endDate: "",
    content: "",
    participants: [],
  });
  fileList.value = [];
  existingAttachments.value = [];
  deletingAttachmentId.value = null;
};

const addParticipant = () => {
  formData.participants.push({
    name: "",
    role: "",
    department: "",
    phone: "",
  });
};

const removeParticipant = (index: number) => {
  formData.participants.splice(index, 1);
};

const handleFileChange = (_file: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = [...uploadFiles];
};

const handleFileRemove = (file: UploadFile) => {
  const index = fileList.value.findIndex((f) => f.uid === file.uid);
  if (index > -1) {
    fileList.value.splice(index, 1);
  }
};

const handleClose = () => {
  visible.value = false;
};

const formatFileSize = (size: number) => {
  const bytes = Number(size || 0);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
};

const handleDeleteExistingAttachment = async (attachmentId: number) => {
  const contractId = props.contractData?.id;
  if (!contractId) {
    return;
  }
  try {
    await ElMessageBox.confirm("确定删除该附件吗？", "提示", {
      type: "warning",
      confirmButtonText: "确定",
      cancelButtonText: "取消",
    });
    deletingAttachmentId.value = attachmentId;
    await deleteContractAttachment(contractId, attachmentId);
    ElMessage.success("附件删除成功");
    await loadExistingAttachments();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }
    console.error("删除附件失败:", error);
    ElMessage.error(extractErrorMessage(error, "删除附件失败"));
  } finally {
    deletingAttachmentId.value = null;
  }
};

const handleSubmit = async () => {
  if (!formRef.value) return;

  const valid = await formRef.value.validate();
  if (!valid) return;

  loading.value = true;

  try {
    const submitData: ContractUpsertPayload = {
      contractNo: formData.contractNumber,
      contractName: formData.contractName,
      contractType: formData.contractType,
      amount: formData.amount,
      taxRate: formData.taxRate,
      startDate: formData.startDate,
      endDate: formData.endDate,
      description: formData.content,
      customerName: formData.customerName || "",
      companySignatory: formData.companySignatory || "",
      partyName: formData.customerName || "",
      partyContact: formData.companySignatory || "",
      partyPhone:
        formData.participants.find((p) => p.role === "对方单位")?.phone || "",
      createdBy: userStore.userInfo?.id,
      participants: formData.participants.map((participant) => ({
        name: participant.name || "",
        role: participant.role || "",
        department: participant.department || "",
        phone: participant.phone || "",
      })),
    };

    let savedData: { id?: string | number; contractId?: string | number } = {};
    if (isEdit.value && props.contractData?.id !== undefined) {
      await updateContract(String(props.contractData.id), submitData);
      savedData = { id: props.contractData.id };
    } else {
      const created = await createContract(submitData);
      savedData = {
        id: created?.id,
        contractId: created?.contractId,
      };
    }
    const savedContractId = String(
      savedData?.id || savedData?.contractId || props.contractData?.id || "",
    );
    const rawFiles = fileList.value
      .map((item) => item.raw as UploadRawFile | undefined)
      .filter((item): item is UploadRawFile => !!item);
    if (savedContractId && rawFiles.length > 0) {
      const attachmentFormData = new FormData();
      rawFiles.forEach((rawFile) => {
        attachmentFormData.append("files", rawFile);
      });
      try {
        await uploadContractAttachments(savedContractId, attachmentFormData);
      } catch (uploadError) {
        console.error("附件上传失败:", uploadError);
        ElMessage.warning("合同已保存，但附件上传失败，请在详情页重试上传");
      }
    }

    ElMessage.success(isEdit.value ? "合同更新成功" : "合同创建成功");
    emit("success");
    handleClose();
  } catch (error) {
    console.error("合同操作失败:", error);
    ElMessage.error(
      extractErrorMessage(
        error,
        isEdit.value ? "合同更新失败" : "合同创建失败",
      ),
    );
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss" scoped>
.contract-form {
  .mt-20 {
    margin-top: 20px;
  }

  .participants-section {
    .participants-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      font-weight: bold;
    }

    .participant-item {
      margin-bottom: 10px;
      padding: 10px;
      border: 1px solid #e0e0e0;
      border-radius: 4px;

      &:last-child {
        margin-bottom: 0;
      }
    }
  }

  .existing-attachments {
    margin-bottom: 12px;

    .attachments-title {
      margin-bottom: 8px;
      color: #606266;
      font-size: 13px;
    }

    .attachment-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 0;
      border-bottom: 1px solid #f0f2f5;
    }

    .attachment-name {
      flex: 1;
      color: #303133;
      word-break: break-all;
    }

    .attachment-meta {
      color: #909399;
      font-size: 12px;
      min-width: 80px;
      text-align: right;
    }
  }
}
</style>
