import request from "./index";

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

export interface ContractQueryParams {
  page?: number;
  size?: number;
  contractNo?: string;
  contractName?: string;
  contractType?: string;
  status?: string;
  partyName?: string;
  startDate?: string;
  endDate?: string;
  ownerId?: string;
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
export const createContract = (data: Contract) => {
  return request({
    url: "/contracts",
    method: "post",
    data,
  });
};

// 更新合同
export const updateContract = (id: string, data: Contract) => {
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
