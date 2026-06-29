package ai.wanaku.backend.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class NamespaceTenantConfigResolver implements TenantConfigResolver {

    static final Pattern NS_PATH_PATTERN = Pattern.compile("^/ns-(\\d+)/.*");
    static final int MAX_NAMESPACES = 10;

    @ConfigProperty(name = "auth.server")
    String authServer;

    @ConfigProperty(name = "auth.realm")
    String authRealm;

    @ConfigProperty(name = "auth.proxy")
    String authProxy;

    @ConfigProperty(name = "quarkus.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String oidcProxyRootPath;

    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext) {
        String path = context.request().path();
        Matcher matcher = NS_PATH_PATTERN.matcher(path);

        if (!matcher.matches()) {
            return Uni.createFrom().nullItem();
        }

        int nsIndex;
        try {
            nsIndex = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Uni.createFrom().nullItem();
        }

        if (nsIndex < 0 || nsIndex >= MAX_NAMESPACES) {
            return Uni.createFrom().nullItem();
        }

        String tenantId = "ns-" + nsIndex;
        String authServerUrl = authServer + "/realms/" + authRealm;
        String authorizationServer = authProxy + oidcProxyRootPath;

        OidcTenantConfig config = OidcTenantConfig.authServerUrl(authServerUrl)
                .tenantId(tenantId)
                .token()
                .audience("wanaku-mcp-client")
                .end()
                .resourceMetadata()
                .enabled()
                .resource(tenantId + "/mcp")
                .authorizationServer(authorizationServer)
                .forceHttpsScheme(false)
                .end()
                .build();

        return Uni.createFrom().item(config);
    }
}
