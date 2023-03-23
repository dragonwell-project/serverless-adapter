package com.alibaba.jvm.probe;

import com.alibaba.jvm.util.Utils;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

class HttpGetProbe implements Probe {
    private final HTTPGetAction httpGet;

    public HttpGetProbe(HTTPGetAction httpGet) {
        this.httpGet = httpGet;
    }

    @Override
    public boolean isSuccess(int timeoutSeconds, Utils.LogInfo logInfo) throws Exception {
        String urlStr = getURL();
        URL url = new URL(urlStr);
        HttpURLConnection conn = null;
        try {
            if (urlStr.startsWith("https")) {
                trustAllHosts();
                HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                https.setHostnameVerifier(DO_NOT_VERIFY);
                conn = https;
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            if (httpGet.httpHeaders != null) {
                for (HTTPHeader header : httpGet.httpHeaders) {
                    conn.setRequestProperty(header.name, header.value);
                }
            }
            conn.setConnectTimeout(timeoutSeconds * 1000);
            conn.setReadTimeout(timeoutSeconds * 1000);
            conn.setRequestMethod("GET");
            int statusCode = conn.getResponseCode();
            Utils.log("Send http get to %s , statusCode: %s", urlStr, String.valueOf(statusCode));
            if (statusCode >= 200 && statusCode < 400) {
                return true;
            }
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

    }

    private void trustAllHosts() {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getURL() {
        String scheme = "http";
        if ("HTTP".equalsIgnoreCase(httpGet.scheme)) {
            scheme = "http";
        } else if ("HTTPS".equalsIgnoreCase(httpGet.scheme)) {
            scheme = "https";
        }
        String host = isEmpty(httpGet.host) ? "localhost" : httpGet.host;
        return scheme + "://" + host + (isEmpty(httpGet.port) ? "" : ":" + httpGet.port) + (isEmpty(httpGet.path) ? "/" : httpGet.path);
    }

    private final static HostnameVerifier DO_NOT_VERIFY = (s, sslSession) -> true;
}
