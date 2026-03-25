package ai.wanaku.cli.main.commands.credentials;

import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;

import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_ERROR;
import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_OK;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialsCommandsTest {

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    @Mock
    private KeycloakAdminClient adminClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---- Happy-path tests ----

    @Test
    void credentialsAddHappyPath() throws Exception {
        doNothing().when(adminClient).createClient(any(), any(), any());

        CredentialsAdd cmd = new CredentialsAdd(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void credentialsListHappyPath() throws Exception {
        when(adminClient.listClients(any()))
                .thenReturn(List.of(Map.of("clientId", "my-service", "description", "test", "enabled", true)));

        CredentialsList cmd = new CredentialsList(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printTable(any(List.class), eq("clientId"), eq("description"), eq("enabled"));
    }

    @Test
    void credentialsListShouldFilterInternalClients() throws Exception {
        when(adminClient.listClients(any()))
                .thenReturn(List.of(
                        Map.of("clientId", "account", "description", "", "enabled", true),
                        Map.of("clientId", "admin-cli", "description", "", "enabled", true),
                        Map.of("clientId", "my-service", "description", "custom", "enabled", true)));

        CredentialsList cmd = new CredentialsList(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
    }

    @Test
    void credentialsRemoveHappyPath() throws Exception {
        doNothing().when(adminClient).deleteClient(any(), any());

        CredentialsRemove cmd = new CredentialsRemove(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void credentialsRegenerateHappyPath() throws Exception {
        when(adminClient.regenerateClientSecret(any(), any())).thenReturn("new-secret");

        CredentialsRegenerate cmd = new CredentialsRegenerate(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void credentialsShowWithoutFlagShouldWarn() throws Exception {
        CredentialsShow cmd = new CredentialsShow(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printWarningMessage(any());
    }

    // ---- Error-propagation tests ----

    @Test
    void credentialsAddShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("client already exists"))
                .when(adminClient)
                .createClient(any(), any(), any());

        CredentialsAdd cmd = new CredentialsAdd(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("client already exists");
    }

    @Test
    void credentialsListShouldReturnErrorOnKeycloakException() throws Exception {
        when(adminClient.listClients(any())).thenThrow(new KeycloakAdminClient.KeycloakAdminException("forbidden"));

        CredentialsList cmd = new CredentialsList(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("forbidden");
    }

    @Test
    void credentialsRemoveShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("client not found"))
                .when(adminClient)
                .deleteClient(any(), any());

        CredentialsRemove cmd = new CredentialsRemove(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("client not found");
    }

    @Test
    void credentialsRegenerateShouldReturnErrorOnKeycloakException() throws Exception {
        when(adminClient.regenerateClientSecret(any(), any()))
                .thenThrow(new KeycloakAdminClient.KeycloakAdminException("cannot regenerate"));

        CredentialsRegenerate cmd = new CredentialsRegenerate(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("cannot regenerate");
    }
}
