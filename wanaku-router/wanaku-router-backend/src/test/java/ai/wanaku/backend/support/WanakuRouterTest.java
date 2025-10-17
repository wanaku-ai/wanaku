package ai.wanaku.backend.support;

public abstract class WanakuRouterTest {

    public static boolean isUnsupportedOSOnGithub() {
        String osName = System.getProperty("os.name").toLowerCase();
        String githubActions = System.getenv("GITHUB_ACTIONS");
        return "true".equalsIgnoreCase(githubActions)
                && (osName.contains("mac") || osName.contains("darwin") || osName.contains("win"));
    }
}
