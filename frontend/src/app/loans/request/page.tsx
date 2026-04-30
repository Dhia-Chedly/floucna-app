'use client';

import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function LoanRequestPage() {
  const { user } = useAuth();
  const router = useRouter();

  const [form, setForm] = useState({
    amount: '',
    durationDays: '30',
    purpose: '',
    declaredIncome: '',
    declaredExistingDebt: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState<string | null>(null);

  const set =
    (key: keyof typeof form) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
      setForm(prev => ({ ...prev, [key]: e.target.value }));
    };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const loan = await api.createLoan({
        amount: Number(form.amount),
        currency: 'TND',
        durationDays: Number(form.durationDays),
        purpose: form.purpose,
        declaredIncome: form.declaredIncome ? Number(form.declaredIncome) : null,
        declaredExistingDebt: form.declaredExistingDebt ? Number(form.declaredExistingDebt) : null,
      });

      setSuccess(`Submitted with demo score ${loan.demo_score} (${loan.score_label}).`);
      setTimeout(() => router.push('/dashboard'), 1800);
    } catch (err: any) {
      setError(err.message || 'Failed to submit loan request');
    } finally {
      setLoading(false);
    }
  };

  if (!user) return null;

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 720, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 28 }}>
          <button className="btn btn-ghost btn-sm" onClick={() => router.back()} style={{ marginBottom: 14 }}>
            Back
          </button>
          <h1 style={{ marginBottom: 8 }}>Submit Loan Request</h1>
          <p>Requests are reviewed by admin after KYC verification and demo scoring.</p>
        </div>

        {user.kycStatus !== 'VERIFIED' && (
          <div className="alert alert-error" style={{ marginBottom: 18 }}>
            KYC must be VERIFIED before submitting a loan request.
          </div>
        )}

        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert alert-success">{success}</div>}

        <form onSubmit={submit} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div className="form-group">
            <label className="form-label">Amount (TND)</label>
            <input className="input" type="number" min={1} value={form.amount} onChange={set('amount')} required />
          </div>

          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">Currency</label>
              <input className="input" value="TND" disabled />
            </div>
            <div className="form-group">
              <label className="form-label">Duration</label>
              <select className="input" value={form.durationDays} onChange={set('durationDays')}>
                <option value="7">7 days</option>
                <option value="14">14 days</option>
                <option value="30">30 days</option>
                <option value="60">60 days</option>
                <option value="90">90 days</option>
                <option value="180">180 days</option>
                <option value="365">365 days</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Purpose</label>
            <textarea
              className="input"
              placeholder="Medical expense, education, business equipment, etc."
              value={form.purpose}
              onChange={set('purpose')}
              required
            />
          </div>

          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">Declared Monthly Income (TND)</label>
              <input className="input" type="number" min={0} value={form.declaredIncome} onChange={set('declaredIncome')} />
            </div>
            <div className="form-group">
              <label className="form-label">Declared Existing Debt (TND)</label>
              <input className="input" type="number" min={0} value={form.declaredExistingDebt} onChange={set('declaredExistingDebt')} />
            </div>
          </div>

          <button className="btn btn-primary btn-lg btn-full" type="submit" disabled={loading || user.kycStatus !== 'VERIFIED'}>
            {loading ? <span className="spinner" /> : 'Submit Request'}
          </button>
        </form>
      </div>
    </div>
  );
}
