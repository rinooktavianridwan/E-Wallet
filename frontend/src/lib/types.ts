// Bentuk respons backend (lihat DTO di backend/src/main/java).

export interface AccessTokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface User {
  id: number
  email: string
  name: string
  createdAt: string
}

export interface Wallet {
  id: number
  balance: number
  active: boolean
  updatedAt: string
}

export type TransactionType = 'CREDIT' | 'DEBIT'
export type TransactionCategory = 'TOP_UP' | 'TRANSFER' | 'WITHDRAW'

export interface Transaction {
  id: string
  referenceNumber: string
  type: TransactionType
  category: TransactionCategory
  amount: number
  balanceAfter: number
  description: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RegisterInput {
  name: string
  email: string
  password: string
}

export interface TransferInput {
  recipientEmail: string
  amount: number
  description?: string
}

export interface TopUpInput {
  amount: number
  description?: string
}
