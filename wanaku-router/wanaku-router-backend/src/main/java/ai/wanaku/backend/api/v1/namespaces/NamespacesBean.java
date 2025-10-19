package ai.wanaku.backend.api.v1.namespaces;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.core.persistence.api.NamespaceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import org.jboss.logging.Logger;

@Singleton
public class NamespacesBean {
    private static final Logger LOG = Logger.getLogger(NamespacesBean.class);

    @Inject
    Instance<NamespaceRepository> namespaceRepositoryInstance;

    private NamespaceRepository namespaceRepository;

    private final int maxNamespaces = 10;

    @PostConstruct
    void init() {
        namespaceRepository = namespaceRepositoryInstance.get();
    }

    public synchronized void preload() {
        // Preload data
        if (namespaceRepository.size() < maxNamespaces) {
            for (int i = 0; i < maxNamespaces; i++) {
                final String namespacePath = String.format("ns-%d", i);
                Namespace namespace = new Namespace();
                namespace.setPath(namespacePath);
                namespace.setName(null);

                namespaceRepository.persist(namespace);

                LOG.infof("Created new namespace path %s", namespacePath);
            }
        } else {
            LOG.infof("This instance already has %d namespaces allocated", namespaceRepository.size());
        }

        List<Namespace> publicList = namespaceRepository.findByName("public");
        if (publicList.isEmpty()) {
            // Register the public namespace
            Namespace publicNs = new Namespace();
            publicNs.setPath("public");
            publicNs.setName("public");

            namespaceRepository.persist(publicNs);
        }
    }

    public Namespace alocateNamespace(String name) {
        List<Namespace> byName = namespaceRepository.findByName(name);
        if (byName.isEmpty()) {
            final List<Namespace> namespaces = namespaceRepository.findFirstAvailable(name);
            Namespace namespace = namespaces.getFirst();

            if (namespace.getName() == null) {
                LOG.debugf("Allocating namespace with path %s for %s", namespace.getPath(), name);
                namespace.setName(name);
                return namespaceRepository.persist(namespace);
            } else {
                LOG.debugf("Reusing namespace with path %s for %s", namespace.getPath(), name);
            }

            return namespace;
        }

        return byName.getFirst();
    }

    public List<Namespace> list() {
        return namespaceRepository.listAll();
    }
}
