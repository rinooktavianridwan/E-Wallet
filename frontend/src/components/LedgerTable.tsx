import { formatDateTime, formatRupiah } from '../lib/format'
import type { Transaction, TransactionCategory } from '../lib/types'

const categoryLabel: Record<TransactionCategory, string> = {
  TOP_UP: 'Isi saldo',
  TRANSFER: 'Transfer',
  WITHDRAW: 'Tarik tunai',
}

function titleOf(entry: Transaction) {
  return entry.description || categoryLabel[entry.category]
}

/**
 * Halaman catatan bergaya buku tabungan: kolom keluar, masuk, dan saldo berjalan
 * (saldo berjalan berasal dari balanceAfter yang dicatat backend pada tiap entri).
 * Di layar kecil, tiap catatan menjadi satu baris ringkas.
 */
export function LedgerTable({ entries }: { entries: Transaction[] }) {
  return (
    <div className="page overflow-hidden">
      {/* Layar lebar: tabel */}
      <table className="hidden w-full border-collapse text-left text-[15px] sm:table">
        <caption className="sr-only">Daftar transaksi, terbaru di atas</caption>
        <thead className="cover">
          <tr>
            <th scope="col" className="margin-line px-4 py-3 font-semibold">
              Tanggal
            </th>
            <th scope="col" className="px-4 py-3 font-semibold">
              Keterangan
            </th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">
              Keluar
            </th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">
              Masuk
            </th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">
              Saldo
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr key={entry.id} className="ledger-row align-top">
              <td className="margin-line whitespace-nowrap px-4 py-3.5 text-ink-soft">
                {formatDateTime(entry.createdAt)}
              </td>
              <td className="px-4 py-3.5">
                <span className="block font-semibold">{titleOf(entry)}</span>
                <span className="block text-xs text-ink-soft">{entry.referenceNumber}</span>
              </td>
              <td className="amount px-4 py-3.5 text-right font-medium text-debit">
                {entry.type === 'DEBIT' ? formatRupiah(entry.amount) : ''}
              </td>
              <td className="amount px-4 py-3.5 text-right font-medium text-credit">
                {entry.type === 'CREDIT' ? formatRupiah(entry.amount) : ''}
              </td>
              <td className="amount px-4 py-3.5 text-right font-semibold">{formatRupiah(entry.balanceAfter)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Layar kecil: daftar ringkas */}
      <ul className="sm:hidden">
        {entries.map((entry) => {
          const debit = entry.type === 'DEBIT'
          return (
            <li key={entry.id} className="ledger-row flex items-start justify-between gap-3 px-4 py-3.5">
              <div className="min-w-0">
                <p className="truncate font-semibold">{titleOf(entry)}</p>
                <p className="text-xs text-ink-soft">{formatDateTime(entry.createdAt)}</p>
              </div>
              <div className="text-right">
                <p className={`amount font-semibold ${debit ? 'text-debit' : 'text-credit'}`}>
                  {debit ? '−' : '+'}
                  {formatRupiah(entry.amount)}
                </p>
                <p className="amount text-xs text-ink-soft">Saldo {formatRupiah(entry.balanceAfter)}</p>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
