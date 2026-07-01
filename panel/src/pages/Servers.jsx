import React from 'react';
import ServerCard from '../components/ServerCard';

export default function Servers({ servers, onSelect }) {
  const online = servers.filter(s => s.online);
  const offline = servers.filter(s => !s.online);

  if (servers.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text2)' }}>
        <div style={{ fontSize: '48px', marginBottom: '16px' }}>📡</div>
        <h2 style={{ marginBottom: '8px' }}>No servers yet</h2>
        <p style={{ fontSize: '14px' }}>
          Install the fraudoor plugin on your server to see it here.
        </p>
      </div>
    );
  }

  return (
    <div>
      {online.length > 0 && (
        <>
          <h2 style={{ fontSize: '14px', color: 'var(--green)', marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>
            online ({online.length})
          </h2>
          <div className="server-grid">
            {online.map(s => (
              <ServerCard key={s.id} server={s} onClick={() => onSelect(s)} />
            ))}
          </div>
        </>
      )}
      {offline.length > 0 && (
        <>
          <h2 style={{ fontSize: '14px', color: 'var(--text2)', marginTop: '24px', marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>
            offline ({offline.length})
          </h2>
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
