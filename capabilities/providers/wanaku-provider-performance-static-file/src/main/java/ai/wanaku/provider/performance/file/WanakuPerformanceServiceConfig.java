package ai.wanaku.provider.performance.file;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.service.performance")
public interface WanakuPerformanceServiceConfig {

    @WithDefault("10")
    int delay();
}
