package ai.wanaku.operator.wanaku;

import java.util.List;
import io.fabric8.kubernetes.api.model.Condition;

public class WanakuCodeExecutionEngineStatus {
    private String deploymentState;
    private String serviceUrl;
    private List<String> activeRoutes;
    private List<HealthCheck> healthChecks;
    private List<Condition> conditions;

    public String getDeploymentState() {
        return deploymentState;
    }

    public void setDeploymentState(String deploymentState) {
        this.deploymentState = deploymentState;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public List<String> getActiveRoutes() {
        return activeRoutes;
    }

    public void setActiveRoutes(List<String> activeRoutes) {
        this.activeRoutes = activeRoutes;
    }

    public List<HealthCheck> getHealthChecks() {
        return healthChecks;
    }

    public void setHealthChecks(List<HealthCheck> healthChecks) {
        this.healthChecks = healthChecks;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public static class HealthCheck {
        private String name;
        private String status;
        private String message;
        private String timestamp;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
