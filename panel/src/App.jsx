import React, { useState, useEffect } from 'react';
import Servers from './pages/Servers';
import ServerDashboard from './pages/ServerDashboard';
import Injection from './pages/Injection';
import { apiUrl } from './api';

const TABS = { SERVERS: 'servers', INJECTION: 'injection' };

function ServersIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><circle cx="6" cy="6" r="1" fill="currentColor"/><circle cx="6" cy="18" r="1" fill="currentColor"/></svg>;
}

function InjectionIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="16.5" y1="9.4" x2="7.5" y2="4.21"/><path d="M21 16a2 2 0 0 1-1.09 1.76l-6 3.24a2 2 0 0 1-1.82 0l-6-3.24A2 2 0 0 1 5 16v-6a2 2 0 0 1 1.09-1.76l6-3.24a2 2 0 0 1 1.82 0l6 3.24A2 2 0 0 1 21 10Z"/><line x1="12" y1="22" x2="12" y2="11"/></svg>;
}

export default function App() {
  const [tab, setTab] = useState(TABS.SERVERS);
  const [selectedServer, setSelectedServer] = useState(null);
  const [servers, setServers] = useState([]);

  useEffect(() => {
    const fetchServers = async () => {
      try {
        const res = await fetch(apiUrl('/api/servers'));
        if (res.ok) setServers(await res.json());
      } catch {}
    };
    fetchServers();
    const interval = setInterval(fetchServers, 5000);
    return () => clearInterval(interval);
  }, []);

  if (selectedServer) {
    return (
      <div className="app-layout">
        <Sidebar tab={TABS.SERVERS} setTab={setTab} servers={servers} />
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
      <Sidebar tab={tab} setTab={setTab} servers={servers} />
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
      </div>
    </div>
  );
}

function Sidebar({ tab, setTab, servers }) {
  const online = servers.filter(s => s.online).length;
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">F</div>
      <nav className="sidebar-nav">
        <button
          className={`sidebar-btn ${tab === 'servers' ? 'active' : ''}`}
          onClick={() => setTab('servers')}
          title="Servers"
        >
          <ServersIcon />
          <span>{online}</span>
          {online > 0 && <span className="sidebar-count">{online}</span>}
        </button>
        <button
          className={`sidebar-btn ${tab === 'injection' ? 'active' : ''}`}
          onClick={() => setTab('injection')}
          title="Injector"
        >
          <InjectionIcon />
          <span>Inject</span>
        </button>
      </nav>
    </aside>
  );
}
