package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration class for tool services
 */
@ConfigMapping(prefix = "wanaku.service.routing")
public interface WanakuRoutingConfig extends WanakuServiceConfig {

    String name();

    @WithDefault("%s://%s")
    String baseUri();

    Service service();
    Credentials credentials();

    Registration registration();
}
