package ai.wanaku.backend.api.v1.installations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.runtime.ShutdownEvent;

/**
 * CDI producer that selects the appropriate {@link Launcher} implementation
 * based on the {@code wanaku.launcher.enabled} configuration property.
 *
 * <p>When the property is absent or set to anything other than {@code "true"},
 * the {@link NoopLauncher} is used. When {@code wanaku.launcher.enabled=true},
 * a {@link SubProcessLauncher} backed by a {@link ProcessInterface} is produced.
 */
@ApplicationScoped
public class LauncherProvider {

    @Inject
    Instance<ProcessInterface> processInterfaceInstance;

    @Inject
    Instance<Launcher> launcherInstance;

    @Produces
    @DefaultBean
    @ApplicationScoped
    Launcher noopLauncher() {
        return new NoopLauncher();
    }

    @Produces
    @LookupIfProperty(name = "wanaku.launcher.enabled", stringValue = "true")
    @ApplicationScoped
    Launcher subProcessLauncher() {
        return new SubProcessLauncher(processInterfaceInstance.get());
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (launcherInstance.isResolvable()) {
            launcherInstance.get().shutdown();
        }
    }
}
