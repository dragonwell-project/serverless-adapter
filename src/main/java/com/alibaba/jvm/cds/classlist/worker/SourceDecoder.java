package com.alibaba.jvm.cds.classlist.worker;

import com.alibaba.jvm.cds.CDSDataValidFactory;
import com.alibaba.jvm.cds.model.ClassCDSDesc;
import com.alibaba.jvm.cds.model.ClassesCDSDesc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class SourceDecoder extends ClassListWorker<ClassesCDSDesc> {
    private final String dirPath;
    HashMap<String, String> fatJarCache = new HashMap<>();

    Set<String> tempFiles = new HashSet<>();

    public SourceDecoder(ClassListWorker next, String dirPath) {
        super(next);
        this.dirPath = dirPath;
    }

    @Override
    public void close() throws IOException {
        tempFiles.forEach((f) -> doDelete(f));
    }

    private static void doDelete(String source) {
        File f = new File(source);
        if (f.exists()) {
            if (f.isFile()) {
                if (!f.delete()) {
                    throw new Error("Clean up temporary file : " + source + " failed!");
                }
            } else if (f.isDirectory()) {
                Path dir = Paths.get(source);
                try {
                    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new Error("Clean up temporary directory: " + source + " failed!", e);
                }
            }
        }
    }

    @Override
    public void run(ClassesCDSDesc ccd) throws Exception {
        // Now replace Id with incremental numbers
        // First should be java/lang/Object
        System.out.println("Total class load: " + ccd.getAll().size());
        ClassCDSDesc data = ccd.getAll().get(0);
        if (!data.getClassName().equals("java/lang/Object")) {
            throw new RuntimeException("First should be java/lang/Object!");
        }
        data.setSuperId(null);
        ccd.getIdIds().put(data.getId(), "1");
        data.setId(String.valueOf(ccd.genKlassID()));
        decodeSource(data);

        for (int i = 1; i < ccd.getAll().size(); i++) {
            boolean isvalid = true;
            data = ccd.getAll().get(i);
            if (invalidCheck(data, ccd)) {
                // continue;
            }
            String newId = String.valueOf(ccd.genKlassID());
            ccd.getIdIds().put(data.getId(), newId);
            data.setId(newId);
            if (data.getSuperId() != null) {
                String sp = ccd.getIdIds().get(data.getSuperId());
                if (sp == null) {
                    isvalid = false;
                }
                data.setSuperId(sp);
            }
            if (data.getInterfaceIds() != null && data.getInterfaceIds().size() != 0) {
                for (int j = 0; j < data.getInterfaceIds().size(); j++) {
                    String intf = data.getInterfaceIds().get(j);
                    String iid = ccd.getIdIds().get(intf);
                    if (iid == null) {
                        isvalid = false;
                    }
                    data.getInterfaceIds().remove(j);
                    data.getInterfaceIds().add(j, iid);
                }
            }
            if (isvalid) {
                decodeSource(data);
                Optional<String> invalid = CDSDataValidFactory.getInstance().isInvalid(data);
                if (invalid.isPresent()) {
                    data.setInvalid(true);
                    System.out.println(invalid.get() + ",CDSData id: " + data.getId() + ",name: " + data.getClassName());
                    continue;
                }
            }
        }

        if (next != null) {
            next.run(ccd);
        }
    }

    private void decodeSource(ClassCDSDesc data) throws Exception {
        if (data.getSource() == null) {
            return;
        }
        String source = data.getSource();  // convenience
        int end = -1;
        int start = -1;
        boolean isFatJar = false;
        boolean isJarFile = false;
        boolean isFileDir = false;
        String mainJarName;
        String fatJarName;
        // assert source.contains(".jar");
        if (source.contains("jar:file:")) {

            String[] jjs = source.split("!");
            if (jjs.length >= 2) {
                if (jjs[0].contains(".jar")) {
                    if (jjs[1].contains(".jar")) {
                        isFatJar = true;
                    } else {
                        if (jjs[1].length() == 1 && jjs[1].endsWith("/")) {
                            // this is a regular jar (note it is processed by custom loader)
                            isJarFile = true;
                        } else {
                            // assume that it is a file dir in jar. It may be wrong here!
                            isFileDir = true;
                        }
                    }
                } else {
                    throw new Exception("Cannot parse " + source);
                }
            }

            if (isJarFile) {
                start = source.indexOf("jar:file:");
                end = jjs[0].length() - 1;
                while (jjs[0].charAt(end) == '/') end--;
                mainJarName = jjs[0].substring(start, end + 1);
                source = mainJarName;
                source = source.substring("jar:file:".length());
                data.setSource(source);
            }

            // this is a fat jar case
            // jar:file:<main jar>!<fat jar>!/
            if (isFatJar) {
                mainJarName = jjs[0].substring("jar:file:".length());
                start = 0;
                end = jjs[1].length() - 1;
                // skip '/' at first
                while (jjs[1].charAt(start) == '/') {
                    start++;
                }
                // skip "!/" at end
                while (jjs[1].charAt(end) == '/' || jjs[1].charAt(end) == '!') {
                    end--;
                }
                fatJarName = jjs[1].substring(start, end + 1);
                extractFatJar(data, mainJarName, fatJarName);
            }
            // file dir in jar
            // Test id:626736 super:4080 source:jar:file:/home/myid/tests/appcds/test-1.0-SNAPSHOT.jar!/BOOT-INF/classes!/
            if (isFileDir) {
                mainJarName = jjs[0].substring("jar:file:".length());
                start = 0;
                end = jjs[1].length() - 1;
                ;
                while (jjs[1].charAt(end) == '/' || jjs[1].charAt(end) == '!') end--;
                while (jjs[1].charAt(start) == '/') start++;
                String fileDir = jjs[1].substring(start, end + 1);
                // fatJarCache.put(mainJarName, fileDir);

                extractFileDir(data, mainJarName, fileDir);
            }
        } else if (source.contains("file:")) {
            // regular jar case
            // file:<dir/<main jar>
            int index = source.indexOf("file:");
            index += "file:".length();
            while (source.charAt(index) == ' ') {
                index++;
            }
            data.setSource(source.substring(index));
        }
    }

    // extract fat jar from main jar, put it to local tmp dir
    private boolean extractFatJar(ClassCDSDesc data, String mainJarName, String fatJarName) {
        String source = fatJarCache.get(fatJarName);
        if (source != null) {
            data.setSource(source);
            return true;
        }
        try {
            JarFile jar = new JarFile(mainJarName);
            ZipEntry ze = jar.getEntry(fatJarName);
            String tmpFile = dirPath + "/" + fatJarName;    // this will be ./tmp/BOOT/.../fatJar.jar
            if (ze != null) {
                InputStream sin = jar.getInputStream(ze);
                File parent = new File(tmpFile).getParentFile();
                mkdir(parent);   // if not exists the dir, create it.
                tmpFile = dirPath + "/" + fatJarName;
//                System.out.println("Extracted file: " + tmpDir + " " + fatJarName);
                FileOutputStream output = new FileOutputStream(tmpFile);
                byte buff[] = new byte[4096];
                int n;
                while ((n = sin.read(buff)) > 0) {
                    output.write(buff, 0, n);
                }
            } else {
                System.out.println("ZipEntry does not exist for " + fatJarName);
                return false;
            }
            data.setSource(tmpFile);
            fatJarCache.put(fatJarName, tmpFile);
            return true;
        } catch (IOException e) {
            System.out.println("Exception happened: " + e);
            return false;
        }

    }

    private boolean extractFileDir(ClassCDSDesc data, String mainJarName, String path) throws Exception {
        String outerPath = path;  // usually be 'BOOT-INF/classes/'
        String dir = fatJarCache.get(path);
        if (dir != null) {
            return true;
        }
        String newName = data.getClassName();
        String filePrePath = null;
        if (newName.contains("/")) {      // what does these code work for??
            int index = newName.length() - 1;
            while (newName.charAt(index) != '/') index--;
            if (!path.endsWith("/")) path += "/";
            filePrePath = newName.substring(0, index + 1);
            newName = newName.substring(index + 1);
        } else if (newName.contains(".")) {
            int index = newName.length() - 1;
            while (newName.charAt(index) != '.') index--;
            if (!path.endsWith("/")) path += "/";
            filePrePath = newName.substring(0, index + 1);
            filePrePath = filePrePath.replace('.', '/');
            newName = newName.substring(index + 1);
        } else {
            if (!path.endsWith("/")) path += "/";
        }

        try {
            JarFile jar = new JarFile(mainJarName);
            if (filePrePath != null) {
                path += filePrePath;
            }
            String total = path + newName + ".class";
            ZipEntry ze = jar.getEntry(total);
            String tmpFile;
            if (ze != null) {
                InputStream input = jar.getInputStream(ze);
                File fdir = new File(dirPath + "/" + path);
                mkdir(fdir);
                tmpFile = dirPath + "/" + path + "/" + newName + ".class";
                FileOutputStream output = new FileOutputStream(tmpFile);
                byte buff[] = new byte[4096];
                int n;
                while ((n = input.read(buff)) > 0) {
                    output.write(buff, 0, n);
                }
            } else {
                throw new Exception("ZipEntry does not exist for " + path + " ?");
            }
            fatJarCache.put(path, tmpFile);
            data.setSource(dirPath + "/" + outerPath);
            return true;
        } catch (IOException e) {
            System.out.println("Exception happened: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    // recursive call
    private void mkdir(File dir) {
        if (!dir.exists()) {
            File parentDir = dir.getParentFile();
            if (!parentDir.exists()) {
                mkdir(parentDir);
            }
            dir.mkdir();
            tempFiles.add(dir.getAbsolutePath());
        }
    }

    private boolean invalidCheck(ClassCDSDesc data, ClassesCDSDesc ccd) {
        if (data.getSource() == null) {
            return false;
        }
        // appCDS
        if (data.getDefiningHash() == null) {
            if (ccd.getEagerCDSSet().contains(data.getClassName()) || ccd.getAppCDSSet().contains(data.getClassName())) {
                return true;
            } else {
                ccd.getAppCDSSet().add(data.getClassName());
            }
        } else {
            //eagerAppCDS
            if (ccd.getAppCDSSet().contains(data.getClassName())) {
                return true;
            } else {
                ccd.getEagerCDSSet().add(data.getClassName());
            }
        }
        return false;
    }
}
