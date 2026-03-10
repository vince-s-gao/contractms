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
              <el-input :model-value="signingYearPreview" disabled placeholder="将从合同编号自动提取" />
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
            <el-form-item label="合同金额" prop="amount">
              <el-input-number
                v-model="formData.amount"
                :min="0"
                :precision="2"
                style="width: 100%"
                placeholder="请输入合同金额（选填）"
              />
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
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="formData.startDate"
                type="date"
                placeholder="选择开始日期"
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
        <el-upload
          class="upload-demo"
          action="#"
          :file-list="fileList"
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
              支持上传PDF、Word、Excel文件，单个文件不超过10MB
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
import { useRouter } from "vue-router";
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadFile,
} from "element-plus";
import { getContractTypes, type ContractTypeItem } from "@/api/contract";

interface ContractFormData {
  contractNumber: string;
  contractName: string;
  contractType: string;
  amount: number | undefined;
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

const props = defineProps<{
  modelValue: boolean;
  contractData?: any;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  success: [];
}>();

const router = useRouter();
const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();
const fileList = ref<UploadFile[]>([]);
const contractTypeOptions = ref<ContractTypeItem[]>([
  { code: "SALES", name: "销售合同" },
  { code: "PURCHASE", name: "采购合同" },
  { code: "SERVICE", name: "服务合同" },
  { code: "OTHER", name: "其他" },
]);

const isEdit = computed(() => !!props.contractData);
const formTitle = computed(() => (isEdit.value ? "编辑合同" : "新建合同"));
const signingYearPreview = computed(() => {
  const matched = (formData.contractNumber || "").match(/20\d{2}/);
  return matched ? matched[0] : "";
});

const formData = reactive<ContractFormData>({
  contractNumber: "",
  contractName: "",
  contractType: "",
  amount: undefined,
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
  startDate: [{ required: true, message: "请选择开始日期", trigger: "change" }],
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
        loadContractData();
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

const loadContractData = () => {
  if (!props.contractData) return;

  Object.assign(formData, {
    ...props.contractData,
    customerName:
      props.contractData.customerName || props.contractData.partyA || "",
    companySignatory:
      props.contractData.companySignatory || props.contractData.partyB || "",
  });
  if (
    formData.contractType &&
    !contractTypeOptions.value.some((item) => item.code === formData.contractType)
  ) {
    contractTypeOptions.value.push({
      code: formData.contractType,
      name: formData.contractType,
    });
  }
};

const resetForm = () => {
  formRef.value?.resetFields();
  Object.assign(formData, {
    contractNumber: "",
    contractName: "",
    contractType: "",
    amount: undefined,
    customerName: "",
    companySignatory: "",
    startDate: "",
    endDate: "",
    content: "",
    participants: [],
  });
  fileList.value = [];
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

const handleFileChange = (file: UploadFile) => {
  fileList.value.push(file);
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

const handleSubmit = async () => {
  if (!formRef.value) return;

  const valid = await formRef.value.validate();
  if (!valid) return;

  loading.value = true;

  try {
    const token = localStorage.getItem("token");
    const userInfo = JSON.parse(localStorage.getItem("userInfo") || "{}");

    // 准备提交数据
    const submitData = {
      contractNo: formData.contractNumber,
      contractName: formData.contractName,
      contractType: formData.contractType,
      amount: formData.amount,
      startDate: formData.startDate,
      endDate: formData.endDate,
      description: formData.content,
      partyName: formData.customerName || "",
      partyContact: formData.companySignatory || "",
      partyPhone:
        formData.participants.find((p) => p.role === "对方单位")?.phone || "",
      createdBy: userInfo.id || 1,
    };

    const url = isEdit.value
      ? `/api/contracts/${props.contractData.id}`
      : "/api/contracts";
    const method = isEdit.value ? "PUT" : "POST";

    const response = await fetch(url, {
      method: method,
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "X-User-Id": userInfo.id || "1",
      },
      body: JSON.stringify(submitData),
    });

    if (response.status === 401) {
      ElMessage.error("登录已过期，请重新登录");
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      router.push("/login");
      return;
    }

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || "操作失败");
    }

    ElMessage.success(isEdit.value ? "合同更新成功" : "合同创建成功");
    emit("success");
    handleClose();
  } catch (error) {
    console.error("合同操作失败:", error);
    ElMessage.error(
      error.message || (isEdit.value ? "合同更新失败" : "合同创建失败"),
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
}
</style>
