package io.fraudoor.rcon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

@Plugin(
  id = "fraudoor-rcon",
  name = "FraudoorRCON",
  version = "1.0.0",
  description = "The most advanced RCON tool for Minecraft",
  authors = {"fraudoor"}
)
public class RCONVelocityPlugin {

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final Gson gson = new Gson();
  private WebSocketClient ws;
  private boolean connected = false;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private String serverId;
  private String serverUri;
  private String secret;

  @Inject
  public RCONVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onProxyInit(ProxyInitializeEvent event) {
    loadConfig();

    serverId = "velocity-" + System.currentTimeMillis();
    logger.info("[fraudoor] connecting to {}", serverUri);

    connect();

    scheduler.scheduleAtFixedRate(() -> {
      if (connected) sendStatus();
    }, 10, 10, TimeUnit.SECONDS);

    startConsoleCapture();
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    if (ws != null) ws.close();
    scheduler.shutdown();
  }

  private void loadConfig() {
    Path configFile = dataDirectory.resolve("config.yml");
    serverUri = "ws://localhost:8080/ws";
    secret = "";

    if (Files.exists(configFile)) {
      try {
        String content = Files.readString(configFile);
        for (String line : content.split("\n")) {
          line = line.trim();
          if (line.startsWith("server:"))
            serverUri = line.substring(7).trim().replace("\"", "");
          if (line.startsWith("secret:"))
            secret = line.substring(7).trim().replace("\"", "");
        }
      } catch (IOException e) {
        logger.error("[fraudoor] failed to load config", e);
      }
    }
  }

  private void connect() {
    try {
      ws = new WebSocketClient(new URI(serverUri)) {
        @Override
        public void onOpen(ServerHandshake handshake) {
          connected = true;
          logger.info("[fraudoor] connected to {}", serverUri);
          sendHello();
        }

        @Override
        public void onMessage(String message) {
          handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          connected = false;
          logger.info("[fraudoor] disconnected: {}", reason);
          scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
          connected = false;
          logger.warn("[fraudoor] error: {}", ex.getMessage());
          scheduleReconnect();
        }
      };
      ws.addHeader("Origin", "fraudoor-plugin");
      ws.setConnectionLostTimeout(15);
      ws.connect();
    } catch (Exception e) {
      logger.warn("[fraudoor] connection failed: {}", e.getMessage());
      scheduleReconnect();
    }
  }

  private void scheduleReconnect() {
    scheduler.schedule(() -> {
      if (!connected && (ws == null || ws.isClosed())) {
        logger.info("[fraudoor] reconnecting...");
        connect();
      }
    }, 5, TimeUnit.SECONDS);
  }

  private void sendHello() {
    JsonObject payload = new JsonObject();
    payload.addProperty("id", serverId);
    payload.addProperty("name", "Velocity Proxy");
    payload.addProperty("ip", getLocalIp());
    payload.addProperty("port", server.getBoundAddress().isPresent()
        ? server.getBoundAddress().get().getPort() : 25577);
    payload.addProperty("version", server.getVersion().getVersion());
    payload.addProperty("type", "velocity");
    payload.addProperty("playerCount", server.getPlayerCount());
    payload.addProperty("maxPlayers", server.getConfiguration().getShowMaxPlayers());
    send("plugin:hello", payload);
  }

  private void sendStatus() {
    JsonObject payload = new JsonObject();
    payload.addProperty("playerCount", server.getPlayerCount());
    payload.addProperty("maxPlayers", server.getConfiguration().getShowMaxPlayers());
    payload.addProperty("online", true);
    var players = new com.google.gson.JsonArray();
    server.getAllPlayers().forEach(p -> players.add(p.getUsername()));
    payload.add("players", players);
    send("plugin:status", payload);
  }

  private void handleMessage(String msg) {
    try {
      JsonObject json = gson.fromJson(msg, JsonObject.class);
      String type = json.get("type").getAsString();
      if ("server:command".equals(type)) {
        String cmd = json.getAsJsonObject("payload").get("command").getAsString();
        logger.info("[fraudoor] executing command: {}", cmd);
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), cmd);
      }
    } catch (Exception e) {
      logger.warn("[fraudoor] handle error: {}", e.getMessage());
    }
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

  private void startConsoleCapture() {
    new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (connected) {
            JsonObject payload = new JsonObject();
            payload.addProperty("line", line);
            send("plugin:console", payload);
          }
        }
      } catch (Exception ignored) {}
    }, "fraudoor-console").start();
  }

  private String getLocalIp() {
    try {
      return java.net.InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }
}
