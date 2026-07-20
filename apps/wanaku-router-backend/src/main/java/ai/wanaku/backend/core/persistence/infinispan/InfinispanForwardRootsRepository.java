package ai.wanaku.backend.core.persistence.infinispan;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;
import ai.wanaku.backend.bridge.ForwardRoots;
import ai.wanaku.core.util.WanakuHome;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File-based repository for persisting {@link ForwardRoots} entries.
 * <p>
 * Each entry is keyed by forward name and stores the root name-to-URI mappings
 * that the MCP client should advertise to the upstream server.
 * <p>
 * Entries are persisted as a JSON file under the Wanaku home directory.
 */
@Singleton
public class InfinispanForwardRootsRepository {
    private static final Logger LOG = Logger.getLogger(InfinispanForwardRootsRepository.class);
    private static final String FILE_NAME = "forward-roots.json";

    private final Map<String, ForwardRoots> cache = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path filePath;

    public InfinispanForwardRootsRepository() {
        this.filePath = Path.of(WanakuHome.get(), "router", FILE_NAME);
        loadFromDisk();
    }

    /**
     * Stores or replaces the roots configuration for a forward.
     *
     * @param forwardRoots the roots configuration to store
     */
    public void store(ForwardRoots forwardRoots) {
        if (forwardRoots == null || forwardRoots.getForwardName() == null) {
            return;
        }
        try {
            lock.lock();
            cache.put(forwardRoots.getForwardName(), forwardRoots);
            persistToDisk();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the roots configuration for a forward.
     *
     * @param forwardName the forward name
     * @return an Optional containing the roots if found, empty otherwise
     */
    public Optional<ForwardRoots> findByName(String forwardName) {
        if (forwardName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(forwardName));
    }

    /**
     * Removes the roots configuration for a forward.
     *
     * @param forwardName the forward name
     * @return true if the entry was removed, false if it did not exist
     */
    public boolean remove(String forwardName) {
        if (forwardName == null) {
            return false;
        }
        try {
            lock.lock();
            boolean removed = cache.remove(forwardName) != null;
            if (removed) {
                persistToDisk();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            Map<String, ForwardRoots> loaded =
                    objectMapper.readValue(filePath.toFile(), new TypeReference<Map<String, ForwardRoots>>() {});
            if (loaded != null) {
                cache.putAll(loaded);
            }
        } catch (IOException e) {
            LOG.warnf("Failed to load forward roots from %s: %s", filePath, e.getMessage());
        }
    }

    private void persistToDisk() {
        try {
            Files.createDirectories(filePath.getParent());
            objectMapper.writeValue(filePath.toFile(), cache);
        } catch (IOException e) {
            LOG.warnf("Failed to persist forward roots to %s: %s", filePath, e.getMessage());
        }
    }
}
