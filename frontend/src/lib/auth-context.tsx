'use client';

import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import Keycloak from 'keycloak-js';
import { api } from '@/lib/api';

type Role = 'BORROWER' | 'ADMIN';

type AuthMode = 'KEYCLOAK' | 'LOCAL';

interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string;
  kycStatus: string;
  isVerified: boolean;
}

interface AuthCtx {
  user: User | null;
  loading: boolean;
  authMode: AuthMode;
  login: () => Promise<void>;
  register: () => Promise<void>;
  logout: () => Promise<void>;
  setLocalToken: (token: string) => Promise<void>;
  clearLocalToken: () => void;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

const AUTH_MODE: AuthMode =
  (process.env.NEXT_PUBLIC_AUTH_MODE || 'LOCAL').toUpperCase() === 'KEYCLOAK' ? 'KEYCLOAK' : 'LOCAL';

let keycloakInstance: Keycloak | null = null;

function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    const url = process.env.NEXT_PUBLIC_KEYCLOAK_URL;
    const realm = process.env.NEXT_PUBLIC_KEYCLOAK_REALM;
    const clientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID;

    if (!url || !realm || !clientId) {
      throw new Error('Missing Keycloak frontend configuration');
    }

    keycloakInstance = new Keycloak({ url, realm, clientId });
  }
  return keycloakInstance;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const refreshTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const clearLocalToken = () => {
    localStorage.removeItem('floucna_token');
    setUser(null);
  };

  const setLocalToken = async (token: string) => {
    localStorage.setItem('floucna_token', token);
    const profile = await api.me(token);
    setUser(profile);
  };

  const loadProfileFromStorage = async () => {
    const token = localStorage.getItem('floucna_token');
    if (!token) {
      setUser(null);
      return;
    }
    try {
      const profile = await api.me(token);
      setUser(profile);
    } catch {
      clearLocalToken();
    }
  };

  useEffect(() => {
    let mounted = true;

    const init = async () => {
      try {
        if (AUTH_MODE === 'KEYCLOAK') {
          const kc = getKeycloak();
          const authenticated = await kc.init({
            onLoad: 'check-sso',
            pkceMethod: 'S256',
            checkLoginIframe: false,
          });

          if (authenticated && kc.token) {
            localStorage.setItem('floucna_token', kc.token);
            const profile = await api.me(kc.token);
            if (mounted) {
              setUser(profile);
            }

            refreshTimer.current = setInterval(async () => {
              try {
                const refreshed = await kc.updateToken(30);
                if (refreshed && kc.token) {
                  localStorage.setItem('floucna_token', kc.token);
                }
              } catch {
                localStorage.removeItem('floucna_token');
                setUser(null);
              }
            }, 20000);
          } else if (mounted) {
            setUser(null);
          }
        } else {
          await loadProfileFromStorage();
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    init();

    return () => {
      mounted = false;
      if (refreshTimer.current) {
        clearInterval(refreshTimer.current);
        refreshTimer.current = null;
      }
    };
  }, []);

  const login = async () => {
    if (AUTH_MODE !== 'KEYCLOAK') {
      throw new Error('Use local token login in LOCAL mode');
    }
    const kc = getKeycloak();
    await kc.login({ redirectUri: window.location.origin + '/dashboard' });
  };

  const register = async () => {
    if (AUTH_MODE !== 'KEYCLOAK') {
      throw new Error('Registration is available only in KEYCLOAK mode');
    }
    const kc = getKeycloak();
    await kc.register({ redirectUri: window.location.origin + '/dashboard' });
  };

  const logout = async () => {
    localStorage.removeItem('floucna_token');
    setUser(null);

    if (AUTH_MODE === 'KEYCLOAK') {
      const kc = getKeycloak();
      await kc.logout({ redirectUri: window.location.origin + '/' });
    }
  };

  const value = useMemo<AuthCtx>(
    () => ({ user, loading, authMode: AUTH_MODE, login, register, logout, setLocalToken, clearLocalToken }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
