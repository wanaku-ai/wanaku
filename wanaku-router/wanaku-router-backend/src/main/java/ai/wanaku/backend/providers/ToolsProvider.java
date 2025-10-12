package ai.wanaku.backend.providers;

import ai.wanaku.backend.proxies.InvokerProxy;
import ai.wanaku.backend.resolvers.WanakuToolsResolver;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.VersionHelper;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * A provider for tools resolvers
 */
@ApplicationScoped
public class ToolsProvider extends AbstractProvider<ToolsResolver> {
    private static final Logger LOG = Logger.getLogger(ToolsProvider.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    @Produces
    @Override
    @DefaultBean
    ToolsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        return new WanakuToolsResolver(new InvokerProxy(resolver));
    }
}
