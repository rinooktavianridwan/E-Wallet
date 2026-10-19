import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { TopUpInput, TransferInput } from './types'

export const walletKey = ['wallet'] as const
export const transactionsKey = ['transactions'] as const

export function useWallet() {
  return useQuery({ queryKey: walletKey, queryFn: api.wallet })
}

export function useTransactions(page: number, size: number) {
  return useQuery({
    queryKey: [...transactionsKey, page, size],
    queryFn: () => api.transactions(page, size),
    placeholderData: keepPreviousData,
  })
}

// Setelah uang bergerak, saldo dan riwayat harus diambil ulang.
function useRefreshMoneyViews() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: walletKey }),
      queryClient.invalidateQueries({ queryKey: transactionsKey }),
    ])
}

export function useTopUp() {
  const refresh = useRefreshMoneyViews()
  return useMutation({
    mutationFn: (input: TopUpInput) => api.topUp(input),
    onSuccess: refresh,
  })
}

export function useTransfer() {
  const refresh = useRefreshMoneyViews()
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: TransferInput; idempotencyKey: string }) =>
      api.transfer(input, idempotencyKey),
    onSuccess: refresh,
  })
}
