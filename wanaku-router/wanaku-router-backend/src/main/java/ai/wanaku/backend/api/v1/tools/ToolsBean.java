package ai.wanaku.backend.api.v1.tools;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import io.micrometer.common.util.StringUtils;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.backend.api.v1.namespaces.NamespacesBean;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.common.LabelsAwareWanakuEntityBean;
import ai.wanaku.backend.common.ToolsHelper;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.exchange.v1.PropertySchema;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
public class ToolsBean extends LabelsAwareWanakuEntityBean<ToolReference> {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsBridge toolsBridge;

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    Instance<ToolReferenceRepository> toolReferenceRepositoryInstance;

    private ToolReferenceRepository toolReferenceRepository;
    private boolean async = false;

    @PostConstruct
    void init() {
        toolReferenceRepository = toolReferenceRepositoryInstance.get();

        final Boolean value = ConfigProvider.getConfig().getValue("wanaku.router.async", Boolean.class);
        async = value != null && value;
        if (async) {
            LOG.info("Serving tools in async mode with executor");
        }
    }

    public ToolReference add(ToolReference toolReference) {
        // then registers the tool with the tool manager
        registerTool(toolReference);

        // if all goes well, persist the tool, so it can be loaded back when restarting
        return toolReferenceRepository.persist(toolReference);
    }

    public ToolReference add(ToolPayload toolPayload) {
        // First, provision the tool (i.e.: configuration, secrets, etc) in the target defined by the remote
        provision(toolPayload);

        return add(toolPayload.getPayload());
    }

    private void provision(ToolPayload toolPayload) {
        final ProvisioningReference provisioningReference = toolsBridge.provision(toolPayload);

        final Map<String, PropertySchema> serviceProperties = provisioningReference.properties();

        ToolReference toolReference = toolPayload.getPayload();
        final Map<String, Property> clientProperties =
                toolReference.getInputSchema().getProperties();
        for (var serviceProperty : serviceProperties.entrySet()) {
            clientProperties.computeIfAbsent(
                    serviceProperty.getKey(), v -> toProperty(serviceProperty, serviceProperties));
        }

        toolReference.setConfigurationURI(
                provisioningReference.configurationURI().toString());
        toolReference.setSecretsURI(provisioningReference.secretsURI().toString());
    }

    private static Property toProperty(
            Map.Entry<String, PropertySchema> serviceProperty, Map<String, PropertySchema> serviceProperties) {
        PropertySchema schema = serviceProperties.get(serviceProperty.getKey());
        Property property = new Property();

        property.setDescription(schema.getDescription());
        property.setType(schema.getType());

        return property;
    }

    private void registerTool(ToolReference toolReference) throws ToolNotFoundException {
        if (async) {
            doRegisterToolAsync(toolReference);
        } else {
            doRegisterTool(toolReference);
        }
    }

    @Deprecated
    private void doRegisterTool(ToolReference toolReference) {
        if (!StringHelper.isEmpty(toolReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(toolReference.getNamespace());
            ToolsHelper.registerTool(toolReference, toolManager, namespace, toolsBridge::execute);
        } else {
            ToolsHelper.registerTool(toolReference, toolManager, toolsBridge::execute);
        }
    }

    private void doRegisterToolAsync(ToolReference toolReference) {
        if (!StringHelper.isEmpty(toolReference.getNamespace())) {
            final Namespace namespace = namespacesBean.alocateNamespace(toolReference.getNamespace());
            ToolsHelper.registerToolAsync(toolReference, toolManager, namespace, toolsBridge::executeAsync);
        } else {
            ToolsHelper.registerToolAsync(toolReference, toolManager, toolsBridge::executeAsync);
        }
    }

    public List<ToolReference> list() {
        return toolReferenceRepository.listAll();
    }

    public List<ToolReference> list(String labelFilter) {
        if (StringUtils.isBlank(labelFilter)) {
            return toolReferenceRepository.listAll();
        }
        return toolReferenceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    void loadTools(@Observes StartupEvent ev) {
        // Preload data
        namespacesBean.preload();

        for (ToolReference toolReference : list()) {
            try {
                registerTool(toolReference);
            } catch (EntityAlreadyExistsException e) {
                LOG.errorf(
                        "Error registering a tool named %s during startup, but it already exists",
                        toolReference.getName());
            }
        }
    }

    public int remove(String name) throws WanakuException {
        int removed = 0;
        try {
            removed = removeByName(name);
        } finally {
            if (removed > 0) {
                toolManager.removeTool(name);
            }
        }

        return removed;
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
