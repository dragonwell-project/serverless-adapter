package com.alibaba.jvm.util;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public class Utils {

    // https://stackoverflow.com/a/820223
    // Show my respect to stackoverflow pals because they are awesome.
    // The below regex splits ' ' but ignores '\ '.
    // like: '-Dcom.alibaba.wisp.threadAsWisp.black=name:process\ reaper\;name:epollEventLoopGroup\*'
    //   could be compiled by this regex as one unified option.
    public static final String JAVA_COMMAND_LINE_SPLITTER = "(?<!\\\\)[\r\t ]";

    public static void printArgs(List<String> arguments, String msg, boolean verbose) {
        printArgs(arguments, msg, verbose, System.out);
    }

    public static void printArgs(List<String> arguments, String msg, boolean verbose, File existedFile) {
        try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(existedFile)), true)) {
            printArgs(arguments, msg, verbose, ps);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void printArgs(List<String> arguments, String msg, boolean verbose, PrintStream ps) {
        if (!verbose) {
            return;
        }
        ps.print(msg);
        for (String s : arguments) {
            ps.print(s + " ");
        }
        ps.println();
    }

    public static final String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";
    public static String removeAgentOp() {
        String toolOp = System.getenv(JAVA_TOOL_OPTIONS);
        return toolOp == null ? null : toolOp.replaceAll("-javaagent\\S*\\s?", " ");
    }

    public static final int NO_TIMEOUT = -1;
    public static class TimeOut {
        public int timeout = NO_TIMEOUT;
        public TimeUnit timeUnit = TimeUnit.HOURS;

        public TimeOut() {}
        public TimeOut(int timeout, TimeUnit timeUnit) {
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        public boolean noTimeOut() {
            return timeout == NO_TIMEOUT;
        }
    }
    public static class LogInfo {
        private Path logDir;
        private String logName;

        public LogInfo() {}
        public LogInfo(Path logDir, String logName) {
            this.logDir = logDir;
            this.logName = logName;
        }
    }

    private static void readProcessOutputWithThread(Process p, File f) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                while (true) {
                    String line = br.readLine();
                    if (line != null) {
                        System.out.println(line);
                    }
                    if (!p.isAlive()) {
                        return;
                    }
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        }).start();
    }

    private static String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static void runProcess(List<String> arguments, boolean verbose, Consumer<ProcessBuilder> op, TimeOut timeout, LogInfo log) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(arguments).inheritIO();
        if (op != null) {
            op.accept(pb);
        }
        // create log files
        if (!log.logDir.toFile().exists()) {
            log.logDir.toFile().mkdirs();
        }
        File logFile = log.logDir.resolve(log.logName).toFile();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
        printArgs(arguments, "[Command] ", verbose, logFile);
        // redirect child process's streams
        if (verbose) {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectErrorStream(true);
        } else {
            // ignore output to prevent child stucking at outputstream
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));  // log error even if not verbose
        }
        Process p = pb.start();

        if (verbose) {
            // another thread to read from the stream.
            // we are outputting to the file and we want also
            // get this just-in-time for stdout.
            readProcessOutputWithThread(p, logFile);
        }

        int ret = 0;
        // wait for the process.
        if (timeout != null && !timeout.noTimeOut()) {
            // https://stackoverflow.com/a/56734937/7093297
            // we have a timeout setting.
            if (!p.waitFor(timeout.timeout, timeout.timeUnit)) {
                List<String> jstackArgs = new LinkedList<>();
                jstackArgs.add(Path.of(getJDKHome(), "bin", "jstack").toString());
                jstackArgs.add(Long.toString(p.pid()));
                runProcess(jstackArgs, true, null, null /*new TimeOut(1, TimeUnit.MINUTES)  (no output with a timeout  */, new LogInfo(log.logDir, "jstack.log"));
                p.destroyForcibly();
                throw new TimeoutException("[Fatal] Process timed out");
            }
            ret = p.exitValue();
        } else {
            ret = p.waitFor();
        }

        boolean hasError;
        if ((hasError = (ret != 0)) || verbose) {
            System.out.println("return value: " + ret);
            if (hasError) {
                throw new Exception("Process failed: { " + readFile(logFile) + " }");
            }
        }
    }

    public static void runProcess(boolean verbose, String msg, LogInfo logInfo, String... args) {
        List<String> command = List.of(args);
        Utils.printArgs(command, msg, verbose);
        try {
            Utils.runProcess(command, verbose, null, null, logInfo);
        } catch (Exception e) {
            e.printStackTrace();
            // we are in a child process. feel free to return -1.
            System.exit(-1);
        }
    }

    public static String getJDKHome() {
        String jdkHome = System.getProperty("java.home");
        if (!new File(jdkHome).exists()) {
            throw new Error("Fatal error, cannot find jdk path: [" + jdkHome + "] doesn't exist!");
        }
        return jdkHome;
    }

    // copied from apache loggings for we could not add apache loggings into our path.
    public static class IOUtils {
        public static long copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
            return copyLarge(input, output, new byte[bufferSize]);
        }
        public static long copy(InputStream input, OutputStream output) throws IOException {
            return copy(input, output, 4096);
        }
        public static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {
            long count;
            int n;
            for(count = 0L; -1 != (n = input.read(buffer)); count += (long)n) {
                output.write(buffer, 0, n);
            }

            return count;
        }

        public static void copyToFile(final InputStream source, final File destination) throws IOException {
            try (InputStream in = source;
                 OutputStream out = new FileOutputStream(destination)) {
                IOUtils.copy(in, out);
            }
        }
    }
    public static List<String> readPlainText(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            while (line != null) {
                lines.add(line);
                line = br.readLine();
            }
        }
        return lines;
    }
    public static void log(String format, String... args) {
        System.out.println("[" + new Date() + "]" + String.format(format, args));
    }
    public static String fingerprint(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = IOUtils.copy(input, output);
        byte[] bytes = output.toByteArray();
        return fingerprint(bytes, count);
    }
    public static String fingerprint(byte[] bytes, long count) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        long v = ((count) << 32) | crc.getValue();
        return String.format("0x%016x", v);
    }

    public static String getjcmdPath(String javaHome) {
        Path p = Paths.get(javaHome);
        if (p.endsWith("jre")) {
            return Paths.get(p.getParent().toString(), "bin", "jcmd").toString();
        } else {
            return Paths.get(javaHome, "bin", "jcmd").toString();
        }
    }
}
