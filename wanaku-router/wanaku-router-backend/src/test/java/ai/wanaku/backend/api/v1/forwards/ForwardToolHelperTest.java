package ai.wanaku.backend.api.v1.forwards;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ForwardToolHelperTest {

    @Test
    void keepsRemoteNameWhenAvailable() {
        Set<String> reserved = new HashSet<>();
        String name =
                ForwardToolHelper.buildUniqueRemoteToolName("dog-facts", "mcp-server-1", existing -> false, reserved);

        assertEquals("dog-facts", name);
    }

    @Test
    void prefixesWithForwardNameWhenCollisionExists() {
        Set<String> reserved = new HashSet<>();
        String name = ForwardToolHelper.buildUniqueRemoteToolName(
                "dog-facts", "mcp-server-1", existing -> existing.equals("dog-facts"), reserved);

        assertEquals("mcp-server-1__dog-facts", name);
    }

    @Test
    void appendsNumericSuffixWhenPrefixAlsoCollides() {
        Set<String> reserved = new HashSet<>();
        String name = ForwardToolHelper.buildUniqueRemoteToolName(
                "dog-facts",
                "mcp-server-1",
                existing -> existing.equals("dog-facts") || existing.equals("mcp-server-1__dog-facts"),
                reserved);

        assertEquals("mcp-server-1__dog-facts__2", name);
    }

    @Test
    void sanitizesForwardNameBeforePrefixing() {
        Set<String> reserved = new HashSet<>();
        String name = ForwardToolHelper.buildUniqueRemoteToolName(
                "dog-facts", "mcp server 1", existing -> existing.equals("dog-facts"), reserved);

        assertEquals("mcp_server_1__dog-facts", name);
    }

    @Test
    void skipsReservedNamesToo() {
        Set<String> reserved = new HashSet<>();
        reserved.add("mcp-server-1__dog-facts");

        String name = ForwardToolHelper.buildUniqueRemoteToolName(
                "dog-facts", "mcp-server-1", existing -> existing.equals("dog-facts"), reserved);

        assertEquals("mcp-server-1__dog-facts__2", name);
    }
}
