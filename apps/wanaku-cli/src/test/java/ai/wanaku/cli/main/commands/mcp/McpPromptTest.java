package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPromptMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpPromptTest {

    @Mock
    McpClient mcpClient;

    McpPrompt command;

    @BeforeEach
    void setUp() {
        command = new McpPrompt();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
        command.name = "greeting";
        command.args = new LinkedHashMap<>(Map.of("name", "Alice"));
    }

    @Test
    @DisplayName("Should get prompt and print messages")
    void shouldGetPromptAndPrintMessages() throws Exception {
        McpPromptMessage message = mock(McpPromptMessage.class);
        when(message.toString()).thenReturn("Hello Alice!");
        McpGetPromptResult promptResult = mock(McpGetPromptResult.class);
        when(promptResult.messages()).thenReturn(List.of(message));

        when(mcpClient.getPrompt(eq("greeting"), any())).thenReturn(promptResult);

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            assertTrue(captured.toString().contains("Hello Alice!"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should show info when prompt returns no messages")
    void shouldShowInfoWhenEmpty() throws Exception {
        McpGetPromptResult emptyResult = mock(McpGetPromptResult.class);
        when(emptyResult.messages()).thenReturn(Collections.emptyList());

        when(mcpClient.getPrompt(eq("greeting"), any())).thenReturn(emptyResult);

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).printInfoMessage("Prompt returned no messages");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.getPrompt(eq("greeting"), any())).thenThrow(new RuntimeException("not found"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("not found");
    }
}
