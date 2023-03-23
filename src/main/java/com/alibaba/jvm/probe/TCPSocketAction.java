package com.alibaba.jvm.probe;

class TCPSocketAction {
    String port;
    String host;

    @Override
    public String toString() {
        return "TCPSocketAction{" +
                "port=" + port +
                ", host='" + host + '\'' +
                '}';
    }
}
