package ai.wanaku.core.uri;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class URIParserTest {

    @ParameterizedTest
    @MethodSource("mapProvider")
    void parseTestSimple(String expected, String provided, Map<String, Object> arguments) {
        assertEquals(expected, URIParser.parse(provided, arguments));
    }


    static Stream<Arguments> mapProvider() {
        return Stream.of(
                arguments("http://my-host/", "http://my-host/{id ?: ''}", Map.of()),
                arguments("http://my-host/data", "http://my-host/{id ?: ''}data", Map.of()),
                arguments("http://my-host/123/data", "http://my-host/{id ? id : ''}/data", Map.of("id", "123")),
                arguments("http://my-host/1/data", "http://my-host/{id ? id or 1}/data", Map.of()),
                arguments("http://my-host/123/data", "http://my-host/{id ? id or 1}/data", Map.of("id", "123")),
                arguments("http://my-host/data?id=456", "http://my-host/data?id={id}", Map.of("id", "456")),
                arguments("http://my-host/data?id=456&name=user", "http://my-host/data?id={id}&name=user", Map.of("id", "456")),
                arguments("http://my-host/data?id=456", "http://my-host/data{query.build}",
                        Map.of("query", new Query(Map.of("id", "456")))),
                arguments("http://my-host/?id=456&name=MyName", "http://my-host/{query.build}",
                        Map.of("query", new Query(Map.of("id", "456", "name", "MyName")))),
                arguments("http://my-host/data", "http://my-host/data{query.build}", Map.of("query", Query.EMPTY)),
                arguments("http://my-host/?id=456&name=My+Name+With+Spaces", "http://my-host/{query.build}",
                        Map.of("query", new Query(Map.of("id", "456", "name", "My Name With Spaces"))))
        );
    }
}