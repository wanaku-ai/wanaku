package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolTest {

    @Mock
    McpClient mcpClient;

    McpTool command;

    @BeforeEach
    void setUp() {
        command = new McpTool();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
        command.name = "echo";
        command.params = new LinkedHashMap<>(Map.of("message", "hello"));
    }

    @Test
    @DisplayName("Should call tool and print result")
    void shouldCallToolAndPrintResult() throws Exception {
        when(mcpClient.executeTool(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.builder()
                        .resultText("hello world")
                        .isError(false)
                        .build());

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            assertTrue(captured.toString().contains("hello world"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should return error when tool execution fails")
    void shouldReturnErrorWhenToolFails() throws Exception {
        when(mcpClient.executeTool(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.builder()
                        .resultText("tool failed")
                        .isError(true)
                        .build());

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("tool failed");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.executeTool(any(ToolExecutionRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("connection refused");
    }

    @Test
    @DisplayName("Should serialize params to JSON")
    void shouldSerializeParamsToJson() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");

        String json = McpTool.serializeParams(params);

        assertEquals("{\"key1\":\"value1\",\"key2\":\"value2\"}", json);
    }

    @Test
    @DisplayName("Should return error when uri is missing")
    void shouldReturnErrorWhenUriMissing() throws Exception {
        command.uri = null;
        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);
        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("Missing required option: --uri");
    }

    @Test
    @DisplayName("Should return error when name is missing")
    void shouldReturnErrorWhenNameMissing() throws Exception {
        command.name = null;
        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);
        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("Missing required option: --name");
    }
}
