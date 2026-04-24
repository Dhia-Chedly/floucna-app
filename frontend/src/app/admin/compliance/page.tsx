'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';

export default function CompliancePage() {
  const [loanId, setLoanId] = useState('');
  const [report, setReport] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const verify = async () => {
    if (!loanId.trim()) return;
    setLoading(true); setError(''); setReport(null);
    try {
      const r = await api.verifyContract(loanId.trim());
      setReport(r);
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  const Check = ({ ok, label }: { ok: boolean; label: string }) => (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '14px 18px',
      background: ok ? 'rgba(34,197,94,0.07)' : 'rgba(239,68,68,0.07)',
      borderRadius: 'var(--radius-sm)',
      border: `1px solid ${ok ? 'rgba(34,197,94,0.2)' : 'rgba(239,68,68,0.2)'}`,
    }}>
      <span style={{ fontWeight: 500 }}>{label}</span>
      <span style={{ fontWeight: 700, color: ok ? '#22C55E' : '#EF4444', fontSize: '1.1rem' }}>
        {ok ? '✓ PASS' : '✗ FAIL'}
      </span>
    </div>
  );

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 720, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ marginBottom: 8 }}>Compliance Verification</h1>
          <p>Enter a Loan ID to cryptographically verify the integrity of its digital contract.</p>
        </div>

        <div className="card" style={{ marginBottom: 32 }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <input
              id="verify-loan-id"
              className="input"
              placeholder="Paste Loan ID..."
              value={loanId}
              onChange={e => setLoanId(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && verify()}
              style={{ flex: 1 }}
            />
            <button id="verify-btn" className="btn btn-primary" onClick={verify} disabled={loading || !loanId}>
              {loading ? <span className="spinner" /> : '🔍 Verify'}
            </button>
          </div>
          {error && <div className="alert alert-error" style={{ marginTop: 16, marginBottom: 0 }}>{error}</div>}
        </div>

        {report && (
          <div className="card anim-scale-in">
            {/* Verdict banner */}
            <div style={{
              padding: '20px 24px',
              borderRadius: 'var(--radius-md)',
              marginBottom: 24,
              background: report.overallValid ? 'linear-gradient(135deg,rgba(34,197,94,0.15),rgba(34,197,94,0.05))' : 'linear-gradient(135deg,rgba(239,68,68,0.15),rgba(239,68,68,0.05))',
              border: `2px solid ${report.overallValid ? 'rgba(34,197,94,0.4)' : 'rgba(239,68,68,0.4)'}`,
              textAlign: 'center',
            }}>
              <div style={{ fontSize: '3rem', marginBottom: 8 }}>{report.overallValid ? '🔒' : '🔓'}</div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, fontFamily: 'Space Grotesk,sans-serif', color: report.overallValid ? '#22C55E' : '#EF4444' }}>
                {report.verdict}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 6 }}>
                Verified at {report.verifiedAt?.substring(0, 19).replace('T', ' ')} UTC
              </div>
            </div>

            {/* Individual checks */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
              <Check ok={report.documentFound} label="📄 Document Found on Server" />
              <Check ok={report.signaturePresent} label="🔏 Digital Signature Present" />
              <Check ok={report.timestampValid} label="⏱️ Trusted Timestamp Valid (TSA)" />
            </div>

            {/* Technical details */}
            <div style={{ background: 'var(--surface-2)', borderRadius: 'var(--radius-sm)', padding: '16px' }}>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 10, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Technical Details</div>
              <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', fontFamily: 'monospace', display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div>Hash: {report.documentHash?.substring(0, 32)}...</div>
                <div>TSA: {report.tsaToken?.substring(0, 50)}...</div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
