import React, { useState, useEffect } from 'react';
import { useAuth } from '../AuthContext';
import { apiUrl } from '../api';

export default function Users() {
  const { authedFetch, user } = useAuth();
  const [users, setUsers] = useState([]);
  const [showAdd, setShowAdd] = useState(false);
  const [newUser, setNewUser] = useState({ username: '', password: '', role: 'user' });
  const [error, setError] = useState('');
  const [editingPwd, setEditingPwd] = useState(null);
  const [pwdValue, setPwdValue] = useState('');

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      const res = await authedFetch(apiUrl('/api/users'));
      if (res.ok) {
        const data = await res.json();
        setUsers(data.users || []);
      }
    } catch {}
  };

  const handleAdd = async () => {
    if (!newUser.username || !newUser.password) return;
    setError('');
    try {
      const res = await authedFetch(apiUrl('/api/users'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newUser),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: 'failed' }));
        throw new Error(err.error);
      }
      const data = await res.json();
      setUsers(data.users);
      setNewUser({ username: '', password: '', role: 'user' });
      setShowAdd(false);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleDelete = async (username) => {
    if (!confirm(`Delete user "${username}"?`)) return;
    try {
      const res = await authedFetch(apiUrl(`/api/users/${encodeURIComponent(username)}`), { method: 'DELETE' });
      if (!res.ok) throw new Error('delete failed');
      const data = await res.json();
      setUsers(data.users);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleRole = async (username, role) => {
    try {
      const res = await authedFetch(apiUrl(`/api/users/${encodeURIComponent(username)}/role`), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role }),
      });
      if (!res.ok) throw new Error('role change failed');
      const data = await res.json();
      setUsers(data.users);
    } catch (err) {
      setError(err.message);
    }
  };

  const handlePwdChange = async (username) => {
    if (!pwdValue) return;
    try {
      const res = await authedFetch(apiUrl(`/api/users/${encodeURIComponent(username)}/password`), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: pwdValue }),
      });
      if (!res.ok) throw new Error('password change failed');
      setEditingPwd(null);
      setPwdValue('');
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <h1 className="page-title">Users</h1>
      <p className="page-subtitle">Manage panel accounts and permissions</p>

      <div className="users-toolbar">
        <button className="users-add-btn" onClick={() => setShowAdd(!showAdd)}>
          {showAdd ? 'Cancel' : '+ Add User'}
        </button>
      </div>

      {showAdd && (
        <div className="users-add-form">
          <input
            className="members-input"
            placeholder="Username"
            value={newUser.username}
            onChange={e => setNewUser({ ...newUser, username: e.target.value })}
          />
          <input
            className="members-input"
            type="password"
            placeholder="Password"
            value={newUser.password}
            onChange={e => setNewUser({ ...newUser, password: e.target.value })}
          />
          <select
            className="members-input"
            value={newUser.role}
            onChange={e => setNewUser({ ...newUser, role: e.target.value })}
            style={{ width: 'auto' }}
          >
            <option value="user">User</option>
            <option value="admin">Admin</option>
          </select>
          <button className="members-add-btn" onClick={handleAdd}>Create</button>
        </div>
      )}

      {error && <div className="login-error" style={{ margin: '12px 0' }}>{error}</div>}

      <div className="users-list">
        <div className="users-header">
          <span>Username</span>
          <span>Role</span>
          <span>Actions</span>
        </div>
        {users.map(u => (
          <div key={u.username} className={`users-row ${u.username === 'root' ? 'root' : ''}`}>
            <span className="users-name">
              {u.username}
              {u.username === 'root' && <span className="users-badge">root</span>}
              {u.username === user?.username && <span className="users-badge you">you</span>}
            </span>
            <span className={`users-role ${u.role}`}>
              {u.role === 'admin' ? 'Admin' : 'User'}
            </span>
            <span className="users-actions">
              {editingPwd === u.username ? (
                <span className="users-pwd-edit">
                  <input
                    type="password"
                    className="pwd-input"
                    value={pwdValue}
                    onChange={e => setPwdValue(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handlePwdChange(u.username)}
                    placeholder="New password"
                  />
                  <button className="users-action-btn save" onClick={() => handlePwdChange(u.username)}>Set</button>
                  <button className="users-action-btn" onClick={() => { setEditingPwd(null); setPwdValue(''); }}>X</button>
                </span>
              ) : (
                <button className="users-action-btn" onClick={() => setEditingPwd(u.username)}>Change Password</button>
              )}
              {u.role === 'admin' ? (
                <button className="users-action-btn" onClick={() => handleRole(u.username, 'user')}>Demote to User</button>
              ) : (
                <button className="users-action-btn promote" onClick={() => handleRole(u.username, 'admin')}>Promote to Admin</button>
              )}
              {u.username !== 'root' && (
                <button className="users-action-btn danger" onClick={() => handleDelete(u.username)}>Delete</button>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
