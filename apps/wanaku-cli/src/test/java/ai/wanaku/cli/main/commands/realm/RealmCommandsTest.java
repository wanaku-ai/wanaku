package ai.wanaku.cli.main.commands.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;

import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_ERROR;
import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_OK;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class RealmCommandsTest {

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    @Mock
    private KeycloakAdminClient adminClient;

    @TempDir
    Path tempDir;

    private String configPath;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        Path configFile = tempDir.resolve("realm.json");
        Files.writeString(configFile, "{\"realm\": \"test\"}");
        configPath = configFile.toString();
    }

    @Test
    void realmCreateHappyPath() throws Exception {
        doNothing().when(adminClient).importRealm(any());

        RealmCreate cmd = new RealmCreate(adminClient, configPath);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void realmCreateShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("realm already exists"))
                .when(adminClient)
                .importRealm(any());

        RealmCreate cmd = new RealmCreate(adminClient, configPath);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("realm already exists");
    }

    @Test
    void realmCreateShouldReturnErrorOnMissingFile() throws Exception {
        RealmCreate cmd = new RealmCreate(adminClient, "/nonexistent/path/realm.json");
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage(contains("/nonexistent/path/realm.json"));
    }

    @Test
    void realmCreateWithDefaultConfigShouldReturnErrorWhenFileMissing() throws Exception {
        RealmCreate cmd = new RealmCreate(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage(contains("deploy/auth/wanaku-config.json"));
    }

    @Test
    void shouldResolvePlaceholderFromEnvironment() {
        String json = "{\"secret\": \"${WANAKU_SERVICE_SECRET:mypasswd}\"}";
        Map<String, String> env = Map.of("WANAKU_SERVICE_SECRET", "real-secret");

        String resolved = RealmCreate.resolveEnvPlaceholders(json, env::get);

        assertEquals("{\"secret\": \"real-secret\"}", resolved);
    }

    @Test
    void shouldResolvePlaceholderToDefaultWhenEnvUnset() {
        String json = "{\"secret\": \"${WANAKU_SERVICE_SECRET:mypasswd}\"}";

        String resolved = RealmCreate.resolveEnvPlaceholders(json, name -> null);

        assertEquals("{\"secret\": \"mypasswd\"}", resolved);
    }

    @Test
    void shouldLeaveKeycloakI18nKeysUntouched() {
        String json = "{\"description\": \"${role_admin}\", \"secret\": \"${WANAKU_SERVICE_SECRET:mypasswd}\"}";

        String resolved = RealmCreate.resolveEnvPlaceholders(json, name -> null);

        assertEquals("{\"description\": \"${role_admin}\", \"secret\": \"mypasswd\"}", resolved);
    }

    @Test
    void shouldEscapeJsonSpecialCharactersInEnvValues() {
        String json = "{\"secret\": \"${WANAKU_SERVICE_SECRET:mypasswd}\"}";
        Map<String, String> env = Map.of("WANAKU_SERVICE_SECRET", "pa\"ss\\wd");

        String resolved = RealmCreate.resolveEnvPlaceholders(json, env::get);

        assertEquals("{\"secret\": \"pa\\\"ss\\\\wd\"}", resolved);
    }

    @Test
    void realmCreateShouldImportRealmWithResolvedPlaceholders() throws Exception {
        Path configFile = tempDir.resolve("realm-placeholder.json");
        Files.writeString(
                configFile,
                "{\"realm\": \"wanaku\", \"secret\": \"${WANAKU_SERVICE_SECRET:mypasswd}\","
                        + " \"description\": \"${role_admin}\"}");
        doNothing().when(adminClient).importRealm(any());

        RealmCreate cmd = new RealmCreate(adminClient, configFile.toString());
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminClient).importRealm(jsonCaptor.capture());
        String envSecret = System.getenv("WANAKU_SERVICE_SECRET");
        String expectedSecret = (envSecret != null && !envSecret.isBlank()) ? envSecret : "mypasswd";
        assertEquals(
                "{\"realm\": \"wanaku\", \"secret\": \"" + expectedSecret + "\", \"description\": \"${role_admin}\"}",
                jsonCaptor.getValue());
    }
}
