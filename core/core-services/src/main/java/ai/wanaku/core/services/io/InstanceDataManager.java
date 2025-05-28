package ai.wanaku.core.services.io;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class InstanceDataManager {

    private String dataDir;
    private String name;

    public InstanceDataManager(String dataDir, String name) {
        this.dataDir = dataDir;
        this.name = name;
    }

    public boolean dataFileExists() {
        final File serviceFile = serviceFile();
        return serviceFile.exists();
    }

    public void createDataDirectory() throws IOException {
        Files.createDirectories(serviceFile().getParentFile().toPath());
    }

    public ServiceEntry readEntry() {
        final File file = serviceFile();
        if (!file.exists()) {
            return null;
        }

        try (InstanceDataReader reader = new InstanceDataReader(file))  {
            return reader.readEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File serviceFile() {
        final File file = new File(dataDir, name + ".wanaku.dat");
        return file;
    }

    public void writeEntry(ServiceTarget serviceTarget) {
        final File file = serviceFile();
        if (file.exists()) {
            return;
        }

        FileHeader fileHeader = null;
        if (serviceTarget.getServiceType() == ServiceType.RESOURCE_PROVIDER) {
            fileHeader = FileHeader.RESOURCE_PROVIDER;
        } else {
            fileHeader = FileHeader.TOOL_INVOKER;
        }

        try (InstanceDataWriter writer = new InstanceDataWriter(file, fileHeader)) {
            writer.write(new ServiceEntry(serviceTarget.getId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
