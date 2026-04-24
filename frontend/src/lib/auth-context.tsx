'use client';
import React, { createContext, useContext, useState, useEffect } from 'react';
import { api } from '@/lib/api';

interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'BORROWER' | 'LENDER' | 'ADMIN';
  isVerified: boolean;
  floucnaScore: number | null;
  riskCeiling: number | null;
}

interface AuthCtx {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string, role: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('floucna_token');
    if (token) {
      api.me().then(setUser).catch(() => localStorage.removeItem('floucna_token')).finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (email: string, password: string) => {
    const res = await api.login({ email, password });
    localStorage.setItem('floucna_token', res.token);
    const profile = await api.me();
    setUser(profile);
  };

  const register = async (email: string, password: string, fullName: string, role: string) => {
    const res = await api.register({ email, password, fullName, role });
    localStorage.setItem('floucna_token', res.token);
    const profile = await api.me();
    setUser(profile);
  };

  const logout = () => {
    localStorage.removeItem('floucna_token');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
