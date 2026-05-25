package ai.wanaku.core.persistence.infinispan.remote;

public abstract class InfinispanRemoteTestBase {

    protected static boolean isUnsupportedOSOnGithub() {
        String osName = System.getProperty("os.name").toLowerCase();
        String githubActions = System.getenv("GITHUB_ACTIONS");
        return "true".equalsIgnoreCase(githubActions)
                && (osName.contains("mac") || osName.contains("darwin") || osName.contains("win"));
    }
}
