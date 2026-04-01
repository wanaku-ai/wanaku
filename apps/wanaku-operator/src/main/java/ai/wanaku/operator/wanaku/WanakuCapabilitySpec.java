package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuCapabilitySpec {
    private WanakuTypes.AuthSpec auth;
    private WanakuTypes.SecretsSpec secrets;
    private String imagePullPolicy;
    private String routerRef;
    private List<CapabilitiesSpec> capabilities;

    public WanakuTypes.AuthSpec getAuth() {
        return auth;
    }

    public void setAuth(WanakuTypes.AuthSpec auth) {
        this.auth = auth;
    }

    public WanakuTypes.SecretsSpec getSecrets() {
        return secrets;
    }

    public void setSecrets(WanakuTypes.SecretsSpec secrets) {
        this.secrets = secrets;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public String getRouterRef() {
        return routerRef;
    }

    public void setRouterRef(String routerRef) {
        this.routerRef = routerRef;
    }

    public List<CapabilitiesSpec> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilitiesSpec> capabilities) {
        this.capabilities = capabilities;
    }

    public static class CapabilitiesSpec {
        private String name;
        private String image;
        private String type;
        private List<WanakuTypes.EnvVar> env;
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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<WanakuTypes.EnvVar> getEnv() {
            return env;
        }

        public void setEnv(List<WanakuTypes.EnvVar> env) {
            this.env = env;
        }

        public String getImagePullPolicy() {
            return imagePullPolicy;
        }

        public void setImagePullPolicy(String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
        }
    }
}
