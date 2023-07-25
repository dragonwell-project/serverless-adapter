package com.alibaba.jvm.cds.diff;

import java.util.Objects;

public class ClassFileInfo {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassFileInfo that = (ClassFileInfo) o;
        return Objects.equals(fingerprint, that.fingerprint) && Objects.equals(filename, that.filename) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint, filename, version);
    }

    private String fingerprint;
    private String filename;
    /**
     * multi-release jar support
     */
    private String version;

    public ClassFileInfo(String fingerprint, String filename) {
        this.fingerprint = fingerprint;
        this.filename = filename;
    }

    public ClassFileInfo(String fingerprint, String filename, String version) {
        this.fingerprint = fingerprint;
        this.filename = filename;
        this.version = version;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getFilename() {
        return filename;
    }

    public String getVersion() {
        return version;
    }
}
