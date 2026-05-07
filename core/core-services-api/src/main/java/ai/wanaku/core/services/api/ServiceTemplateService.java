package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * Service interface for Service Template operations via REST API.
 * Service Templates are catalog packages with parameterized service.properties files
 * that can be instantiated into service catalogs by filling in parameter values.
 */
@Path("/api/v1/service-template")
public interface ServiceTemplateService {

    /**
     * List all service template entries, optionally filtered by search term.
     *
     * @param search optional search term to filter templates by name or description
     * @return response with list of template summaries
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> list(@QueryParam("search") String search);

    /**
     * List all service template entries without filtering.
     *
     * @return response with list of all template summaries
     */
    default WanakuResponse<List<DataStore>> list() {
        return list(null);
    }

    /**
     * Get a specific service template by name.
     *
     * @param name the template name
     * @return response with template details
     * @throws WanakuException if template not found
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> get(@QueryParam("name") String name) throws WanakuException;

    /**
     * Deploy a service template ZIP package.
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return response with the created data store entry
     * @throws WanakuException if the ZIP is invalid
     */
    @Path("/deploy")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> deploy(DataStore dataStore) throws WanakuException;

    /**
     * Download a service template by name, returning the raw DataStore with Base64-encoded ZIP data.
     *
     * @param name the template name
     * @return response with the DataStore containing the Base64-encoded ZIP
     * @throws WanakuException if template not found
     */
    @Path("/download")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> download(@QueryParam("name") String name) throws WanakuException;

    /**
     * Remove a service template by name.
     *
     * @param name the template name to remove
     * @return HTTP response
     * @throws WanakuException if removal fails
     */
    @Path("/remove")
    @DELETE
    Response remove(@QueryParam("name") String name) throws WanakuException;

    /**
     * Get the properties declared in a service template.
     * Returns a map of system name to property key-value pairs.
     *
     * @param name the template name
     * @return response with map of system → property key → current value
     * @throws WanakuException if template not found or parsing fails
     */
    @Path("/properties")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, Map<String, String>>> getProperties(@QueryParam("name") String name)
            throws WanakuException;

    /**
     * Instantiate a service template by filling in property values.
     * Creates a new service catalog from the template.
     *
     * @param request instantiation request containing template name and property values
     * @return response with the newly created service catalog DataStore
     * @throws WanakuException if instantiation fails
     */
    @Path("/instantiate")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> instantiate(TemplateInstantiationRequest request) throws WanakuException;

    /**
     * Request body for template instantiation.
     */
    class TemplateInstantiationRequest {
        private String templateName;
        private Map<String, String> properties;

        public String getTemplateName() {
            return templateName;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
