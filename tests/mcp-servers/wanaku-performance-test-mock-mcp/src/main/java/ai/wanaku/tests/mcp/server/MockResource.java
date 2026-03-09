package ai.wanaku.tests.mcp.server;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;

public class MockResource {

    @Inject
    MockMcpPerformanceConfig config;

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
