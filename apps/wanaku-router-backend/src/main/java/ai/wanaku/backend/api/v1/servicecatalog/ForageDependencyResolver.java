package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.jboss.logging.Logger;
import io.kaoto.forage.catalog.reader.ForageCatalogReader;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

@ApplicationScoped
public class ForageDependencyResolver {
    private static final Logger LOG = Logger.getLogger(ForageDependencyResolver.class);
    private static final ForageCatalogReader FORAGE_CATALOG_READER = ForageCatalogReader.getInstance();

    /**
     * Given a kind of bean, resolve the dependencies required to run it
     * @param beanKind the bean kind (i.e.: postgresql, artemis, etc)
     * @return A list of dependencies in the standard GAV format
     */
    public Collection<String> resolveGavs(String beanKind) {

        try {
            Collection<String> beanGavs = FORAGE_CATALOG_READER.getBeanGavs(beanKind);
            final Optional<String> gavType = FORAGE_CATALOG_READER.findFactoryTypeKeyForBeanName(beanKind);
            if (gavType.isPresent()) {
                final Optional<String> factoryGav = resolveFactoryGav(gavType.get());

                beanGavs.add(factoryGav.get());

                return beanGavs;
            } else {
                throw new WanakuException("Could not find factory bean with name " + beanKind);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to resolve Forage dependencies for bean kind '%s': %s", beanKind, e.getMessage());
            return Collections.emptySet();
        }
    }

    Optional<String> resolveFactoryGav(String factoryTypeKey) {
        try {
            return FORAGE_CATALOG_READER.getFactoryVariantGav(factoryTypeKey, "base");
        } catch (Exception e) {
            LOG.warnf(
                    "Failed to resolve Forage dependencies for factory %s type 'base': %s",
                    factoryTypeKey, e.getMessage());
            return Optional.empty();
        }
    }
}
