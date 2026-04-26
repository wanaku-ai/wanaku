package ai.wanaku.cli.main.commands.configure;

import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
        assertEquals("uvx", wanaku.path("command").asText());
        assertEquals("mcp-proxy", wanaku.path("args").get(0).asText());
        assertEquals(
                "http://localhost:8080/mcp/sse/", wanaku.path("args").get(1).asText());
        assertTrue(wanaku.path("env").isObject());

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
        assertEquals("uvx", wanaku.path("command").asText());
        assertEquals("mcp-proxy", wanaku.path("args").get(0).asText());
        assertEquals("--transport=streamablehttp", wanaku.path("args").get(1).asText());
        assertEquals("http://localhost:8080/mcp", wanaku.path("args").get(2).asText());
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
    void configureShouldRejectUnsupportedTransport() throws Exception {
        Path configPath = tempDir.resolve("mcp.json");
        ConfigureCursor cmd = new ConfigureCursor(configPath);
        cmd.transport = "stdio";

        int result = cmd.doCall(terminal, printer);

        assertEquals(EXIT_ERROR, result);
        assertFalse(Files.exists(configPath));
        verify(printer).printErrorMessage("Unsupported transport 'stdio'. Supported transports: sse, http");
    }
}
