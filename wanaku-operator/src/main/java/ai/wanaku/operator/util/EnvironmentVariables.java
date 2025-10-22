package ai.wanaku.operator.util;

public final class EnvironmentVariables {
    public static final String AUTH_SERVER = "AUTH_SERVER";
    public static final String AUTH_PROXY = "AUTH_PROXY";
    public static final String QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED =
            "QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED";
    public static final String WANAKU_SERVICE_REGISTRATION_URI = "WANAKU_SERVICE_REGISTRATION_URI";
    public static final String QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET = "QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET";

    private EnvironmentVariables() {}
}
