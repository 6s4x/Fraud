import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const USERS_FILE = join(__dirname, '..', 'users.json');

const DEFAULT_USERS = [
  { username: 'root', password: 'cipa123', role: 'admin' },
  { username: 'rwijkoper', password: 'cwel123', role: 'user' },
  { username: 'spacja', password: 'dupa123', role: 'user' },
];

let users = [];

export function initUsers() {
  if (existsSync(USERS_FILE)) {
    try {
      users = JSON.parse(readFileSync(USERS_FILE, 'utf-8'));
      return;
    } catch {}
  }
  users = DEFAULT_USERS.map(u => ({ ...u }));
  saveUsers();
}

function saveUsers() {
  try {
    writeFileSync(USERS_FILE, JSON.stringify(users, null, 2));
  } catch {}
}

export function loginUser(username, password) {
  return users.find(u => u.username === username && u.password === password) || null;
}

export function getUsers() {
  return users.map(u => ({ username: u.username, role: u.role }));
}

export function addUser(username, password, role) {
  if (users.find(u => u.username === username)) return false;
  users.push({ username, password, role: role || 'user' });
  saveUsers();
  return true;
}

export function removeUser(username) {
  const idx = users.findIndex(u => u.username === username);
  if (idx < 0) return false;
  users.splice(idx, 1);
  saveUsers();
  return true;
}

export function setUserRole(username, role) {
  const u = users.find(u => u.username === username);
  if (!u) return false;
  u.role = role;
  saveUsers();
  return true;
}

export function changePassword(username, newPassword) {
  const u = users.find(u => u.username === username);
  if (!u) return false;
  u.password = newPassword;
  saveUsers();
  return true;
}
