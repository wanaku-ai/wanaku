package ai.wanaku.core.services.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetRepoIndexTest {

    @Test
    void testValidIndex() throws Exception {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "weather,finance");
        props.setProperty("wanaku.description.weather", "Weather tools");
        props.setProperty("wanaku.description.finance", "Finance tools");
        props.setProperty("wanaku.toolset.icon", "collection-icon");
        props.setProperty("wanaku.toolset.weather.icon", "weather-icon");

        ToolsetRepoIndex index = parseProps(props);

        assertEquals("collection-icon", index.getIcon());
        assertEquals(2, index.getEntries().size());

        ToolsetRepoIndex.ToolsetEntry weather = index.getEntries().get(0);
        assertEquals("weather", weather.getName());
        assertEquals("Weather tools", weather.getDescription());
        assertEquals("weather-icon", weather.getIcon());

        ToolsetRepoIndex.ToolsetEntry finance = index.getEntries().get(1);
        assertEquals("finance", finance.getName());
        assertEquals("Finance tools", finance.getDescription());
        assertNull(finance.getIcon());
    }

    @Test
    void testMissingToolsetsProperty() {
        Properties props = new Properties();
        WanakuException ex = assertThrows(WanakuException.class, () -> parseProps(props));
        assertTrue(ex.getMessage().contains("wanaku.toolsets"));
    }

    @Test
    void testEmptyToolsetsProperty() {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "");
        WanakuException ex = assertThrows(WanakuException.class, () -> parseProps(props));
        assertTrue(ex.getMessage().contains("wanaku.toolsets"));
    }

    @Test
    void testBlankToolsetsProperty() {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "   ");
        WanakuException ex = assertThrows(WanakuException.class, () -> parseProps(props));
        assertTrue(ex.getMessage().contains("wanaku.toolsets"));
    }

    @Test
    void testOptionalDescription() throws Exception {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "tools");

        ToolsetRepoIndex index = parseProps(props);
        assertEquals(1, index.getEntries().size());
        assertEquals("", index.getEntries().get(0).getDescription());
    }

    @Test
    void testOptionalIcons() throws Exception {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "tools");
        props.setProperty("wanaku.description.tools", "Some tools");

        ToolsetRepoIndex index = parseProps(props);
        assertNull(index.getIcon());
        assertNull(index.getEntries().get(0).getIcon());
    }

    @Test
    void testToolsetUrl() {
        assertEquals(
                "https://example.com/repo/toolsets/weather.json",
                ToolsetRepoIndex.toolsetUrl("https://example.com/repo", "weather"));
        assertEquals(
                "https://example.com/repo/toolsets/weather.json",
                ToolsetRepoIndex.toolsetUrl("https://example.com/repo/", "weather"));
    }

    @Test
    void testNormalizeBaseUrl() {
        assertEquals("https://example.com/", ToolsetRepoIndex.normalizeBaseUrl("https://example.com"));
        assertEquals("https://example.com/", ToolsetRepoIndex.normalizeBaseUrl("https://example.com/"));
    }

    @Test
    void testWhitespaceTrimming() throws Exception {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", " weather , finance ");
        props.setProperty("wanaku.description.weather", "Weather tools");
        props.setProperty("wanaku.description.finance", "Finance tools");

        ToolsetRepoIndex index = parseProps(props);
        assertEquals(2, index.getEntries().size());
        assertEquals("weather", index.getEntries().get(0).getName());
        assertEquals("finance", index.getEntries().get(1).getName());
    }

    @Test
    void testSingleToolset() throws Exception {
        Properties props = new Properties();
        props.setProperty("wanaku.toolsets", "search");
        props.setProperty("wanaku.description.search", "Search tools for web");

        ToolsetRepoIndex index = parseProps(props);
        assertEquals(1, index.getEntries().size());
        assertEquals("search", index.getEntries().get(0).getName());
        assertEquals("Search tools for web", index.getEntries().get(0).getDescription());
    }

    private ToolsetRepoIndex parseProps(Properties props) throws WanakuException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            props.store(baos, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ToolsetRepoIndex.fromProperties(new ByteArrayInputStream(baos.toByteArray()));
    }
}
