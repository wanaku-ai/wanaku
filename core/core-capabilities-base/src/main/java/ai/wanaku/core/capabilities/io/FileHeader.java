package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceType;

public class FileHeader {
    public static final String FORMAT_NAME = "wanaku";
    public static final int FORMAT_NAME_SIZE = FORMAT_NAME.length();
    public static final int CURRENT_FILE_VERSION = 1;
    public static final int BYTES;

    private final String formatName;
    private final ServiceType serviceType;
    private final int fileVersion;

    public static final FileHeader TOOL_INVOKER = new FileHeader(FORMAT_NAME, ServiceType.TOOL_INVOKER, CURRENT_FILE_VERSION);
    public static final FileHeader RESOURCE_PROVIDER = new FileHeader(FORMAT_NAME, ServiceType.RESOURCE_PROVIDER, CURRENT_FILE_VERSION);

    static {
        // The underlying header size is format + file version + service type;
        BYTES = FORMAT_NAME_SIZE + Integer.BYTES + Integer.BYTES + Integer.BYTES + 2;
    }

    FileHeader(String formatName, ServiceType serviceType, int fileVersion) {
        this.formatName = formatName;
        this.serviceType = serviceType;
        this.fileVersion = fileVersion;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public String getFormatName() {
        return formatName;
    }
}
