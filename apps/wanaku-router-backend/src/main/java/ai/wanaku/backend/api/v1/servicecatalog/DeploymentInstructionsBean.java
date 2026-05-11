package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String TEMPLATES_PATH = "templates/deployment/";

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    private final Map<String, String> templates = new HashMap<>();

    @PostConstruct
    void loadTemplates() {
        templates.put("local-cic", loadTemplate("local-cic.tpl"));
        templates.put("local-native", loadTemplate("local-native.tpl"));
        templates.put("docker-cic", loadTemplate("docker-cic.tpl"));
        templates.put("docker-native", loadTemplate("docker-native.tpl"));
        templates.put("kubernetes-header", loadTemplate("kubernetes-header.tpl"));
        templates.put("kubernetes-capability-cic", loadTemplate("kubernetes-capability-cic.tpl"));
        templates.put("kubernetes-capability-native", loadTemplate("kubernetes-capability-native.tpl"));
    }

    private String loadTemplate(String fileName) {
        String path = TEMPLATES_PATH + fileName;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Template not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + path, e);
        }
    }

    private String renderTemplate(String templateKey, String catalogName, String systemName) {
        return templates
                .get(templateKey)
                .replace("{{catalogName}}", catalogName)
                .replace("{{systemName}}", systemName);
    }

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
                systems = generatePerSystemInstructions(index, catalogType, "local-cic", "local-native", "shell");
                placeholders = getLocalPlaceholders(catalogType);
                break;
            case "docker":
                systems = generatePerSystemInstructions(index, catalogType, "docker-cic", "docker-native", "shell");
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

    private List<SystemInstruction> generatePerSystemInstructions(
            ServiceCatalogIndex index, String catalogType, String cicTemplate, String nativeTemplate, String format) {
        List<SystemInstruction> instructions = new ArrayList<>();
        String templateKey = TYPE_CIC.equals(catalogType) ? cicTemplate : nativeTemplate;

        for (String system : index.getServiceNames()) {
            String rendered = renderTemplate(templateKey, index.getName(), system);
            instructions.add(new SystemInstruction(system, rendered, format));
        }
        return instructions;
    }

    private List<SystemInstruction> generateKubernetesInstructions(ServiceCatalogIndex index, String catalogType) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderTemplate("kubernetes-header", index.getName(), ""));

        String capabilityTemplate =
                TYPE_CIC.equals(catalogType) ? "kubernetes-capability-cic" : "kubernetes-capability-native";

        for (String system : index.getServiceNames()) {
            sb.append("\n");
            sb.append(renderTemplate(capabilityTemplate, index.getName(), system));
        }

        List<SystemInstruction> instructions = new ArrayList<>();
        instructions.add(new SystemInstruction("all", sb.toString(), "yaml"));
        return instructions;
    }

    // --- Placeholder definitions ---

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
