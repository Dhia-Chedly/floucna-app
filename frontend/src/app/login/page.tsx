'use client';

import { useAuth } from '@/lib/auth-context';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function LoginPage() {
  const { user, loading: authLoading, authMode, login, loginLocal, registerLocal } = useAuth();
  const router = useRouter();

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!authLoading && user) {
      router.replace('/dashboard');
    }
  }, [authLoading, user, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (mode === 'login') {
        await loginLocal(email, password);
      } else {
        await registerLocal(email, password, fullName);
      }
      router.replace('/dashboard');
    } catch (err: any) {
      setError(err.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex-center page" style={{ minHeight: '100vh' }}>
      <div style={{ position: 'absolute', inset: 0 }}>
        <div className="orb orb-blue" style={{ top: '10%', right: '10%', opacity: 0.1 }} />
        <div className="orb orb-orange" style={{ bottom: '10%', left: '10%', opacity: 0.1 }} />
      </div>

      <div className="card anim-scale-in" style={{ width: '100%', maxWidth: 460, position: 'relative', zIndex: 1 }}>
        <div style={{ textAlign: 'center', marginBottom: 26 }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 60, height: 60, borderRadius: 16, marginBottom: 14, objectFit: 'cover' }} />
          <h2 style={{ marginBottom: 6 }}>Access Floucna</h2>
          <p style={{ fontSize: '0.9rem' }}>Secure authentication for borrower and admin roles.</p>
        </div>

        {authMode === 'KEYCLOAK' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <button
              className="btn btn-primary btn-full btn-lg"
              onClick={async () => {
                setLoading(true);
                try {
                  await login();
                } catch (err: any) {
                  setError(err.message || 'Keycloak login failed');
                  setLoading(false);
                }
              }}
              disabled={loading}
            >
              {loading ? <span className="spinner" /> : 'Login with Keycloak'}
            </button>
            <button
              className="btn btn-ghost btn-full btn-lg"
              onClick={() => router.push('/register')}
              disabled={loading}
            >
              Create Borrower Account
            </button>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', textAlign: 'center' }}>
              If Keycloak session is active, you will be redirected automatically.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {mode === 'register' && (
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input
                  className="input"
                  type="text"
                  value={fullName}
                  onChange={e => setFullName(e.target.value)}
                  placeholder="Jane Doe"
                  required
                  autoComplete="name"
                />
              </div>
            )}
            <div className="form-group">
              <label className="form-label">Email</label>
              <input
                className="input"
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
                autoComplete="email"
              />
            </div>
            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                className="input"
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder={mode === 'register' ? 'Min 8 chars, upper, lower, digit' : ''}
                required
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
            </div>
            <button
              className="btn btn-primary btn-full btn-lg"
              type="submit"
              disabled={loading}
              style={{ marginTop: 4 }}
            >
              {loading ? <span className="spinner" /> : mode === 'login' ? 'Sign in' : 'Create account'}
            </button>
            <p style={{ textAlign: 'center', fontSize: '0.88rem', color: 'var(--text-muted)', margin: 0 }}>
              {mode === 'login' ? (
                <>No account?{' '}
                  <button type="button" className="btn btn-ghost" style={{ padding: '0 4px', fontSize: 'inherit' }} onClick={() => { setMode('register'); setError(''); }}>
                    Register
                  </button>
                </>
              ) : (
                <>Already registered?{' '}
                  <button type="button" className="btn btn-ghost" style={{ padding: '0 4px', fontSize: 'inherit' }} onClick={() => { setMode('login'); setError(''); }}>
                    Sign in
                  </button>
                </>
              )}
            </p>
          </form>
        )}

        {error && <div className="alert alert-error" style={{ marginTop: 14 }}>{error}</div>}
      </div>
    </div>
  );
}
