import { useState, useEffect } from 'react'
import axios from 'axios'

// We create an axios instance to deal with our proxied API
const api = axios.create()

function App() {
  const [activeTab, setActiveTab] = useState<'login' | 'signup'>('login')
  const [token, setToken] = useState<string | null>(localStorage.getItem('accessToken'))
  
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [nickname, setNickname] = useState('')
  
  const [msg, setMsg] = useState<{type: 'error'|'success', text: string} | null>(null)

  // Initialization: check for access token in URL hash (OAuth2 callback)
  useEffect(() => {
    const hash = window.location.hash
    if (hash.includes('accessToken=')) {
      const params = new URLSearchParams(hash.substring(1)) // remove the #
      const at = params.get('accessToken')
      if (at) {
        setToken(at)
        localStorage.setItem('accessToken', at)
        setMsg({ type: 'success', text: 'Social Login Successful!' })
        // Clean up the URL securely
        window.history.replaceState(null, '', window.location.pathname)
      }
    }
  }, [])

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const res = await api.post('/login', { username, password })
      const at = res.data.accessToken
      setToken(at)
      localStorage.setItem('accessToken', at)
      setMsg({ type: 'success', text: 'Local Login Successful!' })
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setMsg({ type: 'error', text: err.response?.data?.message || 'Login failed' })
      } else {
        setMsg({ type: 'error', text: 'An unexpected error occurred' })
      }
    }
  }

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await api.post('/signup', { username, password, email, nickname })
      setMsg({ type: 'success', text: 'Signup successful! You can now login.' })
      setActiveTab('login')
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setMsg({ type: 'error', text: err.response?.data?.message || 'Signup failed' })
      } else {
        setMsg({ type: 'error', text: 'An unexpected error occurred' })
      }
    }
  }

  const handleLogout = async () => {
    try {
      // Pass the token as Authorization header if you implemented Bearer token check
      await api.post('/logout', {}, { headers: { Authorization: `Bearer ${token}` } })
    } catch (err) {
      console.error(err)
    } finally {
      setToken(null)
      localStorage.removeItem('accessToken')
      setMsg({ type: 'success', text: 'Logged out successfully.' })
    }
  }

  const handleOAuthLogin = () => {
    // Redirect directly to the Spring Boot OAuth2 endpoint
    window.location.href = 'http://localhost:8080/oauth2/authorization/google'
  }

  if (token) {
    return (
      <div className="container">
        <h1>Dashboard ✨</h1>
        {msg && <div className={`message ${msg.type}`}>{msg.text}</div>}
        <div className="form-group">
          <label>Access Token:</label>
          <div className="token-box">{token}</div>
        </div>
        <button className="btn btn-danger" onClick={handleLogout}>
          Logout
        </button>
      </div>
    )
  }

  return (
    <div className="container">
      <h1>Welcome 👋</h1>
      {msg && <div className={`message ${msg.type}`}>{msg.text}</div>}
      
      <div className="tabs">
        <button 
          className={`tab ${activeTab === 'login' ? 'active' : ''}`}
          onClick={() => setActiveTab('login')}
        >
          Login
        </button>
        <button 
          className={`tab ${activeTab === 'signup' ? 'active' : ''}`}
          onClick={() => setActiveTab('signup')}
        >
          Sign up
        </button>
      </div>

      {activeTab === 'login' && (
        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label>Username</label>
            <input type="text" placeholder="Enter username" value={username} onChange={e => setUsername(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input type="password" placeholder="Enter password" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
          <button type="submit" className="btn btn-primary">Sign In Local</button>
        </form>
      )}

      {activeTab === 'signup' && (
        <form onSubmit={handleSignup}>
          <div className="form-group">
            <label>Username</label>
            <input type="text" placeholder="Desired username" value={username} onChange={e => setUsername(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input type="password" placeholder="Must be secure" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Email</label>
            <input type="email" placeholder="example@email.com" value={email} onChange={e => setEmail(e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Nickname</label>
            <input type="text" placeholder="Your display name" value={nickname} onChange={e => setNickname(e.target.value)} required />
          </div>
          <button type="submit" className="btn btn-primary">Create Account</button>
        </form>
      )}

      <div className="divider">OR</div>

      <button className="btn btn-google" onClick={handleOAuthLogin}>
        <svg width="20" height="20" viewBox="0 0 24 24">
          <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
          <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
          <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
          <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
        </svg>
        Continue with Google
      </button>
    </div>
  )
}

export default App
