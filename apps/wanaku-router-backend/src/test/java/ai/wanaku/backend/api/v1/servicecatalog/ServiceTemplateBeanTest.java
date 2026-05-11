package ai.wanaku.backend.api.v1.servicecatalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTemplateBeanTest {

    @Mock
    DataStoreRepository dataStoreRepository;

    @Mock
    ServiceCatalogBean serviceCatalogBean;

    @InjectMocks
    ServiceTemplateBean serviceTemplateBean;

    private DataStore templateStore;

    @BeforeEach
    void setUp() {
        templateStore = new DataStore();
        templateStore.setId("tmpl-1");
        templateStore.setName("test-template");
        templateStore.setData(createTemplateZipBase64("my-service", "A template", "sys1"));

        Map<String, String> labels = new HashMap<>();
        labels.put("wanaku.type", "template");
        templateStore.setLabels(labels);
    }

    @Test
    void testInstantiateWithDefaultValues() throws Exception {
        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=template"))
                .thenReturn(List.of(templateStore));

        DataStore deployed = new DataStore();
        deployed.setId("catalog-1");
        deployed.setName("my-service");
        when(serviceCatalogBean.deploy(any())).thenReturn(deployed);

        DataStore result = serviceTemplateBean.instantiate("my-service", Map.of());

        assertNotNull(result);
        assertEquals("catalog-1", result.getId());

        ArgumentCaptor<DataStore> captor = ArgumentCaptor.forClass(DataStore.class);
        verify(serviceCatalogBean).deploy(captor.capture());

        DataStore catalogArg = captor.getValue();
        assertEquals("my-service", catalogArg.getName());

        Properties indexProps = extractIndexProperties(catalogArg.getData());
        assertEquals("my-service", indexProps.getProperty("catalog.name"));
        assertEquals("sys1", indexProps.getProperty("catalog.services"));
    }

    @Test
    void testInstantiateWithServiceNameOverride() throws Exception {
        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=template"))
                .thenReturn(List.of(templateStore));

        DataStore deployed = new DataStore();
        deployed.setId("catalog-2");
        deployed.setName("custom-name");
        when(serviceCatalogBean.deploy(any())).thenReturn(deployed);

        DataStore result = serviceTemplateBean.instantiate("my-service", Map.of(), "custom-name", null);

        assertNotNull(result);

        ArgumentCaptor<DataStore> captor = ArgumentCaptor.forClass(DataStore.class);
        verify(serviceCatalogBean).deploy(captor.capture());

        DataStore catalogArg = captor.getValue();
        assertEquals("custom-name", catalogArg.getName());

        Properties indexProps = extractIndexProperties(catalogArg.getData());
        assertEquals("custom-name", indexProps.getProperty("catalog.name"));
        assertEquals("sys1", indexProps.getProperty("catalog.services"));
    }

    @Test
    void testInstantiateWithServiceSystemOverride() throws Exception {
        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=template"))
                .thenReturn(List.of(templateStore));

        DataStore deployed = new DataStore();
        deployed.setId("catalog-3");
        deployed.setName("my-service");
        when(serviceCatalogBean.deploy(any())).thenReturn(deployed);

        DataStore result = serviceTemplateBean.instantiate("my-service", Map.of(), null, "custom-system");

        assertNotNull(result);

        ArgumentCaptor<DataStore> captor = ArgumentCaptor.forClass(DataStore.class);
        verify(serviceCatalogBean).deploy(captor.capture());

        DataStore catalogArg = captor.getValue();
        assertEquals("my-service", catalogArg.getName());

        Properties indexProps = extractIndexProperties(catalogArg.getData());
        assertEquals("my-service", indexProps.getProperty("catalog.name"));
        assertEquals("custom-system", indexProps.getProperty("catalog.services"));
        assertEquals("sys1/sys1.camel.yaml", indexProps.getProperty("catalog.routes.custom-system"));
        assertEquals("sys1/sys1.wanaku-rules.yaml", indexProps.getProperty("catalog.rules.custom-system"));
        assertNull(indexProps.getProperty("catalog.routes.sys1"));
        assertNull(indexProps.getProperty("catalog.rules.sys1"));
    }

    @Test
    void testInstantiateWithBothOverrides() throws Exception {
        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=template"))
                .thenReturn(List.of(templateStore));

        DataStore deployed = new DataStore();
        deployed.setId("catalog-4");
        deployed.setName("new-name");
        when(serviceCatalogBean.deploy(any())).thenReturn(deployed);

        DataStore result = serviceTemplateBean.instantiate("my-service", Map.of(), "new-name", "new-system");

        assertNotNull(result);

        ArgumentCaptor<DataStore> captor = ArgumentCaptor.forClass(DataStore.class);
        verify(serviceCatalogBean).deploy(captor.capture());

        DataStore catalogArg = captor.getValue();
        assertEquals("new-name", catalogArg.getName());

        Properties indexProps = extractIndexProperties(catalogArg.getData());
        assertEquals("new-name", indexProps.getProperty("catalog.name"));
        assertEquals("new-system", indexProps.getProperty("catalog.services"));
    }

    @Test
    void testInstantiateWithBlankOverridesUsesDefaults() throws Exception {
        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=template"))
                .thenReturn(List.of(templateStore));

        DataStore deployed = new DataStore();
        deployed.setId("catalog-5");
        deployed.setName("my-service");
        when(serviceCatalogBean.deploy(any())).thenReturn(deployed);

        DataStore result = serviceTemplateBean.instantiate("my-service", Map.of(), "", "  ");

        assertNotNull(result);

        ArgumentCaptor<DataStore> captor = ArgumentCaptor.forClass(DataStore.class);
        verify(serviceCatalogBean).deploy(captor.capture());

        DataStore catalogArg = captor.getValue();
        assertEquals("my-service", catalogArg.getName());

        Properties indexProps = extractIndexProperties(catalogArg.getData());
        assertEquals("my-service", indexProps.getProperty("catalog.name"));
        assertEquals("sys1", indexProps.getProperty("catalog.services"));
    }

    // --- Helpers ---

    private String createTemplateZipBase64(String name, String description, String... systems) {
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

    private Properties extractIndexProperties(String base64Zip) throws Exception {
        byte[] zipBytes = Base64.getDecoder().decode(base64Zip);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("index.properties".equals(entry.getName())) {
                    Properties props = new Properties();
                    props.load(new StringReader(new String(zis.readAllBytes())));
                    return props;
                }
                zis.closeEntry();
            }
        }
        throw new AssertionError("index.properties not found in ZIP");
    }
}
