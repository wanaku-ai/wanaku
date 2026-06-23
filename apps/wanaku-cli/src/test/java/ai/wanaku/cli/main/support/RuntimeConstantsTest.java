package ai.wanaku.cli.main.support;

import java.io.File;
import ai.wanaku.core.util.WanakuHome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeConstantsTest {

    @Test
    void wanakuHomeDirUsesCurrentUserHome() {
        String expected = WanakuHome.get();
        assertEquals(expected, RuntimeConstants.wanakuHomeDir());
    }

    @Test
    void wanakuHomeDirDefaultsToUserHomeDotWanaku() {
        String userHome = System.getProperty("user.home");
        assertNotNull(userHome);
        String expected = userHome + File.separator + ".wanaku";
        // When no wanaku.home system property is set, it should default to user.home/.wanaku
        // This test validates the default behavior - in test env wanaku.home may not be set
        assertEquals(expected, WanakuHome.get());
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
        assertEquals(WanakuHome.get(), RuntimeConstants.wanakuHomeDir());
    }
}
