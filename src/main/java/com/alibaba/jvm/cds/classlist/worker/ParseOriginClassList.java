package com.alibaba.jvm.cds.classlist.worker;

import com.alibaba.jvm.cds.model.ClassCDSDesc;
import com.alibaba.jvm.cds.model.ClassesCDSDesc;
import com.alibaba.jvm.cds.diff.FingerprintFile;
import com.alibaba.jvm.cds.CDSDumper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ParseOriginClassList extends ClassListWorker<String> {

    public ParseOriginClassList(ClassListWorker<ClassesCDSDesc> next) {
        super(next);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void run(String originClassListName) throws Exception {
        ClassesCDSDesc ccd = new ClassesCDSDesc();
        try (BufferedReader in = new BufferedReader(new FileReader(originClassListName))) {
            String line = in.readLine();
            HashMap<String, ClassCDSDesc> nameidCDSData = new HashMap<>();
            while (!line.isEmpty()) {
                // skip the comment in classlist
                if(line.charAt(0) != '#') {
                    if (line.contains("defining_loader_hash:") && line.contains("initiating_loader_hash:")) {
                        String name = getKlassName(line);
                        String id = getId(line);
                        String definingHash = getDefiningLoaderHash(line);
                        String initiatingHash = getInitiatingLoaderHash(line);
                        ClassCDSDesc oldData = nameidCDSData.get(name + id + definingHash);
                        // oldData != null: this klass might be from __JVM_DefineClass__ so it doesn't
                        //                  get recorded in the oldData Set.
                        if (oldData != null && oldData.getInitiatingHash() == null) {
                            oldData.setInitiatingHash(initiatingHash);
                        }
                    } else if (line.contains("source: not.found.class")) {
                        String name = getKlassName(line);
    
                        String source = getKlassPath(line);
                        String initiatingHash = getInitiatingLoaderHash(line);
                        if (!ccd.getNotFoundSet().contains(name + initiatingHash)) {
                            ClassCDSDesc newData = new ClassCDSDesc(name, source, initiatingHash);
                            ccd.getAllNotFound().add(newData);
                            ccd.getNotFoundSet().add(name + initiatingHash);
                        }
    
                    } else if (line.contains("source: __JVM_DefineClass__")) {
                        // nothing to do
                    } else if (line.contains("@lambda-form-invoker") || line.contains("@lambda-proxy")) {
                        // nothing to do
                    } else {
                        String name = getKlassName(line);
                        String id = getId(line);
                        String source = getKlassPath(line);
                        String superId = getSuperId(line);
                        List<String> iids = getInterfaces(line);
                        String definingHash = getDefiningLoaderHash(line);
                        String fingerprint = getFingerprint(line);
                        String originSource = getOriginSource(line);
                        ClassCDSDesc newData = new ClassCDSDesc(name, id, superId, iids, source, originSource, definingHash, fingerprint);
                        ccd.getAll().add(newData);
                        nameidCDSData.put(name + id + definingHash, newData);
                    }
                    line = in.readLine();
                    if (line == null) {
                        break;
                    }
                }
            }
        }

        if (next != null) {
            next.run(ccd);
        }

        if (CDSDumper.enableStaticDiffClass()) {
            String dest = genFingerprintFile(ccd);
            System.out.println("Write CDS diff support file to " + dest + " successful!");
        }

    }

    private String getKlassPath(String line) {
        int index = line.indexOf("source:");
        if (index == -1) {
            return null;
        }
        index += "source:".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (line.charAt(index) != ' ') index++;
        String path = line.substring(start, index);
        return path;
    }

    private String getId(String line) throws Exception {
        int index = line.indexOf("klass: ");
        if (index == -1) {
            throw new Exception("no Id in line: " + line);
        }
        index += "klass: ".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String id = line.substring(start, index);
        return id.trim();
    }

    private String getSuperId(String line) {
        int index = line.indexOf("super: ");
        if (index == -1) {
            return null;
        }
        index += "super: ".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String superId = line.substring(start, index);
        return superId;
    }

    private List<String> getInterfaces(String line) {
        List<String> itfs = new ArrayList<String>();
        int index = line.indexOf("interfaces: ");
        if (index == -1) {
            return itfs;
        }
        index += "interfaces: ".length();
        while (line.charAt(index) == ' ') index++;

        String intfs;
        // find first alphabet
        int i;
        for (i = index; i < line.length(); ) {
            i += 18;
            if (i >= line.length()) {
                break;
            } else {
                while (line.charAt(i) == ' ') i++;
                if ((i + 1) >= line.length()) {
                    break;
                } else {
                    if (line.charAt(i) == '0' && line.charAt(i + 1) == 'x') {
                        continue;
                    } else {
                        break;
                    }
                }
            }
        }
        if (i == line.length()) {   // no alphabet anymore
            intfs = line.substring(index).trim();
        } else {
            try {
                intfs = line.substring(index, i).trim();
            } catch (Exception e) {
                System.out.println("error: " + line);
                throw new RuntimeException(e);
            }
        }

        String[] iids = intfs.split(" ");
        for (String s : iids) {
            itfs.add(s);
        }
        return itfs;
    }

    private String getDefiningLoaderHash(String line) {
        int index = line.indexOf("defining_loader_hash: ");
        if (index == -1) return null;
        index += "defining_loader_hash: ".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String hash = line.substring(start, index);
        return hash;
    }

    private String getInitiatingLoaderHash(String line) {
        int index = line.indexOf("initiating_loader_hash: ");
        if (index == -1) return null;
        index += "initiating_loader_hash: ".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String hash = line.substring(start, index);
        return hash;
    }

    private String getFingerprint(String line) {
        int index = line.indexOf("fingerprint: ");
        if (index == -1) return null;
        index += "fingerprint: ".length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String name = line.substring(start, index);
        return name;
    }

    private String getOriginSource(String line) {
        return getValue(line, "origin: ");
    }

    private String getValue(String line, String key) {
        int index = line.indexOf(key);
        if (index == -1) return null;
        index += key.length();
        while (line.charAt(index) == ' ') index++;
        int start = index;
        while (index < line.length() && line.charAt(index) != ' ') index++;
        String name = line.substring(start, index);
        return name;
    }

    private String getKlassName(String line) {
        int index = 0;
        while (line.charAt(index) != ' ') index++;
        String name = line.substring(0, index);
        return name;
    }

    String genFingerprintFile(ClassesCDSDesc ccd) throws Exception {
        FingerprintFile fingerprintFile = new FingerprintFile();
        for (ClassCDSDesc c : ccd.getAll()) {
            fingerprintFile.addClassRecord(c.getClassName(), c.getOriginSource(), c.getFingerprint(), c.getId(), c.getSuperId(), c.getInterfaceIds());
        }
        for (ClassCDSDesc c : ccd.getAllNotFound()) {
            fingerprintFile.addNotFoundClassRecord(c.getClassName());
        }
        return fingerprintFile.write(CDSDumper.getInfo().dirPath);
    }
}
