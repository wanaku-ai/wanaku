package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.mcp.client.ClientUtil;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
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
class McpResourceTest {

    @Mock
    McpClient mcpClient;

    McpResource command;

    @BeforeEach
    void setUp() {
        command = new McpResource();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
        command.resourceUri = "file:///test.txt";
    }

    @Test
    @DisplayName("Should read resource and print content")
    void shouldReadResourceAndPrintContent() throws Exception {
        McpTextResourceContents textContent =
                new McpTextResourceContents("file:///test.txt", "Hello from resource", "text/plain");
        McpReadResourceResult readResult = new McpReadResourceResult(List.of(textContent));

        when(mcpClient.readResource("file:///test.txt")).thenReturn(readResult);

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            assertTrue(captured.toString().contains("Hello from resource"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should show info when resource returns no content")
    void shouldShowInfoWhenEmpty() throws Exception {
        McpReadResourceResult emptyResult = new McpReadResourceResult(Collections.emptyList());
        when(mcpClient.readResource("file:///test.txt")).thenReturn(emptyResult);

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).printInfoMessage("Resource returned no content");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.readResource("file:///test.txt")).thenThrow(new RuntimeException("not found"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("not found");
    }

    @Test
    @DisplayName("Should forward auth token to MCP client")
    void shouldForwardAuthToken() throws Exception {
        McpClient tokenClient = mock(McpClient.class);
        McpReadResourceResult emptyResult = new McpReadResourceResult(Collections.emptyList());
        when(tokenClient.readResource("file:///test.txt")).thenReturn(emptyResult);

        McpResource cmd = new McpResource();
        new CommandLine(cmd)
                .parseArgs(
                        "--uri",
                        "http://localhost:9999/mcp/sse",
                        "--resource-uri",
                        "file:///test.txt",
                        "--token",
                        "my-secret-token");

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
