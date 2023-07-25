package com.alibaba.jvm.util;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipHelper {

    @SuppressWarnings(value = "Unused")
    public static void zipTo(String folderPath, String zipTo) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipTo))) {
            compressDirectoryToZipFile((new File(folderPath)).toURI(), new File(folderPath), zipOutputStream);
        }
    }

    private static void compressDirectoryToZipFile(URI basePath, File dir, ZipOutputStream out) throws IOException {
        List<File> fileList = Files.list(Paths.get(dir.getAbsolutePath()))
                .map(Path::toFile)
                .collect(Collectors.toList());
        for (File file : fileList) {
            if (file.isDirectory()) {
                compressDirectoryToZipFile(basePath, file, out);
            } else {
                out.putNextEntry(new ZipEntry(basePath.relativize(file.toURI()).getPath()));
                try (FileInputStream in = new FileInputStream(file)) {
                    Utils.IOUtils.copy(in, out);
                }
            }
        }
    }

    public static void unzipTo(Path zipFilePath, Path unzipTo) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), ZipFile.OPEN_READ)){
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = unzipTo.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream in = zipFile.getInputStream(entry)){
                        try (OutputStream out = new FileOutputStream(entryPath.toFile())){
                            Utils.IOUtils.copy(in, out);
                        }
                    }
                }
            }
        }
    }

}
