import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button } from './ui'

const links = [
  { to: '/', label: 'Beranda', end: true },
  { to: '/top-up', label: 'Isi saldo', end: false },
  { to: '/transfer', label: 'Kirim uang', end: false },
  { to: '/riwayat', label: 'Riwayat', end: false },
]

function Mark({ className = '' }: { className?: string }) {
  // Logo: buku kecil dengan dua garis, seperti dua sisi catatan
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <rect x="3" y="3" width="26" height="26" rx="3" fill="currentColor" />
      <path d="M10 11h12M10 16h12M10 21h7" stroke="#e0a122" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  )
}

/** Pembungkus halaman yang butuh login. Selama sesi dipulihkan, tidak menampilkan apa pun. */
export function ProtectedLayout() {
  const { status, user, logout } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="grid min-h-screen place-items-center text-ink-soft" role="status">
        Membuka buku kas…
      </div>
    )
  }
  if (status === 'anonymous') {
    return <Navigate to="/masuk" replace state={{ from: location.pathname }} />
  }

  return (
    <div className="mx-auto min-h-screen max-w-4xl px-4 pb-20 sm:px-6">
      <header className="flex items-center justify-between gap-4 py-5">
        <div className="flex items-center gap-2.5">
          <Mark className="size-8 text-cover" />
          <p className="font-display text-2xl font-semibold">Buku Kas</p>
        </div>
        <div className="flex items-center gap-3">
          <span className="hidden text-sm font-medium text-ink-soft sm:inline">{user?.name}</span>
          <Button variant="quiet" small onClick={() => void logout()}>
            Keluar
          </Button>
        </div>
      </header>

      <nav aria-label="Menu utama" className="mb-8 flex gap-1 overflow-x-auto border-b-2 border-rule">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) =>
              `-mb-0.5 whitespace-nowrap border-b-4 px-4 py-2.5 text-[15px] font-semibold transition-colors ${
                isActive
                  ? 'border-stamp text-ink'
                  : 'border-transparent text-ink-soft hover:border-rule hover:text-ink'
              }`
            }
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      <main>
        <Outlet />
      </main>
    </div>
  )
}

/** Halaman masuk dan daftar: sampul di satu sisi, formulir di sisi lain. */
export function PublicLayout() {
  const { status } = useAuth()
  if (status === 'loading') {
    return (
      <div className="grid min-h-screen place-items-center text-ink-soft" role="status">
        Memuat…
      </div>
    )
  }
  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }
  return (
    <div className="grid min-h-screen md:grid-cols-[5fr_6fr]">
      <aside className="cover flex flex-col justify-between gap-12 px-6 py-8 sm:px-10 md:py-12">
        <div className="flex items-center gap-2.5">
          <Mark className="size-9 text-cover-ink" />
          <p className="font-display text-2xl font-semibold">Buku Kas</p>
        </div>
        <div className="max-w-md">
          <p className="font-display text-4xl font-semibold leading-tight sm:text-5xl">
            Setiap rupiah tercatat dua kali.
          </p>
          <p className="mt-4 text-cover-ink/75">
            Saat kamu mengirim uang, satu catatan keluar di bukumu dan satu catatan masuk di buku penerima.
          </p>
        </div>
      </aside>
      <div className="flex items-center justify-center px-4 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
