package ai.wanaku.core.capabilities.io;

/**
 * Represents a service entry in the instance data file.
 * <p>
 * This class encapsulates the service identity information that is persisted to disk
 * to maintain service continuity across restarts. Each entry contains a unique service
 * identifier that is used for registration and discovery purposes.
 * <p>
 * The binary representation of this entry is a fixed-size record containing:
 * <ul>
 *   <li>A 36-byte service ID (typically a UUID)</li>
 *   <li>4 additional bytes for alignment</li>
 * </ul>
 *
 * @see InstanceDataManager
 * @see InstanceDataReader
 * @see InstanceDataWriter
 */
public class ServiceEntry {
    /**
     * The fixed length of the service ID in bytes (36 bytes for UUID format).
     */
    public static final int ID_LENGTH = 36;

    /**
     * The total size of a service entry record in bytes, including alignment padding.
     */
    public static final int BYTES = ID_LENGTH + 4;

    private String id;

    /**
     * Default constructor for internal use.
     */
    ServiceEntry() {}

    /**
     * Constructs a service entry with the specified service ID.
     *
     * @param id the unique service identifier
     */
    ServiceEntry(String id) {
        this.id = id;
    }

    /**
     * Gets the unique service identifier.
     *
     * @return the service ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique service identifier.
     *
     * @param id the service ID to set
     */
    public void setId(String id) {
        this.id = id;
    }
}
