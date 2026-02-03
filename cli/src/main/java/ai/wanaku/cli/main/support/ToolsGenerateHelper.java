package ai.wanaku.cli.main.support;

import static ai.wanaku.capabilities.sdk.api.util.ReservedArgumentNames.BODY;
import static ai.wanaku.cli.main.support.FileHelper.cannotWriteToDirectory;
import static ai.wanaku.cli.main.support.StringHelper.isEmpty;
import static ai.wanaku.cli.main.support.StringHelper.isNotEmpty;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_COOKIE;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.util.CollectionsHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.util.ResolverFully;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

public class ToolsGenerateHelper {

    private static final Logger LOG = Logger.getLogger(ToolsGenerateHelper.class);
    private static final String HTTP_TOOL_TYPE = "http";
    private static final String DEFAULT_OUTPUT_FILENAME = "out.json";
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{(.+?)\\}");

    private ToolsGenerateHelper() {}

    /**
     * Loads and resolves an OpenAPI specification from the given location.
     *
     * @param specLocation The file path or URL of the OpenAPI specification
     * @return A fully resolved OpenAPI object
     */
    public static OpenAPI loadAndResolveOpenAPI(String specLocation) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        OpenAPI openAPI = new OpenAPIV3Parser().read(specLocation, null, options);

        if (openAPI != null) {
            new ResolverFully().resolveFully(openAPI);
        }

