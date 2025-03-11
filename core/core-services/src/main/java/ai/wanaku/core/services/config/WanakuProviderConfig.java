package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration class for providers
 */
@ConfigMapping(prefix = "wanaku.service.provider")
public interface WanakuProviderConfig extends WanakuServiceConfig {

    String name();

    @WithDefault("%s://%s")
    String baseUri();

    Service service();
    Credentials credentials();

    Registration registration();
}
