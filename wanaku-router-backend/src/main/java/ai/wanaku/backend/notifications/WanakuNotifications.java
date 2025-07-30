package ai.wanaku.backend.notifications;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;

public class WanakuNotifications {
    private static final Logger LOG = Logger.getLogger(WanakuNotifications.class);

    @Inject
    NotificationManager notificationManager;

    @Startup
    void addNotification() {
         notificationManager.newNotification("foo")
                 .setType(Type.INITIALIZED)
                 .setHandler(args -> {
                     LOG.infof("New client connected: %s (with id = %s)",
                             args.connection().initialRequest().implementation().name(), args.connection().id());
                     return null;
                 }).register();
    }
}
