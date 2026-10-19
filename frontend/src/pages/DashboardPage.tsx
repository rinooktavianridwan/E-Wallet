import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { LedgerTable } from '../components/LedgerTable'
import { buttonClass, ErrorNote, Notice } from '../components/ui'
import { formatDateTime, formatRupiah } from '../lib/format'
import { useTransactions, useWallet } from '../lib/queries'

export default function DashboardPage() {
  const { user } = useAuth()
  const wallet = useWallet()
  const recent = useTransactions(0, 5)

  return (
    <div className="space-y-10">
      <section className="cover rounded-sm px-6 py-8 sm:px-10 sm:py-12" aria-label="Saldo">
        <p className="text-cover-ink/75">Saldo {user?.name}</p>

        {wallet.isPending ? (
          <p className="mt-2 font-display text-6xl text-cover-ink/40" role="status">
            …
          </p>
        ) : wallet.isError ? (
          <div className="mt-4 text-ink">
            <ErrorNote error={wallet.error} />
          </div>
        ) : (
          <>
            <p className="amount mt-2 font-display text-5xl font-semibold sm:text-6xl">
              {formatRupiah(wallet.data.balance)}
            </p>
            <p className="mt-2 text-sm text-cover-ink/65">Diperbarui {formatDateTime(wallet.data.updatedAt)}</p>
          </>
        )}

        <div className="mt-8 flex flex-wrap gap-3">
          <Link to="/top-up" className={buttonClass('stamp')}>
            Isi saldo
          </Link>
          <Link to="/transfer" className={buttonClass('onCover')}>
            Kirim uang
          </Link>
        </div>
      </section>

      {wallet.data && !wallet.data.active && (
        <Notice>Dompet ini sedang tidak aktif, jadi belum bisa dipakai untuk transaksi.</Notice>
      )}

      <section aria-labelledby="recent-heading">
        <div className="mb-4 flex items-baseline justify-between">
          <h2 id="recent-heading" className="text-2xl font-semibold">
            Catatan terakhir
          </h2>
          <Link to="/riwayat" className="text-sm font-semibold underline underline-offset-4">
            Lihat semua
          </Link>
        </div>

        {recent.isPending && <p className="text-ink-soft">Memuat catatan…</p>}
        {recent.isError && <ErrorNote error={recent.error} />}
        {recent.data && recent.data.content.length === 0 && (
          <div className="page px-6 py-12 text-center">
            <p className="font-display text-xl font-semibold">Halaman ini masih kosong</p>
            <p className="mt-1 text-ink-soft">
              Isi saldo dulu, dan catatan pertamamu akan muncul di sini.
            </p>
            <Link to="/top-up" className={`${buttonClass('primary')} mt-5`}>
              Isi saldo
            </Link>
          </div>
        )}
        {recent.data && recent.data.content.length > 0 && <LedgerTable entries={recent.data.content} />}
      </section>
    </div>
  )
}
