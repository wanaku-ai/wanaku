package ai.wanaku.core.services.api;

import java.util.List;

/**
 * Deployment instructions for a service catalog, generated for a specific deployment model.
 *
 * @param catalogName the service catalog name
 * @param catalogType the detected capability type: "camel-integration-capability" or "native"
 * @param deploymentModel the deployment model: "local", "docker", or "kubernetes"
 * @param systems per-system deployment instructions
 * @param placeholders placeholder definitions for user-specific values in the instructions
 */
public record DeploymentInstructions(
        String catalogName,
        String catalogType,
        String deploymentModel,
        List<SystemInstruction> systems,
        List<PlaceholderDefinition> placeholders) {}
