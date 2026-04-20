package ai.wanaku.operator.wanaku;

import java.util.List;

public class WanakuCodeExecutionEngineSpec {
    private WanakuTypes.AuthSpec auth;
    private WanakuTypes.SecretsSpec secrets;
    private String routerRef;
    private String deploymentMode = "in-cluster";
    private String engineType = "camel";
    private String languageName;
    private String image;
    private String imagePullPolicy;
    private Integer port = 9190;
    private List<WanakuTypes.EnvVar> env;
    private SecuritySpec security;
    private DependencyCacheSpec dependencyCache;
    private ResourceSpec resources;
    private RemoteSpec remote;

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

    public String getRouterRef() {
        return routerRef;
    }

    public void setRouterRef(String routerRef) {
        this.routerRef = routerRef;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public List<WanakuTypes.EnvVar> getEnv() {
        return env;
    }

    public void setEnv(List<WanakuTypes.EnvVar> env) {
        this.env = env;
    }

    public SecuritySpec getSecurity() {
        return security;
    }

    public void setSecurity(SecuritySpec security) {
        this.security = security;
    }

    public DependencyCacheSpec getDependencyCache() {
        return dependencyCache;
    }

    public void setDependencyCache(DependencyCacheSpec dependencyCache) {
        this.dependencyCache = dependencyCache;
    }

    public ResourceSpec getResources() {
        return resources;
    }

    public void setResources(ResourceSpec resources) {
        this.resources = resources;
    }

    public RemoteSpec getRemote() {
        return remote;
    }

    public void setRemote(RemoteSpec remote) {
        this.remote = remote;
    }

    public static class SecuritySpec {
        private List<String> componentAllowlist;
        private List<String> componentBlocklist;
        private List<String> endpointAllowlist;
        private List<String> endpointBlocklist;
        private List<String> routeAllowlist;
        private List<String> routeBlocklist;

        public List<String> getComponentAllowlist() {
            return componentAllowlist;
        }

        public void setComponentAllowlist(List<String> componentAllowlist) {
            this.componentAllowlist = componentAllowlist;
        }

        public List<String> getComponentBlocklist() {
            return componentBlocklist;
        }

        public void setComponentBlocklist(List<String> componentBlocklist) {
            this.componentBlocklist = componentBlocklist;
        }

        public List<String> getEndpointAllowlist() {
            return endpointAllowlist;
        }

        public void setEndpointAllowlist(List<String> endpointAllowlist) {
            this.endpointAllowlist = endpointAllowlist;
        }

        public List<String> getEndpointBlocklist() {
            return endpointBlocklist;
        }

        public void setEndpointBlocklist(List<String> endpointBlocklist) {
            this.endpointBlocklist = endpointBlocklist;
        }

        public List<String> getRouteAllowlist() {
            return routeAllowlist;
        }

        public void setRouteAllowlist(List<String> routeAllowlist) {
            this.routeAllowlist = routeAllowlist;
        }

        public List<String> getRouteBlocklist() {
            return routeBlocklist;
        }

        public void setRouteBlocklist(List<String> routeBlocklist) {
            this.routeBlocklist = routeBlocklist;
        }
    }

    public static class DependencyCacheSpec {
        private String strategy = "inmemory";
        private Boolean enabled = true;
        private String cacheName;
        private String templateNamespace;
        private String templatePrefix;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getCacheName() {
            return cacheName;
        }

        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getTemplateNamespace() {
            return templateNamespace;
        }

        public void setTemplateNamespace(String templateNamespace) {
            this.templateNamespace = templateNamespace;
        }

        public String getTemplatePrefix() {
            return templatePrefix;
        }

        public void setTemplatePrefix(String templatePrefix) {
            this.templatePrefix = templatePrefix;
        }
    }

    public static class ResourceSpec {
        private String cpuRequest;
        private String memoryRequest;
        private String cpuLimit;
        private String memoryLimit;

        public String getCpuRequest() {
            return cpuRequest;
        }

        public void setCpuRequest(String cpuRequest) {
            this.cpuRequest = cpuRequest;
        }

        public String getMemoryRequest() {
            return memoryRequest;
        }

        public void setMemoryRequest(String memoryRequest) {
            this.memoryRequest = memoryRequest;
        }

        public String getCpuLimit() {
            return cpuLimit;
        }

        public void setCpuLimit(String cpuLimit) {
            this.cpuLimit = cpuLimit;
        }

        public String getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
        }
    }

    public static class RemoteSpec {
        private String host;
        private Integer port;
        private String scheme = "http";
        private String path;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
