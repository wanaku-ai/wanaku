package ai.wanaku.backend.support;

import ai.wanaku.backend.api.v1.management.discovery.DiscoveryResourceTest;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.quarkus.test.keycloak.server.KeycloakContainer;
import java.util.HashMap;
import java.util.Map;
import org.keycloak.representations.idm.RealmRepresentation;

public class WanakuKeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private KeycloakContainer keycloak;

    @Override
    public Map<String, String> start() {
        keycloak = new KeycloakContainer()
                .withUseHttps(false);
        keycloak.start();

        final String path = DiscoveryResourceTest.class.getResource("/wanaku-realm.json").getPath();

        KeycloakTestClient keycloakClient = new KeycloakTestClient(keycloak.getServerUrl());

        final RealmRepresentation realmRepresentation = keycloakClient.readRealmFile(path);
        realmRepresentation.getClients().stream().filter(c -> c.getClientId().equals("wanaku-service"))
                .findFirst().get().setSecret("secret");

        keycloakClient.createRealm(realmRepresentation);

        Map<String, String> conf = new HashMap<>();
        conf.put("keycloak.url", keycloak.getServerUrl());
        conf.put("quarkus.oidc.auth-server-url", String.format("%s/realms/%s", keycloak.getServerUrl(), "wanaku"));

        conf.put("quarkus.oidc.credentials.secret",  "secret");
        conf.put("quarkus.oidc.client-id", "wanaku-service");

        return conf;
    }

    @Override
    public void stop() {
        keycloak.stop();
    }
}
