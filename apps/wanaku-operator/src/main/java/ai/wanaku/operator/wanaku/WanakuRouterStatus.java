package ai.wanaku.operator.wanaku;

import java.util.List;
import io.fabric8.kubernetes.api.model.Condition;

public class WanakuRouterStatus {
    private String host;
    private String sseEndpoint;
    private String streamableEndpoint;
    private List<Condition> conditions;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public String getStreamableEndpoint() {
        return streamableEndpoint;
    }

    public void setStreamableEndpoint(String streamableEndpoint) {
        this.streamableEndpoint = streamableEndpoint;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }
}
