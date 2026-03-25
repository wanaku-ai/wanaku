package ai.wanaku.cli.main.support.security;

public class CustomSecurityServiceConfig implements SecurityServiceConfig {
    private String clientId;
    private String clientSecret;
    private String secret;
    private String tokenEndpoint;
    private String username;
    private String password;

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    public String getPassword() {
        return password;
    }
}
