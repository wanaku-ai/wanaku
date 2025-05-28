package ai.wanaku.api.types.providers;

import ai.wanaku.api.types.WanakuEntity;
import java.util.Map;
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
    private Map<String, String> configurations;

    public ServiceTarget() {
    }

    /**
     * Constructs a new instance of {@link ServiceTarget}.
     *
     * @param service        The name of the service.
     * @param host           The host address of the service.
     * @param port           The port number of the service.
     * @param serviceType    The type of service, either RESOURCE_PROVIDER or TOOL_INVOKER.
     * @param configurations The available configuration options on the service
     */
    public ServiceTarget(String id, String service, String host, int port, ServiceType serviceType,
            Map<String, String> configurations) {
        this.id = id;
        this.service = service;
        this.host = host;
        this.port = port;
        this.serviceType = serviceType;
        this.configurations = configurations;
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

    public Map<String, String> getConfigurations() {
        return configurations;
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
        return port == that.port && Objects.equals(id, that.id) && Objects.equals(service,
                that.service) && Objects.equals(host,
                that.host) && serviceType == that.serviceType && Objects.equals(configurations, that.configurations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, service, host, port, serviceType, configurations);
    }

    @Override
    public String toString() {
        return "ServiceTarget{" +
                "id='" + id + '\'' +
                ", service='" + service + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", serviceType=" + serviceType +
                ", configurations=" + configurations +
                '}';
    }

    /**
     * Creates a new instance of {@link ServiceTarget} with the specified parameters and a service type of RESOURCE_PROVIDER.
     *
     * @param service The name of the service.
     * @param address The host address of the service.
     * @param port The port number of the service.
     * @param configurations The available configuration options on the service
     * @return A new instance of {@link ServiceTarget}.
     */
    public static ServiceTarget provider(String service, String address, int port, Map<String, String> configurations) {
        return new ServiceTarget(null, service, address, port, ServiceType.RESOURCE_PROVIDER, configurations);
    }

    /**
     * Creates a new instance of {@link ServiceTarget} with the specified parameters and a service type of TOOL_INVOKER.
     *
     * @param service The name of the service.
     * @param address The host address of the service.
     * @param port The port number of the service.
     * @param configurations The available configuration options on the service
     * @return A new instance of {@link ServiceTarget}.
     */
    public static ServiceTarget toolInvoker(String service, String address, int port, Map<String, String> configurations) {
        return new ServiceTarget(null, service, address, port, ServiceType.TOOL_INVOKER, configurations);
    }


}
