package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ResourcesService;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ResourcesLabelRemoveTest {

    @Mock
    private ResourcesService resourcesService;

    private ResourcesLabelRemove command;

    @BeforeEach
    void setUp() {
        command = new ResourcesLabelRemove();
        command.resourcesService = resourcesService;
        command.host = "http://localhost:8080";
    }

    @Nested
    @DisplayName("Single Resource Operations")
    class SingleResourceOperationsTests {

        @Test
        @DisplayName("Should remove single label from resource")
        void shouldRemoveSingleLabel() throws Exception {
            // Arrange
            ResourceReference existingResource =
                    createResourceReference("my-resource", Map.of("year", "2024", "category", "data"));
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("my-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "my-resource";
            command.labelKeys = List.of("year");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertFalse(labels.containsKey("year"));
            assertEquals("data", labels.get("category"));
        }

        @Test
        @DisplayName("Should remove multiple labels from resource")
        void shouldRemoveMultipleLabels() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference(
                    "my-resource", Map.of("year", "2024", "category", "data", "department", "sales"));
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);
            Response updateResponse = mock(Response.class);

            when(resourcesService.getByName("my-resource")).thenReturn(getResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.name = "my-resource";
            command.labelKeys = List.of("year", "department");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            ArgumentCaptor<ResourceReference> captor = ArgumentCaptor.forClass(ResourceReference.class);
            verify(resourcesService).update(captor.capture());
            Map<String, String> labels = captor.getValue().getLabels();
            assertEquals(1, labels.size());
            assertEquals("data", labels.get("category"));
        }

        @Test
        @DisplayName("Should handle removing non-existent label")
        void shouldHandleNonExistentLabel() throws Exception {
            // Arrange
            ResourceReference existingResource = createResourceReference("my-resource", Map.of("category", "data"));
            WanakuResponse<ResourceReference> getResponse = new WanakuResponse<>(existingResource);

            when(resourcesService.getByName("my-resource")).thenReturn(getResponse);

            command.name = "my-resource";
            command.labelKeys = List.of("nonexistent");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result); // Success even though label didn't exist
            verify(resourcesService, never()).update(any()); // No update needed
        }

        @Test
        @DisplayName("Should handle resource not found")
        void shouldHandleResourceNotFound() throws Exception {
            // Arrange
            when(resourcesService.getByName("nonexistent")).thenReturn(new WanakuResponse<>(null));

            command.name = "nonexistent";
            command.labelKeys = List.of("label");

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
        @DisplayName("Should remove labels from multiple resources matching expression")
        void shouldRemoveLabelsFromMultipleResources() throws Exception {
            // Arrange
            ResourceReference resource1 =
                    createResourceReference("resource1", Map.of("status", "archived", "year", "2023"));
            ResourceReference resource2 =
                    createResourceReference("resource2", Map.of("status", "archived", "category", "data"));
            List<ResourceReference> matchingResources = List.of(resource1, resource2);
            WanakuResponse<List<ResourceReference>> listResponse = new WanakuResponse<>(matchingResources);
            Response updateResponse = mock(Response.class);

            when(resourcesService.list("status=archived")).thenReturn(listResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.labelExpression = "status=archived";
            command.labelKeys = List.of("status");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(resourcesService, times(2)).update(any(ResourceReference.class));
        }

        @Test
        @DisplayName("Should skip resources with no labels to remove")
        void shouldSkipResourcesWithNoLabelsToRemove() throws Exception {
            // Arrange
            ResourceReference resource1 = createResourceReference("resource1", Map.of("category", "data"));
            ResourceReference resource2 =
                    createResourceReference("resource2", Map.of("status", "active")); // has label to remove
            List<ResourceReference> matchingResources = List.of(resource1, resource2);
            WanakuResponse<List<ResourceReference>> listResponse = new WanakuResponse<>(matchingResources);
            Response updateResponse = mock(Response.class);

            when(resourcesService.list(anyString())).thenReturn(listResponse);
            when(resourcesService.update(any(ResourceReference.class))).thenReturn(updateResponse);

            command.labelExpression = "category=data|status=active";
            command.labelKeys = List.of("status");

            // Act
            Integer result = command.doCall(null, createMockPrinter());

            // Assert
            assertEquals(0, result);
            verify(resourcesService, times(1)).update(any(ResourceReference.class)); // Only resource2 updated
        }

        @Test
        @DisplayName("Should handle no resources matching expression")
        void shouldHandleNoResourcesMatching() throws Exception {
            // Arrange
            WanakuResponse<List<ResourceReference>> listResponse = new WanakuResponse<>(List.of());

            when(resourcesService.list("category=nonexistent")).thenReturn(listResponse);

            command.labelExpression = "category=nonexistent";
            command.labelKeys = List.of("label");

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
            command.labelKeys = List.of("label");

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
            command.labelKeys = List.of("label");

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
        if (labels != null) {
            resource.setLabels(new HashMap<>(labels));
        }
        return resource;
    }

    private ai.wanaku.cli.main.support.WanakuPrinter createMockPrinter() {
        return mock(ai.wanaku.cli.main.support.WanakuPrinter.class);
    }
}
