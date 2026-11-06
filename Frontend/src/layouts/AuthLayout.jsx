import { Outlet } from 'react-router-dom'
import { Brand } from '../components/common/Navigation'

function AuthLayout() {
  return (
    <div className="auth-layout">
      <header className="auth-header">
        <Brand />
      </header>
      <main className="auth-content">
        <Outlet />
      </main>
      <footer className="auth-footer">Built for better wholesale trade.</footer>
    </div>
  )
}

export default AuthLayout
