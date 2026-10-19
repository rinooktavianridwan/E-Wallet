import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import { ApiError } from '../lib/api'

type Variant = 'primary' | 'quiet' | 'stamp' | 'onCover'

/** Kelas tombol, dipakai bersama oleh <Button> dan <Link> yang tampil seperti tombol. */
export function buttonClass(variant: Variant = 'primary', small = false) {
  const styles: Record<Variant, string> = {
    primary: 'bg-cover text-cover-ink hover:bg-cover-soft disabled:bg-cover/50',
    quiet: 'border border-rule bg-sheet text-ink hover:border-ink-soft disabled:text-ink-soft',
    stamp: 'bg-stamp text-ink hover:brightness-95',
    onCover: 'border border-cover-ink/40 text-cover-ink hover:bg-cover-ink/10',
  }
  const size = small ? 'px-3.5 py-1.5 text-sm' : 'px-5 py-3 text-base'
  return `inline-flex items-center justify-center rounded-sm font-semibold transition-colors disabled:cursor-not-allowed ${size} ${styles[variant]}`
}

export function Button({
  variant = 'primary',
  small = false,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; small?: boolean }) {
  return <button className={`${buttonClass(variant, small)} ${className}`} {...props} />
}

export function Field({
  label,
  error,
  hint,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string | null; hint?: string; id: string }) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-semibold">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`w-full rounded-sm border-2 bg-sheet px-3.5 py-3 text-base placeholder:text-ink-soft/60 focus-visible:border-cover focus-visible:outline-none ${
          error ? 'border-debit' : 'border-rule'
        }`}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="text-sm font-medium text-debit">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-sm text-ink-soft">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

/** Menampilkan pesan error dari backend apa adanya; pesan validasi per kolom ditampilkan di bawah kolomnya. */
export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const message =
    error instanceof ApiError
      ? error.message
      : 'Tidak bisa terhubung ke server. Periksa koneksi lalu coba lagi.'
  return (
    <p role="alert" className="border-l-4 border-debit bg-debit/10 px-3.5 py-2.5 text-sm font-medium text-debit">
      {message}
    </p>
  )
}

export function Notice({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="border-l-4 border-stamp bg-stamp/15 px-3.5 py-2.5 text-sm font-medium">
      {children}
    </p>
  )
}

/** Halaman buku: kertas terang dengan bayangan keras. */
export function Sheet({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`page ${className}`}>{children}</section>
}

/** Cap "tercatat" yang menempel saat transaksi berhasil. */
export function Stamp({ children }: { children: ReactNode }) {
  return <span className="stamp text-credit">{children}</span>
}

export function PageTitle({ children }: { children: ReactNode }) {
  return <h1 className="text-3xl font-semibold">{children}</h1>
}
