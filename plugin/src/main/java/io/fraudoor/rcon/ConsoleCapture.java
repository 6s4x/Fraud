package io.fraudoor.rcon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class ConsoleCapture {

  private final RCONPlugin plugin;
  private Handler handler;
  private Thread consoleThread;

  public ConsoleCapture(RCONPlugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    plugin.getLogger().addHandler(handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        if (plugin.getClient() != null && plugin.getClient().isConnected()) {
          plugin.getClient().sendConsole(record.getMessage());
        }
      }

      @Override
      public void flush() {}
      @Override
      public void close() throws SecurityException {}
    });

    consoleThread = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(System.in))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (plugin.getClient() != null && plugin.getClient().isConnected()) {
            plugin.getClient().sendConsole(line);
          }
        }
      } catch (Exception ignored) {}
    }, "fraudoor-console");
    consoleThread.setDaemon(true);
    consoleThread.start();
  }

  public void stop() {
    if (handler != null) plugin.getLogger().removeHandler(handler);
    if (consoleThread != null) consoleThread.interrupt();
  }
}
