package ai.wanaku.core.forward.discovery.client;

public class NoopDiscoveryClient implements ForwardDiscoveryClient {

    @Override
    public boolean isRegistered() {
        return true;
    }

    @Override
    public void register() {}

    @Override
    public void deregister() {}
}
