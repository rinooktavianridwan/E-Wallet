import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedLayout, PublicLayout } from './components/Layout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import TopUpPage from './pages/TopUpPage'
import TransferPage from './pages/TransferPage'
import HistoryPage from './pages/HistoryPage'

export default function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/masuk" element={<LoginPage />} />
        <Route path="/daftar" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="/top-up" element={<TopUpPage />} />
        <Route path="/transfer" element={<TransferPage />} />
        <Route path="/riwayat" element={<HistoryPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
