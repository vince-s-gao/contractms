export const extractErrorMessage = (
  error: unknown,
  fallback = "请求失败，请稍后重试",
): string => {
  if (typeof error === "string" && error.trim()) {
    return error;
  }
  if (error && typeof error === "object") {
    const e = error as {
      message?: unknown;
      response?: { data?: { message?: unknown } };
    };
    if (
      typeof e.response?.data?.message === "string" &&
      e.response.data.message.trim()
    ) {
      return e.response.data.message;
    }
    if (typeof e.message === "string" && e.message.trim()) {
      return e.message;
    }
  }
  return fallback;
};
