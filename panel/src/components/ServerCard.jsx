import React from 'react';

export default function ServerCard({ server, onClick }) {
  const online = server.online;
  const players = server.playerCount != null ? `${server.playerCount}/${server.maxPlayers || '?'}` : '?';
  const lastSeen = server.lastSeen ? new Date(server.lastSeen).toLocaleString() : 'never';

  return (
    <div className={`server-card ${online ? 'online' : ''}`} onClick={onClick}>
      <div className="server-card-top">
        <span className="server-card-name">{server.name || server.ip || 'Unknown'}</span>
        <span className="server-card-status">
          <span className={`status-dot ${online ? 'online' : 'offline'}`} />
          {online ? 'online' : 'offline'}
        </span>
      </div>
      <div className="server-card-info">
        <span className="label">Address</span>
        <span className="value">{server.ip || '-'}:{server.port || '?'}</span>
        <span className="label">Players</span>
        <span className="value">{players}</span>
        <span className="label">Version</span>
        <span className="value">{server.version || '?'}</span>
        <span className="label">TPS</span>
        <span className="value">{server.tps?.toFixed?.(1) || '?'}</span>
      </div>
      <div className="server-card-footer">
        <span>{server.type || 'paper'}</span>
        <span>{lastSeen}</span>
      </div>
    </div>
  );
}
