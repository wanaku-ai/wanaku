package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import dev.langchain4j.mcp.client.McpClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpResourceListTest {

    @Mock
    McpClient mcpClient;

    McpResourceList command;

    @BeforeEach
    void setUp() {
        command = new McpResourceList();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
    }

    @Test
    @DisplayName("Should list resources from MCP server")
    void shouldListResources() throws Exception {
        dev.langchain4j.mcp.client.McpResource res1 =
                new dev.langchain4j.mcp.client.McpResource("file:///data.csv", "data.csv", "A data file", "text/csv");
        dev.langchain4j.mcp.client.McpResource res2 = new dev.langchain4j.mcp.client.McpResource(
                "file:///config.json", "config.json", "Config", "application/json");

        when(mcpClient.listResources()).thenReturn(List.of(res1, res2));

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            String output = captured.toString();
            assertTrue(output.contains("data.csv"));
            assertTrue(output.contains("config.json"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should show info when no resources found")
    void shouldShowInfoWhenEmpty() throws Exception {
        when(mcpClient.listResources()).thenReturn(Collections.emptyList());

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).printInfoMessage("No resources found");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.listResources()).thenThrow(new RuntimeException("connection refused"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("connection refused");
    }
}
