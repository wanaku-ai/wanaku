package ai.wanaku.core.persistence.infinispan.remote;

public final class WanakuPersistenceTest {

    private WanakuPersistenceTest() {}

    public static boolean isUnsupportedOSOnGithub() {
        String osName = System.getProperty("os.name").toLowerCase();
        String githubActions = System.getenv("GITHUB_ACTIONS");
        return "true".equalsIgnoreCase(githubActions)
                && (osName.contains("mac") || osName.contains("darwin") || osName.contains("win"));
    }
}
