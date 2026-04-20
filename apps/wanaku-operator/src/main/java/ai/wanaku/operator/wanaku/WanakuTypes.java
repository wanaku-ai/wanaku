package ai.wanaku.operator.wanaku;

import java.util.List;

public final class WanakuTypes {

    private WanakuTypes() {}

    // Deployment mode constants - must match CRD enum values
    public static final String DEPLOYMENT_MODE_IN_CLUSTER = "in-cluster";
    public static final String DEPLOYMENT_MODE_REMOTE = "remote";
    public static final List<String> VALID_DEPLOYMENT_MODES =
            List.of(DEPLOYMENT_MODE_IN_CLUSTER, DEPLOYMENT_MODE_REMOTE);

    // Dependency cache strategy constants - must match CRD enum values
    public static final String CACHE_STRATEGY_IN_MEMORY = "inmemory";
    public static final String CACHE_STRATEGY_INFINISPAN = "infinispan";
    public static final String CACHE_STRATEGY_DISABLED = "disabled";
    public static final List<String> VALID_CACHE_STRATEGIES =
            List.of(CACHE_STRATEGY_IN_MEMORY, CACHE_STRATEGY_INFINISPAN, CACHE_STRATEGY_DISABLED);

    public static class AuthSpec {
        private String authServer;
        private String authProxy;

        public String getAuthServer() {
            return authServer;
        }

        public void setAuthServer(String authServer) {
            this.authServer = authServer;
        }

        public String getAuthProxy() {
            return authProxy;
        }

        public void setAuthProxy(String authProxy) {
            this.authProxy = authProxy;
        }
    }

    public static class SecretsSpec {
        private String oidcCredentialsSecret;

        public String getOidcCredentialsSecret() {
            return oidcCredentialsSecret;
        }

        public void setOidcCredentialsSecret(String oidcCredentialsSecret) {
            this.oidcCredentialsSecret = oidcCredentialsSecret;
        }
    }

    public static class EnvVar {
        private String name;
        private String value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class IngressSpec {
        private String host;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }
}
