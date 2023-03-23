package com.alibaba.jvm.probe;

import com.alibaba.jvm.util.Utils;

import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketProbe implements Probe {

    private final TCPSocketAction tcpSocket;

    public SocketProbe(TCPSocketAction tcpSocket) {
        this.tcpSocket = tcpSocket;
    }

    @Override
    public boolean isSuccess(int timeoutSeconds, Utils.LogInfo logInfo) throws Exception {
        Socket client = new Socket();
        client.connect(new InetSocketAddress(isEmpty(tcpSocket.host) ? "localhost" : tcpSocket.host,
                Integer.valueOf(tcpSocket.port)), timeoutSeconds * 1000);
        client.close();
        return true;
    }
}
