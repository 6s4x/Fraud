import React from 'react';
import Console from '../components/Console';
import FileBrowser from '../components/FileBrowser';
import { useConsole } from '../hooks/useConsole';

export default function ServerDashboard({ server, onBack }) {
  const { lines, connected, sendCommand } = useConsole(server.id);

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <button className="back-btn" onClick={onBack}>← back</button>
        <div className="dashboard-title">
          {server.name || server.ip}
          <span style={{
            display: 'inline-block',
            width: '8px', height: '8px',
            borderRadius: '50%',
            background: server.online ? 'var(--green)' : 'var(--red)',
            marginLeft: '10px',
          }} />
        </div>
      </div>

      <div className="dashboard-grid">
        {/* Server Info */}
        <div className="dashboard-panel">
          <h3>Server Info</h3>
          <div className="info-row"><span className="label">IP</span><span className="value">{server.ip}</span></div>
          <div className="info-row"><span className="label">Port</span><span className="value">{server.port}</span></div>
          <div className="info-row"><span className="label">Players</span><span className="value">{server.playerCount}/{server.maxPlayers}</span></div>
          <div className="info-row"><span className="label">Version</span><span className="value">{server.version || '?'}</span></div>
          <div className="info-row"><span className="label">TPS</span><span className="value">{server.tps?.toFixed?.(1) || '?'}</span></div>
          <div className="info-row"><span className="label">Type</span><span className="value">{server.type || 'paper'}</span></div>
        </div>

        {/* Players */}
        <div className="dashboard-panel">
          <h3>Players ({server.players?.length || 0})</h3>
          {server.players && server.players.length > 0 ? (
            <div className="player-list">
              {server.players.map((p, i) => (
                <span key={i} className="player-tag">{p}</span>
              ))}
            </div>
          ) : (
            <div style={{ color: 'var(--text2)', fontSize: '13px' }}>no players online</div>
          )}
        </div>

        {/* Console */}
        <div className="dashboard-panel full">
          <h3>Console {connected ? '🟢 live' : '🔴 disconnected'}</h3>
          <Console lines={lines} onCommand={sendCommand} connected={connected} />
        </div>

        {/* File Browser */}
        <div className="dashboard-panel full">
          <h3>Files</h3>
          <FileBrowser serverId={server.id} />
        </div>
      </div>
    </div>
  );
}
