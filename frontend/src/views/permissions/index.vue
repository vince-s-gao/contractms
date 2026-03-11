<template>
  <div class="permissions-page">
    <el-card class="section-card" header="用户角色分配">
      <div class="toolbar">
        <el-input
          v-model="userKeyword"
          placeholder="按用户名/姓名搜索"
          clearable
          style="width: 260px"
          @keyup.enter="loadUsers"
        />
        <el-button type="primary" @click="loadUsers">搜索</el-button>
      </div>

      <el-table v-loading="userLoading" :data="userList" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="roleName" label="当前角色" min-width="140" />
        <el-table-column label="分配角色" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.roleId"
              placeholder="请选择角色"
              style="width: 180px"
              @change="(roleId:number) => handleAssignRole(row.id, roleId)"
            >
              <el-option
                v-for="role in roleList"
                :key="role.id"
                :label="role.roleName"
                :value="role.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="isAdminUser && row.username !== 'admin' && row.username !== currentUsername"
              link
              type="danger"
              @click="handleDeleteUser(row)"
            >
              删除用户
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="section-card" header="角色权限管理">
      <div class="toolbar">
        <el-button type="primary" @click="openRoleDialog()">新增角色</el-button>
      </div>

      <el-table v-loading="roleLoading" :data="roleList" stripe>
        <el-table-column prop="roleCode" label="角色编码" min-width="160" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column label="权限数量" width="100">
          <template #default="{ row }">
            {{ row.permissionCodes?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoleDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="roleDialogVisible"
      :title="editingRoleId ? '编辑角色' : '新增角色'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="角色编码">
          <el-input
            v-model="roleForm.roleCode"
            placeholder="如：CONTRACT_EDITOR"
            :disabled="!!editingRoleId"
          />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input
            v-model="roleForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入角色描述"
          />
        </el-form-item>
        <el-form-item label="权限勾选">
          <el-checkbox-group v-model="roleForm.permissionCodes">
            <el-checkbox
              v-for="permission in permissionList"
              :key="permission.code"
              :label="permission.code"
            >
              {{ permission.name }}（{{ permission.code }}）
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveRoleLoading" @click="handleSaveRole">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createSystemRole,
  deleteSystemUser,
  deleteSystemRole,
  getSystemPermissions,
  getSystemRoles,
  getSystemUsers,
  updateSystemRole,
  updateSystemUserRole,
  type PermissionItem,
  type RoleItem,
  type UserPermissionItem,
} from "@/api/permission";
import { useUserStore } from "@/stores/user";
import { extractErrorMessage } from "@/utils/error";

interface RoleForm {
  roleCode: string;
  roleName: string;
  description: string;
  permissionCodes: string[];
}

const userLoading = ref(false);
const roleLoading = ref(false);
const saveRoleLoading = ref(false);
const userKeyword = ref("");

const userList = ref<UserPermissionItem[]>([]);
const roleList = ref<RoleItem[]>([]);
const permissionList = ref<PermissionItem[]>([]);

const roleDialogVisible = ref(false);
const editingRoleId = ref<number | null>(null);
const currentUsername = ref("");
const isAdminUser = ref(false);
const roleForm = reactive<RoleForm>({
  roleCode: "",
  roleName: "",
  description: "",
  permissionCodes: [],
});
const userStore = useUserStore();

const loadUsers = async () => {
  userLoading.value = true;
  try {
    const data = await getSystemUsers(userKeyword.value.trim());
    userList.value = data.records || data.data?.records || [];
  } catch (error) {
    console.error("加载用户失败:", error);
    ElMessage.error("加载用户失败");
  } finally {
    userLoading.value = false;
  }
};

const loadRoles = async () => {
  roleLoading.value = true;
  try {
    const data = await getSystemRoles();
    roleList.value = data.records || data.data?.records || [];
  } catch (error) {
    console.error("加载角色失败:", error);
    ElMessage.error("加载角色失败");
  } finally {
    roleLoading.value = false;
  }
};

const loadPermissions = async () => {
  try {
    const data = await getSystemPermissions();
    permissionList.value = data.records || data.data?.records || [];
  } catch (error) {
    console.error("加载权限列表失败:", error);
    ElMessage.error("加载权限列表失败");
  }
};

const handleAssignRole = async (userId: number, roleId: number) => {
  try {
    await updateSystemUserRole(userId, roleId);
    ElMessage.success("角色分配成功");
    await loadUsers();
  } catch (error) {
    console.error("分配角色失败:", error);
    ElMessage.error(extractErrorMessage(error, "分配角色失败"));
    await loadUsers();
  }
};

const handleDeleteUser = async (row: UserPermissionItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除用户【${row.username}】吗？删除后不可恢复。`,
      "提示",
      {
        type: "warning",
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
      },
    );
    await deleteSystemUser(row.id);
    ElMessage.success("用户删除成功");
    await loadUsers();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }
    console.error("删除用户失败:", error);
    ElMessage.error(extractErrorMessage(error, "删除用户失败"));
  }
};

const resetRoleForm = () => {
  editingRoleId.value = null;
  roleForm.roleCode = "";
  roleForm.roleName = "";
  roleForm.description = "";
  roleForm.permissionCodes = [];
};

const openRoleDialog = (role?: RoleItem) => {
  resetRoleForm();
  if (role) {
    editingRoleId.value = role.id;
    roleForm.roleCode = role.roleCode;
    roleForm.roleName = role.roleName;
    roleForm.description = role.description || "";
    roleForm.permissionCodes = [...(role.permissionCodes || [])];
  }
  roleDialogVisible.value = true;
};

const handleSaveRole = async () => {
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) {
    ElMessage.warning("角色编码和角色名称不能为空");
    return;
  }
  saveRoleLoading.value = true;
  try {
    const payload = {
      roleCode: roleForm.roleCode.trim().toUpperCase(),
      roleName: roleForm.roleName.trim(),
      description: roleForm.description.trim(),
      permissionCodes: roleForm.permissionCodes,
    };
    if (editingRoleId.value) {
      await updateSystemRole(editingRoleId.value, payload);
      ElMessage.success("角色更新成功");
    } else {
      await createSystemRole(payload);
      ElMessage.success("角色创建成功");
    }
    roleDialogVisible.value = false;
    await loadRoles();
    await loadUsers();
  } catch (error) {
    console.error("保存角色失败:", error);
    ElMessage.error(extractErrorMessage(error, "保存角色失败"));
  } finally {
    saveRoleLoading.value = false;
  }
};

const handleDeleteRole = async (role: RoleItem) => {
  try {
    await ElMessageBox.confirm(`确认删除角色【${role.roleName}】吗？`, "提示", {
      type: "warning",
      confirmButtonText: "确定",
      cancelButtonText: "取消",
    });
    await deleteSystemRole(role.id);
    ElMessage.success("删除成功");
    await loadRoles();
    await loadUsers();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }
    console.error("删除角色失败:", error);
    ElMessage.error(extractErrorMessage(error, "删除角色失败"));
  }
};

onMounted(async () => {
  userStore.loadUserInfoFromStorage();
  currentUsername.value = String(userStore.userInfo?.username || "");
  isAdminUser.value =
    /admin/i.test(String(userStore.userInfo?.role || "")) ||
    /admin/i.test(currentUsername.value);
  await Promise.all([loadPermissions(), loadRoles()]);
  await loadUsers();
});
</script>

<style scoped lang="scss">
.permissions-page {
  .section-card {
    margin-bottom: 16px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
  }
}
</style>
