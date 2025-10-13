package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.Namespace;
import java.util.List;

/**
 * Repository interface for managing {@link Namespace} entities.
 * <p>
 * This interface extends {@link WanakuRepository} to provide persistence operations
 * for namespaces, which provide logical grouping and isolation for capabilities,
 * tools, and resources within the Wanaku system.
 */
public interface NamespaceRepository extends WanakuRepository<Namespace, String> {

    /**
     * Finds all namespaces with the specified name.
     * <p>
     * While namespace names are typically unique, this method returns a list
     * to support potential future use cases or naming conflicts.
     *
     * @param name the name of the namespaces to find
     * @return a list of matching namespaces, or an empty list if none found
     */
    List<Namespace> findByName(String name);

    /**
     * Finds the first available namespace with the specified name.
     * <p>
     * This method is optimized for the common case where only one namespace
     * with a given name is expected. It returns the first match found.
     *
     * @param name the name of the namespace to find
     * @return a list containing the first available namespace, or an empty list if not found
     */
    List<Namespace> findFirstAvailable(String name);
}
