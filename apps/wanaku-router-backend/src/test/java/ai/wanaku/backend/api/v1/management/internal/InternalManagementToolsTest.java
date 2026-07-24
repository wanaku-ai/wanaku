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

/**
 * Tests for internal management tools registered on the wanaku-internal MCP namespace.
 * <p>
 * The MCP protocol test is disabled because the SDK's {@code HttpClientStreamableHttpTransport}
 * does not properly maintain session state across requests in the Quarkus test environment,
 * causing {@code listTools()} to return empty results. The same functionality is validated by
 * the external integration tests in the wanaku-tests repository.
 */
@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class InternalManagementToolsTest extends WanakuRouterTest {

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Test
    @Disabled("SDK HttpClientStreamableHttpTransport session management issue in QuarkusTest — "
            + "validated by wanaku-tests integration suite instead")
    void internalManagementToolsAreRegisteredAndCallable() {
        // Validated externally by wanaku-tests/camel-integration-capability-tests
    }
}
