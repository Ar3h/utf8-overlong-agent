/**
 * @Author Ar3h
 * @Date 2024/4/22 22:37
 */

import java.lang.instrument.Instrumentation;

public class Main {
    public static int BYTE_LENGTH = 0;

    public static void premain(String agentArgs, Instrumentation inst) {
        if (agentArgs != null && agentArgs != "") {
            try {
                BYTE_LENGTH = Integer.valueOf(agentArgs);
            } catch (NumberFormatException e) {
                throw new RuntimeException("agentArgs set error, only set 2 or 3");
            }
            if (BYTE_LENGTH != 2 && BYTE_LENGTH != 3) {
                throw new RuntimeException("agentArgs set error, only set 2 or 3");
            }
            System.err.println("[agent] set BYTE_LENGTH => " + BYTE_LENGTH);
        }

        Class[] classes = inst.getAllLoadedClasses();
        String targetClassName = "java.io.ObjectOutputStream$BlockDataOutputStream";

        boolean flag = false;
        try {
            for (Class aClass : classes) {
                if (aClass.getName().equals(targetClassName)) {
                    System.err.println("find class: " + aClass.getName());
                    inst.addTransformer(new Transformer(), true);
                    inst.retransformClasses(aClass);
                    flag = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!flag) {
            inst.addTransformer(new Transformer());
        }
    }
}
