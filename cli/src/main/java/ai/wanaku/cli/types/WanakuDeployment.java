package ai.wanaku.cli.types;

import java.util.List;

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
