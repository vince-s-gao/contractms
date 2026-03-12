import request from "./index";

const silentErrorHeaders = {
  "X-Silent-Error-Message": "true",
};

export interface PermissionItem {
  code: string;
  name: string;
  module?: string;
}

export interface RoleItem {
  id: number;
  roleName: string;
  roleCode: string;
  description?: string;
  permissionCodes: string[];
}

export interface UserPermissionItem {
  id: number;
  username: string;
  realName?: string;
  email?: string;
  roleId?: number;
  roleName?: string;
  roleCode?: string;
  enabled?: number;
}

export interface OperationLogItem {
  id: number;
  username: string;
  module: string;
  operationType: string;
  description: string;
  status: string;
  ipAddress?: string;
  requestMethod?: string;
  requestUrl?: string;
  errorMessage?: string;
  operationTime: string;
}

export const getSystemUsers = (keyword?: string) => {
  return request({
    url: "/system/users",
    method: "get",
    params: { keyword },
  });
};

export const updateSystemUserRole = (id: number, roleId: number) => {
  return request({
    url: `/system/users/${id}/role`,
    method: "put",
    data: { roleId },
  });
};

export const deleteSystemUser = (id: number) => {
  return request({
    url: `/system/users/${id}`,
    method: "delete",
  });
};

export const getSystemRoles = () => {
  return request({
    url: "/system/roles",
    method: "get",
  });
};

export const createSystemRole = (data: {
  roleName: string;
  roleCode: string;
  description?: string;
  permissionCodes: string[];
}) => {
  return request({
    url: "/system/roles",
    method: "post",
    data,
  });
};

export const updateSystemRole = (
  id: number,
  data: {
    roleName: string;
    roleCode: string;
    description?: string;
    permissionCodes: string[];
  },
) => {
  return request({
    url: `/system/roles/${id}`,
    method: "put",
    data,
  });
};

export const deleteSystemRole = (id: number) => {
  return request({
    url: `/system/roles/${id}`,
    method: "delete",
  });
};

export const getSystemPermissions = () => {
  return request({
    url: "/system/permissions",
    method: "get",
  });
};

export const getOperationLogs = (params: {
  page?: number;
  size?: number;
  keyword?: string;
  username?: string;
  module?: string;
  operationType?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}) => {
  return request({
    url: "/system/operation-logs",
    method: "get",
    params,
    headers: silentErrorHeaders,
  }).catch((error: { response?: { status?: number } }) => {
    if (error?.response?.status === 404) {
      return request({
        url: "/system/operationLogs",
        method: "get",
        params,
        headers: silentErrorHeaders,
      });
    }
    return Promise.reject(error);
  });
};
