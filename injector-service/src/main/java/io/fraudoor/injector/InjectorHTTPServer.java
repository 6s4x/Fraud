package io.fraudoor.injector;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

public class InjectorHTTPServer {

  private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));

  public static void main(String[] args) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
    server.createContext("/inject", InjectorHTTPServer::handleInject);
    server.createContext("/health", InjectorHTTPServer::handleHealth);
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.start();
    System.out.println("[fraudoor-injector] listening on port " + PORT);
  }

  private static void handleHealth(HttpExchange exchange) throws IOException {
    byte[] resp = "{\"status\":\"ok\"}".getBytes();
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, resp.length);
    exchange.getResponseBody().write(resp);
    exchange.getResponseBody().close();
  }

  private static void handleInject(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

    if ("OPTIONS".equals(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(204, -1);
      return;
    }

    if (!"POST".equals(exchange.getRequestMethod())) {
      sendError(exchange, 405, "Method not allowed");
      return;
    }

    Path tmpDir = null;
    try {
      String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      if (contentType == null || !contentType.contains("multipart/form-data")) {
        sendError(exchange, 400, "Content-Type must be multipart/form-data");
        return;
      }

      String boundary = extractBoundary(contentType);
      if (boundary == null) {
        sendError(exchange, 400, "Could not parse boundary");
        return;
      }

      tmpDir = Files.createTempDirectory("fraudoor-inject-");

      byte[] body = exchange.getRequestBody().readAllBytes();
      Map<String, Part> parts = parseMultipart(body, boundary);

      Part pluginPart = parts.get("plugin");
      Part platformPart = parts.get("platform");

      if (pluginPart == null) {
        sendError(exchange, 400, "Missing 'plugin' field");
        return;
      }

      String platform = platformPart != null ? new String(platformPart.data) : "paper";

      Path inputJar = tmpDir.resolve("input.jar");
      Files.write(inputJar, pluginPart.data);

      Path outputJar = tmpDir.resolve("injected.jar");

      JarInjector injector = new JarInjector(inputJar.toFile(), outputJar.toFile(),
          platform.trim(), "", "");
      injector.inject();

      byte[] result = Files.readAllBytes(outputJar);

      exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
      exchange.getResponseHeaders().set("Content-Disposition",
          "attachment; filename=\"FRAUDED-" + pluginPart.filename + "\"");
      exchange.sendResponseHeaders(200, result.length);
      exchange.getResponseBody().write(result);
      exchange.getResponseBody().close();

    } catch (Exception e) {
      e.printStackTrace();
      sendError(exchange, 500, "Injection failed: " + e.getMessage());
    } finally {
      if (tmpDir != null) deleteDir(tmpDir.toFile());
    }
  }

  private static void sendError(HttpExchange exchange, int code, String msg) throws IOException {
    byte[] resp = ("{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}").getBytes();
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(code, resp.length);
    exchange.getResponseBody().write(resp);
    exchange.getResponseBody().close();
  }

  private static String extractBoundary(String contentType) {
    for (String part : contentType.split(";")) {
      part = part.trim();
      if (part.startsWith("boundary=")) {
        return part.substring("boundary=".length());
      }
    }
    return null;
  }

  private static class Part {
    String name;
    String filename;
    byte[] data;
  }

  private static Map<String, Part> parseMultipart(byte[] body, String boundary) {
    Map<String, Part> parts = new HashMap<>();
    byte[] delimiter = ("--" + boundary).getBytes();
    byte[] endDelimiter = ("--" + boundary + "--").getBytes();

    int pos = 0;
    while (pos < body.length) {
      int start = indexOf(body, delimiter, pos);
      if (start == -1) break;

      int partStart = start + delimiter.length;
      if (partStart >= body.length) break;

      if (body[partStart] == '-' && partStart + 1 < body.length && body[partStart + 1] == '-') break;

      int nextStart = indexOf(body, delimiter, partStart);
      if (nextStart == -1) nextStart = body.length;

      int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), partStart);
      if (headerEnd == -1 || headerEnd > nextStart) {
        pos = nextStart;
        continue;
      }

      String headers = new String(body, partStart, headerEnd - partStart);
      int dataStart = headerEnd + 4;
      int dataEnd = nextStart;
      if (dataEnd > 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') dataEnd -= 2;

      Part part = new Part();
      part.name = extractHeaderValue(headers, "name=\"", "\"");
      part.filename = extractHeaderValue(headers, "filename=\"", "\"");
      if (part.filename == null) part.filename = "file";

      if (part.name != null) {
        part.data = Arrays.copyOfRange(body, dataStart, dataEnd);
        parts.put(part.name, part);
      }

      pos = nextStart;
    }
    return parts;
  }

  private static String extractHeaderValue(String headers, String prefix, String suffix) {
    int idx = headers.indexOf(prefix);
    if (idx == -1) return null;
    int start = idx + prefix.length();
    int end = headers.indexOf(suffix, start);
    return end == -1 ? null : headers.substring(start, end);
  }

  private static int indexOf(byte[] data, byte[] pattern, int start) {
    outer: for (int i = start; i <= data.length - pattern.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  private static void deleteDir(File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) deleteDir(f);
        else f.delete();
      }
    }
    dir.delete();
  }
}
