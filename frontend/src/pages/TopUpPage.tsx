import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button, ErrorNote, Field, PageTitle, Sheet, Stamp } from '../components/ui'
import { ApiError } from '../lib/api'
import { formatRupiah, parseAmount, validateAmount } from '../lib/format'
import { useTopUp } from '../lib/queries'
import type { Transaction } from '../lib/types'

export default function TopUpPage() {
  const topUp = useTopUp()
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [touched, setTouched] = useState(false)
  const [done, setDone] = useState<Transaction | null>(null)

  const amountError = touched ? validateAmount(amount) : null
  const fieldErrors = topUp.error instanceof ApiError ? topUp.error.fieldErrors : {}
  const generalError = Object.keys(fieldErrors).length === 0 ? topUp.error : null

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setTouched(true)
    const value = parseAmount(amount)
    if (validateAmount(amount) || value === null) return
    topUp.mutate(
      { amount: value, description: description.trim() || undefined },
      {
        onSuccess: (transaction) => {
          setDone(transaction)
          setAmount('')
          setDescription('')
          setTouched(false)
        },
      },
    )
  }

  return (
    <div className="space-y-8">
      <PageTitle>Isi saldo</PageTitle>

      {done && (
        <Sheet className="flex flex-wrap items-center justify-between gap-4 px-6 py-5">
          <div role="status">
            <p className="text-lg font-semibold">Saldo bertambah {formatRupiah(done.amount)}</p>
            <p className="text-ink-soft">
              Saldo sekarang <span className="amount font-medium text-ink">{formatRupiah(done.balanceAfter)}</span>.
              Nomor referensi {done.referenceNumber}.
            </p>
            <Link to="/riwayat" className="mt-1 inline-block text-sm font-semibold underline underline-offset-4">
              Lihat riwayat
            </Link>
          </div>
          <Stamp>Tercatat</Stamp>
        </Sheet>
      )}

      <Sheet className="max-w-lg px-6 py-7">
        <form onSubmit={onSubmit} className="space-y-5" noValidate>
          <ErrorNote error={generalError} />
          <Field
            id="amount"
            label="Nominal (Rp)"
            inputMode="decimal"
            autoComplete="off"
            placeholder="50.000"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value)
              setDone(null)
            }}
            onBlur={() => setTouched(true)}
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
          <Button type="submit" disabled={topUp.isPending}>
            {topUp.isPending ? 'Menambah saldo…' : 'Isi saldo'}
          </Button>
        </form>
      </Sheet>
    </div>
  )
}
