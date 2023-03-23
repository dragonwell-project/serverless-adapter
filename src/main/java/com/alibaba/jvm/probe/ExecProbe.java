package com.alibaba.jvm.probe;

import com.alibaba.jvm.util.Utils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class ExecProbe implements Probe {
    private final ExecAction exec;

    public ExecProbe(ExecAction exec) {
        this.exec = exec;
    }

    @Override
    public boolean isSuccess(int timeoutSeconds, Utils.LogInfo logInfo) throws Exception {
        Utils.runProcess(Arrays.stream(exec.command).collect(Collectors.toList()),
                true,
                null,
                new Utils.TimeOut(timeoutSeconds, TimeUnit.SECONDS),
                logInfo);
        return true;
    }
}
