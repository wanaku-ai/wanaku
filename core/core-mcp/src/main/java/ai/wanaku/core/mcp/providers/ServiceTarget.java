package ai.wanaku.core.mcp.providers;

/**
 * Represents a target service endpoint that can be either a resource provider or a tool invoker.
 */
public class ServiceTarget {
    private final String service;
    private final String host;
    private final int port;
    private final ServiceType serviceType;

    /**
     * Constructs a new instance of {@link ServiceTarget}.
     *
     * @param service The name of the service.
     * @param host The host address of the service.
     * @param port The port number of the service.
     * @param serviceType The type of service, either RESOURCE_PROVIDER or TOOL_INVOKER.
     */
    private ServiceTarget(String service, String host, int port, ServiceType serviceType) {
        this.service = service;
        this.host = host;
        this.port = port;
        this.serviceType = serviceType;
    }

    /**
     * Gets the name of the service.
     *
     * @return The name of the service.
     */
    public String getService() {
        return service;
    }

    /**
     * Gets the host address of the service.
     *
     * @return The host address of the service.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port number of the service.
     *
     * @return The port number of the service.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the type of service, either RESOURCE_PROVIDER or TOOL_INVOKER.
     *
     * @return The type of service.
     */
    public ServiceType getServiceType() {
        return serviceType;
    }

    /**
     * Returns a string representation of the service address in the format "host:port".
     *
     * @return A string representation of the service address.
     */
    public String toAddress() {
        return String.format("%s:%d", host, port);
    }

    /**
     * Creates a new instance of {@link ServiceTarget} with the specified parameters and a service type of RESOURCE_PROVIDER.
     *
     * @param service The name of the service.
     * @param address The host address of the service.
     * @param port The port number of the service.
     * @return A new instance of {@link ServiceTarget}.
     */
    public static ServiceTarget provider(String service, String address, int port) {
        return new ServiceTarget(service, address, port, ServiceType.RESOURCE_PROVIDER);
    }

    /**
     * Creates a new instance of {@link ServiceTarget} with the specified parameters and a service type of TOOL_INVOKER.
     *
     * @param service The name of the service.
     * @param address The host address of the service.
     * @param port The port number of the service.
     * @return A new instance of {@link ServiceTarget}.
     */
    public static ServiceTarget toolInvoker(String service, String address, int port) {
        return new ServiceTarget(service, address, port, ServiceType.TOOL_INVOKER);
    }
}
