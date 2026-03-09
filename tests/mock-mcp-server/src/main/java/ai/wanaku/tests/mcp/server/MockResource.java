package ai.wanaku.tests.mcp.server;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;

public class MockResource {

    @Resource(uri = "file:///mock/data", description = "A mock resource that returns static data for testing")
    TextResourceContents mockData() {
        return TextResourceContents.create("file:///mock/data", "mock-resource-data-0987654321");
    }
}
