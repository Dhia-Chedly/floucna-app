'use client';

import { api } from '@/lib/api';
import { useEffect, useState } from 'react';

export default function CompliancePage() {
  const [loanId, setLoanId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [report, setReport] = useState<any>(null);
  const [history, setHistory] = useState<any[]>([]);

  const loadHistory = async () => {
    try {
      setHistory(await api.complianceReports());
    } catch {
      setHistory([]);
    }
  };

  useEffect(() => {
    loadHistory();
  }, []);

  const verify = async () => {
    if (!loanId.trim()) {
      setError('Loan ID is required.');
      return;
    }

    setLoading(true);
    setError('');
    setReport(null);

    try {
      const form = new FormData();
      form.append('loanId', loanId.trim());
      if (file) {
        form.append('file', file);
      }
      const r = await api.verifyCompliance(form);
      setReport(r);
      await loadHistory();
    } catch (err: any) {
      setError(err.message || 'Verification failed');
    } finally {
      setLoading(false);
    }
  };

  const Check = ({ label, value }: { label: string; value: string }) => {
    const ok = value === 'PRESENT' || value === 'VALID' || value === 'UNCHANGED' || value === 'PASS' || value === 'SELF_SIGNED';
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 14px',
          borderRadius: 'var(--radius-sm)',
          background: ok ? 'rgba(34,197,94,0.08)' : 'rgba(239,68,68,0.08)',
          border: `1px solid ${ok ? 'rgba(34,197,94,0.25)' : 'rgba(239,68,68,0.25)'}`,
        }}
      >
        <span>{label}</span>
        <strong style={{ color: ok ? '#22C55E' : '#EF4444' }}>{value}</strong>
      </div>
    );
  };

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 860, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ marginBottom: 8 }}>Compliance Verification</h1>
          <p>Upload a signed/timestamped PDF and verify signature integrity for a specific loan contract.</p>
        </div>

        <div className="card" style={{ marginBottom: 24 }}>
          <div className="grid-2" style={{ marginBottom: 12 }}>
            <div className="form-group">
              <label className="form-label">Loan ID</label>
              <input className="input" value={loanId} onChange={e => setLoanId(e.target.value)} placeholder="Loan UUID" />
            </div>
            <div className="form-group">
              <label className="form-label">PDF File (optional)</label>
              <input className="input" type="file" accept="application/pdf" onChange={e => setFile(e.target.files?.[0] || null)} />
            </div>
          </div>

          <button className="btn btn-primary" onClick={verify} disabled={loading || !loanId.trim()}>
            {loading ? <span className="spinner" /> : 'Run Verification'}
          </button>

          {error && <div className="alert alert-error" style={{ marginTop: 14 }}>{error}</div>}
        </div>

        {report && (
          <div className="card" style={{ marginBottom: 24 }}>
            <div
              style={{
                padding: '16px 18px',
                marginBottom: 16,
                borderRadius: 'var(--radius-md)',
                background: report.overallResult === 'PASS'
                  ? 'linear-gradient(135deg, rgba(34,197,94,0.14), rgba(34,197,94,0.05))'
                  : 'linear-gradient(135deg, rgba(239,68,68,0.14), rgba(239,68,68,0.05))',
                border: `1px solid ${report.overallResult === 'PASS' ? 'rgba(34,197,94,0.3)' : 'rgba(239,68,68,0.3)'}`,
              }}
            >
              <div style={{ fontWeight: 700, marginBottom: 4 }}>Overall Result: {report.overallResult}</div>
              <div style={{ fontSize: '0.84rem', color: 'var(--text-muted)' }}>Verified at {report.verifiedAt}</div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <Check label="Signature Presence" value={report.signaturePresence} />
              <Check label="Signature Validity" value={report.signatureValidity} />
              <Check label="Document Integrity" value={report.documentIntegrity} />
              <Check label="Timestamp" value={report.timestamp} />
              <Check label="Certificate" value={report.certificate} />
            </div>
          </div>
        )}

        <div className="card">
          <h3 style={{ marginBottom: 12 }}>Verification Reports</h3>
          {history.length === 0 ? (
            <div style={{ color: 'var(--text-muted)' }}>No verification reports yet.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Loan ID</th>
                    <th>Borrower</th>
                    <th>Result</th>
                    <th>Verified At</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map(item => (
                    <tr key={`${item.loanId}-${item.verifiedAt}`}>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{String(item.loanId).slice(0, 16)}...</td>
                      <td>{item.borrowerName}</td>
                      <td>
                        <span className={`badge ${item.verificationResult === 'PASS' ? 'badge-green' : 'badge-red'}`}>
                          {item.verificationResult}
                        </span>
                      </td>
                      <td>{item.verifiedAt ? String(item.verifiedAt).replace('T', ' ').slice(0, 19) : '-'}</td>
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
