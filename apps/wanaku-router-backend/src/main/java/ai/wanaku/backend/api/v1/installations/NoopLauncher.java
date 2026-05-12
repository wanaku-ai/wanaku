package ai.wanaku.backend.api.v1.installations;

import java.util.Collections;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * A no-operation implementation of {@link Launcher} used when the launcher
 * subsystem is disabled.
 *
 * <p>All methods log a warning and return safe default values. This is the
 * default launcher bean, active unless {@code wanaku.launcher.enabled=true}
 * is set in configuration.
 */
public class NoopLauncher implements Launcher {
    private static final Logger LOG = Logger.getLogger(NoopLauncher.class);

    private static final String DISABLED_MSG = "Launcher is disabled. Set wanaku.launcher.enabled=true to enable.";

    /**
     * {@inheritDoc}
     *
     * @return a non-running process status
     */
    @Override
    public ProcessStatus launch(String catalogName, String systemName) {
        LOG.warn(DISABLED_MSG);
        return new ProcessStatus(false, 0, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public void stop(String catalogName, String systemName) {
        LOG.warn(DISABLED_MSG);
    }

    /**
     * {@inheritDoc}
     *
     * @return a non-running process status
     */
    @Override
    public ProcessStatus getStatus(String catalogName, String systemName) {
        LOG.warn(DISABLED_MSG);
        return new ProcessStatus(false, 0, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @return an empty map
     */
    @Override
    public Map<String, ProcessStatus> getAllStatuses() {
        LOG.warn(DISABLED_MSG);
        return Collections.emptyMap();
    }
}
