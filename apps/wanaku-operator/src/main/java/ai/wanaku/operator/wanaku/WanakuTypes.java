package ai.wanaku.operator.wanaku;

public final class WanakuTypes {

    private WanakuTypes() {}

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
