package ai.wanaku.cli.main.commands.auth;

import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.nio.file.Path;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthCommandsTest {

    @TempDir
    Path tempDir;

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    private Path credentialsFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        credentialsFile = tempDir.resolve("test-credentials");
    }

    @Test
    void authLogoutShouldClearCredentials() throws Exception {
        AuthCredentialStore store = new AuthCredentialStore(credentialsFile.toUri());
        store.storeApiToken("test-token");

        AuthLogout authLogout = new AuthLogout(store);
        int result = authLogout.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printSuccessMessage("Successfully cleared authentication credentials");
    }

    @Test
    void authLogoutShouldHandleNoCredentials() throws Exception {
        AuthCredentialStore store = new AuthCredentialStore(credentialsFile.toUri());
        AuthLogout authLogout = new AuthLogout(store);
        int result = authLogout.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printInfoMessage("No authentication credentials found");
    }

    @Test
    void authStatusShouldDisplayStatus() throws Exception {
        AuthCredentialStore store = new AuthCredentialStore(credentialsFile.toUri());
        store.storeApiToken("test-token-123456789");
        store.storeAuthMode("token");

        AuthStatus authStatus = new AuthStatus(store);
        int result = authStatus.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printInfoMessage("Authentication Status:");
        verify(printer).printInfoMessage("Mode: token");
        verify(printer).printInfoMessage(contains("API Token: test***6789"));
        verify(printer).printInfoMessage("Has Credentials: true");
    }
}
