package ai.wanaku.operator.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import ai.wanaku.core.util.StringHelper;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuCapabilitySpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Helper for computing environment variables for capability deployments.
 *
 * <p>Both Wanaku-native and Camel Integration capabilities share a common structure:
 * resolve auth server, OIDC secret, and registration URI, then build an {@link EnvVar} list.
 * This class extracts the common logic and parameterizes the env var names.</p>
 */
public final class EnvironmentVariableHelper {

    static final String ANNOTATION_ENV_PREFIX = "env.wanaku.ai/";

    /**
     * Pattern for valid POSIX/Kubernetes environment variable names:
     * must start with a letter or underscore, followed by letters, digits, or underscores.
     */
    static final Pattern ENV_VAR_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private EnvironmentVariableHelper() {}

    /**
     * Extracts environment variables from CR metadata annotations.
     *
     * <p>Any annotation whose key starts with {@code env.wanaku.ai/} is promoted to an
     * environment variable. The prefix is stripped to form the variable name; the annotation
     * value becomes the variable value. Annotation-derived variables are intended to be merged
     * last so they override any same-name variables set by the reconciler.</p>
     *
     * @param annotations the CR metadata annotations (may be null or empty)
     * @return a mutable list of {@link EnvVar} objects; never null
     */
    public static List<EnvVar> extractAnnotationEnvVars(Map<String, String> annotations) {
        List<EnvVar> result = new ArrayList<>();
        if (annotations == null || annotations.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : annotations.entrySet()) {
            if (entry.getKey().startsWith(ANNOTATION_ENV_PREFIX)) {
                String name = entry.getKey().substring(ANNOTATION_ENV_PREFIX.length());
                if (StringHelper.isBlank(name)
                        || !ENV_VAR_NAME_PATTERN.matcher(name).matches()) {
                    continue;
                }
                result.add(new EnvVarBuilder()
                        .withName(name)
                        .withValue(entry.getValue())
                        .build());
            }
        }
        return result;
    }

    /**
     * Merges annotation-derived env vars into an existing list, with annotation vars taking
     * precedence. Any existing var whose name matches an annotation var is removed before
     * the annotation vars are appended.
     *
     * @param envVars the mutable base list (modified in place)
     * @param annotations the CR metadata annotations (may be null or empty)
     */
    public static void applyAnnotationEnvVars(List<EnvVar> envVars, Map<String, String> annotations) {
        List<EnvVar> fromAnnotations = extractAnnotationEnvVars(annotations);
        if (fromAnnotations.isEmpty()) {
            return;
        }
        fromAnnotations.forEach(a -> envVars.removeIf(e -> e.getName().equals(a.getName())));
        envVars.addAll(fromAnnotations);
    }

