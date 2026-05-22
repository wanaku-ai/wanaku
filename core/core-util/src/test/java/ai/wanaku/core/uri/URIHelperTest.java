package ai.wanaku.core.uri;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

    @Test
    void testSpecialCharactersInParameterValues() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "a&b");
        params.put("key2", "x=y");
        params.put("key3", "path/to?resource#anchor");
        params.put("key4", "value%20test");
        String uri = URIHelper.buildUri("http://example.com", params);
        assertEquals("http://example.com?key1=a&b&key2=x=y&key3=path/to?resource#anchor&key4=value%20test", uri);
    }

    @Test
    void testSpacesInParameterValues() {
        String uri = URIHelper.buildUri("http://example.com", Map.of("name", "John Doe"));
        assertEquals("http://example.com?name=John Doe", uri);
    }

    @Test
    void testUnicodeCharactersInParameters() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("emoji", "😀🎉");
        params.put("chinese", "你好");
        params.put("arabic", "مرحبا");
        String uri = URIHelper.buildUri("http://example.com", params);
        assertEquals("http://example.com?arabic=مرحبا&chinese=你好&emoji=😀🎉", uri);
    }

    @Test
    void testNullParameterValue() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "value1");
        params.put("key2", null);
        String uri = URIHelper.buildUri("http://example.com", params);
        assertEquals("http://example.com?key1=value1&key2=null", uri);
    }

    @Test
    void testEmptyStringParameterValue() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "value1");
        params.put("key2", "");
        String uri = URIHelper.buildUri("http://example.com", params);
        assertEquals("http://example.com?key1=value1&key2=", uri);
    }

    @Test
    void testLargeNumberOfParameters() {
        Map<String, String> params = generateLargeMap(50);
        String uri = URIHelper.buildUri("http://example.com", params);
        for (int i = 0; i < 50; i++) {
            assertTrue(uri.contains("p%d=v%d".formatted(i, i)));
        }
    }

    @ParameterizedTest
    @MethodSource("schemeProvider")
    void testBuildUriWithDifferentSchemes(String baseUri, Map<String, String> params) {
        String uri = URIHelper.buildUri(baseUri, params);
        assertEquals(baseUri, uri);
    }

    @Test
    void testAddQueryParametersToUriWithFragment() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");
        String uri = URIHelper.addQueryParameters("http://example.com/path#section", params);
        assertEquals("http://example.com/path#section?key1=value1&key2=value2", uri);
    }

    private static Stream<Arguments> schemeProvider() {
        return Stream.of(
                arguments("http://example.com", Map.of()),
                arguments("https://example.com", Map.of()),
                arguments("ftp://example.com", Map.of()),
                arguments("file:///path/to/file", Map.of()),
                arguments("custom://resource", Map.of()));
    }

    private static Map<String, String> generateLargeMap(int size) {
        SortedMap<String, String> map = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            map.put("p" + i, "v" + i);
        }
        return map;
    }
}
