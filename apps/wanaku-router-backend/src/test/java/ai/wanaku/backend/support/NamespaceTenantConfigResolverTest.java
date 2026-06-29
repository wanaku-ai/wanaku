package ai.wanaku.backend.support;

import java.util.regex.Matcher;
import io.quarkus.oidc.OidcTenantConfig;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamespaceTenantConfigResolverTest {

    private NamespaceTenantConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NamespaceTenantConfigResolver();
        resolver.authServer = "http://keycloak:8080";
        resolver.authRealm = "wanaku";
        resolver.authProxy = "http://localhost:8080";
        resolver.oidcProxyRootPath = "/q/oidc";
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ns-0/mcp/sse", "/ns-5/mcp/message", "/ns-9/something"})
    void resolvesValidNamespacePaths(String path) {
        RoutingContext ctx = mockContext(path);
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();

        assertNotNull(config);

        Matcher matcher = NamespaceTenantConfigResolver.NS_PATH_PATTERN.matcher(path);
        assertTrue(matcher.matches());
        String expectedTenantId = "ns-" + matcher.group(1);
        assertEquals(expectedTenantId, config.tenantId().orElse(null));
    }

    @Test
    void setsCorrectAuthServerUrl() {
        RoutingContext ctx = mockContext("/ns-3/mcp/sse");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();

        assertNotNull(config);
        assertEquals(
                "http://keycloak:8080/realms/wanaku", config.authServerUrl().orElse(null));
    }

    @Test
    void setsCorrectTokenAudience() {
        RoutingContext ctx = mockContext("/ns-0/mcp/sse");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();

        assertNotNull(config);
        assertTrue(config.token().audience().isPresent());
        assertTrue(config.token().audience().get().contains("wanaku-mcp-client"));
    }

    @Test
    void setsCorrectResourceMetadata() {
        RoutingContext ctx = mockContext("/ns-7/mcp/sse");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();

        assertNotNull(config);
        assertTrue(config.resourceMetadata().enabled());
        assertEquals("ns-7/mcp", config.resourceMetadata().resource().orElse(null));
        assertEquals(
                "http://localhost:8080/q/oidc",
                config.resourceMetadata().authorizationServer().orElse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/tools", "/mcp/sse", "/admin/dashboard", "/public/mcp", "/"})
    void returnsNullForNonNamespacePaths(String path) {
        RoutingContext ctx = mockContext(path);
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();
        assertNull(config);
    }

    @Test
    void returnsNullForNamespaceExceedingMax() {
        RoutingContext ctx = mockContext("/ns-10/mcp/sse");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();
        assertNull(config);
    }

    @Test
    void returnsNullForNegativeNamespace() {
        RoutingContext ctx = mockContext("/ns--1/mcp/sse");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();
        assertNull(config);
    }

    @Test
    void patternDoesNotMatchBareNamespacePath() {
        RoutingContext ctx = mockContext("/ns-0");
        OidcTenantConfig config = resolver.resolve(ctx, null).await().indefinitely();
        assertNull(config);
    }

    private RoutingContext mockContext(String path) {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(request);
        when(request.path()).thenReturn(path);
        return ctx;
    }
}
