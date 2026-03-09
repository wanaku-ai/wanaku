package ai.wanaku.tests.mcp.server;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class MockTool {

    @Tool(description = "A mock tool that returns static data for testing")
    String mockTool(@ToolArg(description = "A name parameter") String name) {
        return "1234567890";
    }
}
