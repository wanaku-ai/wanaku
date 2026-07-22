package ai.wanaku.operator.wanaku;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public class WanakuCamelRouteSpec {
    private static final String DEFAULT_CIC_IMAGE = "quay.io/wanaku/camel-integration-capability:latest";

    private String routerRef;
    private String image;
    private String imagePullPolicy;
    private JsonNode route;
    private McpSpec mcp;
    private Map<String, String> properties;

    public String getRouterRef() {
        return routerRef;
    }

    public void setRouterRef(String routerRef) {
        this.routerRef = routerRef;
    }

    public String getImage() {
        return (image != null && !image.isBlank()) ? image : DEFAULT_CIC_IMAGE;
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

    public JsonNode getRoute() {
        return route;
    }

    public void setRoute(JsonNode route) {
        this.route = route;
    }

    public McpSpec getMcp() {
        return mcp;
    }

    public void setMcp(McpSpec mcp) {
        this.mcp = mcp;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public static class McpSpec {
        private List<ToolSpec> tools;
        private List<ResourceSpec> resources;

        public List<ToolSpec> getTools() {
            return tools;
        }

        public void setTools(List<ToolSpec> tools) {
            this.tools = tools;
        }

        public List<ResourceSpec> getResources() {
            return resources;
        }

        public void setResources(List<ResourceSpec> resources) {
            this.resources = resources;
        }
    }

    public static class ToolSpec {
        private String name;
        private String routeId;
        private String description;
        private List<PropertySpec> properties;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<PropertySpec> getProperties() {
            return properties;
        }

        public void setProperties(List<PropertySpec> properties) {
            this.properties = properties;
        }
    }

    public static class ResourceSpec {
        private String name;
        private String routeId;
        private String description;
        private String uri;
        private String mimeType;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    public static class PropertySpec {
        private String name;
        private String type;
        private String description;
        private boolean required;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
