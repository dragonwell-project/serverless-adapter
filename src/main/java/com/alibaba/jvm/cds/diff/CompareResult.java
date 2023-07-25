package com.alibaba.jvm.cds.diff;

public enum CompareResult {
    CLASS_NOT_FOUND,
    NO_FILE_MATCH,
    CRC32_DIFF,
    SAME,
    SUPER_CHANGED,
    INTERFACE_CHANGED,
    NOT_REAL_NOT_FOUND // Trace not found this class ,Replay found this class
    ;
}
