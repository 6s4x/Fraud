import React, { useState, useEffect } from 'react';
import Servers from './pages/Servers';
import ServerDashboard from './pages/ServerDashboard';
import Injection from './pages/Injection';
import { apiUrl } from './api';

const TABS = {
  SERVERS: 'servers',
  INJECTION: 'injection',
};

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
      <ServerDashboard
        server={selectedServer}
        onBack={() => setSelectedServer(null)}
      />
    );
  }

  return (
    <div className="app">
      <header className="header">
        <h1 className="logo">fraudoor</h1>
        <span className="tagline">THE rcon tool</span>
      </header>

      <nav className="tabs">
        <button
          className={`tab ${tab === TABS.SERVERS ? 'active' : ''}`}
          onClick={() => setTab(TABS.SERVERS)}
        >
          Servers ({servers.length})
        </button>
        <button
          className={`tab ${tab === TABS.INJECTION ? 'active' : ''}`}
          onClick={() => setTab(TABS.INJECTION)}
        >
          Injection
        </button>
      </nav>

      <main className="content">
        {tab === TABS.SERVERS && (
          <Servers servers={servers} onSelect={setSelectedServer} />
        )}
        {tab === TABS.INJECTION && <Injection />}
      </main>
    </div>
  );
}
