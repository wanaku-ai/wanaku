package ai.wanaku.core.services.io;

import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstanceDataReaderTest {
    private static String TEST_ID = UUID.nameUUIDFromBytes("test-id".getBytes()).toString();
    private File instanceDataFile;


    @AfterEach
    void clean() {
        if (instanceDataFile != null && instanceDataFile.exists()) {
            instanceDataFile.delete();
        }
    }

    void generateDataFilePredictable() throws IOException {
        String path = this.getClass().getResource(".").getPath();
        instanceDataFile = new File(path, "testReadHeader.dat");


        try (InstanceDataWriter binaryRateWriter = new InstanceDataWriter(instanceDataFile, FileHeader.RESOURCE_PROVIDER)) {
            ServiceEntry serviceEntry = new ServiceEntry(TEST_ID);

            binaryRateWriter.write(serviceEntry);
        }
    }


    @Test
    public void testHeader() {
        String path = this.getClass().getResource(".").getPath();
        instanceDataFile = new File(path, "testReadHeader.dat");

        Assertions.assertDoesNotThrow(this::generateDataFilePredictable);

        try (InstanceDataReader reader = new InstanceDataReader(instanceDataFile)) {

            FileHeader fileHeader = reader.getHeader();
            assertEquals(FileHeader.FORMAT_NAME, fileHeader.getFormatName().trim());
            assertEquals(FileHeader.CURRENT_FILE_VERSION, fileHeader.getFileVersion());
            assertEquals(ServiceType.RESOURCE_PROVIDER, fileHeader.getServiceType());
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
        }
    }
}
