import React from 'react';
import Console from '../components/Console';
import FileBrowser from '../components/FileBrowser';
import { useConsole } from '../hooks/useConsole';

export default function ServerDashboard({ server, onBack }) {
  const { lines, connected, sendCommand } = useConsole(server.id);

  return (
    <div>
      <div className="dashboard-header">
        <button className="back-btn" onClick={onBack}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 12H5"/><polyline points="12 19 5 12 12 5"/></svg>
        </button>
        <div className="dashboard-title">
          {server.name || server.ip || 'Server'}
          <span className={`dashboard-server-status ${server.online ? 'online' : 'offline'}`} />
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-panel">
          <div className="dashboard-panel-header">
            <h3>Server Info</h3>
          </div>
          <div className="info-rows">
            <div className="info-row">
              <span className="label">Address</span>
              <span className="value">{server.ip || '-'}:{server.port || '?'}</span>
            </div>
            <div className="info-row">
              <span className="label">Players</span>
              <span className="value">{server.playerCount || 0}/{server.maxPlayers || '?'}</span>
            </div>
            <div className="info-row">
              <span className="label">Version</span>
              <span className="value">{server.version || '?'}</span>
            </div>
            <div className="info-row">
              <span className="label">TPS</span>
              <span className="value">{server.tps?.toFixed?.(1) || '?'}</span>
            </div>
            <div className="info-row">
              <span className="label">Type</span>
              <span className="value">{server.type || 'paper'}</span>
            </div>
          </div>
        </div>

        <div className="dashboard-panel">
          <div className="dashboard-panel-header">
            <h3>Players ({server.players?.length || 0})</h3>
          </div>
          {server.players && server.players.length > 0 ? (
            <div className="player-list">
              {server.players.map((p, i) => (
                <span key={i} className="player-tag">{p}</span>
              ))}
            </div>
          ) : (
            <div style={{ color: 'var(--text-dim)', fontSize: '13px' }}>No players online</div>
          )}
        </div>

        <div className="dashboard-panel full">
          <div className="dashboard-panel-header">
            <h3>Console</h3>
            <span className={`dashboard-panel-badge ${connected ? 'live' : 'off'}`}>
              {connected ? 'live' : 'disconnected'}
            </span>
          </div>
          <Console lines={lines} onCommand={sendCommand} connected={connected} />
        </div>

        <div className="dashboard-panel full">
          <div className="dashboard-panel-header">
            <h3>Files</h3>
          </div>
          <FileBrowser serverId={server.id} />
        </div>
      </div>
    </div>
  );
}
