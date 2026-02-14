package ai.wanaku.core.uri;

import java.util.Map;
import io.quarkus.qute.Qute;

public final class URIParser {
    public static String parse(String uri, Map<String, Object> arguments) {
        return Qute.fmt(uri, arguments);
    }

    private URIParser() {}
}
