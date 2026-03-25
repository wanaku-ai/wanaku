package ai.wanaku.cli.types;

/**
 * Represents a service configuration in a Wanaku deployment.
 * <p>
 * This class defines a service with its name, container image, and
 * environment configuration. Services can represent infrastructure components,
 * application services, or server instances within a deployment.
 * </p>
 */
public class Service {

    private String name;
    private String image;

    private Environment environment;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
