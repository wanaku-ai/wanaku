package ai.wanaku.core.security;

import io.quarkus.security.spi.runtime.AuthorizationController;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER)
@ApplicationScoped
public class EnabledAuthController extends AuthorizationController {
    @ConfigProperty(name = "wanaku.enable.authorization", defaultValue = "false")
    boolean enableAuthorization;

    @Override
    public boolean isAuthorizationEnabled() {
        return enableAuthorization;
    }
}