    /**
     * Computes environment variables for Wanaku-native capability deployments.
     *
     * @param resource the WanakuCapability custom resource
     * @param capabilitiesSpec the specific capability specification
     * @return a mutable list of environment variables for the deployment
     */
    public static List<EnvVar> computeWanakuCapabilitiesEnvVars(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {

        List<EnvVar> envVars;
        WanakuTypes.AuthSpec authSpec = resource.getSpec().getAuth();
        if (authSpec != null) {
            envVars = buildCommonEnvVars(
                    resource,
                    authSpec.getAuthServer(),
                    EnvironmentVariables.AUTH_SERVER,
                    EnvironmentVariables.WANAKU_SERVICE_REGISTRATION_URI,
                    EnvironmentVariables.QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET);
        } else {
            envVars = new ArrayList<>();
        }

        addCustomVars(capabilitiesSpec.getEnv(), envVars);
        return envVars;
    }

    /**
     * Computes environment variables for Camel Integration Capability deployments.
     *
     * @param resource the WanakuCapability custom resource
     * @param capabilitiesSpec the specific capability specification
     * @return a mutable list of environment variables for the deployment
     */
    public static List<EnvVar> computeCamelIntegrationCapabilitiesEnvVars(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {

        List<EnvVar> envVars;
        WanakuTypes.AuthSpec authSpec = resource.getSpec().getAuth();
        if (authSpec != null) {
            String realm = OperatorUtil.resolveAuthRealm(resource);
            String authServerValue = authSpec.getAuthServer() + "/realms/" + realm;

            envVars = buildCommonEnvVars(
                    resource,
                    authServerValue,
                    EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT,
                    EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_URL,
                    EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET);
        } else {
            envVars = new ArrayList<>();
        }

        envVars.add(new EnvVarBuilder()
                .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_NAME)
                .withValue(capabilitiesSpec.getName())
                .build());

        if (capabilitiesSpec.getServiceCatalog() != null
                && !capabilitiesSpec.getServiceCatalog().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG)
                    .withValue(capabilitiesSpec.getServiceCatalog())
                    .build());
        }
        if (capabilitiesSpec.getServiceCatalogSystem() != null
                && !capabilitiesSpec.getServiceCatalogSystem().isBlank()) {
            envVars.add(new EnvVarBuilder()
                    .withName(EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_SERVICE_CATALOG_SYSTEM)
                    .withValue(capabilitiesSpec.getServiceCatalogSystem())
                    .build());
        }

        addCustomVars(capabilitiesSpec.getEnv(), envVars);
        return envVars;
    }

    /**
     * Builds the common set of environment variables shared by all capability types.
     *
     * <p>This method extracts the duplicated structure: auth server, registration URI,
     * and OIDC secret resolution. Only the env var names and auth server value differ
     * between capability types.</p>
     *
     * @param resource the WanakuCapability custom resource
     * @param authServerValue the resolved auth server value (raw URL or with realm path)
     * @param authServerEnvName the env var name for the auth server
     * @param registrationUriEnvName the env var name for the registration URI
     * @param oidcSecretEnvName the env var name for the OIDC secret
     * @return a mutable list of the common environment variables
     */
    private static List<EnvVar> buildCommonEnvVars(
            WanakuCapability resource,
            String authServerValue,
            String authServerEnvName,
            String registrationUriEnvName,
            String oidcSecretEnvName) {

        final String oidcSecret = resource.getSpec().getSecrets().getOidcCredentialsSecret();
        String registrationUri = getInternalRegistrationUri(resource);

        EnvVar authServerEnv = new EnvVarBuilder()
                .withName(authServerEnvName)
                .withValue(authServerValue)
                .build();
        EnvVar registrationUriEnv = new EnvVarBuilder()
                .withName(registrationUriEnvName)
                .withValue(registrationUri)
                .build();
        EnvVar oidcSecretEnv = new EnvVarBuilder()
                .withName(oidcSecretEnvName)
                .withValue(oidcSecret)
                .build();

        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(authServerEnv);
        envVars.add(registrationUriEnv);
        envVars.add(oidcSecretEnv);

        return envVars;
    }

    /**
     * Adds custom user-defined environment variables to the list.
     *
     * @param customEnv the custom environment variables from the spec (may be null)
     * @param envVars the target list to add variables to
     */
    static void addCustomVars(List<WanakuTypes.EnvVar> customEnv, List<EnvVar> envVars) {
        if (customEnv != null && !customEnv.isEmpty()) {
            for (WanakuTypes.EnvVar env : customEnv) {
                EnvVar customEnvVar = new EnvVarBuilder()
                        .withName(env.getName())
                        .withValue(env.getValue())
                        .build();
                envVars.add(customEnvVar);
            }
        }
    }

    /**
     * Constructs the internal registration URI for a capability based on its router reference.
     *
     * @param resource the WanakuCapability custom resource
     * @return the internal registration URI
     */
    private static String getInternalRegistrationUri(WanakuCapability resource) {
        return OperatorUtil.getRouterBaseUrl(resource.getSpec().getRouterRef()) + "/";
    }
}
