package ai.wanaku.tool.tavily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavilyDelegateTest {

    private final TavilyDelegate delegate = new TavilyDelegate();

    @Test
    void coerceResponseWithString() throws Exception {
        List<String> result = delegate.coerceResponse("search result");
        assertEquals(1, result.size());
        assertEquals("search result", result.get(0));
    }

    @Test
    void coerceResponseWithNull() {
        assertThrows(InvalidResponseTypeException.class, () -> delegate.coerceResponse(null));
    }

    @Test
    void coerceResponseWithListFiltersNulls() throws Exception {
        List<String> input = new ArrayList<>(Arrays.asList("result1", null, "result2", "", "result3"));
        List<String> result = delegate.coerceResponse(input);
        assertEquals(3, result.size());
        assertEquals("result1", result.get(0));
        assertEquals("result2", result.get(1));
        assertEquals("result3", result.get(2));
    }

    @Test
    void coerceResponseWithCleanList() throws Exception {
        List<String> input = List.of("a", "b", "c");
        List<String> result = delegate.coerceResponse(input);
        assertEquals(3, result.size());
    }

    @Test
    void coerceResponseWithEmptyList() throws Exception {
        List<String> input = new ArrayList<>();
        List<String> result = delegate.coerceResponse(input);
        assertTrue(result.isEmpty());
    }

    @Test
    void coerceResponseWithUnsupportedType() {
        assertThrows(InvalidResponseTypeException.class, () -> delegate.coerceResponse(42));
    }
}
