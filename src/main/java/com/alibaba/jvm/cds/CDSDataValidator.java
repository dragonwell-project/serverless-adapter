package com.alibaba.jvm.cds;

import com.alibaba.jvm.cds.model.ClassCDSDesc;

import java.util.Optional;

public interface CDSDataValidator {
    Optional<Boolean> isInvalid(ClassCDSDesc cdsData);
}
