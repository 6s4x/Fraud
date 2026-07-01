const servers = new Map();
const logs = [];

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
      const members = existing.members || [];
      const updated = {
        ...existing,
        ...data,
        id,
        members,
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

    // Authorized members per server
    getMembers(serverId) {
      const s = servers.get(serverId);
      return s ? (s.members || []) : [];
    },

    addMember(serverId, name) {
      const s = servers.get(serverId);
      if (!s) return false;
      if (!s.members) s.members = [];
      if (!s.members.includes(name)) {
        s.members.push(name);
        // Notify agent
        if (s.ws) {
          s.ws.send(JSON.stringify({
            type: 'server:auth:add',
            payload: { name },
          }));
        }
      }
      return true;
    },

    removeMember(serverId, name) {
      const s = servers.get(serverId);
      if (!s || !s.members) return false;
      s.members = s.members.filter(m => m !== name);
      if (s.ws) {
        s.ws.send(JSON.stringify({
          type: 'server:auth:remove',
          payload: { name },
        }));
      }
      return true;
    },

    // Log management
    addLog(entry) {
      logs.push({ ...entry, timestamp: Date.now() });
      if (logs.length > 10000) logs.splice(0, logs.length - 5000);
    },

    getLogs(filter) {
      let result = logs;
      if (filter?.type) result = result.filter(l => l.type === filter.type);
      if (filter?.serverId) result = result.filter(l => l.serverId === filter.serverId);
      if (filter?.since) result = result.filter(l => l.timestamp > filter.since);
      return result.slice(-500);
    },
  };
}
