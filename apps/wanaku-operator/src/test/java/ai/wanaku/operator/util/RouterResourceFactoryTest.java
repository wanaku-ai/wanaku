package ai.wanaku.operator.util;

import java.util.List;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import ai.wanaku.operator.wanaku.WanakuRouter;
import ai.wanaku.operator.wanaku.WanakuRouterSpec;
import ai.wanaku.operator.wanaku.WanakuTypes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        assertEquals("http://router.example.com", getEnvValue(deployment, EnvironmentVariables.AUTH_PROXY));
    }

    private static WanakuRouter createRouter(WanakuTypes.AuthSpec auth, WanakuRouterSpec.RouterSpec routerSpec) {
        WanakuRouter router = new WanakuRouter();
        router.setMetadata(new ObjectMetaBuilder()
                .withName("test-router")
                .withNamespace("default")
                .withUid("test-uid-1234")
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
