'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';

const ACTION_COLORS: Record<string, string> = {
  REGISTER: 'badge-blue',
  LOGIN: 'badge-blue',
  KYC_SUBMIT: 'badge-amber',
  KYC_APPROVE: 'badge-green',
  KYC_REJECT: 'badge-red',
  LOAN_CREATE: 'badge-orange',
  PLEDGE: 'badge-blue',
  LOAN_REPAY: 'badge-green',
  VERIFY_CONTRACT: 'badge-orange',
};

export default function AuditPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    api.auditLogs(200).then(setLogs).finally(() => setLoading(false));
  }, []);

  const filtered = logs.filter(l =>
    !filter || l.action?.includes(filter.toUpperCase()) || l.actorEmail?.includes(filter)
  );

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ marginBottom: 8 }}>Audit Trail</h1>
          <p>Complete immutable log of all platform events and actor actions.</p>
        </div>

        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
          <input
            className="input"
            placeholder="Filter by action or email..."
            style={{ flex: 1, minWidth: 200 }}
            value={filter}
            onChange={e => setFilter(e.target.value)}
          />
          <div className="badge badge-blue" style={{ padding: '0 16px', fontSize: '0.85rem' }}>
            {filtered.length} events
          </div>
        </div>

        <div className="card">
          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[...Array(8)].map((_, i) => <div key={i} className="skeleton" style={{ height: 48 }} />)}
            </div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Timestamp</th><th>Actor</th><th>Action</th><th>Target ID</th><th>IP</th></tr></thead>
                <tbody>
                  {filtered.map(log => (
                    <tr key={log.id}>
                      <td style={{ fontSize: '0.82rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                        {log.timestamp?.substring(0, 19).replace('T', ' ')}
                      </td>
                      <td style={{ fontSize: '0.88rem' }}>{log.actorEmail ?? 'system'}</td>
                      <td>
                        <span className={`badge ${ACTION_COLORS[log.action] ?? 'badge-blue'}`} style={{ fontSize: '0.75rem' }}>
                          {log.action}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                        {log.targetId?.substring(0, 16)}...
                      </td>
                      <td style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>{log.ipAddress}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {filtered.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted)' }}>No events found.</div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
