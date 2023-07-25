package com.alibaba.jvm.cds.diff;

import com.alibaba.jvm.util.Utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ClassComparator {
    public static final String COMMENT = "#";
    private final Map<String, FingerprintFile.ClassDesc> idToClass = new HashMap<>();
    private final List<WithResult> toCompareList = new ArrayList<>();
    private List<FingerprintFile.NotFoundClassDesc> toCompareNotFoundList;
    private List<String> notFoundResultList;
    private final String FILE_VERSION = "1.0.0";
    private final String FILE_FIX_HEADER = "#Don't modify the file.The first 4 rows are version,total class number,invalid class number,invalid not found class number";

    public ClassComparator(List<FingerprintFile.ClassDesc> classDescList, List<FingerprintFile.NotFoundClassDesc> notFoundClassList) {
        for (FingerprintFile.ClassDesc cd : classDescList) {
            idToClass.put(cd.id, cd);
            WithResult wr = new WithResult(cd);
            toCompareList.add(wr);
        }
        this.toCompareNotFoundList = notFoundClassList;
    }


    public String write(String outputFile, int total) throws IOException {
        List<WithResult> diffResult = prepare();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
            writeHeader(bw, total, diffResult);
            writeBody(bw, diffResult, notFoundResultList);
        }
        return outputFile;
    }

    private List<WithResult> prepare() {
        List<WithResult> result = new ArrayList<>();
        Set<String> written = new HashSet<>();
        for (WithResult wr : toCompareList) {
            if (wr.result == CompareResult.SAME || written.contains(wr.cd.name)) {
                continue;
            }
            result.add(wr);
            written.add(wr.cd.name);
        }
        return result;
    }

    private void writeBody(BufferedWriter bw, List<WithResult> diffResult, List<String> notFoundResultList) throws IOException {
        Map<CompareResult, List<WithResult>> map = diffResult.stream().collect(Collectors.groupingBy(wr -> wr.result));
        for (CompareResult cr : CompareResult.values()) {
            List<WithResult> list = map.get(cr);
            if (isEmpty(list)) {
                continue;
            }
            list.sort(Comparator.comparing(wr -> wr.cd.name));

            bw.write(COMMENT + cr);
            bw.newLine();
            for (WithResult wr : list) {
                bw.write(wr.cd.name);
                bw.newLine();
            }
        }
        if (!notFoundResultList.isEmpty()) {
            bw.write(COMMENT + CompareResult.NOT_REAL_NOT_FOUND);
            bw.newLine();
            for (String str : notFoundResultList) {
                bw.write(str);
                bw.newLine();
            }
        }
    }

    private void writeHeader(BufferedWriter bw, int total, List<WithResult> diffResult) throws IOException {
        for (String s : new String[]{FILE_FIX_HEADER, FILE_VERSION, String.valueOf(total), String.valueOf(diffResult.size()), String.valueOf(notFoundResultList.size())}) {
            bw.write(s);
            bw.newLine();
        }
    }

    private void invalidByClassHierarchy() {
        while (true) {
            Set<String> diffClassId = toCompareList.stream().filter((wr) -> wr.result != CompareResult.SAME).map((wr) -> wr.cd.id).collect(Collectors.toSet());
            if (isEmpty(diffClassId)) {
                break;
            }
            List<WithResult> candidate = toCompareList.stream().filter((wr) -> wr.result == CompareResult.SAME).collect(Collectors.toList());
            if (isEmpty(candidate)) {
                break;
            }
            boolean updated = false;
            for (WithResult wr : candidate) {
                if (diffClassId.contains(wr.cd.superId)) {
                    wr.result = CompareResult.SUPER_CHANGED;
                    updated = true;
                } else {
                    if (!isEmpty(wr.cd.interfaceIds)) {
                        for (String id : wr.cd.interfaceIds) {
                            if (diffClassId.contains(id)) {
                                wr.result = CompareResult.INTERFACE_CHANGED;
                                updated = true;
                                break;
                            }
                        }
                    }
                }
            }

            //reach a fixed point,no more to update
            if (!updated) {
                break;
            }
        }
    }

    private boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    public void compare(Map<String, Set<ClassFileInfo>> classFileInfoMap) {
        compareClasses(classFileInfoMap);
        compareNotFoundClasses(classFileInfoMap);
    }

    private void compareNotFoundClasses(Map<String, Set<ClassFileInfo>> classFileInfoMap) {
        notFoundResultList = toCompareNotFoundList.stream().filter((c) -> classFileInfoMap.containsKey(c.name)).map((c) -> c.name).collect(Collectors.toList());
    }

    private void compareClasses(Map<String, Set<ClassFileInfo>> classFileInfoMap) {
        for (WithResult wr : toCompareList) {
            Set<ClassFileInfo> classInfoSet = classFileInfoMap.get(wr.cd.name);
            if (classInfoSet != null) {
                if (classInfoSet.size() > 1) {
                    //if there are multiple classes,try to match by filename.
                    List<ClassFileInfo> list = classInfoSet.stream().filter((c) -> c.getFilename().equals(wr.cd.ownerFile)).collect(Collectors.toList());
                    if (list.isEmpty()) {
                        wr.result = CompareResult.NO_FILE_MATCH;
                    } else {
                        //if it's a multi-release jar.the list size > 1,or else is 1.
                        Optional<ClassFileInfo> cfi = list.stream().filter((c) -> c.getFingerprint().equals(wr.cd.fingerprint)).findAny();
                        wr.result = cfi.isPresent() ? CompareResult.SAME : CompareResult.CRC32_DIFF;
                    }
                } else {
                    String fingerprint = classInfoSet.stream().findFirst().get().getFingerprint();
                    if (wr.cd.fingerprint.equals(fingerprint)) {
                        wr.result = CompareResult.SAME;
                    } else {
                        wr.result = CompareResult.CRC32_DIFF;
                        Utils.log("class: %s, cache: %s, now: %s", wr.cd.name, wr.cd.fingerprint, fingerprint);
                    }
                }
            } else {
                wr.result = CompareResult.CLASS_NOT_FOUND;
            }
        }

        //invalidate classes by class hierarchy until fixed point.
        invalidByClassHierarchy();
    }

    public class WithResult {
        FingerprintFile.ClassDesc cd;
        CompareResult result;

        public WithResult(FingerprintFile.ClassDesc cd) {
            this.cd = cd;
        }
    }
}
