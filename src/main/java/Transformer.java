import javassist.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class Transformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String targetClassName = "java.io.ObjectOutputStream$BlockDataOutputStream";
        String targetClassNameWithSlash = targetClassName.replace(".", "/");

        // 判断是否是目标类
        if (targetClassNameWithSlash.equals(className)) {
            System.err.println("[agent] transform class: " + className);
            try {
                ClassPool cp = ClassPool.getDefault();
                if (classBeingRedefined != null) {
                    ClassClassPath classPath = new ClassClassPath(classBeingRedefined);
                    cp.insertClassPath(classPath);
                }

                CtClass cc = cp.get(targetClassName);
                cp.importPackage(IOException.class.getName());

                // 删除 writeUTF 方法
                CtMethod writeUTFMethod = cc.getMethod("writeUTF", "(Ljava/lang/String;J)V");
                cc.removeMethod(writeUTFMethod);

                // 添加 writeUTF 方法
                CtMethod bewWriteUTFMethod;
                if (Main.BYTE_LENGTH == 2 || Main.BYTE_LENGTH == 3) {
                    bewWriteUTFMethod = CtNewMethod.make("    void writeUTF(String s, long utflen) throws IOException {\n" +
                            "        // if (utflen > 0xFFFFL) {\n" +
                            "        //     throw new UTFDataFormatException();\n" +
                            "        // }\n" +
                            "        int len = s.length();\n" +
                            "\n" +
                            "        writeShort((int) (" + Main.BYTE_LENGTH + " * len ));\n" +
                            "        writeUTFBody(s);\n" +
                            "    }", cc);
                } else {
                    bewWriteUTFMethod = CtNewMethod.make("    void writeUTF(String s, long utflen) throws IOException {\n" +
                            "        // if (utflen > 0xFFFFL) {\n" +
                            "        //     throw new UTFDataFormatException();\n" +
                            "        // }\n" +
                            "        int len = s.length();\n" +
                            "        int threeByteCount = len / 2 + 1;\n" +
                            "\n" +
                            "        writeShort((int) (3 * threeByteCount + 2 * (len - threeByteCount)));\n" +
                            "        writeUTFBody(s);\n" +
                            "    }", cc);
                }
                cc.addMethod(bewWriteUTFMethod);


                CtMethod randomCallMethod = CtNewMethod.make("    public static boolean randomCall(int remainingPositions, int remainingCalls) {\n" +
                        "java.util.Random rand = new java.util.Random();" +
                        "double probability = (double) $2 / $1;" +
                        "double randomProbability = rand.nextDouble();" +
                        "return randomProbability < probability;" +
                        "    }", cc);
                cc.addMethod(randomCallMethod);


                // 删除 writeUTFBody 方法
                CtMethod writeUTFBodyMethod = cc.getMethod("writeUTFBody", "(Ljava/lang/String;)V");
                cc.removeMethod(writeUTFBodyMethod);


                // 添加 writeUTFBody 方法
                CtMethod newWriteUTFBodyMethod;
                if (Main.BYTE_LENGTH == 2 || Main.BYTE_LENGTH == 3) {
                    String boolString = Main.BYTE_LENGTH == 3 ? "true" : "false";
                    newWriteUTFBodyMethod = CtNewMethod.make("    private void writeUTFBody(String s) throws IOException {\n" +
                            "        int limit = MAX_BLOCK_SIZE - 3;\n" +
                            "        int len = s.length();\n" +
                            "\n" +
                            "        for (int off = 0; off < len; ) {\n" +
                            "            int csize = Math.min(len - off, CHAR_BUF_SIZE);\n" +
                            "            s.getChars(off, off + csize, cbuf, 0);\n" +
                            "            for (int cpos = 0; cpos < csize; cpos++) {\n" +
                            "                char c = cbuf[cpos];\n" +
                            "                if (pos <= limit) {\n" +
                            "                    // if (c <= 0x007F && c != 0) {\n" +
                            "                    //     buf[pos++] = (byte) c;\n" +
                            "                    // } else if (c > 0x07FF) {\n" +
                            "\n" +
                            "                    if (" + boolString + ") {\n" +
                            "                        buf[pos + 2] = (byte) (0x80 | ((c >> 0) & 0x3F));\n" +
                            "                        buf[pos + 1] = (byte) (0x80 | ((c >> 6) & 0x3F));\n" +
                            "                        buf[pos + 0] = (byte) (0xE0 | ((c >> 12) & 0x0F));\n" +
                            "                        pos += 3;\n" +
                            "                    } else{\n" +
                            "                        buf[pos + 1] = (byte) (0x80 | ((c >> 0) & 0x3F));\n" +
                            "                        buf[pos + 0] = (byte) (0xC0 | ((c >> 6) & 0x1F));\n" +
                            "                        pos += 2;\n" +
                            "                    }\n" +
                            "                } else {    // write one byte at a time to normalize block\n" +
                            "                    if (c <= 0x007F && c != 0) {\n" +
                            "                        write(c);\n" +
                            "                    } else if (c > 0x07FF) {\n" +
                            "                        write(0xE0 | ((c >> 12) & 0x0F));\n" +
                            "                        write(0x80 | ((c >> 6) & 0x3F));\n" +
                            "                        write(0x80 | ((c >> 0) & 0x3F));\n" +
                            "                    } else {\n" +
                            "                        write(0xC0 | ((c >> 6) & 0x1F));\n" +
                            "                        write(0x80 | ((c >> 0) & 0x3F));\n" +
                            "                    }\n" +
                            "                }\n" +
                            "            }\n" +
                            "            off += csize;\n" +
                            "        }\n" +
                            "    }", cc);

                } else {
                    newWriteUTFBodyMethod = CtNewMethod.make("    private void writeUTFBody(String s) throws IOException {\n" +
                            "        int limit = MAX_BLOCK_SIZE - 3;\n" +
                            "        int len = s.length();\n" +
                            "        int threeByteCount = len / 2 + 1;\n" +
                            "\n" +
                            "        int count = 0;\n" +
                            "\n" +
                            "        for (int off = 0; off < len; ) {\n" +
                            "            int csize = Math.min(len - off, CHAR_BUF_SIZE);\n" +
                            "            s.getChars(off, off + csize, cbuf, 0);\n" +
                            "            for (int cpos = 0; cpos < csize; cpos++) {\n" +
                            "                char c = cbuf[cpos];\n" +
                            "                if (pos <= limit) {\n" +
                            "                    // if (c <= 0x007F && c != 0) {\n" +
                            "                    //     buf[pos++] = (byte) c;\n" +
                            "                    // } else if (c > 0x07FF) {\n" +
                            "\n" +
                            // "                    if (threeByteCount-- > 0) {\n" +
                            "                    if (threeByteCount > 0 && randomCall(len - count, threeByteCount)) {\n" +
                            "                        buf[pos + 2] = (byte) (0x80 | ((c >> 0) & 0x3F));\n" +
                            "                        buf[pos + 1] = (byte) (0x80 | ((c >> 6) & 0x3F));\n" +
                            "                        buf[pos + 0] = (byte) (0xE0 | ((c >> 12) & 0x0F));\n" +
                            "                        pos += 3;\n" +
                            "                        threeByteCount--;\n" +
                            "                    } else{\n" +
                            "                        buf[pos + 1] = (byte) (0x80 | ((c >> 0) & 0x3F));\n" +
                            "                        buf[pos + 0] = (byte) (0xC0 | ((c >> 6) & 0x1F));\n" +
                            "                        pos += 2;\n" +
                            "                    }\n" +
                            "                } else {    // write one byte at a time to normalize block\n" +
                            "                    if (c <= 0x007F && c != 0) {\n" +
                            "                        write(c);\n" +
                            "                    } else if (c > 0x07FF) {\n" +
                            "                        write(0xE0 | ((c >> 12) & 0x0F));\n" +
                            "                        write(0x80 | ((c >> 6) & 0x3F));\n" +
                            "                        write(0x80 | ((c >> 0) & 0x3F));\n" +
                            "                    } else {\n" +
                            "                        write(0xC0 | ((c >> 6) & 0x1F));\n" +
                            "                        write(0x80 | ((c >> 0) & 0x3F));\n" +
                            "                    }\n" +
                            "                }\n" +
                            "                count++;\n" +
                            "            }\n" +
                            "            off += csize;\n" +
                            "        }\n" +
                            "    }", cc);

                }
                cc.addMethod(newWriteUTFBodyMethod);

                System.err.println("[agent] modify success");

                byte[] bytes = cc.toBytecode();
                cc.detach();
                return bytes;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }
}
