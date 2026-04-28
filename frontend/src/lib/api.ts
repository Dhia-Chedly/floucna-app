const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function getToken() {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('floucna_token');
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path: string, options: RequestInit = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...authHeaders(),
    ...(options.headers as Record<string, string> || {}),
  };
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers,
  });
  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : null;
  if (!res.ok) throw new Error((data && data.error) || 'Request failed');
  return data;
}

export const api = {
  // Auth
  register: (body: object) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: object) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request('/api/auth/me'),

  // KYC
  kycStatus: () => request('/api/kyc/status'),
  kycUpload: async (form: FormData) => {
    const res = await fetch(`${API}/api/kyc/upload`, { method: 'POST', headers: authHeaders(), body: form });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
  },

  // Loans
  marketplace: () => request('/api/loans'),
  myLoans: () => request('/api/loans/my'),
  loanDetail: (id: string) => request(`/api/loans/${id}`),
  createLoan: (body: object) => request('/api/loans', { method: 'POST', body: JSON.stringify(body) }),
  repay: (id: string, amount: number) => request(`/api/loans/${id}/repay?amount=${amount}`, { method: 'POST' }),

  // Pledges
  pledge: (loanId: string, amount: number) =>
    request(`/api/loans/${loanId}/pledge`, { method: 'POST', body: JSON.stringify({ amount }) }),
  pledges: (loanId: string) => request(`/api/loans/${loanId}/pledges`),

  // Contracts
  contract: (loanId: string) => request(`/api/contracts/${loanId}`),
  contractDownloadUrl: (loanId: string) => `${API}/api/contracts/${loanId}/download`,
  downloadContract: async (loanId: string) => {
    const res = await fetch(`${API}/api/contracts/${loanId}/download`, { headers: authHeaders() });
    if (!res.ok) {
      let error = 'Download failed';
      try {
        const data = await res.json();
        error = data.error || error;
      } catch {}
      throw new Error(error);
    }
    return res.blob();
  },

  // Admin
  verifyContract: (loanId: string) => request(`/api/contracts/${loanId}/verify`, { method: 'POST' }),
  auditLogs: (limit = 100) => request(`/api/admin/audit-logs?limit=${limit}`),
  adminUsers: () => request('/api/admin/users'),
  adminStats: () => request('/api/admin/stats'),
  kycPending: () => request('/api/kyc/pending'),
  approveKyc: (userId: string, approve: boolean) =>
    request(`/api/kyc/${userId}/approve?approve=${approve}`, { method: 'POST' }),
};
