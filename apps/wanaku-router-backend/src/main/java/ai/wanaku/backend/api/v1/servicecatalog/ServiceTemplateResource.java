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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.backend.api.v1.exceptions.ServiceTemplateNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.DataStoreResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ServiceCatalogIndex;
import ai.wanaku.core.services.api.ServiceTemplateService;
import ai.wanaku.core.services.api.ServiceTemplateSummary;
import ai.wanaku.core.util.StringHelper;

/**
 * REST API resource for service template operations.
 * Base path: /api/v1/service-template
 */
@ApplicationScoped
@Path("/api/v1/service-template")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceTemplateResource {
    private static final Logger LOG = Logger.getLogger(ServiceTemplateResource.class);

    @Inject
    ServiceTemplateBean serviceTemplateBean;

    /**
     * List all service template entries, optionally filtered by search term.
     * GET /api/v1/service-template/list
     * GET /api/v1/service-template/list?search={term}
     *
     * @param search optional search term
     * @return response with list of template summaries
     */
    @Path("/list")
    @GET
    public WanakuResponse<List<ServiceTemplateSummary>> list(@QueryParam("search") String search) {
        if (search != null && !search.isBlank()) {
            LOG.debugf("REST: Listing service templates with search: %s", search);
        } else {
            LOG.debug("REST: Listing all service templates");
        }

        List<DataStore> templates = serviceTemplateBean.list(search);
        List<ServiceTemplateSummary> summaries = new ArrayList<>();

        for (DataStore ds : templates) {
            try {
                ServiceCatalogIndex index = serviceTemplateBean.parseIndex(ds);
                boolean hasProps = index.hasServiceProperties();
                if (!hasProps) {
                    try {
                        hasProps = !serviceTemplateBean
                                .getProperties(index.getName())
                                .isEmpty();
                    } catch (WanakuException ignored) {
                    }
                }
                summaries.add(new ServiceTemplateSummary(
                        ds.getId(),
                        index.getName(),
                        index.getIcon(),
                        index.getDescription(),
                        index.getServiceNames(),
                        hasProps));
            } catch (WanakuException e) {
                LOG.warnf("Failed to parse template index for '%s': %s", ds.getName(), e.getMessage());
            }
        }

        return new WanakuResponse<>(summaries);
    }

    /**
     * Get a specific service template by name with system details.
     * GET /api/v1/service-template/get?name={name}
     *
     * @param name the template name
     * @return response with template detail including system information
     */
    @Path("/get")
    @GET
    public WanakuResponse<Map<String, Object>> get(@QueryParam("name") String name) {
        LOG.debugf("REST: Getting service template: %s", name);

        if (StringHelper.isBlank(name)) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        DataStore template = serviceTemplateBean.get(name);
        if (template == null) {
            throw new ServiceTemplateNotFoundException("Service template not found: %s".formatted(name));
        }

        ServiceCatalogIndex index = serviceTemplateBean.parseIndex(template);
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", template.getId());
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
            systemInfo.put("propertiesFile", index.getPropertiesFile(system));
            systems.add(systemInfo);
        }
        detail.put("services", systems);

        return new WanakuResponse(detail);
    }

    /**
     * Download a service template by name, returning the raw DataStore with Base64-encoded ZIP data.
     * GET /api/v1/service-template/download?name={name}
     *
     * @param name the template name
     * @return response with the DataStore containing the Base64-encoded ZIP
     */
    @Path("/download")
    @GET
    public WanakuResponse<DataStore> download(@QueryParam("name") String name) {
        LOG.debugf("REST: Downloading service template: %s", name);

        if (StringHelper.isBlank(name)) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        DataStore template = serviceTemplateBean.get(name);
        if (template == null) {
            throw new ServiceTemplateNotFoundException("Service template not found: %s".formatted(name));
        }

        return new WanakuResponse<>(template);
    }

    /**
     * Deploy a service template ZIP package.
     * POST /api/v1/service-template/deploy
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return response with the created data store entry
     */
    @Path("/deploy")
    @POST
    public WanakuResponse<DataStore> deploy(DataStore dataStore) {
        LOG.debugf("REST: Deploying service template: %s", dataStore.getName());
        DataStore result = serviceTemplateBean.deploy(dataStore);
        return new WanakuResponse<>(result);
    }

    /**
     * Remove a service template by name.
     * DELETE /api/v1/service-template/remove?name={name}
     *
     * @param name the template name to remove
     * @return HTTP 200 if removed, 404 if not found
     */
    @Path("/remove")
    @DELETE
    public WanakuResponse<Void> remove(@QueryParam("name") String name) {
        LOG.debugf("REST: Removing service template: %s", name);

        if (StringHelper.isBlank(name)) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        int removed = serviceTemplateBean.remove(name);
        if (removed > 0) {
            return new WanakuResponse<>();
        } else {
            throw new DataStoreResourceNotFoundException(name);
        }
    }

    /**
     * Get the properties declared in a service template.
     * GET /api/v1/service-template/properties?name={name}
     *
     * @param name the template name
     * @return response with map of system → property key → value
     */
    @Path("/properties")
    @GET
    public WanakuResponse<Map<String, Map<String, String>>> getProperties(@QueryParam("name") String name) {
        LOG.debugf("REST: Getting properties for template: %s", name);

        if (StringHelper.isBlank(name)) {
            throw new WanakuException("Query parameter 'name' is required");
        }

        Map<String, Map<String, String>> properties = serviceTemplateBean.getProperties(name);
        return new WanakuResponse<>(properties);
    }

    /**
     * Instantiate a service template by filling in property values.
     * POST /api/v1/service-template/instantiate
     *
     * @param request instantiation request containing template name and property values
     * @return response with the newly created service catalog DataStore
     */
    @Path("/instantiate")
    @POST
    public WanakuResponse<DataStore> instantiate(ServiceTemplateService.TemplateInstantiationRequest request) {
        LOG.debugf("REST: Instantiating template: %s", request.getTemplateName());

        if (StringHelper.isBlank(request.getTemplateName())) {
            throw new WanakuException("Template name is required");
        }

        DataStore catalog = serviceTemplateBean.instantiate(
                request.getTemplateName(), request.getProperties(),
                request.getServiceName(), request.getServiceSystem());
        return new WanakuResponse<>(catalog);
    }
}
