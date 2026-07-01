const API_URL = import.meta.env.VITE_API_URL || '';
const WS_URL = import.meta.env.VITE_WS_URL || `${(() => {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}`;
})()}/ws`;

let ws = null;
let listeners = {};
let reconnectTimer = null;

export function connectWebSocket() {
  if (ws && ws.readyState === WebSocket.OPEN) return;

  ws = new WebSocket(WS_URL);

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
    reconnectTimer = setTimeout(() => connectWebSocket(), 3000);
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
