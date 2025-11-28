package ai.wanaku.cli.main.commands.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ResourcesService;
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
public class ResourcesLabelAddTest {

    @Mock
    private ResourcesService resourcesService;

    private ResourcesLabelAdd command;

    @BeforeEach
    void setUp() {
        command = new ResourcesLabelAdd();
        command.resourcesService = resourcesService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Label Parsing Tests")
    class LabelParsingTests {

        @Test
        @DisplayName("Should parse valid label in key=value format")
        void shouldParseValidLabel() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference("test-resource", Map.of());
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("test-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "test-resource";
            command.labels = List.of("category=finance");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            assertEquals("finance", captor.getValue().getLabels().get("category"));
        }

        @Test
        @DisplayName("Should parse multiple labels")
        void shouldParseMultipleLabels() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference("test-resource", Map.of());
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("test-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "test-resource";
            command.labels = List.of("category=data", "year=2024", "department=sales");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals("data", labels.get("category"));
            assertEquals("2024", labels.get("year"));
            assertEquals("sales", labels.get("department"));
        }

        @Test
        @DisplayName("Should reject invalid label format")
        void shouldRejectInvalidLabelFormat() throws Exception {
            // Arrange
            command.name = "test-resource";
            command.labels = List.of("invalid-label");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(resourcesService, never()).update(any());
        }
    }

    @Nested
    @DisplayName("Single Resource Operations")
    class SingleResourceOperationsTests {

        @Test
        @DisplayName("Should add labels to existing resource by name")
        void shouldAddLabelsToExistingResource() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference("my-resource", Map.of("existing", "label"));
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("my-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "my-resource";
            command.labels = List.of("new=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(2, labels.size());
            assertEquals("label", labels.get("existing"));
            assertEquals("value", labels.get("new"));
        }

        @Test
        @DisplayName("Should update existing label value")
        void shouldUpdateExistingLabelValue() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference("my-resource", Map.of("year", "2023"));
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("my-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "my-resource";
            command.labels = List.of("year=2024");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            assertEquals("2024", captor.getValue().getLabels().get("year"));
        }

        @Test
        @DisplayName("Should handle resource not found")
        void shouldHandleResourceNotFound() throws Exception {
            // Arrange
            when(resourcesService.getByName("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.name = "nonexistent";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(resourcesService, never()).update(any());
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should add labels to multiple resources matching expression")
        void shouldAddLabelsToMultipleResources() throws Exception {
            // Arrange
            ResourceReference resource1 = createResourceReference("resource1", Map.of("category", "data"));
            ResourceReference resource2 = createResourceReference("resource2", Map.of("category", "data"));
            List<ResourceReference> matchingResources = List.of(resource1, resource2);
            WanakuResponse<List<ResourceReference>> listResponse = new WanakuResponse<>(matchingResources);
            Response updateResponse = mock(Response.class);

            when(resourcesService.list("category=data")).thenReturn(listResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.labelExpression = "category=data";
            command.labels = List.of("migrated=true");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(resourcesService, times(2)).update(any(ResourceReference.class));
        }

        @Test
        @DisplayName("Should handle no resources matching expression")
        void shouldHandleNoResourcesMatching() throws Exception {
            // Arrange
            WanakuResponse<List<ResourceReference>> listResponse = new WanakuResponse<>(List.of());

            when(resourcesService.list("category=nonexistent")).thenReturn(listResponse);

            command.labelExpression = "category=nonexistent";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success but no changes
            verify(resourcesService, never()).update(any());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject both name and label-expression")
        void shouldRejectBothNameAndExpression() throws Exception {
            // Arrange
            command.name = "test-resource";
            command.labelExpression = "category=test";
            command.labels = List.of("label=value");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(1, result); // EXIT_ERROR
            verify(resourcesService, never()).getByName(any());
            verify(resourcesService, never()).list(any());
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
            verify(resourcesService, never()).getByName(any());
            verify(resourcesService, never()).list(any());
        }
    }

    // Helper methods
    private ResourceReference createResourceReference(String name, Map<String, String> labels) {
        ResourceReference resource = new ResourceReference();
        resource.setName(name);
        resource.setLabels(new HashMap<>(labels));
        return resource;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
