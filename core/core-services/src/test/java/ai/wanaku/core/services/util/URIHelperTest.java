package ai.wanaku.core.services.util;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class URIHelperTest {

    @Test
    void testEmptyQuery() {
        String uri = URIHelper.buildUri("ftp://test", Map.of());
        assertEquals("ftp://test", uri);
    }

    @Test
    void testQuery() {
        String uri = URIHelper.buildUri("ftp://test", Map.of("key", "value"));
        assertEquals("ftp://test?key=value", uri);
    }

    @Test
    void testQueryTwoParams() {
        String uri = URIHelper.buildUri("ftp://test", Map.of("key1", "value1", "key2", "value2"));
        assertEquals("ftp://test?key2=value2&key1=value1", uri);
    }

}