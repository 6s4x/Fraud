import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import multer from 'multer';
import { getStore } from './ws.js';
import { spawn } from 'child_process';
import { getUsers, addUser, removeUser, setUserRole, changePassword } from './users.js';
import { tmpdir } from 'os';
import { join } from 'path';
import { writeFileSync, unlinkSync, mkdtempSync, readFileSync, existsSync } from 'fs';
import { loginHandler, authMiddleware, adminOnly } from './auth.js';

export const apiRouter = Router();
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 50 * 1024 * 1024 } });

function store() {
  return getStore();
}

// --- Auth ---
apiRouter.post('/login', loginHandler);

// --- Servers (public for discovery) ---
apiRouter.get('/servers', (req, res) => {
  const list = store().getServers().map(s => ({
    id: s.id, name: s.name, ip: s.ip, port: s.port,
    playerCount: s.playerCount || 0, maxPlayers: s.maxPlayers || 0,
    motd: s.motd || '', version: s.version || '',
    online: s.online || false, tps: s.tps || 20,
    lastSeen: s.lastSeen, type: s.type || 'paper',
    players: s.players || [],
  }));
  res.json(list);
});

apiRouter.get('/servers/:id', (req, res) => {
  const s = store().getServer(req.params.id);
  if (!s) return res.status(404).json({ error: 'server not found' });
  res.json({
    id: s.id, name: s.name, ip: s.ip, port: s.port,
    playerCount: s.playerCount || 0, maxPlayers: s.maxPlayers || 0,
    motd: s.motd || '', version: s.version || '',
    online: s.online || false, tps: s.tps || 20,
    lastSeen: s.lastSeen, type: s.type || 'paper',
    plugins: s.plugins || [], players: s.players || [],
    members: s.members || [],
  });
});

// --- Authorized members (auth required) ---
apiRouter.get('/servers/:id/members', authMiddleware, (req, res) => {
  const members = store().getMembers(req.params.id);
  res.json({ members });
});

apiRouter.post('/servers/:id/members', authMiddleware, adminOnly, (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'name required' });
  const ok = store().addMember(req.params.id, name);
  if (!ok) return res.status(404).json({ error: 'server not found' });
  res.json({ ok: true, members: store().getMembers(req.params.id) });
});

apiRouter.delete('/servers/:id/members/:name', authMiddleware, adminOnly, (req, res) => {
  const ok = store().removeMember(req.params.id, req.params.name);
  if (!ok) return res.status(404).json({ error: 'server or member not found' });
  res.json({ ok: true, members: store().getMembers(req.params.id) });
});

// --- User management (admin only) ---
apiRouter.get('/users', authMiddleware, adminOnly, (req, res) => {
  res.json({ users: getUsers() });
});

apiRouter.post('/users', authMiddleware, adminOnly, (req, res) => {
  const { username, password, role } = req.body;
  if (!username || !password) return res.status(400).json({ error: 'username and password required' });
  if (!addUser(username, password, role || 'user')) return res.status(409).json({ error: 'user already exists' });
  res.json({ ok: true, users: getUsers() });
});

apiRouter.delete('/users/:username', authMiddleware, adminOnly, (req, res) => {
  if (req.params.username === 'root') return res.status(403).json({ error: 'cannot remove root' });
  if (!removeUser(req.params.username)) return res.status(404).json({ error: 'user not found' });
  res.json({ ok: true, users: getUsers() });
});

apiRouter.patch('/users/:username/role', authMiddleware, adminOnly, (req, res) => {
  const { role } = req.body;
  if (!role || !['admin', 'user'].includes(role)) return res.status(400).json({ error: 'role must be admin or user' });
  if (req.params.username === 'root' && role !== 'admin') return res.status(403).json({ error: 'root must stay admin' });
  if (!setUserRole(req.params.username, role)) return res.status(404).json({ error: 'user not found' });
  res.json({ ok: true, users: getUsers() });
});

apiRouter.patch('/users/:username/password', authMiddleware, (req, res) => {
  if (req.user.username !== req.params.username && req.user.role !== 'admin') {
    return res.status(403).json({ error: 'can only change own password' });
  }
  const { password } = req.body;
  if (!password) return res.status(400).json({ error: 'password required' });
  if (!changePassword(req.params.username, password)) return res.status(404).json({ error: 'user not found' });
  res.json({ ok: true });
});

// --- Logs (admin only) ---
apiRouter.get('/logs', authMiddleware, adminOnly, (req, res) => {
  const { type, serverId } = req.query;
  const logs = store().getLogs({ type, serverId });
  res.json({ logs });
});

// --- Plugin registration ---
apiRouter.post('/plugin/register', (req, res) => {
  const { secret, name, ip, port, version, type } = req.body;
  if (secret !== process.env.PLUGIN_SECRET && process.env.PLUGIN_SECRET) {
    return res.status(401).json({ error: 'invalid secret' });
  }
  const id = uuidv4();
  store().addOrUpdateServer(id, { name, ip, port, version, type, online: true });
  res.json({ id });
});

apiRouter.post('/plugin/heartbeat', (req, res) => {
  const { id, playerCount, maxPlayers, motd, tps, players, online } = req.body;
  const s = store().getServer(id);
  if (!s) return res.status(404).json({ error: 'unknown server' });
  store().addOrUpdateServer(id, { playerCount, maxPlayers, motd, tps, players, online });
  res.json({ ok: true });
});

// --- Injection ---
apiRouter.post('/inject', upload.single('plugin'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'no plugin file uploaded' });
  if (!req.file.originalname.endsWith('.jar')) return res.status(400).json({ error: 'file must be a .jar' });

  const platform = req.body.platform || 'paper';
  const tmpDir = mkdtempSync(join(tmpdir(), 'fraudoor-inject-'));
  const inPath = join(tmpDir, req.file.originalname);
  const outPath = join(tmpDir, `FRAUDED-${req.file.originalname}`);

  writeFileSync(inPath, req.file.buffer);

  const injectorJar = join(process.cwd(), 'injector.jar');

  if (!existsSync(injectorJar)) {
    return res.status(501).json({
      error: 'injector backend not available',
      hint: 'Download the injector CLI from /api/injector/download and run it locally',
    });
  }

  const proto = req.headers['x-forwarded-proto'] === 'https' ? 'wss' : 'ws';
  const host = req.headers['host'] || 'localhost:8080';
  const serverUrl = process.env.FRAUDOOR_SERVER || `${proto}://${host}/ws`;

  const proc = spawn('java', [
    '-jar', injectorJar,
    '--input', inPath,
    '--output', outPath,
    '--platform', platform,
    '--server', serverUrl,
    '--secret', process.env.PLUGIN_SECRET || '',
  ]);

  let stderr = '';
  proc.stderr.on('data', d => stderr += d.toString());

  proc.on('close', (code) => {
    try { unlinkSync(inPath); } catch {}
    if (code !== 0) {
      try { unlinkSync(outPath); } catch {}
      return res.status(500).json({ error: `injector failed: ${stderr}` });
    }
    try {
      const data = readFileSync(outPath);
      unlinkSync(outPath);
      res.set('Content-Type', 'application/java-archive');
      res.set('Content-Disposition', `attachment; filename="FRAUDED-${req.file.originalname}"`);
      res.send(data);
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });
});
