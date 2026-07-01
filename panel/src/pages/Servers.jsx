import React from 'react';
import ServerCard from '../components/ServerCard';

export default function Servers({ servers, onSelect }) {
  const online = servers.filter(s => s.online);
  const offline = servers.filter(s => !s.online);

  if (servers.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
            <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
            <circle cx="6" cy="6" r="1" fill="currentColor"/>
            <circle cx="6" cy="18" r="1" fill="currentColor"/>
          </svg>
        </div>
        <h2>No servers detected</h2>
        <p>Inject a plugin and add it to your server. When the server starts, the agent will connect here automatically.</p>
      </div>
    );
  }

  return (
    <div>
      {online.length > 0 && (
        <>
          <div className="section-header">
            <span className="section-dot" style={{ background: 'var(--green)' }} />
            Online ({online.length})
          </div>
          <div className="server-grid">
            {online.map(s => (
              <ServerCard key={s.id} server={s} onClick={() => onSelect(s)} />
            ))}
          </div>
        </>
      )}
      {offline.length > 0 && (
        <>
          <div className="section-header" style={{ marginTop: online.length > 0 ? 32 : 0 }}>
            <span className="section-dot" style={{ background: 'var(--text-muted)' }} />
            Offline ({offline.length})
          </div>
          <div className="server-grid">
            {offline.map(s => (
              <ServerCard key={s.id} server={s} onClick={() => onSelect(s)} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
