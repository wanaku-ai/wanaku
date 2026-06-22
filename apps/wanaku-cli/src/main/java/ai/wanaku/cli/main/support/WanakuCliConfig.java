package ai.wanaku.cli.main.support;

import java.util.List;
import java.util.Map;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import ai.wanaku.core.config.WanakuConfig;

@ConfigMapping(prefix = "wanaku.cli")
public interface WanakuCliConfig extends WanakuConfig {

    interface Tool {
        String createCmd();
    }

    interface Resource {
        String createCmd();
    }

    interface Mcp {
        String createCmd();
    }

    interface Auth {
        @WithDefault("none")
        String mode();

        @WithDefault("~/.wanaku/credentials")
        String credentialsFile();

        @WithDefault("false")
        boolean enabled();
    }

    Tool tool();

    Resource resource();

    Mcp mcp();

    Auth auth();

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

    /**
     * Maximum number of seconds to wait for the local router readiness
     * endpoint before starting capability services.
     */
    @WithDefault("30")
    int routerStartWaitSecs();

    @WithDefault("local")
    String localProfile();
}
