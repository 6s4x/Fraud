package io.fraudoor.rcon;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class FileSystem {

  private final RCONPlugin plugin;
  private final File root;

  public FileSystem(RCONPlugin plugin) {
    this.plugin = plugin;
    this.root = plugin.getDataFolder().getParentFile().getParentFile();
  }

  public void listFiles(String path, String requestId) {
    File dir = resolve(path);
    if (dir == null || !dir.isDirectory()) dir = root;

    List<Map<String, Object>> entries = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      Arrays.sort(files, (a, b) -> {
        if (a.isDirectory() && !b.isDirectory()) return -1;
        if (!a.isDirectory() && b.isDirectory()) return 1;
        return a.getName().compareToIgnoreCase(b.getName());
      });
      for (File f : files) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("name", f.getName());
        entry.put("path", relativize(f));
        entry.put("isDirectory", f.isDirectory());
        entry.put("size", f.length());
        entries.add(entry);
      }
    }

    plugin.getClient().sendFileList(requestId, entries, relativize(dir));
  }

  public void readFile(String path, String requestId) {
    File file = resolve(path);
    if (file == null || !file.isFile() || !isSafe(file)) {
      plugin.getClient().sendFileRead(requestId, path, "Error: invalid path", true);
      return;
    }
    try {
      String content = new String(Files.readAllBytes(file.toPath()));
      plugin.getClient().sendFileRead(requestId, path, content, false);
    } catch (Exception e) {
      plugin.getClient().sendFileRead(requestId, path, "Error: " + e.getMessage(), true);
    }
  }

  public void writeFile(String path, String content, String requestId) {
    File file = resolve(path);
    if (file == null || !isSafe(file)) {
      plugin.getClient().sendFileWrite(requestId, path, false);
      return;
    }
    try {
      file.getParentFile().mkdirs();
      byte[] data;
      try {
        data = Base64.getDecoder().decode(content);
      } catch (IllegalArgumentException e) {
        data = content.getBytes();
      }
      Files.write(file.toPath(), data);
      plugin.getClient().sendFileWrite(requestId, path, true);
    } catch (Exception e) {
      plugin.getClient().sendFileWrite(requestId, path, false);
    }
  }

  public void deleteFile(String path, String requestId) {
    File file = resolve(path);
    if (file == null || !isSafe(file)) {
      plugin.getClient().sendFileDelete(requestId, path, false);
      return;
    }
    try {
      if (file.isDirectory()) {
        deleteDir(file);
      } else {
        file.delete();
      }
      plugin.getClient().sendFileDelete(requestId, path, true);
    } catch (Exception e) {
      plugin.getClient().sendFileDelete(requestId, path, false);
    }
  }

  private File resolve(String path) {
    try {
      File file = new File(root, path != null ? path : "");
      if (file.getCanonicalPath().startsWith(root.getCanonicalPath())) {
        return file;
      }
    } catch (Exception ignored) {}
    return null;
  }

  private boolean isSafe(File file) {
    try {
      return file.getCanonicalPath().startsWith(root.getCanonicalPath());
    } catch (Exception e) {
      return false;
    }
  }

  private String relativize(File file) {
    try {
      String rel = root.toURI().relativize(file.toURI()).getPath();
      return "/" + rel.replace("\\", "/");
    } catch (Exception e) {
      return "/";
    }
  }

  private void deleteDir(File dir) {
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
