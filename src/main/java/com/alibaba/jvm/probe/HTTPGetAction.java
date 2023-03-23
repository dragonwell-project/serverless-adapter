package com.alibaba.jvm.probe;

import java.util.Arrays;

class HTTPGetAction {
    String path;
    String port;
    String host;
    String scheme;
    HTTPHeader[] httpHeaders;

    @Override
    public String toString() {
        return "HTTPGetAction{" +
                "path='" + path + '\'' +
                ", port=" + port +
                ", host='" + host + '\'' +
                ", scheme='" + scheme + '\'' +
                ", httpHeaders=" + Arrays.toString(httpHeaders) +
                '}';
    }
}
