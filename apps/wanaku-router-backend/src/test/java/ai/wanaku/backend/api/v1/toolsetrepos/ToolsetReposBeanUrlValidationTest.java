package ai.wanaku.backend.api.v1.toolsetrepos;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolsetReposBeanUrlValidationTest {

    @Mock
    UrlAllowlistConfig urlAllowlistConfig;

    @InjectMocks
    ToolsetReposBean bean;

    @BeforeEach
    void setUpNoAllowlist() {
        lenient().when(urlAllowlistConfig.isAllowlistConfigured()).thenReturn(false);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://github.com/wanaku-ai/wanaku-toolsets",
                "https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/main",
                "https://example.com/toolsets",
                "http://public-server.org/repo"
            })
    void validateUrl_allowsPublicUrls(String url) {
        assertDoesNotThrow(() -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"ftp://example.com/repo", "file:///etc/passwd", "javascript:alert(1)", "data:text/plain,hello"})
    void validateUrl_rejectsNonHttpSchemes(String url) {
        assertThrows(WanakuException.class, () -> bean.validateUrl(url));
    }

    @Test
    void validateUrl_rejectsMissingScheme() {
        assertThrows(WanakuException.class, () -> bean.validateUrl("example.com/repo"));
    }

    @Test
    void validateUrl_rejectsMissingHost() {
        assertThrows(WanakuException.class, () -> bean.validateUrl("https:///path-only"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost/repo", "https://127.0.0.1/repo", "https://0.0.0.0/repo"})
    void validateUrl_rejectsLocalhost(String url) {
        assertThrows(WanakuException.class, () -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://10.0.0.1/repo",
                "https://10.255.255.255/repo",
                "https://172.16.0.1/repo",
                "https://172.31.255.255/repo",
                "https://192.168.1.1/repo",
                "https://192.168.0.100/repo"
            })
    void validateUrl_rejectsPrivateIpRanges(String url) {
        assertThrows(WanakuException.class, () -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://172.15.0.1/repo", "https://172.32.0.1/repo"})
    void validateUrl_allowsNonPrivate172Range(String url) {
        assertDoesNotThrow(() -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://169.254.1.1/repo", "https://169.254.169.254/repo"})
    void validateUrl_rejectsLinkLocalAddresses(String url) {
        assertThrows(WanakuException.class, () -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://100.64.0.1/repo", "https://100.127.255.255/repo"})
    void validateUrl_rejectsCarrierGradeNat(String url) {
        assertThrows(WanakuException.class, () -> bean.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://100.63.0.1/repo", "https://100.128.0.1/repo"})
    void validateUrl_allowsNonCgnat100Range(String url) {
        assertDoesNotThrow(() -> bean.validateUrl(url));
    }

    @Test
    void validateUrl_withAllowlist_acceptsMatchingHost() {
        when(urlAllowlistConfig.isAllowlistConfigured()).thenReturn(true);
        when(urlAllowlistConfig.isHostAllowed("raw.githubusercontent.com")).thenReturn(true);

        assertDoesNotThrow(() -> bean.validateUrl("https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/main"));
    }

    @Test
    void validateUrl_withAllowlist_rejectsNonMatchingHost() {
        when(urlAllowlistConfig.isAllowlistConfigured()).thenReturn(true);
        when(urlAllowlistConfig.isHostAllowed("evil.com")).thenReturn(false);
        when(urlAllowlistConfig.getAllowlistPatterns()).thenReturn(List.of("*.github.com"));

        assertThrows(WanakuException.class, () -> bean.validateUrl("https://evil.com/repo"));
    }

    @Test
    void validateUrl_withAllowlist_localhostBlockedEvenWithoutExplicitPrivateCheck() {
        when(urlAllowlistConfig.isAllowlistConfigured()).thenReturn(true);
        when(urlAllowlistConfig.isHostAllowed("localhost")).thenReturn(false);
        when(urlAllowlistConfig.getAllowlistPatterns()).thenReturn(List.of("*.github.com"));

        assertThrows(WanakuException.class, () -> bean.validateUrl("https://localhost/repo"));
    }

    @Test
    void validateUrl_withAllowlist_stillBlocksPrivateIpAddresses() {
        when(urlAllowlistConfig.isAllowlistConfigured()).thenReturn(true);
        when(urlAllowlistConfig.isHostAllowed("169.254.169.254")).thenReturn(true);
        when(urlAllowlistConfig.getAllowlistPatterns()).thenReturn(List.of("*"));

        assertThrows(WanakuException.class, () -> bean.validateUrl("https://169.254.169.254/repo"));
    }

    @Test
    void isLoopbackHost_localhost() {
        assertTrue(ToolsetReposBean.isLoopbackHost("localhost"));
    }

    @Test
    void isLoopbackHost_127Ip() {
        assertTrue(ToolsetReposBean.isLoopbackHost("127.0.0.1"));
    }

    @Test
    void isLoopbackHost_publicHost() {
        assertFalse(ToolsetReposBean.isLoopbackHost("example.com"));
    }

    @Test
    void isPrivateOrReservedHost_rfc1918() {
        assertTrue(ToolsetReposBean.isPrivateOrReservedHost("10.0.0.1"));
        assertTrue(ToolsetReposBean.isPrivateOrReservedHost("172.16.0.1"));
        assertTrue(ToolsetReposBean.isPrivateOrReservedHost("192.168.1.1"));
    }

    @Test
    void isPrivateOrReservedHost_linkLocal() {
        assertTrue(ToolsetReposBean.isPrivateOrReservedHost("169.254.1.1"));
    }

    @Test
    void isPrivateOrReservedHost_cgnat() {
        assertTrue(ToolsetReposBean.isPrivateOrReservedHost("100.64.0.1"));
    }

    @Test
    void isPrivateOrReservedHost_publicAddress() {
        assertFalse(ToolsetReposBean.isPrivateOrReservedHost("1.1.1.1"));
    }

    @Test
    void isPrivate172_validRange() {
        assertTrue(ToolsetReposBean.isPrivate172("172.16.0.1"));
        assertTrue(ToolsetReposBean.isPrivate172("172.31.255.255"));
    }

    @Test
    void isPrivate172_outsideRange() {
        assertFalse(ToolsetReposBean.isPrivate172("172.15.0.1"));
        assertFalse(ToolsetReposBean.isPrivate172("172.32.0.1"));
        assertFalse(ToolsetReposBean.isPrivate172("10.0.0.1"));
    }

    @Test
    void isPrivateIpv4Pattern_unresolvableHost() {
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("10.0.0.1"));
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("192.168.1.1"));
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("169.254.1.1"));
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("100.64.0.1"));
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("127.0.0.1"));
        assertTrue(ToolsetReposBean.isPrivateIpv4Pattern("0.0.0.0"));
        assertFalse(ToolsetReposBean.isPrivateIpv4Pattern("8.8.8.8"));
    }
}
