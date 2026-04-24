'use client';
import { useState, Suspense } from 'react';
import { useAuth } from '@/lib/auth-context';
import { useRouter, useSearchParams } from 'next/navigation';

function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [form, setForm] = useState({ email: '', password: '', fullName: '', role: params.get('role') || 'BORROWER' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  const handle = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      await register(form.email, form.password, form.fullName, form.role);
      router.replace('/kyc');
    } catch (err: any) { setError(err.message); }
    finally { setLoading(false); }
  };

  return (
    <form onSubmit={handle} style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="form-group">
        <label className="form-label">I want to</label>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          {(['BORROWER', 'LENDER'] as const).map(r => (
            <button key={r} type="button" onClick={() => setForm(f => ({ ...f, role: r }))}
              style={{
                padding: '14px', borderRadius: 'var(--radius-md)', border: '2px solid',
                borderColor: form.role === r ? 'var(--orange)' : 'var(--border)',
                background: form.role === r ? 'rgba(245,166,35,0.1)' : 'var(--surface-2)',
                color: form.role === r ? 'var(--orange)' : 'var(--text-secondary)',
                fontWeight: 600, cursor: 'pointer', transition: 'all 0.2s', fontSize: '0.9rem',
              }}>
              {r === 'BORROWER' ? '🧑 Borrow Money' : '💰 Lend Money'}
            </button>
          ))}
        </div>
      </div>

      <div className="form-group">
        <label className="form-label">Full Name</label>
        <input id="reg-name" className="input" placeholder="Ahmed Benali" value={form.fullName} onChange={set('fullName')} required />
      </div>
      <div className="form-group">
        <label className="form-label">Email Address</label>
        <input id="reg-email" className="input" type="email" placeholder="you@example.com" value={form.email} onChange={set('email')} required />
      </div>
      <div className="form-group">
        <label className="form-label">Password</label>
        <input id="reg-password" className="input" type="password" placeholder="Min. 8 characters" value={form.password} onChange={set('password')} minLength={8} required />
      </div>

      <button id="reg-submit" className="btn btn-primary btn-full btn-lg" type="submit" disabled={loading}>
        {loading ? <span className="spinner" /> : 'Create Account'}
      </button>
    </form>
  );
}

export default function RegisterPage() {
  const router = useRouter();
  return (
    <div className="flex-center page" style={{ minHeight: '100vh' }}>
      <div style={{ position: 'absolute', inset: 0 }}>
        <div className="orb orb-orange" style={{ top: '10%', left: '5%', opacity: 0.1 }} />
        <div className="orb orb-blue"   style={{ bottom: '10%', right: '5%', opacity: 0.1 }} />
      </div>
      <div className="card anim-scale-in" style={{ width: '100%', maxWidth: 460, position: 'relative', zIndex: 1 }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 60, height: 60, borderRadius: 16, marginBottom: 14, objectFit: 'cover' }} />
          <h2 style={{ marginBottom: 6 }}>Join Floucna</h2>
          <p style={{ fontSize: '0.9rem' }}>Create your secure account in seconds</p>
        </div>
        <Suspense>
          <RegisterForm />
        </Suspense>
        <div className="divider" />
        <p style={{ textAlign: 'center', fontSize: '0.9rem' }}>
          Already have an account?{' '}
          <button className="btn btn-ghost btn-sm" onClick={() => router.push('/login')}>Sign in →</button>
        </p>
      </div>
    </div>
  );
}
