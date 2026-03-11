import request from "./index";

// 用户相关API接口
export interface User {
  id?: string;
  username: string;
  password?: string;
  email: string;
  phone?: string;
  realName: string;
  department?: string;
  position?: string;
  status?: string;
  lastLoginTime?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  user: User;
  token: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  confirmPassword: string;
}

// 用户登录
export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
};

// 用户注册
export const register = (data: RegisterRequest) => {
  return request({
    url: "/auth/register",
    method: "post",
    data,
  });
};

// 获取当前用户信息
export const getCurrentUser = () => {
  return request({
    url: "/users/current",
    method: "get",
  });
};

// 根据用户名查询用户
export const getUserByUsername = (username: string) => {
  return request({
    url: `/users/username/${username}`,
    method: "get",
  });
};

// 分页查询用户列表
export const getUsers = (params: {
  page?: number;
  size?: number;
  username?: string;
  realName?: string;
  department?: string;
  status?: string;
}) => {
  return request({
    url: "/users",
    method: "get",
    params,
  });
};

// 更新用户状态
export const updateUserStatus = (id: string, status: string) => {
  return request({
    url: `/users/${id}/status`,
    method: "put",
    params: { status },
  });
};

// 重置用户密码
export const resetPassword = (id: string, newPassword: string) => {
  return request({
    url: `/users/${id}/password`,
    method: "put",
    params: { newPassword },
  });
};

// 检查用户名唯一性
export const checkUsernameUnique = (username: string, excludeId?: string) => {
  return request({
    url: "/users/check-unique",
    method: "get",
    params: { username, excludeId },
  });
};

// 用户退出登录
export const logout = () => {
  return Promise.resolve();
};
