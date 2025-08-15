package ai.wanaku.core.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

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
        SortedMap<String, String> params = new TreeMap<>();

        params.put("key1", "value1");
        params.put("key2", "value2");
        String uri = URIHelper.buildUri("ftp://test", params);
        assertEquals("ftp://test?key1=value1&key2=value2", uri);
    }

    @Test
    void testAddQueryParameterToBaseUri() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");
        String uri = URIHelper.addQueryParameters("ftp://test", params);
        assertEquals("ftp://test?key1=value1&key2=value2", uri);
    }

    @Test
    void testAddQueryParametersToUriAlreadyContainsQueryParameters() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");
        String uri = URIHelper.addQueryParameters("ftp://test?foo=bar&dead=false", params);
        assertEquals("ftp://test?foo=bar&dead=false&key1=value1&key2=value2", uri);
    }
}
