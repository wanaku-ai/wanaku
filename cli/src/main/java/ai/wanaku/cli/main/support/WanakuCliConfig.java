package ai.wanaku.cli.main.support;

import ai.wanaku.core.config.WanakuConfig;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "wanaku.cli")
public interface WanakuCliConfig extends WanakuConfig {

    interface Tool {
        String createCmd();
    }

    interface Resource {
        String createCmd();
    }

    Tool tool();

    Resource resource();


}
