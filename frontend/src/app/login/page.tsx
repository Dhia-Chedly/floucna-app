'use client';
import { useState, Suspense } from 'react';
import { useAuth } from '@/lib/auth-context';
import { useRouter, useSearchParams } from 'next/navigation';

function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handle = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      await login(email, password);
      router.replace('/dashboard');
    } catch (err: any) {
      setError(err.message);
    } finally { setLoading(false); }
  };

  return (
    <form onSubmit={handle} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-group">
        <label className="form-label">Email Address</label>
        <input id="login-email" className="input" type="email" placeholder="you@example.com" value={email} onChange={e => setEmail(e.target.value)} required />
      </div>
      <div className="form-group">
        <label className="form-label">Password</label>
        <input id="login-password" className="input" type="password" placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} required />
      </div>
      <button id="login-submit" className="btn btn-primary btn-full btn-lg" type="submit" disabled={loading}>
        {loading ? <span className="spinner" /> : 'Sign In'}
      </button>
    </form>
  );
}

export default function LoginPage() {
  const router = useRouter();
  return (
    <div className="flex-center page" style={{ minHeight: '100vh' }}>
      <div style={{ position: 'absolute', inset: 0 }}>
        <div className="orb orb-blue" style={{ top: '10%', right: '10%', opacity: 0.1 }} />
        <div className="orb orb-orange" style={{ bottom: '10%', left: '10%', opacity: 0.1 }} />
      </div>
      <div className="card anim-scale-in" style={{ width: '100%', maxWidth: 420, position: 'relative', zIndex: 1 }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 60, height: 60, borderRadius: 16, marginBottom: 16, objectFit: 'cover' }} />
          <h2 style={{ marginBottom: 6 }}>Welcome back</h2>
          <p style={{ fontSize: '0.9rem' }}>Sign in to your Floucna account</p>
        </div>
        <Suspense>
          <LoginForm />
        </Suspense>
        <div className="divider" />
        <p style={{ textAlign: 'center', fontSize: '0.9rem' }}>
          No account?{' '}
          <button className="btn btn-ghost btn-sm" onClick={() => router.push('/register')}>Create one →</button>
        </p>
      </div>
    </div>
  );
}
