package com.alibaba.jvm.cds.valid;

import com.alibaba.jvm.cds.CDSDataValidator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

import com.alibaba.jvm.cds.model.ClassCDSDesc;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.JavaUtilJarAccess;

public class SignedJarValidator implements CDSDataValidator {
    private Map<String, Optional<Boolean>> signSourceMap = new HashMap<>();

    @Override
    public Optional<Boolean> isInvalid(ClassCDSDesc cdsData) {
        if (null == cdsData.getSource()|| "".equals(cdsData.getSource())) {
            return Optional.of(false);
        }
        return signSourceMap.computeIfAbsent(cdsData.getSource(), (s) -> isSigned(s));
    }

    Optional<Boolean> isSigned(String source) {
        File f = new File(source);
        if (!f.exists() || f.isDirectory() || !f.getName().toLowerCase().endsWith(".jar")) {
            return Optional.of(false);
        }
        JavaUtilJarAccess juja = SharedSecrets.javaUtilJarAccess();
        try (JarFile jarFile = new JarFile(f)) {
            List<Object> list = juja.getManifestDigests(jarFile);
            return Optional.of(list != null && !list.isEmpty());
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static void main(String[] args) throws IOException {
        SignedJarValidator sjv = new SignedJarValidator();
        for (String file : args) {
            System.out.println("[" + sjv.isSigned(file) + "] " + file);
        }
    }
}
