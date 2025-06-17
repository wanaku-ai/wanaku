package ai.wanaku.core.runtime.camel;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.capabilities.common.ServiceOptions;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EndpointOptions implements ServiceOptions {
    private static final Logger LOG = Logger.getLogger(EndpointOptions.class);

    @Override
    public Map<String, String> merge(String serviceName, Map<String, String> staticOpt) {
        Objects.requireNonNull(serviceName, "The component name must not be null");

        CamelCatalog catalog = new DefaultCamelCatalog(true);

        final ComponentModel componentModel = catalog.componentModel(serviceName);
        if (componentModel == null) {
            LOG.warnf("No component model found for component: %s", serviceName);
            return Map.of();
        }
        final List<ComponentModel.EndpointOptionModel> options = componentModel.getEndpointParameterOptions();
        for (BaseOptionModel option : options) {
            if (option.getLabel().contains("consumer") || option.getLabel().contains("common") ||
                    option.getGroup().contains("common") || option.getLabel().contains("security")) {
                staticOpt.put(option.getName(), option.getDescription());
            }
        }

        return staticOpt;
    }

}
