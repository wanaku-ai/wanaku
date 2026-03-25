package ai.wanaku.cli.main.commands.users;

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

class UsersCommandsTest {

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
    void usersAddHappyPath() throws Exception {
        doNothing().when(adminClient).createUser(any(), any(), any(), any());

        UsersAdd cmd = new UsersAdd(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void usersListHappyPath() throws Exception {
        when(adminClient.listUsers(any()))
                .thenReturn(List.of(Map.of("username", "alice", "email", "alice@test.com", "enabled", true)));

        UsersList cmd = new UsersList(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printTable(any(List.class), eq("username"), eq("email"), eq("enabled"));
    }

    @Test
    void usersRemoveHappyPath() throws Exception {
        doNothing().when(adminClient).deleteUser(any(), any());

        UsersRemove cmd = new UsersRemove(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    @Test
    void usersSetPasswordHappyPath() throws Exception {
        doNothing().when(adminClient).setPassword(any(), any(), any());

        UsersSetPassword cmd = new UsersSetPassword(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage(any());
    }

    // ---- Error-propagation tests ----

    @Test
    void usersAddShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("user already exists"))
                .when(adminClient)
                .createUser(any(), any(), any(), any());

        UsersAdd cmd = new UsersAdd(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("user already exists");
    }

    @Test
    void usersListShouldReturnErrorOnKeycloakException() throws Exception {
        when(adminClient.listUsers(any())).thenThrow(new KeycloakAdminClient.KeycloakAdminException("forbidden"));

        UsersList cmd = new UsersList(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("forbidden");
    }

    @Test
    void usersRemoveShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("user not found"))
                .when(adminClient)
                .deleteUser(any(), any());

        UsersRemove cmd = new UsersRemove(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("user not found");
    }

    @Test
    void usersSetPasswordShouldReturnErrorOnKeycloakException() throws Exception {
        doThrow(new KeycloakAdminClient.KeycloakAdminException("cannot set password"))
                .when(adminClient)
                .setPassword(any(), any(), any());

        UsersSetPassword cmd = new UsersSetPassword(adminClient);
        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("cannot set password");
    }
}
