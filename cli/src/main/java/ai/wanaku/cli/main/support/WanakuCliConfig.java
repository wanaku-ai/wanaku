package ai.wanaku.cli.main.support;

import ai.wanaku.core.config.WanakuConfig;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Map;

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

    @WithDefault("early-access")
    String earlyAccessTag();

    List<String> defaultServices();

    /**
     * Returns a map of components that can be used in the getting started
     *
     * @return A map of component
     */
    Map<String, String> components();

    /**
     * Every service needs its own gRPC port. The CLI increases it automatically
     * starting from this port number
     * @return the port
     */
    @WithDefault("9000")
    int initialGrpcPort();

    @WithDefault("5")
    int routerStartWaitSecs();
}
