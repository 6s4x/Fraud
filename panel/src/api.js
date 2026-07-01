const API_URL = import.meta.env.VITE_API_URL || '';
const WS_URL = import.meta.env.VITE_WS_URL || `${(() => {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}`;
})()}/ws`;

let ws = null;
let listeners = {};
let reconnectTimer = null;
let currentToken = null;

export function connectWebSocket(token) {
  currentToken = token;
  if (ws) {
    try { ws.close(); } catch {}
    ws = null;
  }
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }

  const url = token ? `${WS_URL}?token=${encodeURIComponent(token)}` : WS_URL;
  ws = new WebSocket(url);

  ws.onopen = () => {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    emit('connected');
  };

  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data);
      emit(msg.type, msg.payload);
    } catch {}
  };

  ws.onclose = () => {
    emit('disconnected');
    reconnectTimer = setTimeout(() => connectWebSocket(currentToken), 3000);
  };

  ws.onerror = () => {};
}

export function sendWS(type, payload) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  ws.send(JSON.stringify({ type, payload }));
}

export function onWS(type, fn) {
  if (!listeners[type]) listeners[type] = [];
  listeners[type].push(fn);
  return () => {
    listeners[type] = listeners[type].filter(l => l !== fn);
  };
}

export function apiUrl(path) {
  return `${API_URL}${path}`;
}

function emit(type, payload) {
  (listeners[type] || []).forEach(fn => fn(payload));
  (listeners['*'] || []).forEach(fn => fn(type, payload));
}
