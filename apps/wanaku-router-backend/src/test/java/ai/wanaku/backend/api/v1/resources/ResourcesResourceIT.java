package ai.wanaku.backend.api.v1.resources;

import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIf;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class ResourcesResourceIT extends AbstractResourcesResourceTest {

    private static KeycloakTestClient keycloakClient;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
        keycloakClient = new KeycloakTestClient();
    }

    @Override
    protected Map<String, String> getHeaders() {
        final String accessToken = keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
        Assertions.assertNotNull(accessToken);
        return Map.of("Content-Type", MediaType.APPLICATION_JSON, "Authorization", "Bearer " + accessToken);
    }
}
