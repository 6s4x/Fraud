import React from 'react';

export default function ServerCard({ server, onClick }) {
  const online = server.online;
  return (
    <div className="server-card" onClick={onClick}>
      <div className="top">
        <span className="name">{server.name || server.ip}</span>
        <span className={`status ${online ? 'online' : 'offline'}`}>
          {online ? 'online' : 'offline'}
        </span>
      </div>
      <div className="info">
        <span>IP</span><span>{server.ip}</span>
        <span>Port</span><span>{server.port}</span>
        <span>Players</span><span>{server.playerCount}/{server.maxPlayers}</span>
        <span>Version</span><span>{server.version || '?'}</span>
        <span>TPS</span><span>{server.tps?.toFixed?.(1) || '?'}</span>
        <span>Type</span><span>{server.type || 'paper'}</span>
      </div>
    </div>
  );
}
