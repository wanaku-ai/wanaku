package ai.wanaku.api.types.management;

import java.util.Map;

/**
 * Represents a downstream service of some type.
 *
 * This class encapsulates information about a service, including its target and configurations,
 * providing a structured way to represent and manage services in a system.
 */
public class Service {

    /**
     * The target or destination of the service, such as an endpoint or URL.
     */
    private String target;

    /**
     * The unique name of the Service
     */
    private String name;

    /**
     * Tpye of the service, can be a resource or a tool
     */
    private String serviceType;

    /**
     * The configuration settings for the service, which can include various parameters and values.
     */
    private Configurations configurations;

    /**
     * Returns the target of the service, which is used to identify or address the service.
     *
     * @return The target as a string.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the target of the service to a new value.
     *
     * @param target The updated target as a string.
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * Returns the configuration settings for the service, which can include various parameters and values.
     *
     * @return The configuration settings as a map of {@link Configuration} objects.
     */
    public Configurations getConfigurations() {
        return configurations;
    }

    /**
     * Sets the configuration settings for the service to new values.
     *
     * @param configurations The updated configuration settings as a map of {@link Configuration} objects.
     */
    public void setConfigurations(Configurations configurations) {
        this.configurations = configurations;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    /**
     * Creates a new instance of the service with the given target and configurations.
     *
     * This factory method provides a convenient way to create a service object with pre-configured settings.
     *
     * @param target         The target or destination of the service, such as an endpoint or URL.
     * @param toolsConfigurations A map of configuration settings for the service, where each key is a parameter name and each value is a description.
     * @return A new instance of the {@link Service} class with the specified target and configurations.
     */
    public static Service newService(String target, Map<String, String> toolsConfigurations) {
        Service srv = new Service();
        srv.setTarget(target);
        Configurations configurations = new Configurations();

        for (Map.Entry<String, String> entry : toolsConfigurations.entrySet()) {
            Configuration cfg = new Configuration();
            cfg.setDescription(entry.getValue());
            configurations.getConfigurations().put(entry.getKey(), cfg);
        }
        srv.setConfigurations(configurations);
        return srv;
    }
}
