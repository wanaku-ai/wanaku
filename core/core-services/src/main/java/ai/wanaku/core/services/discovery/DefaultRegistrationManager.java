package ai.wanaku.core.services.discovery;

import jakarta.ws.rs.core.Response;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.core.service.discovery.client.DiscoveryService;
import ai.wanaku.core.services.io.InstanceDataManager;
import ai.wanaku.core.services.io.ServiceEntry;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import static ai.wanaku.core.services.common.ServicesHelper.waitAndRetry;

public class DefaultRegistrationManager implements RegistrationManager {
    private static final Logger LOG = Logger.getLogger(DefaultRegistrationManager.class);

    private final DiscoveryService service;
    private ServiceTarget target;
    private int retries;
    private int waitSeconds;
    private final InstanceDataManager instanceDataManager;
    private volatile boolean registered;
    private final ReentrantLock lock = new ReentrantLock();


    public DefaultRegistrationManager(DiscoveryService service, ServiceTarget target,
            int retries, int waitSeconds, String dataDir) {
        this.service = Objects.requireNonNull(service);
        this.target = Objects.requireNonNull(target);

        this.retries = retries;
        this.waitSeconds = waitSeconds;

        instanceDataManager = new InstanceDataManager(dataDir, target.getService());

        if (instanceDataManager.dataFileExists()) {
            final ServiceEntry serviceEntry = instanceDataManager.readEntry();
            if (serviceEntry != null) {
                target.setId(serviceEntry.getId());
            }
        } else {
            try {
                instanceDataManager.createDataDirectory();
            } catch (IOException e) {
                throw new WanakuException(e);
            }
        }
    }

    private void tryRegistering() {
        do {
            try (final RestResponse<WanakuResponse<ServiceTarget>> response = service.register(target)) {
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    LOG.warnf("The service %s failed to register. Response code: %d", target.getService(), response.getStatus());
                }

                final WanakuResponse<ServiceTarget> entity = response.getEntity();
                if (entity == null || entity.data() == null) {
                    throw new WanakuException("Could not register service because the provided response is null or invalid");
                }

                target = entity.data();
                instanceDataManager.writeEntry(target);
                registered = true;
                LOG.debugf("The service %s successfully registered with ID %s.", target.getService(), target.getId());
            } catch (Exception e) {
                retries = waitAndRetry(target.getService(), e, retries, waitSeconds);
            }
        } while (!registered && (retries > 0));
    }

    private boolean isRegistered() {
        return registered;
    }

    @Override
    public void register() {
        if (isRegistered()) {
            ping();
        } else {
            LOG.debugf("Registering %s service %s with address %s", target.getServiceType().asValue(), target.getService(), target.toAddress());
            try {
                if (!lock.tryLock(1, TimeUnit.SECONDS)) {
                    LOG.warnf("Could not obtain a registration lock in 1 second. Giving up ...");
                    return;
                }
                tryRegistering();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void deregister() {
        if (target != null && target.getId() != null) {
            try (Response response = service.deregister(target)) {
                if (response.getStatus() != 200) {
                    LOG.warnf("De-registering service %s failed with status %d", target.getServiceType().asValue(), response.getStatus());
                }
            } catch (Exception e) {
                logServiceFailure(e, "De-registering failed with %s");
            }
        }
    }

    @Override
    public void ping() {
        if (target != null && target.getId() != null) {
            LOG.tracef("Pinging router ...");
            try (var response = service.ping(target.getId())) {
                if (response.getStatus() != 200) {
                    LOG.warnf("Pinging router failed with status %d", response.getStatus());
                }

                if (LOG.isDebugEnabled()) {
                    LOG.tracef("Pinging router completed successfully");
                }
            } catch (Exception e) {
                logServiceFailure(e, "Pinging router failed with %s");

            }
        }
    }

    @Override
    public void lastAsFail(String reason) {
        if (target.getId() == null) {
            LOG.warnf("Trying to update the state of an unknown service %s", target.getService());
            return;
        }

        try (final Response response = service.updateState(target.getId(), ServiceState.newUnhealthy(reason))) {
            if (response.getStatus() != 200) {
                LOG.errorf("Could not update the state of an service %s (%s)", target.getService(), target.getId());
            }
        } catch (Exception e) {
            logServiceFailure(e, "Updating last status failed with %s");
        }
    }

    private static void logServiceFailure(Exception e, String format) {
        if (LOG.isTraceEnabled()) {
            LOG.errorf(e, format, e.getMessage());
        } else {
            LOG.errorf(format, e.getMessage());
        }
    }

    @Override
    public void lastAsSuccessful() {
        if (target.getId() == null) {
            LOG.warnf("Trying to update the state of an unknown service %s", target.getService());
            return;
        }

        try (final Response response = service.updateState(target.getId(), ServiceState.newHealthy())) {
            if (response.getStatus() != 200) {
                LOG.errorf("Could not update the state of an service %s (%s)", target.getService(), target.getId());
            }
        } catch (Exception e) {
            logServiceFailure(e, "Updating last status failed with %s");
        }
    }


    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = waitSeconds;
    }
}
