package ai.wanaku.cli.main.commands.admin;

import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseAdminCommandTest {

    private static class TestAdminCommand extends BaseAdminCommand {
        private TestAdminCommand(Map<String, String> env) {
            super(null, env::get);
        }

        @Override
        public Integer doCall(Terminal terminal, WanakuPrinter printer) {
            return EXIT_OK;
        }

        private String resolveUsernameForTest() {
            return resolveAdminUsername();
        }

        private String resolvePasswordForTest() {
            return resolveAdminPassword();
        }
    }

    @Test
    void resolvesCredentialsFromEnvironmentWhenOptionsMissing() {
        TestAdminCommand command = new TestAdminCommand(Map.of(
                "WANAKU_ADMIN_USERNAME", "env-admin",
                "WANAKU_ADMIN_PASSWORD", "env-pass"));

        assertEquals("env-admin", command.resolveUsernameForTest());
        assertEquals("env-pass", command.resolvePasswordForTest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", "  \t  "})
    void treatsBlankOrWhitespaceUsernameEnvAsMissing(String usernameEnvValue) {
        TestAdminCommand command = new TestAdminCommand(
                Map.of("WANAKU_ADMIN_USERNAME", usernameEnvValue, "WANAKU_ADMIN_PASSWORD", "env-pass"));

        CommandLine.ParameterException ex =
                assertThrows(CommandLine.ParameterException.class, command::resolveUsernameForTest);
        assertEquals(
                "Admin username not provided. Use --admin-username or set WANAKU_ADMIN_USERNAME.", ex.getMessage());
        assertEquals("env-pass", command.resolvePasswordForTest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", "  \t  "})
    void treatsBlankOrWhitespacePasswordEnvAsMissing(String passwordEnvValue) {
        TestAdminCommand command = new TestAdminCommand(
                Map.of("WANAKU_ADMIN_USERNAME", "env-admin", "WANAKU_ADMIN_PASSWORD", passwordEnvValue));

        assertEquals("env-admin", command.resolveUsernameForTest());
        CommandLine.ParameterException ex =
                assertThrows(CommandLine.ParameterException.class, command::resolvePasswordForTest);
        assertEquals(
                "Admin password not provided. Use --admin-password or set WANAKU_ADMIN_PASSWORD.", ex.getMessage());
    }

    @Test
    void optionsOverrideEnvironment() {
        TestAdminCommand command = new TestAdminCommand(Map.of(
                "WANAKU_ADMIN_USERNAME", "env-admin",
                "WANAKU_ADMIN_PASSWORD", "env-pass"));
        command.adminUsername = "cli-admin";
        command.adminPassword = "cli-pass";

        assertEquals("cli-admin", command.resolveUsernameForTest());
        assertEquals("cli-pass", command.resolvePasswordForTest());
    }

    @Test
    void throwsWhenUsernameMissingEverywhere() {
        TestAdminCommand command = new TestAdminCommand(Map.of());

        CommandLine.ParameterException ex =
                assertThrows(CommandLine.ParameterException.class, command::resolveUsernameForTest);
        assertEquals(
                "Admin username not provided. Use --admin-username or set WANAKU_ADMIN_USERNAME.", ex.getMessage());
    }

    @Test
    void throwsWhenPasswordMissingEverywhere() {
        TestAdminCommand command = new TestAdminCommand(Map.of());

        CommandLine.ParameterException ex =
                assertThrows(CommandLine.ParameterException.class, command::resolvePasswordForTest);
        assertEquals(
                "Admin password not provided. Use --admin-password or set WANAKU_ADMIN_PASSWORD.", ex.getMessage());
    }
}
