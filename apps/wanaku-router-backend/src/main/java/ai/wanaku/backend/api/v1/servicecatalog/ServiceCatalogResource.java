package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

/**
 * REST API resource for service catalog operations.
 * Base path: /api/v1/service-catalog
 */
@ApplicationScoped
@Path("/api/v1/service-catalog")
public class ServiceCatalogResource {
    private static final Logger LOG = Logger.getLogger(ServiceCatalogResource.class);

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    /**
     * List all service catalog entries, optionally filtered by search term.
     * GET /api/v1/service-catalog/list
     * GET /api/v1/service-catalog/list?search={term}
     *
     * @param search optional search term
     * @return response with list of catalog summaries
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Map<String, Object>>> list(@QueryParam("search") String search) throws WanakuException {
        if (search != null && !search.isBlank()) {
            LOG.debugf("REST: Listing service catalogs with search: %s", search);
        } else {
            LOG.debug("REST: Listing all service catalogs");
        }

        List<DataStore> catalogs = serviceCatalogBean.list(search);
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (DataStore ds : catalogs) {
            try {
                ServiceCatalogIndex index = serviceCatalogBean.parseIndex(ds);
                Map<String, Object> summary = new HashMap<>();
                summary.put("id", ds.getId());
                summary.put("name", index.getName());
                summary.put("icon", index.getIcon());
                summary.put("description", index.getDescription());
                summary.put("services", index.getServiceNames());
                summaries.add(summary);
            } catch (WanakuException e) {
                LOG.warnf("Failed to parse catalog index for '%s': %s", ds.getName(), e.getMessage());
            }
        }

        return new WanakuResponse<>(summaries);
    }

    /**
     * Get a specific service catalog by name with system details.
     * GET /api/v1/service-catalog/get?name={name}
     *
     * @param name the catalog name
     * @return response with catalog detail including system information
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, Object>> get(@QueryParam("name") String name) throws WanakuException {
        LOG.debugf("REST: Getting service catalog: %s", name);

        if (name == null || name.isBlank()) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        DataStore catalog = serviceCatalogBean.get(name);
        if (catalog == null) {
            throw new WanakuException("Service catalog not found: " + name);
        }

        ServiceCatalogIndex index = serviceCatalogBean.parseIndex(catalog);
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", catalog.getId());
        detail.put("name", index.getName());
        detail.put("icon", index.getIcon());
        detail.put("description", index.getDescription());

        List<Map<String, String>> systems = new ArrayList<>();
        for (String system : index.getServiceNames()) {
            Map<String, String> systemInfo = new HashMap<>();
            systemInfo.put("name", system);
            systemInfo.put("routesFile", index.getRoutesFile(system));
            systemInfo.put("rulesFile", index.getRulesFile(system));
            systemInfo.put("dependenciesFile", index.getDependenciesFile(system));
            systems.add(systemInfo);
        }
        detail.put("services", systems);

        return new WanakuResponse<>(detail);
    }

    /**
     * Download a service catalog by name, returning the raw DataStore with Base64-encoded ZIP data.
     * GET /api/v1/service-catalog/download?name={name}
     *
     * @param name the catalog name
     * @return response with the DataStore containing the Base64-encoded ZIP
     */
    @Path("/download")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<DataStore> download(@QueryParam("name") String name) throws WanakuException {
        LOG.debugf("REST: Downloading service catalog: %s", name);

        if (name == null || name.isBlank()) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        DataStore catalog = serviceCatalogBean.get(name);
        if (catalog == null) {
            throw new WanakuException("Service catalog not found: " + name);
        }

        return new WanakuResponse<>(catalog);
    }

    /**
     * Deploy a service catalog ZIP package.
     * POST /api/v1/service-catalog/deploy
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return response with the created data store entry
     */
    @Path("/deploy")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<DataStore> deploy(DataStore dataStore) throws WanakuException {
        LOG.debugf("REST: Deploying service catalog: %s", dataStore.getName());
        DataStore result = serviceCatalogBean.deploy(dataStore);
        return new WanakuResponse<>(result);
    }

    /**
     * Remove a service catalog by name.
     * DELETE /api/v1/service-catalog/remove?name={name}
     *
     * @param name the catalog name to remove
     * @return HTTP 200 if removed, 404 if not found
     */
    @Path("/remove")
    @DELETE
    public Response remove(@QueryParam("name") String name) throws WanakuException {
        LOG.debugf("REST: Removing service catalog: %s", name);

        if (name == null || name.isBlank()) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        int removed = serviceCatalogBean.remove(name);
        if (removed > 0) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
