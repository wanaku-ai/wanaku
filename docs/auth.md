# Authentication

Wanaku uses Keycloak to issue tokens. oauth2-proxy validates browser sessions and bearer tokens before it forwards requests to Wanaku.

```text
Browser/CLI ──► oauth2-proxy-mcp (:4180) ──► Wanaku MCP (:8081)
            └─► oauth2-proxy-mgmt (:4181) ──► Wanaku Management API (:8080)
```

Both proxies share a cookie secret. A browser login through either proxy then works on both ports. CLI requests use a Keycloak access token.

## Local Docker Compose setup

Follow the [authenticated local deployment guide](../deploy/auth/README.md). It starts Keycloak, Wanaku, and both proxies. It also creates a test user and demonstrates the `wanaku` and `wanaku-keycloak-admin` commands.

The bundled realm has three separate clients:

- `admin-cli` lets `wanaku-keycloak-admin` manage Keycloak. Its token does not authorize Wanaku API requests.
- `mcp-client` lets `wanaku` log in as a user. Its default scope adds the audience accepted by the proxies.
- `wanaku-mcp-router` lets oauth2-proxy authenticate browser sessions. It is a confidential client with a generated secret.

The local realm enables the password grant for `mcp-client`. Use this grant only for local development. Production deployments should use an interactive OAuth flow and production Keycloak clients.

## Issuer addresses

Set `WANAKU_AUTH_ISSUER` to the issuer URL that clients can reach. Set `WANAKU_AUTH_UPSTREAM_ISSUER` when the server must use a different URL to reach Keycloak. The upstream variable defaults to `WANAKU_AUTH_ISSUER`.

For the Compose setup, clients use `http://localhost:8543/realms/wanaku`; the server container uses `http://keycloak:8080/realms/wanaku` for token requests. This keeps public discovery metadata and browser redirects usable from the host.

When `WANAKU_AUTH_ISSUER` is set, Wanaku serves OAuth protected-resource metadata for MCP endpoints. When it is unset, these metadata routes return 404.

## Proxy behavior

The MCP proxy accepts bearer tokens with the configured Wanaku MCP audience. MCP endpoints use `/{namespace}/mcp`, such as `/default/mcp`. The CLI maps a bare server origin to `/default/mcp`.

The management proxy accepts the same bearer tokens for `/api/` requests. It protects the admin UI with a browser session. Set the proxy's allowed roles only when the realm maps the selected administrator role into access tokens.

Public MCP requests use `/public/mcp`. The Compose setup allows this endpoint without a token. Keep public tools safe for anonymous use.

## Logging out

`wanaku auth logout` clears the CLI's saved credentials. It does not end a browser session or revoke a token that was already issued. Browser logout behavior is tracked in [issue #2026](https://github.com/wanaku-ai/wanaku/issues/2026).
