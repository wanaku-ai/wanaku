package ai.wanaku.cli.main.converter;

import java.io.File;
import java.net.URL;
import picocli.CommandLine;

public class URLConverter implements CommandLine.ITypeConverter<URL> {

    /**
     *
     * @param value the command line argument String value
     * @return the URL object
     * @throws Exception if any error occurs during the conversion.
     */
    @Override
    public URL convert(String value) throws Exception {
        try {
            return new URL(value);
        } catch (Exception e) {
            // so it's not an URL, maybe a local path?
        }
        // try if is a local path
        return new File(value).toURI().toURL();
    }
}
