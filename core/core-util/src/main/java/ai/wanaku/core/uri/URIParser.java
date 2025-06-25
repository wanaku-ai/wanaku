package ai.wanaku.core.uri;

import io.quarkus.qute.Qute;
import java.util.Map;

public final class URIParser {
    public static String parse(String uri, Map<String, Object> arguments) {
        return Qute.fmt(uri, arguments);
    }

    private URIParser() {}
}
