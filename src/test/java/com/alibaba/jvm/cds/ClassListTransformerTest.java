package com.alibaba.jvm.cds;

import com.alibaba.jvm.cds.classlist.ClassListTransformer;
import com.alibaba.jvm.cds.classlist.worker.ClassListWorker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClassListTransformerTest {
    private final static String ORIGIN_CLASS_LIST = "cds_origin_class.lst";
    private final static String FINAL_CLASS_LIST = "cds_final_class.lst";

    @Test
    public void testOK() throws Exception {
        transformThenCheck(new String[]{
                "java/lang/Object klass: 0x00000007c4001080 super: 0x0000000000000000 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409",
                "java/io/Serializable klass: 0x00000007c4001298 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d",
                "java/lang/Comparable klass: 0x00000007c40014a8 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f",
        }, new String[]{
                "java/lang/Object id: 1 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409 ",
                "java/io/Serializable id: 2 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d ",
                "java/lang/Comparable id: 3 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f ",
        });
    }

    private void transformThenCheck(String[] input, String[] output) throws Exception {
        Path cacheDir = Files.createTempDirectory("cdstest");
        writeToFile(cacheDir.resolve(ORIGIN_CLASS_LIST), input);
        ClassListWorker first = ClassListTransformer.create(cacheDir.resolve(FINAL_CLASS_LIST).toFile().getAbsolutePath(),
                cacheDir.toFile().getAbsolutePath());
        first.run(cacheDir.resolve(ORIGIN_CLASS_LIST).toFile().getAbsolutePath());
        first.close();
        assertArrayEquals(readFromFile(cacheDir.resolve(FINAL_CLASS_LIST)), output);
    }

    private String[] readFromFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        return lines.toArray(new String[lines.size()]);
    }

    private void writeToFile(Path file, String[] lines) throws IOException {
        Files.write(file, List.of(lines));
    }

    @Test
    public void testSuperNotExist() throws Exception {
        transformThenCheck(new String[]{
                "java/lang/Object klass: 0x00000007c4001080 super: 0x0000000000000000 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409",
                "java/io/Serializable klass: 0x00000007c4001298 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d",
                "java/lang/Comparable klass: 0x00000007c40014a8 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f",
                "common/io/lettuce/core/event/connection/JfrConnectionCreatedEvent origin: file:/home/admin/release-lib/x-decision-common-1.0.4.jar source: file:/home/admin/release-lib/x-decision-common-1.0.4.jar klass: 0x00000007c70a44f0 super: 0x00000007c7038378 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/x-decision-common-1.0.4.jar fingerprint: 0x000004b0e81ff840",
                "common/io/lettuce/core/event/connection/JfrConnectionCreatedEvent klass: 0x00000007c70a44f0 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7"
        }, new String[]{
                "java/lang/Object id: 1 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409 ",
                "java/io/Serializable id: 2 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d ",
                "java/lang/Comparable id: 3 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f ",
        });
    }

    @Test
    public void testInterfaceNotExist() throws Exception {
        transformThenCheck(new String[]{
                "java/lang/Object klass: 0x00000007c4001080 super: 0x0000000000000000 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409",
                "java/io/Serializable klass: 0x00000007c4001298 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d",
                "java/lang/Comparable klass: 0x00000007c40014a8 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f",
                "io/undertow/util/ConnectionUtils$4 origin: file:/home/admin/release-lib/xx-core-1.4.27.Final.jar source: file:/home/admin/release-lib/xx-core-1.4.27.Final.jar klass: 0x00000007c922dae0 super: 0x00000007c4001080 interfaces: 0x00000007c7b11040 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/xx-core-1.4.27.Final.jar fingerprint: 0x0000080f4e8c82de",
                "io/undertow/util/ConnectionUtils$4 klass: 0x00000007c922dae0 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7"
        }, new String[]{
                "java/lang/Object id: 1 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409 ",
                "java/io/Serializable id: 2 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d ",
                "java/lang/Comparable id: 3 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f ",
        });
    }

    @Test
    public void testSuperSuperNotExist() throws Exception {
        transformThenCheck(new String[]{
                "java/lang/Object klass: 0x00000007c4001080 super: 0x0000000000000000 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409",
                "java/io/Serializable klass: 0x00000007c4001298 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d",
                "java/lang/Comparable klass: 0x00000007c40014a8 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f",
                "jackson/databind/ser/std/AsArraySerializerBase origin: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar source: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar klass: 0x00000007c922c8d8 super: 0x00000007c7310690 interfaces: 0x00000007c7275a30 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar fingerprint: 0x00003dc93fca778b",
                "jackson/databind/ser/std/AsArraySerializerBase klass: 0x00000007c922c8d8 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7",
                "jackson/databind/ser/impl/IndexedListSerializer origin: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar source: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar klass: 0x00000007c922d0a0 super: 0x00000007c922c8d8 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/xx-new-client-2.0.9-oneservice-aoge.jar fingerprint: 0x000027c769048c0e",
                "jackson/databind/ser/impl/IndexedListSerializer klass: 0x00000007c922d0a0 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7"
        }, new String[]{
                "java/lang/Object id: 1 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409 ",
                "java/io/Serializable id: 2 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d ",
                "java/lang/Comparable id: 3 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f ",
        });
    }

    @Test
    public void testInterfaceInterfaceNotExist() throws Exception {
        transformThenCheck(new String[]{
                "java/lang/Object klass: 0x00000007c4001080 super: 0x0000000000000000 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409",
                "java/io/Serializable klass: 0x00000007c4001298 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d",
                "java/lang/Comparable klass: 0x00000007c40014a8 super: 0x00000007c4001080 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f",
                "org/xnio/Pooled origin: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar source: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar klass: 0x00000007c922b128 super: 0x00000007c4001080 interfaces: 0x00000007c400f508 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar fingerprint: 0x0000018dba713a53",
                "org/xnio/Pooled klass: 0x00000007c922b128 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7",
                "org/xnio/Buffers$4 origin: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar source: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar klass: 0x00000007c922b338 super: 0x00000007c4001080 interfaces: 0x00000007c922b128 defining_loader_hash: 3be7eae7 origin: file:/home/admin/release-lib/xnio-api-3.3.8.Final.jar fingerprint: 0x000003fad26da6ce",
                "org/xnio/Buffers$4 klass: 0x00000007c922b338 defining_loader_hash: 3be7eae7 initiating_loader_hash: 3be7eae7"
        }, new String[]{
                "java/lang/Object id: 1 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000079887f9c409 ",
                "java/io/Serializable id: 2 origin: /home/admin/jdk/lib/modules fingerprint: 0x0000007184f7709d ",
                "java/lang/Comparable id: 3 origin: /home/admin/jdk/lib/modules fingerprint: 0x000000eb62cb929f ",
        });
    }
}
