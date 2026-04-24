'use client';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useEffect } from 'react';

export default function HomePage() {
  const router = useRouter();
  const { user } = useAuth();

  useEffect(() => {
    if (user) router.replace('/dashboard');
  }, [user]);

  return (
    <div style={{ minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>
      {/* Background orbs */}
      <div className="orb orb-orange" style={{ top: '-200px', left: '-200px' }} />
      <div className="orb orb-blue"   style={{ bottom: '-150px', right: '-150px' }} />

      {/* Grid pattern */}
      <div style={{
        position: 'absolute', inset: 0, zIndex: 0,
        backgroundImage: `linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
                          linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)`,
        backgroundSize: '48px 48px',
      }} />

      {/* Hero */}
      <div className="container flex-center" style={{ minHeight: '100vh', position: 'relative', zIndex: 1, flexDirection: 'column', textAlign: 'center', gap: 32, paddingTop: 80 }}>

        {/* Logo mark */}
        <div className="anim-scale-in" style={{ animationDelay: '0s' }}>
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 88, height: 88, borderRadius: 24, objectFit: 'cover', boxShadow: 'var(--shadow-orange)' }} />
        </div>

        {/* Tagline badge */}
        <div className="badge badge-orange anim-fade-in" style={{ animationDelay: '0.1s', fontSize: '0.8rem', padding: '6px 16px' }}>
          🔐 PAdES Cryptographic Signatures · Trusted Timestamping · Real-Time Compliance
        </div>

        {/* Headline */}
        <div className="anim-fade-up" style={{ animationDelay: '0.2s' }}>
          <h1>
            Lend & Borrow with{' '}
            <span style={{ background: 'linear-gradient(135deg,#F5A623,#E84300)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              Cryptographic Trust
            </span>
          </h1>
          <p style={{ maxWidth: 600, margin: '16px auto 0', fontSize: '1.15rem', lineHeight: 1.7 }}>
            Floucna Mina Fina is a secure peer-to-peer micro-lending platform where every agreement is digitally signed, timestamped, and verifiable — powered by PAdES and BouncyCastle.
          </p>
        </div>

        {/* CTA Buttons */}
        <div className="flex gap-md anim-fade-up" style={{ animationDelay: '0.35s', flexWrap: 'wrap', justifyContent: 'center' }}>
          <button className="btn btn-primary btn-lg" onClick={() => router.push('/register')}>
            Start Borrowing
          </button>
          <button className="btn btn-blue btn-lg" onClick={() => router.push('/register?role=LENDER')}>
            Become a Lender
          </button>
          <button className="btn btn-ghost btn-lg" onClick={() => router.push('/marketplace')}>
            Browse Marketplace
          </button>
        </div>

        {/* Feature cards */}
        <div className="grid-3 anim-fade-up" style={{ marginTop: 48, animationDelay: '0.5s', width: '100%', maxWidth: 900 }}>
          {[
            { icon: '🪪', title: 'KYC Identity Verification', desc: 'Upload your ID and selfie. Our biometric matching ensures only real people participate.' },
            { icon: '📄', title: 'PAdES Digital Contracts', desc: 'Every funded loan auto-generates a legally signed PDF contract with a trusted timestamp.' },
            { icon: '🔍', title: 'Compliance Dashboard', desc: 'Admins verify document integrity in real-time. Any tampered contract is immediately flagged.' },
          ].map(f => (
            <div key={f.title} className="card-glass" style={{ textAlign: 'left' }}>
              <div style={{ fontSize: '2rem', marginBottom: 12 }}>{f.icon}</div>
              <h3 style={{ marginBottom: 8, fontSize: '1.05rem' }}>{f.title}</h3>
              <p style={{ fontSize: '0.9rem' }}>{f.desc}</p>
            </div>
          ))}
        </div>

        {/* Trust stats */}
        <div className="flex gap-lg anim-fade-up" style={{ animationDelay: '0.65s', flexWrap: 'wrap', justifyContent: 'center', marginTop: 16 }}>
          {[
            { v: 'SHA-256', l: 'Hash Algorithm' },
            { v: 'PAdES-B', l: 'Signature Standard' },
            { v: 'SQLite WAL', l: 'Concurrency Mode' },
            { v: 'Zero-Config', l: 'Deployment' },
          ].map(s => (
            <div key={s.l} style={{ textAlign: 'center' }}>
              <div style={{ fontFamily: 'Space Grotesk,sans-serif', fontWeight: 800, fontSize: '1.3rem', background: 'linear-gradient(135deg,#F5A623,#E84300)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>{s.v}</div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 2 }}>{s.l}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
