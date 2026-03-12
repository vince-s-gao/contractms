<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="500px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="审批意见" prop="comment">
        <el-input
          v-model="formData.comment"
          type="textarea"
          :rows="4"
          :placeholder="
            action === 'approve'
              ? '请输入审批通过意见（可选）'
              : '请输入拒绝原因（必填）'
          "
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="action === 'reject' && !formData.comment.trim()"
        @click="handleSubmit"
      >
        {{ action === "approve" ? "通过" : "拒绝" }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { approveContract } from "@/api/contract";
import { extractErrorMessage } from "@/utils/error";

interface ApprovalFormData {
  comment: string;
}

interface ApprovalDialogTask {
  id: string;
  contractId?: string;
  contractName: string;
}

const props = defineProps<{
  modelValue: boolean;
  task?: ApprovalDialogTask | null;
  action: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  success: [payload: { contractId: string; approved: boolean }];
}>();

const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();

const formData = reactive<ApprovalFormData>({
  comment: "",
});

const formRules: FormRules = {
  comment: [
    {
      validator: (rule, value, callback) => {
        if (props.action === "reject" && !value.trim()) {
          callback(new Error("拒绝原因不能为空"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
};

const dialogTitle = computed(() => {
  if (!props.task) return "";
  return `${props.action === "approve" ? "通过" : "拒绝"}审批 - ${props.task.contractName}`;
});

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      resetForm();
    }
  },
);

watch(visible, (val) => {
  emit("update:modelValue", val);
});

const resetForm = () => {
  formRef.value?.resetFields();
  formData.comment = "";
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
    const contractId = props.task?.contractId || props.task?.id;
    if (!contractId) {
      ElMessage.error("审批目标不存在");
      return;
    }
    await approveContract(
      String(contractId),
      props.action === "approve",
      formData.comment.trim() || undefined,
    );

    ElMessage.success(
      props.action === "approve" ? "审批通过成功" : "审批拒绝成功",
    );
    emit("success", {
      contractId: String(contractId),
      approved: props.action === "approve",
    });
    handleClose();
  } catch (error) {
    ElMessage.error(
      extractErrorMessage(
        error,
        props.action === "approve" ? "审批通过失败" : "审批拒绝失败",
      ),
    );
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss" scoped>
// 样式可以根据需要添加
</style>
