package com.alibaba.jvm.cds.classlist.worker;

@FunctionalInterface
public interface ChainWorker<T> {
    void run(T t) throws Exception;
}
