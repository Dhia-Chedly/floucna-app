'use client';

import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function DashboardPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [loans, setLoans] = useState<any[]>([]);
  const [loadingLoans, setLoadingLoans] = useState(true);

  useEffect(() => {
    if (!loading && !user) {
      router.replace('/login');
      return;
    }

    if (user?.role === 'BORROWER') {
      api
        .myLoans()
        .then(setLoans)
        .catch(() => setLoans([]))
        .finally(() => setLoadingLoans(false));
    } else {
      setLoadingLoans(false);
    }
  }, [user, loading, router]);

  if (loading || !user) return null;

  const kycBadgeClass =
    user.kycStatus === 'VERIFIED'
      ? 'badge-green'
      : user.kycStatus === 'UNDER_REVIEW' || user.kycStatus === 'SESSION_CREATED'
        ? 'badge-amber'
        : 'badge-red';

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 34 }}>
          <h1 style={{ marginBottom: 6 }}>
            Welcome,{' '}
            <span
              style={{
                background: 'linear-gradient(135deg,#F5A623,#E84300)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              {user.fullName.split(' ')[0]}
            </span>
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span className={`badge ${kycBadgeClass}`}>KYC: {user.kycStatus}</span>
            <span className="badge badge-blue">{user.role}</span>
          </div>
        </div>

        {user.role === 'BORROWER' ? (
          <>
            {user.kycStatus !== 'VERIFIED' && (
              <div
                style={{
                  background: 'linear-gradient(135deg, rgba(245,166,35,0.12), rgba(232,67,0,0.12))',
                  border: '1px solid rgba(245,166,35,0.3)',
                  borderRadius: 'var(--radius-lg)',
                  padding: '24px 28px',
                  marginBottom: 30,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 16,
                  flexWrap: 'wrap',
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, marginBottom: 4 }}>Complete KYC to submit loan requests</div>
                  <p style={{ fontSize: '0.9rem', margin: 0 }}>Start identity verification through Didit or local fallback mode.</p>
                </div>
                <button className="btn btn-primary" onClick={() => router.push('/kyc')}>
                  Start KYC
                </button>
              </div>
            )}

            <div className="grid-3" style={{ marginBottom: 30 }}>
              <div className="stat-card">
                <div className="stat-value">{loans.length}</div>
                <div className="stat-label">My Loan Requests</div>
              </div>
              <div className="stat-card">
                <div className="stat-value" style={{ color: '#22C55E' }}>{loans.filter(l => l.status === 'APPROVED').length}</div>
                <div className="stat-label">Approved</div>
              </div>
              <div className="stat-card">
                <div className="stat-value" style={{ color: '#F59E0B' }}>{loans.filter(l => l.status === 'SUBMITTED').length}</div>
                <div className="stat-label">Pending Review</div>
              </div>
            </div>

            <div className="card">
              <div className="flex-between" style={{ marginBottom: 18, flexWrap: 'wrap', gap: 12 }}>
                <h3>My Loans</h3>
                <button className="btn btn-primary btn-sm" onClick={() => router.push('/loans/request')}>
                  + New Loan Request
                </button>
              </div>

              {loadingLoans ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {[1, 2, 3].map(i => (
                    <div key={i} className="skeleton" style={{ height: 52 }} />
                  ))}
                </div>
              ) : loans.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '36px 0', color: 'var(--text-muted)' }}>
                  No loan requests yet.
                </div>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Amount</th>
                        <th>Duration</th>
                        <th>Score</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {loans.map(loan => (
                        <tr key={loan.id}>
                          <td style={{ fontWeight: 600 }}>
                            {loan.currency} {Number(loan.amount).toLocaleString()}
                          </td>
                          <td>{loan.duration_days} days</td>
                          <td>
                            {loan.demo_score} <span style={{ color: 'var(--text-muted)' }}>({loan.score_label})</span>
                          </td>
                          <td>
                            <span className={`badge ${loan.status === 'APPROVED' ? 'badge-green' : loan.status === 'REJECTED' ? 'badge-red' : 'badge-amber'}`}>
                              {loan.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="grid-3">
            {[
              { label: 'Admin Overview', href: '/admin', desc: 'Platform KPIs and KYC queue' },
              { label: 'Loan Review', href: '/admin/loans', desc: 'Approve or reject submitted requests' },
              { label: 'Contracts', href: '/admin/contracts', desc: 'Generate, sign, and timestamp agreements' },
              { label: 'Compliance', href: '/admin/compliance', desc: 'Verify uploaded signed contracts' },
              { label: 'Audit Logs', href: '/admin/audit', desc: 'Review security and workflow events' },
            ].map(card => (
              <div key={card.label} className="card" style={{ cursor: 'pointer' }} onClick={() => router.push(card.href)}>
                <h3 style={{ marginBottom: 8 }}>{card.label}</h3>
                <p style={{ fontSize: '0.9rem' }}>{card.desc}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
