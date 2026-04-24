'use client';
import { useState, useRef } from 'react';
import { useAuth } from '@/lib/auth-context';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

export default function KycPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [files, setFiles] = useState<{ front?: File; back?: File; selfie?: File }>({});
  const [previews, setPreviews] = useState<{ front?: string; back?: string; selfie?: string }>({});
  const [step, setStep] = useState<'upload' | 'submitting' | 'done' | 'already'>('upload');
  const [error, setError] = useState('');
  const videoRef = useRef<HTMLVideoElement>(null);
  const [camActive, setCamActive] = useState(false);

  const handleFile = (key: 'front' | 'back' | 'selfie') => (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFiles(f => ({ ...f, [key]: file }));
    setPreviews(p => ({ ...p, [key]: URL.createObjectURL(file) }));
  };

  const startCam = async () => {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    if (videoRef.current) { videoRef.current.srcObject = stream; videoRef.current.play(); }
    setCamActive(true);
  };

  const captureSelfie = () => {
    if (!videoRef.current) return;
    const canvas = document.createElement('canvas');
    canvas.width = videoRef.current.videoWidth;
    canvas.height = videoRef.current.videoHeight;
    canvas.getContext('2d')!.drawImage(videoRef.current, 0, 0);
    canvas.toBlob(blob => {
      if (!blob) return;
      const file = new File([blob], 'selfie.jpg', { type: 'image/jpeg' });
      setFiles(f => ({ ...f, selfie: file }));
      setPreviews(p => ({ ...p, selfie: URL.createObjectURL(blob) }));
      (videoRef.current!.srcObject as MediaStream).getTracks().forEach(t => t.stop());
      setCamActive(false);
    }, 'image/jpeg');
  };

  const submit = async () => {
    if (!files.front || !files.back || !files.selfie) { setError('Please provide all three documents.'); return; }
    setStep('submitting'); setError('');
    try {
      const form = new FormData();
      form.append('idFront', files.front);
      form.append('idBack', files.back);
      form.append('selfie', files.selfie);
      await api.kycUpload(form);
      setStep('done');
    } catch (e: any) { setError(e.message); setStep('upload'); }
  };

  if (step === 'done') return (
    <div className="page flex-center" style={{ minHeight: '100vh', flexDirection: 'column', gap: 20 }}>
      <div style={{ fontSize: '4rem' }}>✅</div>
      <h2>KYC Submitted!</h2>
      <p>Your documents are under review. We'll notify you once verified.</p>
      <button className="btn btn-primary" onClick={() => router.push('/dashboard')}>Back to Dashboard</button>
    </div>
  );

  return (
    <div className="page">
      <div className="container" style={{ maxWidth: 720, paddingTop: 40, paddingBottom: 60 }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ marginBottom: 8 }}>Identity Verification</h1>
          <p>Upload your official ID documents to unlock all platform features.</p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {/* Steps progress */}
        <div style={{ display: 'flex', gap: 0, marginBottom: 36, position: 'relative' }}>
          {['ID Front', 'ID Back', 'Selfie'].map((s, i) => {
            const done = (i === 0 && files.front) || (i === 1 && files.back) || (i === 2 && files.selfie);
            return (
              <div key={s} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, position: 'relative' }}>
                {i > 0 && <div style={{ position: 'absolute', top: 16, left: '-50%', right: '50%', height: 2, background: done ? 'var(--orange)' : 'var(--border)' }} />}
                <div style={{
                  width: 32, height: 32, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: done ? 'var(--grad-brand)' : 'var(--surface-2)',
                  border: `2px solid ${done ? 'var(--orange)' : 'var(--border)'}`,
                  fontWeight: 700, fontSize: '0.85rem', transition: 'all 0.3s',
                }}>
                  {done ? '✓' : i + 1}
                </div>
                <span style={{ fontSize: '0.8rem', color: done ? 'var(--orange)' : 'var(--text-muted)' }}>{s}</span>
              </div>
            );
          })}
        </div>

        <div className="grid-3" style={{ marginBottom: 32 }}>
          {/* ID Front */}
          <div className="card" style={{ textAlign: 'center', cursor: 'pointer', position: 'relative', overflow: 'hidden' }}>
            <label htmlFor="id-front" style={{ cursor: 'pointer', display: 'block' }}>
              {previews.front ? (
                <img src={previews.front} alt="ID Front" style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 'var(--radius-sm)' }} />
              ) : (
                <div style={{ height: 140, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, color: 'var(--text-muted)' }}>
                  <span style={{ fontSize: '2rem' }}>🪪</span>
                  <span style={{ fontSize: '0.85rem' }}>ID Card Front</span>
                  <span style={{ fontSize: '0.75rem' }}>Click to upload</span>
                </div>
              )}
              <input id="id-front" type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFile('front')} />
            </label>
            {files.front && <div className="badge badge-green" style={{ marginTop: 10 }}>✓ Uploaded</div>}
          </div>

          {/* ID Back */}
          <div className="card" style={{ textAlign: 'center', cursor: 'pointer' }}>
            <label htmlFor="id-back" style={{ cursor: 'pointer', display: 'block' }}>
              {previews.back ? (
                <img src={previews.back} alt="ID Back" style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 'var(--radius-sm)' }} />
              ) : (
                <div style={{ height: 140, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, color: 'var(--text-muted)' }}>
                  <span style={{ fontSize: '2rem' }}>🪪</span>
                  <span style={{ fontSize: '0.85rem' }}>ID Card Back</span>
                  <span style={{ fontSize: '0.75rem' }}>Click to upload</span>
                </div>
              )}
              <input id="id-back" type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFile('back')} />
            </label>
            {files.back && <div className="badge badge-green" style={{ marginTop: 10 }}>✓ Uploaded</div>}
          </div>

          {/* Selfie */}
          <div className="card" style={{ textAlign: 'center' }}>
            {previews.selfie ? (
              <img src={previews.selfie} alt="Selfie" style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 'var(--radius-sm)', marginBottom: 10 }} />
            ) : camActive ? (
              <video ref={videoRef} style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 'var(--radius-sm)', marginBottom: 10 }} />
            ) : (
              <div style={{ height: 140, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, color: 'var(--text-muted)' }}>
                <span style={{ fontSize: '2rem' }}>🤳</span>
                <span style={{ fontSize: '0.85rem' }}>Liveness Selfie</span>
              </div>
            )}
            {!files.selfie && !camActive && <button className="btn btn-ghost btn-sm btn-full" onClick={startCam}>Open Webcam</button>}
            {camActive && <button className="btn btn-primary btn-sm btn-full" onClick={captureSelfie}>📸 Capture</button>}
            {files.selfie && <div className="badge badge-green" style={{ marginTop: 10 }}>✓ Captured</div>}
          </div>
        </div>

        <button
          id="kyc-submit"
          className="btn btn-primary btn-lg btn-full"
          onClick={submit}
          disabled={step === 'submitting' || !files.front || !files.back || !files.selfie}
        >
          {step === 'submitting' ? <><span className="spinner" /> Verifying...</> : 'Submit for Verification'}
        </button>
      </div>
    </div>
  );
}
