package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.services.api.DeploymentInstructions;
import ai.wanaku.core.services.api.PlaceholderDefinition;
import ai.wanaku.core.services.api.ServiceCatalogIndex;
import ai.wanaku.core.services.api.SystemInstruction;

@ApplicationScoped
public class DeploymentInstructionsBean {
    private static final Logger LOG = Logger.getLogger(DeploymentInstructionsBean.class);

    private static final String TYPE_CIC = "camel-integration-capability";
    private static final String TYPE_NATIVE = "native";

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    public DeploymentInstructions generateInstructions(String catalogName, String deploymentModel)
            throws WanakuException {
        LOG.debugf("Generating deployment instructions for catalog '%s' with model '%s'", catalogName, deploymentModel);

        DataStore catalog = serviceCatalogBean.get(catalogName);
        if (catalog == null) {
            throw new WanakuException("Service catalog not found: " + catalogName);
        }

        ServiceCatalogIndex index = serviceCatalogBean.parseIndex(catalog);
        String catalogType = detectCatalogType(index);

        List<SystemInstruction> systems;
        List<PlaceholderDefinition> placeholders;

        switch (deploymentModel) {
            case "local":
                systems = generateLocalInstructions(index, catalogType);
                placeholders = getLocalPlaceholders(catalogType);
                break;
            case "docker":
                systems = generateDockerInstructions(index, catalogType);
                placeholders = getDockerPlaceholders(catalogType);
                break;
            case "kubernetes":
                systems = generateKubernetesInstructions(index, catalogType);
                placeholders = getKubernetesPlaceholders();
                break;
            default:
                throw new WanakuException(
                        "Unsupported deployment model: " + deploymentModel + ". Use: local, docker, kubernetes");
        }

        return new DeploymentInstructions(catalogName, catalogType, deploymentModel, systems, placeholders);
    }

    private String detectCatalogType(ServiceCatalogIndex index) {
        for (String system : index.getServiceNames()) {
            if (index.getRoutesFile(system) != null) {
                return TYPE_CIC;
            }
        }
        return TYPE_NATIVE;
    }

    // --- Local instructions ---

    private List<SystemInstruction> generateLocalInstructions(ServiceCatalogIndex index, String catalogType) {
        List<SystemInstruction> instructions = new ArrayList<>();
        if (TYPE_CIC.equals(catalogType)) {
            for (String system : index.getServiceNames()) {
                instructions.add(new SystemInstruction(system, buildLocalCicCommand(index.getName(), system), "shell"));
            }
        } else {
            for (String system : index.getServiceNames()) {
                instructions.add(new SystemInstruction(system, buildLocalNativeCommand(system), "shell"));
            }
        }
        return instructions;
    }

    private String buildLocalCicCommand(String catalogName, String systemName) {
        return "java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \\\n"
                + "  --registration-url <registration-url> \\\n"
                + "  --registration-announce-address localhost \\\n"
                + "  --grpc-port <grpc-port> \\\n"
                + "  --name " + systemName + " \\\n"
                + "  --service-catalog " + catalogName + " \\\n"
                + "  --service-catalog-system " + systemName + " \\\n"
                + "  --client-id wanaku-service \\\n"
                + "  --fail-fast";
    }

    private String buildLocalNativeCommand(String systemName) {
        return "java -jar capabilities/tools/wanaku-tool-service-" + systemName + "/target/quarkus-app/quarkus-run.jar";
    }

    private List<PlaceholderDefinition> getLocalPlaceholders(String catalogType) {
        List<PlaceholderDefinition> placeholders = new ArrayList<>();
        if (TYPE_CIC.equals(catalogType)) {
            placeholders.add(new PlaceholderDefinition(
                    "registration-url",
                    "Registration URL",
                    "URL of the Wanaku router for service registration",
                    "http://localhost:8080",
                    "url"));
            placeholders.add(
                    new PlaceholderDefinition("grpc-port", "gRPC Port", "Port for the gRPC service endpoint", "9191"));
        }
        return placeholders;
    }

    // --- Docker instructions ---

    private List<SystemInstruction> generateDockerInstructions(ServiceCatalogIndex index, String catalogType) {
        List<SystemInstruction> instructions = new ArrayList<>();
        if (TYPE_CIC.equals(catalogType)) {
            for (String system : index.getServiceNames()) {
                instructions.add(
                        new SystemInstruction(system, buildDockerCicCommand(index.getName(), system), "shell"));
            }
        } else {
            for (String system : index.getServiceNames()) {
                instructions.add(new SystemInstruction(system, buildDockerNativeCommand(system), "shell"));
            }
        }
        return instructions;
    }

