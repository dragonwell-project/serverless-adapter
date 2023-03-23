package com.alibaba.jvm.probe;

import com.alibaba.jvm.util.Utils;

interface Probe {
    boolean isSuccess(int timeoutSeconds, Utils.LogInfo logInfo) throws Exception;

    default boolean isEmpty(String s) {
        return s == null || "".equals(s);
    }
}
