package ai.wanaku.core.capabilities.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class AuthConfigSource implements ConfigSource {

    private static final String AUTH_PROPERTY = "wanaku.http.auth";
    private static final String AUTH_NONE = "none";
    private static final int ORDINAL = 260;

    private static final String ENV_VAR = "WANAKU_HTTP_AUTH";

    private volatile Map<String, String> noauthProperties;

    @Override
    public Set<String> getPropertyNames() {
        if (isNoAuth()) {
            return getNoauthProperties().keySet();
        }
        return Collections.emptySet();
    }

    @Override
    public String getValue(String propertyName) {
        if (isNoAuth()) {
            return getNoauthProperties().get(propertyName);
        }
        return null;
    }

    @Override
    public Map<String, String> getProperties() {
        if (isNoAuth()) {
            return getNoauthProperties();
        }
        return Collections.emptyMap();
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getName() {
        return "wanaku-auth-config-source";
    }

    private boolean isNoAuth() {
        return AUTH_NONE.equalsIgnoreCase(resolveAuthValue());
    }

    private String resolveAuthValue() {
        String value = System.getProperty(AUTH_PROPERTY);
        if (value != null) {
            return value;
        }

        return System.getenv(ENV_VAR);
    }

    private Map<String, String> getNoauthProperties() {
        if (noauthProperties == null) {
            Map<String, String> props = new HashMap<>();
            props.put("quarkus.oidc-client.enabled", "false");
            noauthProperties = Collections.unmodifiableMap(props);
        }
        return noauthProperties;
    }
}
