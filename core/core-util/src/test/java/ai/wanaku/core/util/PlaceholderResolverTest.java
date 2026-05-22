package ai.wanaku.core.util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderResolverTest {

    private static final String VER_A = "1.0.0";
    private static final String VER_B = "2.0.0-SNAPSHOT";

    @Test
    void testResolveSinglePlaceholder() {
        String result = PlaceholderResolver.resolve("com.example:lib-a:${ver.a}", Map.of("ver.a", VER_A));
        assertEquals("com.example:lib-a:1.0.0", result);
    }

    @Test
    void testResolveHyphenatedPlaceholder() {
        String result =
                PlaceholderResolver.resolve("com.example:lib-a:${camel-version}", Map.of("camel-version", VER_A));
        assertEquals("com.example:lib-a:1.0.0", result);
    }

    @Test
    void testResolveMultiplePlaceholders() {
        String result = PlaceholderResolver.resolve(
                "com.example:lib-a:${ver.a}\ncom.example:lib-b:${ver.b}\n", Map.of("ver.a", VER_A, "ver.b", VER_B));
        assertEquals("com.example:lib-a:1.0.0\ncom.example:lib-b:2.0.0-SNAPSHOT\n", result);
    }

    @Test
    void testUnresolvedPlaceholderLeftUnchanged() {
        String result = PlaceholderResolver.resolve("com.example:lib-a:${unknown.version}", Map.of("ver.a", VER_A));
        assertEquals("com.example:lib-a:${unknown.version}", result);
    }

    @Test
    void testMixedResolvedAndUnresolved() {
        String result = PlaceholderResolver.resolve(
                "com.example:lib-a:${ver.a}\ncom.example:lib-b:${unknown.version}", Map.of("ver.a", VER_A));
        assertEquals("com.example:lib-a:1.0.0\ncom.example:lib-b:${unknown.version}", result);
    }

    @Test
    void testNoPlaceholders() {
        String result = PlaceholderResolver.resolve("com.example:lib-a:1.0.0", Map.of("ver.a", VER_A));
        assertEquals("com.example:lib-a:1.0.0", result);
    }

    @Test
    void testNullText() {
        assertNull(PlaceholderResolver.resolve(null, Map.of("ver.a", VER_A)));
    }

    @Test
    void testNullBindings() {
        String result = PlaceholderResolver.resolve("${ver.a}", null);
        assertEquals("${ver.a}", result);
    }

    @Test
    void testEmptyBindings() {
        String result = PlaceholderResolver.resolve("${ver.a}", Map.of());
        assertEquals("${ver.a}", result);
    }

    @Test
    void testHardcodedVersionUnchanged() {
        String result = PlaceholderResolver.resolve("com.example:lib-a:1.0.0", Map.of("ver.a", "9.9.9"));
        assertEquals("com.example:lib-a:1.0.0", result);
    }

    @Test
    void testHasPlaceholders() {
        assertTrue(PlaceholderResolver.hasPlaceholders("${ver.a}"));
        assertTrue(PlaceholderResolver.hasPlaceholders("prefix-${ver.a}-suffix"));
        assertTrue(PlaceholderResolver.hasPlaceholders("line1\n${ver.a}\nline3"));
    }

    @Test
    void testHasNoPlaceholders() {
        assertFalse(PlaceholderResolver.hasPlaceholders("com.example:lib-a:1.0.0"));
        assertFalse(PlaceholderResolver.hasPlaceholders(""));
        assertFalse(PlaceholderResolver.hasPlaceholders(null));
    }

    @Test
    void testLoadBindingsFromString() {
        Map<String, String> bindings = PlaceholderResolver.loadBindings("ver.a=1.0.0\nver.b=2.0.0-SNAPSHOT\n");
        assertEquals(VER_A, bindings.get("ver.a"));
        assertEquals(VER_B, bindings.get("ver.b"));
    }

    @Test
    void testLoadBindingsWithComments() {
        Map<String, String> bindings = PlaceholderResolver.loadBindings(
                "# Version properties\nver.a=1.0.0\n# Another comment\nver.b=2.0.0-SNAPSHOT\n");
        assertEquals(2, bindings.size());
        assertEquals(VER_A, bindings.get("ver.a"));
    }

    @Test
    void testEndToEndResolveFromProperties() {
        String versionProps = "ver.a=1.0.0\nver.b=2.0.0-SNAPSHOT\nver.c=3.0.0\n";
        Map<String, String> bindings = PlaceholderResolver.loadBindings(versionProps);

        String deps = "com.example:lib-a:${ver.a}\n"
                + "com.example:lib-b:${ver.b}\n"
                + "com.example:lib-b2:${ver.b}\n"
                + "com.example:lib-b3:${ver.b}\n"
                + "com.example:lib-c:${ver.c}\n";

        String result = PlaceholderResolver.resolve(deps, bindings);

        String expected = "com.example:lib-a:1.0.0\n"
                + "com.example:lib-b:2.0.0-SNAPSHOT\n"
                + "com.example:lib-b2:2.0.0-SNAPSHOT\n"
                + "com.example:lib-b3:2.0.0-SNAPSHOT\n"
                + "com.example:lib-c:3.0.0\n";

        assertEquals(expected, result);
    }
}
