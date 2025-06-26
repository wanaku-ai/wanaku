package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InstanceDataWriterTest {
    private static final String TEST_ID = UUID.nameUUIDFromBytes("test-id".getBytes()).toString();
    private File instanceDataFile;

    void clean() {
        if (instanceDataFile != null && instanceDataFile.exists()) {
            instanceDataFile.delete();
        }
    }

    void generateDataFilePredictable() throws IOException {
        String path = this.getClass().getResource(".").getPath();
        instanceDataFile = new File(path, "testWriteHeader.dat");


        try (InstanceDataWriter binaryRateWriter = new InstanceDataWriter(instanceDataFile, FileHeader.TOOL_INVOKER)) {
            ServiceEntry serviceEntry = new ServiceEntry(TEST_ID);

            binaryRateWriter.write(serviceEntry);
        }
    }


    @Test
    public void testHeader() {
        Assertions.assertDoesNotThrow(this::generateDataFilePredictable);
        Assumptions.assumeTrue(instanceDataFile.exists());

        try (InstanceDataReader reader = new InstanceDataReader(instanceDataFile)) {

            FileHeader fileHeader = reader.getHeader();
            assertEquals(FileHeader.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(FileHeader.CURRENT_FILE_VERSION, fileHeader.getFileVersion());
            assertEquals(ServiceType.TOOL_INVOKER, fileHeader.getServiceType());

        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        } finally {
            clean();
        }
    }


    @Test
    public void testHeaderReadWriteRecords() throws IOException {
        Assertions.assertDoesNotThrow(this::generateDataFilePredictable);
        Assumptions.assumeTrue(instanceDataFile.exists());

        try (InstanceDataReader reader = new InstanceDataReader(instanceDataFile)) {

            FileHeader fileHeader = reader.getHeader();
            assertEquals(FileHeader.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(FileHeader.CURRENT_FILE_VERSION, fileHeader.getFileVersion());
            assertEquals(ServiceType.TOOL_INVOKER, fileHeader.getServiceType());

            ServiceEntry entry = reader.readEntry();
            assertNotNull(entry);
            assertEquals(TEST_ID, entry.getId());
        }
        finally {
            clean();
        }
    }
}


