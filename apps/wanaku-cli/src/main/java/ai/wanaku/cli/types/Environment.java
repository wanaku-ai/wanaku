package ai.wanaku.cli.types;

import java.util.List;

/**
 * Represents environment configuration for a deployment.
 * <p>
 * This class encapsulates environment variables that are applied
 * to services and containers during deployment.
 * </p>
 */
public class Environment {

    private List<String> variables;

    public List<String> getVariables() {
        return variables;
    }

    public void setVariables(List<String> variables) {
        this.variables = variables;
    }
}
