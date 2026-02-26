package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogResourceTest {

    @Mock
    ServiceCatalogBean serviceCatalogBean;

    @InjectMocks
    ServiceCatalogResource resource;

    private DataStore testCatalog;
    private ServiceCatalogIndex testIndex;

    @BeforeEach
    void setUp() throws Exception {
        testCatalog = new DataStore();
        testCatalog.setId("test-id");
        testCatalog.setName("test.service.zip");
        testCatalog.setData(createTestZipBase64("testservice", "A test service", "sys1"));

        testIndex = ServiceCatalogIndex.fromBase64(testCatalog.getData());
    }

    @Test
    void testListEmpty() throws Exception {
        when(serviceCatalogBean.list(null)).thenReturn(Collections.emptyList());

        WanakuResponse<List<Map<String, Object>>> response = resource.list(null);
        assertNotNull(response);
        assertNotNull(response.data());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void testListPopulated() throws Exception {
        when(serviceCatalogBean.list(null)).thenReturn(List.of(testCatalog));
        when(serviceCatalogBean.parseIndex(testCatalog)).thenReturn(testIndex);

        WanakuResponse<List<Map<String, Object>>> response = resource.list(null);
        assertNotNull(response);
        assertEquals(1, response.data().size());

        Map<String, Object> summary = response.data().get(0);
        assertEquals("testservice", summary.get("name"));
        assertEquals("A test service", summary.get("description"));
    }

    @Test
    void testListWithSearch() throws Exception {
        when(serviceCatalogBean.list("test")).thenReturn(List.of(testCatalog));
        when(serviceCatalogBean.parseIndex(testCatalog)).thenReturn(testIndex);

        WanakuResponse<List<Map<String, Object>>> response = resource.list("test");
        assertNotNull(response);
        assertEquals(1, response.data().size());
        verify(serviceCatalogBean).list("test");
    }

    @Test
    void testGetFound() throws Exception {
        when(serviceCatalogBean.get("testservice")).thenReturn(testCatalog);
        when(serviceCatalogBean.parseIndex(testCatalog)).thenReturn(testIndex);

        WanakuResponse<Map<String, Object>> response = resource.get("testservice");
        assertNotNull(response);
        assertEquals("testservice", response.data().get("name"));
    }

    @Test
    void testGetNotFound() {
        when(serviceCatalogBean.get("nonexistent")).thenReturn(null);
        assertThrows(WanakuException.class, () -> resource.get("nonexistent"));
    }

    @Test
    void testGetMissingName() {
        assertThrows(WanakuException.class, () -> resource.get(null));
        assertThrows(WanakuException.class, () -> resource.get(""));
    }

    @Test
    void testDownloadFound() throws Exception {
        when(serviceCatalogBean.get("testservice")).thenReturn(testCatalog);

        WanakuResponse<DataStore> response = resource.download("testservice");
        assertNotNull(response);
        assertNotNull(response.data());
        assertEquals("test-id", response.data().getId());
        assertNotNull(response.data().getData());
    }

    @Test
    void testDownloadNotFound() {
        when(serviceCatalogBean.get("nonexistent")).thenReturn(null);
        assertThrows(WanakuException.class, () -> resource.download("nonexistent"));
    }

    @Test
    void testDownloadMissingName() {
        assertThrows(WanakuException.class, () -> resource.download(null));
        assertThrows(WanakuException.class, () -> resource.download(""));
    }

    @Test
    void testDeployValid() throws Exception {
        DataStore input = new DataStore();
        input.setName("test.service.zip");
        input.setData(createTestZipBase64("test", "desc", "sys1"));

        when(serviceCatalogBean.deploy(any())).thenReturn(testCatalog);

        WanakuResponse<DataStore> response = resource.deploy(input);
        assertNotNull(response);
        assertEquals("test-id", response.data().getId());
        verify(serviceCatalogBean).deploy(input);
    }

    @Test
    void testRemoveFound() throws Exception {
        when(serviceCatalogBean.remove("test.service.zip")).thenReturn(1);
        Response response = resource.remove("test.service.zip");
        assertEquals(200, response.getStatus());
    }

    @Test
    void testRemoveNotFound() throws Exception {
        when(serviceCatalogBean.remove("nonexistent")).thenReturn(0);
        Response response = resource.remove("nonexistent");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testRemoveMissingName() {
        assertThrows(WanakuException.class, () -> resource.remove(null));
        assertThrows(WanakuException.class, () -> resource.remove(""));
    }

    // Helper

    private String createTestZipBase64(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry("index.properties"));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
