package ai.wanaku.core.persistence.infinispan.remote.prompt;

public class PromptConfiguration {
    private String id;
    private String name;
    private String description;
    private String role;
    private String fullText;

    public PromptConfiguration() {}

    public PromptConfiguration(String id, String name, String description, String role, String fullText) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.role = role;
        this.fullText = fullText;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }
}
