package ai.wanaku.cli.types;

/**
 * Represents a container configuration.
 * <p>
 * This class defines a container with its name and image reference,
 * used for container-based deployments in the Wanaku platform.
 * </p>
 */
public class Container {

    private String name;
    private String image;

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
}
