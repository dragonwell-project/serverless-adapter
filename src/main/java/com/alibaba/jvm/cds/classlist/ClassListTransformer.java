package com.alibaba.jvm.cds.classlist;

import com.alibaba.jvm.cds.classlist.worker.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

public class ClassListTransformer {
    private ClassListTransformer() {
    }

    public static ClassListWorker create(String finalClassListName, String dirPath) {
        return new ParseOriginClassList(
                new SourceDecoder(
                        new PrepareClassNotFoundList(
                                new WriteFinalClassList(
                                        null,
                                        finalClassListName
                                )
                        ),
                        dirPath
                ));
    }

    public static void main(String... args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            printHelp();
        }

        String originClassListName;
        String finalClassListName;
        String dirPath;
        File f = new File(args[0]);
        if (!f.exists()) {
            System.out.println("Non exists input file: " + args[0]);
            return;
        }
        if (args.length == 3) {
            File cachePath = new File(args[2]);
            if (!cachePath.exists()) {
                throw new FileNotFoundException("cache path error: " + cachePath);
            }
            dirPath = cachePath.getAbsolutePath();
        } else {
            dirPath = "./tmp";
        }
        originClassListName = args[0];
        finalClassListName = args[1];

        ClassListWorker first = create(finalClassListName, dirPath);
        first.run(originClassListName);
        first.close();
    }


    public static void printHelp() {
        System.out.println("Usage: ");
        System.out.println(" <input file> <output file>");
        System.exit(0);
    }
}
