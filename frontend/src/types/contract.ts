import type { ContractTypeItem } from "@/api/contract";

export interface ContractListItem {
  id: string;
  contractNumber: string;
  signingYear?: number;
  contractName: string;
  customerName?: string;
  companySignatory?: string;
  contractType: string;
  amount: number;
  status: string;
  startDate: string;
  endDate: string;
  createdBy: string;
  createdAt: string;
}

export type ContractTypeOption = ContractTypeItem;
