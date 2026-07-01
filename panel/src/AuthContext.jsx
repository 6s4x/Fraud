import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { apiUrl, connectWebSocket, sendWS, onWS } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('fraudoor_user');
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('fraudoor_token'));

  useEffect(() => {
    if (token) {
      localStorage.setItem('fraudoor_token', token);
      localStorage.setItem('fraudoor_user', JSON.stringify(user));
      connectWebSocket(token);
      // Auth WS connection
      const t = setTimeout(() => sendWS('panel:auth', { token }), 500);
      return () => clearTimeout(t);
    } else {
      localStorage.removeItem('fraudoor_token');
      localStorage.removeItem('fraudoor_user');
    }
  }, [token]);

  const login = useCallback(async (username, password) => {
    const res = await fetch(apiUrl('/api/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ error: 'login failed' }));
      throw new Error(err.error);
    }
    const data = await res.json();
    setToken(data.token);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('fraudoor_token');
    localStorage.removeItem('fraudoor_user');
  }, []);

  const authedFetch = useCallback(async (url, opts = {}) => {
    const headers = { ...opts.headers };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(url, { ...opts, headers });
    return res;
  }, [token]);

  return (
    <AuthContext.Provider value={{ user, token, login, logout, authedFetch, isAdmin: user?.role === 'admin' }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
