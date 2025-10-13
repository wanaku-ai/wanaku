package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceType;

/**
 * Header structure for service instance data files.
 * <p>
 * This class defines the binary file header format used for persisting service
 * instance data. The header contains format identification, versioning information,
 * and service type classification.
 * <p>
 * Pre-defined headers are available for:
 * <ul>
 *   <li>Tool invoker services</li>
 *   <li>Resource provider services</li>
 *   <li>Multi-capability services</li>
 * </ul>
 *
 * @see ServiceType
 */
public class FileHeader {
    /**
     * Format identifier for Wanaku service data files.
     */
    public static final String FORMAT_NAME = "wanaku";

    /**
     * Size in bytes of the format name string.
     */
    public static final int FORMAT_NAME_SIZE = FORMAT_NAME.length();

    /**
     * Current version of the file format specification.
     */
    public static final int CURRENT_FILE_VERSION = 1;

    /**
     * Total size in bytes of the complete file header.
     */
    public static final int BYTES;

    private final String formatName;
    private final ServiceType serviceType;
    private final int fileVersion;

    /**
     * Pre-defined header for tool invoker service data files.
     */
    public static final FileHeader TOOL_INVOKER =
            new FileHeader(FORMAT_NAME, ServiceType.TOOL_INVOKER, CURRENT_FILE_VERSION);

    /**
     * Pre-defined header for resource provider service data files.
     */
    public static final FileHeader RESOURCE_PROVIDER =
            new FileHeader(FORMAT_NAME, ServiceType.RESOURCE_PROVIDER, CURRENT_FILE_VERSION);

    /**
     * Pre-defined header for multi-capability service data files.
     */
    public static final FileHeader MULTI_CAPABILITY =
            new FileHeader(FORMAT_NAME, ServiceType.MULTI_CAPABILITY, CURRENT_FILE_VERSION);

    static {
        // The underlying header size is format + file version + service type;
        BYTES = FORMAT_NAME_SIZE + Integer.BYTES + Integer.BYTES + Integer.BYTES + 2;
    }

    FileHeader(String formatName, ServiceType serviceType, int fileVersion) {
        this.formatName = formatName;
        this.serviceType = serviceType;
        this.fileVersion = fileVersion;
    }

    /**
     * Gets the service type specified in this header.
     *
     * @return the service type
     */
    public ServiceType getServiceType() {
        return serviceType;
    }

    /**
     * Gets the file format version number.
     *
     * @return the file version
     */
    public int getFileVersion() {
        return fileVersion;
    }

    /**
     * Gets the format name identifier.
     *
     * @return the format name
     */
    public String getFormatName() {
        return formatName;
    }
}
