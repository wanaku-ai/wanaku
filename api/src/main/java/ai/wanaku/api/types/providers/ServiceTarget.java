package ai.wanaku.api.types.providers;

import ai.wanaku.api.types.WanakuEntity;
import java.util.Objects;

/**
 * Represents a target service endpoint that can be either a resource provider or a tool invoker.
 *
 * This class encapsulates information about a service, including its target and configurations,
 * providing a structured way to represent and manage services in a system.
 */
public class ServiceTarget implements WanakuEntity<String> {
    private String id;
    private String service;
    private String host;
    private int port;
    private ServiceType serviceType;

    /**
     * Default constructor for ServiceTarget.
     */
    public ServiceTarget() {}

    /**
     * Constructs a new instance of {@link ServiceTarget}.
     *
     * @param id             The unique identifier for the service.
     * @param service        The name of the service.
     * @param host           The host address of the service.
     * @param port           The port number of the service.
     * @param serviceType    The type of service, either RESOURCE_PROVIDER or TOOL_INVOKER.
     */
    public ServiceTarget(String id, String service, String host, int port, ServiceType serviceType) {
        this.id = id;
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

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceTarget that = (ServiceTarget) o;
        return port == that.port
                && Objects.equals(id, that.id)
                && Objects.equals(service, that.service)
                && Objects.equals(host, that.host)
                && serviceType == that.serviceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, service, host, port, serviceType);
    }

    @Override
    public String toString() {
        return "ServiceTarget{" + "id='"
                + id + '\'' + ", service='"
                + service + '\'' + ", host='"
                + host + '\'' + ", port="
                + port + ", serviceType="
                + serviceType + '}';
    }

    /**
     * Creates a new instance of {@link ServiceTarget} with the specified parameters with the given service type.
     *
     * @param service The name of the service.
     * @param address The host address of the service.
     * @param port The port number of the service.
     * @param serviceType The type of service (RESOURCE_PROVIDER, TOOL_INVOKER, or MULTI_CAPABILITY).
     * @return A new instance of {@link ServiceTarget}.
     */
    public static ServiceTarget newEmptyTarget(String service, String address, int port, ServiceType serviceType) {
        return new ServiceTarget(null, service, address, port, serviceType);
    }
}
