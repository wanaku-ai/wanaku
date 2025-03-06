package ai.wanaku.cli.main.converter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

public class UtilsTest {

    @Test
    public void toStringUrlTest() throws Exception {
        String current_dir = System.getProperty("user.dir");

        URLConverter converter = new URLConverter();

        String relativePath = "my/local/relative/path/tools.json";
        Assertions.assertEquals("file:"+current_dir+ File.separator+relativePath,converter.convert(relativePath).toString());

        String absolutePath = "/my/local/relative/path/tools.json";
        Assertions.assertEquals("file:"+absolutePath,converter.convert(absolutePath).toString());

        String fileAbsoluteURL = "file:/my/local/absolute/path/tools.json";
        Assertions.assertEquals(fileAbsoluteURL,converter.convert(fileAbsoluteURL).toString());

    }
}
