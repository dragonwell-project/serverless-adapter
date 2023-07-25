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

import java.lang.reflect.Method;

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
        
        try (JarFile jarFile = new JarFile(f)) {
            String sharedSecretClassName = ((getVersion() == 11) ? 
                                            "jdk.internal.misc.SharedSecrets" : "jdk.internal.access.SharedSecrets");
            Class sharedSecretClass = Class.forName(sharedSecretClassName);
            Method m1 = sharedSecretClass.getDeclaredMethod("javaUtilJarAccess");
            Object javaUtilJarAccess = m1.invoke(null);
            Class juja = javaUtilJarAccess.getClass();
            Method m2 = juja.getDeclaredMethod("getManifestDigests", JarFile.class);
            m2.setAccessible(true);
            List<Object> list = (List<Object>) m2.invoke(javaUtilJarAccess, jarFile);
            return Optional.of(list != null && !list.isEmpty());
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        } catch (Exception e) {
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

    private static int getVersion() {
        String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if(dot != -1) { version = version.substring(0, dot); }
        } return Integer.parseInt(version);
    }
}
