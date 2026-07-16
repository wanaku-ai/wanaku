package ai.wanaku.operator.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngine;
import ai.wanaku.operator.wanaku.WanakuCamelCodeExecutionEngineSpec;
import ai.wanaku.operator.wanaku.WanakuCapability;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Shared operator utilities for condition management, image pull policy resolution,
 * auth realm resolution, and router URL construction.
 *
 * <p>Resource-specific factory methods have been extracted to:
 * <ul>
 * <li>{@link RouterResourceFactory} for router K8s resources</li>
 * <li>{@link CapabilityResourceFactory} for capability K8s resources</li>
 * <li>{@link CodeExecutionEngineResourceFactory} for code execution engine K8s resources</li>
 * <li>{@link EnvironmentVariableHelper} for environment variable computation</li>
 * </ul>
 */
public final class OperatorUtil {
    private static final Logger LOG = Logger.getLogger(OperatorUtil.class);
    public static final String READY_CONDITION = "Ready";
    public static final String CONDITION_STATUS_TRUE = "True";
    public static final String CONDITION_REASON_READY = "ReconciliationSucceeded";

    public static final String DEFAULT_PULL_POLICY = "IfNotPresent";
    public static final Set<String> VALID_PULL_POLICIES = Set.of("Always", "IfNotPresent", "Never");

    /** Optional comma-separated allowlist of permitted container-image prefixes. */
    public static final String ALLOWED_IMAGE_PREFIXES = "wanaku.operator.allowed-image-prefixes";

    private OperatorUtil() {}

    /**
     * Validates a custom-resource-supplied container image against the optional allowlist
     * {@value #ALLOWED_IMAGE_PREFIXES} (comma-separated registry/repository prefixes). When the
     * allowlist is empty (the default) any image is permitted; when configured, the image must
     * start with one of the prefixes, otherwise reconciliation fails. This prevents a principal
     * able to create/patch a {@code WanakuCapability}/{@code WanakuRouter} from scheduling an
     * arbitrary, untrusted image.
     *
     * @param image the image reference from the custom resource (may be null or blank)
     * @throws IllegalArgumentException if the image is not in the configured allowlist
     */
    public static void validateImageAllowed(String image) {
        String allowlist = ConfigProvider.getConfig()
                .getOptionalValue(ALLOWED_IMAGE_PREFIXES, String.class)
                .orElse("");
        validateImageAllowed(image, allowlist);
    }

    /**
     * Package-private variant taking the allowlist explicitly, so the matching logic can be unit
     * tested without configuration.
     *
     * @param image the image reference from the custom resource (may be null or blank)
     * @param allowlist comma-separated allowed prefixes (empty disables the check)
     * @throws IllegalArgumentException if the image is not in the allowlist
     */
    static void validateImageAllowed(String image, String allowlist) {
        if (allowlist == null || allowlist.isBlank() || image == null || image.isBlank()) {
            return;
        }
        boolean allowed = Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .anyMatch(image::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Container image '%s' is not in the allowed registries (%s)".formatted(image, allowlist));
        }
    }

    /**
     * Creates a Ready condition for a custom resource status.
     *
     * @param generation the observed generation
     * @param previousCondition the previous condition (may be null)
     * @param message the status message
     * @return a new Ready condition
     */
    public static Condition readyCondition(Long generation, Condition previousCondition, String message) {
        final boolean alreadyReady =
                previousCondition != null && CONDITION_STATUS_TRUE.equals(previousCondition.getStatus());
        final String lastTransitionTime = alreadyReady && previousCondition.getLastTransitionTime() != null
                ? previousCondition.getLastTransitionTime()
                : OffsetDateTime.now(ZoneOffset.UTC).toString();

        return new ConditionBuilder()
                .withType(READY_CONDITION)
                .withStatus(CONDITION_STATUS_TRUE)
                .withObservedGeneration(generation)
                .withLastTransitionTime(lastTransitionTime)
                .withReason(CONDITION_REASON_READY)
                .withMessage(message)
                .build();
    }

