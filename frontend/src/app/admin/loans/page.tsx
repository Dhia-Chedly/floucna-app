'use client';

import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function AdminLoansPage() {
  const router = useRouter();
  const [loans, setLoans] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [feedback, setFeedback] = useState<Record<string, string>>({});

  const load = async () => {
    setLoading(true);
    try {
      setLoans(await api.adminLoans());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const review = async (id: string, approve: boolean) => {
    setBusy(prev => ({ ...prev, [id]: true }));
    setFeedback(prev => ({ ...prev, [id]: '' }));
    try {
      const updated = approve ? await api.approveLoan(id) : await api.rejectLoan(id);
      setFeedback(prev => ({ ...prev, [id]: approve ? '✓ Approved' : '✗ Rejected' }));
      setLoans(prev => prev.map(item => (item.id === id ? updated : item)));
    } catch (err: any) {
      setFeedback(prev => ({ ...prev, [id]: err.message || 'Action failed' }));
    } finally {
      setBusy(prev => ({ ...prev, [id]: false }));
    }
  };

  const generatePdf = async (id: string) => {
    setBusy(prev => ({ ...prev, [id]: true }));
    setFeedback(prev => ({ ...prev, [id]: '' }));
    try {
      await api.generateContract(id);
      setFeedback(prev => ({ ...prev, [id]: '✓ PDF generated — continue in Contracts' }));
    } catch (err: any) {
      setFeedback(prev => ({ ...prev, [id]: err.message || 'Failed' }));
    } finally {
      setBusy(prev => ({ ...prev, [id]: false }));
    }
  };

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ marginBottom: 8 }}>Loan Review</h1>
          <p>Review submitted borrower requests and decide approval outcomes.</p>
        </div>

        <div className="card">
          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[1, 2, 3, 4].map(i => <div key={i} className="skeleton" style={{ height: 52 }} />)}
            </div>
          ) : loans.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '26px 0' }}>No loan requests found.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Borrower</th>
                    <th>Amount</th>
                    <th>Duration</th>
                    <th>Score</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {loans.map(loan => {
                    const canReview = loan.status === 'SUBMITTED' || loan.status === 'UNDER_REVIEW';
                    return (
                      <tr key={loan.id}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{loan.borrower_name}</div>
                          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{loan.borrower_email}</div>
                        </td>
                        <td>{loan.currency} {Number(loan.amount).toLocaleString()}</td>
                        <td>{loan.duration_days} days</td>
                        <td>{loan.demo_score} ({loan.score_label})</td>
                        <td>
                          <span className={`badge ${loan.status === 'APPROVED' ? 'badge-green' : loan.status === 'REJECTED' ? 'badge-red' : 'badge-amber'}`}>
                            {loan.status}
                          </span>
                        </td>
                        <td>
                          {canReview ? (
                            <div style={{ display: 'flex', gap: 8 }}>
                              <button
                                className="btn btn-sm"
                                style={{ background: 'rgba(34,197,94,0.15)', color: '#22C55E' }}
                                disabled={!!busy[loan.id]}
                                onClick={() => review(loan.id, true)}
                              >
                                Approve
                              </button>
                              <button
                                className="btn btn-danger btn-sm"
                                disabled={!!busy[loan.id]}
                                onClick={() => review(loan.id, false)}
                              >
                                Reject
                              </button>
                            </div>
                          ) : loan.status === 'APPROVED' ? (
                            <button
                              className="btn btn-sm"
                              style={{ background: 'rgba(245,166,35,0.15)', color: '#F5A623' }}
                              disabled={!!busy[loan.id]}
                              onClick={() => generatePdf(loan.id)}
                            >
                              {busy[loan.id] ? <span className="spinner" /> : '📄 Generate PDF'}
                            </button>
                          ) : (
                            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Finalized</span>
                              <button
                                className="btn btn-sm"
                                style={{ background: 'rgba(99,102,241,0.12)', color: '#818CF8', fontSize: '0.75rem' }}
                                onClick={() => router.push('/admin/contracts')}
                              >
                                Contracts →
                              </button>
                            </div>
                          )}
                          {feedback[loan.id] && (
                            <div style={{ marginTop: 6, fontSize: '0.78rem' }}>{feedback[loan.id]}</div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
