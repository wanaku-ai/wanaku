package ai.wanaku.backend.api.v1.management.internal;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuRouterTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class InternalManagementToolsTest extends WanakuRouterTest {

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Test
    @Disabled("MCP protocol test needs migration to official SDK MCP client (Streamable HTTP)")
    void internalManagementToolsAreRegisteredAndCallable() {
        // TODO: Rewrite using io.modelcontextprotocol.client.McpClient with Streamable HTTP transport
        // to connect to /wanaku-internal/mcp and verify tool registration and execution
    }
}
