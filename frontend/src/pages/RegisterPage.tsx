import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api, ApiError } from '../lib/api'
import { Button, ErrorNote, Field } from '../components/ui'

export default function RegisterPage() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  // Pesan umum hanya ditampilkan jika tidak ada pesan per kolom
  const generalError = Object.keys(fieldErrors).length === 0 ? error : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api.register({ name: name.trim(), email: email.trim(), password })
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (e) {
      setError(e)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-5" noValidate>
      <div>
        <h1 className="text-3xl font-semibold">Buat akun</h1>
        <p className="mt-1 text-ink-soft">Dompetmu dibuat otomatis begitu akun jadi.</p>
      </div>
      <ErrorNote error={generalError} />
      <Field
        id="name"
        label="Nama"
        autoComplete="name"
        required
        maxLength={100}
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={fieldErrors.name}
      />
      <Field
        id="email"
        label="Email"
        type="email"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
      />
      <Field
        id="password"
        label="Kata sandi"
        type="password"
        autoComplete="new-password"
        required
        minLength={8}
        maxLength={72}
        hint="8 sampai 72 karakter."
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
      />
      <Button type="submit" className="w-full" disabled={submitting || !name || !email || !password}>
        {submitting ? 'Membuat akun…' : 'Buat akun'}
      </Button>
      <p className="text-sm text-ink-soft">
        Sudah punya akun?{' '}
        <Link to="/masuk" className="font-semibold text-ink underline underline-offset-4">
          Masuk
        </Link>
      </p>
    </form>
  )
}
