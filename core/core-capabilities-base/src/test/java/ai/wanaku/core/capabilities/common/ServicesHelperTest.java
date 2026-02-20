package ai.wanaku.core.capabilities.common;

import java.io.File;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServicesHelperTest {

    @Test
    void waitAndRetryDecrementsRetries() {
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), 3, 0);
        assertEquals(2, result);
    }

    @Test
    void waitAndRetryReturnsZeroWhenNoRetriesLeft() {
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), 1, 0);
        assertEquals(0, result);
    }

    @Test
    void getCanonicalServiceHomeExpandsPlaceholder() {
        String userHome = System.getProperty("user.home");
        // Create a minimal config implementation for testing
        TestServiceConfig config = new TestServiceConfig("my-service", "${user.home}/.wanaku");
        String result = ServicesHelper.getCanonicalServiceHome(config);
        assertEquals(userHome + File.separator + ".wanaku" + File.separator + "my-service", result);
    }

    @Test
    void getCanonicalServiceHomeWithoutPlaceholder() {
        TestServiceConfig config = new TestServiceConfig("my-service", "/opt/wanaku");
        String result = ServicesHelper.getCanonicalServiceHome(config);
        assertEquals("/opt/wanaku" + File.separator + "my-service", result);
    }
}
