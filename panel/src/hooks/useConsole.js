import { useState, useEffect, useRef, useCallback } from 'react';
import { sendWS, onWS } from '../api';

export function useConsole(serverId) {
  const [lines, setLines] = useState([]);
  const [connected, setConnected] = useState(false);
  const maxLines = 5000;

  useEffect(() => {

    const unsub1 = onWS('connected', () => {
      setConnected(true);
      if (serverId) sendWS('panel:subscribe', { serverId });
    });

    const unsub2 = onWS('disconnected', () => {
      setConnected(false);
    });

    const unsub3 = onWS('server:console', (payload) => {
      if (payload.serverId === serverId) {
        setLines(prev => {
          const next = [...prev, payload];
          return next.length > maxLines ? next.slice(next.length - maxLines) : next;
        });
      }
    });

    return () => {
      unsub1(); unsub2(); unsub3();
      if (serverId) sendWS('panel:unsubscribe', { serverId });
    };
  }, [serverId]);

  const sendCommand = useCallback((cmd) => {
    sendWS('panel:command', { serverId, command: cmd });
  }, [serverId]);

  return { lines, connected, sendCommand };
}
