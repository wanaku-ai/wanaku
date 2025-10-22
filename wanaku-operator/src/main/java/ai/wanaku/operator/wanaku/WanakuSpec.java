package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuSpec {
    private AuthSpec auth;
    private SecretsSpec secrets;
    private RouterSpec router;
    private List<CapabilitiesSpec> services;

    public AuthSpec getAuth() {
        return auth;
    }

    public void setAuth(AuthSpec auth) {
        this.auth = auth;
    }

    public SecretsSpec getSecrets() {
        return secrets;
    }

    public void setSecrets(SecretsSpec secrets) {
        this.secrets = secrets;
    }

    public RouterSpec getRouter() {
        return router;
    }

    public void setRouter(RouterSpec router) {
        this.router = router;
    }

    public List<CapabilitiesSpec> getCapabilities() {
        return services;
    }

    public void setCapabilities(List<CapabilitiesSpec> services) {
        this.services = services;
    }

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

    public static class RouterSpec {
        private String image;
        private List<EnvVar> env;

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public List<EnvVar> getEnv() {
            return env;
        }

        public void setEnv(List<EnvVar> env) {
            this.env = env;
        }
    }

    public static class CapabilitiesSpec {
        private String name;
        private String image;
        private String type;
        private List<EnvVar> env;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public List<EnvVar> getEnv() {
            return env;
        }

        public void setEnv(List<EnvVar> env) {
            this.env = env;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
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
}
