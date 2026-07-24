package ai.wanaku.operator.util;

import java.util.List;
import java.util.Map;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterSpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterResourceFactoryTest {

    @Test
    void authEnabledSetsAllAuthEnvVars() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");

        WanakuRouter router = createRouter(auth, null);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("http://keycloak:8080", getEnvValue(deployment, EnvironmentVariables.AUTH_SERVER));
        assertEquals("http://keycloak:8080", getEnvValue(deployment, EnvironmentVariables.AUTH_PROXY));
        assertEquals("wanaku", getEnvValue(deployment, EnvironmentVariables.AUTH_REALM));
    }

    @Test
    void authEnabledWithCustomRealmPropagatesRealm() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");
        auth.setAuthRealm("custom-realm");

        WanakuRouter router = createRouter(auth, null);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("custom-realm", getEnvValue(deployment, EnvironmentVariables.AUTH_REALM));
    }

    @Test
    void authDisabledPreservesTemplateEnvVars() {
        WanakuRouter router = createRouter(null, null);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("true", getEnvValue(deployment, EnvironmentVariables.QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED));

        assertEquals("", getEnvValue(deployment, EnvironmentVariables.AUTH_SERVER));
        assertEquals("", getEnvValue(deployment, EnvironmentVariables.AUTH_PROXY));
        assertEquals("", getEnvValue(deployment, EnvironmentVariables.AUTH_REALM));
    }

    @Test
    void templateEnvVarsPreservedWhenAuthEnabled() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");

        WanakuRouter router = createRouter(auth, null);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertNotNull(getEnvValue(deployment, EnvironmentVariables.QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED));
        assertEquals("true", getEnvValue(deployment, EnvironmentVariables.QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED));
        assertNotNull(getEnvValue(deployment, EnvironmentVariables.AUTH_SERVER));
    }

    @Test
    void customEnvVarsFromRouterSpec() {
        WanakuRouterSpec.RouterSpec routerSpec = new WanakuRouterSpec.RouterSpec();
        WanakuTypes.EnvVar customVar = new WanakuTypes.EnvVar();
        customVar.setName("MY_VAR");
        customVar.setValue("my_value");
        routerSpec.setEnv(List.of(customVar));

        WanakuRouter router = createRouter(null, routerSpec);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("my_value", getEnvValue(deployment, "MY_VAR"));
    }

    @Test
    void authProxyAutoUsesHost() {
        WanakuTypes.AuthSpec auth = new WanakuTypes.AuthSpec();
        auth.setAuthServer("http://keycloak:8080");
        auth.setAuthProxy("auto");

        WanakuRouter router = createRouter(auth, null);
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("https://router.example.com", getEnvValue(deployment, EnvironmentVariables.AUTH_PROXY));
    }

    // ── Ingress factory tests ─────────────────────────────────────────────────

    @Test
    void buildIngressWithoutTlsHasNoTlsStanza() {
        WanakuRouter router = createRouter(null, null);
        Ingress ingress = RouterResourceFactory.makeRouterIngress(router, "wanaku.example.com");
        assertTrue(
                ingress.getSpec().getTls() == null || ingress.getSpec().getTls().isEmpty());
    }

    @Test
    void buildIngressWithTlsSecretNameSetsStanza() {
        WanakuTypes.TlsSpec tls = new WanakuTypes.TlsSpec();
        tls.setSecretName("wanaku-tls");
        WanakuRouter router = createRouterWithIngress(null, "nginx", null, tls);
        Ingress ingress = RouterResourceFactory.makeRouterIngress(router, "wanaku.example.com");
        assertNotNull(ingress.getSpec().getTls());
        assertEquals(1, ingress.getSpec().getTls().size());
        assertEquals("wanaku-tls", ingress.getSpec().getTls().getFirst().getSecretName());
        assertEquals(
                List.of("wanaku.example.com"),
                ingress.getSpec().getTls().getFirst().getHosts());
    }

    @Test
    void buildIngressWithIngressClassName() {
        WanakuRouter router = createRouterWithIngress(null, "nginx", null, null);
        Ingress ingress = RouterResourceFactory.makeRouterIngress(router, "wanaku.example.com");
        assertEquals("nginx", ingress.getSpec().getIngressClassName());
    }

    @Test
    void buildIngressWithoutIngressClassNameLeavesItNull() {
        WanakuRouter router = createRouter(null, null);
        Ingress ingress = RouterResourceFactory.makeRouterIngress(router, "wanaku.example.com");
        assertNull(ingress.getSpec().getIngressClassName());
    }

    @Test
    void buildIngressWithAnnotationsMergesThem() {
        Map<String, String> annotations = Map.of("cert-manager.io/cluster-issuer", "letsencrypt-prod");
        WanakuRouter router = createRouterWithIngress(null, null, annotations, null);
        Ingress ingress = RouterResourceFactory.makeRouterIngress(router, "wanaku.example.com");
        assertNotNull(ingress.getMetadata().getAnnotations());
        assertEquals("letsencrypt-prod", ingress.getMetadata().getAnnotations().get("cert-manager.io/cluster-issuer"));
    }

    // ── Route factory tests ───────────────────────────────────────────────────

    @Test
    void buildRouteWithoutTlsHasNoTlsStanza() {
        WanakuRouter router = createRouter(null, null);
        Route route = RouterResourceFactory.makeRouterExternalService(router);
        assertNull(route.getSpec().getTls());
    }

    @Test
    void buildRouteWithEdgeTlsSetsTermination() {
        WanakuTypes.TlsSpec tls = new WanakuTypes.TlsSpec();
        tls.setTermination(WanakuTypes.TlsTermination.EDGE);
        WanakuRouter router = createRouterWithIngress(WanakuTypes.ExposureType.ROUTE, null, null, tls);
        Route route = RouterResourceFactory.makeRouterExternalService(router);
        assertNotNull(route.getSpec().getTls());
        assertEquals("edge", route.getSpec().getTls().getTermination());
    }

    @Test
    void buildRouteWithReencryptAndCerts() {
        WanakuTypes.TlsSpec tls = new WanakuTypes.TlsSpec();
        tls.setTermination(WanakuTypes.TlsTermination.REENCRYPT);
        tls.setCertificate("CERT");
        tls.setKey("KEY");
        tls.setDestinationCACertificate("DEST-CA");
        WanakuRouter router = createRouterWithIngress(WanakuTypes.ExposureType.ROUTE, null, null, tls);
        Route route = RouterResourceFactory.makeRouterExternalService(router);
        assertNotNull(route.getSpec().getTls());
        assertEquals("reencrypt", route.getSpec().getTls().getTermination());
        assertEquals("CERT", route.getSpec().getTls().getCertificate());
        assertEquals("KEY", route.getSpec().getTls().getKey());
        assertEquals("DEST-CA", route.getSpec().getTls().getDestinationCACertificate());
    }

    @Test
    void buildRouteWithInsecureEdgeRedirect() {
        WanakuTypes.TlsSpec tls = new WanakuTypes.TlsSpec();
        tls.setTermination(WanakuTypes.TlsTermination.EDGE);
        tls.setInsecureEdgeTerminationPolicy(WanakuTypes.InsecureEdgeTerminationPolicy.REDIRECT);
        WanakuRouter router = createRouterWithIngress(WanakuTypes.ExposureType.ROUTE, null, null, tls);
        Route route = RouterResourceFactory.makeRouterExternalService(router);
        assertEquals("Redirect", route.getSpec().getTls().getInsecureEdgeTerminationPolicy());
    }

    // ── Annotation env var tests ──────────────────────────────────────────────

    @Test
    void annotationEnvVarOverridesTemplateVar() {
        WanakuRouter router = createRouter(null, null, Map.of("env.wanaku.ai/QUARKUS_TLS_TRUST_ALL", "false"));
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("false", getEnvValue(deployment, "QUARKUS_TLS_TRUST_ALL"));
    }

    @Test
    void annotationEnvVarAddsNewVar() {
        WanakuRouter router = createRouter(null, null, Map.of("env.wanaku.ai/MY_ANNOTATION_VAR", "injected"));
        Deployment deployment =
                RouterResourceFactory.makeDesiredRouterBackendDeployment(router, null, "router.example.com");

        assertEquals("injected", getEnvValue(deployment, "MY_ANNOTATION_VAR"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static WanakuRouter createRouterWithIngress(
            WanakuTypes.ExposureType type,
            String ingressClassName,
            Map<String, String> annotations,
            WanakuTypes.TlsSpec tls) {
        WanakuTypes.ExposureSpec ingressSpec = new WanakuTypes.ExposureSpec();
        ingressSpec.setType(type);
        ingressSpec.setHost("wanaku.example.com");
        ingressSpec.setIngressClassName(ingressClassName);
        ingressSpec.setAnnotations(annotations);
        ingressSpec.setTls(tls);
        WanakuRouter router = createRouter(null, null);
        router.getSpec().setExposure(ingressSpec);
        return router;
    }

    private static WanakuRouter createRouter(WanakuTypes.AuthSpec auth, WanakuRouterSpec.RouterSpec routerSpec) {
        return createRouter(auth, routerSpec, null);
    }

    private static WanakuRouter createRouter(
            WanakuTypes.AuthSpec auth, WanakuRouterSpec.RouterSpec routerSpec, Map<String, String> annotations) {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .withUid("test-uid-1234")
                .withAnnotations(annotations)
                .build());
        WanakuRouterSpec spec = new WanakuRouterSpec();
        spec.setAuth(auth);
        spec.setRouter(routerSpec);
        router.setSpec(spec);
        return router;
    }

    private static String getEnvValue(Deployment deployment, String name) {
        return deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                .filter(c -> c.getName().equals("wanaku-mcp-router"))
                .findFirst()
                .flatMap(c -> c.getEnv().stream()
                        .filter(e -> e.getName().equals(name))
                        .findFirst())
                .map(EnvVar::getValue)
                .orElse(null);
    }
}