        return openAPI;
    }

    /**
     * Determines the base URL to use for API calls based on available information.
     * First checks if a server URL is explicitly provided, then falls back to servers defined in the OpenAPI spec.
     *
     * @param openAPI The OpenAPI specification object
     * @param serverUrl A server URL that overrides the ones in the OpenAPI spec (may be null)
     * @param serverIndex The index of the server to use from the OpenAPI spec's servers list (if serverUrl is not provided)
     * @param serverVariables Map of variable names to values for server URL templating
     * @return The resolved base URL for API calls
     */
    public static String determineBaseUrl(
            OpenAPI openAPI, String serverUrl, Integer serverIndex, Map<String, String> serverVariables) {
        // If serverUrl is specified, use it
        if (isNotBlank(serverUrl)) {
            return serverUrl;
        }

        // Otherwise, use servers from the OpenAPI specification
        if (CollectionsHelper.isEmpty(openAPI.getServers())) {
            LOG.error("No servers found in the OpenAPI specification");
            System.err.println(
                    "No servers found in the OpenAPI specification or specified in the 'serverUrl' parameter");
            System.exit(-1);
        }

        int index = serverIndex != null ? serverIndex : 0;
        Server server = openAPI.getServers().get(index);

        // If no server variables, just return the URL
        if (server.getVariables() == null || server.getVariables().isEmpty()) {
            return server.getUrl();
        }

        // Otherwise, interpolate the server URL with variables
        return interpolateServerUrl(server, serverVariables);
    }

    /**
     * Generates tool references for all paths and operations in the OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification object
     * @param baseUrl The base URL to use for API calls
     * @param labels The labels to be added to all generated tool references
     * @return A list of tool references
     */
    public static List<ToolReference> generateToolReferences(
            OpenAPI openAPI, String baseUrl, Map<String, String> labels) {
        return openAPI.getPaths().entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .map(entry -> pathItem2ToolReferences(baseUrl, entry.getKey(), entry.getValue(), labels))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Writes the tool references to the specified output location as JSON.
     *
     * @param toolReferences The list of tool references to write
     * @param output The output path or null to write to standard output
     * @throws Exception If an error occurs during writing
     */
    public static void writeOutput(List<ToolReference> toolReferences, String output) throws Exception {
        try (PrintWriter out = getOutputPrintWriter(output)) {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, toolReferences);
        } catch (Exception e) {
            LOG.trace("Error writing toolset", e);
            throw e;
        }
    }

    /**
     * Converts a PathItem and its operations into a list of tool references.
     *
     * @param baseUrl The base URL for the API
     * @param path The path from the OpenAPI specification
     * @param pathItem The PathItem object containing the operations
     * @param labels The labels to be added to all generated tool references
     * @return A list of tool references, one for each operation in the PathItem
     */
    public static List<ToolReference> pathItem2ToolReferences(
            String baseUrl, String path, PathItem pathItem, Map<String, String> labels) {
        if (pathItem == null) {
            return List.of();
        }

        String pathBaseUrl = null;
        // prefer local server definition to the global one
        if (pathItem.getServers() != null
                && !pathItem.getServers().isEmpty()
                && isNotBlank(pathItem.getServers().getFirst().getUrl())) {
            pathBaseUrl = pathItem.getServers().getFirst().getUrl();
        }

        final String finalBaseUrl = isBlank(pathBaseUrl) ? baseUrl : pathBaseUrl;

        record OperationEntry(String method, Operation operation) {}

        return Stream.of(
                        new OperationEntry("GET", pathItem.getGet()),
                        new OperationEntry("POST", pathItem.getPost()),
                        new OperationEntry("PUT", pathItem.getPut()),
                        new OperationEntry("DELETE", pathItem.getDelete()),
                        new OperationEntry("PATCH", pathItem.getPatch()),
                        new OperationEntry("OPTIONS", pathItem.getOptions()),
                        new OperationEntry("HEAD", pathItem.getHead()),
                        new OperationEntry("TRACE", pathItem.getTrace()))
                .filter(entry -> entry.operation() != null)
                .map(entry -> operation2ToolReference(
                        pathItem, entry.operation(), finalBaseUrl, path, entry.method(), labels))
                .toList();
    }

    /**
     * Generates a tool name from the API path and HTTP method.
     * This is used as a fallback when the operation doesn't have an operationId.
     *
     * @param path The API path (e.g., "/users/{id}")
     * @param method The HTTP method (e.g., "GET", "POST")
     * @return A generated tool name (e.g., "get_users_id")
     */
    public static String generateToolNameFromPathAndMethod(String path, String method) {
        // Start with lowercase method
        String name = method.toLowerCase();

        // Clean up the path: remove leading/trailing slashes, replace special chars with underscores
        String cleanPath = path.replaceAll("^/+", "") // Remove leading slashes
                .replaceAll("/+$", "") // Remove trailing slashes
                .replaceAll("[^a-zA-Z0-9]+", "_"); // Replace non-alphanumeric chars with underscores

        // Combine method and path
        if (isNotEmpty(cleanPath)) {
            name = name + "_" + cleanPath;
        }

        return name;
    }

    /**
     * Converts an operation to a tool reference.
     *
     * @param pathItem The PathItem containing the operation
     * @param operation The Operation to convert
     * @param baseUrl The base URL for the API
     * @param path The path from the OpenAPI specification
     * @param method The HTTP method for this operation
     * @param labels The labels to be added to the tool reference
     * @return A ToolReference representing the operation
     */
    public static ToolReference operation2ToolReference(
            PathItem pathItem,
            Operation operation,
            String baseUrl,
            String path,
            String method,
            Map<String, String> labels) {
        ToolReference toolReference = new ToolReference();

        // Set basic properties
        String name = operation.getOperationId();
        if (isEmpty(name)) {
            name = generateToolNameFromPathAndMethod(path, method);
        }
        toolReference.setName(name);
        toolReference.setDescription(operation.getDescription());
        toolReference.setType(HTTP_TOOL_TYPE);

        // Add labels if provided
        if (labels != null && !labels.isEmpty()) {
            toolReference.addLabels(labels);
        }

        // Determine URI
        String uri = determineOperationUri(operation, baseUrl);
        toolReference.setUri(toolReferenceUrl(uri, path));

        List<Parameter> parameters = pathItem.getParameters() != null ? pathItem.getParameters() : List.of();

        if (operation.getParameters() != null) {
            parameters = Stream.concat(operation.getParameters().stream(), parameters.stream())
                    .toList();
        }

        // Set input schema
        InputSchema inputSchema = parameters2InputSchema(parameters, operation.getRequestBody());

        // Add HTTP method parameter
        addHttpMethodProperty(inputSchema, method);

        toolReference.setInputSchema(inputSchema);
        return toolReference;
    }

    /**
     * Determines the URI to use for an operation.
     * First checks if a base URL is provided, then falls back to servers defined in the operation.
     *
     * @param operation The Operation object
     * @param baseUrl The default base URL to use
     * @return The URI to use for this operation
     */
    public static String determineOperationUri(Operation operation, String baseUrl) {
        return Optional.ofNullable(baseUrl).orElseGet(() -> Optional.ofNullable(operation.getServers())
                .filter(CollectionsHelper::isNotEmpty)
                .flatMap(servers -> servers.stream()
                        .filter(server -> isNotEmpty(server.getUrl()))
                        .findFirst()
                        .map(Server::getUrl))
                .orElse(baseUrl));
    }

    /**
     * Adds the HTTP method as a property to the input schema.
     *
     * @param inputSchema The input schema to modify
     * @param method The HTTP method to add
     */
    public static void addHttpMethodProperty(InputSchema inputSchema, String method) {
        Property property = new Property();
        property.setTarget(TARGET_HEADER);
        property.setType("string");
        property.setScope(SCOPE_SERVICE);
        property.setDescription("HTTP method to use when call the service.");
        property.setValue(method);
        inputSchema.getProperties().put("CamelHttpMethod", property);
    }

    /**
     * Converts OpenAPI parameters and request body to an input schema for the tool reference.
     *
     * @param parameters The list of parameters from the OpenAPI specification
     * @param requestBody The request body from the OpenAPI specification
     * @return An InputSchema object representing the parameters and request body
     */
    public static InputSchema parameters2InputSchema(List<Parameter> parameters, RequestBody requestBody) {
        List<String> requiredParams = new ArrayList<>();
        Map<String, Property> properties = new HashMap<>();

        InputSchema inputSchema = new InputSchema();
        inputSchema.setType("object");
        inputSchema.setRequired(requiredParams);
        inputSchema.setProperties(properties);

        // Process parameters
        parameters.forEach(parameter -> {
            properties.put(parameter.getName(), parameter2Property(parameter));

            if (Boolean.TRUE.equals(parameter.getRequired())) {
                requiredParams.add(parameter.getName());
            }
        });

        // Process request body if present
        if (requestBody != null) {
            if (Boolean.TRUE.equals(requestBody.getRequired())) {
                requiredParams.add(BODY);
            }

            Property body = new Property();
            body.setType("string");
            properties.put(BODY, body);
        }

        return inputSchema;
    }

    /**
     * Converts an OpenAPI parameter to a tool reference property.
     *
     * @param parameter The OpenAPI parameter to convert
     * @return A Property object representing the parameter
     */
    public static Property parameter2Property(Parameter parameter) {
        Property property = new Property();
        property.setDescription(parameter.getDescription());
        property.setType("string");
        property.setScope(SCOPE_SERVICE);

        // Set target based on parameter location
        switch (parameter.getIn()) {
            case "header" -> property.setTarget(TARGET_HEADER);
            case "cookie" -> property.setTarget(TARGET_COOKIE);
                // Other cases (query, path) don't need special handling
        }

        return property;
    }

    /**
     * Creates a URL template for the tool reference by replacing path parameters with the tool reference format.
     *
     * @param baseUrl The base URL for the API
     * @param path The path from the OpenAPI specification
     * @return A URL template for the tool reference
     */
    public static String toolReferenceUrl(String baseUrl, String path) {
        return baseUrl
                + PARAMETER_PATTERN
                        .matcher(path)
                        .replaceAll(matchResult -> String.format("{parameter.value('%s')}", matchResult.group(1)));
    }

    /**
     * Interpolates the server URL with the provided variables.
     * Uses default values for any variables not explicitly provided.
     *
     * @param server The Server object containing the URL template and variables
     * @param variables Map of variable names to values (may be null or empty)
     * @return The interpolated server URL
     */
    public static String interpolateServerUrl(Server server, Map<String, String> variables) {
        String result = server.getUrl();

        if (CollectionsHelper.isEmpty(server.getVariables())) {
            return result;
        }

        Map<String, String> replacements = server.getVariables().entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().getDefault()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (CollectionsHelper.isNotEmpty(variables)) {
            variables = variables.entrySet().stream()
                    .filter(e -> isNotEmpty(e.getKey()) && isNotEmpty(e.getValue()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

            // Replace
            replacements.putAll(variables);
        }

        for (var entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result;
    }

    /**
     * Gets a PrintWriter for the specified outputFile location.
     * If outputFile is null or empty, returns a PrintWriter for standard outputFile.
     * Otherwise, returns a PrintWriter for the specified file.
     *
     * @param output The outputFile path or null for standard outputFile
     * @return A PrintWriter for the specified outputFile
     * @throws Exception If the outputFile file cannot be created or written to
     */
    public static PrintWriter getOutputPrintWriter(String output) throws Exception {
        if (isEmpty(output)) {
            // Write to STDOUT
            return new PrintWriter(System.out);
        }

        Path outputPath = Paths.get(output);

        // Handle directory case
        if (Files.isDirectory(outputPath)) {
            String newFilePath = outputPath.resolve(DEFAULT_OUTPUT_FILENAME).toString();
            System.err.println("Warning: outputFile file " + output
                    + " is a directory. The tools list will be written to " + newFilePath);
            outputPath = Paths.get(newFilePath);
        }

        // Check if file exists
        if (Files.exists(outputPath)) {
            String errorMessage = "Output file " + output + " already exists.";
            System.err.println(errorMessage);
            throw new Exception(errorMessage);
        }

        Path parent = outputPath.getParent() == null ? Path.of(".") : outputPath.getParent();

        // Check if parent directory is writable
        if (cannotWriteToDirectory(parent)) {
            String errorMessage = "Cannot write to directory: " + parent;
            System.err.println(errorMessage);
            throw new Exception(errorMessage);
        }

        try {
            return new PrintWriter(new FileWriter(outputPath.toString()));
        } catch (IOException e) {
            String errorMessage = "Could not open outputFile file " + output + " :" + e.getMessage();
            LOG.error(errorMessage);
            System.err.println(errorMessage);
            throw new Exception(errorMessage);
        }
    }
}
