package ai.wanaku.operator.wanaku;

import java.util.List;
import io.fabric8.kubernetes.api.model.Condition;

public class WanakuCapabilityStatus {
    private List<Condition> conditions;

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }
}
