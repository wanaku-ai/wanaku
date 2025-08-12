package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class InstanceDataManager {

    private final String dataDir;
    private final String name;

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

        try (InstanceDataReader reader = new InstanceDataReader(file)) {
            return reader.readEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File serviceFile() {
        return new File(dataDir, name + ".wanaku.dat");
    }

    public void writeEntry(ServiceTarget serviceTarget) {
        final File file = serviceFile();
        if (file.exists()) {
            return;
        }

        final FileHeader fileHeader = newFileHeader(serviceTarget);

        try (InstanceDataWriter writer = new InstanceDataWriter(file, fileHeader)) {
            writer.write(new ServiceEntry(serviceTarget.getId()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static FileHeader newFileHeader(ServiceTarget serviceTarget) {
        if (serviceTarget.getServiceType() == ServiceType.RESOURCE_PROVIDER) {
            return FileHeader.RESOURCE_PROVIDER;
        } else {
            return FileHeader.TOOL_INVOKER;
        }
    }
}
