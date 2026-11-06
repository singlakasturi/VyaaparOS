import { RouterProvider } from 'react-router-dom'
import { router } from './routes'
import { AuthProvider } from './services/AuthProvider'
import { SalespersonProvider } from './services/SalespersonProvider'
import PwaExperience from './components/common/PwaExperience.jsx'

function App() {
  return (
    <AuthProvider>
      <SalespersonProvider>
        <PwaExperience />
        <RouterProvider router={router} />
      </SalespersonProvider>
    </AuthProvider>
  )
}

export default App
