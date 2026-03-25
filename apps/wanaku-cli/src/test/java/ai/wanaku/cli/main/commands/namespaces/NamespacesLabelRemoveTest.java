package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespacesLabelRemoveTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesLabelRemove command;

    @BeforeEach
    void setUp() {
        command = new NamespacesLabelRemove();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Single Namespace Operations")
    class SingleNamespaceOperationsTests {

        @Test
        @DisplayName("Should remove single label from namespace")
        void shouldRemoveSingleLabel() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("my-id", Map.of("env", "dev", "tier", "backend"));
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("env");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertFalse(labels.containsKey("env"));
            assertEquals("backend", labels.get("tier"));
        }

        @Test
        @DisplayName("Should remove multiple labels from namespace")
        void shouldRemoveMultipleLabels() throws Exception {
            // Arrange
            Namespace existingNamespace =
                    createNamespace("my-id", Map.of("env", "dev", "tier", "backend", "version", "1.0"));
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("env", "version");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertEquals("backend", labels.get("tier"));
        }

        @Test
        @DisplayName("Should handle removing non-existent label")
        void shouldHandleNonExistentLabel() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("my-id", Map.of("env", "dev"));
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("nonexistent");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success even though label didn't exist
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            // Verify the existing label is still there and nonexistent was not added
            assertEquals(1, captor.getValue().getLabels().size());
            assertEquals("dev", captor.getValue().getLabels().get("env"));
        }

        @Test
        @DisplayName("Should handle namespace with null labels")
        void shouldHandleNamespaceWithNullLabels() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("my-id", null);
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("env");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            // Verify empty map was created
            assertNotNull(captor.getValue().getLabels());
            assertTrue(captor.getValue().getLabels().isEmpty());
        }

        @Test
        @DisplayName("Should handle namespace not found")
        void shouldHandleNamespaceNotFound() throws Exception {
            // Arrange
            when(namespacesService.getById("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.id = "nonexistent";
            command.labels = List.of("label");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(namespacesService, never()).update(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should remove labels from multiple namespaces matching expression")
        void shouldRemoveLabelsFromMultipleNamespaces() throws Exception {
            // Arrange
            Namespace namespace1 = createNamespace("id1", Map.of("status", "temp", "env", "dev"));
            Namespace namespace2 = createNamespace("id2", Map.of("status", "temp", "tier", "backend"));
            List<Namespace> matchingNamespaces = List.of(namespace1, namespace2);
            WanakuResponse<List<Namespace>> listResponse = new WanakuResponse<>(matchingNamespaces);
            Response updateResponse = mock(Response.class);

            when(namespacesService.list("status=temp")).thenReturn(listResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.labelExpression = "status=temp";
            command.labels = List.of("status");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(namespacesService, times(2)).update(anyString(), any(Namespace.class));
        }

        @Test
        @DisplayName("Should handle no namespaces matching expression")
        void shouldHandleNoNamespacesMatching() throws Exception {
            // Arrange
            WanakuResponse<List<Namespace>> listResponse = new WanakuResponse<>(List.of());

            when(namespacesService.list("category=nonexistent")).thenReturn(listResponse);

            command.labelExpression = "category=nonexistent";
            command.labels = List.of("label");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success but no changes
            verify(namespacesService, never()).update(anyString(), any());
        }

        @Test
        @DisplayName("Should continue on partial failures")
        void shouldContinueOnPartialFailures() throws Exception {
            // Arrange
            Namespace namespace1 = createNamespace("id1", Map.of("env", "dev"));
            Namespace namespace2 = createNamespace("id2", Map.of("env", "dev"));
            List<Namespace> matchingNamespaces = List.of(namespace1, namespace2);
            WanakuResponse<List<Namespace>> listResponse = new WanakuResponse<>(matchingNamespaces);
            Response updateResponse = mock(Response.class);

            when(namespacesService.list(anyString())).thenReturn(listResponse);
            when(namespacesService.update(anyString(), eq(namespace1))).thenReturn(updateResponse);
            when(namespacesService.update(anyString(), eq(namespace2))).thenThrow(new WebApplicationException());

            command.labelExpression = "env=dev";
            command.labels = List.of("env");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR due to one failure
            verify(namespacesService, times(2)).update(anyString(), any(Namespace.class));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject both id and label-expression")
        void shouldRejectBothIdAndExpression() throws Exception {
            // Arrange
            command.id = "test-id";
            command.labelExpression = "category=test";
            command.labels = List.of("label");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(namespacesService, never()).getById(any());
            verify(namespacesService, never()).list(any());
        }

        @Test
        @DisplayName("Should reject neither id nor label-expression")
        void shouldRejectNeitherIdNorExpression() throws Exception {
            // Arrange
            command.labels = List.of("label");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(namespacesService, never()).getById(any());
            verify(namespacesService, never()).list(any());
        }
    }

    // Helper methods
    private Namespace createNamespace(String id, Map<String, String> labels) {
        Namespace namespace = new Namespace();
        namespace.setId(id);
        if (labels != null) {
            namespace.setLabels(new HashMap<>(labels));
        }
        return namespace;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
