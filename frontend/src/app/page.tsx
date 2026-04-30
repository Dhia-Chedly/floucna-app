'use client';

import { useAuth } from '@/lib/auth-context';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export default function HomePage() {
  const router = useRouter();
  const { user } = useAuth();

  useEffect(() => {
    if (user) {
      router.replace('/dashboard');
    }
  }, [user, router]);

  return (
    <div style={{ minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>
      <div className="orb orb-orange" style={{ top: '-200px', left: '-200px' }} />
      <div className="orb orb-blue" style={{ bottom: '-150px', right: '-150px' }} />

      <div
        style={{
          position: 'absolute',
          inset: 0,
          zIndex: 0,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)',
          backgroundSize: '48px 48px',
        }}
      />

      <div
        className="container flex-center"
        style={{
          minHeight: '100vh',
          position: 'relative',
          zIndex: 1,
          flexDirection: 'column',
          textAlign: 'center',
          gap: 30,
          paddingTop: 80,
        }}
      >
        <div className="anim-scale-in" style={{ animationDelay: '0s' }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 88, height: 88, borderRadius: 24, objectFit: 'cover', boxShadow: 'var(--shadow-orange)' }} />
        </div>

        <div className="badge badge-orange anim-fade-in" style={{ animationDelay: '0.1s', fontSize: '0.8rem', padding: '6px 16px' }}>
          Keycloak Auth · KYC · PAdES Signing · DSS Compliance
        </div>

        <div className="anim-fade-up" style={{ animationDelay: '0.2s' }}>
          <h1>
            Secure Micro-Lending with{' '}
            <span style={{ background: 'linear-gradient(135deg,#F5A623,#E84300)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              Verifiable Contracts
            </span>
          </h1>
          <p style={{ maxWidth: 700, margin: '16px auto 0', fontSize: '1.1rem', lineHeight: 1.7 }}>
            Borrowers complete KYC and submit loan requests. Admins review approvals, generate signed PDFs, add timestamps,
            and verify integrity through a compliance dashboard.
          </p>
        </div>

        <div className="flex gap-md anim-fade-up" style={{ animationDelay: '0.35s', flexWrap: 'wrap', justifyContent: 'center' }}>
          <button className="btn btn-primary btn-lg" onClick={() => router.push('/login')}>
            Login
          </button>
          <button className="btn btn-ghost btn-lg" onClick={() => router.push('/dashboard')}>
            Open Dashboard
          </button>
        </div>

        <div className="grid-3 anim-fade-up" style={{ marginTop: 48, animationDelay: '0.5s', width: '100%', maxWidth: 900 }}>
          {[
            { icon: '🛂', title: 'Keycloak Access', desc: 'OIDC-based identity and role-aware authorization for BORROWER and ADMIN users.' },
            { icon: '🪪', title: 'Didit + Local KYC', desc: 'Provider-backed verification with fallback upload and admin review workflow.' },
            { icon: '📄', title: 'Compliance Trail', desc: 'Generate, sign, timestamp, and verify contract PDFs with auditable integrity checks.' },
          ].map(f => (
            <div key={f.title} className="card-glass" style={{ textAlign: 'left' }}>
              <div style={{ fontSize: '2rem', marginBottom: 12 }}>{f.icon}</div>
              <h3 style={{ marginBottom: 8, fontSize: '1.05rem' }}>{f.title}</h3>
              <p style={{ fontSize: '0.9rem' }}>{f.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
