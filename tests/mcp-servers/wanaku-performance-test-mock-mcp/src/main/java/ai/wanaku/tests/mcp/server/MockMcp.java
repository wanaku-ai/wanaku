package ai.wanaku.tests.mcp.server;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;

public class MockMcp {
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

    @Resource(uri = "file:///mock/data", description = "A mock resource that returns static data for testing")
    TextResourceContents mockData() {
        int delay = config.delay();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return TextResourceContents.create("file:///mock/data", "mock-resource-data-0987654321");
    }
}
