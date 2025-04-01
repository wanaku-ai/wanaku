package ai.wanaku.routers;

import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.VersionHelper;
import java.io.File;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopToolsResolver;
import ai.wanaku.routers.config.WanakuRouterConfig;
import ai.wanaku.routers.proxies.tools.InvokerProxy;
import ai.wanaku.routers.resolvers.WanakuToolsResolver;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import static ai.wanaku.core.mcp.common.resolvers.Resolver.DEFAULT_TOOLS_INDEX_FILE_NAME;

/**
 * A provider for tools resolvers
 */
@ApplicationScoped
public class ToolsProvider extends AbstractProvider<ToolsResolver> {
    private static final Logger LOG = Logger.getLogger(ToolsProvider.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    WanakuRouterConfig config;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    @Produces
    @IfBuildProfile(anyOf = {"dev", "test"})
    public ToolsResolver devResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        File resourcesIndexFile = initializeResourcesIndex(config.indexesPath(), DEFAULT_TOOLS_INDEX_FILE_NAME);
        LOG.infof("Using resources index file: %s", resourcesIndexFile.getAbsolutePath());

        return new WanakuToolsResolver(new InvokerProxy(serviceRegistry));
    }

    @Produces
    @Override
    @DefaultBean
    ToolsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        File resourcesIndexFile = initializeIndex();

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        return new WanakuToolsResolver(new InvokerProxy(serviceRegistry));
    }

    @Override
    protected File initializeIndex() {
        String defaultValue = config.indexesPath();
        String value = parseResult.matchedOptionValue("indexes-path", defaultValue);

        String indexPath = value.replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_TOOLS_INDEX_FILE_NAME);
    }
}
