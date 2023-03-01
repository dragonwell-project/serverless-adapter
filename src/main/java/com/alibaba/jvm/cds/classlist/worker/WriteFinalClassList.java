package com.alibaba.jvm.cds.classlist.worker;

import com.alibaba.jvm.cds.model.ClassCDSDesc;
import com.alibaba.jvm.cds.model.ClassesCDSDesc;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

public class WriteFinalClassList extends ClassListWorker<ClassesCDSDesc> {
    private final String finalClassListName;

    public WriteFinalClassList(ClassListWorker next, String finalClassListName) {
        super(next);
        this.finalClassListName = finalClassListName;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void run(ClassesCDSDesc ccd) throws Exception {
        // clean file content
        PrintWriter pw = new PrintWriter(finalClassListName);
        pw.close();
        try (PrintStream out = new PrintStream(finalClassListName)) {
            for (int i = 0; i < ccd.getAll().size(); i++) {
                ClassCDSDesc data = ccd.getAll().get(i);
                if (data.isInvalid()) {
                    continue;
                }
                data.write(out);
            }
            for (int i = 0; i < ccd.getAllNotFound().size(); i++) {
                ClassCDSDesc data = ccd.getAllNotFound().get(i);
                if (data.isInvalid()) {
                    continue;
                }
                data.write(out);
            }
        }

        if (next != null) {
            next.run(ccd);
        }
    }
}