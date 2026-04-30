'use client';

import { useAuth } from '@/lib/auth-context';
import { usePathname, useRouter } from 'next/navigation';

const BORROWER_LINKS = [
  { label: 'Dashboard', href: '/dashboard' },
  { label: 'KYC', href: '/kyc' },
  { label: 'Request Loan', href: '/loans/request' },
];

const ADMIN_LINKS = [
  { label: 'Admin', href: '/admin' },
  { label: 'Loans', href: '/admin/loans' },
  { label: 'Contracts', href: '/admin/contracts' },
  { label: 'Compliance', href: '/admin/compliance' },
  { label: 'Audit', href: '/admin/audit' },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const path = usePathname();

  const links = !user ? [] : user.role === 'ADMIN' ? ADMIN_LINKS : BORROWER_LINKS;

  return (
    <nav className="nav">
      <div className="nav-inner">
        <button
          className="nav-logo"
          onClick={() => router.push('/')}
          style={{ background: 'none', border: 'none', cursor: 'pointer' }}
        >
          <img src="/logo.jpeg" alt="Floucna" style={{ width: 36, height: 36, borderRadius: 10, objectFit: 'cover' }} />
          <span>
            Floucna{' '}
            <span
              style={{
                background: 'linear-gradient(135deg,#F5A623,#E84300)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              Mina Fina
            </span>
          </span>
        </button>

        <div className="nav-links">
          {links.map(l => (
            <button
              key={l.href}
              className={`nav-link ${path === l.href ? 'active' : ''}`}
              onClick={() => router.push(l.href)}
            >
              {l.label}
            </button>
          ))}

          {user ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginLeft: 12 }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>{user.fullName}</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{user.role}</div>
              </div>
              <button
                className="btn btn-ghost btn-sm"
                onClick={async () => {
                  await logout();
                  router.push('/');
                }}
              >
                Logout
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 8, marginLeft: 12 }}>
              <button className="btn btn-primary btn-sm" onClick={() => router.push('/login')}>
                Login
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
