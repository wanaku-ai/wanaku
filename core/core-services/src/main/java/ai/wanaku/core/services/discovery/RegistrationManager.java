package ai.wanaku.core.services.discovery;

public interface RegistrationManager {

    void register();
    void deregister();
    void ping();
    void lastAsFail(String reason);
    void lastAsSuccessful();
}
