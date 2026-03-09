package ai.wanaku.tests.mcp.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.service.performance")
public interface MockMcpPerformanceConfig {

    @WithDefault("10")
    int delay();
}
