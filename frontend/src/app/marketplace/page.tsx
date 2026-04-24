'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { useRouter } from 'next/navigation';

export default function MarketplacePage() {
  const [loans, setLoans] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const { user } = useAuth();
  const router = useRouter();
  const [pledging, setPledging] = useState<string | null>(null);
  const [amounts, setAmounts] = useState<Record<string, number>>({});
  const [msg, setMsg] = useState<Record<string, string>>({});

  useEffect(() => {
    api.marketplace().then(setLoans).finally(() => setLoading(false));
  }, []);

  const filtered = loans.filter(l =>
    !filter || l.purpose?.toLowerCase().includes(filter.toLowerCase()) ||
    l.borrower_name?.toLowerCase().includes(filter.toLowerCase())
  );

  const doPledge = async (loanId: string) => {
    setPledging(loanId);
    try {
      const res = await api.pledge(loanId, amounts[loanId] || 0);
      setMsg(m => ({ ...m, [loanId]: res.fullyFunded ? '🎉 Fully Funded! Contract generating...' : `✓ Pledged! Now at ${Math.round(res.fundingPct)}%` }));
      const updated = await api.marketplace();
      setLoans(updated);
    } catch (e: any) {
      setMsg(m => ({ ...m, [loanId]: '✗ ' + e.message }));
    } finally { setPledging(null); }
  };

  const riskColor = (score: number) =>
    score >= 70 ? '#22C55E' : score >= 40 ? '#F59E0B' : '#EF4444';

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        {/* Header */}
        <div style={{ marginBottom: 36 }}>
          <h1 style={{ marginBottom: 8 }}>Loan Marketplace</h1>
          <p>Browse verified borrower requests and fund the ones you believe in.</p>
        </div>

        {/* Search */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 32, flexWrap: 'wrap' }}>
          <input
            className="input"
            placeholder="🔍  Search by purpose or borrower..."
            style={{ flex: 1, minWidth: 240 }}
            value={filter}
            onChange={e => setFilter(e.target.value)}
          />
          <div className="badge badge-blue" style={{ padding: '0 16px', fontSize: '0.85rem' }}>
            {filtered.length} requests available
          </div>
        </div>

        {loading ? (
          <div className="grid-3">
            {[1,2,3,4,5,6].map(i => <div key={i} className="skeleton" style={{ height: 280, borderRadius: 'var(--radius-lg)' }} />)}
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 0', color: 'var(--text-muted)' }}>
            <div style={{ fontSize: '3rem', marginBottom: 16 }}>🏪</div>
            <p>No open loan requests found.</p>
          </div>
        ) : (
          <div className="grid-3">
            {filtered.map(loan => (
              <div key={loan.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {/* Borrower + score */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontWeight: 600, marginBottom: 2 }}>{loan.borrower_name}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Verified Borrower</div>
                  </div>
                  <div style={{
                    width: 48, height: 48, borderRadius: '50%',
                    background: `conic-gradient(${riskColor(loan.score ?? 0)} ${(loan.score ?? 0) * 3.6}deg, var(--surface-2) 0deg)`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative',
                  }}>
                    <div style={{ position: 'absolute', inset: 5, borderRadius: '50%', background: 'var(--surface)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <span style={{ fontSize: '0.7rem', fontWeight: 800, color: riskColor(loan.score ?? 0) }}>{loan.score ?? 0}</span>
                    </div>
                  </div>
                </div>

                {/* Amount + purpose */}
                <div>
                  <div style={{ fontSize: '1.6rem', fontWeight: 800, fontFamily: 'Space Grotesk,sans-serif', color: 'var(--text-primary)' }}>
                    DZD {Number(loan.amount).toLocaleString()}
                  </div>
                  <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginTop: 4 }}>{loan.purpose || 'General Purpose'}</div>
                </div>

                {/* Details */}
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <span className="badge badge-orange">{loan.duration_days}d</span>
                  <span className="badge badge-blue">{loan.interest_rate}% rate</span>
                </div>

                {/* Progress */}
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: '0.8rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Funded</span>
                    <span style={{ fontWeight: 600 }}>{Math.round(loan.funding_pct || 0)}%</span>
                  </div>
                  <div className="progress-bar">
                    <div className="progress-fill" style={{ width: `${loan.funding_pct || 0}%` }} />
                  </div>
                </div>

                {/* Pledge */}
                {user?.role === 'LENDER' && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                    <input
                      className="input"
                      type="number"
                      placeholder="Amount DZD"
                      style={{ flex: 1 }}
                      value={amounts[loan.id] || ''}
                      onChange={e => setAmounts(a => ({ ...a, [loan.id]: Number(e.target.value) }))}
                    />
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => doPledge(loan.id)}
                      disabled={pledging === loan.id || !amounts[loan.id]}
                    >
                      {pledging === loan.id ? <span className="spinner" style={{ width: 14, height: 14 }} /> : 'Fund'}
                    </button>
                  </div>
                )}
                {msg[loan.id] && (
                  <div className={`alert ${msg[loan.id].startsWith('✗') ? 'alert-error' : 'alert-success'}`} style={{ margin: 0, fontSize: '0.82rem' }}>
                    {msg[loan.id]}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
