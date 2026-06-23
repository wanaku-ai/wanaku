package ai.wanaku.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WanakuHomeTest {

    @Test
    void expandPlaceholders_expandsWanakuHome() {
        withSystemProperty("wanaku.home", "/custom/wanaku", () -> {
            String result = WanakuHome.expandPlaceholders("${wanaku.home}/data");
            assertNotNull(result);
            assertEquals("/custom/wanaku/data", result);
        });
    }

    @Test
    void expandPlaceholders_expandsUserHome() {
        withSystemProperty("user.home", "/test/user/home", () -> {
            String result = WanakuHome.expandPlaceholders("${user.home}/data");
            assertEquals("/test/user/home/data", result);
        });
    }

    @Test
    void expandPlaceholders_expandsBoth() {
        withSystemProperties(
                new Property[] {
                    new Property("wanaku.home", "/custom/wanaku"), new Property("user.home", "/test/user/home")
                },
                () -> {
                    String result = WanakuHome.expandPlaceholders("${wanaku.home}/${user.home}/data");
                    assertEquals("/custom/wanaku//test/user/home/data", result);
                });
    }

    @Test
    void expandPlaceholders_returnsOriginalWhenNoPlaceholders() {
        String result = WanakuHome.expandPlaceholders("/plain/path");
        assertEquals("/plain/path", result);
    }

    @Test
    void expandPlaceholders_returnsNullForNull() {
        String result = WanakuHome.expandPlaceholders(null);
        assertNull(result);
    }

    @Test
    void expandPlaceholders_handlesEmptyString() {
        String result = WanakuHome.expandPlaceholders("");
        assertEquals("", result);
    }

    private void withSystemProperty(String name, String value, Runnable test) {
        String original = System.getProperty(name);
        System.setProperty(name, value);
        try {
            test.run();
        } finally {
            if (original != null) {
                System.setProperty(name, original);
            } else {
                System.clearProperty(name);
            }
        }
    }

    private void withSystemProperties(Property[] properties, Runnable test) {
        String[] originals = new String[properties.length];
        for (int i = 0; i < properties.length; i++) {
            originals[i] = System.getProperty(properties[i].name);
            System.setProperty(properties[i].name, properties[i].value);
        }
        try {
            test.run();
        } finally {
            for (int i = 0; i < properties.length; i++) {
                if (originals[i] != null) {
                    System.setProperty(properties[i].name, originals[i]);
                } else {
                    System.clearProperty(properties[i].name);
                }
            }
        }
    }

    private record Property(String name, String value) {}
}
