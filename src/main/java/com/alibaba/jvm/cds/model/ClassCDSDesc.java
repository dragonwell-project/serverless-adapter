package com.alibaba.jvm.cds.model;

import java.io.PrintStream;
import java.util.List;

public class ClassCDSDesc {
    String className;
    String id;
    String superId;
    List<String> interfaceIds;
    String originSource; // originSource != source when it's a fat jar.
    String source;
    String definingHash;
    String initiatingHash;
    String fingerprint;
    boolean invalid;

    public ClassCDSDesc(String name, String id, String superId, List<String> iids, String sourcePath, String originSource, String definingHash, String fingerprint) {
        className = name;
        this.id = id;
        this.superId = superId;
        interfaceIds = iids;
        source = sourcePath;
        this.originSource = originSource;
        this.definingHash = definingHash;
        this.initiatingHash = null;
        this.fingerprint = fingerprint;
    }

    public ClassCDSDesc(String name, String sourcePath, String initiatingHash) {
        className = name;
        this.id = null;
        this.superId = null;
        interfaceIds = null;
        source = sourcePath;
        this.definingHash = null;
        this.initiatingHash = initiatingHash;
        this.fingerprint = null;
    }


    public String getClassName() {
        return className;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSuperId() {
        return superId;
    }

    public void setSuperId(String superId) {
        this.superId = superId;
    }

    public List<String> getInterfaceIds() {
        return interfaceIds;
    }

    public String getOriginSource() {
        return originSource;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDefiningHash() {
        return definingHash;
    }

    public String getInitiatingHash() {
        return initiatingHash;
    }

    public void setInitiatingHash(String initiatingHash) {
        this.initiatingHash = initiatingHash;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public void write(PrintStream out) {
        ClassCDSDesc data = this;
        out.print(data.className);
        out.print(" ");
        out.print("id: " + data.id);
        out.print(" ");

        if (data.source != null && !data.source.contains("jrt:")) {
            if (data.superId != null) {
                out.print("super: " + data.superId);
                out.print(" ");
            }
            if (data.interfaceIds != null && data.interfaceIds.size() != 0) {
                out.print("interfaces: ");
                for (String s : data.interfaceIds) {
                    out.print(s);
                    out.print(" ");
                }
            }
            out.print("origin: " + data.originSource);
            out.print(" ");
            out.print("source: " + data.source);
            out.print(" ");
            // System.out.println(data.source);
        } else {
            out.print("origin: " + data.originSource);
            out.print(" ");   // Well... one loaded by AppClassLoader may not have "source"... because it's is_builtin.
        }
        if (data.initiatingHash != null) {
            out.print("initiating_loader_hash: " + data.initiatingHash);
            out.print(" ");
        } else if (data.definingHash != null) {
            out.print("initiating_loader_hash: " + data.definingHash);
            out.print(" ");
        }

        if (data.definingHash != null) {
            out.print("defining_loader_hash: " + data.definingHash);
            out.print(" ");
        }
        if (data.fingerprint != null) {
            out.print("fingerprint: " + data.fingerprint);
            out.print(" ");
        }
        out.println();
    }
}
