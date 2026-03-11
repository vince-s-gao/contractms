import axios from "axios";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user";
import type { AxiosRequestConfig, RawAxiosRequestHeaders } from "axios";

const SILENT_ERROR_HEADER = "X-Silent-Error-Message";

const shouldShowGlobalError = (config?: AxiosRequestConfig) => {
  const headers = config?.headers as
    | RawAxiosRequestHeaders
    | undefined
    | {
        get?: (name: string) => string | null | undefined;
        [key: string]: unknown;
      };
  if (!headers) {
    return true;
  }
  if (typeof headers.get === "function") {
    return String(headers.get(SILENT_ERROR_HEADER) || "").toLowerCase() !== "true";
  }
  const direct =
    headers[SILENT_ERROR_HEADER] ?? headers[SILENT_ERROR_HEADER.toLowerCase()];
  return String(direct || "").toLowerCase() !== "true";
};

// 创建axios实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000,
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore();
    const token = userStore.getToken();

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 添加用户ID头部
    if (userStore.userInfo?.id) {
      config.headers["X-User-Id"] = userStore.userInfo.id;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { data } = response;

    // 如果返回的是文件流，直接返回
    if (response.config.responseType === "blob") {
      return response;
    }

    // 兼容两类后端返回：标准包装结构与直接业务对象
    if (
      data?.code === 200 ||
      data?.success === true ||
      typeof data?.code === "undefined"
    ) {
      return data.data || data;
    } else {
      if (shouldShowGlobalError(response.config)) {
        ElMessage.error(data.message || "请求失败");
      }
      return Promise.reject(new Error(data.message || "请求失败"));
    }
  },
  (error) => {
    const { status, data } = error.response || {};
    const showGlobalError = shouldShowGlobalError(error.config);

    switch (status) {
      case 401:
        if (showGlobalError) {
          ElMessage.error("登录已过期，请重新登录");
        }
        useUserStore().clearUserInfo();
        window.location.href = "/login";
        break;
      case 403:
        if (showGlobalError) {
          ElMessage.error("没有权限访问该资源");
        }
        break;
      case 404:
        if (showGlobalError) {
          ElMessage.error("请求的资源不存在");
        }
        break;
      case 500:
        if (showGlobalError) {
          ElMessage.error("服务器内部错误");
        }
        break;
      default:
        if (showGlobalError) {
          ElMessage.error(data?.message || "网络错误，请稍后重试");
        }
    }

    return Promise.reject(error);
  },
);

export default request;
