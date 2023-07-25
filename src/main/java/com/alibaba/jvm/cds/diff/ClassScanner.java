package com.alibaba.jvm.cds.diff;

import com.alibaba.jvm.util.Utils;
import com.alibaba.jvm.util.ZipHelper;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ClassScanner {
    private static final String CLASS = ".class";
    private static final String JAR = ".jar";
    private static final String CLASS_DUMMY_FILE = "class-dummy";
    public static final String MULTIPLE_RELEASE_ENTRY = "META-INF/versions/";

    /**
     * scan all classes in directory that contains in libDirFile
     *
     * @param typeDirs
     * @return key is the class name internal form, value is a list that all class file with same name.
     */
    public static Map<String, Set<ClassFileInfo>> doScan(List<String> typeDirs) throws IOException {
        Map<String, Set<ClassFileInfo>> result = new HashMap<>();
        for (String line : typeDirs) {
            String[] typeDir = line.trim().split(",");
            if (typeDir == null || typeDir.length != 2) {
                throw new RuntimeException(line + " is ill-formed!");
            }

            Utils.log("Scan dir %s with type %s", typeDir[1], typeDir[0]);
            if ("class".equals(typeDir[0])) {
                check(typeDir[1], true);
                scanClasses(result, typeDir[1]);
            } else if ("jar".equals(typeDir[0])) {
                check(typeDir[1], false);
                scanJar(result, typeDir[1]);
            } else {
                throw new RuntimeException("Expect type is class or jar, actually is " + typeDir[0]);
            }
        }
        return result;
    }

    private static void scanClasses(Map<String, Set<ClassFileInfo>> result, String dir) throws IOException {
        List<File> classFiles = Files.walk(new File(dir).toPath())
                .filter((path -> path.getFileName().toString().endsWith(CLASS)))
                .map((p) -> p.toFile())
                .filter((f) -> f.isFile())
                .collect(Collectors.toList());
        readClassFromClassFile(classFiles, result);
    }

    private static void scanJar(Map<String, Set<ClassFileInfo>> result, String jarPath) throws IOException {
        File f = new File(jarPath);
        if (f.isDirectory()) {
            readClassesInJar(List.of(new File(jarPath).listFiles((d, n) -> n.endsWith(JAR))), true, result);
        } else {
            if (f.getName().endsWith(JAR)) {
                readClassesInJar(List.of(f), true, result);
            } else {
                throw new RuntimeException(jarPath + " is not a directory and not a jar!");
            }
        }
    }

    private static void readClassesInJar(List<File> jarFiles, boolean recursive, Map<String, Set<ClassFileInfo>> result) throws IOException {
        for (File file : jarFiles) {
            boolean fatJar = false;
            try (JarFile jarFile = new JarFile(file)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    if (jarEntry.getName().endsWith(CLASS)) {
                        String[] nameVersion = getNameVersion(jarEntry);
                        try (InputStream input = jarFile.getInputStream(jarEntry)) {
                            add(result, toClassName(nameVersion[0]), Utils.fingerprint(input), file.getName(), nameVersion[1]);
                        }
                    } else if (jarEntry.getName().endsWith(JAR)) {
                        fatJar = true;
                    }
                }
            }

            if (fatJar && recursive) {
                File tmp = Files.createTempDirectory("cds-cnf").toFile();
                Utils.log("Begin to unzip : " + file + " to " + tmp);
                ZipHelper.unzipTo(file.toPath(), tmp.toPath());
                Utils.log("End to unzip : " + file + " to " + tmp);
                Path unzipPath = tmp.toPath();

                List<File> files = Files.walk(unzipPath).filter((path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(JAR) || name.endsWith(CLASS);
                })).map((p) -> p.toFile()).collect(Collectors.toList());

                readClassesInJar(files.stream().
                        filter((f) -> f.isFile() && f.getName().endsWith(JAR))
                        .collect(Collectors.toList()), false, result);
                readClassFromClassFile(files.stream().
                        filter((f) -> f.isFile() && f.getName().endsWith(CLASS))
                        .collect(Collectors.toList()), result);
                deleteFiles(tmp);
            }
        }
    }

    private static String[] getNameVersion(JarEntry jarEntry) {
        String entryName = jarEntry.getName();
        if (jarEntry.getName().startsWith(MULTIPLE_RELEASE_ENTRY)) {
            //entry like this: META-INF/versions/9/org/apache/logging/log4j/core/util/SystemClock.class
            int index = entryName.indexOf('/', MULTIPLE_RELEASE_ENTRY.length());
            if (index != -1) {
                return new String[]{entryName.substring(index + 1), entryName.substring(MULTIPLE_RELEASE_ENTRY.length(), index)};
            } else {
                return new String[]{entryName, null};
            }
        } else {
            return new String[]{entryName, null};
        }
    }

    /**
     * this class file don't know the package.so need use asm parse it
     *
     * @param classFiles
     * @param result
     */
    private static void readClassFromClassFile(List<File> classFiles, Map<String, Set<ClassFileInfo>> result) throws IOException {
        for (File classFile : classFiles) {
            try (FileInputStream fis = new FileInputStream(classFile)) {
                //read the class file have 2 purpose:
                //1. get the full qualified class name
                //2. calculate the fingerprint
                ByteArrayOutputStream baos = new ByteArrayOutputStream((int) classFile.length());
                Utils.IOUtils.copy(fis, baos);
                byte[] fileContent = baos.toByteArray();

                try {
                    add(result, new ClassReader(fileContent).getClassName(), Utils.fingerprint(fileContent, fileContent.length), CLASS_DUMMY_FILE);
                } catch (Exception e) {
                    //there may some older classes that cannot parse it.ignore it
                    e.printStackTrace();
                }
            }
        }
    }

    private static void add(Map<String, Set<ClassFileInfo>> result, String className, String fingerprint, String filename) {
        if (!result.containsKey(className)) {
            result.put(className, new HashSet<>());
        }
        result.get(className).add(new ClassFileInfo(fingerprint, filename));
    }

    private static void add(Map<String, Set<ClassFileInfo>> result, String className, String fingerprint, String filename, String version) {
        if (!result.containsKey(className)) {
            result.put(className, new HashSet<>());
        }
        result.get(className).add(new ClassFileInfo(fingerprint, filename, version));
    }

    private static void check(String name, boolean mustBeDir) {
        File f = new File(name);
        if (!f.exists()) {
            throw new RuntimeException(name + " not exist!");
        }
        if (mustBeDir) {
            if (!f.isDirectory()) {
                throw new RuntimeException(name + " is not a directory!");
            }
        }
    }

    protected static String toClassName(String entryName) {
        return entryName.substring(0, entryName.length() - CLASS.length());
    }

    private static void deleteFiles(File f) {
        if (f.exists()) {
            if (f.isFile()) {
                if (!f.delete()) {
                    throw new Error("Clean up temporary file : " + f.getName() + " failed!");
                }
            } else if (f.isDirectory()) {
                try {
                    Files.walkFileTree(f.toPath(), new SimpleFileVisitor<Path>() {
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
                    throw new Error("Clean up temporary directory: " + f.getName() + " failed!", e);
                }
            }
        }
    }
}
