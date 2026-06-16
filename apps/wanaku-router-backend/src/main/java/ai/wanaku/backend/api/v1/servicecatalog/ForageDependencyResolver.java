package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Collections;
import org.jboss.logging.Logger;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;

@ApplicationScoped
public class ForageDependencyResolver {
    private static final Logger LOG = Logger.getLogger(ForageDependencyResolver.class);

    public Collection<String> resolveGavs(String beanKind) {
        try {
            return ForageCatalogReader.getInstance().getBeanGavs(beanKind);
        } catch (Exception e) {
            LOG.warnf("Failed to resolve Forage dependencies for bean kind '%s': %s", beanKind, e.getMessage());
            return Collections.emptySet();
        }
    }
}
