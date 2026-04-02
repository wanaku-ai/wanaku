package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuCapabilityStatus {
    private List<StatusCondition> conditions;

    public List<StatusCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<StatusCondition> conditions) {
        this.conditions = conditions;
    }
}
