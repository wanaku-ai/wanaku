package ai.wanaku.backend.api.v1.toolsetrepos;

import java.net.Inet6Address;
import java.net.InetAddress;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                "https://example.com/toolsets"
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
    void validateUrl_rejectsUnresolvableHost() {
        assertThrows(
                WanakuException.class, () -> bean.validateUrl("https://this-host-does-not-exist-xyz123.invalid/repo"));
    }

    @Test
    void validateUrl_returnsResolvedUrl() {
        ToolsetReposBean.ResolvedUrl result = bean.validateUrl("https://example.com/repo");
        assertNotNull(result);
        assertNotNull(result.address());
        assertFalse(result.address().isLoopbackAddress());
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
    void isPrivateOrReservedAddress_rfc1918() throws Exception {
        assertTrue(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("192.168.1.1")));
    }

    @Test
    void isPrivateOrReservedAddress_linkLocal() throws Exception {
        assertTrue(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("169.254.1.1")));
    }

    @Test
    void isPrivateOrReservedAddress_cgnat() throws Exception {
        assertTrue(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("100.64.0.1")));
    }

    @Test
    void isPrivateOrReservedAddress_publicAddress() throws Exception {
        assertFalse(ToolsetReposBean.isPrivateOrReservedAddress(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    void isReservedIpv6_blocksIpv4MappedLoopback() throws Exception {
        assertIpv4MappedReserved("::ffff:127.0.0.1", "loopback", addr -> addr.isLoopbackAddress());
    }

    @Test
    void isReservedIpv6_blocksIpv4MappedLinkLocal() throws Exception {
        assertIpv4MappedReserved("::ffff:169.254.169.254", "link-local", addr -> addr.isLinkLocalAddress());
    }

    @Test
    void isReservedIpv6_blocksIpv4MappedPrivate() throws Exception {
        assertIpv4MappedReserved("::ffff:10.0.0.1", "site-local", addr -> addr.isSiteLocalAddress());
    }

    @Test
    void isReservedIpv6_blocksIpv4MappedCgnat() throws Exception {
        assertIpv4MappedReserved("::ffff:100.64.0.1", "CGNAT", addr -> ToolsetReposBean.isCarrierGradeNat(addr));
    }

    @Test
    void isReservedIpv6_allowsIpv4MappedPublic() throws Exception {
        assertIpv4MappedAllowed(
                "::ffff:8.8.8.8",
                addr -> !(addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()));
    }

    /**
     * Asserts that an IPv4-mapped IPv6 address is correctly identified as reserved.
     * On JVMs that resolve IPv4-mapped addresses as {@link Inet6Address}, this
     * verifies via {@link ToolsetReposBean#isReservedIpv6}. On JVMs that resolve
     * them as {@link java.net.Inet4Address}, this falls back to the provided checker.
     */
    private void assertIpv4MappedReserved(
            String ipv6Literal, String category, java.util.function.Predicate<InetAddress> v4Fallback)
            throws Exception {
        InetAddress addr = InetAddress.getByName(ipv6Literal);
        if (addr instanceof Inet6Address) {
            assertTrue(ToolsetReposBean.isReservedIpv6(addr), ipv6Literal + " should be reserved (Inet6Address path)");
        } else {
            assertTrue(v4Fallback.test(addr), ipv6Literal + " resolved as Inet4Address and is " + category);
        }
    }

    private void assertIpv4MappedAllowed(String ipv6Literal, java.util.function.Predicate<InetAddress> v4Fallback)
            throws Exception {
        InetAddress addr = InetAddress.getByName(ipv6Literal);
        if (addr instanceof Inet6Address) {
            assertFalse(
                    ToolsetReposBean.isReservedIpv6(addr), ipv6Literal + " should not be reserved (Inet6Address path)");
        } else {
            assertTrue(v4Fallback.test(addr), ipv6Literal + " resolved as Inet4Address and is public");
        }
    }

    @Test
    void isReservedIpv6_blocksUniqueLocalAddress() throws Exception {
        InetAddress fc00 = InetAddress.getByName("fc00::1");
        assertTrue(ToolsetReposBean.isReservedIpv6(fc00));
        InetAddress fdff = InetAddress.getByName("fdff::1");
        assertTrue(ToolsetReposBean.isReservedIpv6(fdff));
    }

    @Test
    void isReservedIpv6_allowsPublicIpv6() throws Exception {
        InetAddress addr = InetAddress.getByName("2001:4860:4860::8888");
        assertFalse(ToolsetReposBean.isReservedIpv6(addr));
    }

    @Test
    void isIpv4MappedIpv6_detectsMappedAddress() throws Exception {
        byte[] addr = new byte[16];
        addr[10] = (byte) 0xFF;
        addr[11] = (byte) 0xFF;
        addr[12] = 0x7F;
        addr[13] = 0x00;
        addr[14] = 0x00;
        addr[15] = 0x01;
        assertTrue(ToolsetReposBean.isIpv4MappedIpv6(addr));
    }

    @Test
    void isIpv4MappedIpv6_rejectsNonMappedAddress() throws Exception {
        byte[] normal = InetAddress.getByName("2001:db8::1").getAddress();
        assertFalse(ToolsetReposBean.isIpv4MappedIpv6(normal));
    }
}
