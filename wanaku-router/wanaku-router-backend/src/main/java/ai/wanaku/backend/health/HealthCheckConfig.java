package ai.wanaku.backend.health;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.router.health-check")
public interface HealthCheckConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("60")
    int intervalSeconds();

    @WithDefault("10")
    int maxConcurrent();

    @WithDefault("10")
    int timeoutSeconds();
}
