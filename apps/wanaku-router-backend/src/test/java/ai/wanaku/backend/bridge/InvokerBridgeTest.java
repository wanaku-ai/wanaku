package ai.wanaku.backend.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvokerBridgeTest {

    private ToolReference buildToolReference(Map<String, Property> properties) {
        InputSchema schema = new InputSchema();
        schema.setType("object");
        schema.setProperties(properties);

        ToolReference ref = new ToolReference();
        ref.setName("sample");
        ref.setType("http");
        ref.setUri("https://example-app/hello-world/v1/test");
        ref.setInputSchema(schema);
        return ref;
    }

    private static Property prop(String target, String scope, String value) {
        Property p = new Property();
        p.setType("string");
        p.setDescription("test");
        p.setTarget(target);
        p.setScope(scope);
        p.setValue(value);
        return p;
    }

    @Test
    void extractHeaders_onlyHeaderAndServiceScopeAreReturned() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-Request-ID", prop("header", "service", "abc-123"));
        props.put("CamelHttpMethod", prop("header", "service", "GET"));
        props.put("name", prop(null, "service", null));

        ToolReference ref = buildToolReference(props);

        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of());
        Map<String, String> headers =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());

        assertEquals(2, headers.size(), "Should only include header+service entries");
        assertEquals("abc-123", headers.get("X-Request-ID"));
        assertEquals("GET", headers.get("CamelHttpMethod"));
        assertFalse(headers.containsKey("name"), "Non-header properties must be ignored");
    }

    private static McpSchema.CallToolRequest mockCallToolRequest(Map<String, Object> args) {
        return new McpSchema.CallToolRequest("sample", args, null);
    }

    @Test
    void extractHeaders_ignoresNonServiceScope() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-Request-ID", prop("header", "service", "xyz"));
        props.put("Some-Other-Header", prop("header", "service-endpoint", "should-be-ignored"));

        ToolReference ref = buildToolReference(props);

        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of());
        Map<String, String> headers =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());

        assertEquals(1, headers.size());
        assertEquals("xyz", headers.get("X-Request-ID"));
        assertFalse(headers.containsKey("Some-Other-Header"));
    }

    @Test
    void extractHeaders_returnsEmptyMapWhenNoHeaders() {
        Map<String, Property> props = new HashMap<>();
        props.put("name", prop(null, "service", null));
        props.put("id", prop(null, "service", null));

        ToolReference ref = buildToolReference(props);

        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of());
        Map<String, String> headers =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());
        assertTrue(headers.isEmpty());
    }

    @Test
    void extractHeaders_throwsNPEWhenPropertyHasNoDefaultValueAndArgumentNotProvided() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", null));

        ToolReference ref = buildToolReference(props);
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of());

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> InvokerToolExecutor.extractHeaders(
                        ref, request.arguments() != null ? request.arguments() : Map.of()),
                "Should throw NPE when property value is null and argument is not provided");
    }

    @Test
    void extractHeaders_usesTheArgumentValueFromToolInvocation() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", null));

        ToolReference ref = buildToolReference(props);
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of("X-API-Key", "123"));

        final Map<String, String> stringStringMap =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());
        assertEquals("123", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractHeaders_usesTheDefaultArgument() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", "abc"));

        ToolReference ref = buildToolReference(props);
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of());

        final Map<String, String> stringStringMap =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());
        assertEquals("abc", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractHeaders_prefersTheProvidedArgument() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", "abc"));

        ToolReference ref = buildToolReference(props);
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of("X-API-Key", "123"));

        final Map<String, String> stringStringMap =
                InvokerToolExecutor.extractHeaders(ref, request.arguments() != null ? request.arguments() : Map.of());
        assertEquals("123", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractMetadataHeaders_extractsPrefixedArgsAndStripsPrefix() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_userId", "user-456");
        args.put("regularArg", "value");
        McpSchema.CallToolRequest request = mockCallToolRequest(args);

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(request);

        assertEquals(2, headers.size());
        assertEquals("ctx-123", headers.get("contextId"));
        assertEquals("user-456", headers.get("userId"));
        assertFalse(headers.containsKey("regularArg"));
        assertFalse(headers.containsKey("wanaku_meta_contextId"));
    }

    @Test
    void extractMetadataHeaders_handlesNullValues() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_nullValue", null);
        McpSchema.CallToolRequest request = mockCallToolRequest(args);

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(request);

        assertEquals(1, headers.size());
        assertEquals("ctx-123", headers.get("contextId"));
        assertFalse(headers.containsKey("nullValue"));
    }

    @Test
    void extractMetadataHeaders_returnsEmptyMapWhenNoMetadataArgs() {
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of("regularArg", "value"));

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(request);
        assertTrue(headers.isEmpty());
    }

    @Test
    void filterOutReservedArgs_removesMetadataPrefixedArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_userId", "user-456");
        args.put("regularArg", "value");
        args.put("anotherArg", 42);

        Map<String, Object> filtered = InvokerToolExecutor.filterOutReservedArgs(args);

        assertEquals(2, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertEquals(42, filtered.get("anotherArg"));
        assertFalse(filtered.containsKey("wanaku_meta_contextId"));
        assertFalse(filtered.containsKey("wanaku_meta_userId"));
    }

    @Test
    void filterOutReservedArgs_removesAuthPrefixedArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_auth_Authorization", "Bearer token-123");
        args.put("wanaku_auth_X-Third-Party", "secret-456");
        args.put("regularArg", "value");

        Map<String, Object> filtered = InvokerToolExecutor.filterOutReservedArgs(args);

        assertEquals(1, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertFalse(filtered.containsKey("wanaku_auth_Authorization"));
        assertFalse(filtered.containsKey("wanaku_auth_X-Third-Party"));
    }

    @Test
    void filterOutReservedArgs_removesBothMetadataAndAuthArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_auth_Authorization", "Bearer token-123");
        args.put("regularArg", "value");

        Map<String, Object> filtered = InvokerToolExecutor.filterOutReservedArgs(args);

        assertEquals(1, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertFalse(filtered.containsKey("wanaku_meta_contextId"));
        assertFalse(filtered.containsKey("wanaku_auth_Authorization"));
    }

    @Test
    void filterOutReservedArgs_returnsAllArgsWhenNoReserved() {
        Map<String, Object> args = new HashMap<>();
        args.put("regularArg", "value");
        args.put("anotherArg", 42);

        Map<String, Object> filtered = InvokerToolExecutor.filterOutReservedArgs(args);

        assertEquals(2, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertEquals(42, filtered.get("anotherArg"));
    }

    @Test
    void extractAuthHeaders_extractsPrefixedArgsAndStripsPrefix() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_auth_Authorization", "Bearer token-123");
        args.put("wanaku_auth_X-Third-Party", "secret-456");
        args.put("regularArg", "value");
        McpSchema.CallToolRequest request = mockCallToolRequest(args);

        Map<String, String> headers = InvokerToolExecutor.extractAuthHeaders(request);

        assertEquals(2, headers.size());
        assertEquals("Bearer token-123", headers.get("Authorization"));
        assertEquals("secret-456", headers.get("X-Third-Party"));
        assertFalse(headers.containsKey("regularArg"));
        assertFalse(headers.containsKey("wanaku_auth_Authorization"));
    }

    @Test
    void extractAuthHeaders_handlesNullValues() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_auth_Authorization", "Bearer token-123");
        args.put("wanaku_auth_nullValue", null);
        McpSchema.CallToolRequest request = mockCallToolRequest(args);

        Map<String, String> headers = InvokerToolExecutor.extractAuthHeaders(request);

        assertEquals(1, headers.size());
        assertEquals("Bearer token-123", headers.get("Authorization"));
        assertFalse(headers.containsKey("nullValue"));
    }

    @Test
    void extractAuthHeaders_returnsEmptyMapWhenNoAuthArgs() {
        McpSchema.CallToolRequest request = mockCallToolRequest(Map.of("regularArg", "value"));

        Map<String, String> headers = InvokerToolExecutor.extractAuthHeaders(request);
        assertTrue(headers.isEmpty());
    }

    @Test
    void extractAuthHeaders_doesNotInterfereWithMetadataHeaders() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_auth_Authorization", "Bearer token-123");
        args.put("regularArg", "value");
        McpSchema.CallToolRequest request = mockCallToolRequest(args);

        Map<String, String> authHeaders = InvokerToolExecutor.extractAuthHeaders(request);
        Map<String, String> metaHeaders = InvokerToolExecutor.extractMetadataHeaders(request);

        assertEquals(1, authHeaders.size());
        assertEquals("Bearer token-123", authHeaders.get("Authorization"));

        assertEquals(1, metaHeaders.size());
        assertEquals("ctx-123", metaHeaders.get("contextId"));
    }

    @Test
    void validateRequiredParameters_throwsWhenRequiredParamMissing() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query"));

        Map<String, Object> args = Map.of();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> InvokerToolExecutor.validateRequiredParameters(ref, args));
        assertTrue(ex.getMessage().contains("query"), "Error should name the missing parameter");
    }

    @Test
    void validateRequiredParameters_throwsWhenRequiredParamIsBlank() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query"));

        Map<String, Object> args = Map.of("query", "   ");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> InvokerToolExecutor.validateRequiredParameters(ref, args));
        assertTrue(ex.getMessage().contains("query"));
    }

    @Test
    void validateRequiredParameters_throwsWhenRequiredParamIsNull() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query"));

        Map<String, Object> args = new HashMap<>();
        args.put("query", null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> InvokerToolExecutor.validateRequiredParameters(ref, args));
        assertTrue(ex.getMessage().contains("query"));
    }

    @Test
    void validateRequiredParameters_passesWhenAllRequiredPresent() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query"));

        Map<String, Object> args = Map.of("query", "search term");

        assertDoesNotThrow(() -> InvokerToolExecutor.validateRequiredParameters(ref, args));
    }

    @Test
    void validateRequiredParameters_passesWhenNoRequiredList() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        // required is null by default

        Map<String, Object> args = Map.of();

        assertDoesNotThrow(() -> InvokerToolExecutor.validateRequiredParameters(ref, args));
    }

    @Test
    void validateRequiredParameters_passesWhenRequiredListIsEmpty() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of());

        Map<String, Object> args = Map.of();

        assertDoesNotThrow(() -> InvokerToolExecutor.validateRequiredParameters(ref, args));
    }

    @Test
    void validateRequiredParameters_passesWhenInputSchemaIsNull() {
        ToolReference ref = new ToolReference();
        ref.setName("sample");
        ref.setType("http");
        ref.setUri("https://example.com");
        // no input schema set

        Map<String, Object> args = Map.of();

        assertDoesNotThrow(() -> InvokerToolExecutor.validateRequiredParameters(ref, args));
    }

    @Test
    void validateRequiredParameters_reportsAllMissingParams() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));
        props.put("format", prop(null, null, null));
        props.put("limit", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query", "format", "limit"));

        Map<String, Object> args = Map.of("format", "json");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> InvokerToolExecutor.validateRequiredParameters(ref, args));
        assertTrue(ex.getMessage().contains("query"), "Should report 'query' as missing");
        assertTrue(ex.getMessage().contains("limit"), "Should report 'limit' as missing");
        assertFalse(ex.getMessage().contains("format"), "Should NOT report 'format' (it was provided)");
    }

    @Test
    void validateRequiredParameters_throwsWhenArgsIsNull() {
        Map<String, Property> props = new HashMap<>();
        props.put("query", prop(null, null, null));

        ToolReference ref = buildToolReference(props);
        ref.getInputSchema().setRequired(List.of("query"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> InvokerToolExecutor.validateRequiredParameters(ref, null));
        assertTrue(ex.getMessage().contains("query"), "Should report 'query' as missing");
    }
}
