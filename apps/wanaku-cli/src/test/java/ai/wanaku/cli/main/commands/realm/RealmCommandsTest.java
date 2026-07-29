package ai.wanaku.cli.main.commands.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void resolvePropertyPlaceholdersShouldUseDefaultWhenEnvNotSet() {
        String input = "{\"secret\": \"${WANAKU_TEST_NOT_SET_12345:mypasswd}\"}";
        String result = RealmCreate.resolvePropertyPlaceholders(input);
        assertEquals("{\"secret\": \"mypasswd\"}", result);
    }

    @Test
    void resolvePropertyPlaceholdersShouldLeaveLocalizationKeysUntouched() {
        String input = "{\"name\": \"${client_account}\", \"desc\": \"${role_uma_authorization}\"}";
        String result = RealmCreate.resolvePropertyPlaceholders(input);
        assertEquals(input, result, "Keycloak localization keys (without colon default) must not be changed");
    }

    @Test
    void resolvePropertyPlaceholdersShouldHandleMultiplePlaceholders() {
        String input = "{\"a\": \"${NOT_SET_A_12345:valA}\", \"b\": \"${NOT_SET_B_12345:valB}\"}";
        String result = RealmCreate.resolvePropertyPlaceholders(input);
        assertEquals("{\"a\": \"valA\", \"b\": \"valB\"}", result);
    }

    @Test
    void resolvePropertyPlaceholdersShouldHandleEmptyDefault() {
        String input = "{\"secret\": \"${NOT_SET_EMPTY_12345:}\"}";
        String result = RealmCreate.resolvePropertyPlaceholders(input);
        assertEquals("{\"secret\": \"\"}", result);
    }

    @Test
    void resolvePropertyPlaceholdersShouldPreservePlainText() {
        String input = "{\"realm\": \"wanaku\", \"enabled\": true}";
        String result = RealmCreate.resolvePropertyPlaceholders(input);
        assertEquals(input, result);
    }

    @Test
    void realmCreateShouldResolvePlaceholdersBeforeImport() throws Exception {
        Path configFile = tempDir.resolve("realm-with-placeholders.json");
        Files.writeString(configFile, "{\"secret\": \"${WANAKU_TEST_NOT_SET_67890:resolved_secret}\"}");
        doNothing().when(adminClient).importRealm(any());

        RealmCreate cmd = new RealmCreate(adminClient, configFile.toString());
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(adminClient).importRealm(captor.capture());
        String importedJson = captor.getValue();
        assertFalse(importedJson.contains("${"), "Placeholders should be resolved before import");
        assertTrue(importedJson.contains("resolved_secret"), "Default value should be substituted");
    }
}
