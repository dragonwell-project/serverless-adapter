package com.alibaba.jvm.cds.classlist.worker;

import com.alibaba.jvm.cds.model.ClassCDSDesc;
import com.alibaba.jvm.cds.model.ClassesCDSDesc;

import java.io.IOException;

public class PrepareClassNotFoundList extends ClassListWorker<ClassesCDSDesc> {
    public PrepareClassNotFoundList(ClassListWorker next) {
        super(next);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void run(ClassesCDSDesc ccd) throws Exception {
        for (int i = 0; i < ccd.getAllNotFound().size(); i++) {
            ClassCDSDesc data = ccd.getAllNotFound().get(i);
            String name = data.getClassName();
            if (ccd.getAppCDSSet().contains(name)) {
                data.setInvalid(true);
                continue;
            }
            String newId = String.valueOf(ccd.genKlassID());
            data.setId(newId);
        }
        if (next != null) {
            next.run(ccd);
        }
    }
}
