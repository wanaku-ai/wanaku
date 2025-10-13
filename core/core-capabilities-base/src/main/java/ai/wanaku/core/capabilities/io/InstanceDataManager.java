package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceTarget;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Manages persistent storage of service instance data for capability provider services.
 * <p>
 * This class handles the lifecycle of service instance data files, including reading existing
 * service entries, writing new entries, and managing the data directory structure. Each service
 * instance maintains its identity across restarts by persisting its ID to a binary data file.
 * <p>
 * The manager creates data files with the naming convention {@code <service-name>.wanaku.dat}
 * in the specified data directory. These files contain a {@link FileHeader} followed by
 * {@link ServiceEntry} data.
 *
 * @see ServiceEntry
 * @see FileHeader
 * @see InstanceDataReader
 * @see InstanceDataWriter
 */
public class InstanceDataManager {

    private final String dataDir;
    private final String name;

    /**
     * Constructs a new instance data manager for a specific service.
     *
     * @param dataDir the directory path where service data files will be stored
     * @param name the service name, used to construct the data file name
     */
    public InstanceDataManager(String dataDir, String name) {
        this.dataDir = dataDir;
        this.name = name;
    }

    /**
     * Checks whether the data file for this service already exists.
     *
     * @return {@code true} if the data file exists, {@code false} otherwise
     */
    public boolean dataFileExists() {
        final File serviceFile = serviceFile();
        return serviceFile.exists();
    }

    /**
     * Creates the data directory structure if it does not already exist.
     * <p>
     * This method creates all necessary parent directories to ensure the
     * data file can be written successfully.
     *
     * @throws IOException if the directory cannot be created due to I/O errors
     */
    public void createDataDirectory() throws IOException {
        Files.createDirectories(serviceFile().getParentFile().toPath());
    }

    /**
     * Reads and returns the service entry from the data file.
     * <p>
     * If the data file does not exist, this method returns {@code null}. If the file
     * exists but cannot be read, a {@code RuntimeException} is thrown wrapping the
     * underlying I/O exception.
     *
     * @return the service entry read from the file, or {@code null} if the file does not exist
     * @throws RuntimeException if an I/O error occurs while reading the file
     */
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

    /**
     * Writes a new service entry to the data file.
     * <p>
     * If the data file already exists, this method returns without writing to avoid
     * overwriting existing service identity data. The appropriate {@link FileHeader}
     * is automatically selected based on the service type of the provided target.
     *
     * @param serviceTarget the service target containing the ID and type information to persist
     * @throws RuntimeException if an I/O error occurs while writing the file
     */
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
        return switch (serviceTarget.getServiceType()) {
            case TOOL_INVOKER -> FileHeader.TOOL_INVOKER;
            case RESOURCE_PROVIDER -> FileHeader.RESOURCE_PROVIDER;
            case MULTI_CAPABILITY -> FileHeader.MULTI_CAPABILITY;
        };
    }
}
