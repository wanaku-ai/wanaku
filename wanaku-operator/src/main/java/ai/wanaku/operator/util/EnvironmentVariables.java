package ai.wanaku.operator.util;

public final class EnvironmentVariables {
    public static final String AUTH_SERVER = "AUTH_SERVER";
    public static final String AUTH_PROXY = "AUTH_PROXY";
    public static final String QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED =
            "QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED";
    public static final String WANAKU_SERVICE_REGISTRATION_URI = "WANAKU_SERVICE_REGISTRATION_URI";
    public static final String QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET = "QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET";

    public static final String CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_URL = "REGISTRATION_URL";
    public static final String CAMEL_INTEGRATION_CAPABILITY_REGISTRATION_ANNOUNCE_ADDRESS =
            "REGISTRATION_ANNOUNCE_ADDRESS";
    public static final String CAMEL_INTEGRATION_CAPABILITY_GRPC_PORT = "GRPC_PORT";
    public static final String CAMEL_INTEGRATION_CAPABILITY_SERVICE_NAME = "SERVICE_NAME";
    public static final String CAMEL_INTEGRATION_CAPABILITY_ROUTES_PATH = "ROUTES_PATH";
    public static final String CAMEL_INTEGRATION_CAPABILITY_ROUTES_RULES = "ROUTES_RULES";
    public static final String CAMEL_INTEGRATION_CAPABILITY_TOKEN_ENDPOINT = "TOKEN_ENDPOINT";
    public static final String CAMEL_INTEGRATION_CAPABILITY_CLIENT_ID = "CLIENT_ID";
    public static final String CAMEL_INTEGRATION_CAPABILITY_CLIENT_SECRET = "CLIENT_SECRET";

    private EnvironmentVariables() {}
}
