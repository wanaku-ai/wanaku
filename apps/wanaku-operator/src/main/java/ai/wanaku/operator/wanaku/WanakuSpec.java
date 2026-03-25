package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuSpec {
    private AuthSpec auth;
    private SecretsSpec secrets;
    private RouterSpec router;
    private List<CapabilitiesSpec> capabilities;
    private String imagePullPolicy;
    private IngressSpec ingress;

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

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
        return capabilities;
    }

    public void setCapabilities(List<CapabilitiesSpec> capabilities) {
        this.capabilities = capabilities;
    }

    public IngressSpec getIngress() {
        return ingress;
    }

    public void setIngress(IngressSpec ingress) {
        this.ingress = ingress;
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
        private String imagePullPolicy;

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

        public String getImagePullPolicy() {
            return imagePullPolicy;
        }

        public void setImagePullPolicy(String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
        }
    }

    public static class CapabilitiesSpec {
        private String name;
        private String image;
        private String type;
        private List<EnvVar> env;

        public String getImagePullPolicy() {
            return imagePullPolicy;
        }

        public void setImagePullPolicy(String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
        }

        private String imagePullPolicy;

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
