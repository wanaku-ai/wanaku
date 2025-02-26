package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.service.routing")
public interface WanakuRoutingConfig extends WanakuServiceConfig {

    @WithDefault("%s://%s")
    String baseUri();

    Service service();
    Credentials credentials();

}
