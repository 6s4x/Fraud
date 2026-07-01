package io.fraudoor.rcon;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;

public class RCONPlugin extends JavaPlugin {

  private RCONClient client;
  private ConsoleCapture consoleCapture;
  private FileSystem fileSystem;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    reloadConfig();

    String server = getConfig().getString("server", "ws://localhost:8080/ws");
    String secret = getConfig().getString("secret", "");

    getLogger().info("[fraudoor] starting RCON agent...");

    fileSystem = new FileSystem(this);

    client = new RCONClient(server, secret, this);
    client.connect();

    consoleCapture = new ConsoleCapture(this);
    consoleCapture.start();

    new BukkitRunnable() {
      @Override
      public void run() {
        if (client != null && client.isConnected()) {
          int count = getServer().getOnlinePlayers().size();
          int max = getServer().getMaxPlayers();
          String[] players = getServer().getOnlinePlayers().stream()
              .map(Player::getName).toArray(String[]::new);
          double tps = getTps();
          client.sendStatus(count, max, players, tps);
        }
      }
    }.runTaskTimer(this, 100L, 100L);
  }

  @Override
  public void onDisable() {
    if (consoleCapture != null) consoleCapture.stop();
    if (client != null) client.close();
  }

  public void executeCommand(String command) {
    Bukkit.getScheduler().runTask(this, () -> {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    });
  }

  public RCONClient getClient() { return client; }
  public FileSystem getFileSystem() { return fileSystem; }

  private double getTps() {
    try {
      Object mspt = getServer().getClass().getMethod("getAverageTickTime").invoke(getServer());
      return Math.min(20.0, 1000.0 / (Double) mspt);
    } catch (Exception e) {
      return -1;
    }
  }
}
