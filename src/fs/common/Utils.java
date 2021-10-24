package fs.common;

import io.vertx.core.Vertx;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class Utils {
    public static final Vertx VERTX = Vertx.vertx();
    
    private Utils() { }
    
    public static void logError(Throwable t) {
        if(InstanceHandler.commandHandler == null ? false : InstanceHandler.commandHandler.questioning())
            System.err.println("[STDERR]:");
        System.err.println(t.getClass().getName() + ": " + t.getMessage());
        for(StackTraceElement ste : t.getStackTrace())
            System.err.println('\t' + ste.toString());
    }
    
    public static void failOnError(Throwable cause, ExitCode errorType) {
        logError(cause);
        System.err.println("BUILD FAILED. Error code: " + errorType.getErrorCode() + " (" + errorType.toString().toUpperCase() + ")");
        System.runFinalization();
        System.exit(errorType.getErrorCode());
    }
    
    public static void failOnError(String msg, ExitCode errorType) {
        if(InstanceHandler.commandHandler == null ? false : InstanceHandler.commandHandler.questioning())
            System.err.print("[STDERR]: ");
        System.err.println(msg);
        System.err.println("BUILD FAILED. Error code: " + errorType.getErrorCode() + " (" + errorType.toString().toUpperCase() + ")");
        System.runFinalization();
        System.exit(errorType.getErrorCode());
    }
    
    public static void failOnError(String msg, Throwable cause, ExitCode errorType) {
        if(InstanceHandler.commandHandler == null ? false : InstanceHandler.commandHandler.questioning())
            System.err.print("[STDERR]: ");
        System.err.println(msg);
        logError(cause);
        System.err.println("BUILD FAILED. Error code: " + errorType.getErrorCode() + " (" + errorType.toString().toUpperCase() + ")");
        System.runFinalization();
        System.exit(errorType.getErrorCode());
    }
    
    public static void log(Object msg) {
        boolean flag = InstanceHandler.commandHandler == null ? false : InstanceHandler.commandHandler.questioning();
        if(flag) System.out.print("[STDOUT]: ");
        System.out.println(msg);
        if(flag) System.out.print("> ");
    }
    
    public static void err(Object msg) {
        boolean flag = InstanceHandler.commandHandler == null ? false : InstanceHandler.commandHandler.questioning();
        if(flag) System.err.print("[STDERR]: ");
        System.err.println(msg);
        if(flag) System.err.print("> ");
    }
    
    public static String[] safeArgSplit(String str) {
        int flags = 0x0;
        List<String> split = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for(int i = 0;i < str.length();++ i) {
            char c = str.charAt(i);
            if(c == '\"') {
                flags ^= 0x1;
                continue;
            }else if(c == '\'') {
                flags ^= 0x2;
                continue;
            }
            if(c == ' ' && flags == 0) {
                split.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if(current.length() > 0)
            split.add(current.toString());
        return split.toArray(new String[split.size()]);
    }
    
    public static String combinePathElements(String... pathElements) {
        return String.join(File.separator, pathElements);
    }
    
    public static int constrain(int num, int min, int max) {
        return num < min ? min : (num > max ? max : num);
    }
    
    public static <T> boolean containsMatch(Collection<T> c, Predicate<T> p) {
        return c.stream().anyMatch(p);
    }
    
    public static <T> T getFirstMatch(Collection<T> c, Predicate<T> p) {
        for(T t : c) {
            if(p.test(t))
                return t;
        }
        return null;
    }
}
