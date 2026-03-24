package ai.wanaku.backend;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.router")
public interface WanakuRouterConfig {

    HealthCheckConfig healthCheck();

    interface HealthCheckConfig {

        @WithDefault("true")
        boolean enabled();

        @WithDefault("60")
        int intervalSeconds();

        @WithDefault("10")
        int maxConcurrent();
    }
}
