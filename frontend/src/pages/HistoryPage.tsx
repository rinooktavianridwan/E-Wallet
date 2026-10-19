import { useState } from 'react'
import { LedgerTable } from '../components/LedgerTable'
import { Button, ErrorNote, PageTitle } from '../components/ui'
import { useTransactions } from '../lib/queries'

const PAGE_SIZE = 10

export default function HistoryPage() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError, error, isPlaceholderData } = useTransactions(page, PAGE_SIZE)

  return (
    <div className="space-y-6">
      <PageTitle>Riwayat</PageTitle>

      {isPending && <p className="text-ink-soft">Memuat riwayat…</p>}
      <ErrorNote error={isError ? error : null} />

      {data && data.content.length === 0 && (
        <div className="page px-6 py-12 text-center">
          <p className="font-display text-xl font-semibold">Belum ada transaksi</p>
          <p className="mt-1 text-ink-soft">Catatan akan muncul di sini setelah kamu mengisi saldo atau mengirim uang.</p>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className={isPlaceholderData ? 'opacity-60 transition-opacity' : ''}>
            <LedgerTable entries={data.content} />
          </div>
          <nav aria-label="Halaman riwayat" className="flex items-center justify-between gap-3">
            <Button variant="quiet" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Sebelumnya
            </Button>
            <p className="text-sm font-medium text-ink-soft">
              Halaman {data.page + 1} dari {Math.max(data.totalPages, 1)}
            </p>
            <Button
              variant="quiet"
              disabled={page + 1 >= data.totalPages || isPlaceholderData}
              onClick={() => setPage((p) => p + 1)}
            >
              Berikutnya
            </Button>
          </nav>
        </>
      )}
    </div>
  )
}
