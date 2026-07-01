import React, { useState, useEffect, useCallback } from 'react';
import { connectWebSocket, sendWS, onWS } from '../api';

export default function FileBrowser({ serverId }) {
  const [cwd, setCwd] = useState('');
  const [entries, setEntries] = useState([]);
  const [editingFile, setEditingFile] = useState(null);
  const [fileContent, setFileContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [filePath, setFilePath] = useState('');

  const requestId = React.useId();

  const listDir = useCallback((path) => {
    setLoading(true);
    sendWS('panel:file:list', { serverId, path, requestId });
  }, [serverId, requestId]);

  useEffect(() => {
    connectWebSocket();
    listDir('');
  }, [listDir]);

  useEffect(() => {
    const unsub1 = onWS('server:file:list', (payload) => {
      if (payload.requestId !== requestId) return;
      setEntries(payload.entries || []);
      setCwd(payload.path || '');
      setLoading(false);
    });

    const unsub2 = onWS('server:file:read', (payload) => {
      if (payload.requestId !== requestId) return;
      setFileContent(payload.content || '');
      setFilePath(payload.path || '');
      setEditingFile(payload.path);
    });

    const unsub3 = onWS('server:file:write', (payload) => {
      if (payload.requestId !== requestId) return;
      setEditingFile(null);
      setFileContent('');
      listDir(cwd);
    });

    const unsub4 = onWS('server:file:delete', (payload) => {
      if (payload.requestId !== requestId) return;
      setEditingFile(null);
      setFileContent('');
      listDir(cwd);
    });

    return () => { unsub1(); unsub2(); unsub3(); unsub4(); };
  }, [requestId, cwd, listDir]);

  const openDir = (path) => listDir(path);

  const openFile = (path) => {
    sendWS('panel:file:read', { serverId, path, requestId });
  };

  const saveFile = () => {
    sendWS('panel:file:write', { serverId, path: editingFile, content: fileContent, requestId });
  };

  const deleteFile = (path) => {
    if (!confirm(`Delete ${path}?`)) return;
    sendWS('panel:file:delete', { serverId, path, requestId });
  };

  const handleUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      sendWS('panel:file:upload', {
        serverId,
        path: cwd ? `${cwd}/${file.name}` : file.name,
        content: reader.result.split(',')[1] || reader.result,
        requestId,
      });
    };
    reader.readAsDataURL(file);
  };

  const pathParts = cwd ? cwd.split('/').filter(Boolean) : [];

  if (editingFile) {
    return (
      <div>
        <div className="breadcrumb">
          <span onClick={() => { setEditingFile(null); listDir(cwd); }}>files</span>
          <span className="sep">/</span>
          <span>{editingFile}</span>
        </div>
        <textarea
          className="file-editor"
          value={fileContent}
          onChange={e => setFileContent(e.target.value)}
          spellCheck={false}
        />
        <div className="editor-actions">
          <button className="btn-save" onClick={saveFile}>save</button>
          <button className="btn-cancel" onClick={() => { setEditingFile(null); listDir(cwd); }}>cancel</button>
          <button className="btn-delete" onClick={() => deleteFile(editingFile)} style={{ marginLeft: 'auto' }}>delete</button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="breadcrumb">
        <span onClick={() => listDir('')}>root</span>
        {pathParts.map((part, i) => (
          <React.Fragment key={i}>
            <span className="sep">/</span>
            <span onClick={() => listDir(pathParts.slice(0, i + 1).join('/'))}>{part}</span>
          </React.Fragment>
        ))}
        <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text2)' }}>
          {loading ? 'loading...' : `${entries.length} items`}
        </span>
      </div>
      <div className="file-browser">
        {entries.map((e, i) => (
          <div
            key={i}
            className={`file-entry ${e.isDirectory ? 'dir' : ''}`}
            onClick={() => e.isDirectory ? openDir(e.path) : openFile(e.path)}
          >
            <span className="icon">{e.isDirectory ? '📁' : '📄'}</span>
            <span>{e.name}</span>
            {!e.isDirectory && <span className="size">{formatSize(e.size)}</span>}
          </div>
        ))}
        {entries.length === 0 && !loading && (
          <div style={{ color: 'var(--text2)', padding: '20px', textAlign: 'center', fontSize: '13px' }}>
            empty directory
          </div>
        )}
      </div>
      <div className="upload-area">
        <label style={{ cursor: 'pointer', color: 'var(--accent)', fontSize: '13px' }}>
          upload file
          <input type="file" onChange={handleUpload} />
        </label>
      </div>
    </div>
  );
}

function formatSize(bytes) {
  if (!bytes) return '';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
  return `${size.toFixed(1)} ${units[i]}`;
}
