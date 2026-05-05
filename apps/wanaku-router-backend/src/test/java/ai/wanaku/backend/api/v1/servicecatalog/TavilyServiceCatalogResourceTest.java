package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ServiceCatalogIndex;
import ai.wanaku.core.util.support.TavilyServiceCatalogHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TavilyServiceCatalogResourceTest {

    @Mock
    ServiceCatalogBean serviceCatalogBean;

    @InjectMocks
    ServiceCatalogResource resource;

    private DataStore tavilyCatalog;
    private ServiceCatalogIndex tavilyIndex;

    @BeforeEach
    void setUp() throws Exception {
        tavilyCatalog = new DataStore();
        tavilyCatalog.setId("tavily-id");
        tavilyCatalog.setName("tavily.service.zip");
        tavilyCatalog.setData(TavilyServiceCatalogHelper.createCatalogBase64());

        tavilyIndex = ServiceCatalogIndex.fromBase64(tavilyCatalog.getData());
    }

    @Test
    void testListIncludesTavilyCatalog() throws Exception {
        when(serviceCatalogBean.list(null)).thenReturn(List.of(tavilyCatalog));
        when(serviceCatalogBean.parseIndex(tavilyCatalog)).thenReturn(tavilyIndex);

        WanakuResponse<List<Map<String, Object>>> response = resource.list(null);
        assertNotNull(response);
        assertEquals(1, response.data().size());

        Map<String, Object> summary = response.data().get(0);
        assertEquals("tavily", summary.get("name"));
        assertEquals(TavilyServiceCatalogHelper.CATALOG_DESCRIPTION, summary.get("description"));
    }

    @Test
    void testListSearchTavilyCatalog() throws Exception {
        when(serviceCatalogBean.list("tavily")).thenReturn(List.of(tavilyCatalog));
        when(serviceCatalogBean.parseIndex(tavilyCatalog)).thenReturn(tavilyIndex);

        WanakuResponse<List<Map<String, Object>>> response = resource.list("tavily");
        assertNotNull(response);
        assertEquals(1, response.data().size());
        assertEquals("tavily", response.data().get(0).get("name"));
        verify(serviceCatalogBean).list("tavily");
    }

    @Test
    void testGetTavilyCatalog() throws Exception {
        when(serviceCatalogBean.get(TavilyServiceCatalogHelper.CATALOG_NAME)).thenReturn(tavilyCatalog);
        when(serviceCatalogBean.parseIndex(tavilyCatalog)).thenReturn(tavilyIndex);

        WanakuResponse<Map<String, Object>> response = resource.get(TavilyServiceCatalogHelper.CATALOG_NAME);
        assertNotNull(response);
        assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, response.data().get("name"));
        assertEquals(
                TavilyServiceCatalogHelper.CATALOG_DESCRIPTION, response.data().get("description"));
    }

    @Test
    void testGetTavilyCatalogNotFound() {
        when(serviceCatalogBean.get(TavilyServiceCatalogHelper.CATALOG_NAME)).thenReturn(null);
        assertThrows(WanakuException.class, () -> resource.get(TavilyServiceCatalogHelper.CATALOG_NAME));
    }

    @Test
    void testDownloadTavilyCatalog() throws Exception {
        when(serviceCatalogBean.get(TavilyServiceCatalogHelper.CATALOG_NAME)).thenReturn(tavilyCatalog);

        WanakuResponse<DataStore> response = resource.download(TavilyServiceCatalogHelper.CATALOG_NAME);
        assertNotNull(response);
        assertNotNull(response.data());
        assertEquals("tavily-id", response.data().getId());
        assertNotNull(response.data().getData());
    }

    @Test
    void testDeployTavilyCatalog() throws Exception {
        DataStore input = new DataStore();
        input.setName("tavily.service.zip");
        input.setData(TavilyServiceCatalogHelper.createCatalogBase64());

        when(serviceCatalogBean.deploy(any())).thenReturn(tavilyCatalog);

        WanakuResponse<DataStore> response = resource.deploy(input);
        assertNotNull(response);
        assertEquals("tavily-id", response.data().getId());
        verify(serviceCatalogBean).deploy(input);
    }

    @Test
    void testRemoveTavilyCatalog() throws Exception {
        when(serviceCatalogBean.remove("tavily.service.zip")).thenReturn(1);
        Response response = resource.remove("tavily.service.zip");
        assertEquals(200, response.getStatus());
    }

    @Test
    void testRemoveTavilyCatalogNotFound() throws Exception {
        when(serviceCatalogBean.remove("tavily.service.zip")).thenReturn(0);
        Response response = resource.remove("tavily.service.zip");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testTavilyIndexHasCorrectServiceName() {
        assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, tavilyIndex.getName());
        assertEquals(1, tavilyIndex.getServiceNames().size());
        assertEquals(
                TavilyServiceCatalogHelper.SERVICE_NAME,
                tavilyIndex.getServiceNames().get(0));
        assertEquals(
                TavilyServiceCatalogHelper.ROUTES_FILE,
                tavilyIndex.getRoutesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
        assertEquals(
                TavilyServiceCatalogHelper.RULES_FILE,
                tavilyIndex.getRulesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
        assertEquals(
                TavilyServiceCatalogHelper.DEPENDENCIES_FILE,
                tavilyIndex.getDependenciesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
    }
}
