package ai.wanaku.cli.main.commands.tools;

import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.BODY;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.addHttpMethodProperty;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.determineBaseUrl;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.getOutputPrintWriter;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.interpolateServerUrl;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.operation2ToolReference;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.parameter2Property;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.parameters2InputSchema;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.pathItem2ToolReferences;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.toolReferenceUrl;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_COOKIE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ToolsGenerateSupportTest {

    private static final String HTTP_TOOL_TYPE = "http";
    private static final String DEFAULT_OUTPUT_FILENAME = "out.json";

    @Mock
    private OpenAPI openAPI;

    @Mock
    private Operation operation;

    @Mock
    private PathItem pathItem;

    @Nested
    @DisplayName("interpolateServerUrl tests")
    class InterpolateServerUrlTests {
        @Test
        @DisplayName("Should replace variables in URL")
        void shouldReplaceVariablesInUrl() {
            // Arrange
            Server server = new Server();
            server.setUrl("https://{environment}.example.com/{region}/api");

            ServerVariables serverVariables = new ServerVariables();

            ServerVariable envVar = new ServerVariable();
            envVar.setDefault("dev");
            serverVariables.put("environment", envVar);

            ServerVariable regionVar = new ServerVariable();
            regionVar.setDefault("us-east");
            serverVariables.put("region", regionVar);

            server.setVariables(serverVariables);

            Map<String, String> variables = new HashMap<>();
            variables.put("environment", "prod");
            variables.put("region", "eu-west");

            // Act
            String result = interpolateServerUrl(server, variables);

            // Assert
            assertEquals("https://prod.example.com/eu-west/api", result);
        }

        @Test
        @DisplayName("Should use default values when variables not provided")
        void shouldUseDefaultValuesWhenVariablesNotProvided() {
            // Arrange
            Server server = new Server();
            server.setUrl("https://{environment}.example.com/{region}/api");

            ServerVariables serverVariables = new ServerVariables();

            ServerVariable envVar = new ServerVariable();
            envVar.setDefault("dev");
            serverVariables.put("environment", envVar);

            ServerVariable regionVar = new ServerVariable();
            regionVar.setDefault("us-east");
            serverVariables.put("region", regionVar);

            server.setVariables(serverVariables);

            Map<String, String> variables = new HashMap<>();
            // No variables provided

            // Act
            String result = interpolateServerUrl(server, variables);

            // Assert
            assertEquals("https://dev.example.com/us-east/api", result);
        }

        @Test
        @DisplayName("Should return URL as is when no variables")
        void shouldReturnUrlAsIsWhenNoVariables() {
            // Arrange
            Server server = new Server();
            server.setUrl("https://example.com/api");

            Map<String, String> variables = new HashMap<>();

            // Act
            String result = interpolateServerUrl(server, variables);

            // Assert
            assertEquals("https://example.com/api", result);
        }

        @Test
        @DisplayName("Should handle empty variables map")
        void shouldHandleEmptyVariablesMap() {
            // Arrange
            Server server = new Server();
            server.setUrl("https://{environment}.example.com/api");

            ServerVariables serverVariables = new ServerVariables();
            ServerVariable envVar = new ServerVariable();
            envVar.setDefault("dev");
            serverVariables.put("environment", envVar);
            server.setVariables(serverVariables);

            // Act
            String result = interpolateServerUrl(server, null);

            // Assert
            assertEquals("https://dev.example.com/api", result);
        }
    }

    @Nested
    @DisplayName("toolReferenceUrl tests")
    class ToolReferenceUrlTests {
        @Test
        @DisplayName("Should replace placeholders with parameter values")
        void shouldReplacePlaceholdersWithParameterValues() {
            // Arrange
            String baseUrl = "https://api.example.com";
            String path = "/users/{userId}/posts/{postId}";

            // Act
            String result = toolReferenceUrl(baseUrl, path);

            // Assert
            assertEquals(
                    "https://api.example.com/users/{parameter.value('userId')}/posts/{parameter.value('postId')}",
                    result);
        }

        @Test
        @DisplayName("Should handle path with no placeholders")
        void shouldHandlePathWithNoPlaceholders() {
            // Arrange
            String baseUrl = "https://api.example.com";
            String path = "/users/all";

            // Act
            String result = toolReferenceUrl(baseUrl, path);

            // Assert
            assertEquals("https://api.example.com/users/all", result);
        }
    }

    @Nested
    @DisplayName("parameter2Property tests")
    class Parameter2PropertyTests {
        @Test
        @DisplayName("Should convert header parameter")
        void shouldConvertHeaderParameter() {
            // Arrange
            Parameter parameter = new Parameter();
            parameter.setName("X-API-Key");
            parameter.setIn("header");
            parameter.setDescription("API Key for authentication");
            parameter.setRequired(true);

            // Act
            Property property = parameter2Property(parameter);

            // Assert
            assertEquals("API Key for authentication", property.getDescription());
            assertEquals("string", property.getType());
            assertEquals(SCOPE_SERVICE, property.getScope());
            assertEquals(TARGET_HEADER, property.getTarget());
        }

        @Test
        @DisplayName("Should convert cookie parameter")
        void shouldConvertCookieParameter() {
            // Arrange
            Parameter parameter = new Parameter();
            parameter.setName("sessionId");
            parameter.setIn("cookie");
            parameter.setDescription("Session ID");

            // Act
            Property property = parameter2Property(parameter);

            // Assert
            assertEquals("Session ID", property.getDescription());
            assertEquals(TARGET_COOKIE, property.getTarget());
        }

        @Test
        @DisplayName("Should convert query parameter")
        void shouldConvertQueryParameter() {
            // Arrange
            Parameter parameter = new Parameter();
            parameter.setName("limit");
            parameter.setIn("query");
            parameter.setDescription("Max number of results");

            // Act
            Property property = parameter2Property(parameter);

            // Assert
            assertEquals("Max number of results", property.getDescription());
            assertNull(property.getTarget()); // No special target for query params
        }
    }

    @Nested
    @DisplayName("parameters2InputSchema tests")
    class Parameters2InputSchemaTests {
        @Test
        @DisplayName("Should create input schema from parameters")
        void shouldCreateInputSchemaFromParameters() {
            // Arrange
            List<Parameter> parameters = new ArrayList<>();

            Parameter param1 = new Parameter();
            param1.setName("userId");
            param1.setIn("path");
            param1.setRequired(true);
            parameters.add(param1);

            Parameter param2 = new Parameter();
            param2.setName("format");
            param2.setIn("query");
            param2.setRequired(false);
            parameters.add(param2);

            // Act
            InputSchema schema = parameters2InputSchema(parameters, null);

            // Assert
            assertEquals("object", schema.getType());
            assertEquals(2, schema.getProperties().size());
            assertTrue(schema.getProperties().containsKey("userId"));
            assertTrue(schema.getProperties().containsKey("format"));
            assertTrue(schema.getRequired().contains("userId"));
            assertFalse(schema.getRequired().contains("format"));
        }

        @Test
        @DisplayName("Should handle request body")
        void shouldHandleRequestBody() {
            // Arrange
            RequestBody requestBody = new RequestBody();
            requestBody.setRequired(true);

            // Act
            InputSchema schema = parameters2InputSchema(List.of(), requestBody);

            // Assert
            assertTrue(schema.getProperties().containsKey(BODY));
            assertEquals("string", schema.getProperties().get(BODY).getType());
            assertTrue(schema.getRequired().contains(BODY));
        }

        @Test
        @DisplayName("Should handle empty parameters and no request body")
        void shouldHandleEmptyParametersAndNoRequestBody() {
            // Act
            InputSchema schema = parameters2InputSchema(List.of(), null);

            // Assert
            assertTrue(schema.getProperties().isEmpty());
            assertTrue(schema.getRequired().isEmpty());
        }
    }

    @Nested
    @DisplayName("addHttpMethodProperty tests")
    class AddHttpMethodPropertyTests {
        @Test
        @DisplayName("Should add HTTP method property to input schema")
        void shouldAddHttpMethodPropertyToInputSchema() {
            // Arrange
            InputSchema inputSchema = new InputSchema();
            inputSchema.setProperties(new HashMap<>());
            String method = "POST";

            // Act
            addHttpMethodProperty(inputSchema, method);

            // Assert
            assertTrue(inputSchema.getProperties().containsKey("CamelHttpMethod"));
            Property property = inputSchema.getProperties().get("CamelHttpMethod");
            assertEquals(TARGET_HEADER, property.getTarget());
            assertEquals("string", property.getType());
            assertEquals(SCOPE_SERVICE, property.getScope());
            assertEquals("POST", property.getValue());
        }
    }

    @Nested
    @DisplayName("pathItem2ToolReferences tests")
    class PathItem2ToolReferencesTests {
        @Test
        @DisplayName("Should convert PathItem to ToolReferences")
        void shouldConvertPathItemToToolReferences() {
            // Arrange
            PathItem pathItem = new PathItem();
            Operation getOp = new Operation();
            getOp.setOperationId("getUsers");
            pathItem.setGet(getOp);

            Operation postOp = new Operation();
            postOp.setOperationId("createUser");
            pathItem.setPost(postOp);

            String baseUrl = "https://api.example.com";
            String path = "/users";

            // Act
            List<ToolReference> references = pathItem2ToolReferences(baseUrl, path, pathItem, Map.of());

            // Assert
            assertEquals(2, references.size());
        }

        @Test
        @DisplayName("Should handle null PathItem")
        void shouldHandleNullPathItem() {
            // Act
            List<ToolReference> references = pathItem2ToolReferences("baseUrl", "path", null, Map.of());

            // Assert
            assertTrue(references.isEmpty());
        }

        @Test
        @DisplayName("Should handle PathItem with no operations")
        void shouldHandlePathItemWithNoOperations() {
            // Arrange
            PathItem pathItem = new PathItem();

            // Act
            List<ToolReference> references = pathItem2ToolReferences("baseUrl", "path", pathItem, Map.of());

            // Assert
            assertTrue(references.isEmpty());
        }
    }

    @Nested
    @DisplayName("operation2ToolReference tests")
    class Operation2ToolReferenceTests {
        @Test
        @DisplayName("Should convert Operation to ToolReference")
        void shouldConvertOperationToToolReference() {
            // Arrange
            Operation operation = new Operation();
            operation.setOperationId("getUserById");
            operation.setDescription("Get a user by their ID");

            PathItem pathItem = new PathItem();

            String baseUrl = "https://api.example.com";
            String path = "/users/{userId}";
            String method = "GET";

            // Act
            ToolReference toolRef = operation2ToolReference(pathItem, operation, baseUrl, path, method, Map.of());

            // Assert
            assertEquals("getUserById", toolRef.getName());
            assertEquals("Get a user by their ID", toolRef.getDescription());
            assertEquals(HTTP_TOOL_TYPE, toolRef.getType());
            assertEquals("https://api.example.com/users/{parameter.value('userId')}", toolRef.getUri());
            assertNotNull(toolRef.getInputSchema());
            assertTrue(toolRef.getInputSchema().getProperties().containsKey("CamelHttpMethod"));
        }

        @Test
        @DisplayName("Should add labels to ToolReference")
        void shouldAddLabelsToToolReference() {
            // Arrange
            Operation operation = new Operation();
            operation.setOperationId("getUserById");
            operation.setDescription("Get a user by their ID");

            PathItem pathItem = new PathItem();

            String baseUrl = "https://api.example.com";
            String path = "/users/{userId}";
            String method = "GET";

            Map<String, String> labels = new HashMap<>();
            labels.put("environment", "production");
            labels.put("team", "backend");
            labels.put("version", "v1");

            // Act
            ToolReference toolRef = operation2ToolReference(pathItem, operation, baseUrl, path, method, labels);

            // Assert
            assertEquals("getUserById", toolRef.getName());
            assertNotNull(toolRef.getLabels());
            assertEquals(3, toolRef.getLabels().size());
            assertEquals("production", toolRef.getLabelValue("environment"));
            assertEquals("backend", toolRef.getLabelValue("team"));
            assertEquals("v1", toolRef.getLabelValue("version"));
        }

        @Test
        @DisplayName("Should handle empty labels map")
        void shouldHandleEmptyLabelsMap() {
            // Arrange
            Operation operation = new Operation();
            operation.setOperationId("getUserById");

            PathItem pathItem = new PathItem();

            String baseUrl = "https://api.example.com";
            String path = "/users/{userId}";
            String method = "GET";

            // Act
            ToolReference toolRef = operation2ToolReference(pathItem, operation, baseUrl, path, method, Map.of());

            // Assert
            assertNotNull(toolRef);
            assertTrue(toolRef.getLabels().isEmpty());
        }

        @Test
        @DisplayName("Should handle null labels map")
        void shouldHandleNullLabelsMap() {
            // Arrange
            Operation operation = new Operation();
            operation.setOperationId("getUserById");

            PathItem pathItem = new PathItem();

            String baseUrl = "https://api.example.com";
            String path = "/users/{userId}";
            String method = "GET";

            // Act
            ToolReference toolRef = operation2ToolReference(pathItem, operation, baseUrl, path, method, null);

            // Assert
            assertNotNull(toolRef);
            assertNotNull(toolRef.getLabels());
            assertTrue(toolRef.getLabels().isEmpty());
        }
    }

    @Nested
    @DisplayName("getOutputPrintWriter tests")
    class GetOutputPrintWriterTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Should return System.out when output is null")
        void shouldReturnSystemOutWhenOutputIsNull() throws Exception {
            // Act
            try (PrintWriter writer = getOutputPrintWriter(null)) {

                // Assert
                assertNotNull(writer);
            }
        }

        @Test
        @DisplayName("Should use default filename when output is a directory")
        void shouldUseDefaultFilenameWhenOutputIsDirectory() throws Exception {
            // Arrange
            String dirPath = tempDir.toString();

            // Act
            try (PrintWriter writer = getOutputPrintWriter(dirPath)) {

                // Assert
                assertNotNull(writer);
                assertTrue(Files.exists(tempDir.resolve(DEFAULT_OUTPUT_FILENAME)));
            }
        }

        @Test
        @DisplayName("Should throw exception when file already exists")
        void shouldThrowExceptionWhenFileAlreadyExists() throws Exception {
            // Arrange
            Path filePath = tempDir.resolve("existing.json");
            Files.createFile(filePath);

            // Act & Assert
            Exception exception = assertThrows(Exception.class, () -> {
                getOutputPrintWriter(filePath.toString());
            });

            assertTrue(exception.getMessage().contains("already exists"));
        }
    }

    @Nested
    @DisplayName("determineBaseUrl tests")
    class DetermineBaseUrlTests {
        @Test
        @DisplayName("Should use serverUrl when provided")
        void shouldUseServerUrlWhenProvided() {
            // Arrange
            String serverUrl = "https://custom.example.com";

            // Act
            String result = determineBaseUrl(openAPI, serverUrl, null, Map.of());

            // Assert
            assertEquals(serverUrl, result);
        }

        @Test
        @DisplayName("Should use server from OpenAPI when no serverUrl")
        void shouldUseServerFromOpenAPIWhenNoServerUrl() {
            // Arrange
            List<Server> servers = new ArrayList<>();
            Server server = new Server();
            server.setUrl("https://api.example.com");
            servers.add(server);

            when(openAPI.getServers()).thenReturn(servers);

            // Act
            String result = determineBaseUrl(openAPI, null, null, Map.of());

            // Assert
            assertEquals("https://api.example.com", result);
        }

        @Test
        @DisplayName("Should use serverIndex when provided")
        void shouldUseServerIndexWhenProvided() {
            // Arrange
            List<Server> servers = new ArrayList<>();

            Server server1 = new Server();
            server1.setUrl("https://dev.example.com");
            servers.add(server1);

            Server server2 = new Server();
            server2.setUrl("https://prod.example.com");
            servers.add(server2);

            when(openAPI.getServers()).thenReturn(servers);

            // Act
            String result = determineBaseUrl(openAPI, null, 1, Map.of());

            // Assert
            assertEquals("https://prod.example.com", result);
        }
    }
}