    private String buildDockerCicCommand(String catalogName, String systemName) {
        return "docker run -d \\\n"
                + "  -e REGISTRATION_URL=<registration-url> \\\n"
                + "  -e REGISTRATION_ANNOUNCE_ADDRESS=auto \\\n"
                + "  -e GRPC_PORT=9190 \\\n"
                + "  -e SERVICE_NAME=" + systemName + " \\\n"
                + "  -e SERVICE_CATALOG=" + catalogName + " \\\n"
                + "  -e SERVICE_CATALOG_SYSTEM=" + systemName + " \\\n"
                + "  -e TOKEN_ENDPOINT=<token-endpoint> \\\n"
                + "  -e CLIENT_ID=wanaku-service \\\n"
                + "  -e CLIENT_SECRET=<client-secret> \\\n"
                + "  -p 9190:9190 \\\n"
                + "  quay.io/wanaku/camel-integration-capability:latest";
    }

    private String buildDockerNativeCommand(String systemName) {
        return "docker run -d \\\n"
                + "  -e AUTH_SERVER=<auth-server> \\\n"
                + "  -e WANAKU_SERVICE_REGISTRATION_URI=<registration-url> \\\n"
                + "  -e QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=<client-secret> \\\n"
                + "  -p 9000:9000 \\\n"
                + "  quay.io/wanaku/wanaku-tool-service-" + systemName + ":latest";
    }

    private List<PlaceholderDefinition> getDockerPlaceholders(String catalogType) {
        List<PlaceholderDefinition> placeholders = new ArrayList<>();
        if (TYPE_CIC.equals(catalogType)) {
            placeholders.add(new PlaceholderDefinition(
                    "registration-url",
                    "Registration URL",
                    "URL of the Wanaku router (use host.docker.internal for local router)",
                    "http://host.docker.internal:8080",
                    "url"));
            placeholders.add(new PlaceholderDefinition(
                    "token-endpoint", "Token Endpoint", "OAuth2 token endpoint URL for authentication", "", "url"));
            placeholders.add(new PlaceholderDefinition(
                    "client-secret", "Client Secret", "OIDC client secret for service authentication", ""));
        } else {
            placeholders.add(new PlaceholderDefinition(
                    "auth-server", "Auth Server", "Address of the authentication server", "http://", "url"));
            placeholders.add(new PlaceholderDefinition(
                    "registration-url",
                    "Registration URL",
                    "URL of the Wanaku router (use host.docker.internal for local router)",
                    "http://host.docker.internal:8080/",
                    "url"));
            placeholders.add(
                    new PlaceholderDefinition("client-secret", "Client Secret", "OIDC client credentials secret", ""));
        }
        return placeholders;
    }

    // --- Kubernetes instructions ---

    private List<SystemInstruction> generateKubernetesInstructions(ServiceCatalogIndex index, String catalogType) {
        List<SystemInstruction> instructions = new ArrayList<>();
        String yaml = buildKubernetesYaml(index, catalogType);
        instructions.add(new SystemInstruction("all", yaml, "yaml"));
        return instructions;
    }

    private String buildKubernetesYaml(ServiceCatalogIndex index, String catalogType) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: \"wanaku.ai/v1alpha1\"\n");
        sb.append("kind: WanakuCapability\n");
        sb.append("metadata:\n");
        sb.append("  name: wanaku-dev-").append(index.getName()).append("-capability\n");
        sb.append("spec:\n");
        sb.append("  auth:\n");
        sb.append("    authServer: <auth-server-address>\n");
        sb.append("    authProxy: \"auto\"\n");
        sb.append("  secrets:\n");
        sb.append("    oidcCredentialsSecret: <credentials-secret>\n");
        sb.append("  routerRef: <router-name>\n");
        sb.append("  capabilities:\n");

        for (String system : index.getServiceNames()) {
            if (TYPE_CIC.equals(catalogType)) {
                sb.append("    - name: ").append(system).append("\n");
                sb.append("      type: camel-integration-capability\n");
                sb.append("      image: quay.io/wanaku/camel-integration-capability:latest\n");
                sb.append("      serviceCatalog: ").append(index.getName()).append("\n");
                sb.append("      serviceCatalogSystem: ").append(system).append("\n");
            } else {
                sb.append("    - name: ").append(system).append("\n");
                sb.append("      type: quay.io/wanaku/wanaku-tool-service-")
                        .append(system)
                        .append(":latest\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private List<PlaceholderDefinition> getKubernetesPlaceholders() {
        List<PlaceholderDefinition> placeholders = new ArrayList<>();
        placeholders.add(new PlaceholderDefinition(
                "auth-server-address",
                "Auth Server Address",
                "Address of the authentication server (e.g., Keycloak URL)",
                "http://",
                "url"));
        placeholders.add(new PlaceholderDefinition(
                "credentials-secret", "Credentials Secret",
                "OIDC credentials secret (create with 'wanaku credentials' command)", ""));
        placeholders.add(new PlaceholderDefinition(
                "router-name", "Router Name", "Name of the WanakuRouter CR (find with 'oc get wanakurouter')", ""));
        return placeholders;
    }
}
