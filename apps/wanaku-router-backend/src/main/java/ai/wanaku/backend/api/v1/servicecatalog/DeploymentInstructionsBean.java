package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    private static final String AUTH_NONE = "none";

    static final String TEMPLATE_LOCAL_CIC = "local-cic";
    static final String TEMPLATE_LOCAL_NATIVE = "local-native";
    static final String TEMPLATE_DOCKER_CIC = "docker-cic";
    static final String TEMPLATE_DOCKER_NATIVE = "docker-native";
    static final String TEMPLATE_KUBERNETES_HEADER = "kubernetes-header";
    static final String TEMPLATE_KUBERNETES_CAPABILITY_CIC = "kubernetes-capability-cic";
    static final String TEMPLATE_KUBERNETES_CAPABILITY_NATIVE = "kubernetes-capability-native";

    private static final Map<String, String> AUTH_OPTIONS = Map.of(
            TEMPLATE_LOCAL_CIC, "--client-id wanaku-service \\\n  ",
            TEMPLATE_DOCKER_CIC,
                    "-e TOKEN_ENDPOINT=<token-endpoint> \\\n  -e CLIENT_ID=wanaku-service \\\n  -e CLIENT_SECRET=<client-secret> \\\n  ",
            TEMPLATE_DOCKER_NATIVE,
                    "-e AUTH_SERVER=<auth-server> \\\n  -e QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=<client-secret> \\\n  ",
            TEMPLATE_KUBERNETES_HEADER,
                    "auth:\n    authServer: <auth-server-address>\n    authProxy: \"auto\"\n    secrets:\n      oidcCredentialsSecret: <credentials-secret>\n  ");

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    @ConfigProperty(name = "wanaku.http.auth", defaultValue = "keycloak")
    String httpAuth;

    private final Map<String, String> templates = new HashMap<>();

    @PostConstruct
    void loadTemplates() {
        templates.put(TEMPLATE_LOCAL_CIC, loadTemplate("local-cic.tpl"));
        templates.put(TEMPLATE_LOCAL_NATIVE, loadTemplate("local-native.tpl"));
        templates.put(TEMPLATE_DOCKER_CIC, loadTemplate("docker-cic.tpl"));
        templates.put(TEMPLATE_DOCKER_NATIVE, loadTemplate("docker-native.tpl"));
        templates.put(TEMPLATE_KUBERNETES_HEADER, loadTemplate("kubernetes-header.tpl"));
        templates.put(TEMPLATE_KUBERNETES_CAPABILITY_CIC, loadTemplate("kubernetes-capability-cic.tpl"));
        templates.put(TEMPLATE_KUBERNETES_CAPABILITY_NATIVE, loadTemplate("kubernetes-capability-native.tpl"));
    }

    private String loadTemplate(String fileName) {
        String path = TEMPLATES_PATH + fileName;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Template not found on classpath: %s".formatted(path));
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: %s".formatted(path), e);
        }
    }

    private boolean isNoAuth() {
        return AUTH_NONE.equalsIgnoreCase(httpAuth);
    }

    private void addIfAuthEnabled(List<PlaceholderDefinition> placeholders, PlaceholderDefinition... defs) {
        if (!isNoAuth()) {
            placeholders.addAll(Arrays.asList(defs));
        }
    }

    private String renderTemplate(String templateKey, String catalogName, String systemName) {
        String authOptions = isNoAuth() ? "" : AUTH_OPTIONS.getOrDefault(templateKey, "");
        return templates
                .get(templateKey)
                .replace("{{catalogName}}", catalogName)
                .replace("{{systemName}}", systemName)
                .replace("{{authOptions}}", authOptions);
    }

    public DeploymentInstructions generateInstructions(String catalogName, String deploymentModel)
            throws WanakuException {
        LOG.debugf("Generating deployment instructions for catalog '%s' with model '%s'", catalogName, deploymentModel);

        DataStore catalog = serviceCatalogBean.get(catalogName);
        if (catalog == null) {
            throw new WanakuException("Service catalog not found: %s".formatted(catalogName));
        }

        ServiceCatalogIndex index = serviceCatalogBean.parseIndex(catalog);
        String catalogType = detectCatalogType(index);

        List<SystemInstruction> systems;
        List<PlaceholderDefinition> placeholders;

        switch (deploymentModel) {
            case "local":
                systems = generatePerSystemInstructions(
                        index, catalogType, TEMPLATE_LOCAL_CIC, TEMPLATE_LOCAL_NATIVE, "shell");
                placeholders = getLocalPlaceholders(catalogType);
                break;
            case "docker":
                systems = generatePerSystemInstructions(
                        index, catalogType, TEMPLATE_DOCKER_CIC, TEMPLATE_DOCKER_NATIVE, "shell");
                placeholders = getDockerPlaceholders(catalogType);
                break;
            case "kubernetes":
                systems = generateKubernetesInstructions(index, catalogType);
                placeholders = getKubernetesPlaceholders();
                break;
            default:
                throw new WanakuException(
                        "Unsupported deployment model: %s. Use: local, docker, kubernetes".formatted(deploymentModel));
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
        sb.append(renderTemplate(TEMPLATE_KUBERNETES_HEADER, index.getName(), ""));

        String capabilityTemplate = TYPE_CIC.equals(catalogType)
                ? TEMPLATE_KUBERNETES_CAPABILITY_CIC
                : TEMPLATE_KUBERNETES_CAPABILITY_NATIVE;

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
            addIfAuthEnabled(
                    placeholders,
                    new PlaceholderDefinition(
                            "token-endpoint",
                            "Token Endpoint",
                            "OAuth2 token endpoint URL for authentication",
                            "http://host.docker.internal:8080",
                            "url"),
                    new PlaceholderDefinition(
                            "client-secret", "Client Secret", "OIDC client secret for service authentication", ""));
        } else {
            addIfAuthEnabled(
                    placeholders,
                    new PlaceholderDefinition(
                            "auth-server", "Auth Server", "Address of the authentication server", "http://", "url"),
                    new PlaceholderDefinition("client-secret", "Client Secret", "OIDC client credentials secret", ""));
            placeholders.add(new PlaceholderDefinition(
                    "registration-url",
                    "Registration URL",
                    "URL of the Wanaku router (use host.docker.internal for local router)",
                    "http://host.docker.internal:8080/",
                    "url"));
        }
        return placeholders;
    }

    private List<PlaceholderDefinition> getKubernetesPlaceholders() {
        List<PlaceholderDefinition> placeholders = new ArrayList<>();
        addIfAuthEnabled(
                placeholders,
                new PlaceholderDefinition(
                        "auth-server-address",
                        "Auth Server Address",
                        "Address of the authentication server (e.g., Keycloak URL)",
                        "http://",
                        "url"),
                new PlaceholderDefinition(
                        "credentials-secret", "Credentials Secret",
                        "OIDC credentials secret (create with 'wanaku credentials' command)", ""));
        placeholders.add(new PlaceholderDefinition(
                "router-name", "Router Name", "Name of the WanakuRouter CR (find with 'oc get wanakurouter')", ""));
        return placeholders;
    }
}
