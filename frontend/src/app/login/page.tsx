'use client';

import { useAuth } from '@/lib/auth-context';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useState } from 'react';

export default function LoginPage() {
  const { user, loading: authLoading, authMode, login, setLocalToken, clearLocalToken } = useAuth();
  const router = useRouter();

  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!authLoading && user) {
      router.replace('/dashboard');
    }
  }, [authLoading, user, router]);

  const handleLocalLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await setLocalToken(token.trim());
      router.replace('/dashboard');
    } catch (err: any) {
      setError(err.message || 'Invalid token');
      clearLocalToken();
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
          <form onSubmit={handleLocalLogin} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="alert alert-info" style={{ marginBottom: 0 }}>
              LOCAL mode is enabled. Paste a JWT-like token with `sub`, `email`, and optional `role`/`roles` claims.
            </div>
            <div className="form-group">
              <label className="form-label">Local Bearer Token</label>
              <textarea
                className="input"
                value={token}
                onChange={e => setToken(e.target.value)}
                placeholder="eyJhbGciOi..."
                style={{ minHeight: 120, fontFamily: 'monospace', fontSize: '0.8rem' }}
                required
              />
            </div>
            <button className="btn btn-primary btn-full btn-lg" type="submit" disabled={loading || !token.trim()}>
              {loading ? <span className="spinner" /> : 'Continue'}
            </button>
          </form>
        )}

        {error && <div className="alert alert-error" style={{ marginTop: 14 }}>{error}</div>}
      </div>
    </div>
  );
}
