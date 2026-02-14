package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ToolsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ToolsLabelRemoveTest {

    @Mock
    private ToolsService toolsService;

    private ToolsLabelRemove command;

    @BeforeEach
    void setUp() {
        command = new ToolsLabelRemove();
        command.toolsService = toolsService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Single Tool Operations")
    class SingleToolOperationsTests {

        @Test
        @DisplayName("Should remove single label from tool")
        void shouldRemoveSingleLabel() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("my-tool", Map.of("env", "dev", "tier", "backend"));
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "my-tool";
            command.labelKeys = List.of("env");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertFalse(labels.containsKey("env"));
            assertEquals("backend", labels.get("tier"));
        }

        @Test
        @DisplayName("Should remove multiple labels from tool")
        void shouldRemoveMultipleLabels() throws Exception {
            // Arrange
            ToolReference existingTool =
                    createToolReference("my-tool", Map.of("env", "dev", "tier", "backend", "version", "1.0"));
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);
            Response updateResponse = mock(Response.class);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.name = "my-tool";
            command.labelKeys = List.of("env", "version");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ToolReference> captor = ArgumentCaptor.forClass(ToolReference.class);
            verify(toolsService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertEquals("backend", labels.get("tier"));
        }

        @Test
        @DisplayName("Should handle removing non-existent label")
        void shouldHandleNonExistentLabel() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("my-tool", Map.of("env", "dev"));
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);

            command.name = "my-tool";
            command.labelKeys = List.of("nonexistent");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success even though label didn't exist
            verify(toolsService, never()).update(any()); // No update needed
        }

        @Test
        @DisplayName("Should handle tool with null labels")
        void shouldHandleToolWithNullLabels() throws Exception {
            // Arrange
            ToolReference existingTool = createToolReference("my-tool", null);
            WanakuResponse<ToolReference> getResponse = new WanakuResponse<>(existingTool);

            when(toolsService.getByName("my-tool")).thenReturn(getResponse);

            command.name = "my-tool";
            command.labelKeys = List.of("env");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(toolsService, never()).update(any());
        }

        @Test
        @DisplayName("Should handle tool not found")
        void shouldHandleToolNotFound() throws Exception {
            // Arrange
            when(toolsService.getByName("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.name = "nonexistent";
            command.labelKeys = List.of("label");

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
        @DisplayName("Should remove labels from multiple tools matching expression")
        void shouldRemoveLabelsFromMultipleTools() throws Exception {
            // Arrange
            ToolReference tool1 = createToolReference("tool1", Map.of("status", "deprecated", "env", "dev"));
            ToolReference tool2 = createToolReference("tool2", Map.of("status", "deprecated", "tier", "backend"));
            List<ToolReference> matchingTools = List.of(tool1, tool2);
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(matchingTools);
            Response updateResponse = mock(Response.class);

            when(toolsService.list("status=deprecated")).thenReturn(listResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.labelExpression = "status=deprecated";
            command.labelKeys = List.of("status");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(toolsService, times(2)).update(any(ToolReference.class));
        }

        @Test
        @DisplayName("Should skip tools with no labels to remove")
        void shouldSkipToolsWithNoLabelsToRemove() throws Exception {
            // Arrange
            ToolReference tool1 = createToolReference("tool1", Map.of("env", "dev"));
            ToolReference tool2 = createToolReference("tool2", Map.of("status", "active")); // has label to remove
            ToolReference tool3 = createToolReference("tool3", Map.of("tier", "backend"));
            List<ToolReference> matchingTools = List.of(tool1, tool2, tool3);
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(matchingTools);
            Response updateResponse = mock(Response.class);

            when(toolsService.list(anyString())).thenReturn(listResponse);
            when(toolsService.update(any(ToolReference.class))).thenReturn(updateResponse);

            command.labelExpression = "env=dev|status=active|tier=backend";
            command.labelKeys = List.of("status");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(toolsService, times(1)).update(any(ToolReference.class)); // Only tool2 updated
        }

        @Test
        @DisplayName("Should handle no tools matching expression")
        void shouldHandleNoToolsMatching() throws Exception {
            // Arrange
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(List.of());

            when(toolsService.list("category=nonexistent")).thenReturn(listResponse);

            command.labelExpression = "category=nonexistent";
            command.labelKeys = List.of("label");

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
            ToolReference tool1 = createToolReference("tool1", Map.of("env", "dev"));
            ToolReference tool2 = createToolReference("tool2", Map.of("env", "dev"));
            List<ToolReference> matchingTools = List.of(tool1, tool2);
            WanakuResponse<List<ToolReference>> listResponse = new WanakuResponse<>(matchingTools);
            Response updateResponse = mock(Response.class);

            when(toolsService.list(anyString())).thenReturn(listResponse);
            when(toolsService.update(eq(tool1))).thenReturn(updateResponse);
            when(toolsService.update(eq(tool2))).thenThrow(new WebApplicationException());

            command.labelExpression = "env=dev";
            command.labelKeys = List.of("env");

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
            command.labelKeys = List.of("label");

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
            command.labelKeys = List.of("label");

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
        if (labels != null) {
            tool.setLabels(new HashMap<>(labels));
        }
        return tool;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
