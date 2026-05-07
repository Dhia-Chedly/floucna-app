'use client';

import { api } from '@/lib/api';
import { useEffect, useState } from 'react';

type Contract = {
  id: string;
  loanId: string;
  borrowerName?: string;
  status: string;
  signatureLevel?: string;
  pdfHash?: string;
  signedAt?: string;
  timestampedAt?: string;
  createdAt?: string;
  verificationResult?: string;
};

const STATUS_ORDER: Record<string, number> = {
  PDF_GENERATED: 1,
  SIGNED: 2,
  TIMESTAMPED: 3,
  VERIFIED: 4,
};

function statusBadgeClass(status: string) {
  if (status === 'TIMESTAMPED' || status === 'VERIFIED') return 'badge-green';
  if (status === 'SIGNED') return 'badge-blue';
  if (status === 'PDF_GENERATED') return 'badge-amber';
  return 'badge-amber';
}

function canGenerate(status: string) {
  return !STATUS_ORDER[status];
}
function canSign(status: string) {
  return status === 'PDF_GENERATED';
}
function canTimestamp(status: string) {
  return status === 'SIGNED';
}
function canDownload(status: string) {
  return !!STATUS_ORDER[status];
}

export default function AdminContractsPage() {
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [approvedLoans, setApprovedLoans] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState<Record<string, { msg: string; ok: boolean }>>({});

  const load = async () => {
    setLoading(true);
    try {
      const [c, loans] = await Promise.all([api.adminContracts(), api.adminLoans()]);
      setContracts(c);
      const contractLoanIds = new Set(c.map((x: Contract) => x.loanId));
      setApprovedLoans(loans.filter((l: any) => l.status === 'APPROVED' && !contractLoanIds.has(l.id)));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const setAction = (id: string, action: string) =>
    setBusy(prev => ({ ...prev, [id]: action }));
  const clearAction = (id: string) =>
    setBusy(prev => { const n = { ...prev }; delete n[id]; return n; });
  const setMsg = (id: string, msg: string, ok: boolean) =>
    setFeedback(prev => ({ ...prev, [id]: { msg, ok } }));

  const generatePdf = async (loanId: string) => {
    setAction(loanId, 'generate');
    setMsg(loanId, '', true);
    try {
      await api.generateContract(loanId);
      setMsg(loanId, '✓ PDF generated', true);
      await load();
    } catch (e: any) {
      setMsg(loanId, e.message || 'Failed', false);
    } finally {
      clearAction(loanId);
    }
  };

  const signPdf = async (loanId: string) => {
    setAction(loanId, 'sign');
    setMsg(loanId, '', true);
    try {
      await api.signContract(loanId);
      setMsg(loanId, '✓ Signed (PAdES demo)', true);
      await load();
    } catch (e: any) {
      setMsg(loanId, e.message || 'Failed', false);
    } finally {
      clearAction(loanId);
    }
  };

  const timestampPdf = async (loanId: string) => {
    setAction(loanId, 'timestamp');
    setMsg(loanId, '', true);
    try {
      await api.timestampContract(loanId);
      setMsg(loanId, '✓ Timestamped', true);
      await load();
    } catch (e: any) {
      setMsg(loanId, e.message || 'Failed', false);
    } finally {
      clearAction(loanId);
    }
  };

  const downloadPdf = async (loanId: string) => {
    setAction(loanId, 'download');
    try {
      const blob = await api.downloadContract(loanId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `loan_agreement_${loanId.slice(0, 8)}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      setMsg(loanId, e.message || 'Download failed', false);
    } finally {
      clearAction(loanId);
    }
  };

  const isBusy = (loanId: string, action: string) => busy[loanId] === action;
  const anyBusy = (loanId: string) => !!busy[loanId];

  return (
    <div className="page">
      <div className="container" style={{ paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 28 }}>
          <h1 style={{ marginBottom: 8 }}>Contract Management</h1>
          <p>Generate PDF agreements, apply PAdES signatures, add timestamps, and download final contracts.</p>
        </div>

        {/* Approved loans awaiting contract generation */}
        {!loading && approvedLoans.length > 0 && (
          <div className="card" style={{ marginBottom: 28, border: '1px solid rgba(245,166,35,0.35)' }}>
            <div style={{ marginBottom: 14 }}>
              <h3 style={{ marginBottom: 4 }}>⚡ Ready for Contract Generation</h3>
              <p style={{ fontSize: '0.86rem' }}>
                {approvedLoans.length} approved loan{approvedLoans.length > 1 ? 's' : ''} without a contract yet.
              </p>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Borrower</th>
                    <th>Amount</th>
                    <th>Duration</th>
                    <th>Score</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {approvedLoans.map(loan => (
                    <tr key={loan.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{loan.borrower_name}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{loan.borrower_email}</div>
                      </td>
                      <td>{loan.currency} {Number(loan.amount).toLocaleString()}</td>
                      <td>{loan.duration_days} days</td>
                      <td>{loan.demo_score} <span style={{ color: 'var(--text-muted)' }}>({loan.score_label})</span></td>
                      <td>
                        <button
                          className="btn btn-primary btn-sm"
                          disabled={anyBusy(loan.id)}
                          onClick={() => generatePdf(loan.id)}
                        >
                          {isBusy(loan.id, 'generate') ? <span className="spinner" /> : '📄 Generate PDF'}
                        </button>
                        {feedback[loan.id] && (
                          <div style={{ marginTop: 6, fontSize: '0.78rem', color: feedback[loan.id].ok ? '#22C55E' : '#EF4444' }}>
                            {feedback[loan.id].msg}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* All contracts */}
        <div className="card">
          <div style={{ marginBottom: 18 }}>
            <h3>All Contracts</h3>
            <p style={{ fontSize: '0.86rem', marginTop: 4 }}>{contracts.length} contract record{contracts.length !== 1 ? 's' : ''}</p>
          </div>

          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[1, 2, 3].map(i => <div key={i} className="skeleton" style={{ height: 60 }} />)}
            </div>
          ) : contracts.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '36px 0' }}>
              No contracts yet. Approve a loan and generate its agreement above.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {contracts.map(c => {
                const fb = feedback[c.loanId];
                return (
                  <div
                    key={c.id}
                    style={{
                      border: '1px solid var(--border)',
                      borderRadius: 'var(--radius-md)',
                      padding: '18px 20px',
                      background: 'var(--surface)',
                    }}
                  >
                    {/* Header row */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10, marginBottom: 14 }}>
                      <div>
                        <div style={{ fontWeight: 700, marginBottom: 2 }}>{c.borrowerName || 'Unknown borrower'}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                          Loan ID: <span style={{ userSelect: 'all' }}>{c.loanId}</span>
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span className={`badge ${statusBadgeClass(c.status)}`}>{c.status}</span>
                        {c.signatureLevel && (
                          <span className="badge badge-blue" style={{ fontSize: '0.72rem' }}>{c.signatureLevel}</span>
                        )}
                      </div>
                    </div>

                    {/* Timestamps */}
                    <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 14 }}>
                      {c.createdAt && <span>Created: {c.createdAt.slice(0, 10)}</span>}
                      {c.signedAt && <span>Signed: {c.signedAt.slice(0, 10)}</span>}
                      {c.timestampedAt && <span>Timestamped: {c.timestampedAt.slice(0, 10)}</span>}
                      {c.pdfHash && (
                        <span title={c.pdfHash}>
                          SHA-256: {c.pdfHash.slice(0, 12)}…
                        </span>
                      )}
                    </div>

                    {/* Progress bar */}
                    <div style={{ display: 'flex', gap: 6, marginBottom: 14, alignItems: 'center' }}>
                      {['PDF_GENERATED', 'SIGNED', 'TIMESTAMPED'].map((step, idx) => {
                        const done = (STATUS_ORDER[c.status] || 0) > idx;
                        const active = c.status === step;
                        return (
                          <div key={step} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <div style={{
                              width: 10, height: 10, borderRadius: '50%',
                              background: done || active ? (done ? '#22C55E' : '#F5A623') : 'var(--border)',
                              border: `2px solid ${done ? '#22C55E' : active ? '#F5A623' : 'var(--border)'}`,
                            }} />
                            <span style={{ fontSize: '0.72rem', color: done || active ? 'var(--text)' : 'var(--text-muted)' }}>
                              {step.replace('_', ' ')}
                            </span>
                            {idx < 2 && <div style={{ width: 24, height: 1, background: done ? '#22C55E' : 'var(--border)' }} />}
                          </div>
                        );
                      })}
                    </div>

                    {/* Action buttons */}
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                      {canSign(c.status) && (
                        <button
                          className="btn btn-sm"
                          style={{ background: 'rgba(59,130,246,0.15)', color: '#60A5FA' }}
                          disabled={anyBusy(c.loanId)}
                          onClick={() => signPdf(c.loanId)}
                        >
                          {isBusy(c.loanId, 'sign') ? <span className="spinner" /> : '✍️ Sign PDF (PAdES)'}
                        </button>
                      )}

                      {canTimestamp(c.status) && (
                        <button
                          className="btn btn-sm"
                          style={{ background: 'rgba(168,85,247,0.15)', color: '#A855F7' }}
                          disabled={anyBusy(c.loanId)}
                          onClick={() => timestampPdf(c.loanId)}
                        >
                          {isBusy(c.loanId, 'timestamp') ? <span className="spinner" /> : '⏱ Add Timestamp'}
                        </button>
                      )}

                      {canDownload(c.status) && (
                        <button
                          className="btn btn-sm"
                          style={{ background: 'rgba(34,197,94,0.15)', color: '#22C55E' }}
                          disabled={anyBusy(c.loanId)}
                          onClick={() => downloadPdf(c.loanId)}
                        >
                          {isBusy(c.loanId, 'download') ? <span className="spinner" /> : '⬇ Download PDF'}
                        </button>
                      )}

                      {c.status === 'TIMESTAMPED' && (
                        <span style={{ fontSize: '0.8rem', color: '#22C55E', marginLeft: 4 }}>
                          ✓ Fully signed &amp; timestamped — ready for compliance verification
                        </span>
                      )}
                    </div>

                    {fb && (
                      <div style={{ marginTop: 10, fontSize: '0.82rem', color: fb.ok ? '#22C55E' : '#EF4444' }}>
                        {fb.msg}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
