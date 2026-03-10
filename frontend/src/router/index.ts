import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

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
        },
      },
      {
        path: "contracts",
        name: "Contracts",
        component: () => import("@/views/contracts/index.vue"),
        meta: {
          title: "合同管理",
          requiresAuth: true,
        },
      },
      {
        path: "approval",
        name: "Approval",
        component: () => import("@/views/approval/index.vue"),
        meta: {
          title: "合同审批",
          requiresAuth: true,
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

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 合同管理系统`;
  }

  // 检查登录状态
  const token = localStorage.getItem("token");
  if (to.meta.requiresAuth && !token) {
    next("/login");
  } else {
    next();
  }
});

export default router;
