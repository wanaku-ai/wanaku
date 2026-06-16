package ai.wanaku.operator.wanaku;

import java.util.List;
import io.fabric8.kubernetes.api.model.Condition;

public class WanakuCamelRouteStatus {
    private List<Condition> conditions;
    private String deployedCatalogName;
    private List<String> registeredTools;
    private List<String> registeredResources;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public String getDeployedCatalogName() {
        return deployedCatalogName;
    }

    public void setDeployedCatalogName(String deployedCatalogName) {
        this.deployedCatalogName = deployedCatalogName;
    }

    public List<String> getRegisteredTools() {
        return registeredTools;
    }

    public void setRegisteredTools(List<String> registeredTools) {
        this.registeredTools = registeredTools;
    }

    public List<String> getRegisteredResources() {
        return registeredResources;
    }

    public void setRegisteredResources(List<String> registeredResources) {
        this.registeredResources = registeredResources;
    }
}
