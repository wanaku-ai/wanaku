package ai.wanaku.core.capabilities.common;

import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InvocationDelegate;
import org.jboss.logging.Logger;

public class ServicesHelper {
    private static final Logger LOG = Logger.getLogger(ServicesHelper.class);

    private ServicesHelper() {}


    public static int waitAndRetry(String service, Exception e, int retries, int waitSeconds) {
        retries--;
        if (retries == 0) {
            LOG.errorf(e, "Failed to register service %s: %s. No more retries left", service, e.getMessage());
            return 0;
        } else {
            LOG.warnf("Failed to register service %s: %s. Retries left: %d", service, e.getMessage(), retries);
        }
        try {
            Thread.sleep(waitSeconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warnf("Interrupted while still having %d retries", retries);
            return 0;
        }
        return retries;
    }

    public static InquireReply buildInquireReply(InvocationDelegate delegate) {
        return InquireReply.newBuilder()
                .putAllProperties(delegate.properties())
                .build();
    }
}
