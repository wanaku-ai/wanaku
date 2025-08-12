package ai.wanaku.core.util.discovery;

import ai.wanaku.api.exceptions.WanakuException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jboss.logging.Logger;

/**
 * Utilities to help with target discovery
 */
public final class DiscoveryUtil {
    private static final Logger LOG = Logger.getLogger(DiscoveryUtil.class);

    private DiscoveryUtil() {}

    /**
     * Resolve the address that will be registered in the service registry
     * @return the address as a string
     */
    public static String resolveRegistrationAddress() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            throw new WanakuException(e);
        }
    }

    /**
     * Resolve the address that will be registered in the service registry
     * @param address The address to use or "auto" for auto resolution
     * @return the address as a string
     */
    public static String resolveRegistrationAddress(String address) {
        if ("auto".equals(address)) {
            LOG.infof("Using announce address %s ", address);
            address = DiscoveryUtil.resolveRegistrationAddress();
        }
        return address;
    }
}
