package ai.wanaku.operator.util;

import java.util.List;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
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

    private EnvironmentVariableHelper() {}

    /**
     * Computes environment variables for Wanaku-native capability deployments.
     *
     * @param resource the WanakuCapability custom resource
     * @param capabilitiesSpec the specific capability specification
     * @return a mutable list of environment variables for the deployment
     */
    public static List<EnvVar> computeWanakuCapabilitiesEnvVars(
            WanakuCapability resource, WanakuCapabilitySpec.CapabilitiesSpec capabilitiesSpec) {

        final String authServer = resource.getSpec().getAuth().getAuthServer();

        List<EnvVar> envVars = buildCommonEnvVars(
                resource,
                authServer,
                EnvironmentVariables.AUTH_SERVER,
                EnvironmentVariables.WANAKU_SERVICE_REGISTRATION_URI,
                EnvironmentVariables.QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET);

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

        String realm = OperatorUtil.resolveAuthRealm(resource);
        String authServerValue = resource.getSpec().getAuth().getAuthServer() + "/realms/" + realm;

        List<EnvVar> envVars = buildCommonEnvVars(
                resource,
                authServerValue,
                EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT,
                EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_URL,
                EnvironmentVariables.CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET);

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

        List<EnvVar> envVars = new java.util.ArrayList<>();
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
