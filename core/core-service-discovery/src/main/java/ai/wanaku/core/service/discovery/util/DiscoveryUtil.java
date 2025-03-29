package ai.wanaku.core.service.discovery.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import ai.wanaku.api.exceptions.WanakuException;

/**
 * Utilities to help with target discovery
 */
public final class DiscoveryUtil {

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
}
