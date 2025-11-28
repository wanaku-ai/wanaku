package ai.wanaku.core.runtime.camel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.wanaku.capabilities.sdk.config.provider.api.DefaultConfigResource;
import ai.wanaku.capabilities.sdk.config.provider.file.ConfigFileStore;
import ai.wanaku.capabilities.sdk.config.provider.file.SecretFileStore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CamelQueryParameterBuilderTest {

    @Test
    public void testUri() throws URISyntaxException {
        CamelQueryParameterBuilder cpb = newParameterBuilder();

        final Map<String, String> build = cpb.build();
        build.forEach((k, v) -> Assertions.assertFalse(k.startsWith("query.")));
        assertEquals(2, build.size());
        assertNotNull(build.get("someSecretKey"));
        assertEquals("RAW(someSecretValue)", build.get("someSecretKey"));
    }

    static CamelQueryParameterBuilder newParameterBuilder() throws URISyntaxException {
        final URI sampleCap = Objects.requireNonNull(
                        CamelQueryParameterBuilderTest.class.getResource("/sample-capabilities.properties"))
                .toURI();
        ConfigFileStore configFileStore = new ConfigFileStore(sampleCap);

        final URI sampleSecret = CamelQueryParameterBuilderTest.class
                .getResource("/sample-secrets.properties")
                .toURI();
        SecretFileStore secretFileStore = new SecretFileStore(sampleSecret);

        return new CamelQueryParameterBuilder(new DefaultConfigResource(configFileStore, secretFileStore));
    }
}
