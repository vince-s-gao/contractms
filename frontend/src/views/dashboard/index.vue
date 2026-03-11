<template>
  <div class="dashboard-container">
    <!-- 顶部导航栏 -->
    <el-header class="header">
      <div class="header-left">
        <span class="logo">合同管理系统</span>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            {{ userInfo.username }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="logout" divided
                >退出登录</el-dropdown-item
              >
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <!-- 主内容区域 -->
    <el-container class="main-container">
      <!-- 侧边栏 -->
      <el-aside width="200px" class="sidebar">
        <el-menu
          :default-active="activeMenu"
          class="sidebar-menu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表板</span>
          </el-menu-item>
          <el-menu-item index="/contracts">
            <el-icon><Document /></el-icon>
            <span>合同管理</span>
          </el-menu-item>
          <el-menu-item index="/approval">
            <el-icon><Check /></el-icon>
            <span>合同审批</span>
          </el-menu-item>
          <el-menu-item index="/permissions">
            <el-icon><Setting /></el-icon>
            <span>权限管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- 内容区域 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Setting } from "@element-plus/icons-vue";

interface UserInfo {
  username: string;
  role: string;
}

const router = useRouter();
const route = useRoute();
const activeMenu = computed(() => route.path);
const userInfo = ref<UserInfo>({ username: "", role: "" });

onMounted(() => {
  // 获取用户信息
  const userInfoStr = localStorage.getItem("userInfo");
  if (userInfoStr) {
    userInfo.value = JSON.parse(userInfoStr);
  }
});

const handleCommand = async (command: string) => {
  if (command === "logout") {
    try {
      await ElMessageBox.confirm("确定要退出登录吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });

      localStorage.removeItem("token");
      localStorage.removeItem("userInfo");
      ElMessage.success("退出成功");
      router.push("/login");
    } catch {
      // 用户取消操作
    }
  } else if (command === "profile") {
    ElMessage.info("个人信息功能开发中");
  }
};
</script>

<style lang="scss" scoped>
.dashboard-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);

  .header-left {
    .logo {
      font-size: 20px;
      font-weight: bold;
      color: #409eff;
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      cursor: pointer;
      color: #606266;

      .el-icon {
        margin-right: 5px;
      }
    }
  }
}

.main-container {
  flex: 1;
  overflow: hidden;
}

.sidebar {
  background-color: #304156;

  .sidebar-menu {
    border: none;

    .el-menu-item {
      border-left: 3px solid transparent;

      &.is-active {
        background-color: #1f2d3d !important;
        border-left-color: #409eff;
      }

      &:hover {
        background-color: #263445 !important;
      }
    }
  }
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
