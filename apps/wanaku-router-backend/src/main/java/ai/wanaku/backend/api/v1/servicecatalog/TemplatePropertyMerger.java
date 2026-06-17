package ai.wanaku.backend.api.v1.servicecatalog;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.jboss.logging.Logger;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

final class TemplatePropertyMerger {
    private static final Logger LOG = Logger.getLogger(TemplatePropertyMerger.class);

    private TemplatePropertyMerger() {}

    static Map<String, String> merge(
            Map<String, String> templateFileContents, ServiceCatalogIndex index, Map<String, String> userProperties) {
        Map<String, String> merged = new HashMap<>();

        for (String system : index.getServiceNames()) {
            String propertiesPath = ServiceTemplateBean.resolvePropertiesPath(index, system, templateFileContents);
            if (propertiesPath == null) {
                continue;
            }
            String content = templateFileContents.get(propertiesPath);
            if (content == null) {
                continue;
            }
            try {
                Properties props = new Properties();
                props.load(new StringReader(content));
                for (String key : props.stringPropertyNames()) {
                    merged.put(key, props.getProperty(key));
                }
            } catch (IOException e) {
                LOG.warnf("Failed to parse properties for system '%s': %s", system, e.getMessage());
            }
        }

        merged.putAll(userProperties);
        return merged;
    }
}
