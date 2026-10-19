import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, buttonClass, ErrorNote, Field, Notice, PageTitle, Sheet, Stamp } from '../components/ui'
import { ApiError } from '../lib/api'
import { formatRupiah, parseAmount, validateAmount } from '../lib/format'
import { useTransfer, useWallet } from '../lib/queries'
import type { Transaction } from '../lib/types'

interface Draft {
  recipientEmail: string
  amount: number
  description?: string
  /** Satu kunci per transfer yang direncanakan; dipakai ulang bila pengiriman diulang. */
  idempotencyKey: string
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function TransferPage() {
  const wallet = useWallet()
  const transfer = useTransfer()

  const [recipient, setRecipient] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [touched, setTouched] = useState(false)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [receipt, setReceipt] = useState<Transaction | null>(null)

  const recipientError = touched && !EMAIL_PATTERN.test(recipient.trim()) ? 'Tulis email penerima yang valid.' : null
  const amountError = touched ? validateAmount(amount) : null
  const fieldErrors = transfer.error instanceof ApiError ? transfer.error.fieldErrors : {}
  const generalError = Object.keys(fieldErrors).length === 0 ? transfer.error : null

  function onReview(event: FormEvent) {
    event.preventDefault()
    setTouched(true)
    const value = parseAmount(amount)
    if (!EMAIL_PATTERN.test(recipient.trim()) || validateAmount(amount) || value === null) return
    transfer.reset()
    setReceipt(null)
    setDraft({
      recipientEmail: recipient.trim(),
      amount: value,
      description: description.trim() || undefined,
      idempotencyKey: crypto.randomUUID(),
    })
  }

  function onConfirm() {
    if (!draft) return
    const { idempotencyKey, ...input } = draft
    transfer.mutate(
      { input, idempotencyKey },
      {
        onSuccess: (transaction) => {
          setReceipt(transaction)
          setDraft(null)
          setRecipient('')
          setAmount('')
          setDescription('')
          setTouched(false)
        },
      },
    )
  }

  function onEdit() {
    // Mengubah isi berarti transfer baru, jadi kunci lama dibuang.
    transfer.reset()
    setDraft(null)
  }

  if (receipt) {
    return (
      <div className="space-y-6">
        <PageTitle>Kirim uang</PageTitle>
        <Sheet className="max-w-lg px-6 py-7">
          <div className="flex items-start justify-between gap-4">
            <div role="status">
              <p className="text-lg font-semibold">Transfer berhasil</p>
              <p className="amount mt-1 font-display text-4xl font-semibold">{formatRupiah(receipt.amount)}</p>
            </div>
            <Stamp>Tercatat</Stamp>
          </div>
          <p className="mt-4 text-ink-soft">
            Saldo sekarang <span className="amount font-semibold text-ink">{formatRupiah(receipt.balanceAfter)}</span>
          </p>
          <p className="text-sm text-ink-soft">Nomor referensi {receipt.referenceNumber}</p>
        </Sheet>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => setReceipt(null)}>Kirim lagi</Button>
          <Link to="/riwayat" className={buttonClass('quiet')}>
            Lihat riwayat
          </Link>
        </div>
      </div>
    )
  }

  if (draft) {
    const insufficient = wallet.data ? draft.amount > wallet.data.balance : false
    return (
      <div className="space-y-6">
        <PageTitle>Periksa sebelum mengirim</PageTitle>
        <Sheet className="max-w-lg divide-y divide-rule">
          <Row label="Penerima" value={draft.recipientEmail} />
          <Row label="Nominal" value={formatRupiah(draft.amount)} strong />
          {draft.description && <Row label="Catatan" value={draft.description} />}
          {wallet.data && <Row label="Saldo kamu" value={formatRupiah(wallet.data.balance)} />}
        </Sheet>

        <div className="max-w-lg space-y-4">
          {insufficient && <Notice>Saldo kamu kurang untuk transfer ini.</Notice>}
          <ErrorNote error={generalError} />

          <div className="flex flex-wrap gap-3">
            <Button onClick={onConfirm} disabled={transfer.isPending}>
              {transfer.isPending ? 'Mengirim…' : 'Kirim sekarang'}
            </Button>
            <Button variant="quiet" onClick={onEdit} disabled={transfer.isPending}>
              Ubah
            </Button>
          </div>
          <p className="text-sm text-ink-soft">
            Jika koneksi terputus saat mengirim, tekan “Kirim sekarang” lagi. Transfer yang sama tidak akan terkirim dua
            kali.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <PageTitle>Kirim uang</PageTitle>
      <Sheet className="max-w-lg px-6 py-7">
        <form onSubmit={onReview} className="space-y-5" noValidate>
          <Field
            id="recipient"
            label="Email penerima"
            type="email"
            autoComplete="off"
            value={recipient}
            onChange={(e) => setRecipient(e.target.value)}
            error={recipientError ?? fieldErrors.recipientEmail}
          />
          <Field
            id="amount"
            label="Nominal (Rp)"
            inputMode="decimal"
            autoComplete="off"
            placeholder="50.000"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            error={amountError ?? fieldErrors.amount}
          />
          <Field
            id="description"
            label="Catatan (boleh dikosongkan)"
            maxLength={255}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            error={fieldErrors.description}
          />
          <Button type="submit">Lanjut</Button>
        </form>
      </Sheet>
    </div>
  )
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-6 px-6 py-4">
      <span className="text-ink-soft">{label}</span>
      <span className={`text-right ${strong ? 'amount font-display text-2xl font-semibold' : 'font-medium'}`}>
        {value}
      </span>
    </div>
  )
}
