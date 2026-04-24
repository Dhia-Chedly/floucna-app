'use client';
import { useAuth } from '@/lib/auth-context';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';

function ScoreRing({ score }: { score: number }) {
  const pct = Math.min(score, 100);
  return (
    <div style={{
      width: 100, height: 100, borderRadius: '50%',
      background: `conic-gradient(#F5A623 ${pct * 3.6}deg, var(--surface-2) 0deg)`,
      display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative',
    }}>
      <div style={{
        position: 'absolute', inset: 8, borderRadius: '50%', background: 'var(--surface)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column',
      }}>
        <span style={{ fontWeight: 800, fontSize: '1.4rem', fontFamily: 'Space Grotesk,sans-serif', color: '#F5A623' }}>{score}</span>
        <span style={{ fontSize: '0.6rem', color: 'var(--text-muted)' }}>SCORE</span>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [loans, setLoans] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) { router.replace('/login'); return; }
    const load = async () => {
      try {
        if (user.role === 'BORROWER') setLoans(await api.myLoans());
      } catch {}
      finally { setLoading(false); }
    };
    load();
  }, [user]);

  if (!user) return null;

  const statusColor = (s: string) =>
    s === 'ACTIVE' ? 'badge-green' : s === 'FUNDED' ? 'badge-blue' : s === 'PENDING' ? 'badge-amber' : 'badge-red';

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        {/* Header */}
        <div className="flex-between" style={{ marginBottom: 40 }}>
          <div>
            <h1 style={{ fontSize: '2rem', marginBottom: 6 }}>Welcome back, <span style={{ background: 'linear-gradient(135deg,#F5A623,#E84300)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>{user.fullName.split(' ')[0]}</span></h1>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span className={`badge ${user.isVerified ? 'badge-green' : 'badge-amber'}`}>
                {user.isVerified ? '✓ Verified' : '⚠ Pending Verification'}
              </span>
              <span className="badge badge-blue">{user.role}</span>
            </div>
          </div>
          <ScoreRing score={user.floucnaScore ?? 0} />
        </div>

        {/* Stats row */}
        <div className="grid-4" style={{ marginBottom: 40 }}>
          <div className="stat-card">
            <div className="stat-value" style={{ color: '#F5A623' }}>{user.floucnaScore ?? 0}</div>
            <div className="stat-label">Floucna Score</div>
          </div>
          <div className="stat-card">
            <div className="stat-value" style={{ color: '#1B7FD4' }}>
              {user.riskCeiling ? `DZD ${user.riskCeiling.toLocaleString()}` : '—'}
            </div>
            <div className="stat-label">Risk Ceiling</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{loans.length}</div>
            <div className="stat-label">Total Loans</div>
          </div>
          <div className="stat-card">
            <div className="stat-value" style={{ color: '#22C55E' }}>
              {loans.filter(l => l.status === 'ACTIVE').length}
            </div>
            <div className="stat-label">Active Loans</div>
          </div>
        </div>

        {/* KYC Banner */}
        {!user.isVerified && (
          <div style={{
            background: 'linear-gradient(135deg, rgba(245,166,35,0.12), rgba(232,67,0,0.12))',
            border: '1px solid rgba(245,166,35,0.3)',
            borderRadius: 'var(--radius-lg)', padding: '24px 28px',
            marginBottom: 32, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16,
          }}>
            <div>
              <div style={{ fontWeight: 700, marginBottom: 4 }}>🪪 Complete Identity Verification</div>
              <p style={{ fontSize: '0.9rem', margin: 0 }}>Upload your ID and selfie to unlock borrowing and lending features.</p>
            </div>
            <button className="btn btn-primary" onClick={() => router.push('/kyc')}>Start KYC →</button>
          </div>
        )}

        {/* My Loans */}
        {user.role === 'BORROWER' && (
          <div className="card">
            <div className="flex-between" style={{ marginBottom: 20 }}>
              <h3>My Loan Requests</h3>
              <button className="btn btn-primary btn-sm" onClick={() => router.push('/loans/request')}>+ New Request</button>
            </div>
            {loading ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {[1,2,3].map(i => <div key={i} className="skeleton" style={{ height: 56 }} />)}
              </div>
            ) : loans.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted)' }}>
                <div style={{ fontSize: '2rem', marginBottom: 12 }}>📋</div>
                <p>No loan requests yet. Create your first request to get funded.</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Amount</th><th>Purpose</th><th>Status</th><th>Funded</th><th>Action</th></tr></thead>
                  <tbody>
                    {loans.map(l => (
                      <tr key={l.id}>
                        <td style={{ fontWeight: 600 }}>DZD {Number(l.amount).toLocaleString()}</td>
                        <td style={{ color: 'var(--text-secondary)' }}>{l.purpose}</td>
                        <td><span className={`badge ${statusColor(l.status)}`}>{l.status}</span></td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div className="progress-bar" style={{ width: 80 }}>
                              <div className="progress-fill" style={{ width: `${l.funding_pct}%` }} />
                            </div>
                            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{Math.round(l.funding_pct)}%</span>
                          </div>
                        </td>
                        <td>
                          <button className="btn btn-ghost btn-sm" onClick={() => router.push(`/loans/${l.id}`)}>View</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {user.role === 'LENDER' && (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <div style={{ fontSize: '3rem', marginBottom: 16 }}>💼</div>
            <h3 style={{ marginBottom: 8 }}>Ready to invest?</h3>
            <p style={{ marginBottom: 24 }}>Browse the marketplace to find loan requests that match your profile.</p>
            <button className="btn btn-blue btn-lg" onClick={() => router.push('/marketplace')}>Browse Marketplace →</button>
          </div>
        )}
      </div>
    </div>
  );
}
