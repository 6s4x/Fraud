import React from 'react';

export default function ServerCard({ server, onClick }) {
  const online = server.online;
  const players = server.playerCount != null ? `${server.playerCount}/${server.maxPlayers || '?'}` : '?';
  const playerPercent = server.maxPlayers > 0 ? Math.min((server.playerCount || 0) / server.maxPlayers * 100, 100) : 0;
  const lastSeen = server.lastSeen ? new Date(server.lastSeen).toLocaleString() : 'never';
  const tps = server.tps != null ? server.tps : 20;
  const tpsColor = tps > 19 ? 'var(--green)' : tps > 15 ? 'var(--orange)' : 'var(--red)';
  const tpsWidth = Math.min((tps / 20) * 100, 100);

  return (
    <div className={`server-card ${online ? 'online' : ''}`} onClick={onClick}>
      <div className="server-card-top">
        <span className="server-card-name">{server.name || server.ip || 'Unknown'}</span>
        <span className="server-card-status">
          <span className={`status-dot ${online ? 'online' : 'offline'}`} />
          <span style={{ color: online ? 'var(--green)' : 'var(--text-muted)' }}>
            {online ? 'online' : 'offline'}
          </span>
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
        <span className="value" style={{ color: tpsColor }}>{tps.toFixed?.(1) || '?'}</span>
      </div>
      <div className="tps-bar">
        <div className="tps-bar-fill" style={{ width: `${tpsWidth}%`, background: tpsColor }} />
      </div>
      {online && (
        <div className="server-card-progress">
          <div className={`server-card-progress-bar ${playerPercent > 90 ? 'full' : ''}`} style={{ width: `${playerPercent}%` }} />
        </div>
      )}
      <div className="server-card-footer">
        <span>{server.type || 'paper'}</span>
        <span>{lastSeen}</span>
      </div>
    </div>
  );
}
