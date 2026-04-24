'use client';
import { useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

export default function LoanRequestPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [form, setForm] = useState({ amount: '', purpose: '', durationDays: '30', interestRate: '5' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  const handle = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      await api.createLoan({
        amount: Number(form.amount),
        purpose: form.purpose,
        durationDays: Number(form.durationDays),
        interestRate: Number(form.interestRate),
      });
      setSuccess(true);
      setTimeout(() => router.push('/dashboard'), 2000);
    } catch (err: any) { setError(err.message); }
    finally { setLoading(false); }
  };

  if (success) return (
    <div className="page flex-center" style={{ minHeight: '100vh', flexDirection: 'column', gap: 20 }}>
      <div style={{ fontSize: '4rem' }}>🎉</div>
      <h2>Loan Request Submitted!</h2>
      <p>Your request is now live on the marketplace. Lenders can start funding it immediately.</p>
    </div>
  );

  const maxAmount = user?.riskCeiling ?? 0;

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 640, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 32 }}>
          <button className="btn btn-ghost btn-sm" onClick={() => router.back()} style={{ marginBottom: 16 }}>← Back</button>
          <h1 style={{ marginBottom: 8 }}>Request a Loan</h1>
          <p>Fill in the details below. Your request will be validated against your Floucna Score.</p>
        </div>

        {/* Risk ceiling info */}
        {user?.isVerified && (
          <div className="alert alert-info" style={{ marginBottom: 24 }}>
            💡 Your current Risk Ceiling is <strong>DZD {maxAmount.toLocaleString()}</strong>. You cannot request more than this amount.
          </div>
        )}

        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={handle} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div className="form-group">
            <label className="form-label">Loan Amount (DZD)</label>
            <input
              id="loan-amount"
              className="input"
              type="number"
              placeholder="e.g. 10000"
              value={form.amount}
              onChange={set('amount')}
              min={100}
              max={maxAmount || undefined}
              required
            />
            {form.amount && maxAmount > 0 && (
              <div style={{ marginTop: 8 }}>
                <div className="progress-bar">
                  <div className="progress-fill" style={{ width: `${Math.min((Number(form.amount) / maxAmount) * 100, 100)}%` }} />
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  {Math.round((Number(form.amount) / maxAmount) * 100)}% of your risk ceiling
                </div>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label">Purpose of Loan</label>
            <textarea
              id="loan-purpose"
              className="input"
              placeholder="e.g. Purchase laptop for university, Emergency medical expenses..."
              value={form.purpose}
              onChange={set('purpose')}
              required
            />
          </div>

          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">Duration</label>
              <select id="loan-duration" className="input" value={form.durationDays} onChange={set('durationDays')}>
                <option value="7">7 days</option>
                <option value="14">14 days</option>
                <option value="30">30 days</option>
                <option value="60">60 days</option>
                <option value="90">90 days</option>
                <option value="180">6 months</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Interest Rate (%)</label>
              <input
                id="loan-rate"
                className="input"
                type="number"
                step="0.5"
                min="1"
                max="20"
                value={form.interestRate}
                onChange={set('interestRate')}
              />
            </div>
          </div>

          {/* Summary */}
          {form.amount && (
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--radius-md)', padding: '16px 20px' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 8 }}>Loan Summary</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
                <span>Principal</span><span style={{ fontWeight: 600 }}>DZD {Number(form.amount).toLocaleString()}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem', marginTop: 4 }}>
                <span>Total Repayment</span>
                <span style={{ fontWeight: 600, color: 'var(--orange)' }}>
                  DZD {(Number(form.amount) * (1 + Number(form.interestRate) / 100)).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                </span>
              </div>
            </div>
          )}

          <button id="loan-submit" className="btn btn-primary btn-lg btn-full" type="submit" disabled={loading || !user?.isVerified}>
            {!user?.isVerified ? 'Complete KYC First' : loading ? <><span className="spinner" /> Submitting...</> : 'Submit Loan Request →'}
          </button>
        </form>
      </div>
    </div>
  );
}
