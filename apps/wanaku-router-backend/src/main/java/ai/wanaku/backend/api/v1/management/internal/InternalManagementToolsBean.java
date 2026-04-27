package ai.wanaku.backend.api.v1.management.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import ai.wanaku.backend.api.v1.capabilities.CapabilitiesBean;
import ai.wanaku.backend.api.v1.datastores.DataStoresBean;
import ai.wanaku.backend.api.v1.management.statistics.StatisticsBean;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.api.v1.prompts.PromptsBean;
import ai.wanaku.backend.api.v1.resources.ResourcesBean;
import ai.wanaku.backend.api.v1.tools.ToolsBean;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.management.ServerInfo;
import ai.wanaku.core.services.api.StaleCapabilityInfo;
import ai.wanaku.core.util.VersionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class InternalManagementToolsBean {
    private static final Logger LOG = Logger.getLogger(InternalManagementToolsBean.class);
    private static final String INTERNAL_NAMESPACE = "wanaku-internal";

    @Inject
    ToolManager toolManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    StatisticsBean statisticsBean;

    @Inject
    CapabilitiesBean capabilitiesBean;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    ToolsBean toolsBean;

    @Inject
    ResourcesBean resourcesBean;

    @Inject
    PromptsBean promptsBean;

    @Inject
    DataStoresBean dataStoresBean;

    void loadInternalTools(@Observes StartupEvent ev) {
        registerTool(infoTool(), ignored -> jsonResponse(this::buildServerInfo));
        registerTool(statisticsTool(), ignored -> jsonResponse(statisticsBean::getStatistics));
        registerTool(fleetStatusTool(), ignored -> jsonResponse(capabilitiesBean::getFleetStatus));
        registerTool(capabilitiesTool(), ignored -> jsonResponse(capabilitiesBean::listAllCapabilities));
        registerTool(staleCapabilitiesTool(), args -> jsonResponse(() -> buildStaleCapabilities(args)));
        registerTool(staleCapabilitiesCleanupTool(), args -> jsonResponse(() -> buildStaleCapabilitiesCleanup(args)));
        registerTool(namespacesTool(), args -> jsonResponse(() -> buildNamespaces(args)));
        registerTool(namespaceGetTool(), args -> jsonResponse(() -> buildNamespaceGet(args)));
        registerTool(namespaceCreateTool(), args -> jsonResponse(() -> buildNamespaceCreate(args)));
        registerTool(namespaceUpdateTool(), args -> jsonResponse(() -> buildNamespaceUpdate(args)));
        registerTool(namespaceDeleteTool(), args -> jsonResponse(() -> buildNamespaceDelete(args)));
        registerTool(staleNamespacesTool(), args -> jsonResponse(() -> buildStaleNamespaces(args)));
        registerTool(staleNamespacesCleanupTool(), args -> jsonResponse(() -> buildStaleNamespacesCleanup(args)));
        registerTool(toolsTool(), args -> jsonResponse(() -> buildTools(args)));
        registerTool(toolGetTool(), args -> jsonResponse(() -> buildToolGet(args)));
        registerTool(toolAddTool(), args -> jsonResponse(() -> buildToolAdd(args)));
        registerTool(toolUpdateTool(), args -> jsonResponse(() -> buildToolUpdate(args)));
        registerTool(toolRemoveTool(), args -> jsonResponse(() -> buildToolRemove(args)));
        registerTool(toolsStateTool(), ignored -> jsonResponse(capabilitiesBean::toolsState));
        registerTool(resourcesTool(), args -> jsonResponse(() -> buildResources(args)));
        registerTool(resourceGetTool(), args -> jsonResponse(() -> buildResourceGet(args)));
        registerTool(resourceAddTool(), args -> jsonResponse(() -> buildResourceAdd(args)));
        registerTool(resourceUpdateTool(), args -> jsonResponse(() -> buildResourceUpdate(args)));
        registerTool(resourceRemoveTool(), args -> jsonResponse(() -> buildResourceRemove(args)));
        registerTool(resourcesStateTool(), ignored -> jsonResponse(capabilitiesBean::resourcesState));
        registerTool(promptsTool(), ignored -> jsonResponse(this::buildPrompts));
        registerTool(dataStoresTool(), args -> jsonResponse(() -> buildDataStores(args)));
        registerTool(dataStoreGetTool(), args -> jsonResponse(() -> buildDataStoreGet(args)));
        registerTool(dataStoreAddTool(), args -> jsonResponse(() -> buildDataStoreAdd(args)));
        registerTool(dataStoreUpdateTool(), args -> jsonResponse(() -> buildDataStoreUpdate(args)));
        registerTool(dataStoreRemoveByIdTool(), args -> jsonResponse(() -> buildDataStoreRemoveById(args)));
        registerTool(dataStoreRemoveByNameTool(), args -> jsonResponse(() -> buildDataStoreRemoveByName(args)));

        LOG.info("Registered built-in management MCP tools in wanaku-internal");
    }

    private void registerTool(ToolReference toolReference, ToolHandler handler) {
        ToolsHelper.registerTool(
                toolReference,
                toolManager,
                internalNamespace,
                (toolArguments, ignored) -> handler.apply(toolArguments.args()));
    }

    private Uni<ToolResponse> jsonResponse(Supplier<Object> supplier) {
        return Uni.createFrom().item(() -> {
            try {
                return ToolResponse.success(List.of(new TextContent(objectMapper.writeValueAsString(supplier.get()))));
            } catch (Exception e) {
                LOG.errorf(e, "Internal management tool failed");
                return ToolResponse.error(e.getMessage());
            }
        });
    }

    private ToolReference infoTool() {
        return tool("wanaku-internal-info", "Return the Wanaku router version and server information", null);
    }

    private ToolReference statisticsTool() {
        return tool("wanaku-internal-statistics", "Return aggregated router statistics", null);
    }

    private ToolReference fleetStatusTool() {
        return tool("wanaku-internal-fleet-status", "Return the aggregated capability fleet status", null);
    }

    private ToolReference capabilitiesTool() {
        return tool("wanaku-internal-capabilities", "List all registered capabilities", null);
    }

    private ToolReference staleCapabilitiesTool() {
        return tool(
                "wanaku-internal-stale-capabilities",
                "List stale capabilities using an age threshold and inactivity filter",
                schema(Map.of(
                        "maxAgeSeconds", property("integer", "Maximum age in seconds since last seen"),
                        "inactiveOnly", property("boolean", "Only return capabilities marked as inactive"))));
    }

    private ToolReference staleCapabilitiesCleanupTool() {
        return tool(
                "wanaku-internal-stale-capabilities-cleanup",
                "Remove stale capabilities using an age threshold and inactivity filter",
                schema(Map.of(
                        "maxAgeSeconds", property("integer", "Maximum age in seconds since last seen"),
                        "inactiveOnly", property("boolean", "Only remove capabilities marked as inactive"))));
    }

    private ToolReference namespacesTool() {
        return tool(
                "wanaku-internal-namespaces",
                "List registered namespaces",
                schema(Map.of("labelFilter", property("string", "Optional label expression filter"))));
    }

    private ToolReference namespaceGetTool() {
        return tool(
                "wanaku-internal-namespace-get",
                "Get a namespace by ID",
                schema(Map.of("id", property("string", "Namespace ID"))));
    }

    private ToolReference namespaceCreateTool() {
        return tool(
                "wanaku-internal-namespace-create",
                "Create a namespace",
                schema(Map.of("namespace", objectProperty("Namespace payload to create"))));
    }

    private ToolReference namespaceUpdateTool() {
        return tool(
                "wanaku-internal-namespace-update",
                "Update a namespace by ID",
                schema(Map.of(
                        "id", property("string", "Namespace ID"),
                        "namespace", objectProperty("Namespace payload with updated values"))));
    }

    private ToolReference namespaceDeleteTool() {
        return tool(
                "wanaku-internal-namespace-delete",
                "Delete a namespace by ID",
                schema(Map.of("id", property("string", "Namespace ID"))));
    }

    private ToolReference staleNamespacesTool() {
        return tool(
                "wanaku-internal-stale-namespaces",
                "List stale namespaces using an age threshold and allocation filter",
                schema(Map.of(
                        "maxAgeSeconds", property("integer", "Maximum age in seconds since preallocation"),
                        "unassignedOnly", property("boolean", "Only return namespaces without an assigned name"),
                        "includeUnlabeled", property("boolean", "Treat namespaces without labels as stale"))));
    }

    private ToolReference staleNamespacesCleanupTool() {
        return tool(
                "wanaku-internal-stale-namespaces-cleanup",
                "Remove stale namespaces using an age threshold and allocation filter",
                schema(Map.of(
                        "maxAgeSeconds", property("integer", "Maximum age in seconds since preallocation"),
                        "unassignedOnly", property("boolean", "Only remove namespaces without an assigned name"),
                        "includeUnlabeled", property("boolean", "Treat namespaces without labels as stale"))));
    }

    private ToolReference toolsTool() {
        return tool(
                "wanaku-internal-tools",
                "List registered tools",
                schema(Map.of("labelFilter", property("string", "Optional label expression filter"))));
    }

    private ToolReference toolGetTool() {
        return tool(
                "wanaku-internal-tool-get",
                "Get a tool by name",
                schema(Map.of("name", property("string", "Tool name"))));
    }

    private ToolReference toolAddTool() {
        return tool(
                "wanaku-internal-tool-add",
                "Create and register a tool",
                schema(Map.of("tool", objectProperty("ToolReference payload to create"))));
    }

    private ToolReference toolUpdateTool() {
        return tool(
                "wanaku-internal-tool-update",
                "Update a tool by ID",
                schema(Map.of("tool", objectProperty("ToolReference payload with updated values"))));
    }

    private ToolReference toolRemoveTool() {
        return tool(
                "wanaku-internal-tool-remove",
                "Remove a tool by name",
                schema(Map.of("name", property("string", "Tool name"))));
    }

    private ToolReference toolsStateTool() {
        return tool("wanaku-internal-tools-state", "Return tool capability health by service", null);
    }

    private ToolReference resourcesTool() {
        return tool(
                "wanaku-internal-resources",
                "List registered resources",
                schema(Map.of("labelFilter", property("string", "Optional label expression filter"))));
    }

    private ToolReference resourcesStateTool() {
        return tool("wanaku-internal-resources-state", "Return resource capability health by service", null);
    }

    private ToolReference resourceGetTool() {
        return tool(
                "wanaku-internal-resource-get",
                "Get a resource by name",
                schema(Map.of("name", property("string", "Resource name"))));
    }

    private ToolReference resourceAddTool() {
        return tool(
                "wanaku-internal-resource-add",
                "Create and expose a resource",
                schema(Map.of("resource", objectProperty("ResourceReference payload to create"))));
    }

    private ToolReference resourceUpdateTool() {
        return tool(
                "wanaku-internal-resource-update",
                "Update a resource by ID",
                schema(Map.of("resource", objectProperty("ResourceReference payload with updated values"))));
    }

    private ToolReference resourceRemoveTool() {
        return tool(
                "wanaku-internal-resource-remove",
                "Remove a resource by name",
                schema(Map.of("name", property("string", "Resource name"))));
    }

    private ToolReference promptsTool() {
        return tool("wanaku-internal-prompts", "List registered prompts", null);
    }

    private ToolReference dataStoresTool() {
        return tool(
                "wanaku-internal-data-stores",
                "List registered data stores",
                schema(Map.of("labelFilter", property("string", "Optional label expression filter"))));
    }

    private ToolReference dataStoreGetTool() {
        return tool(
                "wanaku-internal-data-store-get",
                "Get a data store by ID or name",
                schema(Map.of(
                        "id", property("string", "Data store ID"),
                        "name", property("string", "Data store name"))));
    }

    private ToolReference dataStoreAddTool() {
        return tool(
                "wanaku-internal-data-store-add",
                "Create a data store",
                schema(Map.of("dataStore", objectProperty("DataStore payload to create"))));
    }

    private ToolReference dataStoreUpdateTool() {
        return tool(
                "wanaku-internal-data-store-update",
                "Update a data store",
                schema(Map.of("dataStore", objectProperty("DataStore payload with updated values"))));
    }

    private ToolReference dataStoreRemoveByIdTool() {
        return tool(
                "wanaku-internal-data-store-remove-by-id",
                "Remove a data store by ID",
                schema(Map.of("id", property("string", "Data store ID"))));
    }

    private ToolReference dataStoreRemoveByNameTool() {
        return tool(
                "wanaku-internal-data-store-remove-by-name",
                "Remove data stores by name",
                schema(Map.of("name", property("string", "Data store name"))));
    }

    private ToolReference tool(String name, String description, InputSchema schema) {
        ToolReference toolReference = new ToolReference();
        toolReference.setName(name);
        toolReference.setDescription(description);
        toolReference.setType(INTERNAL_NAMESPACE);
        toolReference.setNamespace(INTERNAL_NAMESPACE);
        toolReference.setInputSchema(schema);
        return toolReference;
    }

    private InputSchema schema(Map<String, Property> properties) {
        InputSchema schema = new InputSchema();
        schema.setType("object");
        schema.getProperties().putAll(properties);
        return schema;
    }

    private Property property(String type, String description) {
        Property property = new Property();
        property.setType(type);
        property.setDescription(description);
        return property;
    }

    private Property objectProperty(String description) {
        return property("object", description);
    }

    private static final Namespace internalNamespace = createInternalNamespace();

    private static Namespace createInternalNamespace() {
        Namespace namespace = new Namespace();
        namespace.setName(INTERNAL_NAMESPACE);
        namespace.setPath(INTERNAL_NAMESPACE);
        return namespace;
    }

    private ServerInfo buildServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setVersion(VersionHelper.VERSION);
        return serverInfo;
    }

    private List<StaleCapabilityInfo> buildStaleCapabilities(Map<String, Object> args) {
        long maxAgeSeconds = toLong(args.get("maxAgeSeconds"), 86400L);
        boolean inactiveOnly = toBoolean(args.get("inactiveOnly"));
        return capabilitiesBean.listStaleCapabilities(maxAgeSeconds, inactiveOnly);
    }

    private Integer buildStaleCapabilitiesCleanup(Map<String, Object> args) {
        long maxAgeSeconds = toLong(args.get("maxAgeSeconds"), 86400L);
        boolean inactiveOnly = toBoolean(args.get("inactiveOnly"));
        return capabilitiesBean
                .cleanupStaleCapabilities(maxAgeSeconds, inactiveOnly)
                .size();
    }

    private List<Namespace> buildNamespaces(Map<String, Object> args) {
        return namespacesBean.list(toStringArg(args.get("labelFilter")));
    }

    private Namespace buildNamespaceGet(Map<String, Object> args) {
        String id = toRequiredStringArg(args, "id");
        Namespace namespace = namespacesBean.getById(id);
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace not found: " + id);
        }
        return namespace;
    }

    private Namespace buildNamespaceCreate(Map<String, Object> args) {
        Namespace namespace = toEntity(args.get("namespace"), Namespace.class);
        return namespacesBean.create(namespace);
    }

    private Namespace buildNamespaceUpdate(Map<String, Object> args) {
        String id = toRequiredStringArg(args, "id");
        Namespace namespace = toEntity(args.get("namespace"), Namespace.class);
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace payload is required");
        }
        if (namespace.getId() == null) {
            namespace.setId(id);
        }
        if (!namespacesBean.update(id, namespace)) {
            throw new IllegalArgumentException("Namespace not found or update refused: " + id);
        }
        return namespacesBean.getById(id);
    }

    private Boolean buildNamespaceDelete(Map<String, Object> args) {
        return namespacesBean.deleteById(toRequiredStringArg(args, "id"));
    }

    private List<Namespace> buildStaleNamespaces(Map<String, Object> args) {
        long maxAgeSeconds = toLong(args.get("maxAgeSeconds"), 604800L);
        boolean unassignedOnly = toBoolean(args.get("unassignedOnly"), true);
        boolean includeUnlabeled = toBoolean(args.get("includeUnlabeled"));
        return namespacesBean.listStale(maxAgeSeconds, unassignedOnly, includeUnlabeled);
    }

    private Integer buildStaleNamespacesCleanup(Map<String, Object> args) {
        long maxAgeSeconds = toLong(args.get("maxAgeSeconds"), 604800L);
        boolean unassignedOnly = toBoolean(args.get("unassignedOnly"), true);
        boolean includeUnlabeled = toBoolean(args.get("includeUnlabeled"));
        return namespacesBean.cleanupStale(maxAgeSeconds, unassignedOnly, includeUnlabeled);
    }

    private List<ToolReference> buildTools(Map<String, Object> args) {
        return toolsBean.list(toStringArg(args.get("labelFilter")));
    }

    private ToolReference buildToolGet(Map<String, Object> args) {
        String name = toRequiredStringArg(args, "name");
        ToolReference tool = toolsBean.getByName(name);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + name);
        }
        return tool;
    }

    private ToolReference buildToolAdd(Map<String, Object> args) {
        ToolReference tool = toEntity(args.get("tool"), ToolReference.class);
        if (tool == null) {
            throw new IllegalArgumentException("Tool payload is required");
        }
        return toolsBean.add(tool);
    }

    private ToolReference buildToolUpdate(Map<String, Object> args) {
        ToolReference tool = toEntity(args.get("tool"), ToolReference.class);
        if (tool == null || tool.getId() == null) {
            throw new IllegalArgumentException("Tool payload with id is required");
        }
        toolsBean.update(tool);
        return tool;
    }

    private Integer buildToolRemove(Map<String, Object> args) {
        return toolsBean.remove(toRequiredStringArg(args, "name"));
    }

    private List<ResourceReference> buildResources(Map<String, Object> args) {
        return resourcesBean.list(toStringArg(args.get("labelFilter")));
    }

    private ResourceReference buildResourceGet(Map<String, Object> args) {
        String name = toRequiredStringArg(args, "name");
        ResourceReference resource = resourcesBean.getByName(name);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found: " + name);
        }
        return resource;
    }

    private ResourceReference buildResourceAdd(Map<String, Object> args) {
        ResourceReference resource = toEntity(args.get("resource"), ResourceReference.class);
        if (resource == null) {
            throw new IllegalArgumentException("Resource payload is required");
        }
        return resourcesBean.expose(resource);
    }

    private ResourceReference buildResourceUpdate(Map<String, Object> args) {
        ResourceReference resource = toEntity(args.get("resource"), ResourceReference.class);
        if (resource == null || resource.getId() == null) {
            throw new IllegalArgumentException("Resource payload with id is required");
        }
        resourcesBean.update(resource);
        return resource;
    }

    private Integer buildResourceRemove(Map<String, Object> args) {
        return resourcesBean.remove(toRequiredStringArg(args, "name"));
    }

    private List<PromptReference> buildPrompts() {
        return promptsBean.list();
    }

    private List<DataStore> buildDataStores(Map<String, Object> args) {
        return dataStoresBean.list(toStringArg(args.get("labelFilter")));
    }

    private DataStore buildDataStoreGet(Map<String, Object> args) {
        String id = toStringArg(args.get("id"));
        if (id != null && !id.isBlank()) {
            DataStore dataStore = dataStoresBean.findById(id);
            if (dataStore != null) {
                return dataStore;
            }
        }

        String name = toStringArg(args.get("name"));
        if (name != null && !name.isBlank()) {
            List<DataStore> dataStores = dataStoresBean.findByName(name);
            if (!dataStores.isEmpty()) {
                return dataStores.getFirst();
            }
        }

        throw new IllegalArgumentException("Data store not found");
    }

    private DataStore buildDataStoreAdd(Map<String, Object> args) {
        DataStore dataStore = toEntity(args.get("dataStore"), DataStore.class);
        if (dataStore == null) {
            throw new IllegalArgumentException("Data store payload is required");
        }
        return dataStoresBean.add(dataStore);
    }

    private DataStore buildDataStoreUpdate(Map<String, Object> args) {
        DataStore dataStore = toEntity(args.get("dataStore"), DataStore.class);
        if (dataStore == null || dataStore.getId() == null) {
            throw new IllegalArgumentException("Data store payload with id is required");
        }
        dataStoresBean.update(dataStore);
        return dataStoresBean.findById(dataStore.getId());
    }

    private Integer buildDataStoreRemoveById(Map<String, Object> args) {
        return dataStoresBean.removeById(toRequiredStringArg(args, "id"));
    }

    private Integer buildDataStoreRemoveByName(Map<String, Object> args) {
        return dataStoresBean.remove(toRequiredStringArg(args, "name"));
    }

    private String toStringArg(Object value) {
        return value == null ? null : value.toString();
    }

    private String toRequiredStringArg(Map<String, Object> args, String key) {
        String value = toStringArg(args.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private <T> T toEntity(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return toBoolean(value);
    }

    @FunctionalInterface
    private interface ToolHandler {
        Uni<ToolResponse> apply(Map<String, Object> args);
    }
}
