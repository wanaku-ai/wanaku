package ai.wanaku.backend.support;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.oidc.OidcConfigurationMetadata;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.common.runtime.OidcCommonUtils;
import io.quarkus.oidc.common.runtime.OidcConstants;
import io.quarkus.oidc.runtime.OidcUtils;
import io.quarkus.oidc.runtime.TenantConfigBean;
import io.quarkus.oidc.runtime.TenantConfigContext;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;

@ApplicationScoped
public class WanakuOidcProxy {
    private static final Logger LOG = Logger.getLogger(WanakuOidcProxy.class);

    private static final String RESPONSE_TYPES_SUPPORTED = "response_types_supported";
    private static final String SUBJECT_TYPES_SUPPORTED = "subject_types_supported";
    private static final String SCOPES_SUPPORTED = "scopes_supported";
    private static final String ID_TOKEN_SIGNING_ALGORITHMS_SUPPORTED = "id_token_signing_alg_values_supported";
    private static final String CODE_CHALLENGE_METHODS_SUPPORTED = "code_challenge_methods_supported";

    @ConfigProperty(name = "wanaku.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String rootPath;

    @ConfigProperty(name = "wanaku.oidc-proxy.tenant-id", defaultValue = "")
    String tenantId;

    @ConfigProperty(name = "wanaku.oidc-proxy.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "wanaku.http.auth", defaultValue = "keycloak")
    String authMode;

    @Inject
    Instance<TenantConfigBean> tenantConfigBeanInstance;

    private volatile WebClient client;
    private volatile OidcConfigurationMetadata oidcMetadata;
    private volatile OidcTenantConfig oidcTenantConfig;
    private volatile boolean initialized;

    void setupRoutes(@Observes Router router) {
        if (!enabled || "none".equalsIgnoreCase(authMode)) {
            return;
        }

        tryInitialize();

        String metadataPath = rootPath + "/.well-known/openid-configuration";
        router.get(metadataPath).handler(this::wellKnownConfig);
        router.get(rootPath + "/authorize").handler(this::authorize);
        router.post(rootPath + "/token").handler(this::token);
        router.get(rootPath + "/jwks").handler(this::jwks);
        router.get(rootPath + "/userinfo").handler(this::userinfo);
        router.get(rootPath + "/logout").handler(this::endSession);
        router.post(rootPath + "/register").handler(this::clientRegistration);

        if (initialized) {
            LOG.infof("OIDC proxy routes registered at %s (provider available)", rootPath);
        } else {
            LOG.warnf(
                    "OIDC proxy routes registered at %s in degraded mode — provider unavailable, endpoints will return 503",
                    rootPath);
        }
    }

    private void tryInitialize() {
        if (initialized) {
            return;
        }

        if (!tenantConfigBeanInstance.isResolvable()) {
            LOG.warn("TenantConfigBean not available — OIDC is likely disabled");
            return;
        }

        TenantConfigBean tenantConfigBean = tenantConfigBeanInstance.get();
        TenantConfigContext ctx = (tenantId == null || tenantId.isEmpty())
                ? tenantConfigBean.getDefaultTenant()
                : tenantConfigBean.getStaticTenantsConfig().get(tenantId);

        if (ctx == null) {
            LOG.warn("OIDC proxy tenant config not found — operating in degraded mode");
            return;
        }

        if (ctx.getOidcProviderClient() == null) {
            LOG.warn("OIDC provider client unavailable — proxy operating in degraded mode");
            return;
        }

        this.oidcTenantConfig = ctx.getOidcTenantConfig();
        this.oidcMetadata = ctx.getOidcMetadata();
        this.client = ctx.getOidcProviderClient().getWebClient();
        this.initialized = true;
    }

    private void wellKnownConfig(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        JsonObject json = new JsonObject();
        json.put(OidcConfigurationMetadata.AUTHORIZATION_ENDPOINT, buildUri(context, rootPath + "/authorize"));
        json.put(OidcConfigurationMetadata.TOKEN_ENDPOINT, buildUri(context, rootPath + "/token"));

        if (oidcMetadata.getJsonWebKeySetUri() != null) {
            json.put(OidcConfigurationMetadata.JWKS_ENDPOINT, buildUri(context, rootPath + "/jwks"));
        }
        if (oidcMetadata.getEndSessionUri() != null) {
            json.put(OidcConfigurationMetadata.END_SESSION_ENDPOINT, buildUri(context, rootPath + "/logout"));
        }
        if (oidcMetadata.getUserInfoUri() != null) {
            json.put(OidcConfigurationMetadata.USERINFO_ENDPOINT, buildUri(context, rootPath + "/userinfo"));
        }
        if (oidcMetadata.getRegistrationUri() != null) {
            json.put("registration_endpoint", buildUri(context, rootPath + "/register"));
        }
        if (oidcMetadata.getIssuer() != null) {
            json.put(OidcConfigurationMetadata.ISSUER, oidcMetadata.getIssuer());
        }

        addListProperty(json, RESPONSE_TYPES_SUPPORTED);
        addListProperty(json, SUBJECT_TYPES_SUPPORTED);
        addListProperty(json, SCOPES_SUPPORTED);
        addListProperty(json, CODE_CHALLENGE_METHODS_SUPPORTED);
        addListProperty(json, ID_TOKEN_SIGNING_ALGORITHMS_SUPPORTED);

        endJsonResponse(context, json.toString());
    }

    private void authorize(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        MultiMap queryParams = context.queryParams();
        StringBuilder location = new StringBuilder(oidcMetadata.getAuthorizationUri());
        boolean first = true;

        first = appendParam(location, first, OidcConstants.CODE_FLOW_RESPONSE_TYPE, OidcConstants.CODE_FLOW_CODE);

        String clientId = queryParams.get(OidcConstants.CLIENT_ID);
        if (clientId == null) {
            badRequest(context);
            return;
        }
        first = appendParam(location, first, OidcConstants.CLIENT_ID, OidcCommonUtils.urlEncode(clientId));

        String scope = queryParams.get(OidcConstants.TOKEN_SCOPE);
        if (scope != null) {
            first = appendParam(location, first, OidcConstants.TOKEN_SCOPE, OidcCommonUtils.urlEncode(scope));
        }

        String redirectUri = queryParams.get(OidcConstants.CODE_FLOW_REDIRECT_URI);
        if (redirectUri == null) {
            badRequest(context);
            return;
        }
        first = appendParam(
                location, first, OidcConstants.CODE_FLOW_REDIRECT_URI, OidcCommonUtils.urlEncode(redirectUri));

        String state = queryParams.get(OidcConstants.CODE_FLOW_STATE);
        if (state != null) {
            first = appendParam(location, first, OidcConstants.CODE_FLOW_STATE, state);
        }

        String codeChallenge = queryParams.get("code_challenge");
        if (codeChallenge != null) {
            first = appendParam(location, first, "code_challenge", codeChallenge);
            String codeChallengeMethod = queryParams.get("code_challenge_method");
            if (codeChallengeMethod != null) {
                appendParam(location, first, "code_challenge_method", codeChallengeMethod);
            }
        }

        context.response().setStatusCode(HttpResponseStatus.FOUND.code());
        context.response().putHeader(HttpHeaders.LOCATION, location.toString());
        context.response().end();
    }

    private void token(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        OidcUtils.getFormUrlEncodedData(context)
                .onItem()
                .transformToUni(requestParams -> {
                    var request = client.postAbs(oidcMetadata.getTokenUri());
                    request.putHeader(
                            String.valueOf(HttpHeaders.CONTENT_TYPE),
                            String.valueOf(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED));
                    request.putHeader(String.valueOf(HttpHeaders.ACCEPT), "application/json");

                    Buffer buffer = Buffer.buffer();

                    for (String key : requestParams.names()) {
                        for (String value : requestParams.getAll(key)) {
                            encodeForm(buffer, key, value);
                        }
                    }

                    String authHeader = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
                    if (authHeader != null) {
                        request.putHeader(String.valueOf(HttpHeaders.AUTHORIZATION), authHeader);
                    }

                    return request.sendBuffer(buffer).onItem().transformToUni(response -> {
                        endJsonResponse(context, response.bodyAsString());
                        return io.smallrye.mutiny.Uni.createFrom().voidItem();
                    });
                })
                .subscribe()
                .with(v -> {}, err -> {
                    LOG.errorf(err, "Token exchange failed");
                    context.response().setStatusCode(502).end();
                });
    }

    private void jwks(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        client.getAbs(oidcMetadata.getJsonWebKeySetUri())
                .putHeader(String.valueOf(HttpHeaders.ACCEPT), "application/json")
                .send()
                .subscribe()
                .with(response -> endJsonResponse(context, response.bodyAsString()));
    }

    private void userinfo(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        String authHeader = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
        if (authHeader == null) {
            badRequest(context);
            return;
        }

        client.getAbs(oidcMetadata.getUserInfoUri())
                .putHeader(String.valueOf(HttpHeaderNames.AUTHORIZATION), authHeader)
                .putHeader(String.valueOf(HttpHeaders.ACCEPT), "application/json")
                .send()
                .subscribe()
                .with(response -> endJsonResponse(context, response.bodyAsString()));
    }

    private void endSession(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        if (oidcMetadata.getEndSessionUri() == null) {
            context.response().setStatusCode(404).end();
            return;
        }

        StringBuilder location = new StringBuilder(oidcMetadata.getEndSessionUri());
        boolean first = true;
        for (var entry : context.queryParams()) {
            first = appendParam(location, first, entry.getKey(), entry.getValue());
        }

        context.response().setStatusCode(HttpResponseStatus.FOUND.code());
        context.response().putHeader(HttpHeaders.LOCATION, location.toString());
        context.response().end();
    }

    private void clientRegistration(RoutingContext context) {
        if (!ensureAvailable(context)) {
            return;
        }

        if (oidcMetadata.getRegistrationUri() == null) {
            context.response().setStatusCode(404).end();
            return;
        }

        context.request().bodyHandler(body -> {
            var request = client.postAbs(oidcMetadata.getRegistrationUri());
            String authHeader = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
            if (authHeader != null) {
                request.putHeader(String.valueOf(HttpHeaderNames.AUTHORIZATION), authHeader);
            }
            request.putHeader(String.valueOf(HttpHeaders.CONTENT_TYPE), "application/json");
            request.putHeader(String.valueOf(HttpHeaders.ACCEPT), "application/json");

            request.sendJson(body.toJsonObject())
                    .subscribe()
                    .with(response -> endJsonResponse(context, response.bodyAsString()));
        });
        context.request().resume();
    }

    private boolean ensureAvailable(RoutingContext context) {
        if (!initialized) {
            tryInitialize();
        }
        if (!initialized) {
            context.response().setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
            context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            context.end("{\"error\":\"oidc_provider_unavailable\","
                    + "\"error_description\":\"OIDC proxy is not available because the identity provider"
                    + " was unreachable at startup\"}");
            return false;
        }
        return true;
    }

    private void addListProperty(JsonObject json, String propertyName) {
        List<String> values = oidcMetadata.getStringList(propertyName);
        if (values != null) {
            json.put(propertyName, values);
        }
    }

    private String buildUri(RoutingContext context, String path) {
        String authority = URI.create(context.request().absoluteURI()).getAuthority();
        String scheme = context.request().scheme();
        return scheme + "://" + authority + path;
    }

    private static boolean appendParam(StringBuilder sb, boolean first, String name, String value) {
        sb.append(first ? '?' : '&').append(name).append('=').append(value);
        return false;
    }

    private static void encodeForm(Buffer buffer, String name, String value) {
        if (buffer.length() != 0) {
            buffer.appendByte((byte) '&');
        }
        buffer.appendString(name);
        buffer.appendByte((byte) '=');
        buffer.appendString(OidcCommonUtils.urlEncode(value));
    }

    private static void endJsonResponse(RoutingContext context, String jsonResponse) {
        context.response().setStatusCode(HttpResponseStatus.OK.code());
        context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        context.end(jsonResponse);
    }

    private static void badRequest(RoutingContext context) {
        context.response().setStatusCode(400).end();
    }
}
