import { useEffect, useState } from 'react'
import axios, { type AxiosRequestConfig } from 'axios'

type Message = {
  type: 'error' | 'success' | 'info'
  text: string
}

type RequestLog = {
  id: number
  label: string
  status: number | 'ERROR'
  body: string
}

type SignupForm = {
  username: string
  password: string
  nickname: string
}

type LoginForm = {
  username: string
  password: string
}

const api = axios.create({
  withCredentials: true,
})

const initialSignupForm: SignupForm = {
  username: '',
  password: '',
  nickname: '',
}

const initialLoginForm: LoginForm = {
  username: '',
  password: '',
}

function stringifyPayload(payload: unknown) {
  if (typeof payload === 'string') {
    return payload
  }

  if (payload == null) {
    return 'No response body'
  }

  return JSON.stringify(payload, null, 2)
}

function App() {
  const [accessToken, setAccessToken] = useState<string | null>(localStorage.getItem('accessToken'))
  const [signupForm, setSignupForm] = useState<SignupForm>(initialSignupForm)
  const [loginForm, setLoginForm] = useState<LoginForm>(initialLoginForm)
  const [message, setMessage] = useState<Message | null>(null)
  const [requestLogs, setRequestLogs] = useState<RequestLog[]>([])
  const [isLoading, setIsLoading] = useState<string | null>(null)

  useEffect(() => {
    const hash = window.location.hash
    if (!hash.includes('accessToken=')) {
      return
    }

    const params = new URLSearchParams(hash.slice(1))
    const tokenFromHash = params.get('accessToken')

    if (tokenFromHash) {
      setAccessToken(tokenFromHash)
      localStorage.setItem('accessToken', tokenFromHash)
      setMessage({ type: 'success', text: 'Google OAuth2 login completed. Access token saved.' })
      pushLog('OAuth2 callback', 200, {
        accessToken: tokenFromHash,
      })
    }

    window.history.replaceState(null, '', window.location.pathname)
  }, [])

  const pushLog = (label: string, status: number | 'ERROR', body: unknown) => {
    setRequestLogs((current) => [
      {
        id: Date.now() + current.length,
        label,
        status,
        body: stringifyPayload(body),
      },
      ...current,
    ].slice(0, 8))
  }

  const runRequest = async <T,>(
    label: string,
    request: AxiosRequestConfig,
    onSuccess?: (data: T) => void,
  ) => {
    setIsLoading(label)
    setMessage(null)

    try {
      const response = await api.request<T>(request)
      pushLog(label, response.status, response.data)
      onSuccess?.(response.data)
      setMessage({ type: 'success', text: `${label} succeeded.` })
      return response.data
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        const status = error.response?.status ?? 'ERROR'
        const body = error.response?.data ?? error.message
        pushLog(label, status, body)
        setMessage({
          type: 'error',
          text: typeof body === 'string' ? body : `${label} failed.`,
        })
      } else {
        pushLog(label, 'ERROR', 'Unexpected error')
        setMessage({ type: 'error', text: `${label} failed with an unexpected error.` })
      }
      return null
    } finally {
      setIsLoading(null)
    }
  }

  const updateSignupField = (field: keyof SignupForm, value: string) => {
    setSignupForm((current) => ({ ...current, [field]: value }))
  }

  const updateLoginField = (field: keyof LoginForm, value: string) => {
    setLoginForm((current) => ({ ...current, [field]: value }))
  }

  const handleSignup = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const data = await runRequest<string>(
      'POST /signup',
      {
        method: 'POST',
        url: '/signup',
        data: signupForm,
      },
    )

    if (data) {
      setLoginForm({
        username: signupForm.username,
        password: signupForm.password,
      })
      setSignupForm(initialSignupForm)
    }
  }

  const handleLogin = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    await runRequest<{ accessToken: string }>(
      'POST /login',
      {
        method: 'POST',
        url: '/login',
        data: loginForm,
      },
      (data) => {
        setAccessToken(data.accessToken)
        localStorage.setItem('accessToken', data.accessToken)
      },
    )
  }

  const handleRefresh = async () => {
    await runRequest<{ accessToken: string }>(
      'POST /refresh',
      {
        method: 'POST',
        url: '/refresh',
      },
      (data) => {
        setAccessToken(data.accessToken)
        localStorage.setItem('accessToken', data.accessToken)
      },
    )
  }

  const handleLogout = async () => {
    await runRequest<string>(
      'POST /logout',
      {
        method: 'POST',
        url: '/logout',
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
      },
      () => {
        setAccessToken(null)
        localStorage.removeItem('accessToken')
      },
    )
  }

  const handleOAuthLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/google'
  }

  const callProtectedApi = async (label: string, url: string) => {
    await runRequest<string>(
      label,
      {
        method: 'GET',
        url,
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
      },
    )
  }

  const clearSession = () => {
    setAccessToken(null)
    localStorage.removeItem('accessToken')
    setMessage({ type: 'info', text: 'Front-end token state cleared. Refresh token cookie is unchanged.' })
    pushLog('Local session clear', 200, 'accessToken removed from localStorage')
  }

  return (
    <main className="page-shell">
      <section className="hero-panel">
        <p className="eyebrow">Spring Security Auth Flow Playground</p>
        <h1>Login Demo Console</h1>
        <p className="hero-copy">
          OAuth2, local login, refresh token rotation, logout, and protected endpoint checks in one screen.
        </p>
        {message && <div className={`message ${message.type}`}>{message.text}</div>}
      </section>

      <section className="dashboard-grid">
        <article className="card">
          <div className="card-header">
            <div>
              <p className="card-kicker">Step 1</p>
              <h2>Create local account</h2>
            </div>
            <span className="chip">POST /signup</span>
          </div>
          <form className="stack" onSubmit={handleSignup}>
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                value={signupForm.username}
                onChange={(event) => updateSignupField('username', event.target.value)}
                placeholder="tester-local"
                required
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password"
                value={signupForm.password}
                onChange={(event) => updateSignupField('password', event.target.value)}
                placeholder="local-password"
                required
              />
            </label>
            <label className="field">
              <span>Nickname</span>
              <input
                type="text"
                value={signupForm.nickname}
                onChange={(event) => updateSignupField('nickname', event.target.value)}
                placeholder="Demo User"
                required
              />
            </label>
            <button className="btn btn-primary" type="submit" disabled={isLoading === 'POST /signup'}>
              {isLoading === 'POST /signup' ? 'Creating...' : 'Create local account'}
            </button>
          </form>
        </article>

        <article className="card">
          <div className="card-header">
            <div>
              <p className="card-kicker">Step 2</p>
              <h2>Login</h2>
            </div>
            <span className="chip">POST /login</span>
          </div>
          <form className="stack" onSubmit={handleLogin}>
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                value={loginForm.username}
                onChange={(event) => updateLoginField('username', event.target.value)}
                placeholder="tester-local"
                required
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password"
                value={loginForm.password}
                onChange={(event) => updateLoginField('password', event.target.value)}
                placeholder="local-password"
                required
              />
            </label>
            <button className="btn btn-primary" type="submit" disabled={isLoading === 'POST /login'}>
              {isLoading === 'POST /login' ? 'Signing in...' : 'Sign in local'}
            </button>
          </form>
          <div className="divider">
            <span>or</span>
          </div>
          <button className="btn btn-google" type="button" onClick={handleOAuthLogin}>
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
            </svg>
            Continue with Google
          </button>
        </article>

        <article className="card card-wide">
          <div className="card-header">
            <div>
              <p className="card-kicker">Step 3</p>
              <h2>Token and endpoint checks</h2>
            </div>
            <span className="chip">POST /refresh · POST /logout</span>
          </div>
          <div className="status-strip">
            <div>
              <span className="status-label">Access Token</span>
              <strong>{accessToken ? 'Loaded' : 'Empty'}</strong>
            </div>
            <div>
              <span className="status-label">Refresh Token Cookie</span>
              <strong>HttpOnly on server side</strong>
            </div>
          </div>
          <label className="field">
            <span>Current Access Token</span>
            <textarea
              className="token-box"
              readOnly
              value={accessToken ?? 'No access token stored in localStorage yet.'}
            />
          </label>
          <div className="button-grid">
            <button className="btn btn-secondary" type="button" onClick={handleRefresh} disabled={isLoading === 'POST /refresh'}>
              {isLoading === 'POST /refresh' ? 'Refreshing...' : 'Refresh token'}
            </button>
            <button className="btn btn-danger" type="button" onClick={handleLogout} disabled={isLoading === 'POST /logout'}>
              {isLoading === 'POST /logout' ? 'Logging out...' : 'Logout'}
            </button>
            <button className="btn btn-ghost" type="button" onClick={clearSession}>
              Clear local token only
            </button>
          </div>
          <div className="endpoint-grid">
            <button className="endpoint-button" type="button" onClick={() => callProtectedApi('GET /', '/')}>
              Call `GET /`
            </button>
            <button
              className="endpoint-button"
              type="button"
              onClick={() => callProtectedApi('GET /user/profile', '/user/profile')}
            >
              Call `GET /user/profile`
            </button>
            <button
              className="endpoint-button"
              type="button"
              onClick={() => callProtectedApi('GET /admin/manage', '/admin/manage')}
            >
              Call `GET /admin/manage`
            </button>
          </div>
        </article>

        <article className="card card-wide">
          <div className="card-header">
            <div>
              <p className="card-kicker">Request log</p>
              <h2>Recent responses</h2>
            </div>
            <span className="chip">latest 8</span>
          </div>
          <div className="log-stack">
            {requestLogs.length === 0 ? (
              <div className="empty-state">Run a signup, login, refresh, logout, or protected API request to inspect responses.</div>
            ) : (
              requestLogs.map((entry) => (
                <article className="log-card" key={entry.id}>
                  <div className="log-header">
                    <strong>{entry.label}</strong>
                    <span className={`log-status ${entry.status === 'ERROR' || entry.status >= 400 ? 'error' : 'success'}`}>
                      {entry.status}
                    </span>
                  </div>
                  <pre>{entry.body}</pre>
                </article>
              ))
            )}
          </div>
        </article>
      </section>
    </main>
  )
}

export default App
