package com.alibaba.jvm.cds;

import com.alibaba.jvm.cds.classlist.ClassListTransformer;
import com.alibaba.jvm.cds.classlist.worker.ClassListWorker;
import com.alibaba.jvm.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class CDSDumper {

    private static final String[] MEM_ARGS = {
            "-XX:MaxHeapSize", "-XX:InitialHeapSize", "-Xms", "-Xmn", "-Xmx",
            "-Xss", "-XX:MaxMetaspaceSize", "-XX:MetaspaceSize", "-XX:MaxDirectMemorySize",
            "-XX:NewSize", "-XX:MaxNewSize"};

    public static class Info {
        public String dirPath;
        public String originClassListName;
        public String finalClassListName;
        public boolean eager;
        public String jsaName;
        public String agent;
        public boolean verbose;
        public String runtimeCommandLine;
        public String cp;

        public Info(String dirPath,
                    String originClassListName,
                    String finalClassListName,
                    boolean eager,
                    String jsaName,
                    String agent,
                    boolean verbose,
                    String runtimeCommandLine,
                    String cp) {
            this.dirPath = dirPath;
            this.originClassListName = originClassListName;
            this.finalClassListName = finalClassListName;
            this.eager = eager;
            this.jsaName = jsaName;
            this.agent = agent;
            this.verbose = verbose;
            this.runtimeCommandLine = runtimeCommandLine;
            this.cp = cp;
        }
    }
    private static Info info;
    public static Info getInfo() { return info; }

    /// auto dump

    private static ClassListWorker runClassListWorker() {
        try {
            ClassListWorker first = ClassListTransformer.create(info.finalClassListName, info.dirPath);
            first.run(info.originClassListName);
            return first;
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static boolean enableStaticDiffClass() {
        return info != null && info.runtimeCommandLine != null && info.runtimeCommandLine.contains("-XX:+EagerAppCDSStaticClassDiffCheck");
    }

    private static void handleCompressedOopsAndClassPointers(List<String> arguments) {
        final String[] xmxOptions = new String[] {
            "-Xmx",
            "-XX:MaxHeapSize=",
        };
        for (int i = arguments.size()-1; i >= 0; i--) {
            String argument = arguments.get(i);
            long value = 0L;
            final long KB = 1024L;
            final long MB = KB * KB;
            final long GB = MB * KB;
            final long TB = GB * KB;
            for (String xmx : xmxOptions) {
                if (argument.startsWith(xmx)) {
                    String s = argument.substring(xmx.length());
                    int radix = 10;
                    if (s.startsWith("0x")) {
                        radix = 16;
                        s = s.substring(2);
                    }
                    char lastChar = s.charAt(s.length()-1);
                    lastChar = Character.toUpperCase(lastChar);
                    if (Character.isDigit(lastChar)) {
                        value = Long.parseLong(s, radix);
                        break;
                    }

                    String prefix = s.substring(0, s.length()-1);
                    long number = Long.parseLong(prefix, radix);  // Ignore the last char, see below
                    if (lastChar == 'T') {
                        value = number * TB;
                    } else if (lastChar == 'G') {
                        value = number * GB;
                    } else if (lastChar == 'M') {
                        value = number * MB;
                    } else if (lastChar == 'K') {
                        value = number * KB;
                    } else {
                        throw new Error("Parse error: ShouldNotReachHere: " + argument);
                    }
                }
            }
            if (value == 0) {  // nothing happened
                continue;
            }
            if (value >= 32 * GB) {
                // bingo. We only find the bad one.
                arguments.add("-XX:-UseCompressedOops");
                arguments.add("-XX:-UseCompressedClassPointers");
                break;
            }
        }
    }

    private static void runDumpJSA() {
        String jdkHome = Utils.getJDKHome();
        // deal with all jdk options
        List<String> arguments = new LinkedList<>(Arrays.asList(info.runtimeCommandLine.split(Utils.JAVA_COMMAND_LINE_SPLITTER)));

        arguments.removeIf(arg -> arg.startsWith("-Xshare:off") ||
                arg.startsWith("-XX:SharedClassListFile") ||
                arg.startsWith("-XX:DumpLoadedClassList") ||
                arg.startsWith("-Xquickstart") ||
                arg.startsWith("--patch-module") ||  /* remove --patch-module */
                arg.startsWith("-agentlib:jdwp") ||  /* remove debuggers */
                arg.startsWith("-Xdebug") ||  /* remove debuggers */
                arg.startsWith("-Xrunjdwp") ||  /* remove debuggers */
                arg.startsWith("-javaagent") ||  /* disable javaagent on the dump step */
                arg.trim().isEmpty());           /* arg can be empty string. */

        // special phase to scan `UseCompressedOops` and `UseCommpressedClassPointers` related memory flags
        handleCompressedOopsAndClassPointers(arguments);

        //Dump may fail if no enough memory if use the same args for dump process
        //So remove all memory parameter for dump process.
        for (String memArg : MEM_ARGS) {
            arguments.removeIf((arg) -> arg.startsWith(memArg));
        }

        Utils.printArgs(arguments, "[Current JVM commands] ", info.verbose);

        // prepare command line arguments for further dumping process
        List<String> command = new ArrayList<>();
        command.add(Path.of(jdkHome, "bin", "java").toString());
        command.addAll(arguments);
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xshare:dump");
        command.add("-XX:SharedClassListFile=" + info.finalClassListName);
        command.add("-XX:SharedArchiveFile=" + info.jsaName);
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+AppCDSLegacyVerisonSupport");
        command.add("-XX:+DumpAppCDSWithKlassId");
        command.add("-XX:+DisableAttachMechanism");  // Forbid attachment
        if (info.eager) {
            command.add("-XX:+EagerAppCDS");
            command.add("-Xbootclasspath/a:" + Path.of(jdkHome, "lib", info.agent));
        }
        // append classpath if has
        command.add("-cp");
        command.add(info.cp);

        Utils.printArgs(command, "[Dump JSA Command] ", info.verbose);

        // start new process to dump JSA
        try {
            Utils.runProcess(command, info.verbose, null, null, new Utils.LogInfo(Path.of(info.dirPath, "logs"), "jsa.log"));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void prepareInfo(String dirPath) throws Exception {
        assert Objects.nonNull(dirPath);
        assert Objects.nonNull(info.originClassListName);
        assert Objects.nonNull(info.finalClassListName);
        assert Objects.nonNull(info.jsaName);

        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new RuntimeException(dirPath + " is not a dir? ");
        }
        info.originClassListName = Path.of(dirPath, info.originClassListName).toString();
        info.finalClassListName = Path.of(dirPath, info.finalClassListName).toString();
        info.jsaName = Path.of(dirPath, info.jsaName).toString();

        if (!new File(info.originClassListName).exists()) {
            throw new FileNotFoundException(info.originClassListName + " doesn't exist!");
        }
    }

    public static void dumpJSA() throws Exception {
        prepareInfo(info.dirPath);

        ClassListWorker first = runClassListWorker();

        runDumpJSA();

        //only after dump do clean up.such fat jar's temporary files.
        first.close();
    }

    private static final int ARGC = 9;
    public static void main(String[] args) {
        if (args.length != ARGC) {
            throw new Error("args num should be " + ARGC + ". Now " + args.length);
        }

        String dirPath = args[0];
        String originClassListName = args[1];
        String finalClassListName = args[2];
        boolean eager = Boolean.parseBoolean(args[3]);
        String jsaName = args[4];
        String agent = args[5];
        boolean verbose = Boolean.parseBoolean(args[6]);
        String runtimeCommandLine = args[7];
        String cp = args[8];

        info = new Info(dirPath, originClassListName, finalClassListName,
                eager, jsaName, agent, verbose, runtimeCommandLine, cp);

        try {
            dumpJSA();
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
