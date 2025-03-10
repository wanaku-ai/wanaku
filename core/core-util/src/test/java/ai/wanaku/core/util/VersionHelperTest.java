package ai.wanaku.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionHelperTest {

    @Test
    public void testVersion() {
        Assertions.assertNotNull(VersionHelper.VERSION);
        Assertions.assertNotEquals("", VersionHelper.VERSION);
        Assertions.assertNotEquals("${project.version}", VersionHelper.VERSION);
    }
}
