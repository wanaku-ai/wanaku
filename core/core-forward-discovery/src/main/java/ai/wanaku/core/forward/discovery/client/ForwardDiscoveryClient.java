package ai.wanaku.core.forward.discovery.client;


public interface ForwardDiscoveryClient {

    boolean isRegistered();

    void register();

    void deregister();
}
