import React, { useState, useEffect } from 'react';
import { useAuth } from '../AuthContext';

export default function AuthorizedMembers({ serverId }) {
  const { authedFetch, isAdmin } = useAuth();
  const [members, setMembers] = useState([]);
  const [newName, setNewName] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!serverId) return;
    authedFetch(`/api/servers/${serverId}/members`)
      .then(r => r.json())
      .then(data => {
        if (data.members) setMembers(data.members);
      })
      .catch(() => {});
  }, [serverId]);

  const handleAdd = async () => {
    if (!newName.trim()) return;
    setError('');
    try {
      const res = await authedFetch(`/api/servers/${serverId}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newName.trim() }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: 'failed' }));
        throw new Error(err.error);
      }
      const data = await res.json();
      if (data.members) setMembers(data.members);
      setNewName('');
    } catch (err) {
      setError(err.message);
    }
  };

  const handleRemove = async (name) => {
    try {
      const res = await authedFetch(`/api/servers/${serverId}/members/${encodeURIComponent(name)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('failed to remove');
      const data = await res.json();
      if (data.members) setMembers(data.members);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <div className="dashboard-panel-header">
        <h3>Authorized Members ({members.length})</h3>
      </div>
      {isAdmin && (
        <div className="members-add">
          <input
            type="text"
            placeholder="Add username..."
            value={newName}
            onChange={e => setNewName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleAdd()}
            className="members-input"
          />
          <button className="members-add-btn" onClick={handleAdd} disabled={!newName.trim()}>Add</button>
        </div>
      )}
      {error && <div className="login-error" style={{ margin: '8px 0' }}>{error}</div>}
      <div className="members-list">
        {members.length === 0 && (
          <div style={{ color: 'var(--text-dim)', fontSize: '13px', padding: '12px 0' }}>
            No authorized members yet.
          </div>
        )}
        {members.map((name) => (
          <div key={name} className="member-row">
            <span className="member-name">{name}</span>
            {isAdmin && (
              <button className="member-remove-btn" onClick={() => handleRemove(name)}>
                Remove
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
