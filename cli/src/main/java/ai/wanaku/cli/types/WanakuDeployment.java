package ai.wanaku.cli.types;

import java.util.List;

/**
 * Represents a complete Wanaku deployment configuration.
 * <p>
 * This class encapsulates all components of a Wanaku deployment including
 * environment variables, infrastructure services, application services, and server configuration.
 * It is typically used for parsing deployment configuration files and orchestrating
 * the deployment process.
 * </p>
 */
public class WanakuDeployment {

    private Environment environment;
    private List<Service> infrastructure;
    private List<Service> services;
    private List<Service> server;

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public List<Service> getInfrastructure() {
        return infrastructure;
    }

    public void setInfrastructure(List<Service> infrastructure) {
        this.infrastructure = infrastructure;
    }

    public List<Service> getServices() {
        return services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public List<Service> getServer() {
        return server;
    }

    public void setServer(List<Service> server) {
        this.server = server;
    }
}
