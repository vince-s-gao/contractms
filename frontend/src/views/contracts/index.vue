<template>
  <div class="contracts-container">
    <!-- 搜索和操作区域 -->
    <div class="search-area">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-input
            v-model="searchParams.keyword"
            placeholder="搜索合同名称、编号"
            prefix-icon="Search"
            clearable
          />
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="searchParams.status"
            placeholder="合同状态"
            clearable
          >
            <el-option label="草稿" value="draft" />
            <el-option label="审批中" value="approving" />
            <el-option label="已生效" value="active" />
            <el-option label="已终止" value="terminated" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-date-picker
            v-model="searchParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-col>
        <el-col :span="10">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新建合同
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 合同列表 -->
    <div class="table-area">
      <el-table
        v-loading="loading"
        :data="contractList"
        stripe
        style="width: 100%"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="contractNumber" label="合同编号" width="120" />
        <el-table-column prop="contractName" label="合同名称" min-width="200" />
        <el-table-column prop="contractType" label="合同类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.contractType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="合同金额" width="120">
          <template #default="{ row }">
            ¥{{ formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import ContractDetailDialog from "./components/ContractDetailDialog.vue";
import ContractFormDialog from "./components/ContractFormDialog.vue";

interface Contract {
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
}

interface SearchParams {
  keyword: string;
  status: string;
  dateRange: string[];
}

interface Pagination {
  current: number;
  size: number;
  total: number;
}

const loading = ref(false);
const router = useRouter();
const contractList = ref<Contract[]>([]);
const searchParams = reactive<SearchParams>({
  keyword: "",
  status: "",
  dateRange: [],
});
const pagination = reactive<Pagination>({
  current: 1,
  size: 10,
  total: 0,
});

// 对话框控制
const detailDialogVisible = ref(false);
const formDialogVisible = ref(false);
const currentContractId = ref("");
const currentContract = ref<Contract | null>(null);

// 模拟数据
const mockContracts: Contract[] = [
  {
    id: "1",
    contractNumber: "HT20230001",
    contractName: "软件开发服务合同",
    contractType: "技术服务",
    amount: 500000,
    status: "active",
    startDate: "2023-01-01",
    endDate: "2023-12-31",
    createdBy: "张三",
    createdAt: "2023-01-01 10:00:00",
  },
  {
    id: "2",
    contractNumber: "HT20230002",
    contractName: "设备采购合同",
    contractType: "采购",
    amount: 200000,
    status: "draft",
    startDate: "2023-02-01",
    endDate: "2023-02-28",
    createdBy: "李四",
    createdAt: "2023-02-01 14:30:00",
  },
];

onMounted(() => {
  loadContractList();
});

const loadContractList = async () => {
  loading.value = true;

  try {
    // 调用真实API获取合同列表
    const token = localStorage.getItem("token");
    const response = await fetch(
      `/api/contracts?page=${pagination.current}&size=${pagination.size}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      },
    );

    if (response.status === 401) {
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
    // 降级使用模拟数据
    contractList.value = mockContracts;
    pagination.total = mockContracts.length;
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
  searchParams.status = "";
  searchParams.dateRange = [];
  pagination.current = 1;
  loadContractList();
};

const handleCreate = () => {
  currentContract.value = null;
  formDialogVisible.value = true;
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

    // 模拟删除操作
    ElMessage.success("删除成功");
    loadContractList();
  } catch {
    // 用户取消操作
  }
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
</script>

<style lang="scss" scoped>
.contracts-container {
  padding: 20px;
  background: white;
  border-radius: 4px;
  min-height: calc(100vh - 140px);
}

.search-area {
  margin-bottom: 20px;

  .el-col {
    margin-bottom: 10px;
  }
}

.table-area {
  .pagination-area {
    margin-top: 20px;
    text-align: right;
  }
}
</style>