    /**
     * Finds a condition by type in a list of conditions.
     *
     * @param conditions the list of conditions (may be null or empty)
     * @param type the condition type to find
     * @return the matching condition, or null if not found
     */
    public static Condition findCondition(List<Condition> conditions, String type) {
        if (conditions == null || conditions.isEmpty() || type == null) {
            return null;
        }

        return conditions.stream()
                .filter(condition -> type.equals(condition.getType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves image pull policy with priority:
     * 1. Component-specific policy (router.imagePullPolicy or capability.imagePullPolicy)
     * 2. Global policy (spec.imagePullPolicy)
     * 3. Default (IfNotPresent)
     *
     * @param componentPolicy the component-specific policy (may be null)
     * @param globalPolicy the global policy from spec (may be null)
     * @return the resolved policy, validated against allowed values
     */
    public static String resolveImagePullPolicy(String componentPolicy, String globalPolicy) {
        String resolved =
                componentPolicy != null ? componentPolicy : globalPolicy != null ? globalPolicy : DEFAULT_PULL_POLICY;

        if (!VALID_PULL_POLICIES.contains(resolved)) {
            LOG.warnf("Invalid imagePullPolicy '%s', using default '%s'", resolved, DEFAULT_PULL_POLICY);
            return DEFAULT_PULL_POLICY;
        }

        return resolved;
    }

    /**
     * Constructs the internal base URL for a router.
     *
     * @param routerRef the router reference name
     * @return the base URL in the format "http://internal-{routerRef}:8080"
     */
    public static String getRouterBaseUrl(String routerRef) {
        return "http://internal-" + routerRef + ":8080";
    }

    static String getInternalRegistrationUri(String routerRef) {
        return getRouterBaseUrl(routerRef) + "/";
    }

    public static boolean isRemoteDeploymentMode(WanakuCamelCodeExecutionEngineSpec spec) {
        return "remote".equalsIgnoreCase(normalizeDeploymentMode(spec.getDeploymentMode()));
    }

    public static String normalizeDeploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
        }
        String normalized = deploymentMode.trim().toLowerCase();
        if (WanakuTypes.VALID_DEPLOYMENT_MODES.contains(normalized) || "incluster".equals(normalized)) {
            return WanakuTypes.DEPLOYMENT_MODE_REMOTE.equals(normalized)
                    ? WanakuTypes.DEPLOYMENT_MODE_REMOTE
                    : WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
        }
        return WanakuTypes.DEPLOYMENT_MODE_IN_CLUSTER;
    }

    public static void validateDeploymentMode(String deploymentMode) {
        if (deploymentMode == null || deploymentMode.isBlank()) {
            return;
        }
        String normalized = deploymentMode.trim().toLowerCase();
        if (!WanakuTypes.VALID_DEPLOYMENT_MODES.contains(normalized) && !"incluster".equals(normalized)) {
            throw new WanakuException(
                    "deploymentMode must be one of: " + String.join(", ", WanakuTypes.VALID_DEPLOYMENT_MODES));
        }
    }

    public static void validateCacheStrategy(WanakuCamelCodeExecutionEngineSpec.DependencyCacheSpec cacheSpec) {
        if (cacheSpec == null
                || cacheSpec.getStrategy() == null
                || cacheSpec.getStrategy().isBlank()) {
            return;
        }
        String strategy = cacheSpec.getStrategy().trim().toLowerCase();
        if (!WanakuTypes.VALID_CACHE_STRATEGIES.contains(strategy)) {
            throw new WanakuException("dependencyCache.strategy must be one of: "
                    + String.join(", ", WanakuTypes.VALID_CACHE_STRATEGIES));
        }
    }

    static String resolveAuthRealm(WanakuCapability resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }

    /**
     * Resolves the auth realm for a WanakuRouter resource.
     *
     * @param resource the WanakuRouter custom resource (may be null)
     * @return the configured realm, or the default "wanaku" realm
     */
    static String resolveAuthRealm(WanakuRouter resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }

    static String resolveAuthRealm(WanakuCamelCodeExecutionEngine resource) {
        if (resource == null || resource.getSpec() == null || resource.getSpec().getAuth() == null) {
            return EnvironmentVariables.DEFAULT_AUTH_REALM;
        }
        String realm = resource.getSpec().getAuth().getAuthRealm();
        return (realm == null || realm.isBlank()) ? EnvironmentVariables.DEFAULT_AUTH_REALM : realm;
    }
}
