package ai.wanaku.cli.main.commands.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ToolsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ToolsLabelAddTest {

    @Mock
    private ToolsService toolsService;

    private ToolsLabelAdd command;

    @BeforeEach
    void setUp() {
        command = new ToolsLabelAdd();
        command.toolsService = toolsService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Label Parsing Tests")
    class LabelParsingTests {

        @Test
        @DisplayName("Should parse valid label in key=value format")
        void shouldParseValidLabel() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("test-tool", Map.of());
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("test-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "test-tool";
            command.labels = List.of("env=production");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            assertEquals("production", captor.getValue().getLabels().get("env"));
        }

        @Test
        @DisplayName("Should parse multiple labels")
        void shouldParseMultipleLabels() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("test-tool", Map.of());
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("test-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "test-tool";
            command.labels = List.of("env=production", "tier=backend", "version=2.0");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals("production", labels.get("env"));
            assertEquals("backend", labels.get("tier"));
            assertEquals("2.0", labels.get("version"));
        }

        @Test
        @DisplayName("Should reject invalid label format")
        void shouldRejectInvalidLabelFormat() throws Exception {
            // Arrange
            command.name = "test-tool";
            command.labels = List.of("invalid-label-without-equals");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(toolsService, never()).update(any());
        }

        @Test
        @DisplayName("Should handle labels with equals sign in value")
        void shouldHandleLabelsWithEqualsInValue() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("test-tool", Map.of());
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("test-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "test-tool";
            command.labels = List.of("config=key=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            assertEquals("key=value", captor.getValue().getLabels().get("config"));
        }
    }

    @Nested
    @DisplayName("Single Tool Operations")
    class SingleToolOperationsTests {

        @Test
        @DisplayName("Should add labels to existing tool by name")
        void shouldAddLabelsToExistingTool() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("my-tool", Map.of("existing", "label"));
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "my-tool";
            command.labels = List.of("new=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(2, labels.size());
            assertEquals("label", labels.get("existing"));
            assertEquals("value", labels.get("new"));
        }

        @Test
        @DisplayName("Should update existing label value")
        void shouldUpdateExistingLabelValue() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("my-tool", Map.of("env", "dev"));
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "my-tool";
            command.labels = List.of("env=production");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            assertEquals("production", captor.getValue().getLabels().get("env"));
        }

        @Test
        @DisplayName("Should handle tool not found")
        void shouldHandleToolNotFound() throws Exception {
            // Arrange
            when(toolsService.getByName("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.name = "nonexistent";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(toolsService, never()).update(any());
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should add labels to multiple tools matching expression")
        void shouldAddLabelsToMultipleTools() throws Exception {
            // Arrange
            ToolReference tool1 = createToolReference("tool1", Map.of("category", "weather"));
            ToolReference tool2 = createToolReference("tool2", Map.of("category", "weather"));
            List<ToolReference> matchingTools = List.of(tool1, tool2);
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(matchingTools);
            Response updateResponse = mock(Response.class);

            when(toolsService.list("category=weather")).thenReturn(listResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.labelExpression = "category=weather";
            command.labels = List.of("migrated=true");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(toolsService, times(2)).update(any(ToolReference.class));
        }

        @Test
        @DisplayName("Should handle no tools matching expression")
        void shouldHandleNoToolsMatching() throws Exception {
            // Arrange
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(List.of());

            when(toolsService.list("category=nonexistent")).thenReturn(listResponse);

            command.labelExpression = "category=nonexistent";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success but no changes
            verify(toolsService, never()).update(any());
        }

        @Test
        @DisplayName("Should continue on partial failures")
        void shouldContinueOnPartialFailures() throws Exception {
            // Arrange
            ToolReference tool1 = createToolReference("tool1", Map.of());
            ToolReference tool2 = createToolReference("tool2", Map.of());
            List<ToolReference> matchingTools = List.of(tool1, tool2);
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(matchingTools);
            Response updateResponse = mock(Response.class);

            when(toolsService.list(anyString())).thenReturn(listResponse);
            when(toolsService.update(eq(tool1))).thenReturn(updateResponse);
            when(toolsService.update(eq(tool2))).thenThrow(new WebApplicationException());

            command.labelExpression = "category=test";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR due to one failure
            verify(toolsService, times(2)).update(any(ToolReference.class));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject both name and label-expression")
        void shouldRejectBothNameAndExpression() throws Exception {
            // Arrange
            command.name = "test-tool";
            command.labelExpression = "category=test";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(toolsService, never()).getByName(any());
            verify(toolsService, never()).list(any());
        }

        @Test
        @DisplayName("Should reject neither name nor label-expression")
        void shouldRejectNeitherNameNorExpression() throws Exception {
            // Arrange
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(toolsService, never()).getByName(any());
            verify(toolsService, never()).list(any());
        }
    }

    // Helper methods
    private ToolReference createToolReference(String name, Map<String, String> labels) {
        ToolReference tool = new ToolReference();
        tool.setName(name);
        tool.setLabels(new HashMap<>(labels));
        return tool;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
