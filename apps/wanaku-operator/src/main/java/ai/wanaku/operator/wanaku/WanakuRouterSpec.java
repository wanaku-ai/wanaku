package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuRouterSpec {
    private WanakuTypes.AuthSpec auth;
    private WanakuTypes.SecretsSpec secrets;
    private String imagePullPolicy;
    private WanakuTypes.IngressSpec ingress;
    private RouterSpec router;

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

    public WanakuTypes.IngressSpec getIngress() {
        return ingress;
    }

    public void setIngress(WanakuTypes.IngressSpec ingress) {
        this.ingress = ingress;
    }

    public RouterSpec getRouter() {
        return router;
    }

    public void setRouter(RouterSpec router) {
        this.router = router;
    }

    public static class RouterSpec {
        private String image;
        private List<WanakuTypes.EnvVar> env;
        private String imagePullPolicy;

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
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
