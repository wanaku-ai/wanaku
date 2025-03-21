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
                arguments("http://my-host/", "http://my-host/{parameter.value('id')}", emptyArg()),
                arguments("http://my-host//data", "http://my-host/{parameter.value('id')}/data", emptyArg()),
                arguments("http://my-host/123/data", "http://my-host/{parameter.value('id')}/data", makeArg(Map.of("id", "123"))),
                arguments("http://my-host/1/data", "http://my-host/{parameter.valueOrElse('id', 1)}/data", emptyArg()),
                arguments("http://my-host/1/data", "http://my-host/{parameter.valueOrElse('id', '1')}/data", emptyArg()),
                arguments("http://my-host/123/data", "http://my-host/{parameter.valueOrElse('id', 1)}/data", makeArg(Map.of("id", "123"))),
                arguments("http://my-host/data", "http://my-host/data{parameter.query('id')}", emptyArg()),
                arguments("http://my-host/data?id=456", "http://my-host/data{parameter.query('id')}", makeArg(Map.of("id", "456"))),
                arguments("http://my-host/data?id=456&name=user", "http://my-host/data?id={parameter.value('id')}&name=user", makeArg(Map.of("id", "456"))),
                arguments("http://my-host/data?id=456", "http://my-host/data{parameter.query}",
                        makeArg(Map.of("id", "456"))),
                arguments("http://my-host/?id=456&name=MyName", "http://my-host/{parameter.query}",
                        makeArg(Map.of("id", "456", "name", "MyName"))),
                arguments("http://my-host/data", "http://my-host/data{parameter.query}", emptyArg()),
                arguments("http://my-host/?id=456&name=My+Name+With+Spaces", "http://my-host/{parameter.query('id', 'name')}",
                        makeArg(Map.of("id", "456", "name", "My Name With Spaces"))),

                arguments("http://my-host/data?id=456", "http://my-host/data{parameter.query('id')}",
                        makeArg(Map.of("id", "456", "name", "My Name With Spaces")))
        );
    }

    static Map<String, Object> makeArg(Map<String, Object> map) {
        return Map.of("parameter", new Parameter(map));
    }

    static Map<String, Object> emptyArg() {
        return Map.of("parameter", Parameter.EMPTY);
    }
}