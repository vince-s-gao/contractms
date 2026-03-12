import request from "./index";

const silentErrorHeaders = {
  "X-Silent-Error-Message": "true",
};

// 合同相关API接口
export interface Contract {
  id?: string;
  contractNo: string;
  contractName: string;
  contractType: string;
  amount: number;
  status: string;
  startDate: string;
  endDate: string;
  partyName: string;
  partyContact: string;
  partyPhone: string;
  ourSignatory: string;
  contractSummary?: string;
  contractTerms?: string;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export interface ContractUpsertPayload {
  contractNo: string;
  contractName: string;
  contractType: string;
  amount?: number;
  taxRate?: number;
  startDate: string;
  endDate: string;
  description?: string;
  partyName?: string;
  partyContact?: string;
  partyPhone?: string;
  createdBy?: string | number;
}

export interface ContractQueryParams {
  page?: number;
  size?: number;
  keyword?: string;
  customerName?: string;
  signingYear?: number;
  signingYears?: string;
  contractNo?: string;
  contractName?: string;
  contractType?: string;
  status?: string;
  partyName?: string;
  startDate?: string;
  endDate?: string;
  ownerId?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export interface ContractExportParams {
  fields?: string;
  keyword?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
}

export interface ContractTypeItem {
  code: string;
  name: string;
}

export interface ContractOverviewItem {
  code: string;
  name: string;
  count: number;
}

export interface TopCustomerRevenueItem {
  rank: number;
  customerName: string;
  contractCount: number;
  revenue: number;
}

export interface ContractOverview {
  totalContracts: number;
  approvingContracts: number;
  activeContracts: number;
  newThisMonth: number;
  salesRevenue: number;
  purchaseCost: number;
  top5CustomerRevenueShare?: number;
  contractTypeStats?: ContractOverviewItem[];
  topCustomerRevenue?: TopCustomerRevenueItem[];
}

export interface ContractAttachment {
  id: number;
  name: string;
  size: number;
  fileType?: string;
  uploadTime: string;
}

export interface ApprovalQueryParams {
  status?: string;
  approvalStatus?: string;
  page?: number;
  size?: number;
}

export interface ApprovalTaskItem {
  id: string | number;
  contractId: string | number;
  contractNo?: string;
  contractName?: string;
  contractType?: string;
  amount?: number;
  approvalStatus?: string;
  applicantName?: string;
  createdAt?: string;
  description?: string;
}

// 获取合同列表
export const getContracts = (params: ContractQueryParams) => {
  return request({
    url: "/contracts",
    method: "get",
    params,
  });
};

// 根据ID获取合同详情
export const getContractById = (id: string) => {
  return request({
    url: `/contracts/${id}`,
    method: "get",
  });
};

// 根据合同编号获取合同详情
export const getContractByNo = (contractNo: string) => {
  return request({
    url: `/contracts/no/${contractNo}`,
    method: "get",
  });
};

// 创建合同
export const createContract = (data: ContractUpsertPayload) => {
  return request({
    url: "/contracts",
    method: "post",
    data,
  });
};

// 更新合同
export const updateContract = (id: string, data: ContractUpsertPayload) => {
  return request({
    url: `/contracts/${id}`,
    method: "put",
    data,
  });
};

// 删除合同
export const deleteContract = (id: string) => {
  return request({
    url: `/contracts/${id}`,
    method: "delete",
  });
};

// 提交合同审批
export const submitForApproval = (id: string) => {
  return request({
    url: `/contracts/${id}/submit`,
    method: "post",
  });
};

// 审批合同
export const approveContract = (
  id: string,
  approved: boolean,
  comment?: string,
) => {
  return request({
    url: `/contracts/${id}/approve`,
    method: "post",
    params: { approved, comment },
  });
};

// 更新合同状态
export const updateContractStatus = (id: string, status: string) => {
  return request({
    url: `/contracts/${id}/status`,
    method: "put",
    params: { status },
  });
};

// 查询即将到期合同
export const getExpiringContracts = (days: number = 30) => {
  return request({
    url: "/contracts/expiring",
    method: "get",
    params: { days },
  });
};

// 统计合同金额
export const getTotalAmount = (status?: string, ownerId?: string) => {
  return request({
    url: "/contracts/statistics/amount",
    method: "get",
    params: { status, ownerId },
  });
};

// 检查合同编号唯一性
export const checkContractNoUnique = (
  contractNo: string,
  excludeId?: string,
) => {
  return request({
    url: "/contracts/check-unique",
    method: "get",
    params: { contractNo, excludeId },
  });
};

// 导出合同（Excel）
export const exportContracts = (params: ContractExportParams) => {
  return request({
    url: "/contracts/export",
    method: "get",
    params,
    responseType: "blob",
  });
};

// 批量导入合同（Excel）
export const importContracts = (formData: FormData, overwrite = false) => {
  return request({
    url: `/contracts/import?overwrite=${overwrite}`,
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
};

export const getContractTypes = () => {
  return request({
    url: "/contracts/types",
    method: "get",
  });
};

export const getSigningYears = () => {
  return request({
    url: "/contracts/signing-years",
    method: "get",
  });
};

export const getContractOverview = (params?: { year?: number }) => {
  return request({
    url: "/contracts/statistics/overview",
    method: "get",
    params,
  });
};

export const getApprovalTasks = (params: ApprovalQueryParams) => {
  return request({
    url: "/contracts/approvals",
    method: "get",
    params,
  });
};

export const createContractType = (data: { code: string; name: string }) => {
  return request({
    url: "/contracts/types",
    method: "post",
    data,
    headers: silentErrorHeaders,
    timeout: 30000,
  });
};

export const updateContractType = (
  code: string,
  data: { code?: string; name?: string },
) => {
  return request({
    url: `/contracts/types/${encodeURIComponent(code)}`,
    method: "put",
    data,
    headers: silentErrorHeaders,
    timeout: 30000,
  });
};

export const deleteContractType = (code: string) => {
  return request({
    url: `/contracts/types/${encodeURIComponent(code)}`,
    method: "delete",
    headers: silentErrorHeaders,
    timeout: 30000,
  });
};

export const getContractAttachments = (contractId: string | number) => {
  return request({
    url: `/contracts/${contractId}/attachments`,
    method: "get",
  });
};

export const uploadContractAttachments = (
  contractId: string | number,
  formData: FormData,
) => {
  return request({
    url: `/contracts/${contractId}/attachments`,
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
};

export const downloadContractAttachment = (
  contractId: string | number,
  attachmentId: string | number,
) => {
  return request({
    url: `/contracts/${contractId}/attachments/${attachmentId}/download`,
    method: "get",
    responseType: "blob",
  });
};

export const deleteContractAttachment = (
  contractId: string | number,
  attachmentId: string | number,
) => {
  return request({
    url: `/contracts/${contractId}/attachments/${attachmentId}`,
    method: "delete",
  });
};
