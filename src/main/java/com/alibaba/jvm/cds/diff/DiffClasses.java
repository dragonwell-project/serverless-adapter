package com.alibaba.jvm.cds.diff;

import com.alibaba.jvm.util.Utils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.jvm.cds.diff.ClassScanner.doScan;
import static com.alibaba.jvm.util.Utils.readPlainText;


public class DiffClasses {
    private static final String OUTPUT_FILE = "cds_diff_classes.lst";
    private static final String PARAM_VAR = "--var-";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    public static void main(String[] args) throws Exception {
        System.out.println("call in DiffClasses");
        Param param = parseParam(args);
        FingerprintFile fingerprintFile = new FingerprintFile();
        fingerprintFile.read(param.cacheDir);

        List<String> typeDirs = readPlainText(param.libDirFile).stream().map((s) -> replaceVar(s, param.varMap)).collect(Collectors.toList());
        Map<String, Set<ClassFileInfo>> classFileInfoMap = doScan(typeDirs);

        ClassComparator cc = new ClassComparator(fingerprintFile.getClassDescList(), fingerprintFile.getNotFoundClassList());
        cc.compare(classFileInfoMap);
        String dest = cc.write(param.cacheDir + File.separator + OUTPUT_FILE,fingerprintFile.getTotal());
        Utils.log("Write diff result to : " + dest);
    }

    private static Param parseParam(String[] args) {
        Param param = new Param();
        for (int i = 0; i < args.length; ) {
            switch (args[i]) {
                case "--cache-dir": {
                    param.cacheDir = getOptionValue(++i, args);
                    break;
                }
                case "--lib-dir-file":
                    param.libDirFile = getOptionValue(++i, args);
                    break;
                default:
                    if (args[i].startsWith(PARAM_VAR)) {
                        param.varMap.put(args[i].substring(PARAM_VAR.length()), getOptionValue(++i, args));
                    } else {
                        printUsageAndExit(args[i] + " unknown option!");
                    }
            }
            i++;
        }
        param.verify();
        return param;
    }

    private static String getOptionValue(int i, String[] args) {
        if (i >= args.length) {
            printUsageAndExit(null);
        }
        return args[i];
    }

    private static void printUsageAndExit(String msg) {
        if (msg != null) {
            System.out.println(msg);
        }
        System.out.println("Parameters: ");
        System.err.println("--cache-dir ${cache dir} " +
                "--lib-dir-file ${file contains all application's library}" +
                "--var-${var name} ${var value}");
        System.err.println("--var-${var name} which define the variable can use in file --lib-dir-file like this ${var name}.");
        System.exit(1);
    }

    private static class Param {
        String cacheDir;
        String libDirFile;
        Map<String, String> varMap = new HashMap<>();

        public void verify() {
            checkDir(cacheDir);
            if (libDirFile == null || "".equals(libDirFile)) {
                printUsageAndExit("Miss --lib-dir-file");
            }
            if (!new File(libDirFile).exists()) {
                printUsageAndExit("--lib-dir-file " + libDirFile + " not exist!");
            }
        }

        private void checkDir(String dir) {
            if (null == dir || "".equals(dir)) {
                printUsageAndExit(dir + " is empty!");
            }

            if (dir.contains("..")) {
                printUsageAndExit(dir + "cannot contains \"..\" characters.");
            }

            if (!new File(dir).exists()) {
                printUsageAndExit(dir + " not exist!");
            }
        }
    }

    private static String replaceVar(String str, Map<String, String> varMap) {
        Matcher m = VAR_PATTERN.matcher(str);
        String newStr = str;
        while (m.find()) {
            if (varMap.containsKey(m.group(1))) {
                newStr = newStr.replace(m.group(0), varMap.get(m.group(1)));
            } else {
                throw new RuntimeException(str + " in lib dir file,but the variable " + m.group(1) + " not provide!");
            }
        }
        return newStr;
    }
}
