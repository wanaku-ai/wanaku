package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "wanaku.service.provider")
public interface WanakuProviderConfig extends WanakuServiceConfig {

    @WithDefault("%s://%s")
    String baseUri();

    Service service();
    Credentials credentials();

}
