'use client';

import { api } from '@/lib/api';
import { useEffect, useState } from 'react';

const ACTION_COLORS: Record<string, string> = {
  KYC_SESSION_CREATED: 'badge-blue',
  KYC_LOCAL_UPLOAD: 'badge-amber',
  KYC_WEBHOOK_RECEIVED: 'badge-blue',
  KYC_APPROVED: 'badge-green',
  KYC_REJECTED: 'badge-red',
  LOAN_SUBMITTED: 'badge-orange',
  LOAN_APPROVED: 'badge-green',
  LOAN_REJECTED: 'badge-red',
  PDF_GENERATED: 'badge-blue',
  PDF_SIGNED: 'badge-orange',
  PDF_TIMESTAMPED: 'badge-green',
  CONTRACT_DOWNLOADED: 'badge-blue',
  COMPLIANCE_VERIFICATION_RUN: 'badge-amber',
  COMPLIANCE_VERIFICATION_VALID: 'badge-green',
  COMPLIANCE_VERIFICATION_FAILED: 'badge-red',
};

export default function AuditPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    api
      .auditLogs(200)
      .then(setLogs)
      .finally(() => setLoading(false));
  }, []);

  const filtered = logs.filter(log => {
    if (!filter) return true;
    const term = filter.toLowerCase();
    return (
      String(log.action || '').toLowerCase().includes(term) ||
      String(log.actorEmail || '').toLowerCase().includes(term) ||
      String(log.targetType || '').toLowerCase().includes(term) ||
      String(log.targetId || '').toLowerCase().includes(term)
    );
  });

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ marginBottom: 8 }}>Audit Trail</h1>
          <p>Security and workflow traceability across KYC, loans, contracts, and compliance checks.</p>
        </div>

        <div style={{ display: 'flex', gap: 12, marginBottom: 18, flexWrap: 'wrap' }}>
          <input
            className="input"
            placeholder="Filter by action, actor, target..."
            value={filter}
            onChange={e => setFilter(e.target.value)}
            style={{ flex: 1, minWidth: 260 }}
          />
          <div className="badge badge-blue" style={{ padding: '0 14px' }}>{filtered.length} events</div>
        </div>

        <div className="card">
          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[1, 2, 3, 4, 5].map(i => <div key={i} className="skeleton" style={{ height: 46 }} />)}
            </div>
          ) : filtered.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '30px 0' }}>No matching events.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Target</th>
                    <th>Result</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(log => (
                    <tr key={log.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                        {log.createdAt ? String(log.createdAt).replace('T', ' ').slice(0, 19) : '-'}
                      </td>
                      <td>{log.actorEmail || 'system'}</td>
                      <td>
                        <span className={`badge ${ACTION_COLORS[log.action] || 'badge-blue'}`}>{log.action}</span>
                      </td>
                      <td style={{ fontSize: '0.8rem' }}>
                        {log.targetType || '-'} / {log.targetId ? String(log.targetId).slice(0, 12) + '...' : '-'}
                      </td>
                      <td>{log.result || '-'}</td>
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
