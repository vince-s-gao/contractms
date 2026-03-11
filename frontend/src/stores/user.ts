import { defineStore } from "pinia";
import { ref } from "vue";

interface UserInfo {
  id?: string | number;
  username: string;
  role: string;
  department?: string;
  phone?: string;
  email?: string;
}

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
    // 这里可以根据实际权限系统进行扩展
    return (
      userInfo.value.role === "admin" || userInfo.value.role === permission
    );
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
  };
});
