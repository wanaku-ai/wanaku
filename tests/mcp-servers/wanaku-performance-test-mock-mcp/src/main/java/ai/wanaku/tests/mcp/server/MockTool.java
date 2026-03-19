package ai.wanaku.tests.mcp.server;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;

public class MockTool {
    @Inject
    Instance<ForwardRegistrationManager> registrationManager;

    @Inject
    MockMcpPerformanceConfig config;

    @Tool(description = "A mock tool that returns static data for testing", name = "performancenoop")
    String mockTool(@ToolArg(description = "A name parameter") String name) {
        int delay = config.delay();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return "1234567890";
    }
}
