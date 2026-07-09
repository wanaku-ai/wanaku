package ai.wanaku.backend.api.v1.tools;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpSyncServer;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.common.ProvisioningHelper;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ProvisionerBridge;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.common.LabelsAwareWanakuEntityBean;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.backend.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.backend.core.persistence.api.WanakuRepository;
import ai.wanaku.backend.mcp.McpServerRegistry;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.v1.PropertySchema;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ToolsBean extends LabelsAwareWanakuEntityBean<ToolReference> {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    McpServerRegistry registry;

    @Inject
    ToolsBridge toolsBridge;

    @Inject
    ProvisionerBridge provisionerBridge;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ToolReferenceRepository> toolReferenceRepositoryInstance;

    private ToolReferenceRepository toolReferenceRepository;

    @PostConstruct
    void init() {
        toolReferenceRepository = toolReferenceRepositoryInstance.get();
    }

    public ToolReference add(ToolReference toolReference) {
        registerTool(toolReference);
        return toolReferenceRepository.persist(toolReference);
    }

    public ToolReference add(ToolPayload toolPayload) {
        provision(toolPayload);
        return add(toolPayload.getPayload());
    }

    private void provision(ToolPayload toolPayload) {
        ToolReference toolReference = toolPayload.getPayload();

        final ProvisioningReference provisioningReference = ProvisioningHelper.provision(
                provisionerBridge,
                toolPayload,
                toolReference.getName(),
                toolReference.getType(),
                ServiceType.TOOL_INVOKER.asValue(),
                (configURI, secretsURI) -> {
                    toolReference.setConfigurationURI(configURI);
                    toolReference.setSecretsURI(secretsURI);
                });

        final Map<String, PropertySchema> serviceProperties = provisioningReference.properties();
        if (serviceProperties != null
                && !serviceProperties.isEmpty()
                && toolReference.getInputSchema() != null
                && toolReference.getInputSchema().getProperties() != null) {
            final Map<String, Property> clientProperties =
                    toolReference.getInputSchema().getProperties();
            for (var serviceProperty : serviceProperties.entrySet()) {
                clientProperties.computeIfAbsent(
                        serviceProperty.getKey(), v -> toProperty(serviceProperty, serviceProperties));
            }
        }
    }

    private static Property toProperty(
            Map.Entry<String, PropertySchema> serviceProperty, Map<String, PropertySchema> serviceProperties) {
        PropertySchema schema = serviceProperties.get(serviceProperty.getKey());
        Property property = new Property();
        property.setDescription(schema.getDescription());
        property.setType(schema.getType());
        return property;
    }

    private void registerTool(ToolReference toolReference) {
        if (!StringHelper.isEmpty(toolReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(toolReference.getNamespace());
            McpSyncServer server = registry.getServerForPath(namespace.getPath());
            ToolsHelper.registerTool(toolReference, server, namespace, toolsBridge::execute);
        } else {
            ToolsHelper.registerTool(toolReference, registry.getPublicServer(), toolsBridge::execute);
        }
    }

    public List<ToolReference> list() {
        return toolReferenceRepository.listAll();
    }

    public List<ToolReference> list(String labelFilter) {
        if (StringHelper.isBlank(labelFilter)) {
            return toolReferenceRepository.listAll();
        }
        return toolReferenceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    void loadTools(@Observes StartupEvent ev) {
        namespacesBean.preload();

        for (ToolReference toolReference : list()) {
            try {
                registerTool(toolReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        e,
                        "Error registering a tool named %s during startup, but it already exists",
                        toolReference.getName());
            }
        }
    }

    public int remove(String name) throws WanakuException {
        ToolReference ref = getByName(name);
        int removed = 0;
        try {
            removed = removeByName(name);
        } finally {
            if (removed > 0) {
                removeToolFromServer(name, ref);
            }
        }
        return removed;
    }

    private void removeToolFromServer(String name, ToolReference ref) {
        if (ref != null && !ai.wanaku.core.util.StringHelper.isEmpty(ref.getNamespace())) {
            try {
                Namespace ns = namespacesBean.getById(ref.getNamespace());
                if (ns != null) {
                    registry.getServerForPath(ns.getPath()).removeTool(name);
                    return;
                }
            } catch (Exception e) {
                LOG.debugf(e, "Could not resolve namespace for tool %s, trying public server", name);
            }
        }
        try {
            registry.getPublicServer().removeTool(name);
        } catch (Exception e) {
            LOG.debugf(e, "Tool %s not found on public server for removal", name);
        }
    }

    public void update(ToolReference resource) {
        toolReferenceRepository.update(resource.getId(), resource);
    }

    public ToolReference getByName(String name) {
        List<ToolReference> tools = toolReferenceRepository.findByName(name);
        return tools.isEmpty() ? null : tools.getFirst();
    }

    @Override
    protected WanakuRepository<ToolReference, String> getRepository() {
        return toolReferenceRepository;
    }

    @Override
    public int removeIf(String labelExpression) throws WanakuException {
        List<ToolReference> toRemove = list(labelExpression);
        toRemove.stream().map(t -> t.getName()).forEach(this::remove);
        return toRemove.size();
    }
}
