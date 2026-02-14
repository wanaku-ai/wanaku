package ai.wanaku.backend.bridge;

import java.util.HashMap;
import java.util.Map;
import io.quarkiverse.mcp.server.ToolManager;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        // Mimic entry similar to bug/issue-617-reproducer.json -> getHelloByName
        Map<String, Property> props = new HashMap<>();
        props.put("X-Request-ID", prop("header", "service", "abc-123"));
        props.put("CamelHttpMethod", prop("header", "service", "GET"));
        props.put("name", prop(null, "service", null)); // not a header

        ToolReference ref = buildToolReference(props);

        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        Map<String, String> headers = InvokerToolExecutor.extractHeaders(ref, toolArguments);

        assertEquals(2, headers.size(), "Should only include header+service entries");
        assertEquals("abc-123", headers.get("X-Request-ID"));
        assertEquals("GET", headers.get("CamelHttpMethod"));
        assertFalse(headers.containsKey("name"), "Non-header properties must be ignored");
    }

    private static ToolManager.ToolArguments mockToolArguments() {
        ToolManager.ToolArguments toolArguments = mock(ToolManager.ToolArguments.class);
        when(toolArguments.args()).thenReturn(Map.of());
        return toolArguments;
    }

    @Test
    void extractHeaders_ignoresNonServiceScope() {
        Map<String, Property> props = new HashMap<>();
        props.put("X-Request-ID", prop("header", "service", "xyz"));
        props.put("Some-Other-Header", prop("header", "service-endpoint", "should-be-ignored"));

        ToolReference ref = buildToolReference(props);

        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        Map<String, String> headers = InvokerToolExecutor.extractHeaders(ref, toolArguments);

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

        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        Map<String, String> headers = InvokerToolExecutor.extractHeaders(ref, toolArguments);
        assertTrue(headers.isEmpty());
    }

    @Test
    void extractHeaders_throwsNPEWhenPropertyHasNoDefaultValueAndArgumentNotProvided() {
        // Reproduce the NPE scenario: property has no default value (value is null)
        // and the argument is not provided in toolArguments.args()
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", null)); // null value = should come from args

        ToolReference ref = buildToolReference(props);

        // Mock toolArguments with empty args map (no X-API-Key provided)
        final ToolManager.ToolArguments toolArguments = mockToolArguments();

        // This should throw NPE because:
        // 1. Property.getValue() returns null
        // 2. Code tries to get from toolArguments.args().get("X-API-Key") which returns null
        // 3. Calling .toString() on null throws NPE
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> InvokerToolExecutor.extractHeaders(ref, toolArguments),
                "Should throw NPE when property value is null and argument is not provided");
    }

    @Test
    void extractHeaders_usesTheArgumentValueFromToolInvocation() {
        // Reproduce the NPE scenario: property has no default value (value is null)
        // and the argument is not provided in toolArguments.args()
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", null)); // null value = should come from args

        ToolReference ref = buildToolReference(props);

        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        when(toolArguments.args()).thenReturn(Map.of("X-API-Key", "123"));

        final Map<String, String> stringStringMap = InvokerToolExecutor.extractHeaders(ref, toolArguments);
        assertEquals("123", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractHeaders_usesTheDefaultArgument() {
        // Reproduce the NPE scenario: property has no default value (value is null)
        // and the argument is not provided in toolArguments.args()
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", "abc")); // null value = should come from args

        ToolReference ref = buildToolReference(props);

        // Mock toolArguments with empty args map (no X-API-Key provided)
        final ToolManager.ToolArguments toolArguments = mockToolArguments();

        final Map<String, String> stringStringMap = InvokerToolExecutor.extractHeaders(ref, toolArguments);
        assertEquals("abc", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractHeaders_prefersTheProvidedArgument() {
        // Reproduce the NPE scenario: property has no default value (value is null)
        // and the argument is not provided in toolArguments.args()
        Map<String, Property> props = new HashMap<>();
        props.put("X-API-Key", prop("header", "service", "abc")); // null value = should come from args

        ToolReference ref = buildToolReference(props);

        // Mock toolArguments with empty args map (no X-API-Key provided)
        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        when(toolArguments.args()).thenReturn(Map.of("X-API-Key", "123"));

        final Map<String, String> stringStringMap = InvokerToolExecutor.extractHeaders(ref, toolArguments);
        assertEquals("123", stringStringMap.get("X-API-Key"));
    }

    @Test
    void extractMetadataHeaders_extractsPrefixedArgsAndStripsPrefix() {
        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_userId", "user-456");
        args.put("regularArg", "value");
        when(toolArguments.args()).thenReturn(args);

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(toolArguments);

        assertEquals(2, headers.size());
        assertEquals("ctx-123", headers.get("contextId"));
        assertEquals("user-456", headers.get("userId"));
        assertFalse(headers.containsKey("regularArg"));
        assertFalse(headers.containsKey("wanaku_meta_contextId"));
    }

    @Test
    void extractMetadataHeaders_handlesNullValues() {
        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_nullValue", null);
        when(toolArguments.args()).thenReturn(args);

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(toolArguments);

        assertEquals(1, headers.size());
        assertEquals("ctx-123", headers.get("contextId"));
        assertFalse(headers.containsKey("nullValue"));
    }

    @Test
    void extractMetadataHeaders_returnsEmptyMapWhenNoMetadataArgs() {
        final ToolManager.ToolArguments toolArguments = mockToolArguments();
        when(toolArguments.args()).thenReturn(Map.of("regularArg", "value"));

        Map<String, String> headers = InvokerToolExecutor.extractMetadataHeaders(toolArguments);

        assertTrue(headers.isEmpty());
    }

    @Test
    void filterOutMetadataArgs_removesPrefixedArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("wanaku_meta_contextId", "ctx-123");
        args.put("wanaku_meta_userId", "user-456");
        args.put("regularArg", "value");
        args.put("anotherArg", 42);

        Map<String, Object> filtered = InvokerToolExecutor.filterOutMetadataArgs(args);

        assertEquals(2, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertEquals(42, filtered.get("anotherArg"));
        assertFalse(filtered.containsKey("wanaku_meta_contextId"));
        assertFalse(filtered.containsKey("wanaku_meta_userId"));
    }

    @Test
    void filterOutMetadataArgs_returnsAllArgsWhenNoMetadata() {
        Map<String, Object> args = new HashMap<>();
        args.put("regularArg", "value");
        args.put("anotherArg", 42);

        Map<String, Object> filtered = InvokerToolExecutor.filterOutMetadataArgs(args);

        assertEquals(2, filtered.size());
        assertEquals("value", filtered.get("regularArg"));
        assertEquals(42, filtered.get("anotherArg"));
    }
}
