const servers = new Map();

export function initStore() {
  return {
    getServers() {
      return Array.from(servers.values());
    },

    getServer(id) {
      return servers.get(id) || null;
    },

    addOrUpdateServer(id, data) {
      const existing = servers.get(id) || {};
      const updated = {
        ...existing,
        ...data,
        id,
        lastSeen: Date.now(),
      };
      servers.set(id, updated);
      return updated;
    },

    removeServer(id) {
      servers.delete(id);
    },

    getServerByWs(ws) {
      for (const s of servers.values()) {
        if (s.ws === ws) return s;
      }
      return null;
    },
  };
}
