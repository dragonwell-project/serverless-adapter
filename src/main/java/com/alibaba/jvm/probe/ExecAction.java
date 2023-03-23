package com.alibaba.jvm.probe;

import java.util.Arrays;

class ExecAction {
    String[] command;

    @Override
    public String toString() {
        return "ExecAction{" +
                "command=" + Arrays.toString(command) +
                '}';
    }
}
