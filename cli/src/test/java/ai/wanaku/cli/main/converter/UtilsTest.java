package ai.wanaku.cli.main.converter;

import java.io.File;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class UtilsTest {

    URLConverter converter = new URLConverter();
    String current_dir = System.getProperty("user.dir");

    @Test
    void textFileUrl() throws Exception {
        String fileAbsoluteURL = "file:/my/local/absolute/path/tools.json";
        Assertions.assertEquals(
                fileAbsoluteURL, converter.convert(fileAbsoluteURL).toString());
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void testAbsoluteNix() throws Exception {
        String absolutePath = "/my/local/relative/path/tools.json";
        Assertions.assertEquals(
                "file:" + absolutePath, converter.convert(absolutePath).toString());
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void testRelativeNix() throws Exception {
        String relativePath = "my/local/relative/path/tools.json";
        Assertions.assertEquals(
                "file:" + current_dir + File.separator + relativePath,
                converter.convert(relativePath).toString());
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    void testAbsoluteAsURLWindows() throws Exception {
        String absolutePath = "C:/my/local/relative/path/tools.json";
        Assertions.assertEquals(
                "file:/" + absolutePath, converter.convert(absolutePath).toString());
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    void testRelativeWindows() throws Exception {
        String relativePath = "my\\local\\relative\\path\\tools.json";
        String expected = new File(relativePath).toURI().toURL().toString();
        Assertions.assertEquals(expected, converter.convert(relativePath).toString());
    }
}
