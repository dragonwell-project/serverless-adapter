package com.alibaba.jvm.probe;

class ProbeConf {
    ExecAction exec;

    HTTPGetAction httpGet;

    TCPSocketAction tcpSocket;

    int initialDelaySeconds;

    int timeoutSeconds = 1;

    int periodSeconds = 10;

    int successThreshold = 1;

    int failureThreshold = 3;

    @Override
    public String toString() {
        return "ProbeConf{" +
                "exec=" + exec +
                ", httpGet=" + httpGet +
                ", tcpSocket=" + tcpSocket +
                ", initialDelaySeconds=" + initialDelaySeconds +
                ", timeoutSeconds=" + timeoutSeconds +
                ", periodSeconds=" + periodSeconds +
                ", successThreshold=" + successThreshold +
                ", failureThreshold=" + failureThreshold +
                '}';
    }
}
