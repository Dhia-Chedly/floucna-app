'use client';

import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function KycPage() {
  const { user } = useAuth();
  const router = useRouter();

  const [status, setStatus] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [uploadMode, setUploadMode] = useState(false);
  const [idFront, setIdFront] = useState<File | null>(null);
  const [idBack, setIdBack] = useState<File | null>(null);
  const [face, setFace] = useState<File | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const loadStatus = async () => {
    setLoading(true);
    try {
      setStatus(await api.kycStatus());
    } catch (err: any) {
      setError(err.message || 'Failed to load KYC status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!user) {
      router.replace('/login');
      return;
    }
    loadStatus();
  }, [user, router]);

  const startKyc = async () => {
    setStarting(true);
    setError('');
    setMessage('');
    try {
      const session = await api.kycSession();
      setStatus((prev: any) => ({ ...(prev || {}), ...session }));

      if (session.mode === 'DIDIT' && session.verificationUrl) {
        window.open(session.verificationUrl, '_blank', 'noopener,noreferrer');
        setMessage('Didit verification opened in a new tab. Complete it, then refresh status.');
        setUploadMode(false);
      } else {
        setUploadMode(true);
        if (session.fallback) {
          setMessage('Didit is currently unreachable. Please complete local KYC by uploading your ID (front and back) and a face picture for admin review.');
        } else {
          setMessage('Local KYC mode. Upload your ID (front and back) and a face picture for admin review.');
        }
      }
    } catch (err: any) {
      setError(err.message || 'Failed to start KYC session');
    } finally {
      setStarting(false);
    }
  };

  const submitLocal = async () => {
    if (!idFront || !idBack || !face) {
      setError('Please upload the ID front, ID back, and a face picture.');
      return;
    }

    setError('');
    setMessage('');

    try {
      const form = new FormData();
      form.append('idFront', idFront);
      form.append('idBack', idBack);
      form.append('face', face);
      await api.kycLocalUpload(form);
      setMessage('Documents uploaded successfully. Your KYC is now under admin review.');
      setUploadMode(false);
      setIdFront(null);
      setIdBack(null);
      setFace(null);
      await loadStatus();
    } catch (err: any) {
      setError(err.message || 'Upload failed');
    }
  };

  if (!user) return null;

  const statusValue = status?.status || 'NOT_STARTED';
  const badgeClass =
    statusValue === 'VERIFIED'
      ? 'badge-green'
      : statusValue === 'UNDER_REVIEW' || statusValue === 'SESSION_CREATED'
        ? 'badge-amber'
        : statusValue === 'REJECTED'
          ? 'badge-red'
          : 'badge-blue';

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 760, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 26 }}>
          <h1 style={{ marginBottom: 8 }}>Identity Verification</h1>
          <p>Complete KYC using Didit when configured, or local fallback review for offline/demo mode.</p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert alert-success">{message}</div>}

        <div className="card" style={{ marginBottom: 24 }}>
          {loading ? (
            <div className="skeleton" style={{ height: 62 }} />
          ) : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 4 }}>Current Status</div>
                  <span className={`badge ${badgeClass}`} style={{ fontSize: '0.85rem' }}>{statusValue}</span>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-ghost btn-sm" onClick={loadStatus}>Refresh</button>
                  <button className="btn btn-primary btn-sm" onClick={startKyc} disabled={starting || statusValue === 'VERIFIED'}>
                    {starting ? <span className="spinner" /> : statusValue === 'VERIFIED' ? 'Already Verified' : 'Start KYC'}
                  </button>
                </div>
              </div>

              {status?.mode && (
                <p style={{ marginTop: 12, fontSize: '0.85rem' }}>
                  Active mode: <strong>{status.mode}</strong>
                </p>
              )}
            </>
          )}
        </div>

        {uploadMode && (
          <div className="card">
            <h3 style={{ marginBottom: 10 }}>Local Fallback Upload</h3>
            <p style={{ fontSize: '0.9rem', marginBottom: 14 }}>
              Upload clear images of your ID (front and back) and a face picture. An admin will review and approve/reject manually.
            </p>
            <div className="form-group" style={{ marginBottom: 14 }}>
              <label className="form-label">ID Front</label>
              <input className="input" type="file" accept="image/*" onChange={e => setIdFront(e.target.files?.[0] || null)} />
            </div>
            <div className="form-group" style={{ marginBottom: 14 }}>
              <label className="form-label">ID Back</label>
              <input className="input" type="file" accept="image/*" onChange={e => setIdBack(e.target.files?.[0] || null)} />
            </div>
            <div className="form-group" style={{ marginBottom: 14 }}>
              <label className="form-label">Face Picture</label>
              <input className="input" type="file" accept="image/*" onChange={e => setFace(e.target.files?.[0] || null)} />
            </div>
            <button className="btn btn-primary" onClick={submitLocal} disabled={!idFront || !idBack || !face}>
              Submit Local KYC
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
