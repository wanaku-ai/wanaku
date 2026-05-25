package ai.wanaku.backend.support;

import jakarta.ws.rs.core.MediaType;

import java.util.Map;

public abstract class WanakuRouterTest {

    protected Map<String, String> getHeaders() {
        return Map.of("Content-Type", MediaType.APPLICATION_JSON);
    }

    public static boolean isUnsupportedOSOnGithub() {
        String osName = System.getProperty("os.name").toLowerCase();
        String githubActions = System.getenv("GITHUB_ACTIONS");
        return "true".equalsIgnoreCase(githubActions)
                && (osName.contains("mac") || osName.contains("darwin") || osName.contains("win"));
    }
}
