package ai.wanaku.cli.main.support;

import java.io.File;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeConstantsTest {

    @Test
    void wanakuHomeDirUsesCurrentUserHome() {
        String expected = System.getProperty("user.home") + File.separator + ".wanaku";
        assertEquals(expected, RuntimeConstants.wanakuHomeDir());
    }

    @Test
    void wanakuCacheDirIsUnderHomeDir() {
        String expected = RuntimeConstants.wanakuHomeDir() + File.separator + "cache";
        assertEquals(expected, RuntimeConstants.wanakuCacheDir());
    }

    @Test
    void wanakuLocalDirIsUnderHomeDir() {
        String expected = RuntimeConstants.wanakuHomeDir() + File.separator + "local";
        assertEquals(expected, RuntimeConstants.wanakuLocalDir());
    }

    @Test
    void directoriesReflectSystemProperty() {
        String userHome = System.getProperty("user.home");
        assertNotNull(userHome);
        assertEquals(userHome + File.separator + ".wanaku", RuntimeConstants.wanakuHomeDir());
    }
}
