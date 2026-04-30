const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function getStoredToken() {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('floucna_token');
}

function authHeader(tokenOverride?: string): Record<string, string> {
  const token = tokenOverride || getStoredToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path: string, options: RequestInit = {}, tokenOverride?: string) {
  const incoming = options.headers as Record<string, string> | undefined;
  const hasContentType = incoming && Object.keys(incoming).some(k => k.toLowerCase() === 'content-type');

  const headers: Record<string, string> = {
    ...authHeader(tokenOverride),
    ...(incoming || {}),
  };

  if (!hasContentType && options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(`${API}${path}`, { ...options, headers });
  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : null;
  if (!res.ok) {
    throw new Error((data && data.error) || `Request failed (${res.status})`);
  }
  return data;
}

export const api = {
  me: (tokenOverride?: string) => request('/api/me', {}, tokenOverride),

  adminStats: () => request('/api/admin/stats'),
  adminUsers: () => request('/api/admin/users'),

  kycSession: () => request('/api/kyc/session', { method: 'POST' }),
  kycStatus: () => request('/api/kyc/status'),
  kycLocalUpload: (form: FormData) => request('/api/kyc/local/upload', { method: 'POST', body: form }),
  kycPending: () => request('/api/admin/kyc/pending'),
  approveKyc: (id: string) => request(`/api/admin/kyc/${id}/approve`, { method: 'POST' }),
  rejectKyc: (id: string) => request(`/api/admin/kyc/${id}/reject`, { method: 'POST' }),

  createLoan: (body: object) => request('/api/loans', { method: 'POST', body: JSON.stringify(body) }),
  myLoans: () => request('/api/loans/me'),
  loanDetail: (id: string) => request(`/api/loans/${id}`),
  adminLoans: () => request('/api/admin/loans'),
  approveLoan: (id: string) => request(`/api/admin/loans/${id}/approve`, { method: 'POST' }),
  rejectLoan: (id: string) => request(`/api/admin/loans/${id}/reject`, { method: 'POST' }),

  adminContracts: () => request('/api/admin/contracts'),
  generateContract: (loanId: string) => request(`/api/admin/contracts/generate/${loanId}`, { method: 'POST' }),
  signContract: (loanId: string) => request(`/api/admin/contracts/sign/${loanId}`, { method: 'POST' }),
  timestampContract: (loanId: string) => request(`/api/admin/contracts/timestamp/${loanId}`, { method: 'POST' }),
  contractMeta: (loanId: string) => request(`/api/contracts/${loanId}`),
  contractDownloadUrl: (loanId: string) => `${API}/api/contracts/${loanId}/download`,
  downloadContract: async (loanId: string) => {
    const res = await fetch(`${API}/api/contracts/${loanId}/download`, { headers: authHeader() });
    if (!res.ok) {
      let message = 'Download failed';
      try {
        const data = await res.json();
        message = data.error || message;
      } catch {
        // no-op
      }
      throw new Error(message);
    }
    return res.blob();
  },

  verifyCompliance: (form: FormData) => request('/api/admin/compliance/verify', { method: 'POST', body: form }),
  complianceReports: () => request('/api/admin/compliance/reports'),
  auditLogs: (limit = 100) => request(`/api/admin/audit-logs?limit=${limit}`),
};
