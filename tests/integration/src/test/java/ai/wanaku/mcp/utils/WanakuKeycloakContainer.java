package ai.wanaku.mcp.utils;

import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.quarkus.test.keycloak.server.KeycloakContainer;

public class WanakuKeycloakContainer extends KeycloakContainer {

    // Define the path to the realm file, assuming it's in the test resources
    private static final String REALM_FILE_PATH = "/wanaku-realm.json";
    public static final String REALM_NAME = "wanaku";
    public static final String CLIENT_ID = "wanaku-service";
    public static final String CLIENT_SECRET = "secret";

    private static KeycloakTestClient keycloakClient = new KeycloakTestClient();

    public WanakuKeycloakContainer() {
        // Configure the container
        super(DockerImageName.parse("quay.io/keycloak/keycloak:26.3.5"));
        this.withUseHttps(false);
        this.waitingFor(Wait.forLogMessage(".*Keycloak.*started.*Listening on:.*", 1));
    }

    public void createRealm() {
        final String path =
                KeycloakTestClient.class.getResource(REALM_FILE_PATH).getPath();

        KeycloakTestClient keycloakClient = new KeycloakTestClient(getServerUrl());

        final RealmRepresentation realmRepresentation = keycloakClient.readRealmFile(path);
        realmRepresentation.getClients().stream()
                .filter(c -> c.getClientId().equals(CLIENT_ID))
                .findFirst()
                .get()
                .setSecret(CLIENT_SECRET);

        keycloakClient.createRealm(realmRepresentation);
    }

    // Static method to get the OIDC properties for Quarkus
    public String getRealmUrl() {
        return String.format("%s/realms/%s", getServerUrl(), REALM_NAME);
    }

    public String getRealmUrl(String host) {
        return String.format("http://%s:%d/realms/%s", host, getMappedPort(8080), REALM_NAME);
    }

    public String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken(REALM_NAME, CLIENT_ID, CLIENT_SECRET);
    }

    public static String getOidcSecret() {
        return CLIENT_SECRET;
    }

    public static String getOidcClientId() {
        return CLIENT_ID;
    }

    @Override
    public void start() {
        super.start();

        System.setProperty("keycloak.url", getServerUrl());
        System.setProperty("quarkus.oidc.auth-server-url", String.format("%s/realms/%s", getServerUrl(), "wanaku"));
        System.setProperty("quarkus.oidc.credentials.secret", "secret");
        System.setProperty("quarkus.oidc.client-id", "wanaku-service");
    }
}
