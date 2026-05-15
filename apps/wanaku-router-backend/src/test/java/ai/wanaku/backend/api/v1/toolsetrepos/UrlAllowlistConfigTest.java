package ai.wanaku.backend.api.v1.toolsetrepos;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlAllowlistConfigTest {

    @Test
    void testGlobToRegex_singleAsteriskMatchesSingleSubdomainLabel() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("*.github.com");
        assertTrue(pattern.matcher("api.github.com").matches());
        assertFalse(pattern.matcher("github.com").matches());
        assertFalse(pattern.matcher("a.b.github.com").matches());
        assertFalse(pattern.matcher("evilgithub.com").matches());
    }

    @Test
    void testGlobToRegex_singleAsteriskMatchesDifferentDomain() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("*.githubusercontent.com");
        assertTrue(pattern.matcher("raw.githubusercontent.com").matches());
        assertTrue(pattern.matcher("objects.githubusercontent.com").matches());
        assertFalse(pattern.matcher("githubusercontent.com").matches());
    }

    @Test
    void testGlobToRegex_exactHost() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("github.com");
        assertTrue(pattern.matcher("github.com").matches());
        assertFalse(pattern.matcher("api.github.com").matches());
        assertFalse(pattern.matcher("evilgithub.com").matches());
    }

    @Test
    void testGlobToRegex_doubleAsteriskMatchesAnySubdomainDepth() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("**.example.com");
        assertTrue(pattern.matcher("example.com").matches());
        assertTrue(pattern.matcher("a.example.com").matches());
        assertTrue(pattern.matcher("a.b.example.com").matches());
        assertFalse(pattern.matcher("notexample.com").matches());
    }

    @Test
    void testGlobToRegex_questionMarkMatchesSingleChar() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("host-?.example.com");
        assertTrue(pattern.matcher("host-1.example.com").matches());
        assertTrue(pattern.matcher("host-a.example.com").matches());
        assertFalse(pattern.matcher("host-12.example.com").matches());
    }

    @Test
    void testGlobToRegex_caseInsensitive() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("*.GitHub.com");
        assertTrue(pattern.matcher("api.github.com").matches());
        assertTrue(pattern.matcher("api.GITHUB.COM").matches());
    }

    @Test
    void testGlobToRegex_standaloneAsteriskMatchesEverything() {
        Pattern pattern = UrlAllowlistConfig.toRegexPattern("*");
        assertTrue(pattern.matcher("example.com").matches());
        assertTrue(pattern.matcher("a.b.c.example.com").matches());
        assertTrue(pattern.matcher("localhost").matches());
        assertTrue(pattern.matcher("single").matches());
    }

    @Test
    void testIsHostAllowed_emptyAllowlistAllowsAll() {
        UrlAllowlistConfig config = createConfig(List.of());
        assertTrue(config.isHostAllowed("anything.example.com"));
        assertTrue(config.isHostAllowed("localhost"));
    }

    @Test
    void testIsHostAllowed_matchingPatternAllows() {
        UrlAllowlistConfig config = createConfig(List.of("*.github.com", "*.githubusercontent.com"));
        assertTrue(config.isHostAllowed("api.github.com"));
        assertTrue(config.isHostAllowed("raw.githubusercontent.com"));
    }

    @Test
    void testIsHostAllowed_nonMatchingPatternRejects() {
        UrlAllowlistConfig config = createConfig(List.of("*.github.com", "*.githubusercontent.com"));
        assertFalse(config.isHostAllowed("evil.com"));
        assertFalse(config.isHostAllowed("localhost"));
    }

    @Test
    void testIsAllowlistConfigured_emptyListReturnsFalse() {
        UrlAllowlistConfig config = createConfig(List.of());
        assertFalse(config.isAllowlistConfigured());
    }

    @Test
    void testIsAllowlistConfigured_nonEmptyListReturnsTrue() {
        UrlAllowlistConfig config = createConfig(List.of("*.github.com"));
        assertTrue(config.isAllowlistConfigured());
    }

    @Test
    void testIsAllowlistConfigured_blankEntriesAreFiltered() {
        UrlAllowlistConfig config = createConfig(List.of("", "  "));
        assertFalse(config.isAllowlistConfigured());
    }

    @Test
    void testIsHostAllowed_blankEntriesIgnoredValidEntryWorks() {
        UrlAllowlistConfig config = createConfig(List.of("", "*.github.com", "  "));
        assertTrue(config.isHostAllowed("api.github.com"));
        assertFalse(config.isHostAllowed("evil.com"));
    }

    private static UrlAllowlistConfig createConfig(List<String> patterns) {
        UrlAllowlistConfig config = new UrlAllowlistConfig();
        String joined = String.join(",", patterns);
        config.rawAllowlistPatterns = joined.isEmpty() ? Optional.empty() : Optional.of(joined);
        config.invalidateCache();
        return config;
    }
}
