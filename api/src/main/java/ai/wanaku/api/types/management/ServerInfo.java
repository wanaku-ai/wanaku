package ai.wanaku.api.types.management;

/**
 * Contains server information, such as the version of the server.
 *
 * This class encapsulates essential details about the server, making it
 * easy to access its configuration.
 */
public class ServerInfo {

    /**
     * The version of the server software or platform running on this instance.
     */
    private String version;

    /**
     * Returns the current version of the server.
     *
     * @return The server version as a string.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version of the server to a new value.
     *
     * @param version The updated server version as a string.
     */
    public void setVersion(String version) {
        this.version = version;
    }
}
