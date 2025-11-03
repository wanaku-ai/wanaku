package ai.wanaku.cli.main.commands.namespaces;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;
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
public class NamespacesLabelAddTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesLabelAdd command;

    @BeforeEach
    void setUp() {
        command = new NamespacesLabelAdd();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Label Parsing Tests")
    class LabelParsingTests {

        @Test
        @DisplayName("Should parse valid label in key=value format")
        void shouldParseValidLabel() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("test-id", Map.of());
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("test-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "test-id";
            command.labels = List.of("env=production");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            assertEquals("production", captor.getValue().getLabels().get("env"));
        }

        @Test
        @DisplayName("Should parse multiple labels")
        void shouldParseMultipleLabels() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("test-id", Map.of());
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("test-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "test-id";
            command.labels = List.of("env=production", "tier=backend", "version=2.0");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals("production", labels.get("env"));
            assertEquals("backend", labels.get("tier"));
            assertEquals("2.0", labels.get("version"));
        }

        @Test
        @DisplayName("Should reject invalid label format")
        void shouldRejectInvalidLabelFormat() throws Exception {
            // Arrange
            command.id = "test-id";
            command.labels = List.of("invalid-label-without-equals");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(namespacesService, never()).update(anyString(), any());
        }

        @Test
        @DisplayName("Should handle labels with equals sign in value")
        void shouldHandleLabelsWithEqualsInValue() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("test-id", Map.of());
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("test-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "test-id";
            command.labels = List.of("config=key=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            assertEquals("key=value", captor.getValue().getLabels().get("config"));
        }
    }

    @Nested
    @DisplayName("Single Namespace Operations")
    class SingleNamespaceOperationsTests {

        @Test
        @DisplayName("Should add labels to existing namespace by ID")
        void shouldAddLabelsToExistingNamespace() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("my-id", Map.of("existing", "label"));
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("new=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(2, labels.size());
            assertEquals("label", labels.get("existing"));
            assertEquals("value", labels.get("new"));
        }

        @Test
        @DisplayName("Should update existing label value")
        void shouldUpdateExistingLabelValue() throws Exception {
            // Arrange
            Namespace existingNamespace = createNamespace("my-id", Map.of("env", "dev"));
            WanakuResponse<Namespace> getResponse = new WanakuResponse<>(existingNamespace);
            Response updateResponse = mock(Response.class);

            when(namespacesService.getById("my-id")).thenReturn(getResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.id = "my-id";
            command.labels = List.of("env=production");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
            verify(namespacesService).update(anyString(), captor.capture());
            assertEquals("production", captor.getValue().getLabels().get("env"));
        }

        @Test
        @DisplayName("Should handle namespace not found")
        void shouldHandleNamespaceNotFound() throws Exception {
            // Arrange
            when(namespacesService.getById("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.id = "nonexistent";
            command.labels = List.of("label=value");

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
        @DisplayName("Should add labels to multiple namespaces matching expression")
        void shouldAddLabelsToMultipleNamespaces() throws Exception {
            // Arrange
            Namespace namespace1 = createNamespace("id1", Map.of("category", "internal"));
            Namespace namespace2 = createNamespace("id2", Map.of("category", "internal"));
            List<Namespace> matchingNamespaces = List.of(namespace1, namespace2);
            WanakuResponse<List<Namespace>> listResponse = new WanakuResponse<>(matchingNamespaces);
            Response updateResponse = mock(Response.class);

            when(namespacesService.list("category=internal")).thenReturn(listResponse);
            when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(updateResponse);

            command.labelExpression = "category=internal";
            command.labels = List.of("migrated=true");

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
            command.labels = List.of("label=value");

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
            Namespace namespace1 = createNamespace("id1", Map.of());
            Namespace namespace2 = createNamespace("id2", Map.of());
            List<Namespace> matchingNamespaces = List.of(namespace1, namespace2);
            WanakuResponse<List<Namespace>> listResponse = new WanakuResponse<>(matchingNamespaces);
            Response updateResponse = mock(Response.class);

            when(namespacesService.list(anyString())).thenReturn(listResponse);
            when(namespacesService.update(anyString(), eq(namespace1))).thenReturn(updateResponse);
            when(namespacesService.update(anyString(), eq(namespace2))).thenThrow(new WebApplicationException());

            command.labelExpression = "category=test";
            command.labels = List.of("label=value");

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
            command.labels = List.of("label=value");

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
            command.labels = List.of("label=value");

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
        namespace.setLabels(new HashMap<>(labels));
        return namespace;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
