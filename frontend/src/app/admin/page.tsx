'use client';

import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function AdminPage() {
  const router = useRouter();
  const [stats, setStats] = useState<any>(null);
  const [pendingKyc, setPendingKyc] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<Record<string, string>>({});

  const load = async () => {
    setLoading(true);
    try {
      const [s, k] = await Promise.all([api.adminStats(), api.kycPending()]);
      setStats(s);
      setPendingKyc(k);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const reviewKyc = async (id: string, approve: boolean) => {
    try {
      if (approve) {
        await api.approveKyc(id);
      } else {
        await api.rejectKyc(id);
      }
      setMessage(prev => ({ ...prev, [id]: approve ? 'Approved' : 'Rejected' }));
      setPendingKyc(prev => prev.filter(item => item.id !== id));
    } catch (err: any) {
      setMessage(prev => ({ ...prev, [id]: err.message || 'Failed' }));
    }
  };

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ marginBottom: 8 }}>Admin Control Center</h1>
          <p>Manage KYC, loan reviews, contracts, compliance checks, and audit visibility.</p>
        </div>

        {stats && (
          <div className="grid-4" style={{ marginBottom: 34 }}>
            {[
              { label: 'Total Users', value: stats.totalUsers, color: '#1B7FD4' },
              { label: 'KYC Verified', value: stats.kycVerified, color: '#22C55E' },
              { label: 'Submitted Loans', value: stats.submittedLoans, color: '#F59E0B' },
              { label: 'Contracts', value: stats.contractsGenerated, color: '#F5A623' },
            ].map(item => (
              <div key={item.label} className="stat-card">
                <div className="stat-value" style={{ color: item.color }}>{item.value ?? 0}</div>
                <div className="stat-label">{item.label}</div>
              </div>
            ))}
          </div>
        )}

        <div className="grid-3" style={{ marginBottom: 34 }}>
          {[
            { label: 'Loan Review', href: '/admin/loans', desc: 'Approve or reject borrower requests' },
            { label: 'Contracts', href: '/admin/contracts', desc: 'Generate, sign, and timestamp agreements' },
            { label: 'Compliance', href: '/admin/compliance', desc: 'Run verification and view reports' },
            { label: 'Audit Logs', href: '/admin/audit', desc: 'Trace platform and security actions' },
          ].map(card => (
            <div key={card.label} className="card" style={{ cursor: 'pointer' }} onClick={() => router.push(card.href)}>
              <h3 style={{ marginBottom: 6 }}>{card.label}</h3>
              <p style={{ fontSize: '0.88rem' }}>{card.desc}</p>
            </div>
          ))}
        </div>

        <div className="card">
          <div style={{ marginBottom: 18 }}>
            <h3>KYC Review Queue</h3>
            <p style={{ fontSize: '0.86rem', marginTop: 4 }}>{pendingKyc.length} pending submissions</p>
          </div>

          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[1, 2, 3].map(i => (
                <div key={i} className="skeleton" style={{ height: 52 }} />
              ))}
            </div>
          ) : pendingKyc.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '28px 0' }}>No pending KYC records.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Email</th>
                    <th>Mode</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingKyc.map(item => (
                    <tr key={item.id}>
                      <td style={{ fontWeight: 600 }}>{item.fullName}</td>
                      <td>{item.email}</td>
                      <td>{item.mode}</td>
                      <td><span className="badge badge-amber">{item.status}</span></td>
                      <td>
                        {message[item.id] ? (
                          <span>{message[item.id]}</span>
                        ) : (
                          <div style={{ display: 'flex', gap: 8 }}>
                            <button className="btn btn-sm" style={{ background: 'rgba(34,197,94,0.15)', color: '#22C55E' }} onClick={() => reviewKyc(item.id, true)}>
                              Approve
                            </button>
                            <button className="btn btn-danger btn-sm" onClick={() => reviewKyc(item.id, false)}>
                              Reject
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
