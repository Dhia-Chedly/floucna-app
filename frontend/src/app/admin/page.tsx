'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

export default function AdminPage() {
  const [stats, setStats] = useState<any>(null);
  const [kyc, setKyc] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<Record<string, string>>({});
  const router = useRouter();

  useEffect(() => {
    Promise.all([api.adminStats(), api.kycPending()])
      .then(([s, k]) => { setStats(s); setKyc(k); })
      .finally(() => setLoading(false));
  }, []);

  const approve = async (userId: string, ok: boolean) => {
    try {
      await api.approveKyc(userId, ok);
      setMsg(m => ({ ...m, [userId]: ok ? '✅ Approved' : '❌ Rejected' }));
      setKyc(k => k.filter(u => u.userId !== userId));
    } catch (e: any) { setMsg(m => ({ ...m, [userId]: '✗ ' + e.message })); }
  };

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 36 }}>
          <h1 style={{ marginBottom: 6 }}>Admin <span style={{ background: 'var(--grad-brand)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>Control Center</span></h1>
          <p>Platform overview, KYC approvals, and compliance tools.</p>
        </div>

        {/* Platform Stats */}
        {stats && (
          <div className="grid-4" style={{ marginBottom: 40 }}>
            {[
              { label: 'Total Users', value: stats.totalUsers, color: '#F5A623' },
              { label: 'Verified Users', value: stats.verifiedUsers, color: '#22C55E' },
              { label: 'Pending KYC', value: stats.pendingKyc, color: '#F59E0B' },
              { label: 'Active Loans', value: stats.activeLoans, color: '#1B7FD4' },
            ].map(s => (
              <div key={s.label} className="stat-card">
                <div className="stat-value" style={{ color: s.color }}>{s.value ?? 0}</div>
                <div className="stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        )}

        {/* Quick nav */}
        <div className="grid-3" style={{ marginBottom: 40 }}>
          {[
            { icon: '👥', label: 'All Users', href: '/admin/users', desc: 'Browse and manage registered users' },
            { icon: '🔍', label: 'Compliance', href: '/admin/compliance', desc: 'Verify digital contract signatures' },
            { icon: '📋', label: 'Audit Logs', href: '/admin/audit', desc: 'Full audit trail of all events' },
          ].map(c => (
            <div key={c.label} className="card" style={{ cursor: 'pointer' }} onClick={() => router.push(c.href)}>
              <div style={{ fontSize: '2rem', marginBottom: 12 }}>{c.icon}</div>
              <h3 style={{ marginBottom: 6 }}>{c.label}</h3>
              <p style={{ fontSize: '0.88rem' }}>{c.desc}</p>
              <div style={{ marginTop: 16, color: 'var(--orange)', fontSize: '0.9rem', fontWeight: 600 }}>Open →</div>
            </div>
          ))}
        </div>

        {/* KYC Queue */}
        <div className="card">
          <div style={{ marginBottom: 20 }}>
            <h3>KYC Approval Queue</h3>
            <p style={{ fontSize: '0.88rem', marginTop: 4 }}>{kyc.length} pending verification{kyc.length !== 1 ? 's' : ''}</p>
          </div>

          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[1,2,3].map(i => <div key={i} className="skeleton" style={{ height: 56 }} />)}
            </div>
          ) : kyc.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--text-muted)' }}>
              <div style={{ fontSize: '2rem', marginBottom: 8 }}>✅</div>
              <p>No pending KYC submissions.</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>User</th><th>Email</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>
                  {kyc.map(u => (
                    <tr key={u.userId}>
                      <td style={{ fontWeight: 600 }}>{u.fullName}</td>
                      <td style={{ color: 'var(--text-secondary)' }}>{u.email}</td>
                      <td><span className="badge badge-amber">PENDING</span></td>
                      <td>
                        {msg[u.userId] ? (
                          <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>{msg[u.userId]}</span>
                        ) : (
                          <div style={{ display: 'flex', gap: 8 }}>
                            <button className="btn btn-sm" style={{ background: 'rgba(34,197,94,0.15)', color: '#22C55E', border: '1px solid rgba(34,197,94,0.3)' }}
                              onClick={() => approve(u.userId, true)}>✓ Approve</button>
                            <button className="btn btn-danger btn-sm" onClick={() => approve(u.userId, false)}>✗ Reject</button>
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
