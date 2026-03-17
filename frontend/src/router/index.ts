import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/stores/user";

const dashboardPermissions = ["DASHBOARD:VIEW"];
const contractPermissions = [
  "CONTRACT_VIEW",
  "CONTRACT_CREATE",
  "CONTRACT_EDIT",
  "CONTRACT_DELETE",
  "CONTRACT_BATCH_UPLOAD",
  "CONTRACT_EXPORT",
  "CONTRACT_TYPE_MANAGE",
  "contract:read",
  "contract:write",
];
const approvalPermissions = [
  "APPROVAL_VIEW",
  "APPROVAL_PROCESS",
  "CONTRACT_APPROVE",
  "contract:approval",
];
const permissionManagePermissions = ["SYSTEM:PERMISSION", "system:permission"];

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: "/",
    component: () => import("@/views/dashboard/index.vue"),
    meta: {
      requiresAuth: true,
    },
    children: [
      {
        path: "",
        redirect: "/dashboard",
      },
      {
        path: "dashboard",
        name: "Dashboard",
        component: () => import("@/views/dashboard/home.vue"),
        meta: {
          title: "仪表板",
          requiresAuth: true,
          permissions: dashboardPermissions,
        },
      },
      {
        path: "contracts",
        name: "Contracts",
        component: () => import("@/views/contracts/index.vue"),
        meta: {
          title: "合同管理",
          requiresAuth: true,
          permissions: contractPermissions,
        },
      },
      {
        path: "approval",
        name: "Approval",
        component: () => import("@/views/approval/index.vue"),
        meta: {
          title: "合同审批",
          requiresAuth: true,
          permissions: approvalPermissions,
        },
      },
      {
        path: "permissions",
        name: "Permissions",
        component: () => import("@/views/permissions/index.vue"),
        meta: {
          title: "权限管理",
          requiresAuth: true,
          permissions: permissionManagePermissions,
        },
      },
    ],
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login/index.vue"),
    meta: {
      title: "登录",
      requiresAuth: false,
    },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/views/error/404.vue"),
    meta: {
      title: "页面不存在",
    },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

const resolveFirstAuthorizedPath = (userStore: ReturnType<typeof useUserStore>): string => {
  if (userStore.hasAnyPermission(contractPermissions)) {
    return "/contracts";
  }
  if (userStore.hasAnyPermission(approvalPermissions)) {
    return "/approval";
  }
  if (userStore.hasAnyPermission(permissionManagePermissions)) {
    return "/permissions";
  }
  if (userStore.hasAnyPermission(dashboardPermissions)) {
    return "/dashboard";
  }
  return "/login";
};

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore();
  userStore.loadUserInfoFromStorage();

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 合同管理系统`;
  }

  // 检查登录状态
  const token =
    sessionStorage.getItem("token") || localStorage.getItem("token");
  if (to.meta.requiresAuth && !token) {
    next("/login");
    return;
  }

  const requiredPermissions = Array.isArray(to.meta.permissions)
    ? (to.meta.permissions as string[])
    : [];
  if (requiredPermissions.length > 0 && !userStore.hasAnyPermission(requiredPermissions)) {
    const targetPath = resolveFirstAuthorizedPath(userStore);
    if (targetPath === "/login") {
      userStore.clearUserInfo();
    }
    next(targetPath);
    return;
  }

  next();
});

export default router;
