package ai.wanaku.cli.main.commands.configure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_ERROR;
import static ai.wanaku.cli.main.commands.BaseCommand.EXIT_OK;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConfigureCommandsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void claudeConfigureShouldCreateClaudeDesktopConfig() throws Exception {
        Path configPath = tempDir.resolve("claude/claude_desktop_config.json");
        ConfigureClaude cmd = new ConfigureClaude(configPath);
        cmd.transport = "sse";
        cmd.host = "localhost";
        cmd.port = 8080;

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        assertTrue(Files.exists(configPath));

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        JsonNode wanaku = root.path("mcpServers").path("wanaku");
        assertEquals("http://localhost:8080/mcp/sse/", wanaku.path("url").asText());

        verify(printer).printSuccessMessage(anyString());
    }

    @Test
    void claudeConfigureShouldUseStreamableHttpWhenRequested() throws Exception {
        Path configPath = tempDir.resolve("claude/claude_desktop_config.json");
        ConfigureClaude cmd = new ConfigureClaude(configPath);
        cmd.transport = "http";
        cmd.host = "localhost";
        cmd.port = 8080;

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        JsonNode wanaku = root.path("mcpServers").path("wanaku");
        assertEquals("http://localhost:8080/mcp", wanaku.path("url").asText());
    }

    @Test
    void cursorConfigureShouldMergeExistingConfig() throws Exception {
        Path configPath = tempDir.resolve(".cursor/mcp.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(
                configPath,
                """
                {
                  "theme": "dark",
                  "mcpServers": {
                    "existing": {
                      "url": "http://example.com/mcp"
                    }
                  }
                }
                """);

        ConfigureCursor cmd = new ConfigureCursor(configPath);
        cmd.transport = "http";
        cmd.host = "localhost";
        cmd.port = 8080;

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        assertEquals("dark", root.path("theme").asText());
        assertEquals(
                "http://example.com/mcp",
                root.path("mcpServers").path("existing").path("url").asText());
        assertEquals(
                "http://localhost:8080/mcp",
                root.path("mcpServers").path("wanaku").path("url").asText());
        verify(printer).printSuccessMessage(anyString());
    }

    @Test
    void ibmBobConfigureShouldCreateConfigWithDefaults() throws Exception {
        Path configPath = tempDir.resolve(".bob/mcp_settings.json");
        ConfigureIbmBob cmd = new ConfigureIbmBob(configPath);
        cmd.transport = "http";
        cmd.host = "localhost";
        cmd.port = 8080;

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        assertTrue(Files.exists(configPath));

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        JsonNode wanaku = root.path("mcpServers").path("wanaku");
        assertEquals("http://localhost:8080/mcp", wanaku.path("url").asText());
        assertNull(wanaku.path("headers").get("Authorization"));
        assertTrue(wanaku.path("alwaysAllow").isMissingNode()
                || wanaku.path("alwaysAllow").isEmpty());

        verify(printer).printSuccessMessage(anyString());
    }

    @Test
    void ibmBobConfigureShouldUseCustomHostAndToken() throws Exception {
        Path configPath = tempDir.resolve(".bob/mcp_settings.json");
        ConfigureIbmBob cmd = new ConfigureIbmBob(configPath);
        cmd.transport = "sse";
        cmd.host = "wanaku.example.com";
        cmd.port = 9090;
        cmd.bearerToken = "my-secret-token";
        cmd.alwaysAllow = List.of("tool1", "tool2");

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        JsonNode wanaku = root.path("mcpServers").path("wanaku");
        assertEquals(
                "http://wanaku.example.com:9090/mcp/sse/", wanaku.path("url").asText());
        assertEquals(
                "Bearer my-secret-token",
                wanaku.path("headers").path("Authorization").asText());
        assertEquals("tool1", wanaku.path("alwaysAllow").get(0).asText());
        assertEquals("tool2", wanaku.path("alwaysAllow").get(1).asText());
    }

    @Test
    void ibmBobConfigureShouldMergeExistingConfig() throws Exception {
        Path configPath = tempDir.resolve(".bob/mcp_settings.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(
                configPath,
                """
                {
                  "mcpServers": {
                    "remote-server": {
                      "url": "https://old-server.com/mcp",
                      "headers": { "Authorization": "Bearer old-token" },
                      "alwaysAllow": ["tool1"]
                    }
                  }
                }
                """);

        ConfigureIbmBob cmd = new ConfigureIbmBob(configPath);
        cmd.transport = "sse";
        cmd.host = "localhost";
        cmd.port = 8080;

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);

        JsonNode root = MAPPER.readTree(Files.readString(configPath));
        JsonNode existing = root.path("mcpServers").path("remote-server");
        assertEquals("https://old-server.com/mcp", existing.path("url").asText());
        JsonNode wanaku = root.path("mcpServers").path("wanaku");
        assertEquals("http://localhost:8080/mcp/sse/", wanaku.path("url").asText());
    }

    @Test
    void configureShouldRejectUnsupportedTransport() throws Exception {
        Path configPath = tempDir.resolve("mcp.json");
        ConfigureCursor cmd = new ConfigureCursor(configPath);
        cmd.transport = "stdio";

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        assertFalse(Files.exists(configPath));
        verify(printer).printErrorMessage("Unsupported transport 'stdio'. Supported transports: sse, http");
    }

    @Test
    void claudeCodeConfigureShouldPrintAddCommandWithDefaultHost() throws Exception {
        ConfigureClaudeCode cmd = new ConfigureClaudeCode();

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printInfoMessage("Run this command to register Wanaku with Claude Code:");
        verify(printer).printInfoMessage("claude mcp add wanaku --transport sse http://localhost:8080/mcp/sse");
    }

    @Test
    void claudeCodeConfigureShouldPrintAddCommandWithCustomHost() throws Exception {
        ConfigureClaudeCode cmd = new ConfigureClaudeCode();
        cmd.host = "https://wanaku.example.com/";

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_OK, result);
        verify(printer).printInfoMessage("claude mcp add wanaku --transport sse https://wanaku.example.com/mcp/sse");
    }

    @Test
    void claudeCodeConfigureShouldRejectHostWithoutScheme() throws Exception {
        ConfigureClaudeCode cmd = new ConfigureClaudeCode();
        cmd.host = "wanaku.example.com";

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        verify(printer).printErrorMessage("Wanaku host URL must include a scheme and host");
        verify(printer, never()).printInfoMessage(anyString());
    }
}
