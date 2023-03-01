package com.alibaba.jvm.cds.classlist.worker;

import java.io.Closeable;

public abstract class ClassListWorker<T> implements Closeable, ChainWorker<T> {
    protected final ClassListWorker next;

    public ClassListWorker(ClassListWorker next) {
        this.next = next;
    }
}
