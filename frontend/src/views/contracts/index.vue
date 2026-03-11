<template>
  <div class="contracts-container">
    <div class="search-area">
      <div class="search-fields">
        <el-input
          v-model="searchParams.keyword"
          placeholder="搜索合同名称、编号"
          prefix-icon="Search"
          clearable
        />
        <el-input
          v-model="searchParams.customerName"
          placeholder="客户名称（模糊搜索）"
          clearable
        />
        <el-select
          v-model="searchParams.signingYears"
          placeholder="签约年份"
          clearable
          multiple
          collapse-tags
          collapse-tags-tooltip
        >
          <el-option
            v-for="year in signingYearOptions"
            :key="year"
            :label="String(year)"
            :value="year"
          />
        </el-select>
        <el-select
          v-model="searchParams.contractType"
          placeholder="合同类型"
          clearable
        >
          <el-option
            v-for="item in contractTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="search-actions">
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="warning" plain @click="handleOpenImport">
          批量上传
        </el-button>
        <el-button type="primary" plain @click="handleOpenExport">
          导出合同
        </el-button>
        <el-button type="success" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          新建合同
        </el-button>
        <el-button type="info" plain @click="handleOpenTypeManage">
          合同类型管理
        </el-button>
      </div>
    </div>

    <!-- 合同列表 -->
    <div class="table-area">
      <div class="table-head">
        <div class="table-title">合同列表</div>
        <div class="table-meta">共 {{ pagination.total }} 条</div>
      </div>
      <el-table
        v-loading="loading"
        :data="contractList"
        stripe
        style="width: 100%"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column
          prop="contractNumber"
          label="合同编号"
          width="180"
          sortable="custom"
          class-name="contract-no-col"
        />
        <el-table-column
          prop="signingYear"
          label="签约年份"
          width="120"
          sortable="custom"
          header-cell-class-name="sort-inline"
        />
        <el-table-column prop="contractName" label="合同名称" min-width="200" sortable="custom" />
        <el-table-column prop="customerName" label="客户名称" min-width="160" sortable="custom" />
        <el-table-column
          prop="companySignatory"
          label="公司签约主体"
          min-width="130"
          sortable="custom"
        />
        <el-table-column
          prop="contractType"
          label="合同类型"
          width="120"
          sortable="custom"
          header-cell-class-name="sort-inline"
        >
          <template #default="{ row }">
            <el-tag>{{ getContractTypeLabel(row.contractType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="合同金额" width="120" sortable="custom">
          <template #default="{ row }">
            ¥{{ formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" sortable="custom" />
        <el-table-column prop="endDate" label="结束日期" width="120" sortable="custom" />
        <el-table-column prop="createdAt" label="创建时间" width="180" sortable="custom" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)"
              >查看</el-button
            >
            <el-button
              v-if="row.status === 'draft'"
              link
              type="warning"
              @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              v-if="row.status === 'draft'"
              link
              type="danger"
              @click="handleDelete(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-area">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 合同详情对话框 -->
    <ContractDetailDialog
      v-model="detailDialogVisible"
      :contract-id="currentContractId"
      @close="handleDetailClose"
    />

    <!-- 合同编辑对话框 -->
    <ContractFormDialog
      v-model="formDialogVisible"
      :contract-data="currentContract"
      @success="handleFormSuccess"
    />

    <el-dialog
      v-model="typeManageDialogVisible"
      title="合同类型管理"
      width="760px"
      :close-on-click-modal="false"
    >
      <div class="type-manage-form">
        <el-input
          v-model="typeForm.code"
          :disabled="!!editingTypeCode"
          placeholder="类型编码（如：RENTAL）"
        />
        <el-input v-model="typeForm.name" placeholder="类型名称（如：租赁合同）" />
        <el-button type="primary" :loading="typeManageLoading" @click="handleSaveType">
          {{ editingTypeCode ? "保存修改" : "新增类型" }}
        </el-button>
        <el-button v-if="editingTypeCode" @click="resetTypeForm">取消编辑</el-button>
      </div>

      <el-table :data="contractTypeList" border>
        <el-table-column prop="code" label="类型编码" min-width="180" />
        <el-table-column prop="name" label="类型名称" min-width="180" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEditType(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteType(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="exportDialogVisible"
      title="导出字段选择"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-checkbox-group v-model="selectedExportFields" class="export-field-group">
        <el-checkbox
          v-for="field in exportFieldOptions"
          :key="field.value"
          :label="field.value"
        >
          {{ field.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exportLoading" @click="handleExportConfirm">
          导出 Excel
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      title="批量上传合同"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-upload
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :file-list="importFileList"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
      >
        <el-button type="primary">选择Excel文件</el-button>
        <template #tip>
          <div class="el-upload__tip">
            支持 .xlsx/.xls，表头建议使用：合同编号、合同名称、合同类型、合同金额、状态、开始日期、结束日期
          </div>
        </template>
      </el-upload>

      <el-checkbox v-model="importOverwrite" class="import-overwrite">
        覆盖已存在合同（按合同编号匹配）
      </el-checkbox>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImportConfirm">
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox, type UploadFile, type UploadRawFile } from "element-plus";
import {
  createContractType,
  deleteContractType,
  exportContracts,
  getContractTypes,
  importContracts,
  updateContractType,
  type ContractTypeItem,
} from "@/api/contract";
import ContractDetailDialog from "./components/ContractDetailDialog.vue";
import ContractFormDialog from "./components/ContractFormDialog.vue";

interface Contract {
  id: string;
  contractNumber: string;
  signingYear?: number;
  contractName: string;
  customerName?: string;
  companySignatory?: string;
  contractType: string;
  amount: number;
  status: string;
  startDate: string;
  endDate: string;
  createdBy: string;
  createdAt: string;
}

interface SearchParams {
  keyword: string;
  customerName: string;
  contractType: string;
  signingYears: number[];
}

interface Pagination {
  current: number;
  size: number;
  total: number;
}

interface SortState {
  prop: string;
  order: "" | "ascending" | "descending";
}

interface ExportFieldOption {
  label: string;
  value: string;
}

interface ContractTypeForm {
  code: string;
  name: string;
}

const loading = ref(false);
const router = useRouter();
const contractList = ref<Contract[]>([]);
const searchParams = reactive<SearchParams>({
  keyword: "",
  customerName: "",
  contractType: "",
  signingYears: [],
});
const signingYearOptions = ref<number[]>([]);
const pagination = reactive<Pagination>({
  current: 1,
  size: 10,
  total: 0,
});
const sortState = reactive<SortState>({
  prop: "",
  order: "",
});

// 对话框控制
const detailDialogVisible = ref(false);
const formDialogVisible = ref(false);
const currentContractId = ref("");
const currentContract = ref<Contract | null>(null);
const exportDialogVisible = ref(false);
const exportLoading = ref(false);
const importDialogVisible = ref(false);
const importLoading = ref(false);
const importOverwrite = ref(false);
const importFileList = ref<UploadFile[]>([]);
const importRawFile = ref<UploadRawFile | null>(null);
const typeManageDialogVisible = ref(false);
const typeManageLoading = ref(false);
const defaultContractTypeList: ContractTypeItem[] = [
  { code: "SALES", name: "销售合同" },
  { code: "PURCHASE", name: "采购合同" },
  { code: "SERVICE", name: "服务合同" },
  { code: "OTHER", name: "其他" },
];
const contractTypeList = ref<ContractTypeItem[]>([...defaultContractTypeList]);
const editingTypeCode = ref("");
const typeForm = reactive<ContractTypeForm>({
  code: "",
  name: "",
});
const contractTypeOptions = computed(() =>
  contractTypeList.value.map((item) => ({
    label: item.name,
    value: item.code,
  })),
);
const exportFieldOptions: ExportFieldOption[] = [
  { label: "合同编号", value: "contractNo" },
  { label: "签约年份", value: "signingYear" },
  { label: "合同名称", value: "contractName" },
  { label: "客户名称", value: "customerName" },
  { label: "公司签约主体", value: "companySignatory" },
  { label: "合同类型", value: "contractType" },
  { label: "合同金额", value: "amount" },
  { label: "状态", value: "status" },
  { label: "开始日期", value: "startDate" },
  { label: "结束日期", value: "endDate" },
  { label: "创建时间", value: "createdAt" },
];
const selectedExportFields = ref<string[]>(
  exportFieldOptions.map((item) => item.value),
);

onMounted(() => {
  loadContractTypeList();
  loadSigningYearOptions();
  loadContractList();
});

const loadContractTypeList = async () => {
  try {
    const response = await getContractTypes();
    contractTypeList.value = response.records || response.data?.records || [];
    return true;
  } catch (error) {
    console.error("加载合同类型失败:", error);
    if (!contractTypeList.value.length) {
      contractTypeList.value = [...defaultContractTypeList];
    }
    return false;
  }
};

const loadSigningYearOptions = async () => {
  try {
    const token = localStorage.getItem("token");
    const response = await fetch("/api/contracts/signing-years", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      throw new Error("加载签约年份失败");
    }
    const data = await response.json();
    signingYearOptions.value = (data.records || data.data || [])
      .map((item: any) => Number(item))
      .filter((item: number) => Number.isInteger(item));
  } catch (error) {
    console.error("加载签约年份失败:", error);
    signingYearOptions.value = [];
  }
};

const loadContractList = async () => {
  loading.value = true;

  try {
    const query = new URLSearchParams({
      page: String(pagination.current),
      size: String(pagination.size),
    });
    if (searchParams.keyword) {
      query.append("keyword", searchParams.keyword);
    }
    if (searchParams.customerName) {
      query.append("customerName", searchParams.customerName);
    }
    if (searchParams.contractType) {
      query.append("contractType", searchParams.contractType);
    }
    if (searchParams.signingYears.length > 0) {
      query.append("signingYears", searchParams.signingYears.join(","));
    }
    if (sortState.prop && sortState.order) {
      query.append("sortBy", sortState.prop);
      query.append("sortOrder", sortState.order === "ascending" ? "asc" : "desc");
    }

    // 调用真实API获取合同列表
    const token = localStorage.getItem("token");
    const response = await fetch(`/api/contracts?${query.toString()}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });

    if (response.status === 401 || response.status === 403) {
      // 未授权，跳转到登录页
      ElMessage.error("登录已过期，请重新登录");
      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      router.push("/login");
      return;
    }

    if (!response.ok) {
      throw new Error("获取合同列表失败");
    }

    const data = await response.json();
    contractList.value = data.records || data.data || [];
    pagination.total = data.total || data.data?.length || 0;
  } catch (error) {
    console.error("加载合同列表错误:", error);
    ElMessage.error("加载合同列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadContractList();
};

const handleReset = () => {
  searchParams.keyword = "";
  searchParams.customerName = "";
  searchParams.contractType = "";
  searchParams.signingYears = [];
  sortState.prop = "";
  sortState.order = "";
  pagination.current = 1;
  loadContractList();
};

const handleSortChange = ({
  prop,
  order,
}: {
  prop: string;
  order: "" | "ascending" | "descending";
}) => {
  sortState.prop = prop || "";
  sortState.order = order || "";
  pagination.current = 1;
  loadContractList();
};

const handleCreate = () => {
  currentContract.value = null;
  formDialogVisible.value = true;
};

const handleOpenTypeManage = async () => {
  await loadContractTypeList();
  resetTypeForm();
  typeManageDialogVisible.value = true;
};

const handleOpenExport = () => {
  exportDialogVisible.value = true;
};

const handleOpenImport = () => {
  importDialogVisible.value = true;
};

const getFileNameFromContentDisposition = (contentDisposition?: string) => {
  if (!contentDisposition) {
    return `合同导出_${Date.now()}.xlsx`;
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const basicMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
  if (basicMatch?.[1]) {
    return basicMatch[1];
  }
  return `合同导出_${Date.now()}.xlsx`;
};

const handleExportConfirm = async () => {
  if (selectedExportFields.value.length === 0) {
    ElMessage.warning("请至少选择一个导出字段");
    return;
  }

  exportLoading.value = true;
  try {
    const params: Record<string, string> = {
      fields: selectedExportFields.value.join(","),
    };
    if (searchParams.keyword) {
      params.keyword = searchParams.keyword;
    }
    const response = await exportContracts(params);
    const blob = new Blob([response.data], {
      type:
        response.headers["content-type"] ||
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const fileName = getFileNameFromContentDisposition(
      response.headers["content-disposition"],
    );
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);

    ElMessage.success("导出成功");
    exportDialogVisible.value = false;
  } catch (error) {
    console.error("导出合同失败:", error);
    ElMessage.error("导出失败，请稍后重试");
  } finally {
    exportLoading.value = false;
  }
};

const handleImportFileChange = (file: UploadFile, uploadFiles: UploadFile[]) => {
  importFileList.value = uploadFiles.slice(-1);
  importRawFile.value = (file.raw || null) as UploadRawFile | null;
};

const handleImportFileRemove = () => {
  importRawFile.value = null;
};

const handleImportConfirm = async () => {
  if (!importRawFile.value) {
    ElMessage.warning("请先选择Excel文件");
    return;
  }
  importLoading.value = true;
  try {
    const formData = new FormData();
    formData.append("file", importRawFile.value);
    const result = await importContracts(formData, importOverwrite.value);
    ElMessage.success(
      `导入完成：新增${result.success || 0}，更新${result.updated || 0}，跳过${result.skipped || 0}，失败${result.failed || 0}`,
    );
    importDialogVisible.value = false;
    importFileList.value = [];
    importRawFile.value = null;
    importOverwrite.value = false;
    pagination.current = 1;
    await loadContractList();
  } catch (error: any) {
    console.error("批量导入失败:", error);
    ElMessage.error(error?.response?.data?.message || "批量导入失败，请检查文件格式");
  } finally {
    importLoading.value = false;
  }
};

const handleView = (row: Contract) => {
  currentContractId.value = row.id;
  detailDialogVisible.value = true;
};

const handleEdit = (row: Contract) => {
  currentContract.value = row;
  formDialogVisible.value = true;
};

const handleDelete = async (row: Contract) => {
  try {
    await ElMessageBox.confirm(
      `确定删除合同"${row.contractName}"吗？`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    const token = localStorage.getItem("token");
    const response = await fetch(`/api/contracts/${row.id}`, {
      method: "DELETE",
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
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || "删除失败");
    }

    ElMessage.success("删除成功");
    await loadContractList();
  } catch (error: any) {
    if (error === "cancel" || error === "close") {
      return;
    }
    console.error("删除合同失败:", error);
    ElMessage.error(error?.message || "删除合同失败");
  }
};

const resetTypeForm = () => {
  editingTypeCode.value = "";
  typeForm.code = "";
  typeForm.name = "";
};

const handleEditType = (row: ContractTypeItem) => {
  editingTypeCode.value = row.code;
  typeForm.code = row.code;
  typeForm.name = row.name;
};

const handleSaveType = async () => {
  if (typeManageLoading.value) {
    return;
  }
  const code = typeForm.code.trim().toUpperCase();
  const name = typeForm.name.trim();
  if (!code) {
    ElMessage.warning("请输入类型编码");
    return;
  }
  if (!/^[A-Z0-9_]{2,50}$/.test(code)) {
    ElMessage.warning("类型编码仅支持2-50位大写字母、数字、下划线");
    return;
  }
  if (!name) {
    ElMessage.warning("请输入类型名称");
    return;
  }

  typeManageLoading.value = true;
  try {
    if (editingTypeCode.value) {
      await updateContractType(editingTypeCode.value, { code, name });
      ElMessage.success("合同类型更新成功");
    } else {
      await createContractType({ code, name });
      ElMessage.success("合同类型新增成功");
    }
    await loadContractTypeList();
    resetTypeForm();
  } catch (error: any) {
    console.error("保存合同类型失败:", error);
    const message = error?.response?.data?.message || "保存合同类型失败";
    if (String(message).includes("已存在")) {
      await loadContractTypeList();
      ElMessage.warning("该类型已存在，已刷新列表");
      return;
    }
    ElMessage.error(message);
  } finally {
    typeManageLoading.value = false;
  }
};

const handleDeleteType = async (row: ContractTypeItem) => {
  try {
    await ElMessageBox.confirm(
      `确定删除合同类型【${row.name}（${row.code}）】吗？`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await deleteContractType(row.code);
    ElMessage.success("删除成功");
    await loadContractTypeList();
    if (editingTypeCode.value === row.code) {
      resetTypeForm();
    }
  } catch (error: any) {
    if (error === "cancel" || error === "close") {
      return;
    }
    console.error("删除合同类型失败:", error);
    ElMessage.error(error?.response?.data?.message || "删除合同类型失败");
  }
};

const getContractTypeLabel = (code: string) => {
  const matched = contractTypeList.value.find((item) => item.code === code);
  return matched?.name || code || "-";
};

const handleDetailClose = () => {
  currentContractId.value = "";
};

const handleFormSuccess = () => {
  formDialogVisible.value = false;
  loadContractList();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  loadContractList();
};

const handleCurrentChange = (current: number) => {
  pagination.current = current;
  loadContractList();
};

const formatAmount = (amount: number) => {
  return Number(amount || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

</script>

<style lang="scss" scoped>
.contracts-container {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  min-height: calc(100vh - 140px);
}

.search-area {
  margin-bottom: 14px;
  background: #f8fafc;
  border: 1px solid #e9edf3;
  border-radius: 8px;
  padding: 12px;

  .search-fields {
    display: grid;
    grid-template-columns: 2fr 1.4fr 1fr 1fr;
    gap: 10px;
    margin-bottom: 10px;
  }

  .search-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.table-area {
  border: 1px solid #e9edf3;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  padding: 10px 12px 12px;

  .table-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
  }

  .table-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .table-meta {
    font-size: 12px;
    color: #909399;
  }

  :deep(.el-table th.el-table__cell) {
    background: #f5f7fa;
    color: #4a5568;
    font-weight: 600;
  }

  :deep(.el-table th.sort-inline .cell) {
    white-space: nowrap;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  :deep(.el-table .cell) {
    line-height: 1.4;
  }

  :deep(.el-table .contract-no-col .cell) {
    white-space: nowrap;
    word-break: keep-all;
  }

  .pagination-area {
    margin-top: 14px;
    text-align: right;
  }
}

.export-field-group {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 8px;
}

.import-overwrite {
  margin-top: 16px;
}

.type-manage-form {
  display: grid;
  grid-template-columns: 1.1fr 1.2fr auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

@media (max-width: 1400px) {
  .search-area {
    .search-fields {
      grid-template-columns: 1fr 1fr;
    }
  }
}

@media (max-width: 900px) {
  .contracts-container {
    padding: 10px;
    border-radius: 6px;
  }

  .search-area {
    padding: 10px;

    .search-fields {
      grid-template-columns: 1fr;
    }
  }

  .table-area {
    padding: 8px;

    .pagination-area {
      :deep(.el-pagination) {
        justify-content: center;
      }
    }
  }
}
</style>
