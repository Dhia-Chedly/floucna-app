'use client';

import { useAuth } from '@/lib/auth-context';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function RegisterPage() {
  const { user, loading: authLoading, authMode, register } = useAuth();
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!authLoading && user) {
      router.replace('/dashboard');
    }
  }, [authLoading, user, router]);

  if (authMode !== 'KEYCLOAK') {
    return (
      <div className="flex-center page" style={{ minHeight: '100vh' }}>
        <div className="card" style={{ width: '100%', maxWidth: 460 }}>
          <h2 style={{ marginBottom: 8 }}>Registration Unavailable</h2>
          <p style={{ marginBottom: 18 }}>
            Self-signup is available only in KEYCLOAK mode.
          </p>
          <button className="btn btn-primary btn-full" onClick={() => router.push('/login')}>
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-center page" style={{ minHeight: '100vh' }}>
      <div className="card anim-scale-in" style={{ width: '100%', maxWidth: 460 }}>
        <div style={{ textAlign: 'center', marginBottom: 22 }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 60, height: 60, borderRadius: 16, marginBottom: 14, objectFit: 'cover' }} />
          <h2 style={{ marginBottom: 6 }}>Create Borrower Account</h2>
          <p style={{ fontSize: '0.9rem' }}>
            Continue to Keycloak registration, then you will return to your dashboard.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <button
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            onClick={async () => {
              setError('');
              setLoading(true);
              try {
                await register();
              } catch (err: any) {
                setError(err.message || 'Registration redirect failed');
                setLoading(false);
              }
            }}
          >
            {loading ? <span className="spinner" /> : 'Register with Keycloak'}
          </button>

          <button className="btn btn-ghost btn-full" disabled={loading} onClick={() => router.push('/login')}>
            Back to Login
          </button>
        </div>

        {error && <div className="alert alert-error" style={{ marginTop: 14 }}>{error}</div>}
      </div>
    </div>
  );
}
