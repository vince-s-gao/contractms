import { defineStore } from "pinia";
import { ref } from "vue";

interface UserInfo {
  id?: string | number;
  username: string;
  role: string;
  permissionCodes?: string[];
  authorities?: string[];
  department?: string;
  phone?: string;
  email?: string;
}

const normalizeCode = (value: string): string => {
  return value.trim().toUpperCase().replace(/[:\s-]/g, "_");
};

export const useUserStore = defineStore("user", () => {
  const userInfo = ref<UserInfo | null>(null);
  const token = ref<string>("");

  // 设置用户信息
  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info;
    sessionStorage.setItem("userInfo", JSON.stringify(info));
  };

  // 设置token
  const setToken = (newToken: string) => {
    token.value = newToken;
    sessionStorage.setItem("token", newToken);
  };

  // 获取token
  const getToken = (): string => {
    return (
      token.value ||
      sessionStorage.getItem("token") ||
      localStorage.getItem("token") ||
      ""
    );
  };

  // 清除用户信息
  const clearUserInfo = () => {
    userInfo.value = null;
    token.value = "";
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("userInfo");
    localStorage.removeItem("token");
    localStorage.removeItem("userInfo");
  };

  // 从浏览器存储加载用户信息
  const loadUserInfoFromStorage = () => {
    const tokenStr =
      sessionStorage.getItem("token") || localStorage.getItem("token");
    const userInfoStr =
      sessionStorage.getItem("userInfo") || localStorage.getItem("userInfo");

    if (tokenStr) {
      token.value = tokenStr;
    }

    if (userInfoStr) {
      try {
        userInfo.value = JSON.parse(userInfoStr);
      } catch (error) {
        console.error("解析用户信息失败", error);
        clearUserInfo();
      }
    }
  };

  // 检查登录状态
  const isLoggedIn = (): boolean => {
    return !!getToken();
  };

  // 检查权限
  const hasPermission = (permission: string): boolean => {
    if (!userInfo.value) return false;
    const roleCode = String(userInfo.value.role || "").trim().toUpperCase();
    if (
      roleCode === "ADMIN" ||
      roleCode === "ROLE_ADMIN" ||
      roleCode === "SUPER_ADMIN" ||
      roleCode === "ROLE_SUPER_ADMIN" ||
      roleCode === "ROLE_ROLE_ADMIN"
    ) {
      return true;
    }

    const wanted = normalizeCode(permission || "");
    if (!wanted) {
      return false;
    }

    // 角色兜底：兼容历史数据里未配置 permissions 字段的管理员角色
    if (
      (roleCode === "CONTRACT_MANAGER" || roleCode === "ROLE_CONTRACT_MANAGER") &&
      (wanted.startsWith("CONTRACT_") ||
        wanted === "FILE_UPLOAD" ||
        wanted === "FILE_DELETE" ||
        wanted === "FILE_DOWNLOAD" ||
        wanted === "CONTRACT_READ" ||
        wanted === "CONTRACT_WRITE")
    ) {
      return true;
    }
    if (
      (roleCode === "APPROVAL_MANAGER" || roleCode === "ROLE_APPROVAL_MANAGER") &&
      (wanted.startsWith("APPROVAL_") ||
        wanted === "CONTRACT_APPROVE" ||
        wanted === "CONTRACT_APPROVAL")
    ) {
      return true;
    }

    const permissionSet = new Set<string>();
    const fromUser = Array.isArray(userInfo.value.permissionCodes)
      ? userInfo.value.permissionCodes
      : [];
    const fromAuthorities = Array.isArray(userInfo.value.authorities)
      ? userInfo.value.authorities
      : [];

    for (const item of [...fromUser, ...fromAuthorities]) {
      const raw = String(item || "").trim();
      if (!raw) {
        continue;
      }
      permissionSet.add(raw.toUpperCase());
      permissionSet.add(normalizeCode(raw));
    }

    return permissionSet.has(wanted) || permissionSet.has(String(permission).toUpperCase());
  };

  const hasAnyPermission = (permissions: string[]): boolean => {
    if (!Array.isArray(permissions) || permissions.length === 0) {
      return false;
    }
    return permissions.some((code) => hasPermission(code));
  };

  return {
    userInfo,
    token,
    setUserInfo,
    setToken,
    getToken,
    clearUserInfo,
    loadUserInfoFromStorage,
    isLoggedIn,
    hasPermission,
    hasAnyPermission,
  };
});
