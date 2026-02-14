package ai.wanaku.core.runtime.camel;

import java.net.URISyntaxException;
import java.util.Map;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CamelQueryHelperTest {

    private ParsedToolInvokeRequest parsedRequest;

    @BeforeEach
    void beforeTest() throws URISyntaxException {
        CamelQueryParameterBuilder parameterBuilder = CamelQueryParameterBuilderTest.newParameterBuilder();

        ToolInvokeRequest toolInvokeRequest = ToolInvokeRequest.newBuilder()
                .setBody("")
                .setUri("tool://uri")
                .setConfigurationURI("ignored")
                .setSecretsURI("ignored")
                .putAllHeaders(Map.of())
                .putAllArguments(Map.of())
                .build();

        parsedRequest = ParsedToolInvokeRequest.parseRequest("http://base", toolInvokeRequest, parameterBuilder::build);
    }

    @DisplayName("Tests that building Camel URIs work and URIs with a single value are filtered")
    @Test
    void testUriSingle() {
        assertNotNull(parsedRequest);
        String filtered = CamelQueryHelper.replaceRawValue(
                "http://base?someSecretKey=RAW(someSecretValue)&addKey=addedValue", "xxx111");
        assertEquals("http://base?someSecretKey=RAW(xxx111)&addKey=addedValue", filtered);
    }

    @DisplayName("Tests that building Camel URIs work and URIs with more secret values are filtered")
    @Test
    void testUriMultiple() {
        assertNotNull(parsedRequest);
        String filtered = CamelQueryHelper.replaceRawValue(
                "http://base?someSecretKey1=RAW(someSecretValue1)&someSecretKey2=RAW(someSecretValue2)&addKey=addedValue",
                "aaa222");
        assertEquals("http://base?someSecretKey1=RAW(aaa222)&someSecretKey2=RAW(aaa222)&addKey=addedValue", filtered);
    }
}
