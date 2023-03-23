package com.alibaba.jvm.probe;

import com.alibaba.jvm.util.Utils;
import com.google.gson.Gson;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Implement the same function as https://kubernetes.io/zh-cn/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
 * When run quickstart in k8s, there no way to known startup successful.
 */

public class StartupProbe {
    private static final String LOG_NAME = "startupProbe.log";

    public static void main(String[] args) throws Exception {
        String startupProbe = System.getenv("DRAGONWELL_QUICKSTART_STARTUP_PROBE");
        String pid = getNotEmptyProperty("com.alibaba.quickstart.pid");
        String javaHome = getNotEmptyProperty("com.alibaba.quickstart.javaHome");
        String cacheDir = getNotEmptyProperty("com.alibaba.quickstart.cacheDir");
        Utils.LogInfo logInfo = new Utils.LogInfo(Paths.get(cacheDir, "logs"), LOG_NAME);

        ProbeConf probeConf = getProbeConf(startupProbe);
        Probe probe = getProbe(probeConf);
        if (probeConf.initialDelaySeconds > 0) {
            Utils.log("Initial delay %s s!", String.valueOf(probeConf.initialDelaySeconds));
            Thread.sleep(probeConf.initialDelaySeconds * 1000L);
        }

        int consecutiveSucc = 0;
        int consecutiveFail = 0;
        boolean result = false;
        while (true) {
            Thread.sleep(probeConf.periodSeconds * 1000L);
            try {
                if (probe.isSuccess(probeConf.timeoutSeconds, logInfo)) {
                    consecutiveFail = 0;
                    consecutiveSucc++;
                } else {
                    consecutiveSucc = 0;
                    consecutiveFail++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                consecutiveSucc = 0;
                consecutiveFail++;
            }
            Utils.log("Consecutive succ:  %s ,fail : %s", String.valueOf(consecutiveSucc), String.valueOf(consecutiveFail));
            if (consecutiveFail >= probeConf.failureThreshold) {
                result = false;
                break;
            }
            if (consecutiveSucc >= probeConf.successThreshold) {
                result = true;
                break;
            }
        }
        Utils.log("Probe result : %s", String.valueOf(result));
        if (result) {
            dump(javaHome, pid, logInfo, cacheDir);
        }
    }

    private static ProbeConf getProbeConf(String startupProbe) {
        Gson gson = new Gson();
        System.out.println("startProbe: " + startupProbe);
        String decodeStartupProbe = new String(Base64.getDecoder().decode(startupProbe));
        System.out.println("Base64 decode: " + decodeStartupProbe);
        ProbeConf probeConf = gson.fromJson(decodeStartupProbe, ProbeConf.class);
        if (probeConf.initialDelaySeconds < 0) {
            throw new RuntimeException("initialDelaySeconds < 0");
        }
        if (probeConf.timeoutSeconds < 1) {
            throw new RuntimeException("timeoutSeconds < 1");
        }
        if (probeConf.periodSeconds < 1) {
            throw new RuntimeException("periodSeconds < 1");
        }
        if (probeConf.successThreshold != 1) {
            throw new RuntimeException("successThreshold != 1");
        }
        if (probeConf.failureThreshold < 1) {
            throw new RuntimeException("failureThreshold < 1");
        }
        int available = 0;
        if (probeConf.exec != null) {
            available++;
        }
        if (probeConf.tcpSocket != null) {
            available++;
        }
        if (probeConf.httpGet != null) {
            available++;
        }
        if (available == 0) {
            throw new RuntimeException("No probe config");
        } else if (available > 1) {
            throw new RuntimeException("One and only one of the probe can be config.");
        }
        Utils.log("ProbeConf=%s", probeConf.toString());
        return probeConf;
    }

    private static void dump(String javaHome, String pid, Utils.LogInfo logInfo, String cacheDir) throws Exception {
        notifyFinishProfile(javaHome, pid, logInfo);
        doDump(javaHome, logInfo, cacheDir);
    }

    private static void doDump(String javaHome, Utils.LogInfo logInfo, String cacheDir) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(javaHome, "bin", "java").toString());
        command.add("-Xquickstart:dump,verbose,path=" + cacheDir);
        Utils.runProcess(command, true, null, null, logInfo);
    }

    private static void notifyFinishProfile(String javaHome, String pid, Utils.LogInfo logInfo) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Utils.getjcmdPath(javaHome));
        command.add(pid);
        command.add("QuickStart.dump");
        Utils.runProcess(command, true, null, null, logInfo);
    }

    private static Probe getProbe(ProbeConf probeConf) {
        if (probeConf.exec != null) {
            return new ExecProbe(probeConf.exec);
        } else if (probeConf.tcpSocket != null) {
            return new SocketProbe(probeConf.tcpSocket);
        } else if (probeConf.httpGet != null) {
            return new HttpGetProbe(probeConf.httpGet);
        }
        throw new RuntimeException("Should not reach here!");
    }

    private static String getNotEmptyProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || "".equals(value)) {
            throw new RuntimeException("Property " + key + " must not empty!");
        }
        return value;
    }
}
