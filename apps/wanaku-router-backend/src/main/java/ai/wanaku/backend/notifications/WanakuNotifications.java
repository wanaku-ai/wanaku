package ai.wanaku.backend.notifications;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import io.quarkus.runtime.Startup;

@ApplicationScoped
public class WanakuNotifications {
    private static final Logger LOG = Logger.getLogger(WanakuNotifications.class);

    @Startup
    void onStartup() {
        LOG.info("Wanaku MCP server initialized");
    }
}
