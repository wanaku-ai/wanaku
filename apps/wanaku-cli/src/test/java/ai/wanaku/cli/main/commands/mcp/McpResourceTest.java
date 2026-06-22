package ai.wanaku.cli.main.commands.mcp;

import java.util.Collections;
import java.util.List;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).println(textContent.toString());
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
}
