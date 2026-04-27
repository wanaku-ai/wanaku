package ai.wanaku.operator.wanaku;

import java.util.List;
import io.fabric8.kubernetes.api.model.Condition;

public class WanakuServiceCatalogStatus {
    private List<Condition> conditions;
    private List<String> deployedCatalogs;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public List<String> getDeployedCatalogs() {
        return deployedCatalogs;
    }

    public void setDeployedCatalogs(List<String> deployedCatalogs) {
        this.deployedCatalogs = deployedCatalogs;
    }
}
