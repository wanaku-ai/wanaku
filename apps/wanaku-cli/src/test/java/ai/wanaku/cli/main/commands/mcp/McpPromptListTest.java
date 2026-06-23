package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import picocli.CommandLine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpPromptListTest {

    @Mock
    McpClient mcpClient;

    McpPromptList command;

    @BeforeEach
    void setUp() {
        command = new McpPromptList();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
    }

    @Test
    @DisplayName("Should list prompts from MCP server")
    void shouldListPrompts() throws Exception {
        dev.langchain4j.mcp.client.McpPrompt prompt1 =
                new dev.langchain4j.mcp.client.McpPrompt("greeting", "A greeting prompt", null);
        dev.langchain4j.mcp.client.McpPrompt prompt2 =
                new dev.langchain4j.mcp.client.McpPrompt("summary", "Summarizes text", null);

        when(mcpClient.listPrompts()).thenReturn(List.of(prompt1, prompt2));

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            String output = captured.toString();
            assertTrue(output.contains("greeting"));
            assertTrue(output.contains("summary"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should show info when no prompts found")
    void shouldShowInfoWhenEmpty() throws Exception {
        when(mcpClient.listPrompts()).thenReturn(Collections.emptyList());

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).printInfoMessage("No prompts found");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.listPrompts()).thenThrow(new RuntimeException("connection refused"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("connection refused");
    }

    @Test
    @DisplayName("Should forward auth token to MCP client")
    void shouldForwardAuthToken() throws Exception {
        McpClient tokenClient = mock(McpClient.class);
        when(tokenClient.listPrompts()).thenReturn(Collections.emptyList());

        McpPromptList cmd = new McpPromptList();
        new CommandLine(cmd).parseArgs("--uri", "http://localhost:9999/mcp/sse", "--token", "my-secret-token");

        try (MockedStatic<ClientUtil> clientUtil = mockStatic(ClientUtil.class)) {
            clientUtil
                    .when(() -> ClientUtil.createClient("http://localhost:9999/mcp/sse", "my-secret-token"))
                    .thenReturn(tokenClient);

            Integer result = cmd.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);

            clientUtil.verify(() -> ClientUtil.createClient("http://localhost:9999/mcp/sse", "my-secret-token"));
        }
    }
}
