package ai.wanaku.core.runtime.camel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import java.net.URISyntaxException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CamelUriTest {

    @DisplayName("Tests that building Camel URIs work and ")
    @Test
    void testUri() throws URISyntaxException {
        CamelQueryParameterBuilder parameterBuilder = CamelQueryParameterBuilderTest.newParameterBuilder();

        ToolInvokeRequest toolInvokeRequest = ToolInvokeRequest.newBuilder()
                .setBody("")
                .setUri("tool://uri")
                .setConfigurationURI("ignored")
                .setSecretsURI("ignored")
                .putAllHeaders(Map.of())
                .putAllArguments(Map.of())
                .build();

        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest("http://base", toolInvokeRequest, parameterBuilder::build);

        assertNotNull(parsedRequest);
        assertEquals("http://base?someSecretKey=RAW(someSecretValue)&addKey=addedValue", parsedRequest.uri());
    }
}
