package ai.wanaku.backend.bridge;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;
import dev.langchain4j.mcp.client.McpRoot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForwardRegistryTest {

    private ForwardRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ForwardRegistry();
    }

    @Test
    void linkWithRootsStoresRoots() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");
        List<McpRoot> roots = List.of(new McpRoot("home", "file:///home/user"), new McpRoot("data", "file:///data"));

        registry.link(pair, "http://localhost:8080", roots);

        assertEquals("http://localhost:8080", registry.getClientAddress(pair));
        List<McpRoot> retrievedRoots = registry.getRoots(pair);
        assertNotNull(retrievedRoots);
        assertEquals(2, retrievedRoots.size());
        assertEquals("file:///home/user", retrievedRoots.get(0).uri());
        assertEquals("home", retrievedRoots.get(0).name());
    }

    @Test
    void linkWithNullRootsDoesNotStoreRoots() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");

        registry.link(pair, "http://localhost:8080", null);

        assertEquals("http://localhost:8080", registry.getClientAddress(pair));
        assertNull(registry.getRoots(pair));
    }

    @Test
    void linkWithEmptyRootsDoesNotStoreRoots() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");

        registry.link(pair, "http://localhost:8080", List.of());

        assertEquals("http://localhost:8080", registry.getClientAddress(pair));
        assertNull(registry.getRoots(pair));
    }

    @Test
    void unlinkRemovesRoots() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");
        List<McpRoot> roots = List.of(new McpRoot("home", "file:///home/user"));

        registry.link(pair, "http://localhost:8080", roots);
        assertNotNull(registry.getRoots(pair));

        registry.unlink(pair);
        assertNull(registry.getClientAddress(pair));
        assertNull(registry.getRoots(pair));
    }

    @Test
    void getRootsByAddressReturnsCorrectRoots() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");
        List<McpRoot> roots = List.of(new McpRoot("home", "file:///home/user"));

        registry.link(pair, "http://localhost:8080", roots);

        List<McpRoot> retrievedRoots = registry.getRootsByAddress("http://localhost:8080");
        assertNotNull(retrievedRoots);
        assertEquals(1, retrievedRoots.size());
        assertEquals("file:///home/user", retrievedRoots.get(0).uri());
    }

    @Test
    void getRootsByAddressReturnsNullForUnknownAddress() {
        assertNull(registry.getRootsByAddress("http://unknown:8080"));
    }

    @Test
    void linkWithoutRootsPreservesLegacyBehavior() {
        NameNamespacePair pair = new NameNamespacePair("test-forward", "ns1");

        registry.link(pair, "http://localhost:8080");

        assertEquals("http://localhost:8080", registry.getClientAddress(pair));
        assertNull(registry.getRoots(pair));
    }
}
