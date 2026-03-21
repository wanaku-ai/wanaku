package ai.wanaku.backend.api.v1.namespaces;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.core.persistence.api.NamespaceRepository;

@Singleton
public class NamespacesBean {
    private static final Logger LOG = Logger.getLogger(NamespacesBean.class);
    private static final String LABEL_PREALLOCATED = "wanaku.io/preallocated";
    private static final String LABEL_PREALLOCATED_AT = "wanaku.io/preallocated-at";
    private static final String LABEL_ALLOCATED_AT = "wanaku.io/allocated-at";
    private static final String LABEL_EXPIRES_AT = "wanaku.io/expires-at";
    private static final Set<String> PROTECTED_NAMESPACES = Set.of("public", "wanaku-internal");

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
                markPreallocated(namespace);

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
                markAllocated(namespace);
                return namespaceRepository.persist(namespace);
            } else {
                LOG.debugf("Reusing namespace with path %s for %s", namespace.getPath(), name);
            }

            return namespace;
        }

        return byName.getFirst();
    }

    public Namespace create(Namespace namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace cannot be null");
        }

        if (namespace.getName() != null && namespace.getName().trim().isEmpty()) {
            namespace.setName(null);
        }

        if (isProtectedNamespaceName(namespace.getName()) || isProtectedNamespaceName(namespace.getPath())) {
            throw new IllegalArgumentException("Reserved namespace names cannot be created");
        }

        if (namespace.getName() == null) {
            markPreallocated(namespace);
        } else {
            markAllocated(namespace);
        }

        return namespaceRepository.persist(namespace);
    }

    public List<Namespace> list(String labelFilter) {
        if (labelFilter == null || labelFilter.trim().isEmpty()) {
            ensureDefaultNamespace();
            return namespaceRepository.listAll();
        }
        return namespaceRepository.findAllFilterByLabelExpression(labelFilter);
    }

    public List<Namespace> list() {
        return list(null);
    }

    private void ensureDefaultNamespace() {
        List<Namespace> defaultList = namespaceRepository.findByName("default");
        if (defaultList.isEmpty()) {
            Namespace defaultNs = new Namespace();
            defaultNs.setPath("default");
            defaultNs.setName("default");
            namespaceRepository.persist(defaultNs);
        }
    }

    public boolean exists(String id) {
        return namespaceRepository.exists(id);
    }

    public Namespace getById(String id) {
        return namespaceRepository.findById(id);
    }

    public boolean update(String id, Namespace namespace) {
        return namespaceRepository.update(id, namespace);
    }

    public boolean deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        Namespace namespace = namespaceRepository.findById(id);
        if (namespace == null) {
            return false;
        }

        if (isProtectedNamespace(namespace)) {
            LOG.warnf("Refusing to delete protected namespace %s", namespace.getPath());
            return false;
        }

        return namespaceRepository.deleteById(id);
    }

    public List<Namespace> listStale(long maxAgeSeconds, boolean unassignedOnly, boolean includeUnlabeled) {
        Instant now = Instant.now();
        return namespaceRepository.listAll().stream()
                .filter(namespace -> !unassignedOnly || namespace.getName() == null)
                .filter(namespace -> isStale(namespace, now, maxAgeSeconds, includeUnlabeled))
                .toList();
    }

    public int cleanupStale(long maxAgeSeconds, boolean unassignedOnly, boolean includeUnlabeled) {
        List<Namespace> staleNamespaces = listStale(maxAgeSeconds, unassignedOnly, includeUnlabeled);
        int removed = 0;
        for (Namespace namespace : staleNamespaces) {
            if (namespace.getId() != null && deleteById(namespace.getId())) {
                removed++;
            }
        }
        LOG.infof("Stale namespace cleanup complete: %d namespaces removed", removed);
        return removed;
    }

    private void markPreallocated(Namespace namespace) {
        Map<String, String> labels = ensureLabels(namespace);
        labels.put(LABEL_PREALLOCATED, "true");
        labels.putIfAbsent(LABEL_PREALLOCATED_AT, String.valueOf(Instant.now().getEpochSecond()));
    }

    private void markAllocated(Namespace namespace) {
        Map<String, String> labels = ensureLabels(namespace);
        labels.put(LABEL_PREALLOCATED, "false");
        labels.put(LABEL_ALLOCATED_AT, String.valueOf(Instant.now().getEpochSecond()));
    }

    private Map<String, String> ensureLabels(Namespace namespace) {
        Map<String, String> labels = namespace.getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            namespace.setLabels(labels);
        }
        return labels;
    }

    private boolean isStale(Namespace namespace, Instant now, long maxAgeSeconds, boolean includeUnlabeled) {
        Map<String, String> labels = namespace.getLabels();
        if (labels == null || labels.isEmpty()) {
            return includeUnlabeled;
        }

        Instant expiresAt = parseEpochInstant(labels.get(LABEL_EXPIRES_AT));
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return true;
        }

        Instant preallocatedAt = parseEpochInstant(labels.get(LABEL_PREALLOCATED_AT));
        if (preallocatedAt != null) {
            if (maxAgeSeconds <= 0) {
                return false;
            }
            Duration age = Duration.between(preallocatedAt, now);
            return age.compareTo(Duration.ofSeconds(maxAgeSeconds)) >= 0;
        }

        return includeUnlabeled;
    }

    private boolean isProtectedNamespace(Namespace namespace) {
        return isProtectedNamespaceName(namespace.getName()) || isProtectedNamespaceName(namespace.getPath());
    }

    private boolean isProtectedNamespaceName(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return PROTECTED_NAMESPACES.stream().anyMatch(name -> name.equalsIgnoreCase(trimmed));
    }

    private Instant parseEpochInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed > 100000000000L) {
                return Instant.ofEpochMilli(parsed);
            }
            return Instant.ofEpochSecond(parsed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
