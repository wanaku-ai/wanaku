package ai.wanaku.cli.main.commands.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import dev.langchain4j.agent.tool.ToolSpecification;
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
class McpToolListTest {

    @Mock
    McpClient mcpClient;

    McpToolList command;

    @BeforeEach
    void setUp() {
        command = new McpToolList();
        command.mcpClient = mcpClient;
        command.uri = "http://localhost:3001/mcp";
    }

    @Test
    @DisplayName("Should list tools from MCP server")
    void shouldListTools() throws Exception {
        ToolSpecification tool1 = ToolSpecification.builder()
                .name("echo")
                .description("Echoes the input")
                .build();
        ToolSpecification tool2 = ToolSpecification.builder()
                .name("add")
                .description("Adds two numbers")
                .build();

        when(mcpClient.listTools()).thenReturn(List.of(tool1, tool2));

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Integer result = command.doCall(null, mock(WanakuPrinter.class));
            assertEquals(BaseCommand.EXIT_OK, result);
            String output = captured.toString();
            assertTrue(output.contains("echo"));
            assertTrue(output.contains("add"));
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @DisplayName("Should show info when no tools found")
    void shouldShowInfoWhenEmpty() throws Exception {
        when(mcpClient.listTools()).thenReturn(Collections.emptyList());

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);
        verify(printer).printInfoMessage("No tools found");
    }

    @Test
    @DisplayName("Should return error on exception")
    void shouldReturnErrorOnException() throws Exception {
        when(mcpClient.listTools()).thenThrow(new RuntimeException("connection refused"));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printErrorMessage("connection refused");
    }
}
